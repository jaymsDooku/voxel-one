import dev.jayms.*;
import dev.jayms.net.city.GameConfig;
import dev.jayms.player.Player;
import dev.jayms.window.Window;
import dev.jayms.render.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.concurrent.locks.LockSupport;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import org.joml.FrustumIntersection;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Matched resident/cold/transition route. Fresh travel is wholly outside the spawn preload. */
public class StreamingRoute {
    static Main game=new Main();static int frame,first=-1;static Path out;static PerformanceRecorder recorder;
    static final int TOTAL=166;
    static final StringBuilder residency=new StringBuilder("sample,segment,x,z,center_resident,nearby_resident,pending,loaded\n");
    static Object get(String name)throws Exception{var f=Main.class.getDeclaredField(name);f.setAccessible(true);return f.get(game);}
    static void set(String name,Object value)throws Exception{var f=Main.class.getDeclaredField(name);f.setAccessible(true);f.set(game,value);}
    static boolean resident(World w,float x,float z,int radius){int cx=Math.floorDiv((int)Math.floor(x),16),cz=Math.floorDiv((int)Math.floor(z),16);for(int a=-radius;a<=radius;a++)for(int b=-radius;b<=radius;b++)for(int y=-2;y<=7;y++)if(!w.getLoadedChunks().containsKey(new ChunkPos(cx+a,y,cz+b)))return false;return true;}
    static boolean graphicsReady()throws Exception{
        Object renderer=get("rendering");var f=RenderPipeline.class.getDeclaredField("hasIrradiance");f.setAccessible(true);if(!f.getBoolean(renderer))return false;
        try{f=RenderPipeline.class.getDeclaredField("lightingUpload");f.setAccessible(true);if(f.get(renderer)!=null)return false;}catch(NoSuchFieldException baseline){}
        try{if((int)RenderPipeline.class.getMethod("skyPending").invoke(renderer)!=0)return false;}catch(NoSuchMethodException baseline){}
        try{var requested=RenderPipeline.class.getDeclaredField("desiredVolume");requested.setAccessible(true);var active=RenderPipeline.class.getDeclaredField("volume");active.setAccessible(true);if(requested.get(renderer)!=active.get(renderer))return false;}catch(NoSuchFieldException baseline){}
        try{var requested=RenderPipeline.class.getDeclaredField("desiredSky");requested.setAccessible(true);var active=RenderPipeline.class.getDeclaredField("worldSky");active.setAccessible(true);var prepared=requested.get(renderer);if(prepared!=null&&prepared.getClass().getField("data").get(prepared)!=active.get(renderer))return false;}catch(NoSuchFieldException baseline){}

        var lightingField=RenderPipeline.class.getDeclaredField("lighting");lightingField.setAccessible(true);var lighting=lightingField.get(renderer);var pendingField=WorldLighting.class.getDeclaredField("pending");pendingField.setAccessible(true);if(pendingField.get(lighting)!=null)return false;
        return true;
    }
    static String segment(int sample){if(sample<12)return "resident";if(sample<16)return "coldEntry";if(sample<32)return "coldWalk";if(sample<112)return "coldFlight";if(sample<128)return "warmReversal";if(sample<134)return "teleport";if(sample<158)return "catchup";return "transitionTail";}
    static float x(int sample){if(sample<12)return 8;if(sample<16)return 128;if(sample<32)return 128+(sample-16)*.5f;if(sample<112)return 136+(sample-32)*1.25f;if(sample<128)return 236-(sample-112)*1.25f;return 2048;}
    public static void main(String[] args)throws Exception{
        out=Path.of(args[0]);Files.createDirectories(out);long startup=System.nanoTime();
        GraphicsProfile.preset(GraphicsProfile.Preset.BALANCED).with(GraphicsProfile.Key.RESOLUTION,"640x480@60").save(dev.jayms.ui.Controls.directory().resolve("graphics.properties"));
        set("offlineSave",out.resolve("synthetic.dat"));set("seed",748291L);set("gameConfig",new GameConfig(false,false,1200,10));
        game.run(new Main.FrameObserver(){
            public void started(Main main)throws Exception{
                glfwSwapInterval(0);Files.writeString(out.resolve("route-progress.txt"),"preload\n");recorder=(PerformanceRecorder)get("performance");World world=(World)get("world");
                Method async=null;try{async=World.class.getMethod("streamAsync",float.class,float.class,float.class,long.class);}catch(NoSuchMethodException baseline){}
                while(world.getLoadedChunks().size()<810){if(async==null)world.stream(8,8,9);else async.invoke(world,8f,110f,8f,2_000_000L);glfwSwapBuffers(((Window)get("window")).getHandle());glfwPollEvents();LockSupport.parkNanos(1_000_000L);}
                // Finish identical resident meshes before graphics warmup. No recorder is active.
                var scheduler=(DetailedMeshScheduler)get("detailedMeshes");var visible=new FrustumIntersection(new Matrix4f().ortho(-10000,10000,-10000,10000,-10000,10000));long limit=System.nanoTime()+120_000_000_000L;
                while(world.getLoadedChunks().values().stream().anyMatch(Chunk::dirty)||scheduler.queued()!=0){
                    if(System.nanoTime()>limit)throw new AssertionError("Resident mesh warmup did not catch up");scheduler.update(world,8,8,visible,112,112,2_000_000,2*1024*1024);glfwPollEvents();LockSupport.parkNanos(1_000_000L);
                }
                Files.writeString(out.resolve("startup.json"),"{\"startupAndResidentPreparationMs\":"+(System.nanoTime()-startup)/1e6+",\"residentChunks\":"+world.getLoadedChunks().size()+"}\n");
            }
            public void beforeFrame(Main main)throws Exception{
                ((dev.jayms.ui.ControlsMenu)get("menu")).open=false;
                if(first<0&&frame>=10&&graphicsReady()){
                    first=frame;var graphics=(GraphicsController)get("graphics");recorder.start(out,new PerformanceRecorder.Metadata(System.getProperty("os.name"),System.getProperty("os.arch"),glGetString(GL_RENDERER),glGetString(GL_VENDOR),glGetString(GL_VERSION),System.getProperty("java.version"),args[1],Runtime.getRuntime().availableProcessors(),Runtime.getRuntime().maxMemory(),(int)get("framebufferWidth"),(int)get("framebufferHeight"),graphics.transaction.effective(),PerformanceRecorder.Route.FLIGHT,"810 resident chunks and completed resident meshes; graphics warmup until latest requested irradiance/sky accepted and no pending bake/sky/upload; video inactive"),0);
                    Files.writeString(out.resolve("warmup.json"),"{\"graphicsWarmupFrames\":"+frame+",\"warmupWallMs\":"+(System.nanoTime()-startup)/1e6+"}\n");
                }
                if(first<0&&frame>240)throw new AssertionError("Graphics warmup did not become ready");
                int sample=first<0?-1:frame-first;
                if(sample==TOTAL){recorder.stop().get();Files.writeString(out.resolve("residency.csv"),residency);glfwSetWindowShouldClose(((Window)get("window")).getHandle(),true);}
                ((Player)get("player")).driveSeat(new Vector3f(sample<0?8:x(Math.min(sample,TOTAL-1)),110,8),0);
            }
            public void afterFrame(Main main)throws Exception{
                int sample=first<0?-1:frame-first;
                if(sample>=0&&sample<TOTAL){World world=(World)get("world");float px=x(sample);int pending=-1;try{pending=(int)World.class.getMethod("terrainPending").invoke(world);}catch(NoSuchMethodException baseline){}
                    residency.append(sample+","+segment(sample)+","+px+",8,"+resident(world,px,8,0)+","+resident(world,px,8,1)+","+pending+","+world.getLoadedChunks().size()+"\n");
                    if(sample%10==0)Files.writeString(out.resolve("residency-partial.csv"),residency);
                }
                if(++frame%10==0)Files.writeString(out.resolve("route-progress.txt"),"frame="+frame+"; sample="+sample+"\n");
            }
        });
        System.out.println("Playtest: resident then sustained fresh terrain x128..236, warm reversal and teleport; "+TOTAL+" profiled frames exported");
    }
}
