import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.joml.*;
import static org.lwjgl.glfw.GLFW.*;

/** Runs the production game with real X11 clicks and an isolated synthetic save. */
public class CargoVehicleSmoke {
    static Main game = new Main();
    static volatile Throwable failure;
    static Path out;
    static String windowId;
    static volatile String capture;
    static volatile long frames;
    static volatile Matrix4f screenMatrix;
    static Object get(String name) throws Exception {
        Field f = Main.class.getDeclaredField(name); f.setAccessible(true); return f.get(game);
    }
    static void set(String name,Object value) throws Exception {
        Field f = Main.class.getDeclaredField(name); f.setAccessible(true); f.set(game,value);
    }
    static CityFrame city() throws Exception {
        Method m = Main.class.getDeclaredMethod("city"); m.setAccessible(true); return (CityFrame)m.invoke(game);
    }
    static String x(String... args) throws Exception {
        var cmd = new ArrayList<String>(); cmd.add("xdotool"); cmd.addAll(List.of(args));
        var p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        String text = new String(p.getInputStream().readAllBytes()).trim();
        if (p.waitFor()!=0) throw new AssertionError("X11 input failed: " + args[0]);
        long target=frames+2;
        for(int i=0;i<200 && frames<target;i++) Thread.sleep(50);
        return text;
    }
    static void require(boolean ok,String message) { if (!ok) throw new AssertionError(message); }
    static int width() throws Exception { return (int)get("framebufferWidth"); }
    static int height() throws Exception { return (int)get("framebufferHeight"); }
    static void click(int x,int y) throws Exception { x("mousemove","--window",windowId,Integer.toString(x),Integer.toString(y)); x("click","1"); }
    static volatile Runnable action;
    static void onFrame(Runnable task) throws Exception { action=task; for(int i=0;i<200 && action!=null;i++) Thread.sleep(50); require(action==null,"Frame action finished"); Thread.sleep(300); }
    static dev.jayms.player.Jeep vehicle() throws Exception { return (dev.jayms.player.Jeep)get("jeep"); }
    static void beside() throws Exception { onFrame(() -> { try { ((dev.jayms.player.Player)get("player")).driveSeat(vehicle().position().add(3,0,0),-90); } catch(Exception e) { throw new RuntimeException(e); } }); }
    public static void main(String[] args) throws Exception {
        out=Path.of(args[0]); Files.createDirectories(out);
        set("offlineSave",out.resolve("synthetic-world.dat"));
        glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);
        var driver=new Thread(() -> {
            long handle=0;
            try {
                for(int i=0;i<120 && get("player")==null;i++) Thread.sleep(500);
                require(get("player")!=null,"Game started"); Thread.sleep(2000);
                handle=((dev.jayms.window.Window)get("window")).getHandle();
                windowId=Long.toString(org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window(handle));
                x("windowfocus",windowId); x("windowsize",windowId,"1280","720");
                x("windowmove",windowId,"0","0"); x("windowraise",windowId);
                if(((ControlsMenu)get("menu")).open) x("key","Escape");
                onFrame(() -> { try {
                    World world=new World(); set("world",world);
                    for(int cx=-2;cx<=2;cx++) for(int cz=-2;cz<=2;cz++) {
                        Chunk ground=new Chunk(); for(int xx=0;xx<16;xx++) for(int zz=0;zz<16;zz++) ground.setBlock(xx,0,zz,Blocks.STONE);
                        for(int yy=-2;yy<=7;yy++) world.addChunk(new ChunkPos(cx,yy,cz),yy==5?ground:new Chunk());
                    }
                    set("jeep",new dev.jayms.player.Jeep(new Vector3f(0,81.01f,0),-90));
                    ((dev.jayms.player.Player)get("player")).driveSeat(new Vector3f(3,81.01f,0),-90);
                } catch(Exception e) { throw new RuntimeException(e); } });
                for(var type:dev.jayms.player.CargoVehicle.values()) {
                    require(vehicle().type()==type,"Body selection "+type);
                    if(type==dev.jayms.player.CargoVehicle.CONTAINER) x("key","F10");
                    x("key","j"); require(vehicle().driving(),"Enter "+type);
                    x("key","shift+j"); require(vehicle().type()==type,"Driving body change rejected");
                    Vector3f before=vehicle().position();
                    x("keydown","w");
                    for(int i=0;i<200 && vehicle().speed()<1.5f;i++) Thread.sleep(100);
                    require(vehicle().speed()>=1.5f,"Reached moving speed "+type);
                    require(before.distance(vehicle().position())>.2f,"Drive "+type);
                    x("key","j"); require(vehicle().driving(),"Moving exit rejected "+type);
                    x("keyup","w");
                    for(int i=0;i<200 && vehicle().speed()>.05f;i++) Thread.sleep(100);
                    require(vehicle().speed()<=.05f,"Braked "+type);
                    x("key","j"); require(!vehicle().driving(),"Stopped exit "+type);
                    x("key","j"); require(vehicle().driving(),"Re-enter from cab exit "+type);
                    x("key","j"); require(!vehicle().driving(),"Second stopped exit "+type);
                    Path save=out.resolve("test.jeep"); vehicle().save(save);
                    require(dev.jayms.player.Jeep.load(save,(World)get("world"),vehicle().position()).type()==type,"Save reload "+type);
                    onFrame(() -> { try {
                        set("isometric",true);
                        Vector3f p=vehicle().position(); ((IsometricCamera)get("overview")).focus(p.x,p.z,p.y+1.5f);
                        var overview=(IsometricCamera)get("overview");
                        overview.cityMode();
                        overview.zoom(java.lang.Math.log(256f/overview.zoom())/java.lang.Math.log(1.15));
                    } catch(Exception e) { throw new RuntimeException(e); } });
                    Thread.sleep(500); capture="cargo-"+type.name().toLowerCase()+".png"; Thread.sleep(600);
                    onFrame(() -> { try { set("isometric",false); } catch(Exception e) { throw new RuntimeException(e); } });
                    if(type==dev.jayms.player.CargoVehicle.CONTAINER) { x("key","F10"); Thread.sleep(1200); }
                    beside(); x("key","shift+j");
                }
                require(vehicle().type()==dev.jayms.player.CargoVehicle.JEEP,"Cycle returns to jeep");
                // A wall beyond the jeep footprint must reject the longer container truck.
                onFrame(() -> { try {
                    var p=vehicle().position(); ((World)get("world")).getLoadedChunks().get(new ChunkPos((int)java.lang.Math.floor(p.x/16),5,(int)java.lang.Math.floor((p.z+4)/16))).setBlock(java.lang.Math.floorMod((int)java.lang.Math.floor(p.x),16),2,java.lang.Math.floorMod((int)java.lang.Math.floor(p.z+4),16),Blocks.STONE);
                } catch(Exception e) { throw new RuntimeException(e); } });
                x("key","shift+j"); require(vehicle().type()==dev.jayms.player.CargoVehicle.JEEP,"Larger body rejects wall");
                Files.writeString(out.resolve("results.json"),"{\"status\":\"passed\",\"platform\":\"Linux X11 inherited role display; Mesa\",\"profile\":\"isolated synthetic offline yard\",\"checks\":[\"all five bodies selected via real Shift+J\",\"enter drive brake exit via real J/W\",\"moving exit and driving body switch rejected\",\"all body types survive save reload\",\"longer truck rejects wall\",\"cycle returns to original jeep\"],\"media\":\"five production renderer captures\"}\n");
            } catch(Throwable e) { failure=e; try { Files.writeString(out.resolve("failure.txt"),e.getClass().getSimpleName()+": "+e.getMessage()); } catch(Exception ignored) {} }
            finally { if(handle!=0) glfwSetWindowShouldClose(handle,true); }
        }); driver.setDaemon(true); driver.start();
        game.run(new Main.FrameObserver() {
            public void afterFrame(Main main) throws Exception {
                Runnable task=action; if(task!=null) { action=null; task.run(); }
                screenMatrix=new Matrix4f((Matrix4f)get("projection")).mul((Matrix4f)get("view"));
                frames++;
                String name=capture; if(name==null) return; capture=null;
                int w=width(),h=height();
                var bytes=java.nio.ByteBuffer.allocateDirect(w*h*4);
                org.lwjgl.opengl.GL33.glReadPixels(0,0,w,h,org.lwjgl.opengl.GL33.GL_RGBA,org.lwjgl.opengl.GL33.GL_UNSIGNED_BYTE,bytes);
                var image=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_RGB);
                for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
                    int i=(y*w+x)*4;
                    image.setRGB(x,h-1-y,(bytes.get(i)&255)<<16 | (bytes.get(i+1)&255)<<8 | (bytes.get(i+2)&255));
                }
                javax.imageio.ImageIO.write(image,"png",out.resolve(name).toFile());
            }
        });
        driver.join(1000);
        if(failure!=null) throw new AssertionError("Cargo vehicle playtest failed",failure);
        require(Files.exists(out.resolve("results.json")),"Driver finished");
    }
}
