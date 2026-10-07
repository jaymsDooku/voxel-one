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
public class ParcelWorkflowSmoke {
    static Main game = new Main();
    static volatile Throwable failure;
    static Path out;
    static String windowId;
    static volatile String capture;
    static volatile long frames;
    static volatile boolean levelSite,siteReady;
    static volatile Matrix4f screenMatrix;
    static volatile CityFrame latestCity;
    static final java.util.concurrent.ConcurrentLinkedQueue<java.util.concurrent.FutureTask<?>> frameTasks=new java.util.concurrent.ConcurrentLinkedQueue<>();
    static <T> T onFrame(java.util.concurrent.Callable<T> action) throws Exception {
        var task=new java.util.concurrent.FutureTask<T>(action);frameTasks.add(task);return task.get(30,java.util.concurrent.TimeUnit.SECONDS);
    }
    static void choose(CityTools tools,int algorithm) throws Exception {
        while(tools.parcelAlgorithm!=algorithm) x("key",java.lang.Math.floorMod(algorithm-tools.parcelAlgorithm,15)<=7?"bracketright":"bracketleft");
    }
    static Object get(String name) throws Exception {
        Field f = Main.class.getDeclaredField(name); f.setAccessible(true); return f.get(game);
    }
    static void set(String name,Object value) throws Exception {
        Field f = Main.class.getDeclaredField(name); f.setAccessible(true); f.set(game,value);
    }
    static CityFrame readCity() throws Exception {
        Method m = Main.class.getDeclaredMethod("city"); m.setAccessible(true); return (CityFrame)m.invoke(game);
    }
    static CityFrame city() throws Exception { return latestCity!=null?latestCity:readCity(); }
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
    static void point(float x,float z) throws Exception {
        var p = new Matrix4f(screenMatrix)
                .transform(new Vector4f(x,city().roads().get(0).y()+1.03f,z,1));
        int px = java.lang.Math.round((p.x/p.w*.5f+.5f)*width());
        int py = java.lang.Math.round((.5f-p.y/p.w*.5f)*height());
        require(px>16 && px<width()-16 && py>=130 && py<height()-200,"Road point within viewport: world="+x+","+z+" screen="+px+","+py);
        click(px,py);
    }
    static void menu(int type) throws Exception {
        click((int)(16+1.5f*(width()-32)/9f),height()-180);
        var tools = (CityTools)get("cityTools");
        capture="road-workflow-menu.png"; Thread.sleep(500);
        require(tools.roadMenu,"Roads opens menu; tool="+tools.tool+" mouse="+get("mouseX")+","+get("mouseY")+" size="+width()+"x"+height()+" controls="+((ControlsMenu)get("menu")).open);
        click(80,154+28*type);
        require(!tools.roadMenu && tools.roadType==type && tools.tool==4,"Choice starts road guide");
    }
    public static void main(String[] args) throws Exception {
        out=Path.of(args[0]); Files.createDirectories(out);
        set("offlineSave",out.resolve("synthetic-world.dat"));
        set("gameConfig",new GameConfig(true,false,1200,10));
        glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);
        var driver=new Thread(() -> {
            long handle=0;
            try {
                for(int i=0;i<120 && get("player")==null;i++) Thread.sleep(500);
                require(get("player")!=null,"Game started");
                Thread.sleep(2500);
                handle=((dev.jayms.window.Window)get("window")).getHandle();
                String id=Long.toString(org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window(handle));
                windowId=id;
                x("windowfocus",id);
                x("windowsize",id,"1280","720");
                x("windowmove",id,"0","0");
                x("windowraise",id);
                x("mousemove","--window",id,"640","360");
                x("windowfocus",id);
                Thread.sleep(1200);
                if(((ControlsMenu)get("menu")).open) x("key","Escape");
                capture="initial-state.png"; Thread.sleep(2000);
                require(!((ControlsMenu)get("menu")).open,"Controls closed before road clicks; frames="+frames+" fps="+get("fps"));
                require((boolean)get("isometric"),"City view");
                var local=(LocalGame)get("local");
                Field build=CitySimulation.class.getDeclaredField("nextBuild");build.setAccessible(true);onFrame(()->{build.setDouble(local.city,Double.MAX_VALUE);return null;});
                var camera=(IsometricCamera)get("overview");
                camera.focus(98,153,city().roads().get(0).y()+1);camera.zoom(-6);Thread.sleep(1500);
                levelSite=true;for(int i=0;i<100&&!siteReady;i++)Thread.sleep(100);
                require(siteReady,"Synthetic parcel site leveled");Thread.sleep(500);
                onFrame(()->{local.city.economy.budget=100000;return null;});
                menu(0);point(78,140);point(120,140);x("key","Escape");
                click((int)(16+2.5f*(width()-32)/9f),height()-180);
                var tools=(CityTools)get("cityTools");require(tools.tool==0,"Residential zoning tool selected");
                require(tools.parcelAlgorithm==2,"Grid default selected");
                point(80,142);point(116,142);point(116,166);point(80,166);x("key","Return");
                var zone=city().zones().get(city().zones().size()-1);
                require(zone.polygon().contains(98,154)&&!zone.parcels().isEmpty(),"UI creates zone with grid parcels: "+get("notice")+"; tool message="+tools.message+"; zone="+zone.polygon());
                int zoneId=zone.id();
                capture="parcel-grid.png";x("getwindowfocus");Thread.sleep(600);
                int layouts=0;
                for(int algorithm=0;algorithm<15;algorithm++) {
                    choose(tools,algorithm);
                    int beforeAlgorithm=zone.algorithm();
                    x("key","v");require(tools.parcelPreview,"Compare preview on");
                    x("getwindowfocus");
                    require(city().zones().stream().filter(z->z.id()==zoneId).findFirst().orElseThrow().algorithm()==beforeAlgorithm,"Preview does not alter saved layout");
                    x("key","v");x("key","p");point(98,154);
                    zone=city().zones().stream().filter(z->z.id()==zoneId).findFirst().orElseThrow();
                    require(zone.algorithm()==algorithm,"Applied layout "+algorithm+": "+get("notice"));
                    dev.jayms.net.city.parcel.ZoneParceling.validate(zone.polygon(),zone.parcels());
                    require(get("notice").toString().contains("parcels"),"Parcel report shown");layouts++;
                    if(algorithm==0||algorithm==1||algorithm==4||algorithm==8||algorithm==12||algorithm==13) {
                        capture="parcel-layout-"+algorithm+".png";x("getwindowfocus");Thread.sleep(500);
                    }
                }
                x("key","F10");
                choose(tools,2);x("key","p");point(98,154);
                zone=city().zones().stream().filter(z->z.id()==zoneId).findFirst().orElseThrow();
                require(!dev.jayms.net.city.parcel.ZoneParceling.sites(zone).isEmpty(),"Grid has full building footprints");
                onFrame(()->{local.save();return null;});var restored=CitySimulation.load(CitySaves.sidecar(out.resolve("synthetic-world.dat"),".city"));
                require(restored.zones().stream().filter(z->z.id()==zoneId).findFirst().orElseThrow().equals(zone),"Real city save preserves selected parcels");
                // Isolate this candidate zone for one production construction pass; keep the rest of the fixture unchanged.
                Field zonesField=CitySimulation.class.getDeclaredField("zones");zonesField.setAccessible(true);
                var constructionZone=zone;
                onFrame(()->{
                    Object originalZones=zonesField.get(local.city);zonesField.set(local.city,new ArrayList<>(List.of(constructionZone)));
                    Method construct=CitySimulation.class.getDeclaredMethod("construct");construct.setAccessible(true);
                    try {construct.invoke(local.city);} finally {zonesField.set(local.city,originalZones);}
                    return null;
                });x("getwindowfocus");
                var plot=city().economy().plots().stream().filter(p->p.zone()==zoneId).findFirst().orElseThrow(()->new AssertionError("Production construction bought a parcel site"));
                var selectedZone=zone;
                require(zone.parcels().stream().anyMatch(p->dev.jayms.net.city.parcel.ZoneParceling.fits(p,plot.x(),plot.z(),selectedZone.type())),"Purchased building fits inside one parcel");
                x("key","bracketright");x("key","p");point(98,154);
                require(get("notice").toString().contains("without buildings or purchased plots"),"Occupied-zone layout change rejected");
                require(city().zones().stream().filter(z->z.id()==zoneId).findFirst().orElseThrow().equals(zone),"Rejected change preserves parcels");
                capture="parcel-protected-plot.png";x("getwindowfocus");Thread.sleep(1000);
                x("key","Escape");
                // Road guide regression still uses real UI input.
                camera.focus(98,182,city().roads().get(0).y()+1);x("getwindowfocus");menu(0);point(80,182);point(116,182);
                require(city().roads().stream().anyMatch(r->r.x()==98&&r.z()==182),"Road placement regression");
                x("key","Escape");x("key","F10");Thread.sleep(1500);
                Files.writeString(out.resolve("results.json"),"{\"status\":\"passed\",\"layouts\":"+layouts+",\"checks\":[\"real UI zoning and grid parcels\",\"15 layout choices applied\",\"preview does not mutate zones\",\"partition invariants\",\"real save/reload\",\"production construction fits parcel\",\"purchased plot protects layout\",\"road placement regression\"],\"setup\":\"synthetic city; autonomous purchasing deferred during comparisons; one construction pass isolated to tested zone\",\"profile\":\"isolated synthetic city\",\"platform\":\"Linux inherited X11 Mesa\"}");
            } catch(Throwable e) {
                failure=e;
                try { Files.writeString(out.resolve("failure.txt"),e.getClass().getSimpleName()+": "+e.getMessage()); } catch(Exception ignored) {}
            } finally { if(handle!=0) glfwSetWindowShouldClose(handle,true); }
        });
        driver.setDaemon(true);driver.start();
        game.run(new Main.FrameObserver() {
            public void afterFrame(Main main) throws Exception {
                if(levelSite&&!siteReady) {
                    var world=(World)get("world");var local=(LocalGame)get("local");int grade=city().roads().get(0).y();
                    for(int x=70;x<=130;x++)for(int z=130;z<=195;z++) {
                        for(int y=grade+1;y<=Terrain.MAX_Y;y++)if(world.sample(x,y,z)!=Blocks.AIR) {
                            var e=new Protocol.Edit(x,y,z,Blocks.AIR);world.apply(e);WorldVoxels.remember(local.edits,e);
                        }
                        var e=new Protocol.Edit(x,grade,z,Blocks.GRASS);world.apply(e);WorldVoxels.remember(local.edits,e);
                    }
                    siteReady=true;
                }
                java.util.concurrent.FutureTask<?> frameTask;
                while((frameTask=frameTasks.poll())!=null)frameTask.run();
                latestCity=readCity();
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
        if(failure!=null) throw new AssertionError("Parcel playtest failed",failure);
        require(Files.exists(out.resolve("results.json")),"Driver finished");
    }
}
