import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.player.*;
import dev.jayms.render.*;
import dev.jayms.ui.*;
import org.joml.Vector3f;
import java.nio.file.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Deterministic render-only replay; optional production streaming is a separate workload. */
public class RenderingBenchmark {
    static Main game=new Main();static int frame;static PerformanceRecorder performance;static PerformanceRecorder.Route route;static Path output;static int warmup,frames;static boolean streaming;
    static Object get(String name)throws Exception{var f=Main.class.getDeclaredField(name);f.setAccessible(true);return f.get(game);}
    static void set(String name,Object value)throws Exception{var f=Main.class.getDeclaredField(name);f.setAccessible(true);f.set(game,value);}
    static void add(World w,int x,int y,int z,int type){w.apply(new Protocol.Edit(x,y,z,type));}
    public static void main(String[] args)throws Exception{
        output=Path.of(args[0]);route=PerformanceRecorder.Route.valueOf(args[1]);warmup=Integer.parseInt(args[2]);frames=Integer.parseInt(args[3]);streaming=Boolean.parseBoolean(args[4]);
        GraphicsProfile profile=GraphicsProfile.preset(GraphicsProfile.Preset.valueOf(args[5]));profile.save(Controls.directory().resolve("graphics.properties"));
        set("seed",42L);set("offlineSave",output.resolve("synthetic-world.dat"));set("gameConfig",route==PerformanceRecorder.Route.CITY?GameConfig.cityGame():route==PerformanceRecorder.Route.INTERIOR?new GameConfig(false,false,1200,0):GameConfig.sandbox());((EngineEditor)get("engineEditor")).open=false;
        game.run(new Main.FrameObserver(){
            public void started(Main g)throws Exception{
                long handle=((dev.jayms.window.Window)get("window")).getHandle();glfwSetWindowSize(handle,640,400);
                World world=(World)get("world");if(!streaming){world.close();world=new World(42){@Override public void stream(float x,float z,int budget){}};for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)world.addChunk(new ChunkPos(x,4,z),new Chunk());set("world",world);}
                for(int x=-28;x<29;x++)for(int z=-28;z<29;z++)add(world,x,72,z,Blocks.BRICKS);
                for(int bx=-20;bx<=20;bx+=10)for(int bz=-20;bz<=20;bz+=10){for(int x=bx;x<bx+6;x++)for(int y=73;y<78;y++)add(world,x,y,bz,Blocks.PLANKS);for(int x=bx;x<bx+6;x++)for(int z=bz;z<bz+6;z++)add(world,x,78,z,Blocks.PLANKS);world.apply(new Protocol.Edit(bx+2,74,bz+1,Blocks.LED).withColor(0x30ff90));add(world,bx+3,74,bz,Blocks.GLASS);add(world,bx+4,73,bz+2,Blocks.WATER);world.apply(new Protocol.Edit(bx+1,73,bz+2,Blocks.BRICKS,1,0,0,0));}
                set("renderOnlyReplay",true);set("performanceRoute",route);((ControlsMenu)get("menu")).open=false;
                Player player=new Player(new Vector3f(8,75,14),-90,-12,(Camera)get("camera"));player.toggleFlight();set("player",player);set("isometric",route==PerformanceRecorder.Route.ISOMETRIC);
                performance=(PerformanceRecorder)get("performance");String revision="unknown";try(var in=Main.class.getResourceAsStream("/build-info.properties")){var p=new java.util.Properties();if(in!=null){p.load(in);String value=p.getProperty("revision","");if(value.matches("[0-9a-f]{40}"))revision=value;}}
                performance.start(output,new PerformanceRecorder.Metadata(System.getProperty("os.name"),System.getProperty("os.arch"),glGetString(GL_RENDERER),glGetString(GL_VENDOR),glGetString(GL_VERSION),System.getProperty("java.version"),revision,Runtime.getRuntime().availableProcessors(),Runtime.getRuntime().maxMemory(),640,400,profile,route,streaming?"PRODUCTION_STREAMING":"RENDER_ONLY_REPLAY"),warmup);
            }
            public void beforeFrame(Main g)throws Exception{
                ((ControlsMenu)get("menu")).open=false;
                double t=frame*.04;float x=8,z=14,y=75;float yaw=-90,pitch=-12;
                switch(route){
                    case GROUND -> {x=(float)(Math.sin(t)*24);z=(float)(Math.cos(t)*24);}
                    case FLIGHT -> {x=(float)(Math.sin(t*2)*40);z=(float)(Math.cos(t*2)*40);y=90;pitch=-35;}
                    case TELEPORT -> {x=frame%60<30?-24:24;z=frame%120<60?-24:24;}
                    case ISOMETRIC -> {var overview=(IsometricCamera)get("overview");overview.pan(.4f,.2f);overview.rotateDegrees(.5f);overview.zoom(frame%120<60?.03:-.03);}
                    case CITY -> {x=(float)(Math.sin(t)*20);z=26;y=83;pitch=-25;}
                    case INTERIOR -> {x=-18;z=-18;y=75;yaw=(float)(t*30-90);}
                    case FRACTIONAL -> {if(frame%20==0)((World)get("world")).apply(new Protocol.Edit(15,73,15,frame%40==0?Blocks.PLANKS:Blocks.BRICKS,1,0,0,0));}
                    default -> {}
                }
                Player player=new Player(new Vector3f(x,y,z),yaw,pitch,(Camera)get("camera"));player.toggleFlight();set("player",player);
            }
            public void afterFrame(Main g)throws Exception{
                if(!((DetailedMeshScheduler)get("detailedMeshes")).error.isEmpty())throw new AssertionError("Detailed upload failed");if(glGetError()!=GL_NO_ERROR)throw new AssertionError("Benchmark GL error");if(((DetailedMeshScheduler)get("detailedMeshes")).queued()>DetailedMeshScheduler.MAX_JOBS)throw new AssertionError("Detailed queue bound");frame++;if(frame>=warmup+frames){glfwSetWindowShouldClose(((dev.jayms.window.Window)get("window")).getHandle(),true);}
            }
        });System.out.println("Playtest: deterministic "+route+" replay; "+warmup+" warm-up + "+frames+" measured frames; production streaming="+streaming);
    }
}
