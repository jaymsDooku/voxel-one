import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.render.*;

import org.joml.*;
import org.lwjgl.opengl.GL;

import java.nio.file.*;

import javax.imageio.ImageIO;

/** Actual daytime/nighttime engine GPU check; compile alongside RenderingSmoke.java. */
public class CityRenderingSmoke extends RenderingSmoke {
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        require(glfwInit(), "GLFW");
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(640, 400, "City clock checks", 0, 0);
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        try (var shader = new ShaderProgram("shaders/voxel.vert", "shaders/voxel.frag");
                var pipeline = new RenderPipeline();
                var world = new World();
                var models = new VoxelModelRenderer(world.models())) {
            Chunk chunk = new Chunk();
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++) chunk.setBlock(x, 8, z, Blocks.STONE);
            world.addChunk(new ChunkPos(0, 4, 0), chunk);
            world.apply(new Protocol.Edit(8, 74, 8, Blocks.LED).withColor(0xff2000));
            chunk.checkMesh();
            var eye = new Vector3f(8, 78, 19);
            var projection = new Matrix4f().perspective(1.2f, 1.6f, .1f, 4096);
            var view = new Matrix4f().lookAt(eye, new Vector3f(8, 74, 8), new Vector3f(0, 1, 0));
            var config = new GameConfig(true, true, 1200, 12);
            pipeline.time(config, 0);
            for (int i = 0; i < 150 && !pipeline.lightingReady(); i++) {
                pipeline.update(world, 8, 8);
                Thread.sleep(20);
            }
            pipeline.renderShadows(world, models, eye);
            pipeline.begin(640, 400, projection, view, eye, false, shader);
            camera(shader, projection, view);
            drawWorld(world, shader);
            pipeline.finish();
            var day = capture();
            ImageIO.write(day, "png", out.resolve("city-day.png").toFile());
            pipeline.time(config, 600);
            for (int i = 0; i < 150; i++) {
                pipeline.update(world, 8, 8);
                Thread.sleep(10);
            }
            pipeline.renderShadows(world, models, eye);
            pipeline.begin(640, 400, projection, view, eye, false, shader);
            camera(shader, projection, view);
            drawWorld(world, shader);
            pipeline.finish();
            var night = capture();
            ImageIO.write(night, "png", out.resolve("city-night.png").toFile());
            int changed = differences(day, night);
            require(changed > 10000, "Night changes sky and terrain lighting");
            int warm = 0;
            long dayTotal = 0, nightTotal = 0;
            for (int y = 0; y < 400; y++)
                for (int x = 0; x < 640; x++) {
                    int d = day.getRGB(x, y), n = night.getRGB(x, y);
                    dayTotal += brightness(d);
                    nightTotal += brightness(n);
                    if ((n >> 16 & 255) > (n & 255) * 2 && (n >> 16 & 255) > 80) warm++;
                }
            require(nightTotal < dayTotal * .8, "Night scene is darker");
            require(warm > 100, "LED remains emissive at night");
            require(glGetError() == GL_NO_ERROR, "No GL error");
            String report =
                    "{\"scope\":\"Actual OpenGL day/night and LED checks on VPS software"
                        + " Mesa\",\"dayNightChangedPixels\":"
                            + changed
                            + ",\"nightWarmPixels\":"
                            + warm
                            + ",\"nightBrightnessRatio\":"
                            + ((double) nightTotal / dayTotal)
                            + ",\"status\":\"passed\"}";
            Files.writeString(out.resolve("city-render-checks.json"), report);
            System.out.println(report);
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }
}
