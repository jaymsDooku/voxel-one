import dev.jayms.*;
import dev.jayms.player.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.*;
import org.joml.Matrix4f;
import org.joml.Vector4f;
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
public final class RailwayAirportPlaytest {
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
    static volatile boolean accelerate, sawWaiting, sawRiding, sawArrival;
    static CitySimulation city() throws Exception {return ((LocalGame)get("local")).city;}
    static CityTools tools() throws Exception {return (CityTools)get("cityTools");}
    static void click(int px,int py) throws Exception {x("mousemove","--sync",Integer.toString(px),Integer.toString(py));x("click","1");}
    static void hoverPoint(float wx,float wz) throws Exception {
        Matrix4f p=new Matrix4f((Matrix4f)get("projection")),v=new Matrix4f((Matrix4f)get("view"));
        int width=(int)get("framebufferWidth"),height=(int)get("framebufferHeight");
        var q=p.mul(v).transform(new Vector4f(wx,city().frame().roads().get(0).y()+1.03f,wz,1));
        int px=Math.round((q.x/q.w*.5f+.5f)*width),py=Math.round((.5f-q.y/q.w*.5f)*height);
        require(py>=130&&py<=height-200,"World click lies in planning viewport: "+wx+","+wz+" -> "+px+","+py);x("mousemove","--sync",Integer.toString(px),Integer.toString(py));
    }
    static void point(float wx,float wz) throws Exception { hoverPoint(wx,wz);x("click","1"); }
    static void focus(float wx,float wz) throws Exception {renderEdit(()->{try{var o=(IsometricCamera)get("overview");o.focus(wx,wz,city().frame().roads().get(0).y()+1);o.zoom(-7);}catch(Throwable e){failure=e;}});Thread.sleep(300);}
    static void toolbar(int index) throws Exception {click(Math.round(16+(index+.5f)*((1280-32)/9f)),534);}
    static void permit(int row,float wx,float wz,int expected) throws Exception {
        toolbar(1);click(200,149+(row==9 ? 5 : 6)*28);focus(wx,wz);point(wx+.2f,wz+.2f);
        require(city().frame().buildings().size()==expected,"Rail permit placement: "+get("notice").toString());
    }

