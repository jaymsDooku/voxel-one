package dev.jayms;

import static dev.jayms.ui.Controls.Action.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.net.model.*;
import dev.jayms.player.*;
import dev.jayms.ui.*;
import dev.jayms.window.Window;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.util.*;

public class Main {
    private Window window;
    private MultiplayerClient network;
    private Controls controls;
    private ControlsMenu menu;
    private Overlay overlay;
    private ShaderProgram shader;
    private dev.jayms.render.RenderPipeline rendering;
    private Camera camera;
    private final IsometricCamera overview = new IsometricCamera();
    private boolean isometric;
    private Player player;
    private PlayerModel playerModel;
    private HorseModel horseModel;
    private final CityTools cityTools = new CityTools();
    private GameConfig gameConfig = GameConfig.sandbox();

    private CityFrame city() {
        return network == null ? local.city.frame() : network.city;
    }

    private int riderId() {
        return network == null ? 1000000 : network.id;
    }

    private void cityCommand(CityCommand command) {
        if (network == null) notice = local.city.command(command, riderId(), player.pose(0));
        else if (!network.cityCommand(command))
            notice = "Disconnected: reconnect to use city tools";
    }

    private CityFrame.Horse riding() {
        return city().horses().stream()
                .filter(h -> h.rider() == riderId())
                .findFirst()
                .orElse(null);
    }

    private VoxelModelRenderer modelRenderer;
    private DistantTerrainRenderer distant;
    private ModelEditor editor;
    private LightColorMenu lightColors;
    private int modelResults;
    private World world;
    private InventoryHud inventoryHud = new InventoryHud();
    private LocalGame local;
    private long seed = Terrain.DEFAULT_SEED;
    private java.nio.file.Path offlineSave = Controls.directory().resolve("offline-world.dat");
    private double nextSave;
    private boolean captured = true, firstMouse = true;
    private double mouseX, mouseY, nextNetworkUpdate;
    private int framebufferWidth = 1280, framebufferHeight = 720;
    private final Map<String, Protocol.Edit> predicted = new HashMap<>();
    private final Matrix4f projection = new Matrix4f(), view = new Matrix4f();
    private final FrustumIntersection frustum = new FrustumIntersection();
    private String notice = "";

    public void run() throws Exception {
        controls = new Controls(Controls.directory().resolve("controls.properties"));
        menu = new ControlsMenu(controls);
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");
        try {
            init();
            initScene();
            loop();
        } finally {
            cleanup();
        }
    }

