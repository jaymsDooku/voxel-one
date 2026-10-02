import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.player.*;
import dev.jayms.render.*;

import org.joml.*;
import org.lwjgl.opengl.GL;

import java.nio.file.*;
import java.util.*;

import javax.imageio.ImageIO;

/** Actual player-rig GPU comparisons for the population's daily-life animations. */
public class CitizenRenderingSmoke extends RenderingSmoke {
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        require(glfwInit(), "GLFW");
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(640, 400, "Citizen animation checks", 0, 0);
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        try (var shader = new ShaderProgram("shaders/voxel.vert", "shaders/voxel.frag");
                var pipeline = new RenderPipeline();
                var world = new World();
                var models = new VoxelModelRenderer(world.models());
                var player = new PlayerModel()) {
            Chunk chunk = new Chunk();
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++) chunk.setBlock(x, 8, z, Blocks.STONE);
            world.addChunk(new ChunkPos(0, 4, 0), chunk);
            chunk.checkMesh();
            var eye = new Vector3f(11, 75, 12);
            var projection = new Matrix4f().perspective(.85f, 1.6f, .1f, 4096);
            var view = new Matrix4f().lookAt(eye, new Vector3f(8, 74, 8), new Vector3f(0, 1, 0));
            pipeline.time(new GameConfig(true, false, 1200, 10), 0);
            for (int i = 0; i < 150 && !pipeline.lightingReady(); i++) {
                pipeline.update(world, 8, 8);
                Thread.sleep(20);
            }
            pipeline.renderShadows(world, models, eye);
            List<String> checks = new ArrayList<>();
            String[] activities = {
                "Working in mine",
                "Building for developer",
                "Working in shop",
                "Eating at shop",
                "Sleeping at home"
            };
            for (int a = 0; a < activities.length; a++) {
                java.awt.image.BufferedImage first = null;
                for (int frame = 0; frame < 2; frame++) {
                    pipeline.begin(640, 400, projection, view, eye, false, shader);
                    camera(shader, projection, view);
                    drawWorld(world, shader);
                    player.renderCitizen(
                            new Protocol.Pose(1, 8, 73.01f, 8, -45, 0),
                            0,
                            false,
                            activities[a],
                            frame * .5,
                            shader,
                            models);
                    pipeline.finish();
                    var image = capture();
                    ImageIO.write(
                            image,
                            "png",
                            out.resolve("citizen-life-" + a + "-" + frame + ".png").toFile());
                    if (first == null) first = image;
                    else {
                        int changed = differences(first, image);
                        require(
                                a == 4 ? changed == 0 : changed > 20,
                                "Life animation: " + activities[a]);
                        checks.add(
                                "{\"activity\":\""
                                        + activities[a]
                                        + "\",\"changedPixels\":"
                                        + changed
                                        + "}");
                    }
                }
            }
            require(glGetError() == GL_NO_ERROR, "No GL errors");
            Files.writeString(
                    out.resolve("citizen-life-gpu-checks.json"),
                    "{\"scope\":\"Actual OpenGL rig under VPS software"
                        + " Mesa\",\"status\":\"passed\",\"checks\":["
                            + String.join(",", checks)
                            + "]}\n");
            System.out.println(
                    "Work, construction, shop and meal gestures change rendered pixels; sleeping"
                        + " stays still; no GL errors.");
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }
}
