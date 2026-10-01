package dev.jayms;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.

import dev.jayms.player.Player;
import dev.jayms.player.PlayerModel;
import dev.jayms.window.Window;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.Version;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.util.List;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class Main {

    private Window window;

    private dev.jayms.net.MultiplayerClient network;
    private double nextNetworkUpdate;
    private boolean captured = true;
    private ShaderProgram shader;
    private Camera camera;
    private Player player;
    private PlayerModel playerModel;
    private World world;

    private int framebufferHeight = 720;
    private int framebufferWidth = 1280;

    public void run() throws Exception {
        System.out.println("Hello LWJGL " + Version.getVersion() + "!");

        init();
        initScene();
        try { loop(); } finally { cleanup(); }
    }

    private void cleanup() throws Exception {
        world.close();
        playerModel.close();
        if (network != null) network.close();
        shader.close();

        window.destroy();

        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    private void init() {
        // Setup an error callback. The default implementation
        // will print the error message in System.err.
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if ( !glfwInit() )
            throw new IllegalStateException("Unable to initialize GLFW");

        window = new Window(framebufferWidth, framebufferHeight, "Voxel One", false, true);
        window.registerKeyListener(GLFW_KEY_F5, GLFW_PRESS, () -> player.toggleView());
        window.registerKeyListener(GLFW_KEY_TAB, GLFW_PRESS, () -> {
            captured = !captured;
            glfwSetInputMode(window.getHandle(), GLFW_CURSOR, captured ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
        });
        window.init();
        window.center();
        window.setOpenGlContext();
        window.vSync();

        window.show();

        GL.createCapabilities();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        glClearColor(0.48f, 0.72f, 0.92f, 1.0f);
        glViewport(0, 0, framebufferWidth, framebufferHeight);

        glfwSetFramebufferSizeCallback(
                window.getHandle(),
                (handle, width, height) -> {
                    framebufferWidth = width;
                    framebufferHeight = height;
                    glViewport(0, 0, width, height);
                }
        );
    }

    private void initScene() {
        shader = new ShaderProgram(
                "src/main/resources/shaders/voxel.vert",
                "src/main/resources/shaders/voxel.frag"
        );

        camera = new Camera();
        player = new Player(new Vector3f(8.0f, dev.jayms.net.Protocol.spawnY(), 24.0f), -90.0f, -20.0f, camera);
        playerModel = new PlayerModel();

        world = new World();
        for (int chunkX = -4; chunkX <= 4; chunkX++) {
            for (int chunkZ = -4; chunkZ <= 4; chunkZ++) {
                for (int chunkY = -2; chunkY <= 2; chunkY++) {
                    ChunkPos position =
                            new ChunkPos(chunkX, chunkY, chunkZ);

                    world.addChunk(
                            position,
                            ChunkGenerator.generate(position)
                    );
                }
            }
        }

        if (network != null) {
            var p = network.spawn;
            player = new Player(new Vector3f(p.x(), p.y(), p.z()), p.yaw(), p.pitch(), camera);
            for (var edit : network.initialEdits) world.setBlock(edit.x(), edit.y(), edit.z(), edit.type());
        }
        configureMouse();
    }

    private void loop() {
        double previousTime = glfwGetTime();

        while (!window.shouldClose()) {
            double currentTime = glfwGetTime();

            float deltaTime =
                    (float) (currentTime - previousTime);

            previousTime = currentTime;

            if (network != null) {
                for (var edit : network.poll()) world.setBlock(edit.x(), edit.y(), edit.z(), edit.type());
                if (currentTime >= nextNetworkUpdate) {
                    var p = player.position();
                    network.move(new dev.jayms.net.Protocol.Pose(network.id, p.x, p.y, p.z, player.yaw(), player.pitch()));
                    nextNetworkUpdate = currentTime + .05;
                    glfwSetWindowTitle(window.getHandle(), "Voxel One | " + network.status() + " | F5 view | Tab cursor | Esc quit");
                }
            }
            if (captured) player.update(window.getHandle(), deltaTime, world);
            else player.step(world, deltaTime, 0, 0, false, false);
            player.updateCamera(world);

            glClear(
                    GL_COLOR_BUFFER_BIT
                            | GL_DEPTH_BUFFER_BIT
            );

            if (framebufferWidth > 0 && framebufferHeight > 0) { render(); renderCrosshair(); }

            window.swapBuffers();
            window.pollEvents();
        }
    }

    private void render() {
        float aspectRatio =
                (float) framebufferWidth
                        / framebufferHeight;

        Matrix4f projection = new Matrix4f()
                .perspective(
                        (float) Math.toRadians(70.0),
                        aspectRatio,
                        0.1f,
                        1000.0f
                );

        Matrix4f view =
                camera.createViewMatrix();

        Matrix4f model =
                new Matrix4f();

        shader.bind();

        shader.setMatrix4(
                "uProjection",
                projection
        );

        shader.setMatrix4(
                "uView",
                view
        );

        shader.setMatrix4(
                "uModel",
                model
        );

        shader.setVector3(
                "uLightDirection",
                -0.4f,
                -1.0f,
                -0.3f
        );

        for (var entry : world.getLoadedChunks().entrySet()) {
            ChunkPos position = entry.getKey();

            Matrix4f chunkModel = new Matrix4f().translation(
                    position.chunkX() * Chunk.WIDTH,
                    position.chunkY() * Chunk.HEIGHT,
                    position.chunkZ() * Chunk.LENGTH
            );

            shader.setMatrix4(
                    "uModel",
                    chunkModel
            );
            Chunk chunk = entry.getValue();
            chunk.checkMesh();
            chunk.getMesh().render();
        }
        if (player.thirdPerson()) playerModel.render(player, shader);
        if (network != null) for (var p : network.players.values()) playerModel.render(new Vector3f(p.x(), p.y(), p.z()), p.yaw(), shader);
    }

    private void configureMouse() {
        glfwSetInputMode(
                window.getHandle(),
                GLFW_CURSOR,
                GLFW_CURSOR_DISABLED
        );

        final boolean[] firstMouse = {true};
        final double[] previousMouseX = {0};
        final double[] previousMouseY = {0};

        glfwSetCursorPosCallback(
                window.getHandle(),
                (handle, mouseX, mouseY) -> {
                    if (!captured) { firstMouse[0] = true; return; }
                    if (firstMouse[0]) {
                        previousMouseX[0] = mouseX;
                        previousMouseY[0] = mouseY;
                        firstMouse[0] = false;
                        return;
                    }

                    double deltaX =
                            mouseX - previousMouseX[0];

                    double deltaY =
                            previousMouseY[0] - mouseY;

                    previousMouseX[0] = mouseX;
                    previousMouseY[0] = mouseY;

                    float sensitivity = 0.1f;

                    player.look((float) deltaX * sensitivity, (float) deltaY * sensitivity);
                }
        );

        glfwSetMouseButtonCallback(
                window.getHandle(),
                (handle, button, action, mods) -> {
                    if (!captured) return;
                    if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
                        breakBlock();
                    } else if (button == GLFW_MOUSE_BUTTON_RIGHT && action == GLFW_PRESS) {
                        placeBlock();
                    }
                }
        );
    }

    private void modifyBlock(int x, int y, int z, int color) {
        if (!world.isLoaded(x, y, z)) return;
        if (world.getBlock(x, y, z) == color) {
            return;
        }

        if (color != ChunkGenerator.AIR && player.overlaps(x, y, z)) return;
        if (network == null) world.setBlock(x, y, z, color);
        else network.edit(new dev.jayms.net.Protocol.Edit(x, y, z, color));
    }

    private void breakBlock() {
        BlockHit hit = BlockRaycaster.cast(
                world,
                player.eyePosition(),
                camera.getDirection(),
                6.0f
        );

        if (hit == null) return;

        int current = world.getBlock(hit.x(), hit.y(), hit.z());
        if (current == ChunkGenerator.AIR) return;

        modifyBlock(hit.x(), hit.y(), hit.z(), ChunkGenerator.AIR);
    }

    private void placeBlock() {
        BlockHit hit = BlockRaycaster.cast(
                world,
                player.eyePosition(),
                camera.getDirection(),
                6.0f
        );

        if (hit == null) return;

        if (!world.isLoaded(hit.x() + hit.normalX(), hit.y() + hit.normalY(), hit.z() + hit.normalZ())) return;
        int current = world.getBlock(hit.x() + hit.normalX(), hit.y() + hit.normalY(), hit.z() + hit.normalZ());
        if (current != ChunkGenerator.AIR) return;

        modifyBlock(hit.x() + hit.normalX(), hit.y() + hit.normalY(), hit.z() + hit.normalZ(), ChunkGenerator.STONE);
    }

    private void renderCrosshair() {
        glEnable(GL_SCISSOR_TEST); glClearColor(1, 1, 1, 1);
        glScissor(framebufferWidth / 2 - 7, framebufferHeight / 2 - 1, 14, 2); glClear(GL_COLOR_BUFFER_BIT);
        glScissor(framebufferWidth / 2 - 1, framebufferHeight / 2 - 7, 2, 14); glClear(GL_COLOR_BUFFER_BIT);
        glDisable(GL_SCISSOR_TEST); glClearColor(.48f, .72f, .92f, 1);
    }

    public static void main(String[] args) throws Exception {
        String host = null; int port = dev.jayms.net.Protocol.PORT;
        for (int i = 0; i < args.length; i++) switch (args[i]) {
            case "--server" -> host = args[++i];
            case "--port" -> port = Integer.parseInt(args[++i]);
            default -> throw new IllegalArgumentException("Usage: --server HOST --port PORT (omit for offline play)");
        }
        Main game = new Main();
        try {
            if (host != null) game.network = new dev.jayms.net.MultiplayerClient(host, port);
            game.run();
        } finally { if (game.network != null) game.network.close(); }
    }

}