    private void init() {
        window = new Window(framebufferWidth, framebufferHeight, "Voxel One", false, true);
        glfwSetWindowSizeLimits(window.getHandle(), 640, 640, GLFW_DONT_CARE, GLFW_DONT_CARE);
        window.center();
        window.setOpenGlContext();
        window.vSync();
        GL.createCapabilities();
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glClearColor(.48f, .72f, .92f, 1);
        glfwSetFramebufferSizeCallback(
                window.getHandle(),
                (handle, w, h) -> {
                    framebufferWidth = w;
                    framebufferHeight = h;
                    glViewport(0, 0, w, h);
                });
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var w = stack.mallocInt(1);
            var h = stack.mallocInt(1);
            glfwGetFramebufferSize(window.getHandle(), w, h);
            framebufferWidth = w.get(0);
            framebufferHeight = h.get(0);
            glViewport(0, 0, framebufferWidth, framebufferHeight);
        }
        window.show();
    }

    private void initScene() throws Exception {
        shader = new ShaderProgram("shaders/voxel.vert", "shaders/voxel.frag");
        rendering = new dev.jayms.render.RenderPipeline();
        overlay = new Overlay();
        camera = new Camera();
        playerModel = new PlayerModel();
        horseModel = new HorseModel();
        if (network == null) {
            local = new LocalGame(offlineSave, seed);
            seed = local.seed;
        } else seed = network.seed;
        world = new World(seed, network == null ? local.models : network.models);
        modelRenderer = new VoxelModelRenderer(world.models());
        editor = new ModelEditor();
        lightColors = new LightColorMenu(Controls.directory().resolve("light-colour.properties"));
        for (var e : network == null ? local.edits.values() : network.initialEdits) world.apply(e);
        Protocol.Pose spawn =
                network == null
                        ? new Protocol.Pose(
                                0,
                                8.5f,
                                world.terrain().column(8, 24).height() + 1.01f,
                                24.5f,
                                -90,
                                -20)
                        : network.spawn;
        player =
                new Player(
                        new Vector3f(spawn.x(), spawn.y(), spawn.z()),
                        spawn.yaw(),
                        spawn.pitch(),
                        camera);
        world.stream(spawn.x(), spawn.z(), 9);
        if (network == null) {
            int surface = Terrain.MAX_Y;
            while (surface > Terrain.MIN_Y && world.sample(8, surface, 24) == 0) surface--;
            player = new Player(new Vector3f(8.5f, surface + 1.01f, 24.5f), -90, -20, camera);
        }
        distant = new DistantTerrainRenderer(seed);
        distant.update(world, player.position().x, player.position().z);
        if (local != null)
            local.startGame(
                    gameConfig,
                    new CitySimulation.Ground() {
                        public int type(int x, int y, int z) {
                            return world.sample(x, y, z);
                        }

                        public boolean playerOccupied(int x, int y, int z, int width, int depth) {
                            var p = player.position();
                            return local.city != null
                                    && p.x + .3 > x
                                    && p.x - .3 < x + width
                                    && p.z + .3 > z
                                    && p.z - .3 < z + depth
                                    && p.y + 1.8 > y
                                    && p.y < y + 7;
                        }

                        public boolean occupied(int x, int y, int z, int width, int depth) {
                            return playerOccupied(x, y, z, width, depth)
                                    || local.city != null
                                            && CityOccupancy.overlaps(
                                                    city(), x, y, z, width, 7, depth);
                        }

                        public void apply(List<Protocol.Edit> batch) {
                            for (var edit : batch) {
                                world.apply(edit);
                                WorldVoxels.remember(local.edits, edit);
                            }
                        }
                    });
        if (city().config().city()) {
            isometric = true;
            overview.cityMode();
            overview.focus(16, 26, player.position().y);
        }
        player.resolvePenetration(world);
        configureInput();
        setCaptured(true);
    }

    private void configureInput() {
        glfwSetKeyCallback(
                window.getHandle(),
                (handle, key, scancode, action, mods) -> {
                    if (lightColors.open) {
                        if (action == GLFW_PRESS && controls.matches(LIGHT_COLOR, key))
                            lightColors.open = false;
                        else lightColors.key(key, action, mods);
                        if (!lightColors.open) setCaptured(true);
                        return;
                    }
                    if (editor.open) {
                        if (action == GLFW_PRESS && controls.matches(MODEL_EDITOR, key))
                            editor.closeEditor();
                        else editor.key(key, action, mods);
                        if (!editor.open) setCaptured(true);
                        return;
                    }
                    if (menu.open) {
                        menu.key(key, action);
                        if (!menu.open) setCaptured(true);
                        return;
                    }
                    if (action == GLFW_PRESS) {
                        if (isometric
                                && city().config().city()
                                && !menu.open
                                && !inventoryHud.open
                                && cityTools.key(key, this::cityCommand)) return;
                        input(key);
                    }
                });
        glfwSetCursorPosCallback(
                window.getHandle(),
                (handle, x, y) -> {
                    if (editor.open) {
                        int[] size = window.getSize();
                        editor.drag(
                                (float) x * framebufferWidth / size[0],
                                (float) y * framebufferHeight / size[1],
                                framebufferWidth,
                                framebufferHeight);
                    }
                    if (captured && !firstMouse)
                        player.look(
                                (float) (x - mouseX) * controls.sensitivity,
                                (float) (mouseY - y) * controls.sensitivity);
                    mouseX = x;
                    mouseY = y;
                    firstMouse = !captured;
                });
        glfwSetMouseButtonCallback(
                window.getHandle(),
                (handle, button, action, mods) -> {
                    if (editor.open) {
                        if (action == GLFW_PRESS && controls.matches(MODEL_EDITOR, -button - 1)) {
                            editor.closeEditor();
                            setCaptured(true);
                            return;
                        }
                        if (action == GLFW_RELEASE) editor.release();
                        else if (action == GLFW_PRESS) {
                            int[] size = window.getSize();
                            editor.click(
                                    button,
                                    (float) mouseX * framebufferWidth / size[0],
                                    (float) mouseY * framebufferHeight / size[1],
                                    framebufferWidth,
                                    framebufferHeight,
                                    this::createModel);
                        }
                        return;
                    }
                    if (lightColors.open) {
                        if (action == GLFW_PRESS && button == GLFW_MOUSE_BUTTON_LEFT) {
                            int[] size = window.getSize();
                            lightColors.click(
                                    (float) mouseX * framebufferWidth / size[0],
                                    (float) mouseY * framebufferHeight / size[1],
                                    framebufferWidth,
                                    framebufferHeight);
                            if (!lightColors.open) setCaptured(true);
                        }
                        return;
                    }
                    if (action != GLFW_PRESS) return;
                    if (inventoryHud.open) {
                        int[] size = window.getSize();
                        if (button == GLFW_MOUSE_BUTTON_LEFT)
                            inventoryHud.click(
                                    (float) mouseX * framebufferWidth / size[0],
                                    (float) mouseY * framebufferHeight / size[1],
                                    framebufferWidth,
                                    framebufferHeight,
                                    inventory(),
                                    (a, b) -> {
                                        if (network == null) local.inventory.swap(a, b);
                                        else network.swap(a, b);
                                    },
                                    id -> {
                                        if (network == null)
                                            notice = local.craft(id, player.pose(0));
                                        else network.craft(id, player.pose(network.id));
                                    });
                        return;
                    }
                    if (menu.open) {
                        int[] size = window.getSize();
                        menu.click(
                                button,
                                (float) mouseX * framebufferWidth / size[0],
                                (float) mouseY * framebufferHeight / size[1],
                                framebufferWidth,
                                framebufferHeight,
                                () -> glfwSetWindowShouldClose(handle, true));
                        if (!menu.open) setCaptured(true);
                        return;
                    }
                    if (isometric && city().config().city() && button == GLFW_MOUSE_BUTTON_LEFT) {
                        int[] size = window.getSize();
                        cityTools.click(
                                (float) mouseX * framebufferWidth / size[0],
                                (float) mouseY * framebufferHeight / size[1],
                                framebufferWidth,
                                framebufferHeight,
                                projection,
                                view,
                                city(),
                                this::cityCommand);
                        return;
                    }
                    input(-button - 1);
                });
        glfwSetCharCallback(
                window.getHandle(),
                (handle, character) -> {
                    if (editor.open) editor.character(character);
                    else if (lightColors.open) lightColors.character(character);
                });
        glfwSetScrollCallback(
                window.getHandle(),
                (handle, x, y) -> {
                    if (editor.open) {
                        int[] size = window.getSize();
                        editor.scroll(
                                (float) mouseX * framebufferWidth / size[0],
                                (float) mouseY * framebufferHeight / size[1],
                                y,
                                framebufferWidth,
                                framebufferHeight);
                    } else if (lightColors.open) return;
                    else if (menu.open) menu.scroll(y);
                    else if (isometric && !inventoryHud.open) overview.zoom(y);
                    else if (captured || inventoryHud.open) inventoryHud.scroll(y);
                });
        glfwSetWindowFocusCallback(
                window.getHandle(),
                (handle, focused) -> {
                    if (!focused) {
                        inventoryHud.close();
                        if (editor.open || lightColors.open) return;
                        menu.open = true;
                        setCaptured(false);
                    }
                });
    }

    private Inventory inventory() {
        return network == null ? local.inventory : network.inventory;
    }

    private Map<Integer, ItemDrop> drops() {
        return network == null ? local.drops : network.drops;
    }

    private void createModel(ModelDefinition model) {
        try {
            if (network == null) {
                editor.message = local.createModel(model);
                local.save();
            } else if (network.createModel(model)) editor.message = "Creating model item...";
            else editor.message = "Disconnected: reconnect to create model items.";
        } catch (java.io.IOException e) {
            editor.message = e.getMessage();
        }
    }

    private void input(int code) {
        if (controls.matches(DISMOUNT, code) && riding() != null) {
            cityCommand(new CityCommand(CityCommand.RIDE, 0, List.of()));
            return;
        }
        if (controls.matches(LIGHT_COLOR, code)) {
            inventoryHud.close();
            lightColors.show();
            setCaptured(false);
            return;
        }
        if (controls.matches(MODEL_EDITOR, code)) {
            inventoryHud.close();
            BlockHit hit =
                    BlockRaycaster.cast(world, player.eyePosition(), player.facingDirection(), 6);
            if (hit != null) {
                var model = world.models().get(world.getBlock(hit.x(), hit.y(), hit.z()));
                if (model != null) editor.load(model.definition());
            }
            editor.open = true;
            setCaptured(false);
            return;
        }
        if (inventoryHud.open) {
            if (code == GLFW_KEY_ESCAPE || controls.matches(INVENTORY, code)) {
                inventoryHud.close();
                setCaptured(true);
            }
            return;
        }
        if (controls.matches(ISOMETRIC, code)) {
            isometric = !isometric;
            if (isometric) {
                if (city().config().city())
                    overview.focus(player.position().x, player.position().z, player.position().y);
                else overview.fit();
            }
            setCaptured(!isometric);
            return;
        }
        if (isometric) {
            if (controls.matches(ZOOM_IN, code)) {
                overview.zoom(1);
                return;
            }
            if (controls.matches(ZOOM_OUT, code)) {
                overview.zoom(-1);
                return;
            }
            if (controls.matches(FIT_VIEW, code)) {
                overview.fit();
                return;
            }
            if (controls.matches(VIEW, code)) {
                isometric = false;
                player.toggleView();
                setCaptured(true);
                return;
            }
        }
        if (controls.matches(INVENTORY, code)) {
            inventoryHud.toggle();
            setCaptured(false);
            return;
        }
        for (int i = 0; i < 9; i++)
            if (controls.matches(Controls.Action.values()[SLOT_1.ordinal() + i], code)) {
                inventoryHud.selected = i;
                return;
            }
        if (code == GLFW_KEY_ESCAPE || controls.matches(MENU, code)) {
            menu.toggle();
            setCaptured(!menu.open);
        } else if (controls.matches(CURSOR, code)) setCaptured(!captured);
        else if (captured) {
            if (controls.matches(FLY, code)) player.toggleFlight();
            else if (controls.matches(VIEW, code)) player.toggleView();
            else if (controls.matches(BREAK, code)) interact(false);
            else if (controls.matches(PLACE, code)) interact(true);
        }
    }

    private void setCaptured(boolean value) {
        value =
                value
                        && !isometric
                        && (editor == null || !editor.open)
                        && (lightColors == null || !lightColors.open);
        captured = value;
        firstMouse = true;
        glfwSetInputMode(
                window.getHandle(), GLFW_CURSOR, value ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
    }

    private void loop() throws Exception {
        double previous = glfwGetTime();
        while (!window.shouldClose()) {
            double now = glfwGetTime();
            float dt = (float) (now - previous);
            previous = now;
            // World changes are applied before physics, including collision recovery for late
            // edits.
            if (network != null) {
                for (var e : network.poll()) world.apply(e);
                predicted.entrySet().removeIf(e -> !network.pending(e.getValue()));
                if (!network.connected()) {
                    for (var e : predicted.values()) world.apply(e);
                    predicted.clear();
                }
                if (!network.notice().isEmpty()) notice = network.notice();
                if (network.modelResults != modelResults) {
                    modelResults = network.modelResults;
                    editor.message = network.modelMessage;
                }
            }
            if (network != null && network.respawn != null) {
                var p = network.respawn;
                world.stream(p.x(), p.z(), 9);
                player = new Player(new Vector3f(p.x(), p.y(), p.z()), p.yaw(), p.pitch(), camera);
                network.respawn = null;
                notice = "You respawned. Your inventory was kept.";
            }
            if (local != null) local.city.advance(Math.min(dt, .25));
            var horse = riding();
            player.mount(
                    horse != null,
                    horse == null ? null : new Vector3f(horse.x(), horse.y(), horse.z()));
            if (isometric
                    && city().config().city()
                    && !menu.open
                    && !inventoryHud.open
                    && !editor.open
                    && !lightColors.open) {
                float speed = Math.min(dt, .1f) * 50 / Math.max(.25f, overview.zoom() / 64);
                float
                        f =
                                (controls.down(window.getHandle(), FORWARD) ? 1 : 0)
                                        - (controls.down(window.getHandle(), BACKWARD) ? 1 : 0),
                        r =
                                (controls.down(window.getHandle(), RIGHT) ? 1 : 0)
                                        - (controls.down(window.getHandle(), LEFT) ? 1 : 0);
                if (f != 0 || r != 0) overview.pan((-f + r) * speed, (-f - r) * speed);
            }
            var location = player.position();
            float streamX = location.x, streamZ = location.z;
            if (isometric && city().config().city() && overview.focused()) {
                streamX = overview.focusX();
                streamZ = overview.focusZ();
            }
            world.stream(streamX, streamZ, 2);
            float forward =
                    captured
                            ? (controls.down(window.getHandle(), FORWARD) ? 1 : 0)
                                    - (controls.down(window.getHandle(), BACKWARD) ? 1 : 0)
                            : 0;
            float right =
                    captured
                            ? (controls.down(window.getHandle(), RIGHT) ? 1 : 0)
                                    - (controls.down(window.getHandle(), LEFT) ? 1 : 0)
                            : 0;
            player.step(
                    world,
                    dt,
                    forward,
                    right,
                    captured && controls.down(window.getHandle(), JUMP),
                    captured && controls.down(window.getHandle(), SPRINT),
                    captured && controls.down(window.getHandle(), DESCEND));
            if (local != null) {
                if (local.tick(
                        player.pose(0),
                        player.grounded(),
                        dt,
                        e -> world.sample(e.x(), e.y(), e.z()))) {
                    float y = world.terrain().column(8, 24).height() + 1.01f;
                    world.stream(8, 24, 9);
                    player = new Player(new Vector3f(8.5f, y, 24.5f), -90, -20, camera);
                    notice = "You respawned. Your inventory was kept.";
                }
                if (now >= nextSave) {
                    local.save();
                    nextSave = now + 60;
                }
            }
            if (local != null) local.city.riderMoved(riderId(), player.pose(0));
            player.heldItem(inventory().type(inventoryHud.selected));
            player.heldColor(lightColors.color());
            player.updateCamera(world);
            if (network != null && now >= nextNetworkUpdate) {
                network.move(player.pose(network.id));
                nextNetworkUpdate = now + .05;
            }
            glfwSetWindowTitle(
                    window.getHandle(),
                    "Voxel One | "
                            + (network == null ? "Offline" : network.status())
                            + " | "
                            + (isometric
                                    ? "Isometric"
                                    : player.mounted()
                                            ? "Riding horse | " + player.cameraView()
                                            : player.flying()
                                                    ? "Flying | " + player.cameraView()
                                                    : "Walking | " + player.cameraView())
                            + " | "
                            + (editor.open
                                    ? "Model editor"
                                    : menu.open
                                            ? "Controls menu"
                                            : inventoryHud.open ? "Inventory" : "Esc controls"));
            if (editor.open) glClearColor(.025f, .045f, .075f, 1);
            else glClearColor(.48f, .72f, .92f, 1);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            if (framebufferWidth > 0 && framebufferHeight > 0) {
                render();
                renderOverlay();
            }
            window.swapBuffers();
            window.pollEvents();
        }
    }

    private void render() {
        if (editor.open) {
            editor.renderPreview(shader, framebufferWidth, framebufferHeight);
            return;
        }
        var location = player.position();
        if (isometric && city().config().city() && overview.focused())
            location.set(overview.focusX(), location.y, overview.focusZ());
        distant.update(world, location.x, location.z);
        if (isometric)
            projection.set(
                    overview.projection(distant.bounds(), framebufferWidth, framebufferHeight));
        else
            projection
                    .identity()
                    .perspective(
                            (float) Math.toRadians(70),
                            (float) framebufferWidth / framebufferHeight,
                            .1f,
                            DistantTerrainPlan.RADIUS * 2);
        view.set((isometric ? overview.camera() : camera).createViewMatrix());
        frustum.set(new Matrix4f(projection).mul(view));
        shader.bind();
        shader.setMatrix4("uProjection", projection);
        shader.setMatrix4("uView", view);

        shader.setInt("uVertexColor", 1);
        shader.setInt("uInstanced", 0);
        shader.setInt("uDistantTerrain", 0);
        shader.setInt("uFog", isometric ? 0 : 1);
        shader.setVector3(
                "uCameraPosition", camera.position().x, camera.position().y, camera.position().z);
        // Build complete nearby columns first, including their offscreen chunks, before replacing
        // the background approximation. Air chunks consume neither GPU buffers nor mesh budget.
        var chunks = new ArrayList<>(world.getLoadedChunks().entrySet());
        chunks.sort(
                Comparator.comparingDouble(
                        e -> {
                            float dx = e.getKey().chunkX() * 16 + 8 - location.x;
                            float dz = e.getKey().chunkZ() * 16 + 8 - location.z;
                            return dx * dx + dz * dz;
                        }));
        int meshBudget = 3;
        for (var entry : chunks) {
            Chunk c = entry.getValue();
            if (!c.dirty()) continue;
            if (c.isEmpty()) {
                c.checkMesh();
                continue;
            }
            c.checkMesh();
            if (--meshBudget == 0) break;
        }
        rendering.time(
                city().config(),
                city().elapsed()
                        + (network == null
                                ? 0
                                : Math.min(1, (System.nanoTime() - network.cityReceived) / 1e9)));
        rendering.update(world, location.x, location.z);
        rendering.renderShadows(world, modelRenderer, location);
        rendering.begin(
                framebufferWidth,
                framebufferHeight,
                projection,
                view,
                (isometric ? overview.camera() : camera).position(),
                isometric,
                shader);
        var detailed = world.renderedColumns();
        rendering.distant(distant, frustum, detailed, shader);
        for (var entry : world.getLoadedChunks().entrySet()) {
            ChunkPos p = entry.getKey();
            if (!detailed.contains(new ChunkPos(p.chunkX(), 0, p.chunkZ()))) continue;
            if (!frustum.testAab(
                    p.chunkX() * 16,
                    p.chunkY() * 16,
                    p.chunkZ() * 16,
                    p.chunkX() * 16 + 16,
                    p.chunkY() * 16 + 16,
                    p.chunkZ() * 16 + 16)) continue;
            shader.setMatrix4(
                    "uModel",
                    new Matrix4f().translation(p.chunkX() * 16, p.chunkY() * 16, p.chunkZ() * 16));
            Chunk c = entry.getValue();
            if (c.getMesh() != null) c.getMesh().render();
        }
        modelRenderer.render(world, frustum, shader);
        for (ItemDrop drop : drops().values())
            if (player.position().distanceSquared(drop.x(), drop.y(), drop.z()) < 10000)
                if (Blocks.isModel(drop.type()))
                    modelRenderer.renderDrop(drop, (float) glfwGetTime(), shader);
                else playerModel.renderDrop(drop, (float) glfwGetTime(), shader);
        for (var h : city().horses()) {
            Protocol.Pose p =
                    new Protocol.Pose(
                            h.id(),
                            h.x(),
                            h.y(),
                            h.z(),
                            h.yaw(),
                            0,
                            h.phase(),
                            h.rider() != 0 ? 1 : 0,
                            false);
            if (network != null && network.horses.containsKey(h.id()) && h.rider() != riderId())
                p = network.horses.get(h.id()).sample(System.nanoTime());
            if (h.rider() == riderId()) {
                var loc = player.position();
                p =
                        new Protocol.Pose(
                                h.id(),
                                loc.x,
                                loc.y - .75f,
                                loc.z,
                                player.yaw(),
                                0,
                                player.walkPhase(),
                                player.walkAmount(),
                                false);
            }
            if (p != null) horseModel.render(p, shader);
        }
        for (var c : city().citizens()) {
            Protocol.Pose p =
                    new Protocol.Pose(
                            c.id(),
                            c.x(),
                            c.y(),
                            c.z(),
                            c.yaw(),
                            0,
                            c.phase(),
                            c.activity().startsWith("Commuting")
                                            || c.activity().startsWith("Going")
                                            || c.activity().startsWith("Buying")
                                    ? 1
                                    : 0,
                            false);
            if (network != null && network.citizens.containsKey(c.id()))
                p = network.citizens.get(c.id()).sample(System.nanoTime());
            if (p != null)
                playerModel.renderCitizen(p, c.cohort(), c.horse() != 0, shader, modelRenderer);
        }
        if (isometric || player.thirdPerson()) {
            if (player.mounted()) playerModel.renderRider(player.pose(0), shader, modelRenderer);
            else playerModel.render(player.pose(0), shader, modelRenderer);
        }
        if (network != null)
            for (var remote : network.remotePlayers.values()) {
                var p = remote.sample(System.nanoTime());
                if (p != null) {
                    if (city().horses().stream().anyMatch(h -> h.rider() == p.id()))
                        playerModel.renderRider(p, shader, modelRenderer);
                    else playerModel.render(p, shader, modelRenderer);
                }
            }
        if (!isometric && !player.thirdPerson()) {
            glClear(GL_DEPTH_BUFFER_BIT);
            shader.setInt("uFog", 0);
            shader.setInt("uShadowEnabled", 0);
            shader.setInt("uHeld", 1);
            shader.setMatrix4(
                    "uProjection",
                    new Matrix4f()
                            .perspective(
                                    (float) Math.toRadians(70),
                                    (float) framebufferWidth / framebufferHeight,
                                    .03f,
                                    10));
            shader.setMatrix4("uView", new Matrix4f());
            playerModel.renderFirstPerson(player, shader, modelRenderer);
        }
        rendering.finish();
    }

    private void renderOverlay() {
        overlay.begin(framebufferWidth, framebufferHeight);
        if (editor.open) {
            editor.render(overlay, framebufferWidth, framebufferHeight);
            overlay.end();
            return;
        }
        for (var c : city().citizens())
            nameplate(c.name(), new Vector3f(c.x(), c.y() + 2.15f, c.z()));
        if (network != null)
            for (var remote : network.remotePlayers.values()) {
                var p = remote.sample(System.nanoTime());
                if (p != null) nameplate(remote.name, new Vector3f(p.x(), p.y() + 2.15f, p.z()));
            }
        if (isometric || player.thirdPerson())
            nameplate(
                    network == null ? "Offline player" : network.username,
                    player.position().add(0, 2.15f, 0));
        if (captured) {
            overlay.rectangle(
                    framebufferWidth / 2f - 7, framebufferHeight / 2f - 1, 14, 2, 1, 1, 1, .9f);
            overlay.rectangle(
                    framebufferWidth / 2f - 1, framebufferHeight / 2f - 7, 2, 14, 1, 1, 1, .9f);
        }
        overlay.rectangle(12, 12, 600, 52, .015f, .035f, .065f, .75f);
        overlay.text(
                (network == null ? "Offline" : network.status())
                        + " | "
                        + (isometric
                                ? "ISOMETRIC | 4096 x 4096 BLOCKS"
                                : player.mounted()
                                        ? "RIDING HORSE | H: dismount"
                                        : player.flying() ? "FLYING" : "WALKING"),
                22,
                22,
                1.8f);
        overlay.text(
                isometric
                        ? Controls.keyName(controls.code(ISOMETRIC))
                                + ": return | Wheel / "
                                + Controls.keyName(controls.code(ZOOM_IN))
                                + " / "
                                + Controls.keyName(controls.code(ZOOM_OUT))
                                + ": zoom | "
                                + Controls.keyName(controls.code(FIT_VIEW))
                                + ": fit world"
                        : "Esc: controls | "
                                + Controls.keyName(controls.code(FLY))
                                + ": flight | "
                                + Controls.keyName(controls.code(VIEW))
                                + ": camera | "
                                + Controls.keyName(controls.code(ISOMETRIC))
                                + ": sky view | "
                                + Controls.keyName(controls.code(MODEL_EDITOR))
                                + ": models",
                22,
                44,
                1.5f);
        var pos = player.position();
        overlay.text(
                world.terrain()
                                .column((int) Math.floor(pos.x), (int) Math.floor(pos.z))
                                .biome()
                                .name()
                                .replace('_', ' ')
                        + " | Seed "
                        + seed,
                22,
                76,
                1.4f);
        int[] windowSize = window.getSize();
        inventoryHud.render(
                overlay,
                inventory(),
                network == null ? local.health : network.health,
                framebufferWidth,
                framebufferHeight,
                controls,
                world.models(),
                (float) mouseX * framebufferWidth / windowSize[0],
                (float) mouseY * framebufferHeight / windowSize[1]);
        if (!notice.isEmpty())
            overlay.text(notice, 20, city().config().city() ? 132 : 99, 1.4f, 1, .8f, .4f, 1);
        if (Blocks.material(player.heldItem()) == Blocks.LED)
            overlay.text(
                    String.format(
                            "LED #%06X | %s: colour",
                            lightColors.color(), Controls.keyName(controls.code(LIGHT_COLOR))),
                    20,
                    framebufferHeight - 130,
                    1.4f);
        if (city().config().city() && !inventoryHud.open && !menu.open && !lightColors.open)
            cityTools.render(
                    overlay,
                    framebufferWidth,
                    framebufferHeight,
                    projection,
                    view,
                    city(),
                    isometric);
        menu.render(overlay, framebufferWidth, framebufferHeight);
        lightColors.render(overlay, framebufferWidth, framebufferHeight);
        overlay.end();
    }

    private void nameplate(String name, Vector3f position) {
        float distance = player.position().distance(position);
        if (!isometric && distance > 70) return;
        Vector4f clip = new Vector4f(position, 1);
        view.transform(clip);
        projection.transform(clip);
        if (clip.w <= 0 || clip.z < -clip.w || clip.z > clip.w) return;
        float x = (clip.x / clip.w * .5f + .5f) * framebufferWidth,
                y = (.5f - clip.y / clip.w * .5f) * framebufferHeight;
        float scale = isometric ? 1.3f : Math.max(1.3f, Math.min(2.3f, 16 / Math.max(1, distance)));
        float width = overlay.textWidth(name, scale);
        overlay.rectangle(x - width / 2 - 9, y - 6, width + 18, 22 * scale, .03f, .18f, .24f, .5f);
        overlay.rectangle(x - width / 2 - 9, y - 6, width + 18, 1, .2f, .9f, 1, .8f);
        overlay.text(name, x - width / 2 + 1, y + 1, scale, .1f, .75f, 1, .5f);
        overlay.text(name, x - width / 2, y, scale, .65f, .97f, 1, .95f);
    }

    private void interact(boolean place) {
        if (isometric) return;
        if (place && city().config().city()) {
            if (riding() != null) {
                cityCommand(new CityCommand(CityCommand.RIDE, 0, List.of()));
                return;
            }
            CityFrame.Horse closest = null;
            float best = 5;
            var eye = player.eyePosition();
            var aim = player.facingDirection();
            var blockHit = BlockRaycaster.cast(world, eye, aim, 6);
            if (blockHit != null) best = Math.min(best, blockHit.distance());
            for (var h : city().horses()) {
                if (h.rider() != 0) continue;
                float t = HorseInteraction.distance(h, eye, aim);
                if (t < best) {
                    closest = h;
                    best = t;
                }
            }
            if (closest != null) {
                cityCommand(new CityCommand(CityCommand.RIDE, closest.id(), List.of()));
                return;
            }
        }
        if (!place) player.swing();
        if (network != null && !network.connected()) {
            notice = "Disconnected: reconnect to edit the world.";
            return;
        }
        BlockHit hit =
                BlockRaycaster.cast(world, player.eyePosition(), player.facingDirection(), 6);
        if (hit == null) return;
        int type = place ? inventory().type(inventoryHud.selected) : 0;
        if (place && type == 0) {
            notice =
                    "This slot is empty. Break blocks and collect drops, or craft building pieces.";
            return;
        }
        int depth = place ? Blocks.depth(type) : hit.depth();
        Vector3f point = player.eyePosition().fma(hit.distance(), player.facingDirection());
        double offset = place ? .0001 : -.0001;
        Protocol.Edit edit =
                place && depth == 0
                        ? new Protocol.Edit(
                                hit.x() + hit.normalX(),
                                hit.y() + hit.normalY(),
                                hit.z() + hit.normalZ(),
                                type)
                        : Protocol.Edit.at(
                                point.x + hit.normalX() * offset,
                                point.y + hit.normalY() * offset,
                                point.z + hit.normalZ() * offset,
                                type,
                                depth);
        if (place && Blocks.material(type) == Blocks.LED)
            edit = edit.withColor(lightColors.color());
        int x = edit.x(), y = edit.y(), z = edit.z();
        if (!edit.valid()
                || !world.isLoaded(x, y, z)
                || place && world.region(edit) != 0
                || place && player.overlaps(world, edit)
                || place && CityOccupancy.overlaps(city(), edit)) return;
        if (network == null) {
            if (local.edit(edit, world.region(edit), inventoryHud.selected)) {
                world.apply(edit);
                if (place) player.swing(true);
            }
        } else if (network.edit(edit, player.pose(network.id), inventoryHud.selected)) {
            var old = edit.withType(world.region(edit));
            if (Blocks.material(old.type()) == Blocks.LED)
                old = old.withColor(WorldVoxels.lightColor(world.regionValue(edit)));
            predicted.put(edit.key(), old);
            if (place) player.swing(true);
            // Reserve it immediately so movement cannot enter an unconfirmed solid block.
            world.apply(edit);
        }
    }

    private void cleanup() throws Exception {
        if (local != null) local.save();
        if (network != null) network.close();
        if (distant != null) distant.close();
        if (world != null) world.close();
        if (playerModel != null) playerModel.close();
        if (horseModel != null) horseModel.close();
        if (modelRenderer != null) modelRenderer.close();
        if (editor != null) editor.close();
        if (overlay != null) overlay.close();
        if (rendering != null) rendering.close();
        if (shader != null) shader.close();
        if (window != null) window.destroy();
        glfwTerminate();
        var error = glfwSetErrorCallback(null);
        if (error != null) error.free();
    }

    public static void main(String[] args) throws Exception {
        String host = null, pin = null;
        int port = Protocol.PORT;
        boolean offline = false, cityGame = false, gameExplicit = false;
        double daySeconds = 1200, startHour = 8;
        boolean cycle = true;
        long seed = Terrain.DEFAULT_SEED;
        java.nio.file.Path save = Controls.directory().resolve("offline-world.dat");
        for (int i = 0; i < args.length; i++)
            switch (args[i]) {
                case "--game" -> {
                    String value = args[++i];
                    if (!value.equals("city") && !value.equals("sandbox"))
                        throw new IllegalArgumentException("Game must be city or sandbox");
                    cityGame = value.equals("city");
                    gameExplicit = true;
                    LoginDialog.citySelected = cityGame;
                }
                case "--day-seconds" -> daySeconds = Double.parseDouble(args[++i]);
                case "--start-hour" -> startHour = Double.parseDouble(args[++i]);
                case "--fixed-time" -> cycle = false;
                case "--server" -> host = args[++i];
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--fingerprint" -> pin = args[++i];
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--world" -> save = java.nio.file.Path.of(args[++i]);
                case "--offline" -> offline = true;
                default ->
                        throw new IllegalArgumentException(
                                "Usage: --server HOST --port PORT --fingerprint SHA256 --offline"
                                        + " --world FILE --seed NUMBER");
            }
        Main game = new Main();
        game.seed = seed;
        game.offlineSave = save;
        try {
            if (!offline) {
                try {
                    game.network = LoginDialog.open(host, port, pin);
                } catch (java.util.concurrent.CancellationException e) {
                    return;
                } catch (java.util.concurrent.ExecutionException e) {
                    if (e.getCause() instanceof java.util.concurrent.CancellationException) return;
                    throw e;
                }
            }
            if (!offline && !gameExplicit) cityGame = LoginDialog.citySelected;
            game.gameConfig = new GameConfig(cityGame, cityGame && cycle, daySeconds, startHour);
            if (cityGame && save.equals(Controls.directory().resolve("offline-world.dat")))
                game.offlineSave = Controls.directory().resolve("offline-city.dat");
            game.run();
        } finally {
            if (game.network != null) game.network.close();
        }
    }
}
