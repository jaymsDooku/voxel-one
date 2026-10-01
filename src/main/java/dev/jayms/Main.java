package dev.jayms;

import static dev.jayms.ui.Controls.Action.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

import dev.jayms.net.*;
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
    private Camera camera;
    private Player player;
    private PlayerModel playerModel;
    private World world;
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

    private void initScene() {
        shader = new ShaderProgram("shaders/voxel.vert", "shaders/voxel.frag");
        overlay = new Overlay();
        camera = new Camera();
        playerModel = new PlayerModel();
        world = new World();
        for (int x = -4; x <= 4; x++)
            for (int z = -4; z <= 4; z++)
                for (int y = -2; y <= 2; y++) {
                    ChunkPos p = new ChunkPos(x, y, z);
                    world.addChunk(p, ChunkGenerator.generate(p));
                }
        player = new Player(new Vector3f(8.5f, Protocol.spawnY(), 24.5f), -90, -20, camera);
        if (network != null) {
            for (var e : network.initialEdits) world.setBlock(e.x(), e.y(), e.z(), e.type());
            var p = network.spawn;
            player = new Player(new Vector3f(p.x(), p.y(), p.z()), p.yaw(), p.pitch(), camera);
        }
        player.resolvePenetration(world);
        configureInput();
        setCaptured(true);
    }

    private void configureInput() {
        glfwSetKeyCallback(
                window.getHandle(),
                (handle, key, scancode, action, mods) -> {
                    if (menu.open) {
                        menu.key(key, action);
                        if (!menu.open) setCaptured(true);
                        return;
                    }
                    if (action == GLFW_PRESS) input(key);
                });
        glfwSetCursorPosCallback(
                window.getHandle(),
                (handle, x, y) -> {
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
                    if (action != GLFW_PRESS) return;
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
                    input(-button - 1);
                });
        glfwSetWindowFocusCallback(
                window.getHandle(),
                (handle, focused) -> {
                    if (!focused) {
                        menu.open = true;
                        setCaptured(false);
                    }
                });
    }

    private void input(int code) {
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
        captured = value;
        firstMouse = true;
        glfwSetInputMode(
                window.getHandle(), GLFW_CURSOR, value ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
    }

    private void loop() {
        double previous = glfwGetTime();
        while (!window.shouldClose()) {
            double now = glfwGetTime();
            float dt = (float) (now - previous);
            previous = now;
            // World changes are applied before physics, including collision recovery for late
            // edits.
            if (network != null) {
                for (var e : network.poll()) world.setBlock(e.x(), e.y(), e.z(), e.type());
                predicted.entrySet().removeIf(e -> !network.pending(e.getValue()));
                if (!network.connected()) {
                    for (var e : predicted.values()) world.setBlock(e.x(), e.y(), e.z(), e.type());
                    predicted.clear();
                }
                if (!network.notice().isEmpty()) notice = network.notice();
            }
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
                            + (player.flying() ? "Flying" : "Walking")
                            + " | "
                            + (menu.open ? "Controls menu" : "Esc controls"));
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
        projection
                .identity()
                .perspective(
                        (float) Math.toRadians(70),
                        (float) framebufferWidth / framebufferHeight,
                        .1f,
                        300);
        view.set(camera.createViewMatrix());
        frustum.set(new Matrix4f(projection).mul(view));
        shader.bind();
        shader.setMatrix4("uProjection", projection);
        shader.setMatrix4("uView", view);
        shader.setVector3("uLightDirection", -.4f, -1, -.3f);
        shader.setVector3("uColor", .35f, .70f, .25f);
        for (var entry : world.getLoadedChunks().entrySet()) {
            ChunkPos p = entry.getKey();
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
            c.checkMesh();
            c.getMesh().render();
        }
        if (player.thirdPerson()) playerModel.render(player, shader);
        if (network != null)
            for (var remote : network.remotePlayers.values()) {
                var p = remote.sample(System.nanoTime());
                if (p != null)
                    playerModel.render(
                            new Vector3f(p.x(), p.y(), p.z()),
                            p.yaw(),
                            p.pitch(),
                            p.walkPhase(),
                            p.walkAmount(),
                            shader);
            }
    }

    private void renderOverlay() {
        overlay.begin(framebufferWidth, framebufferHeight);
        if (network != null)
            for (var remote : network.remotePlayers.values()) {
                var p = remote.sample(System.nanoTime());
                if (p != null) nameplate(remote.name, new Vector3f(p.x(), p.y() + 2.15f, p.z()));
            }
        if (player.thirdPerson())
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
                        + (player.flying() ? "FLYING" : "WALKING"),
                22,
                22,
                1.8f);
        overlay.text(
                "Esc: controls | "
                        + Controls.keyName(controls.code(FLY))
                        + ": flight | "
                        + Controls.keyName(controls.code(VIEW))
                        + ": camera",
                22,
                44,
                1.5f);
        if (!notice.isEmpty())
            overlay.text(notice, 20, framebufferHeight - 28, 1.4f, 1, .8f, .4f, 1);
        menu.render(overlay, framebufferWidth, framebufferHeight);
        overlay.end();
    }

    private void nameplate(String name, Vector3f position) {
        float distance = camera.position().distance(position);
        if (distance > 70) return;
        Vector4f clip = new Vector4f(position, 1);
        view.transform(clip);
        projection.transform(clip);
        if (clip.w <= 0 || clip.z < -clip.w || clip.z > clip.w) return;
        float x = (clip.x / clip.w * .5f + .5f) * framebufferWidth,
                y = (.5f - clip.y / clip.w * .5f) * framebufferHeight;
        float scale = Math.max(1.3f, Math.min(2.3f, 16 / Math.max(1, distance)));
        float width = overlay.textWidth(name, scale);
        overlay.rectangle(x - width / 2 - 9, y - 6, width + 18, 22 * scale, .03f, .18f, .24f, .5f);
        overlay.rectangle(x - width / 2 - 9, y - 6, width + 18, 1, .2f, .9f, 1, .8f);
        overlay.text(name, x - width / 2 + 1, y + 1, scale, .1f, .75f, 1, .5f);
        overlay.text(name, x - width / 2, y, scale, .65f, .97f, 1, .95f);
    }

    private void interact(boolean place) {
        if (network != null && !network.connected()) {
            notice = "Disconnected: reconnect to edit the world.";
            return;
        }
        BlockHit hit = BlockRaycaster.cast(world, player.eyePosition(), camera.getDirection(), 6);
        if (hit == null) return;
        int x = hit.x() + (place ? hit.normalX() : 0),
                y = hit.y() + (place ? hit.normalY() : 0),
                z = hit.z() + (place ? hit.normalZ() : 0);
        if (!world.isLoaded(x, y, z)
                || place && world.getBlock(x, y, z) != 0
                || place && player.overlaps(x, y, z)) return;
        Protocol.Edit edit =
                new Protocol.Edit(x, y, z, place ? ChunkGenerator.STONE : ChunkGenerator.AIR);
        if (network == null) world.setBlock(x, y, z, edit.type());
        else if (network.edit(edit, player.pose(network.id))) {
            predicted.put(edit.key(), new Protocol.Edit(x, y, z, world.getBlock(x, y, z)));
            // Reserve it immediately so movement cannot enter an unconfirmed solid block.
            world.setBlock(x, y, z, edit.type());
        }
    }

    private void cleanup() throws Exception {
        if (network != null) network.close();
        if (world != null) world.close();
        if (playerModel != null) playerModel.close();
        if (overlay != null) overlay.close();
        if (shader != null) shader.close();
        if (window != null) window.destroy();
        glfwTerminate();
        var error = glfwSetErrorCallback(null);
        if (error != null) error.free();
    }

    public static void main(String[] args) throws Exception {
        String host = null, pin = null;
        int port = Protocol.PORT;
        boolean offline = false;
        for (int i = 0; i < args.length; i++)
            switch (args[i]) {
                case "--server" -> host = args[++i];
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--fingerprint" -> pin = args[++i];
                case "--offline" -> offline = true;
                default ->
                        throw new IllegalArgumentException(
                                "Usage: --server HOST --port PORT --fingerprint SHA256 --offline");
            }
        Main game = new Main();
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
            game.run();
        } finally {
            if (game.network != null) game.network.close();
        }
    }
}
