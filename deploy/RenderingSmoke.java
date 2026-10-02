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
import java.nio.ByteBuffer;
import java.nio.file.*;

import javax.imageio.ImageIO;

/**
 * Reproducible GPU checks: xvfb-run -a java -cp CLIENT.jar deploy/RenderingSmoke.java OUTPUT_DIR
 */
public class RenderingSmoke {
    private static final int WIDTH = 640, HEIGHT = 400;

    static BufferedImage capture() {
        ByteBuffer bytes = MemoryUtil.memAlloc(WIDTH * HEIGHT * 3);
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        try {
            glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGB, GL_UNSIGNED_BYTE, bytes);
            for (int y = 0; y < HEIGHT; y++)
                for (int x = 0; x < WIDTH; x++) {
                    int i = (x + WIDTH * y) * 3;
                    image.setRGB(
                            x,
                            HEIGHT - 1 - y,
                            (bytes.get(i) & 255) << 16
                                    | (bytes.get(i + 1) & 255) << 8
                                    | (bytes.get(i + 2) & 255));
                }
        } finally {
            MemoryUtil.memFree(bytes);
        }
        return image;
    }

    static int brightness(int rgb) {
        return (rgb >> 16 & 255) + (rgb >> 8 & 255) + (rgb & 255);
    }

    static int differences(BufferedImage a, BufferedImage b) {
        int n = 0;
        for (int y = 0; y < HEIGHT; y++)
            for (int x = 0; x < WIDTH; x++)
                if (Math.abs(brightness(a.getRGB(x, y)) - brightness(b.getRGB(x, y))) > 3) n++;
        return n;
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void drawWorld(World world, ShaderProgram shader) {
        shader.setInt("uVertexColor", 1);
        shader.setInt("uInstanced", 0);
        shader.setMatrix4("uModel", new Matrix4f().translation(0, 64, 0));
        world.getLoadedChunks().values().iterator().next().getMesh().render();
    }

    static void camera(ShaderProgram shader, Matrix4f p, Matrix4f v) {
        shader.setMatrix4("uProjection", p);
        shader.setMatrix4("uView", v);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1)
            throw new IllegalArgumentException("Provide an evidence output directory");
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        require(glfwInit(), "GLFW initialization");
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(WIDTH, HEIGHT, "Voxel One rendering checks", 0, 0);
        require(window != 0, "Window creation");
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        String driver = glGetString(GL_RENDERER);
        try (var shader = new ShaderProgram("shaders/voxel.vert", "shaders/voxel.frag");
                var rendering = new RenderPipeline();
                var world = new World();
                var models = new VoxelModelRenderer(world.models())) {
            Matrix4f projection = new Matrix4f().perspective(1.2f, 1.6f, .1f, 4096);
            Vector3f eye = new Vector3f(8, 80, 24);
            Matrix4f view =
                    new Matrix4f()
                            .lookAt(
                                    eye,
                                    new Vector3f(eye).fma(100, RenderPipeline.SUN),
                                    new Vector3f(0, 1, 0));
            rendering.begin(WIDTH, HEIGHT, projection, view, eye, false, shader);
            rendering.finish();
            glFinish();
            BufferedImage sun = capture();
            require(
                    brightness(sun.getRGB(WIDTH / 2, HEIGHT / 2))
                            > brightness(sun.getRGB(20, 20)) + 35,
                    "Visible sun is brighter than surrounding sky");
            ImageIO.write(sun, "png", output.resolve("voxel-lighting-gpu-sun.png").toFile());
            Chunk chunk = new Chunk();
            for (int x = 2; x < 15; x++)
                for (int z = 2; z < 15; z++) chunk.setBlock(x, 8, z, Blocks.STONE);
            for (int x = 3; x < 14; x++)
                for (int z = 3; z < 10; z++) chunk.setBlock(x, 13, z, Blocks.STONE);
            for (int x = 3; x < 14; x++)
                for (int y = 9; y < 13; y++) chunk.setBlock(x, y, 3, Blocks.PLANKS);
            world.addChunk(new ChunkPos(0, 4, 0), chunk);
            world.apply(new Protocol.Edit(5, 74, 4, Blocks.LED).withColor(0xff3040));
            world.apply(new Protocol.Edit(8, 74, 4, Blocks.LED).withColor(0x30ff90));
            world.apply(new Protocol.Edit(11, 74, 4, Blocks.LED).withColor(0x3050ff));
            for (int x = 6; x <= 10; x++) world.apply(new Protocol.Edit(x, 73, 9, Blocks.GLASS));
            chunk.checkMesh();
            eye.set(8.5f, 75, 14.5f);
            view.identity().lookAt(eye, new Vector3f(8.5f, 74.5f, 4.5f), new Vector3f(0, 1, 0));
            long deadline = System.nanoTime() + 10_000_000_000L;
            while (!rendering.lightingReady() && System.nanoTime() < deadline) {
                rendering.update(world, eye.x, eye.z);
                Thread.sleep(50);
            }
            require(rendering.lightingReady(), "Background irradiance bake uploaded");
            rendering.renderShadows(world, models, eye);
            rendering.begin(WIDTH, HEIGHT, projection, view, eye, false, shader);
            camera(shader, projection, view);
            drawWorld(world, shader);
            rendering.finish();
            glFinish();
            BufferedImage lit = capture();
            ImageIO.write(lit, "png", output.resolve("voxel-lighting-gpu-gallery.png").toFile());
            rendering.begin(WIDTH, HEIGHT, projection, view, eye, false, shader);
            camera(shader, projection, view);
            shader.setInt("uShadowEnabled", 0);
            drawWorld(world, shader);
            rendering.finish();
            glFinish();
            int shadowPixels = differences(lit, capture());
            require(shadowPixels > 20, "Sun shadow map changes visible surface lighting");
            rendering.begin(WIDTH, HEIGHT, projection, view, eye, false, shader);
            camera(shader, projection, view);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_CUBE_MAP, 0);
            drawWorld(world, shader);
            rendering.finish();
            glFinish();
            int reflectionPixels = differences(lit, capture());
            require(reflectionPixels > 20, "Sky cubemap reflections affect surfaces");
            try (var textures = new MaterialTextures()) {
                glBindTexture(GL_TEXTURE_2D_ARRAY, textures.id);
                require(
                        glGetTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER)
                                == GL_LINEAR_MIPMAP_LINEAR,
                        "Trilinear mipmap filter");
                require(
                        glGetTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER) == GL_LINEAR,
                        "Linear magnification filter");
            }
            rendering.begin(333, 271, projection, view, eye, true, shader);
            rendering.finish();
            glFinish();
            require(glGetError() == GL_NO_ERROR, "No GL errors, including resized targets");
            String report =
                    "{\n"
                        + "  \"scope\": \"Real OpenGL engine integration checks on the VPS, not a"
                        + " physical desktop\",\n"
                        + "  \"renderer\": \""
                            + driver.replace("\\", "\\\\").replace("\"", "\\\"")
                            + "\",\n  \"samples\": "
                            + rendering.samples
                            + ",\n"
                            + "  \"sunVisible\": true,\n"
                            + "  \"irradianceUploaded\": true,\n"
                            + "  \"shadowChangedPixels\": "
                            + shadowPixels
                            + ",\n  \"reflectionChangedPixels\": "
                            + reflectionPixels
                            + ",\n"
                            + "  \"mipmapAndLinearFilters\": true,\n"
                            + "  \"resizeAndGlErrors\": \"passed\"\n"
                            + "}\n";
            Files.writeString(
                    output.resolve("rendering-gpu-" + rendering.samples + "x.json"), report);
            System.out.println(report);
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }
}
