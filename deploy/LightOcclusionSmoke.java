import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.render.*;

import org.joml.*;
import org.lwjgl.opengl.GL;

import java.awt.image.BufferedImage;
import java.nio.file.*;

import javax.imageio.ImageIO;

/**
 * Actual engine GPU wall regression: xvfb-run -a java -cp CLIENT.jar
 * deploy/LightOcclusionSmoke.java OUTPUT
 */
public class LightOcclusionSmoke {
    static final int W = 640, H = 400;

    static void require(boolean yes, String message) {
        if (!yes) throw new AssertionError(message);
    }

    static BufferedImage capture() {
        java.nio.ByteBuffer pixels = org.lwjgl.system.MemoryUtil.memAlloc(W * H * 3);
        glReadPixels(0, 0, W, H, GL_RGB, GL_UNSIGNED_BYTE, pixels);
        var image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int i = (x + W * y) * 3;
                image.setRGB(
                        x,
                        H - 1 - y,
                        (pixels.get(i) & 255) << 16
                                | (pixels.get(i + 1) & 255) << 8
                                | pixels.get(i + 2) & 255);
            }
        org.lwjgl.system.MemoryUtil.memFree(pixels);
        return image;
    }

    static BufferedImage room(
            int wall, int depth, int color, boolean gap, Path output, String label)
            throws Exception {
        try (var shader = new ShaderProgram("shaders/voxel.vert", "shaders/voxel.frag");
                var render = new RenderPipeline();
                var world = new World()) {
            var chunk = new Chunk();
            for (int x = 2; x <= 13; x++)
                for (int y = 8; y <= 15; y++)
                    for (int z = 2; z <= 13; z++)
                        if (x == 2 || x == 13 || y == 8 || y == 15 || z == 2 || z == 13)
                            chunk.setBlock(x, y, z, Blocks.STONE);
            world.addChunk(new ChunkPos(0, 4, 0), chunk);
            for (int y = 73; y <= 78; y++)
                for (int z = 3; z <= 12; z++) {
                    if (depth == 0) world.apply(new Protocol.Edit(8, y, z, wall));
                    else
                        for (int iy = 0; iy < 16; iy++)
                            for (int iz = 0; iz < 16; iz++)
                                if (!gap || y != 75 || z != 8 || iy < 6 || iy > 9 || iz < 6
                                        || iz > 9)
                                    world.apply(
                                            new Protocol.Edit(
                                                    8, y, z, Blocks.piece(wall, 4), 4, 8, iy, iz));
                }
            world.apply(new Protocol.Edit(6, 75, 8, Blocks.LED).withColor(color));
            // A blue fill light on the camera side makes the opaque wall visible in the evidence.
            world.apply(new Protocol.Edit(12, 75, 8, Blocks.LED).withColor(0x1040a0));
            chunk.checkMesh();
            var eye = new Vector3f(11.5f, 75.5f, 8.5f);
            var p = new Matrix4f().perspective(1.2f, 1.6f, .1f, 100);
            var v =
                    new Matrix4f()
                            .lookAt(eye, new Vector3f(8.5f, 74.5f, 8.5f), new Vector3f(0, 1, 0));
            long start = System.nanoTime(), deadline = start + 30_000_000_000L;
            while (!render.lightingReady() && System.nanoTime() < deadline) {
                render.update(world, eye.x, eye.z);
                Thread.sleep(20);
            }
            require(render.lightingReady(), "Adaptive lighting uploaded: " + label);
            System.out.println(label + " bake ms=" + (System.nanoTime() - start) / 1_000_000);
            render.begin(W, H, p, v, eye, false, shader);
            shader.setFloat("uDaylight", label.equals("solid-sun") ? 1 : 0);
            shader.setInt("uFog", 0);
            shader.setMatrix4("uProjection", p);
            shader.setMatrix4("uView", v);
            shader.setMatrix4("uModel", new Matrix4f().translation(0, 64, 0));
            shader.setInt("uVertexColor", 1);
            shader.setInt("uInstanced", 0);
            chunk.getMesh().render();
            render.finish();
            glFinish();
            require(glGetError() == GL_NO_ERROR, "No GPU errors: " + label);
            var image = capture();
            ImageIO.write(
                    image, "png", output.resolve("lighting-occlusion-" + label + ".png").toFile());
            return image;
        }
    }

    static int changed(BufferedImage a, BufferedImage b) {
        int result = 0;
        for (int y = 20; y < H - 20; y++)
            for (int x = 20; x < W - 20; x++) {
                int ca = a.getRGB(x, y), cb = b.getRGB(x, y);
                if (java.lang.Math.abs((ca >> 16 & 255) - (cb >> 16 & 255)) > 3
                        || java.lang.Math.abs((ca >> 8 & 255) - (cb >> 8 & 255)) > 3
                        || java.lang.Math.abs((ca & 255) - (cb & 255)) > 3) result++;
            }
        return result;
    }

    static float shadowDepth(int type) throws Exception {
        try (var shader = new ShaderProgram("shaders/shadow.vert", "shaders/shadow.frag");
                var chunk = new Chunk()) {
            chunk.setBlock(0, 0, 0, type);
            chunk.checkMesh();
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glViewport(0, 0, W, H);
            glClearDepth(1);
            glClear(GL_DEPTH_BUFFER_BIT);
            glEnable(GL_DEPTH_TEST);
            glEnable(GL_CULL_FACE);
            shader.bind();
            shader.setMatrix4("uProjection", new Matrix4f().ortho(-1, 1, -1, 1, .1f, 10));
            shader.setMatrix4(
                    "uView",
                    new Matrix4f()
                            .lookAt(
                                    new Vector3f(.5f, .5f, 3),
                                    new Vector3f(.5f, .5f, .5f),
                                    new Vector3f(0, 1, 0)));
            shader.setMatrix4("uModel", new Matrix4f());
            shader.setInt("uInstanced", 0);
            chunk.getMesh().render();
            glFinish();
            float[] value = new float[1];
            glReadPixels(W / 2, H / 2, 1, 1, GL_DEPTH_COMPONENT, GL_FLOAT, value);
            return value[0];
        }
    }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        require(glfwInit(), "GLFW");
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(W, H, "Voxel One solid light occlusion", 0, 0);
        require(window != 0, "Window");
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        try {
            String driver = glGetString(GL_RENDERER);
            var off = room(Blocks.STONE, 4, 0, false, output, "solid-off");
            var solid = room(Blocks.STONE, 4, 0xff0000, false, output, "solid-red");
            int leaked = changed(off, solid);
            require(leaked == 0, "LED does not light the far face of a solid 1/16 wall: " + leaked);
            var sun = room(Blocks.STONE, 4, 0, false, output, "solid-sun");
            int sunLeak = changed(off, sun);
            require(sunLeak == 0, "Enclosed room excludes direct sun and sky reflections");
            var glassOff = room(Blocks.GLASS, 4, 0, false, output, "glass-off");
            var glass = room(Blocks.GLASS, 4, 0xff0000, false, output, "glass-red");
            int transmitted = changed(glassOff, glass);
            require(transmitted > 1000, "Glass transmits red LED light");
            var open = room(Blocks.STONE, 4, 0xff0000, true, output, "opening-red");
            int throughOpening = changed(off, open);
            require(throughOpening > 1000, "Air opening transmits LED light");
            float stoneDepth = shadowDepth(Blocks.STONE), glassDepth = shadowDepth(Blocks.GLASS);
            require(
                    stoneDepth < .9f && glassDepth == 1,
                    "Sun shadow map blocks stone and transmits glass");
            require(glGetError() == GL_NO_ERROR, "Final GL errors");
            String report =
                    "{\n"
                        + "  \"scope\": \"Actual OpenGL engine on Linux VPS; not a physical Windows"
                        + " desktop\",\n"
                        + "  \"renderer\": \""
                            + driver
                            + "\",\n  \"opaqueOneSixteenthWallLeakedPixels\": "
                            + leaked
                            + ",\n  \"sealedRoomSunLeakedPixels\": "
                            + sunLeak
                            + ",\n  \"glassChangedPixels\": "
                            + transmitted
                            + ",\n  \"openingChangedPixels\": "
                            + throughOpening
                            + ",\n  \"sunOpaqueDepth\": "
                            + stoneDepth
                            + ",\n  \"sunGlassDepth\": "
                            + glassDepth
                            + ",\n  \"glErrors\": 0\n}\n";
            Files.writeString(output.resolve("lighting-occlusion-gpu.json"), report);
            System.out.println(report);
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }
}
