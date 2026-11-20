import dev.jayms.*;
import dev.jayms.combat.Vec3;
import dev.jayms.combat.arena.BasaltCourtyard;
import dev.jayms.net.Blocks;
import dev.jayms.player.Player;
import dev.jayms.recording.ScreenRecorder;
import dev.jayms.render.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Synthetic, account-free arena playtest using the actual engine mesher, renderer and player.
 * Run on the assigned inherited display. The recorder toggle is the same path bound to F10.
 * No match controller, combat, server, save, or game entry point is installed by this fixture.
 */
public final class BasaltCourtyardPreview {
    static final int W = 960, H = 600;
    record Draw(ChunkPos pos, Mesh mesh) {}
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static void capture(Path file) throws Exception {
        ByteBuffer rgb = MemoryUtil.memAlloc(W*H*3);
        try {
            glPixelStorei(GL_PACK_ALIGNMENT,1);
            glReadPixels(0,0,W,H,GL_RGB,GL_UNSIGNED_BYTE,rgb);
            var image = new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
            for (int y=0;y<H;y++) for (int x=0;x<W;x++) {
                int i=(y*W+x)*3;
                image.setRGB(x,H-y-1,(rgb.get(i)&255)<<16|(rgb.get(i+1)&255)<<8|(rgb.get(i+2)&255));
            }
            ImageIO.write(image,"png",file.toFile());
        } finally { MemoryUtil.memFree(rgb); }
    }
    static Camera view(float x,float y,float z,float yaw,float pitch) {
        var c = new Camera(); c.position().set(x,y,z); c.setYaw(yaw); c.setPitch(pitch); return c;
    }
    static Player player(float x,float y,float z,float yaw,Camera camera) {
        return new Player(new Vector3f(x,y,z),yaw,0,camera);
    }
    static void render(Camera camera,List<Draw> draws,ShaderProgram shader,RenderPipeline pipeline) {
        var projection = new Matrix4f().perspective((float)Math.toRadians(70),W/(float)H,.1f,400);
        var view = camera.createViewMatrix();
        pipeline.begin(W,H,projection,view,new Vector3f(camera.position()),false,shader);
        shader.setInt("uShadowEnabled",0);
        for (var d:draws) {
            shader.setMatrix4("uModel",new Matrix4f().translation(d.pos.chunkX()*16,d.pos.chunkY()*16,d.pos.chunkZ()*16));
            d.mesh.render();
        }
        pipeline.finish(); glFinish();
        check(glGetError()==GL_NO_ERROR,"OpenGL render error");
    }
    public static void main(String[] args) throws Exception {
        if (args.length!=4 || !args[0].equals("--display") || !args[1].equals(System.getenv("DISPLAY")))
            throw new IllegalArgumentException("Use --display with inherited DISPLAY, then media and scratch directories");
        Path out=Path.of(args[2]), scratch=Path.of(args[3]);
        Files.createDirectories(out); Files.createDirectories(scratch);
        check(glfwInit(),"GLFW initialization");
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
        glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window=glfwCreateWindow(W,H,"Basalt Courtyard fixture preview",0,0);
        check(window!=0,"GLFW window"); glfwMakeContextCurrent(window); glfwSwapInterval(0);
        GL.createCapabilities();
        System.out.println("Renderer: "+glGetString(GL_RENDERER));
        List<Draw> draws=new ArrayList<>();
        var profile=GraphicsProfile.preset(GraphicsProfile.Preset.LOW)
                .with(GraphicsProfile.Key.SCALE,"1").with(GraphicsProfile.Key.HAZE,"0");
        try (var fixture=BasaltCourtyard.generate();
             var shader=new ShaderProgram("shaders/voxel.vert","shaders/voxel.frag");
             var pipeline=new RenderPipeline(profile);
             var recorder=new ScreenRecorder(scratch)) {
            var world=fixture.world();
            // Exercise reset before meshing: floor deletion and new lane obstacle must both recover.
            world.setBlock(0,0,0,Blocks.AIR); world.setBlock(0,2,0,Blocks.BRICKS);
            check(!fixture.safeLanding(new Vec3(.5,1,.5),.3,1.8),"Deleted floor rejects landing");
            fixture.reset();
            check(fixture.safeLanding(new Vec3(.5,1,.5),.3,1.8),"Reset restores clear landing");
            for (var entry:world.getLoadedChunks().entrySet()) {
                if (!entry.getValue().isEmpty()) draws.add(new Draw(entry.getKey(),new Mesh(
                        MeshDataGenerator.generate(new ChunkSnapshot(world,entry.getKey(),entry.getValue())))));
            }
            // Initialize real sky/GI before capture; uninitialized lighting is deliberately black.
            long lightingDeadline=System.nanoTime()+30_000_000_000L;
            while(!pipeline.lightingReady() && System.nanoTime()<lightingDeadline) {
                pipeline.update(world,0,0,10_000_000L); Thread.sleep(20);
            }
            check(pipeline.lightingReady(),"Fixture lighting ready");
            Camera overview=view(55,64,73,-127,-35);
            render(overview,draws,shader,pipeline);
            capture(out.resolve("basalt-courtyard-overview.png"));
            Camera ground=new Camera();
            Player lane=player(0,1,-28,90,ground);
            render(ground,draws,shader,pipeline);
            capture(out.resolve("basalt-courtyard-spawn.png"));
            // A continuous lane sprint, a wall stop and a jumping flank route use actual Player physics.
            for(int i=0;i<420;i++) lane.step(world,1f/60,1,0,false,true);
            check(lane.position().z>27 && lane.position().z<29,"Lane sprint reaches opposite spawn");
            check(Math.abs(lane.position().y-1)<.002,"Lane stays grounded");
            Player wall=player(29,1,0,0,new Camera());
            for(int i=0;i<120;i++) wall.step(world,1f/60,1,0,false,true);
            check(Math.abs(wall.position().x-30.7)<.002,"Boundary stops player");
            Player flank=player(23,1,-10,90,new Camera());
            float maxY=1;
            for(int i=0;i<180;i++) {
                flank.step(world,1f/60,1,0,true,false); maxY=Math.max(maxY,flank.position().y);
            }
            check(maxY>=3 && flank.position().z>0,"Jump stairs reach and cross flank");
            render(view(14,10,-14,55,-25),draws,shader,pipeline);
            capture(out.resolve("basalt-courtyard-flank.png"));
            // Short F10-path clip: overview then a real central-lane sprint. No combat is claimed.
            Player recorded=player(0,1,-28,90,ground);
            recorder.toggle(W,H); check(recorder.active(),"Engine recorder starts");
            for(int frame=0;frame<90;frame++) {
                glfwPollEvents();
                Camera camera=overview;
                if(frame>=20) {
                    for(int step=0;step<3;step++) recorded.step(world,1f/60,1,0,false,true);
                    camera=ground;
                }
                render(camera,draws,shader,pipeline);
                recorder.capture(W,H); glfwSwapBuffers(window);
                Thread.sleep(70);
            }
            recorder.toggle(W,H);
            System.out.printf(Locale.ROOT,"PASS: reset recovery; lane sprint z=%.3f y=%.3f; boundary x=%.3f; flank maxY=%.3f z=%.3f; %d meshes; 90 recorded preview frames%n",
                    lane.position().z,lane.position().y,wall.position().x,maxY,flank.position().z,draws.size());
        } finally {
            for(var d:draws) d.mesh.close();
            glfwDestroyWindow(window); glfwTerminate();
        }
        try(var files=Files.list(scratch)) {
            var videos=files.filter(p->p.toString().endsWith(".mp4")).toList();
            check(videos.size()==1,"One finalized F10-path recording");
            check(Files.size(videos.get(0))<6_000_000,"Recording below 6 MB");
            Files.move(videos.get(0),out.resolve("basalt-courtyard-engine-preview.mp4"),StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
