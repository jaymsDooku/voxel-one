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
public class RoadWorkflowSmoke {
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
                var camera=(IsometricCamera)get("overview");
                camera.focus(70,80,city().roads().get(0).y()+1);
                camera.zoom(11);
                Thread.sleep(1500);
                // All choices, actual world changes, distinct width and divider counts.
                for(int type=0;type<=3;type++) {
                    int z=64+type*10;
                    camera.focus(70,z,city().roads().get(0).y()+1);
                    x("getwindowfocus");
                    menu(type);
                    int before=city().roads().size();
                    point(60,z);
                    // Move to the endpoint without clicking: show production direction guide.
                    var p=new Matrix4f(screenMatrix)
                        .transform(new Vector4f(80,city().roads().get(0).y()+1.03f,z,1));
                    x("mousemove","--window",windowId,Integer.toString(java.lang.Math.round((p.x/p.w*.5f+.5f)*width())),
                        Integer.toString(java.lang.Math.round((.5f-p.y/p.w*.5f)*height())));
                    capture="road-workflow-guide.png"; Thread.sleep(500);
                    point(80,z);
                    final int t=type;
                    require(city().roads().size()>before,"Road placed type "+type);
                    var section=city().roads().stream().filter(r -> r.x()==70 && java.lang.Math.abs(r.z()-z)<=4).toList();
                    require(section.size()==RoadTypes.width(type),"Road width type "+type);
                    require(section.stream().allMatch(r -> r.type()==t),"Stored road type "+type);
                }
                x("key","F10");
                // The last endpoint is already the first point for each new section.
                camera.focus(95,99,city().roads().get(0).y()+1); x("getwindowfocus");
                point(100,94); point(100,104);
                require(city().roads().stream().anyMatch(r -> r.x()==90 && r.z()==94 && r.type()==3),"First chained section built");
                require(city().roads().stream().anyMatch(r -> r.x()==100 && r.z()==100 && r.type()==3),"Second chained section built");
                x("key","Escape");
                // Inspect the paved two-lane route, resize it, then delete it.
                camera.focus(70,74,city().roads().get(0).y()+1); x("getwindowfocus");
                point(70,74);
                var tools=(CityTools)get("cityTools");
                int selected=tools.selectedStreet;
                require(selected!=0,"Inspect selects road section");
                capture="road-workflow-selected.png"; Thread.sleep(1000);
                click(80,218); require(tools.roadMenu,"Edit opens road type menu");
                click(80,154+28*3);
                require(RoadGeometry.section(city(),selected).stream().allMatch(r -> r.type()==3),"Edit changes section to four lanes");
                require(city().roads().stream().filter(r -> r.x()==70 && java.lang.Math.abs(r.z()-74)<=3).count()==7,"Edit widens road");
                click(80,218); click(80,154+28);
                require(city().roads().stream().filter(r -> r.x()==70 && java.lang.Math.abs(r.z()-74)<=3).count()==3,"Edit narrows road and removes old shoulders");
                capture="road-workflow-edited.png"; Thread.sleep(800);
                click(80,246);
                require(RoadGeometry.section(city(),selected).isEmpty(),"Delete removes selected section");
                require(city().roads().stream().anyMatch(r->r.x()==70 && r.z()==64),"Delete preserves adjacent dirt route");
                // Build a zone touching the remaining dirt road, then aim through it.
                camera.focus(90,64,city().roads().get(0).y()+1); x("getwindowfocus");
                click((int)(16+2.5f*(width()-32)/9f),height()-180);
                point(74,66); point(86,66); point(86,78); point(74,78); x("key","Return");
                require(city().zones().stream().anyMatch(zone->zone.polygon().contains(80,70)),"Zone created with game tools: " + get("notice"));
                menu(1); point(100,74);
                var pSnap=new Matrix4f(screenMatrix).transform(new Vector4f(70,city().roads().get(0).y()+1.03f,74,1));
                x("mousemove","--window",windowId,Integer.toString(java.lang.Math.round((pSnap.x/pSnap.w*.5f+.5f)*width())),Integer.toString(java.lang.Math.round((.5f-pSnap.y/pSnap.w*.5f)*height())));
                capture="road-workflow-snap.png"; Thread.sleep(1000);
                point(70,74);
                require(city().roads().stream().anyMatch(r->r.x()==95 && r.z()==74 && r.type()==1),"Snapped road built before zone");
                var snapFrame=city();
                require(snapFrame.roads().stream().noneMatch(r->snapFrame.zones().stream().anyMatch(zone->zone.polygon().contains(r.x()+.5f,r.z()+.5f))),"Snap keeps footprint outside zones");
                x("key","Escape");
                // Upgrade dirt through the same menu and mouse workflow.
                camera.focus(70,64,city().roads().get(0).y()+1); x("getwindowfocus");
                menu(1); point(60,64); point(80,64);
                require(city().roads().stream().filter(r -> r.x()==70 && java.lang.Math.abs(r.z()-64)<=1)
                        .allMatch(r -> r.type()==1),"Dirt upgraded to paved");
                // Cancel after the first endpoint: no world or budget change.
                int count=city().roads().size(); double spending=city().economy().roadSpending();
                camera.focus(70,100,city().roads().get(0).y()+1); x("getwindowfocus");
                menu(3); point(60,100); x("key","Escape");
                require(((CityTools)get("cityTools")).tool==-1,"Escape returns to inspect");
                require(city().roads().size()==count && city().economy().roadSpending()==spending,"Cancel is atomic");
                // Menu can be cancelled before selection, too.
                click((int)(16+1.5f*(width()-32)/9f),height()-180);
                require(((CityTools)get("cityTools")).roadMenu,"Menu reopened");
                Thread.sleep(800); x("key","Escape");
                require(!((CityTools)get("cityTools")).roadMenu,"Menu escape");
                camera.focus(70,80,city().roads().get(0).y()+1); x("getwindowfocus");
                capture="road-workflow-surfaces.png"; Thread.sleep(1200); x("key","F10"); Thread.sleep(2000);
                Files.writeString(out.resolve("results.json"),"{\"status\":\"passed\",\"platform\":\"Linux X11 inherited role display; Mesa\",\"profile\":\"isolated synthetic offline city\",\"checks\":[\"road menu\",\"dirt and paved 2/3/4 lane placement\",\"direction guide\",\"chained sections\",\"road selection highlight\",\"widen and narrow edit\",\"delete preserves neighbor\",\"zone-edge snap and overlap rejection\",\"distinct world widths\",\"dirt upgrade\",\"cancel endpoint leaves world and road spending unchanged\",\"menu escape returns to inspect\"],\"recording\":\"production F10 recorder\"}\n");
            } catch(Throwable e) {
                failure=e;
                try { Files.writeString(out.resolve("failure.txt"),e.getClass().getSimpleName()+": "+e.getMessage()); } catch(Exception ignored) {}
            } finally { if(handle!=0) glfwSetWindowShouldClose(handle,true); }
        });
        driver.setDaemon(true);driver.start();
        game.run(new Main.FrameObserver() {
            public void afterFrame(Main main) throws Exception {
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
        if(failure!=null) throw new AssertionError("Road playtest failed",failure);
        require(Files.exists(out.resolve("results.json")),"Driver finished");
    }
}
