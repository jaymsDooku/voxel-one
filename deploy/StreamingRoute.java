import dev.jayms.*;
import dev.jayms.net.city.GameConfig;
import dev.jayms.player.Player;
import dev.jayms.window.Window;
import dev.jayms.render.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.concurrent.locks.LockSupport;
import org.joml.Vector3f;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Fixed per-frame route in production Main; profile capture excludes video. Synthetic saves only. */
public class StreamingRoute {
    static Main game=new Main();static int frame;static Path out;static PerformanceRecorder recorder;
    static Object get(String name)throws Exception{var f=Main.class.getDeclaredField(name);f.setAccessible(true);return f.get(game);}
    static void set(String name,Object v)throws Exception{var f=Main.class.getDeclaredField(name);f.setAccessible(true);f.set(game,v);}
    public static void main(String[] args)throws Exception{
        out=Path.of(args[0]);Files.createDirectories(out);
        GraphicsProfile.preset(GraphicsProfile.Preset.BALANCED).with(GraphicsProfile.Key.RESOLUTION,"640x480@60").save(dev.jayms.ui.Controls.directory().resolve("graphics.properties"));
        set("offlineSave",out.resolve("synthetic.dat"));set("seed",748291L);set("gameConfig",new GameConfig(false,false,1200,10));set("framebufferWidth",640);set("framebufferHeight",480);
        game.run(new Main.FrameObserver(){
            public void started(Main main)throws Exception{
                glfwSwapInterval(0);Files.writeString(out.resolve("route-progress.txt"),"started\n");recorder=(PerformanceRecorder)get("performance");
                World world=(World)get("world");
                Method async=null;try{async=World.class.getMethod("streamAsync",float.class,float.class,float.class,long.class);}catch(NoSuchMethodException baseline){}
                // Match resident terrain before the same ten graphics warm-up frames.
                while(world.getLoadedChunks().size()<810) {
                    if(async==null)world.stream(8,8,9);else async.invoke(world,8f,110f,8f,2_000_000L);
                    glBindFramebuffer(GL_FRAMEBUFFER,0);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
                    glfwSwapBuffers(((Window)get("window")).getHandle());glfwPollEvents();LockSupport.parkNanos(1_000_000L);
                }
                Files.writeString(out.resolve("environment.txt"),"Renderer="+glGetString(GL_RENDERER)+"\nGL="+glGetString(GL_VERSION)+"\nJava="+System.getProperty("java.version")+"\nseed=748291; 640x480; default effective settings; synthetic offline Sandbox; video inactive\n");
            }
            public void beforeFrame(Main main)throws Exception{
                ((dev.jayms.ui.ControlsMenu)get("menu")).open=false;
                int i=frame-10;float x=8;
                if(i>=0&&i<30)x=8+i*.5f; // walking
                else if(i<70&&i>=30)x=23+(i-30)*1.25f; // rapid flight
                else if(i<90&&i>=70)x=73-(i-70)*1.25f; // reversal
                else if(i>=90&&i<110)x=2048; // teleport
                else if(i>=110)x=8; // warm revisit
                Player p=(Player)get("player");p.driveSeat(new Vector3f(x,110,8),0);
                if(frame==10){var graphics=(GraphicsController)get("graphics");recorder.start(out,new PerformanceRecorder.Metadata(System.getProperty("os.name"),System.getProperty("os.arch"),glGetString(GL_RENDERER),glGetString(GL_VENDOR),glGetString(GL_VERSION),System.getProperty("java.version"),args[1],Runtime.getRuntime().availableProcessors(),Runtime.getRuntime().maxMemory(),(int)get("framebufferWidth"),(int)get("framebufferHeight"),graphics.transaction.effective(),PerformanceRecorder.Route.FLIGHT,"cold route after 10 resident warmup frames"),0);}
            }
            public void afterFrame(Main main)throws Exception{
                if(++frame%10==0)Files.writeString(out.resolve("route-progress.txt"),"frame="+frame+"\n");
                if(frame==130){recorder.stop().get();glfwSetWindowShouldClose(((Window)get("window")).getHandle(),true);}
            }
        });
        System.out.println("Playtest: production Main fixed walk/flight/reversal/teleport/revisit route; "+frame+" frames; profile exported");
    }
}
