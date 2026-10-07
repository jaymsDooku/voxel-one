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
    /** Optional render-thread observer for reproducible application playtests. */
    public interface FrameObserver {
        default void started(Main game) throws Exception {}
        default void beforeFrame(Main game) throws Exception {}
        default void afterFrame(Main game) throws Exception {}
    }
    private FrameObserver frameObserver;
    private Window window;
    private final dev.jayms.recording.ScreenRecorder recorder =
            new dev.jayms.recording.ScreenRecorder(Controls.directory().resolve("recordings"));
    private MultiplayerClient network;
    private Controls controls;
    private ControlsMenu menu;
    private CitySaves citySaves;
    private java.nio.file.Path requestedSave;
    private Overlay overlay;
    private ShaderProgram shader;
    private dev.jayms.render.RenderPipeline rendering;
    private Camera camera;
    private final IsometricCamera overview = new IsometricCamera();
    private final IsometricOrbitDrag orbitDrag = new IsometricOrbitDrag();
    private boolean isometric;
    private Player player;
    private PlayerModel playerModel;
    private HorseModel horseModel;
    private PlaneModel planeModel;
    private RailwayModel railwayModel;
    private ShipModel shipModel;
    private final ShippingRoutes shippingRoutes = new ShippingRoutes();
    private Jeep jeep;
    private dev.jayms.audio.VehicleAudio vehicleAudio;
    private JeepModel jeepModel;
    private java.nio.file.Path jeepSave() { return offlineSave.resolveSibling(offlineSave.getFileName()+".jeep"); }
    private FarmModels farmModels;
    private final CityTools cityTools = new CityTools();
    private final BuildingInfo buildingInfo = new BuildingInfo();
    private final MayorDashboard mayorDashboard = new MayorDashboard();
    private GameConfig gameConfig = GameConfig.sandbox();
    private ProductionCatalog productionCatalog = ProductionCatalog.cityGame();

    private CityFrame renderCity;

    private CityFrame city() {
        if (renderCity != null) return renderCity;
        return network == null ? local.city.frame() : network.city;
    }

    private int riderId() {
        return network == null ? 1000000 : network.id;
    }

    private void cityCommand(CityCommand command) {
        if (network == null) {
            notice = local.city.command(command, riderId(), player.pose(0));
            cityTools.roadResult(command, notice);
        } else if (!network.cityCommand(command, result -> cityTools.roadResult(command, result))) {
            notice = network.notice().isBlank() ? "Disconnected: reconnect to use city tools" : network.notice();
            cityTools.roadResult(command, notice);
        }
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
    private boolean cheatMode;
    private int cheatVehicle;
    private int cheatSpawnCount;
    private final List<Jeep> cheatParked = new ArrayList<>();
    private final List<Aviation.Plane> cheatPlanes = new ArrayList<>();

    private String cheatVehicleName() {
        int roads = dev.jayms.player.CargoVehicle.values().length;
        return cheatVehicle < roads ? dev.jayms.player.CargoVehicle.values()[cheatVehicle].label
                : cheatVehicle == roads ? "Passenger jet" : "Cargo carrier";
    }

    private void spawnCheatVehicle() {
        if (player.mounted() || jeep != null && jeep.driving()) {
            notice = "Exit your vehicle before spawning";
            return;
        }
        if (cheatSpawnCount >= 64) {
            notice = "Cheat vehicle limit: 64 per session";
            return;
        }
        var pos = player.position();
        var direction = player.facingDirection();
        direction.y = 0;
        if (direction.lengthSquared() < .001f) direction.set(0, 0, -1);
        direction.normalize();
        int roads = dev.jayms.player.CargoVehicle.values().length;
        float distance = cheatVehicle < roads ? dev.jayms.player.CargoVehicle.values()[cheatVehicle].halfLength + 4 : 16;
        var at = new Vector3f(pos).add(direction.mul(distance));
        int x = (int)Math.floor(at.x), z = (int)Math.floor(at.z);
        int y = (int)Math.floor(pos.y);
        while (y > Terrain.MIN_Y && world.isLoaded(x, y - 1, z) && world.getBlock(x, y - 1, z) == Blocks.AIR) y--;
        if (!world.isLoaded(x, y - 1, z) || world.getBlock(x, y - 1, z) == Blocks.AIR) {
            notice = "Spawn needs loaded ground nearby";
            return;
        }
        at.y = y + .01f;
        // Reject occupied or unloaded space before changing the current vehicle or world.
        int width = cheatVehicle < roads ? 3 : cheatVehicle == roads ? 18 : 4;
        int depth = cheatVehicle < roads ? (int)Math.ceil(dev.jayms.player.CargoVehicle.values()[cheatVehicle].halfLength * 2 + 2) : cheatVehicle == roads ? 22 : 6;
        int height = cheatVehicle < roads ? 4 : 8;
        for (int dx = -width; dx <= width; dx++) for (int dz = -depth; dz <= depth; dz++)
            for (int dy = 0; dy < height; dy++)
                if (!world.isLoaded(x + dx, y + dy, z + dz) || world.getBlock(x + dx, y + dy, z + dz) != Blocks.AIR) {
                    notice = "Clear a larger loaded area before spawning " + cheatVehicleName();
                    return;
                }
        var vehicles = new ArrayList<>(cheatParked);
        if (jeep != null) vehicles.add(jeep);
        for (var existing : vehicles) if (Math.abs(existing.position().x - at.x) < width + existing.type().halfLength
                && Math.abs(existing.position().z - at.z) < depth + existing.type().halfLength
                && Math.abs(existing.position().y - at.y) < height) {
            notice = "Spawn area overlaps a parked vehicle";
            return;
        }
        for (var plane : cheatPlanes) if (Math.abs(plane.x() - at.x) < width + 18 && Math.abs(plane.z() - at.z) < depth + 22) {
            notice = "Spawn area overlaps a passenger jet";
            return;
        }
        if (cheatVehicle < roads) {
            var fresh = new Jeep(at, player.yaw(), dev.jayms.player.CargoVehicle.values()[cheatVehicle]);
            if (fresh.collides(world, at)) { notice = "Vehicle spawn is obstructed"; return; }
            if (jeep != null) cheatParked.add(jeep);
            jeep = fresh;
        } else if (cheatVehicle == roads) {
            cheatPlanes.add(new Aviation.Plane(-100000 - cheatPlanes.size(), at.x, at.y, at.z, player.yaw(), 0, false));
        } else {
            for (var edit : Shipping.carrier(x, y, z)) {
                world.apply(edit);
                WorldVoxels.remember(local.edits, edit);
            }
        }
        cheatSpawnCount++;
        notice = "Spawned " + cheatVehicleName() + (cheatVehicle < roads ? "; approach cab and press " + Controls.keyName(controls.code(JEEP)) : "; static asset");
    }

    private int fps = -1, fpsFrames;
    private double fpsElapsed;

    public void run() throws Exception {
        run(null);
    }

    public void run(FrameObserver observer) throws Exception {
        Main active = this;
        while (true) {
            active.runOnce(observer);
            if (active.requestedSave == null) return;
            Main next = new Main();
            next.window = active.window;
            next.offlineSave = active.requestedSave;
            next.citySaves = active.citySaves;
            next.seed = active.seed;
            next.gameConfig = active.local.city.frame().config();
            next.productionCatalog = active.productionCatalog;
            active = next;
        }
    }

    private void runOnce(FrameObserver observer) throws Exception {
        frameObserver = observer;
        controls = new Controls(Controls.directory().resolve("controls.properties"));
        menu = new ControlsMenu(controls);
        if (window == null) {
            GLFWErrorCallback.createPrint(System.err).set();
            if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");
        }
        try {
            if (window == null) init();
            else reuseWindow();
            initScene();
            if (vehicleAudio == null) vehicleAudio = new dev.jayms.audio.VehicleAudio();
            if (frameObserver != null) frameObserver.started(this);
            loop();
        } finally {
            cleanup(requestedSave != null);
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
        configureFramebuffer();
        window.show();
    }

    private void reuseWindow() {
        window.setOpenGlContext();
        glfwSetWindowShouldClose(window.getHandle(), false);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glClearColor(.48f, .72f, .92f, 1);
        configureFramebuffer();
    }

    private void configureFramebuffer() {
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
    }

    private void initScene() throws Exception {
        shader = new ShaderProgram("shaders/voxel.vert", "shaders/voxel.frag");
        rendering = new dev.jayms.render.RenderPipeline();
        overlay = new Overlay();
        camera = new Camera();
        playerModel = new PlayerModel();
        horseModel = new HorseModel();
        planeModel = new PlaneModel();
        railwayModel = new RailwayModel();
        shipModel = new ShipModel();
        farmModels = new FarmModels();
        if (network == null) {
            local = new LocalGame(offlineSave, seed);
            seed = local.seed;
        } else seed = network.seed;
        int generatorVersion = network == null ? local.generatorVersion : network.generatorVersion;
        world = new World(seed, network == null ? local.models : network.models, generatorVersion);
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
        if (local != null) {
            jeep = Jeep.load(jeepSave(), world, player.position());
            jeepModel = new JeepModel();
            world.stream(player.position().x, player.position().z, 9);
        }
        distant = new DistantTerrainRenderer(seed, generatorVersion);
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
                    }, productionCatalog);
        if (city().config().city()) {
            isometric = true;
            overview.cityMode();
            overview.focus(16, 26, player.position().y);
        }
        player.resolvePenetration(world);
        if (local != null && city().config().city()) {
            if (citySaves == null) citySaves = new CitySaves(offlineSave);
            menu.saves = new SavesMenu(citySaves, offlineSave, new SavesMenu.Actions() {
                public void save() throws Exception { saveCurrentSimulation(); }
                public void load(java.nio.file.Path path) throws Exception {
                    CitySaves.validate(path, seed);
                    saveCurrentSimulation();
                    requestedSave = path;
                    glfwSetWindowShouldClose(window.getHandle(), true);
                }
                public void create(String name, boolean copy) throws Exception {
                    saveCurrentSimulation();
                    java.nio.file.Path path = citySaves.create(name, copy ? offlineSave : null, seed);
                    load(path);
                }
            });
        }
        configureInput();
        setCaptured(true);
    }

    private void saveCurrentSimulation() throws java.io.IOException {
        local.save();
        if (jeep != null) jeep.save(jeepSave());
    }

    private boolean recordInput(int code) {
        if (!controls.matches(RECORD, code) || menu.open && menu.editing()) return false;
        if (code >= GLFW_KEY_SPACE
                && code < GLFW_KEY_ESCAPE
                && (menu.open && menu.saves != null && menu.saves.open
                        || mayorDashboard.open && mayorDashboard.searchFocus
                        || editor.open
                        || lightColors.open)) return false;
        recorder.toggle(framebufferWidth, framebufferHeight);
        return true;
    }

    private void configureInput() {
        mayorDashboard.submit = this::cityCommand;
        glfwSetKeyCallback(
                window.getHandle(),
                (handle, key, scancode, action, mods) -> {
                    if (action == GLFW_PRESS && recordInput(key)) return;
                    if (buildingInfo.open) {
                        buildingInfo.key(key, action);
                        if (!buildingInfo.open) setCaptured(true);
                        return;
                    }
                    if (mayorDashboard.open) {
                        if (action == GLFW_PRESS
                                && (key == GLFW_KEY_ESCAPE
                                        || controls.matches(MAYOR_DASHBOARD, key)
                                                && (!mayorDashboard.searchFocus
                                                        || key >= GLFW_KEY_F1
                                                                && key <= GLFW_KEY_F25))) {
                            mayorDashboard.close();
                            setCaptured(true);
                        } else mayorDashboard.key(key, action);
                        return;
                    }
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
                    if (orbitDrag.active()) {
                        if (canOrbit()) orbitDrag.move(x, controls.sensitivity, overview);
                        else setCaptured(false);
                    }
                    if (captured && !firstMouse && jeep != null && jeep.driving())
                        jeep.mouse(world, (float)(x-mouseX)*controls.sensitivity);
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
                    if (button == GLFW_MOUSE_BUTTON_RIGHT
                            && action == GLFW_RELEASE
                            && orbitDrag.active()) {
                        setCaptured(false);
                        return;
                    }
                    if (action == GLFW_PRESS && recordInput(-button - 1)) return;
                    if (button == GLFW_MOUSE_BUTTON_RIGHT && action == GLFW_PRESS && canOrbit()) {
                        orbitDrag.begin();
                        glfwSetInputMode(handle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                        return;
                    }
                    if (buildingInfo.open) {
                        if (action == GLFW_PRESS && button == GLFW_MOUSE_BUTTON_LEFT) {
                            int[] size = window.getSize();
                            buildingInfo.click(
                                    (float) mouseX * framebufferWidth / size[0],
                                    (float) mouseY * framebufferHeight / size[1],
                                    framebufferWidth,
                                    framebufferHeight,
                                    city(),
                                    this::cityCommand);
                            if (!buildingInfo.open) setCaptured(true);
                        }
                        return;
                    }
                    if (mayorDashboard.open) {
                        if (action == GLFW_PRESS) {
                            if (button != GLFW_MOUSE_BUTTON_LEFT
                                    && controls.matches(MAYOR_DASHBOARD, -button - 1)) {
                                mayorDashboard.close();
                                setCaptured(true);
                                return;
                            }
                            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                int[] size = window.getSize();
                                mayorDashboard.click(
                                        (float) mouseX * framebufferWidth / size[0],
                                        (float) mouseY * framebufferHeight / size[1],
                                        framebufferWidth,
                                        framebufferHeight,
                                        city(),
                                        this::inspectCitizen);
                                if (!mayorDashboard.open) setCaptured(true);
                            }
                        }
                        return;
                    }
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
                        if (cityTools.tool == -1
                                && (cityTools.selectedBuilding != 0
                                        || cityTools.selectedPlot != 0)) {
                            buildingInfo.show(cityTools.selectedBuilding, cityTools.selectedPlot);
                            setCaptured(false);
                        }
                        if (cityTools.dashboardRequested) {
                            cityTools.dashboardRequested = false;
                            openMayorDashboard();
                        }
                        return;
                    }
                    if (!isometric
                            && city().config().city()
                            && button == GLFW_MOUSE_BUTTON_LEFT
                            && (mods & GLFW_MOD_ALT) != 0) {
                        if (cityTools.selectRay(
                                player.eyePosition(), player.facingDirection(), 12, city())) {
                            buildingInfo.show(cityTools.selectedBuilding, cityTools.selectedPlot);
                            setCaptured(false);
                        }
                        return;
                    }
                    input(-button - 1);
                });
        glfwSetCharCallback(
                window.getHandle(),
                (handle, character) -> {
                    if (menu.open && menu.saves != null && menu.saves.open) menu.saves.character(character);
                    else if (mayorDashboard.open) mayorDashboard.character(character);
                    else if (editor.open) editor.character(character);
                    else if (lightColors.open) lightColors.character(character);
                });
        glfwSetScrollCallback(
                window.getHandle(),
                (handle, x, y) -> {
                    if (buildingInfo.open) {
                        buildingInfo.scroll(y);
                        return;
                    }
                    if (mayorDashboard.open) {
                        mayorDashboard.scroll(y);
                        return;
                    }
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
                        setCaptured(false);
                        inventoryHud.close();
                        if (editor.open
                                || lightColors.open
                                || mayorDashboard.open
                                || buildingInfo.open) return;
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

    private void openMayorDashboard() {
        if (!city().config().city()) {
            notice = "Join Voxel City One to open the mayor dashboard.";
            return;
        }
        inventoryHud.close();
        mayorDashboard.show();
        setCaptured(false);
    }

    private void inspectCitizen(int id) {
        var citizen = city().visibleCitizens().stream().filter(c -> c.id() == id).findFirst().orElse(null);
        if (citizen == null) return;
        mayorDashboard.close();
        cityTools.key(GLFW_KEY_ESCAPE, this::cityCommand);
        cityTools.selectedCitizen = id;
        cityTools.selectedBuilding = cityTools.selectedPlot = 0;
        isometric = true;
        overview.focus(citizen.x(), citizen.z(), citizen.y());
        setCaptured(false);
    }

    private void input(int code) {
        if (controls.matches(CHEATS, code)) {
            if (network != null || local == null) { notice = "Cheat mode is available offline only"; return; }
            if (player.mounted() || jeep != null && jeep.driving()) { notice = "Exit your vehicle before changing cheat mode"; return; }
            cheatMode = !cheatMode;
            if (player.flying() != cheatMode) player.toggleFlight();
            notice = cheatMode ? "Cheat mode enabled: flight on" : "Cheat mode disabled: flight off";
            return;
        }
        if (controls.matches(CHEAT_SELECT, code) || controls.matches(CHEAT_SPAWN, code) || controls.matches(CHEAT_MONEY, code)) {
            if (!cheatMode || network != null || local == null) { notice = "Enter offline cheat mode first"; return; }
            if (controls.matches(CHEAT_SELECT, code)) {
                cheatVehicle = (cheatVehicle + 1) % (dev.jayms.player.CargoVehicle.values().length + 2);
                notice = "Selected " + cheatVehicleName();
            } else if (controls.matches(CHEAT_SPAWN, code)) spawnCheatVehicle();
            else if (!city().config().city()) notice = "City budget is available in Voxel City One";
            else if (local.city.economy.budget > 990000000) notice = "Cheat budget limit reached";
            else { local.city.economy.budget += 10000; notice = "Added $10,000 to city budget"; }
            return;
        }

        if (controls.matches(JEEP, code) && captured && jeep != null) {
            if (glfwGetKey(window.getHandle(), GLFW_KEY_LEFT_SHIFT)==GLFW_PRESS || glfwGetKey(window.getHandle(), GLFW_KEY_RIGHT_SHIFT)==GLFW_PRESS) {
                notice=jeep.cycleBody(world,player) ? "Vehicle: "+jeep.type().label : "Stop, exit, and leave clear space around the vehicle to change body";
                return;
            }
            if (!jeep.driving()) {
                Jeep nearest = jeep;
                for (var parked : cheatParked) if (player.position().distance(parked.seat()) < player.position().distance(nearest.seat())) nearest = parked;
                if (nearest != jeep) { cheatParked.remove(nearest); cheatParked.add(jeep); jeep = nearest; }
            }
            boolean ok = jeep.driving() ? jeep.exit(world, player) : jeep.enter(player);
            notice = ok ? (jeep.driving() ? "Driving "+jeep.type().label+": WASD + mouse, Ctrl boost, vehicle key exits when stopped" : "Left "+jeep.type().label)
                    : "Move within 4 blocks of the cab to enter; stop and leave room beside the vehicle to exit";
            return;
        }
        if (controls.matches(MAYOR_DASHBOARD, code)) {
            openMayorDashboard();
            return;
        }

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
            if (controls.matches(ROTATE_LEFT, code)) {
                overview.rotate(-1);
                return;
            }
            if (controls.matches(ROTATE_RIGHT, code)) {
                overview.rotate(1);
                return;
            }
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
            if (jeep != null && jeep.driving() && (controls.matches(FLY, code)
                    || controls.matches(BREAK, code) || controls.matches(PLACE, code))) return;
            if (controls.matches(FLY, code)) player.toggleFlight();
            else if (controls.matches(VIEW, code)) player.toggleView();
            else if (controls.matches(BREAK, code)) interact(false);
            else if (controls.matches(PLACE, code)) interact(true);
        }
    }

    private boolean canOrbit() {
        return isometric
                && !menu.open
                && !inventoryHud.open
                && !editor.open
                && !lightColors.open
                && !mayorDashboard.open
                && !buildingInfo.open;
    }

    private void setCaptured(boolean value) {
        orbitDrag.end();
        value =
                value
                        && !isometric
                        && (editor == null || !editor.open)
                        && (lightColors == null || !lightColors.open)
                        && !mayorDashboard.open
                        && !buildingInfo.open;
        captured = value;
        firstMouse = true;
        glfwSetInputMode(
                window.getHandle(), GLFW_CURSOR, value ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
    }

    private void loop() throws Exception {
        double previous = glfwGetTime();
        while (!window.shouldClose()) {
            if (frameObserver != null) frameObserver.beforeFrame(this);
            double now = glfwGetTime();
            double frameElapsed = now - previous;
            float dt = (float) frameElapsed;
            fpsElapsed += frameElapsed;
            if (fpsElapsed >= .5) {
                fps = (int) Math.round(fpsFrames / fpsElapsed);
                fpsFrames = 0;
                fpsElapsed = 0;
            }
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
            if (local != null && !(menu.open && menu.saves != null && menu.saves.open)) local.city.advance(Math.min(dt, .25));
            var horse = riding();
            player.mount(
                    horse != null,
                    horse == null ? null : new Vector3f(horse.x(), horse.y(), horse.z()));
            if (isometric
                    && city().config().city()
                    && !menu.open
                    && !inventoryHud.open
                    && !editor.open
                    && !lightColors.open
                    && !mayorDashboard.open
                    && !buildingInfo.open) {
                float speed = Math.min(dt, .1f) * 50 / Math.max(.25f, overview.zoom() / 64);
                float
                        f =
                                (controls.down(window.getHandle(), FORWARD) ? 1 : 0)
                                        - (controls.down(window.getHandle(), BACKWARD) ? 1 : 0),
                        r =
                                (controls.down(window.getHandle(), RIGHT) ? 1 : 0)
                                        - (controls.down(window.getHandle(), LEFT) ? 1 : 0);
                if (f != 0 || r != 0) overview.panRelative(r * speed, f * speed);
            }
            var location = player.position();
            float streamX = location.x, streamZ = location.z;
            if (isometric && city().config().city() && overview.focused()) {
                streamX = overview.focusX();
                streamZ = overview.focusZ();
            }
            if (local != null && !mayorDashboard.open)
                local.city.population.focusNear(streamX, streamZ);
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
            if (jeep != null && player.position().distance(jeep.position()) < 64) {
                jeep.step(world, dt, forward, right, captured && (glfwGetKey(window.getHandle(), GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS
                        || glfwGetKey(window.getHandle(), GLFW_KEY_RIGHT_CONTROL) == GLFW_PRESS));
            }
            if (jeep != null && jeep.driving()) player.driveSeat(jeep.seat(), jeep.yaw());
            else player.step(
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
                    if (jeep != null) jeep.releaseDriver();
                    float y = world.terrain().column(8, 24).height() + 1.01f;
                    world.stream(8, 24, 9);
                    player = new Player(new Vector3f(8.5f, y, 24.5f), -90, -20, camera);
                    notice = "You respawned. Your inventory was kept.";
                }
                if (now >= nextSave) {
                    local.save();
                    if (jeep != null) jeep.save(jeepSave());
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
                                    : jeep != null && jeep.driving() ? "Driving "+jeep.type().label+" | " + player.cameraView()
                                    : player.mounted()
                                            ? "Riding horse | " + player.cameraView()
                                            : player.flying()
                                                    ? "Flying | " + player.cameraView()
                                                    : "Walking | " + player.cameraView())
                            + " | "
                            + (buildingInfo.open
                                    ? "Building information"
                                    : mayorDashboard.open
                                            ? "Mayor dashboard"
                                            : editor.open
                                                    ? "Model editor"
                                                    : menu.open
                                                            ? "Controls menu"
                                                            : inventoryHud.open
                                                                    ? "Inventory"
                                                                    : "Esc controls"));
            if (editor.open) glClearColor(.025f, .045f, .075f, 1);
            else glClearColor(.48f, .72f, .92f, 1);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            Vector3f audioListener = new Vector3f(player.position());
            if (isometric && overview.focused()) audioListener.set(overview.focusX(), overview.focusY(), overview.focusZ());
            vehicleAudio.update(jeep, city(), audioListener, menu.open || editor.open
                    || framebufferWidth == 0 || framebufferHeight == 0);
            if (framebufferWidth > 0 && framebufferHeight > 0) {
                renderCity = network == null ? local.city.frame() : network.city;
                try { render(); renderOverlay(); }
                finally { renderCity = null; }
                if (frameObserver != null) frameObserver.afterFrame(this);
                recorder.capture(framebufferWidth, framebufferHeight);
                fpsFrames++;
            }
            window.swapBuffers();
            window.pollEvents();
        }
    }

    private void render() {
        if (mayorDashboard.open) return;
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
        if (jeep != null) jeepModel.render(jeep, shader);
        for (var parked : cheatParked) jeepModel.render(parked, shader);
        for (var plane : cheatPlanes) planeModel.render(plane, shader);
        for (ItemDrop drop : drops().values())
            if (player.position().distanceSquared(drop.x(), drop.y(), drop.z()) < 10000)
                if (Blocks.isModel(drop.type()))
                    modelRenderer.renderDrop(drop, (float) glfwGetTime(), shader);
                else playerModel.renderDrop(drop, (float) glfwGetTime(), shader);
        farmModels.crops(city().agriculture(), shader);
        for (var cow : city().agriculture().cows()) {
            var pose =
                    new Protocol.Pose(
                            cow.id(),
                            cow.x(),
                            cow.y(),
                            cow.z(),
                            cow.yaw(),
                            0,
                            cow.phase(),
                            1,
                            false);
            if (network != null && network.cows.containsKey(cow.id()))
                pose = network.cows.get(cow.id()).sample(System.nanoTime());
            if (pose != null) farmModels.cow(cow, pose, shader);
        }
        for (var plane : Aviation.planes(city())) planeModel.render(plane, shader);
        railwayModel.render(city(), shader);
        for (var ship : shippingRoutes.ships(city(), world.terrain())) shipModel.render(ship, shader);
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
        for (var c : city().visibleCitizens()) {
            if (c.activity().startsWith("Flying") || c.activity().startsWith("Boarding")) continue;
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
                playerModel.renderCitizen(
                        p,
                        c.cohort(),
                        c.horse() != 0,
                        c.activity(),
                        city().elapsed()
                                + (network == null || !network.connected()
                                        ? 0
                                        : Math.min(
                                                .3,
                                                (System.nanoTime() - network.cityReceived) / 1e9)),
                        shader,
                        modelRenderer);
        }
        if (isometric || player.thirdPerson()) {
            if (player.mounted() || (jeep != null && jeep.driving())) playerModel.renderRider(player.pose(0), shader, modelRenderer);
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
        if (jeep != null) jeepModel.glass(jeep, shader);
        for (var parked : cheatParked) jeepModel.glass(parked, shader);
        if (!isometric && !player.thirdPerson() && !(jeep != null && jeep.driving())) {
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
        if (city().config().city() && (network == null || network.connected()))
            mayorDashboard.history.observe(city());
        overlay.begin(framebufferWidth, framebufferHeight);
        if (cheatMode) overlay.text("CHEAT MODE | " + Controls.keyName(controls.code(CHEAT_SELECT)) + ": select " + cheatVehicleName()
                + " | " + Controls.keyName(controls.code(CHEAT_SPAWN)) + ": spawn | " + Controls.keyName(controls.code(CHEAT_MONEY))
                + ": +$10,000 | " + Controls.keyName(controls.code(CHEATS)) + ": leave", 20, framebufferHeight - 158, 1f, 1, .8f, .3f, 1);
        if (mayorDashboard.open) {
            mayorDashboard.render(
                    overlay,
                    framebufferWidth,
                    framebufferHeight,
                    city(),
                    Controls.keyName(controls.code(MAYOR_DASHBOARD)),
                    network == null || network.connected());
            if (notice != null && !notice.isEmpty())
                overlay.text(
                        notice.length() > 120 ? notice.substring(0, 120) : notice, 24, 126, 1f);
            renderFps();
            overlay.end();
            return;
        }
        if (buildingInfo.open) {
            buildingInfo.render(overlay, framebufferWidth, framebufferHeight, city());
            renderFps();
            overlay.end();
            return;
        }
        if (editor.open) {
            editor.render(overlay, framebufferWidth, framebufferHeight);
            renderFps();
            overlay.end();
            return;
        }
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
                                : jeep != null && jeep.driving() ? "DRIVING "+jeep.type().label+" | " + Controls.keyName(controls.code(JEEP)) + ": exit | " + Math.round(Math.abs(jeep.speed())*3.6f) + " km/h"
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
                                + ": fit | "
                                + Controls.keyName(controls.code(ROTATE_LEFT))
                                + " / "
                                + Controls.keyName(controls.code(ROTATE_RIGHT))
                                + ": rotate | Hold RMB + drag: orbit"
                        : jeep != null && jeep.driving() ? "W/S: drive / reverse | A/D + mouse: steer | Ctrl: boost"
                        : jeep != null && player.position().distance(jeep.position()) <= 5 ? Controls.keyName(controls.code(JEEP)) + ": enter "+jeep.type().label+" | Shift + vehicle key: change body | Esc: controls | " + Controls.keyName(controls.code(VIEW)) + ": camera"
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
        {
            cityTools.hover((float) mouseX * framebufferWidth / windowSize[0],
                    (float) mouseY * framebufferHeight / windowSize[1],
                    framebufferWidth, framebufferHeight, projection, view, city());
            cityTools.render(
                    overlay,
                    framebufferWidth,
                    framebufferHeight,
                    projection,
                    view,
                    city(),
                    isometric);
        }
        menu.render(overlay, framebufferWidth, framebufferHeight);
        lightColors.render(overlay, framebufferWidth, framebufferHeight);
        renderFps();
        overlay.end();
    }

    private void renderFps() {
        String label = fps < 0 ? "FPS --" : "FPS " + fps;
        float scale = 1.6f, width = Math.max(100, overlay.textWidth(label, scale) + 20);
        float x = framebufferWidth - width - 12;
        overlay.rectangle(x, 12, width, 30, .015f, .035f, .065f, .85f);
        overlay.text(label, x + 10, 21, scale);
        String recording = recorder.status(Controls.keyName(controls.code(RECORD)));
        if (!recording.isEmpty()) {
            float rw = overlay.textWidth(recording, 1.3f) + 20;
            float rx = (framebufferWidth - rw) / 2f;
            overlay.rectangle(rx, 12, rw, 30, .3f, .025f, .035f, .95f);
            overlay.text(recording, rx + 10, 22, 1.3f);
        }
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
        if (jeep != null && jeep.driving()) return;
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

    private void cleanup(boolean keepWindow) throws Exception {
        if (vehicleAudio != null) vehicleAudio.close();
        recorder.close();
        if (local != null) local.save();
        if (jeep != null) jeep.save(jeepSave());
        if (network != null) network.close();
        if (distant != null) distant.close();
        if (world != null) world.close();
        if (playerModel != null) playerModel.close();
        if (horseModel != null) horseModel.close();
        if (planeModel != null) planeModel.close();
        if (railwayModel != null) railwayModel.close();
        if (shipModel != null) shipModel.close();
        if (jeepModel != null) jeepModel.close();
        farmModels.close();
        if (modelRenderer != null) modelRenderer.close();
        if (editor != null) editor.close();
        if (overlay != null) overlay.close();
        if (rendering != null) rendering.close();
        if (shader != null) shader.close();
        // Retire scene resources before another simulation uses the same context.
        if (overlay != null) glFinish();
        if (window != null) {
            if (keepWindow) org.lwjgl.glfw.Callbacks.glfwFreeCallbacks(window.getHandle());
            else window.destroy();
        }
        if (!keepWindow) {
            glfwTerminate();
            var error = glfwSetErrorCallback(null);
            if (error != null) error.free();
        }
    }

    public static void main(String[] args) throws Exception {
        String host = null, pin = null;
        int port = Protocol.PORT;
        boolean offline = false, cityGame = false, gameExplicit = false;
        double daySeconds = 1200, startHour = 8;
        boolean cycle = true;
        long seed = Terrain.DEFAULT_SEED;
        java.nio.file.Path productionFile = null;
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
                case "--pedestrian-spacing", "--mounted-spacing" ->
                    RoadSpacing.configure(args[i], args[++i]);
                case "--production-config" -> productionFile = java.nio.file.Path.of(args[++i]);
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
                                        + " --world FILE --seed NUMBER --production-config FILE --pedestrian-spacing BLOCKS --mounted-spacing BLOCKS");
            }
        Main game = new Main();
        if (productionFile != null) game.productionCatalog = ProductionCatalog.load(productionFile);
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
