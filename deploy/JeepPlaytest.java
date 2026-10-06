import dev.jayms.*;
import dev.jayms.player.*;
import dev.jayms.net.Blocks;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Synthetic isolated world, production GLFW callbacks, real X11 input and F10 recording. */
public final class JeepPlaytest {
    static Main game;
    static Path output;
    static volatile boolean ready, done;
    static volatile long frames;
    static volatile Throwable failure;
    static final Queue<Runnable> edits=new ConcurrentLinkedQueue<>();
    static Object get(String name) throws Exception {
        Field f=Main.class.getDeclaredField(name); f.setAccessible(true); return f.get(game);
    }
    static void set(String name,Object value) throws Exception {
        Field f=Main.class.getDeclaredField(name); f.setAccessible(true); f.set(game,value);
    }
    static Jeep jeep() throws Exception { return (Jeep)get("jeep"); }
    static Player player() throws Exception { return (Player)get("player"); }
    static void require(boolean ok,String check) { if(!ok) throw new AssertionError(check); }
    static String x(String... args) throws Exception {
        List<String> cmd=new ArrayList<>(List.of("xdotool")); cmd.addAll(List.of(args));
        Process p=new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        String result=new String(p.getInputStream().readAllBytes()).trim();
        require(p.waitFor()==0,"X11 input "+args[0]);
        long before=frames, limit=System.nanoTime()+30_000_000_000L;
        while(frames<before+2 && System.nanoTime()<limit) Thread.sleep(50);
        require(frames>=before+2,"Frames after X11 input "+args[0]); return result;
    }
    static void renderEdit(Runnable edit) throws Exception {
        CountDownLatch latch=new CountDownLatch(1);
        edits.add(()->{try {edit.run();}finally {latch.countDown();}});
        require(latch.await(10,TimeUnit.SECONDS),"Render edit completed");
        if(failure!=null) throw new AssertionError(failure);
    }
    static void screenshot(String name) {
        try {
            int w=(int)get("framebufferWidth"),h=(int)get("framebufferHeight");
            var pixels=BufferUtils.createByteBuffer(w*h*4);
            glReadPixels(0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
            BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
            for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
                int i=(y*w+x)*4;
                image.setRGB(x,h-y-1,((pixels.get(i)&255)<<16)|((pixels.get(i+1)&255)<<8)|(pixels.get(i+2)&255));
            }
            ImageIO.write(image,"png",output.resolve(name).toFile());
        } catch(Throwable e) { failure=e; }
    }
    interface Check { boolean ok() throws Exception; }
    static void await(Check check,String label) throws Exception {
        long end=System.nanoTime()+45_000_000_000L;
        while(!check.ok() && System.nanoTime()<end) Thread.sleep(50);
        require(check.ok(),label);
    }
    static volatile String capture;
    public static void main(String[] args) throws Exception {
        output=Path.of(args[0]); Files.createDirectories(output);
        glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);
        game=new Main(); set("offlineSave",output.resolve("synthetic-world.dat"));
        Thread driver=new Thread(()->{
            long handle=0;
            try {
                for(int i=0;i<180&&!ready;i++) Thread.sleep(500);
                require(ready,"Game started");
                for(int i=0;i<120&&frames<3;i++) Thread.sleep(500);
                require(frames>=3,"Game frames ready");
                handle=((dev.jayms.window.Window)get("window")).getHandle();
                String id=Long.toString(org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window(handle));
                x("windowfocus",id); Thread.sleep(1000);
                if(((dev.jayms.ui.ControlsMenu)get("menu")).open) x("key","Escape");
                if(!(boolean)get("captured")) x("key","Tab");
                x("mousemove_relative","--","1","0");
                capture="jeep-parked.png"; Thread.sleep(600);
                x("key","F10"); x("key","j"); require(jeep().driving(),"J enters driver seat: captured="+get("captured")+", menu="+((dev.jayms.ui.ControlsMenu)get("menu")).open+", player="+player().position()+", jeep="+jeep().position()+", notice="+get("notice"));
                capture="jeep-driver-seat.png"; await(()->capture==null,"Driver image captured");
                x("key","F5"); x("key","F5");
                require(player().cameraView()==Player.CameraView.FIRST_PERSON && jeep().driving(),"First-person driver camera");
                capture="jeep-windshield.png"; await(()->capture==null,"Windshield image captured");
                x("key","F5");
                require(player().cameraView()==Player.CameraView.THIRD_PERSON,"Third-person driver camera restored");
                Vector3f start=jeep().position();
                x("keydown","w"); await(()->jeep().speed()>=6,"Normal throttle reaches speed");
                float normal=jeep().speed(); require(jeep().position().distance(start)>2,"W moves jeep");
                x("keydown","Control_L"); await(()->jeep().speed()>normal+2,"Boost speed reached");
                require(jeep().speed()>normal+2,"Ctrl accelerates"); x("keyup","Control_L");
                float yaw=jeep().yaw(); float leftStart=yaw; x("keydown","a"); await(()->jeep().yaw()<leftStart-12,"Left turn reached"); x("keyup","a");
                require(jeep().yaw()<yaw-10,"A steers left");
                yaw=jeep().yaw(); float rightStart=yaw; x("keydown","d"); await(()->jeep().yaw()>rightStart+12,"Right turn reached"); x("keyup","d");
                require(jeep().yaw()>yaw+10,"D steers right");
                yaw=jeep().yaw(); x("mousemove_relative","--","180","0");
                require(Math.abs(jeep().yaw()-yaw)>1,"Mouse steers: before="+yaw+", after="+jeep().yaw()+", firstMouse="+get("firstMouse"));
                x("key","j"); require(jeep().driving(),"Moving exit rejected");
                x("keyup","w"); await(()->Math.abs(jeep().speed())<.01f,"Coasting brakes");
                start=jeep().position();
                Vector3f reverseStart=new Vector3f(start);
                x("keydown","s"); await(()->jeep().speed()<-2 && jeep().position().distance(reverseStart)>.7f,"Reverse reached"); x("keyup","s");
                require(jeep().speed()<0 && jeep().position().distance(start)>.5f,"S reverses");
                await(()->Math.abs(jeep().speed())<.01f,"Reverse brakes");
                renderEdit(()->{try {
                    Jeep fresh=new Jeep(new Vector3f(8.5f,33.01f,8.5f),-90);
                    player().driveSeat(fresh.position().add(2.8f,0,0),-90);
                    set("jeep",fresh); require(fresh.enter(player()),"Reset driver");
                }catch(Throwable e){failure=e;}});
                x("keydown","w"); x("keydown","Control_L"); await(()->jeep().position().z<-27 && Math.abs(jeep().speed())<.5f,"Boost wall stop reached");
                require(jeep().position().z>=-28.11f,"Full body stops at wall");
                require(Math.abs(jeep().speed())<.5f,"Wall stops boost speed");
                capture="jeep-wall.png"; Thread.sleep(500);
                x("keyup","w"); x("keyup","Control_L"); Thread.sleep(500);
                x("key","j"); require(!jeep().driving(),"Stopped J exits");
                start=player().position(); Vector3f walkStart=new Vector3f(start);
                x("keydown","w"); await(()->walkStart.distance(player().position())>.7f,"Walking reached"); x("keyup","w");
                require(start.distance(player().position())>.5f,"Walking resumes after exit");
                x("key","F10"); Thread.sleep(3500);
                Files.writeString(output.resolve("results.json"),"{\"status\":\"passed\",\"platform\":\"Linux assigned X11 display\",\"profile\":\"isolated synthetic\",\"checks\":[\"driver entry\",\"first-person windshield camera\",\"third-person camera regression\",\"W drive\",\"Ctrl acceleration\",\"S reverse\",\"A/D steering\",\"mouse steering\",\"moving exit rejected\",\"wall collision at boost speed\",\"stopped exit\",\"walking regression\"],\"recording\":\"F10 engine recorder\"}\n");
            }catch(Throwable e){failure=e;}
            finally {done=true; if(handle!=0) glfwSetWindowShouldClose(handle,true);}
        }); driver.setDaemon(true);
        game.run(new Main.FrameObserver(){
            public void started(Main g) throws Exception {
                World old=(World)get("world"),world=new World(old.terrain().seed,old.models());
                for(int x=-3;x<=3;x++) for(int z=-3;z<=3;z++) for(int y=-2;y<=7;y++) {
                    Chunk c=new Chunk();
                    if(y==2) for(int i=0;i<16;i++) for(int k=0;k<16;k++) c.setBlock(i,0,k,Blocks.GRASS);
                    world.addChunk(new ChunkPos(x,y,z),c);
                }
                for(int x=-20;x<=28;x++) for(int y=33;y<=36;y++) world.setBlock(x,y,-30,Blocks.BRICKS);
                set("world",world); old.close();
                Player p=new Player(new Vector3f(11.3f,33.01f,8.5f),-130,-18,(Camera)get("camera"));
                p.toggleView(); set("player",p); set("jeep",new Jeep(new Vector3f(8.5f,33.01f,8.5f),-90));
                set("isometric",false); ready=true; driver.start();
            }
            public void beforeFrame(Main g) { Runnable edit; while((edit=edits.poll())!=null) edit.run(); }
            public void afterFrame(Main g) { frames++; if(capture!=null){String name=capture; capture=null; screenshot(name);} }
        });
        driver.join(1000); if(failure!=null) throw new AssertionError("Jeep playtest failed",failure);
        require(done&&Files.exists(output.resolve("results.json")),"Playtest completed");
    }
}