    static void airportMenu(int row) throws Exception {
        toolbar(7);require(tools().tool==6,"Special menu opens");
        int h=Math.max(12,Math.min(28,((int)get("framebufferHeight")-340)/13));click(80,140+h*row+h/2);
        require(tools().tool==row-2,"Airport menu action");
    }
    static void unchanged(CityFrame before, Map<List<Integer>,Integer> blocks) throws Exception {
        var after=city().frame();
        require(after.buildings().equals(before.buildings()),"No changed buildings");
        require(after.railway().equals(before.railway()),"No changed railway state");
        require(after.economy().budget()==before.economy().budget(),"No spending");
        require(testGround.blocks.equals(blocks),"No voxel edits");
    }
    public static void main(String[] args) throws Exception {
        output=Path.of(args[0]);Files.createDirectories(output);
        glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);
        game=new Main();set("offlineSave",output.resolve("synthetic-world.dat"));
        Thread driver=new Thread(()->{
            long handle=0;
            try {
                await(()->ready&&frames>=3,"Application ready");
                handle=((dev.jayms.window.Window)get("window")).getHandle();
                String id=Long.toString(org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window(handle));x("windowfocus",id);Thread.sleep(500);
                if(((ControlsMenu)get("menu")).open)x("key","Escape");
                if((boolean)get("captured"))x("key","Tab");
                focus(90,56);toolbar(1);click(200,149+4*28);
                point(48.2f,60.2f);point(132.2f,60.2f);
                require(city().frame().railway().tracks().size()==85,"Initial 85 rail cells");
                x("key","F10");
                var before=city().frame();var blocks=Map.copyOf(testGround.blocks);
                airportMenu(10);focus(62,58);point(50.2f,52.2f);
                require(get("notice").toString().equals("Airport cannot cover rails"),"Airport overlap rejected");
                unchanged(before,blocks);capture="railway-airport-rejected.png";await(()->capture==null,"Airport rejection captured");
                // Fresh synthetic city removes the first rail route for the expansion case.
                renderEdit(()->{try{testGround.blocks.clear();((LocalGame)get("local")).city=RailwayTest.fixture(testGround);}catch(Throwable e){failure=e;}});
                airportMenu(10);focus(62,58);point(50.2f,52.2f);
                require(get("notice").toString().startsWith("Permitted Airport"),"Clear airport permit succeeds");
                focus(68,84);toolbar(1);click(200,149+4*28);point(50.2f,84.2f);point(85.2f,84.2f);
                require(city().frame().railway().tracks().size()==36,"Expansion boundary rail cells");
                before=city().frame();blocks=Map.copyOf(testGround.blocks);
                airportMenu(11);focus(62,58);point(52.2f,54.2f);
                require(get("notice").toString().equals("Airport cannot cover rails"),"Runway expansion overlap rejected");
                unchanged(before,blocks);capture="railway-runway-rejected.png";await(()->capture==null,"Runway rejection captured");
                airportMenu(10);focus(112,58);point(100.2f,52.2f);
                require(city().frame().buildings().size()==2,"Adjacent clear airport succeeds");
                airportMenu(11);point(102.2f,54.2f);
                require(get("notice").toString().equals("Airport expanded to 2 runways"),"Adjacent clear expansion succeeds");
                require(city().frame().railway().tracks().size()==36,"Rail cells survive adjacent construction");
                capture="railway-airport-adjacent.png";await(()->capture==null,"Adjacent airport captured");
                x("key","F10");Thread.sleep(3500);
                renderEdit(()->{try{city().save(output.resolve("airport-rail.city"));var saved=CitySimulation.load(output.resolve("airport-rail.city"));require(saved.railway().equals(city().frame().railway()),"Rails reload with airports");require(saved.buildings().equals(city().frame().buildings()),"Airports reload");}catch(Throwable e){failure=e;}});
                Files.writeString(output.resolve("results.json"),"{\"status\":\"passed\",\"checks\":[\"85-cell airport overlap atomic rejection\",\"36-cell runway overlap atomic rejection\",\"clear airport and adjacent runway expansion\",\"rail and airport save reload\"],\"input\":\"native GLFW callbacks via xdotool\",\"profile\":\"isolated synthetic\",\"recording\":\"F10\"}\n");
            }catch(Throwable e){failure=e;try{Files.writeString(output.resolve("failure.txt"),e.getClass().getSimpleName()+": "+e.getMessage());}catch(Exception ignored){}}
            finally{done=true;if(handle!=0)glfwSetWindowShouldClose(handle,true);}
        });driver.setDaemon(true);
        try { game.run(new Main.FrameObserver(){
            public void started(Main g) throws Exception {
                World old=(World)get("world"),world=new World(old.terrain().seed,old.models());int grade=world.terrain().column(8,24).height();
                for(int cx=2;cx<=8;cx++)for(int cz=2;cz<=5;cz++)for(int cy=-2;cy<=7;cy++){
                    var chunk=new Chunk();if(cy==Math.floorDiv(grade,16))for(int x=0;x<16;x++)for(int z=0;z<16;z++)chunk.setBlock(x,Math.floorMod(grade,16),z,Blocks.DIRT);
                    world.addChunk(new ChunkPos(cx,cy,cz),chunk);
                }
                set("world",world);old.close();
                testGround=new RailwayTest.Ground(grade){public void apply(List<Protocol.Edit> edits){super.apply(edits);for(var e:edits)world.apply(e);}};
                var fixture=RailwayTest.fixture(testGround);((LocalGame)get("local")).city=fixture;
                set("player",new Player(new Vector3f(85,grade+15,40),-135,-35,(Camera)get("camera")));
                set("isometric",true);var overview=(IsometricCamera)get("overview");overview.cityMode();overview.focus(90,56,grade+1);overview.zoom(-7);
                ready=true;driver.start();
            }
            public void beforeFrame(Main g) throws Exception {
                Runnable edit;while((edit=edits.poll())!=null)edit.run();
                if(accelerate)city().advance(1.9);
                if(!city().frame().citizens().isEmpty()){
                    var c=city().frame().citizens().get(0);sawWaiting|=c.activity().contains("rail station");sawRiding|=c.activity().equals("Riding steam train");
                    sawArrival|=sawRiding&&c.x()>85&&city().frame().railway().trains().stream().noneMatch(t->t.passengers().stream().anyMatch(p->p.citizen()==7));
                    if(accelerate&&sawRiding&&!sawArrival) {var train=city().frame().railway().trains().get(0);var o=(IsometricCamera)get("overview");o.focus(train.x(),train.z(),train.y());o.zoom(4);}

                }
            }
            public void afterFrame(Main g){frames++;if(capture!=null){String name=capture;capture=null;screenshot(name);}}
        }); } catch(Throwable startup) {
            String message=startup.getClass().getSimpleName()+": "+startup.getMessage();
            Files.writeString(output.resolve("failure.txt"),message);
            throw startup;
        }
        driver.join(1000);if(failure!=null)throw new AssertionError("Railway playtest failed",failure);require(done&&Files.exists(output.resolve("results.json")),"Playtest completed");
    }
    static RailwayTest.Ground testGround;
}
