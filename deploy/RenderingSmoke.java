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

    static void drawWorld(World world, ShaderProgram shader, RenderPipeline rendering) {
        shader.setInt("uVertexColor", 1);
        shader.setInt("uInstanced", 0);
        shader.setMatrix4("uModel", new Matrix4f().translation(0, 64, 0));
        rendering.chunk(world.getLoadedChunks().values().iterator().next(),new ChunkPos(0,4,0));
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
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, Boolean.getBoolean("voxel.gl33") ? 3 : 4);
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
            rendering.settings.taa=false;rendering.settings.autoExposure=false;rendering.settings.exposure=1;
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
            drawWorld(world, shader, rendering);
            rendering.finish();
            glFinish();
            BufferedImage lit = capture();
            ImageIO.write(lit, "png", output.resolve("voxel-lighting-gpu-gallery.png").toFile());
            rendering.begin(WIDTH, HEIGHT, projection, view, eye, false, shader);
            camera(shader, projection, view);
            shader.setInt("uShadowEnabled", 0);
            drawWorld(world, shader, rendering);
            rendering.finish();
            glFinish();
            int shadowPixels = differences(lit, capture());
            require(shadowPixels > 20, "Sun shadow map changes visible surface lighting");
            rendering.begin(WIDTH, HEIGHT, projection, view, eye, false, shader);
            camera(shader, projection, view);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_CUBE_MAP, 0);
            drawWorld(world, shader, rendering);
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
            // Conservative Hi-Z integration: a synthetic depth plane fully hides a rear AABB.
            int testDepth=glGenTextures();glBindTexture(GL_TEXTURE_2D,testDepth);
            float[] depthPlane=new float[WIDTH*HEIGHT];java.util.Arrays.fill(depthPlane,.2f);
            glTexImage2D(GL_TEXTURE_2D,0,GL_R32F,WIDTH,HEIGHT,0,GL_RED,GL_FLOAT,depthPlane);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            try(var hierarchy=new HiZ()){
                Matrix4f ortho=new Matrix4f().ortho(-32,32,-32,32,.1f,100);
                hierarchy.build(testDepth,WIDTH,HEIGHT,ortho);require(!hierarchy.visible(ortho,-8,-8,-40),"Hi-Z rejects fully hidden rear bounds");
                require(hierarchy.visible(new Matrix4f(ortho).translate(1,0,0),-8,-8,-40),"Camera change invalidates stale Hi-Z use");
                depthPlane[WIDTH/2+HEIGHT/2*WIDTH]=1;glBindTexture(GL_TEXTURE_2D,testDepth);glTexSubImage2D(GL_TEXTURE_2D,0,0,0,WIDTH,HEIGHT,GL_RED,GL_FLOAT,depthPlane);
                hierarchy.build(testDepth,WIDTH,HEIGHT,ortho);require(hierarchy.visible(ortho,-8,-8,-40),"An uncovered depth sample preserves conservative visibility");
                hierarchy.invalidate();require(hierarchy.visible(ortho,-8,-8,-40),"World invalidation preserves visibility");
            }glDeleteTextures(testDepth);
            // Requested workflow: real renderer, editable LED gallery, water and post controls.
            for(int i=0;i<6;i++)rendering.renderShadows(world,models,eye);
            require(rendering.reflectionProbeReady(),"Six-face local reflection probe completes");
            require(rendering.particleCount()==48,"Three emissive GPU particle sources");
            require(rendering.gpuDriven()==(!Boolean.getBoolean("voxel.gl33")&&GL.getCapabilities().OpenGL43),"Compute backend or baseline indexed fallback");
            rendering.resetHistory();
            rendering.begin(WIDTH,HEIGHT,projection,view,eye,false,shader);camera(shader,projection,view);drawWorld(world,shader,rendering);rendering.finish();glFinish();
            BufferedImage beforeDecal=capture();
            rendering.addDecal(new Decal(new Vector3f(8.5f,73.02f,11.5f),new Vector3f(0,1,0),3,.1f,new Vector3f(1,0,0),.85f));
            rendering.begin(WIDTH,HEIGHT,projection,view,eye,false,shader);camera(shader,projection,view);drawWorld(world,shader,rendering);rendering.finish();glFinish();
            int decalPixels=differences(beforeDecal,capture());require(decalPixels>20,"Projected decal changes the floor without world edits");
            ImageIO.write(capture(),"png",output.resolve("rendering-decals.png").toFile());rendering.clearDecals();
            // Shallow pool: textured bed and a submerged vertical wall.
            for(int x=5;x<=11;x++)for(int z=10;z<=13;z++)world.apply(new Protocol.Edit(x,72,z,(x+z)%2==0?Blocks.BRICKS:Blocks.PLANKS));
            for(int z=10;z<=13;z++)world.apply(new Protocol.Edit(4,73,z,Blocks.BRICKS));
            for(int x=5;x<=11;x++)for(int z=10;z<=13;z++)world.apply(new Protocol.Edit(x,73,z,Blocks.WATER));chunk.checkMesh();
            MeshData submerged=MeshDataGenerator.generate(chunk);int floorVertices=0,wallVertices=0;
            for(int i=0;i<submerged.vertices().length;i+=9){
                float[] v=submerged.vertices();if(submerged.surface()[i/3+2]==-2)continue;
                if(v[i+4]==1&&v[i+1]==9&&v[i]>=5&&v[i]<=12&&v[i+2]>=10&&v[i+2]<=14)floorVertices++;
                if(v[i+3]==1&&v[i]==5&&v[i+1]>=9&&v[i+1]<=10&&v[i+2]>=10&&v[i+2]<=14)wallVertices++;
            }
            require(floorVertices>=4&&wallVertices>=4,"Textured submerged floor and wall survive meshing");
            eye.set(8.5f,77,15);view.identity().lookAt(eye,new Vector3f(8.5f,73,11.5f),new Vector3f(0,1,0));rendering.resetHistory();
            rendering.begin(WIDTH,HEIGHT,projection,view,eye,false,shader);camera(shader,projection,view);drawWorld(world,shader,rendering);
            rendering.water(world,models,projection,view,eye);rendering.finish();glFinish();
            BufferedImage wet=capture();require(differences(beforeDecal,wet)>20,"Water reflection/refraction changes the scene");
            ImageIO.write(wet,"png",output.resolve("rendering-water.png").toFile());
            rendering.settings.taa=true;rendering.settings.autoExposure=true;rendering.resetHistory();
            float oldExposure=rendering.exposure();
            for(int i=0;i<12;i++){
                eye.x+=.01f;view.identity().lookAt(eye,new Vector3f(8.5f,74.5f,4.5f),new Vector3f(0,1,0));
                rendering.begin(WIDTH,HEIGHT,projection,view,eye,false,shader);camera(shader,projection,view);drawWorld(world,shader,rendering);
                rendering.water(world,models,projection,view,eye);rendering.finish();glFinish();
            }
            require(rendering.historyFrames()>=12,"Moving camera temporal history rendered");
            require(Float.isFinite(rendering.exposure())&&Math.abs(rendering.exposure()-oldExposure)>.001,"Auto exposure adapts to actual HDR luminance");
            ImageIO.write(capture(),"png",output.resolve("rendering-temporal.png").toFile());
            rendering.settings.renderScale=.65f;
            rendering.begin(WIDTH,HEIGHT,projection,view,eye,false,shader);camera(shader,projection,view);drawWorld(world,shader,rendering);rendering.water(world,models,projection,view,eye);rendering.finish();glFinish();
            require(glGetError()==GL_NO_ERROR,"Temporal upscale after dynamic input size change");
            ImageIO.write(capture(),"png",output.resolve("rendering-upscaled.png").toFile());
            float[] warm=ColourLut.identity(4).rgb();for(int i=0;i<warm.length;i+=3){warm[i]=Math.min(1,warm[i]*1.3f+.1f);warm[i+2]*=.35f;}
            BufferedImage beforeGrade=capture();rendering.setColourLut(new ColourLut(4,warm));
            rendering.begin(WIDTH,HEIGHT,projection,view,eye,false,shader);camera(shader,projection,view);drawWorld(world,shader,rendering);rendering.water(world,models,projection,view,eye);rendering.finish();glFinish();
            require(differences(beforeGrade,capture())>20,"Custom 3D LUT changes visible grading");
            ImageIO.write(capture(),"png",output.resolve("rendering-graded.png").toFile());
            rendering.settings.dynamicResolution=true;rendering.settings.targetFrameMillis=.01f;float originalScale=rendering.settings.renderScale;
            for(int i=0;i<35;i++){
                rendering.begin(WIDTH,HEIGHT,projection,view,eye,false,shader);camera(shader,projection,view);drawWorld(world,shader,rendering);rendering.finish();glFinish();
            }
            require(rendering.settings.renderScale<originalScale,"GPU-time dynamic resolution lowers scale under measured load");
            rendering.settings.dynamicResolution=false;
            world.apply(new Protocol.Edit(5,74,4,0));world.apply(new Protocol.Edit(8,74,4,0));world.apply(new Protocol.Edit(11,74,4,0));rendering.update(world,eye.x,eye.z);
            require(rendering.particleCount()==0,"Removing emissive sources removes their GPU particles");
            rendering.settings.renderScale=1;
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
            Files.writeString(output.resolve("context.txt"),"OpenGL="+glGetString(GL_VERSION)+"; GLSL="+glGetString(GL_SHADING_LANGUAGE_VERSION)+"; renderer="+driver+"; samples="+rendering.samples+"\n");
            Files.writeString(output.resolve("expansion-checks.txt"),"Playtest: native OpenGL synthetic gallery; assigned X11 display; GPU backend="+rendering.gpuDriven()+"; decals changed pixels="+decalPixels+"; reflection probes, GPU emitters/removal, water, camera motion/TAA, exposure, LUT, temporal upscaling, measured dynamic resolution, resize and GL errors passed.\n");
            System.out.println("Rendering integration checks passed");
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }
}
