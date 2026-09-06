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

    private Chunk chunk;
    private Mesh chunkMesh;
    private ShaderProgram shader;
    private Camera camera;
    private Player player;
    private PlayerModel playerModel;
    private World world;

    private int framebufferHeight = 1280;
    private int framebufferWidth = 720;

    public void run() throws Exception {
        System.out.println("Hello LWJGL " + Version.getVersion() + "!");

        init();
        initScene();
        loop();
        cleanup();
    }

    private void cleanup() throws Exception {
        world.close();
        playerModel.close();
        chunkMesh.close();
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

        window = new Window(framebufferWidth, framebufferHeight, "Hello World!", false, true);
        window.registerKeyListener(GLFW_KEY_B, GLFW_PRESS, () -> {
            int current = chunk.getBlock(8, 10, 8);

            int replacement = current == ChunkGenerator.AIR ? ChunkGenerator.STONE : ChunkGenerator.AIR;

            modifyBlock(8, 10, 8, replacement);
        });
        window.registerKeyListener(GLFW_KEY_R, GLFW_PRESS, () -> {
            BlockHit hit = BlockRaycaster.cast(
                    world,
                    camera.position(),
                    camera.getDirection(),
                    6.0f
            );

            System.out.println(hit);

            if (hit == null) return;

            int current = chunk.getBlock(hit.x(), hit.y(), hit.z());

            int replacement = current == ChunkGenerator.AIR ? ChunkGenerator.STONE : ChunkGenerator.AIR;

            modifyBlock(hit.x(), hit.y(), hit.z(), replacement);
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

        glClearColor(1.0f, 0.0f, 0.0f, 0.0f);

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
        chunk = ChunkGenerator.createExampleChunk();

        MeshData meshData = MeshDataGenerator.generate(chunk);
        chunkMesh = new Mesh(meshData);

        shader = new ShaderProgram(
                "src/main/resources/shaders/voxel.vert",
                "src/main/resources/shaders/voxel.frag"
        );

        camera = new Camera();
        player = new Player(new Vector3f(8.0f, 8.4f, 24.0f), -90.0f, -20.0f, camera);
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

        configureMouse();
    }

    private void loop() {
        double previousTime = glfwGetTime();

        while (!window.shouldClose()) {
            double currentTime = glfwGetTime();

            float deltaTime =
                    (float) (currentTime - previousTime);

            previousTime = currentTime;

            player.update(window.getHandle(), deltaTime);

            glClear(
                    GL_COLOR_BUFFER_BIT
                            | GL_DEPTH_BUFFER_BIT
            );

            render();

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
        playerModel.render(player, shader);
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
                    if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
                        breakBlock();
                    } else if (button == GLFW_MOUSE_BUTTON_RIGHT && action == GLFW_PRESS) {
                        placeBlock();
                    }
                }
        );
    }

    private void modifyBlock(int x, int y, int z, int color) {
        if (world.getBlock(x, y, z) == color) {
            return;
        }

        world.setBlock(x, y, z, color);
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

        int current = world.getBlock(hit.x() + hit.normalX(), hit.y() + hit.normalY(), hit.z() + hit.normalZ());
        if (current != ChunkGenerator.AIR) return;

        modifyBlock(hit.x() + hit.normalX(), hit.y() + hit.normalY(), hit.z() + hit.normalZ(), ChunkGenerator.STONE);
    }

    static void main(String[] args) throws Exception {
        new Main().run();
    }

}
