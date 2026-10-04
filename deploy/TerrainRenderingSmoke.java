import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.render.*;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

import javax.imageio.ImageIO;

/** Actual engine mesher and render pipeline: city planning and ground-level valley evidence. */
public class TerrainRenderingSmoke {
    static final int W = 960, H = 600;

    record TileMesh(DistantTerrainPlan.Tile tile, Mesh mesh) {}

    static void capture(Path path) throws Exception {
        ByteBuffer bytes = MemoryUtil.memAlloc(W * H * 3);
        try {
            glReadPixels(0, 0, W, H, GL_RGB, GL_UNSIGNED_BYTE, bytes);
            BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    int i = (y * W + x) * 3;
                    image.setRGB(
                            x,
                            H - 1 - y,
                            (bytes.get(i) & 255) << 16
                                    | (bytes.get(i + 1) & 255) << 8
                                    | (bytes.get(i + 2) & 255));
                }
            ImageIO.write(image, "png", path.toFile());
        } finally {
            MemoryUtil.memFree(bytes);
        }
    }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        boolean headless = args.length > 1 && args[1].equals("--headless");
        if (headless) glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_NULL);
        if (!glfwInit()) throw new AssertionError("GLFW");
        if (headless) glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_EGL_CONTEXT_API);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(W, H, "River valley", 0, 0);
        if (window == 0) throw new AssertionError("GL window");
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        System.out.println("GPU: " + glGetString(GL_RENDERER));
        List<TileMesh> meshes = new ArrayList<>();
        try (var shader = new ShaderProgram("shaders/voxel.vert", "shaders/voxel.frag");
                var pipeline = new RenderPipeline()) {
            Terrain terrain = new Terrain(Terrain.DEFAULT_SEED);
            var mesher = new DistantTerrainMesher(terrain, List.of());
            for (int x = -384; x < 384; x += 64)
                for (int z = -384; z < 512; z += 64) {
                    boolean fine = x >= -64 && x < 192 && z >= -128 && z < 192;
                    int size = fine ? 16 : 64;
                    for (int a = 0; a < 64; a += size)
                        for (int b = 0; b < 64; b += size) {
                            var tile = new DistantTerrainPlan.Tile(x + a, z + b, size);
                            meshes.add(new TileMesh(tile, new Mesh(mesher.build(tile))));
                        }
                }
            for (boolean planning : new boolean[] {true, false}) {
                IsometricCamera overview = new IsometricCamera();
                overview.zoom(3);
                Camera ground = new Camera();
                ground.position().set(18, 28.65f, 24);
                ground.setYaw(-38.2f);
                ground.setPitch(3.45f);
                Matrix4f projection =
                        planning
                                ? overview.projection(new WorldBounds(-384, -384, 384, 512), W, H)
                                : new Matrix4f()
                                        .perspective(
                                                (float) Math.toRadians(70),
                                                W / (float) H,
                                                .1f,
                                                1800);
                Camera camera = planning ? overview.camera() : ground;
                Vector3f eye = new Vector3f(camera.position());
                Matrix4f view = camera.createViewMatrix();
                pipeline.begin(W, H, projection, view, eye, planning, shader);
                shader.setMatrix4("uProjection", projection);
                shader.setMatrix4("uView", view);
                shader.setInt("uInstanced", 0);
                shader.setInt("uVertexColor", 1);
                shader.setInt("uShadowEnabled", 0);
                for (var mesh : meshes) {
                    shader.setMatrix4(
                            "uModel", new Matrix4f().translation(mesh.tile.x(), 0, mesh.tile.z()));
                    mesh.mesh.render();
                }
                pipeline.finish();
                glFinish();
                if (glGetError() != GL_NO_ERROR) throw new AssertionError("GL render error");
                capture(
                        out.resolve(
                                planning
                                        ? "terrain-city-planning.png"
                                        : "terrain-ground-level.png"));
            }
            Path recordingDirectory = out.resolve("terrain-recording");
            try (var playable = new World(Terrain.DEFAULT_SEED);
                    var recorder = new dev.jayms.recording.ScreenRecorder(recordingDirectory)) {
                for (int x = 0; x <= 2; x++)
                    for (int z = 0; z <= 2; z++)
                        for (int y = 0; y <= 3; y++) {
                            var pos = new ChunkPos(x, y, z);
                            playable.addChunk(pos, ChunkGenerator.generate(pos, terrain));
                        }
                IsometricCamera overview = new IsometricCamera();
                overview.zoom(3);
                Camera walking = new Camera();
                var player =
                        new dev.jayms.player.Player(
                                new Vector3f(8.5f, 27.01f, 24.5f), -38.2f, 3.45f, walking);
                recorder.toggle(W, H); // same engine recorder path used by the F10 control
                for (int frame = 0; frame < 120; frame++) {
                    boolean planning = frame < 60;
                    if (planning && frame > 0 && frame % 15 == 0) overview.rotate(1);
                    if (!planning) player.step(playable, 1f / 30, 1, 0, false, false);
                    Matrix4f projection =
                            planning
                                    ? overview.projection(
                                            new WorldBounds(-384, -384, 384, 512), W, H)
                                    : new Matrix4f()
                                            .perspective(
                                                    (float) Math.toRadians(70),
                                                    W / (float) H,
                                                    .1f,
                                                    1800);
                    Camera camera = planning ? overview.camera() : walking;
                    Matrix4f view = camera.createViewMatrix();
                    Vector3f eye = new Vector3f(camera.position());
                    pipeline.begin(W, H, projection, view, eye, planning, shader);
                    shader.setMatrix4("uProjection", projection);
                    shader.setMatrix4("uView", view);
                    shader.setInt("uInstanced", 0);
                    shader.setInt("uVertexColor", 1);
                    shader.setInt("uShadowEnabled", 0);
                    for (var mesh : meshes) {
                        shader.setMatrix4(
                                "uModel",
                                new Matrix4f().translation(mesh.tile.x(), 0, mesh.tile.z()));
                        mesh.mesh.render();
                    }
                    pipeline.finish();
                    glFinish();
                    if (glGetError() != GL_NO_ERROR)
                        throw new AssertionError("Camera motion GL error");
                    recorder.capture(W, H);
                    Thread.sleep(35);
                }
                if (player.position().x < 14 || !player.grounded())
                    throw new AssertionError(
                            "Player did not walk on the generated settlement terrace");
                recorder.toggle(W, H);
                System.out.println(
                        "PASS: four camera orientations and generated-terrain walking; player x="
                                + player.position().x());
            }
            try (var files = Files.list(recordingDirectory)) {
                var recordings =
                        files.filter(path -> path.getFileName().toString().endsWith(".mp4"))
                                .toList();
                if (recordings.size() != 1)
                    throw new AssertionError("Expected one finalized recording");
                if (Files.size(recordings.get(0)) > 6_000_000)
                    throw new AssertionError("Recording exceeds artifact limit");
                Files.move(
                        recordings.get(0),
                        out.resolve("terrain-opengl-camera-walk.mp4"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            Files.delete(recordingDirectory);
            System.out.println(
                    "PASS: both camera views rendered with engine geometry and shaders; "
                            + meshes.size()
                            + " tiles");
        } finally {
            for (var mesh : meshes) mesh.mesh.close();
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }
}
