import dev.jayms.*;
import dev.jayms.player.*;
import dev.jayms.net.Blocks;
import dev.jayms.net.LocalGame;
import dev.jayms.net.city.GameConfig;
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
public final class CheatPlaytest {
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
        game=new Main(); set("offlineSave",output.resolve("synthetic-world.dat")); set("gameConfig", new GameConfig(true,false,1200,10));
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
                x("key","Insert"); require(get("notice").toString().contains("first"),"Money disabled outside cheat mode");
                x("key","F4"); require((boolean)get("cheatMode") && player().flying(),"F4 enters cheat mode and enables flight");
                x("key","F10");
                float initialY=player().position().y;
                x("keydown","space"); await(()->player().position().y>initialY+2,"Space flies up"); x("keyup","space");
                float high=player().position().y;
                x("keydown","Control_L"); await(()->player().position().y<high-1,"Ctrl flies down"); x("keyup","Control_L");
                capture="cheat-flight.png"; await(()->capture==null,"Flight image");
                renderEdit(()->{try { player().driveSeat(new Vector3f(-30,33.01f,24),-90); if(!player().flying()) player().toggleFlight(); }catch(Throwable e){failure=e;}});
                double budget=((LocalGame)get("local")).city.economy.budget;
                x("key","Insert"); require(((LocalGame)get("local")).city.economy.budget==budget+10000,"Insert adds exactly $10,000");
                renderEdit(()->{try { ((LocalGame)get("local")).save(); }catch(Throwable e){failure=e;}});
                var saved=dev.jayms.net.city.CitySimulation.load(output.resolve("synthetic-world.dat.city"));
                require(saved.economy().budget()==budget+10000,"Budget persists in city save");
                for(int i=0;i<5;i++) {
                    final int index=i;
                    renderEdit(()->{try {player().driveSeat(new Vector3f(-30+index*15,33.01f,24),-90); if(!player().flying()) player().toggleFlight();}catch(Throwable e){failure=e;}});
                    x("key","Next");
                    require(jeep().type()==CargoVehicle.values()[i] && get("notice").toString().startsWith("Spawned"),"Spawn road vehicle "+i+": "+get("notice"));
                    capture="cheat-vehicle-"+i+".png"; await(()->capture==null,"Vehicle image");
                    x("key","Next"); require(get("notice").toString().contains("overlaps"),"Repeated spawn rejects occupied vehicle area");
                    if(i<4) x("key","Prior");
                }
                // Enter the lorry through the ordinary vehicle control and reject spawning while driving.
                renderEdit(()->{try {player().driveSeat(jeep().seat().add(2.7f,0,0),-90);}catch(Throwable e){failure=e;}});
                x("key","j"); require(jeep().driving(),"Spawned lorry is drivable");
                x("key","Next"); require(get("notice").toString().contains("Exit"),"Spawn while driving rejected");
                x("key","F4"); require((boolean)get("cheatMode"),"Leaving cheat mode while driving rejected");
                x("key","j"); require(!jeep().driving(),"Ordinary exit works");
                x("key","Prior");
                renderEdit(()->{try {player().driveSeat(new Vector3f(-25,33.01f,-6),-90);}catch(Throwable e){failure=e;}});
                x("key","Next"); require(get("notice").toString().startsWith("Spawned Passenger jet"),"Jet spawn: "+get("notice"));
                capture="cheat-jet.png"; await(()->capture==null,"Jet image");
                x("key","Prior");
                renderEdit(()->{try {player().driveSeat(new Vector3f(25,33.01f,-6),-90);}catch(Throwable e){failure=e;}});
                x("key","Next"); require(get("notice").toString().startsWith("Spawned Cargo carrier"),"Carrier spawn: "+get("notice"));
                capture="cheat-carrier.png"; await(()->capture==null,"Carrier image");
                x("key","Prior"); require((int)get("cheatVehicle")==0,"Vehicle selection wraps to Jeep");
                renderEdit(()->{try {player().driveSeat(new Vector3f(0,33.01f,42),-90); ((World)get("world")).setBlock(0,33,36,Blocks.BRICKS);}catch(Throwable e){failure=e;}});
                Jeep before=jeep(); x("key","Next"); require(jeep()==before && get("notice").toString().contains("Clear"),"Blocked spawn leaves vehicle unchanged");
                x("key","F4"); require(!(boolean)get("cheatMode") && !player().flying(),"Leave cheat mode disables flight");
                budget=((LocalGame)get("local")).city.economy.budget; x("key","Insert"); require(((LocalGame)get("local")).city.economy.budget==budget,"Disabled mode cannot add money");
                x("key","f"); require(player().flying(),"Existing F flight regression"); x("key","f"); require(!player().flying(),"Existing F flight off");
                x("key","F6"); require((boolean)get("isometric"),"F6 sky camera regression"); x("key","F6");
                x("key","F9"); require(((dev.jayms.ui.MayorDashboard)get("mayorDashboard")).open,"F9 mayor dashboard regression");
                x("mousemove","670","104"); x("click","1");
                Field tab=dev.jayms.ui.MayorDashboard.class.getDeclaredField("tab"); tab.setAccessible(true);
                require(tab.getInt(get("mayorDashboard"))==3,"Finances tab opened");
                capture="cheat-budget.png"; await(()->capture==null,"Budget image"); x("key","Escape");
                x("key","F4");
                double priorBudget=((LocalGame)get("local")).city.economy.budget;
                renderEdit(()->{try {((LocalGame)get("local")).city.economy.budget=990000001;}catch(Throwable e){failure=e;}});
                x("key","Insert"); require(((LocalGame)get("local")).city.economy.budget==990000001 && get("notice").toString().contains("limit"),"Budget cap rejects grant");
                renderEdit(()->{try {((LocalGame)get("local")).city.economy.budget=priorBudget; set("cheatSpawnCount",64);}catch(Throwable e){failure=e;}});
                x("key","Next"); require(get("notice").toString().contains("limit"),"Spawn cap rejects 65th vehicle");
                x("key","F4");
                x("key","F10"); Thread.sleep(3500);
                Files.writeString(output.resolve("results.json"),"{\"status\":\"passed\",\"platform\":\"Linux assigned X11 display; software OpenGL\",\"profile\":\"isolated synthetic city\",\"checks\":[\"explicit mode entry and exit\",\"flight up and down\",\"all five road vehicles spawn\",\"jet and carrier spawn\",\"selected kind wraps\",\"spawned lorry entry and exit\",\"spawn while driving rejected\",\"repeat and blocked spawn rejected\",\"exact treasury grant and saved budget\",\"money disabled outside mode\",\"existing F flight, F6 camera and F9 dashboard regressions\"],\"recording\":\"F10 engine recorder\"}\n");
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

                set("world",world); old.close();
                Player p=new Player(new Vector3f(-30f,33.01f,24f),-90,-18,(Camera)get("camera"));
                p.toggleView(); set("player",p); set("jeep",new Jeep(new Vector3f(40f,33.01f,42f),-90));
                set("isometric",false); ready=true; driver.start();
            }
            public void beforeFrame(Main g) { Runnable edit; while((edit=edits.poll())!=null) edit.run(); }
            public void afterFrame(Main g) { frames++; if(capture!=null){String name=capture; capture=null; screenshot(name);} }
        });
        driver.join(1000); if(failure!=null) throw new AssertionError("Jeep playtest failed",failure);
        require(done&&Files.exists(output.resolve("results.json")),"Playtest completed");
    }
}
