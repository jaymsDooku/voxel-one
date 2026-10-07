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

    static void clean(String stage){int error=glGetError();require(error==GL_NO_ERROR,stage+" GL error="+error);}
    static Object field(Object owner,String name)throws Exception{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    static float[] probeFace(ReflectionProbes probe){
        try{glActiveTexture(GL_TEXTURE15);glBindTexture(GL_TEXTURE_CUBE_MAP,(int)field(probe,"cube"));float[] rgb=new float[64*64*3];glGetTexImage(GL_TEXTURE_CUBE_MAP_NEGATIVE_Y,0,GL_RGB,GL_FLOAT,rgb);return rgb;}catch(Exception e){throw new RuntimeException(e);}
    }
    static int probeDifferences(float[] a,float[] b){int count=0;for(int i=0;i<a.length;i+=3)if(Math.abs(a[i]-b[i])+Math.abs(a[i+1]-b[i+1])+Math.abs(a[i+2]-b[i+2])>.002)count++;return count;}
    static void probeImage(float[] rgb,Path path)throws Exception{
        var image=new BufferedImage(64,64,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<64;y++)for(int x=0;x<64;x++){int i=(x+y*64)*3,c=0;for(int k=0;k<3;k++){float v=Math.max(0,rgb[i+k]);c=(c<<8)|Math.min(255,Math.round(255*(float)Math.pow(v/(1+v),1/2.2)));}image.setRGB(x,63-y,c);}ImageIO.write(image,"png",path.toFile());
    }
    static void streamingHiZChecks(int depth, Matrix4f camera, Path output) throws Exception {
        try (var scene = new World(); var renderer = new RenderPipeline()) {
            // Complete sentinel columns keep stream's removal pass isolated from generation.
            for (int x=-4;x<=4;x++) for (int z=-4;z<=4;z++)
                scene.addChunk(new ChunkPos(x,-2,z),new Chunk());
            scene.getLoadedChunks().values().forEach(Chunk::checkMesh);
            renderer.update(scene,0,0);
            var hierarchy=(HiZ)field(renderer,"hiZ");
            hierarchy.build(depth,WIDTH,HEIGHT,camera);
            renderer.update(scene,0,0);
            require((boolean)field(hierarchy,"valid"),"Unchanged stationary scene retains Hi-Z");
            long edits=scene.editsVersion();
            var added=new Chunk();added.setBlock(0,0,0,Blocks.STONE);
            scene.addChunk(new ChunkPos(1,4,0),added);
            scene.getLoadedChunks().values().forEach(Chunk::checkMesh);
            require(!added.dirty()&&scene.editsVersion()==edits,"Streamed addition is clean without edit revision");
            renderer.update(scene,0,0);
            require(!(boolean)field(hierarchy,"valid"),"Meshed chunk addition invalidates stationary Hi-Z");
            require(hierarchy.visible(camera,-8,-8,-40),"Added scene cannot use stale occlusion");
            hierarchy.build(depth,WIDTH,HEIGHT,camera);
            added.setBlock(1,0,0,Blocks.STONE);added.checkMesh();renderer.update(scene,0,0);
            require(!(boolean)field(hierarchy,"valid"),"Rebuilt clean mesh invalidates stationary Hi-Z");
            var distant=new Chunk();distant.setBlock(0,0,0,Blocks.STONE);
            var distantPos=new ChunkPos(8,4,0);scene.addChunk(distantPos,distant);distant.checkMesh();renderer.update(scene,0,0);
            hierarchy.build(depth,WIDTH,HEIGHT,camera);
            require(!hierarchy.visible(camera,-8,-8,-40),"Fresh unchanged depth can reject hidden bounds");
            scene.stream(0,0,1);scene.getLoadedChunks().values().forEach(Chunk::checkMesh);
            require(!scene.getLoadedChunks().containsKey(distantPos)&&scene.editsVersion()==edits,"Streaming unload without edit revision");
            renderer.update(scene,0,0);
            require(!(boolean)field(hierarchy,"valid"),"Chunk removal invalidates stationary Hi-Z");
            require(hierarchy.visible(camera,-8,-8,-40),"Removed occluder cannot leave stale hidden bounds");
            clean("Stationary streaming Hi-Z");
            Files.writeString(output.resolve("streaming-hiz-checks.txt"),"Playtest: PASS. Fixed camera and unchanged editsVersion. Addition after checkMesh, clean mesh replacement and stream removal all invalidate actual renderer Hi-Z. Unchanged scene retains valid depth; hidden bounds become conservatively visible after additions/removals. No GL errors.\n");
        }
    }

    static void probeChecks(World world,Chunk chunk,RenderPipeline rendering,VoxelModelRenderer models,Vector3f eye,Path output)throws Exception{
        var probe=(ReflectionProbes)field(rendering,"probes");var testEye=new Vector3f(11.5f,75,9.5f);
        probe.invalidate();for(int i=0;i<6;i++)rendering.renderShadows(world,models,testEye);require(probe.ready(),"Lit reflection probe completes");clean("Initial lit capture");
        var shader=(ShaderProgram)field(probe,"voxel");int id=(int)field(shader,"programId");
        for(String uniform:new String[]{"uHasIrradiance","uShadowEnabled","uTransportReady","uClusterReady"})require(glGetUniformi(id,glGetUniformLocation(id,uniform))==1,"Probe lighting enabled: "+uniform);
        require(glGetUniformi(id,glGetUniformLocation(id,"uProbeReady"))==0,"Probe capture does not sample itself");
        var binder=RenderPipeline.class.getDeclaredMethod("bindSceneLighting",ShaderProgram.class);binder.setAccessible(true);
        float[] lit=probeFace(probe);clean("Uniform and face readback");int shadows,clusters;
        try(var reference=new ReflectionProbes()){
            for(int i=0;i<6;i++)reference.capture(world,models,testEye,(int)field(rendering,"environment"),v->{try{binder.invoke(rendering,v);v.setInt("uShadowEnabled",0);}catch(Exception e){throw new RuntimeException(e);}});
            clean("Shadow reference capture");shadows=probeDifferences(lit,probeFace(reference));clean("Shadow face readback");require(shadows>4,"Occluder shadow changes reflected receiver pixels: "+shadows);
            reference.invalidate();for(int i=0;i<6;i++)reference.capture(world,models,testEye,(int)field(rendering,"environment"),v->{try{binder.invoke(rendering,v);v.setInt("uClusterReady",0);}catch(Exception e){throw new RuntimeException(e);}});
            clean("Cluster reference capture");clusters=probeDifferences(lit,probeFace(reference));clean("Cluster face readback");require(clusters>4,"Clustered LEDs change reflected receiver pixels: "+clusters);
        }
        clean("Reference close");probeImage(lit,output.resolve("rendering-probe-lit.png"));
        world.apply(new Protocol.Edit(11,74,4,Blocks.LED).withColor(0xff5030));chunk.checkMesh();Object oldVolume=field(rendering,"volume");rendering.update(world,eye.x,eye.z);require(!probe.ready(),"LED edit invalidates reflection capture");
        long end=System.nanoTime()+20_000_000_000L;while(field(rendering,"volume")==oldVolume&&System.nanoTime()<end){rendering.update(world,eye.x,eye.z);Thread.sleep(25);}require(field(rendering,"volume")!=oldVolume,"Edited LED GI bake accepted");
        for(int i=0;i<6;i++)rendering.renderShadows(world,models,testEye);clean("Edited probe capture");float[] edited=probeFace(probe);int editPixels=probeDifferences(lit,edited);require(editPixels>4,"LED edit changes reflected receiver pixels: "+editPixels);probeImage(edited,output.resolve("rendering-probe-led-edit.png"));
        Files.writeString(output.resolve("probe-checks.txt"),"Playtest: PASS. Real reflection cubemap receiver face; irradiance/shadow/transport/cluster switches=1; recursive probe switch=0. Occluder shadow pixels="+shadows+"; clustered LED pixels="+clusters+"; LED edit pixels="+editPixels+". Accepted GI bake and six fresh faces after edit; no stale-ready cube.\n");
        probe.invalidate();for(int i=0;i<6;i++)rendering.renderShadows(world,models,eye);
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
            }
            java.util.Arrays.fill(depthPlane,.2f);glBindTexture(GL_TEXTURE_2D,testDepth);glTexSubImage2D(GL_TEXTURE_2D,0,0,0,WIDTH,HEIGHT,GL_RED,GL_FLOAT,depthPlane);
            streamingHiZChecks(testDepth,new Matrix4f().ortho(-32,32,-32,32,.1f,100),output);
            glDeleteTextures(testDepth);
            // Requested workflow: real renderer, editable LED gallery, water and post controls.
            for(int i=0;i<6;i++)rendering.renderShadows(world,models,eye);
            require(rendering.reflectionProbeReady(),"Six-face local reflection probe completes");
            probeChecks(world,chunk,rendering,models,eye,output);clean("Probe checks");
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
            clean("Temporal sequence");
            rendering.settings.renderScale=.65f;
            rendering.begin(WIDTH,HEIGHT,projection,view,eye,false,shader);camera(shader,projection,view);drawWorld(world,shader,rendering);rendering.water(world,models,projection,view,eye);rendering.finish();glFinish();
            clean("Temporal upscale after dynamic input size change");
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
