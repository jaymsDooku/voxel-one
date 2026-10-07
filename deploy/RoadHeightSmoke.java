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
public class RoadHeightSmoke {
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
                .transform(new Vector4f(x,top(x,z),z,1));
        int px = java.lang.Math.round((p.x/p.w*.5f+.5f)*width());
        int py = java.lang.Math.round((.5f-p.y/p.w*.5f)*height());
        require(px>16 && px<width()-16 && py>=130 && py<height()-200,"Road point within viewport: world="+x+","+z+" screen="+px+","+py);
        click(px,py);
    }
    static float top(float x,float z) throws Exception {
        var world=(World)get("world");
        for(int y=Terrain.MAX_Y;y>=Terrain.MIN_Y;y--) if(world.sample((int)java.lang.Math.floor(x),y,(int)java.lang.Math.floor(z))!=0) return y+1.04f;
        return Terrain.MIN_Y+1.04f;
    }
    static void menu(int type) throws Exception {
        click((int)(16+1.5f*(width()-32)/9f),height()-180);
        var tools = (CityTools)get("cityTools");
        capture="road-menu.png"; Thread.sleep(500);
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
                x("key","F10");
                // Select endpoints at unequal heights through real X11 input.
                var world=(World)get("world");
                int grade=city().roads().get(0).y();
                for(int px=74;px<=88;px++) for(int pz=65;pz<=95;pz++)
                    for(int py=grade+1;py<=grade+8;py++) world.apply(new Protocol.Edit(px,py,pz,2));
                camera.focus(70,80,grade+1); camera.zoom(-6); Thread.sleep(1800);
                menu(1); point(60,80);
                var endpoint=new Matrix4f(screenMatrix).transform(new Vector4f(80,top(80,80),80,1));
                int sx=java.lang.Math.round((endpoint.x/endpoint.w*.5f+.5f)*width());
                int sy=java.lang.Math.round((.5f-endpoint.y/endpoint.w*.5f)*height());
                x("mousemove","--window",windowId,Integer.toString(sx),Integer.toString(sy));
                Thread.sleep(600);
                var tools=(CityTools)get("cityTools");
                var selected=tools.cursorPoint(sx,sy,width(),height(),(Matrix4f)get("projection"),(Matrix4f)get("view"),city());
                require(selected!=null && java.lang.Math.abs(selected.x()-80)<=1 && java.lang.Math.abs(selected.z()-80)<=1,"Raised endpoint matches preview: "+selected);
                capture="height-guide-preview.png"; Thread.sleep(1200);
                point(80,80);
                require(city().roads().stream().anyMatch(r->r.x()==70 && java.lang.Math.abs(r.z()-80)<=1),"Road placed from surface-selected endpoints");
                capture="height-guide-built.png"; Thread.sleep(1200);
                // Edge: highest world surface; cancel must keep the city and spending intact.
                for(int px=54;px<=56;px++) for(int pz=89;pz<=91;pz++) world.apply(new Protocol.Edit(px,Terrain.MAX_Y,pz,2));
                camera.focus(55,90,Terrain.MAX_Y); Thread.sleep(700);
                int count=city().roads().size(); double spending=city().economy().roadSpending();
                menu(0); point(55,90); x("key","Escape");
                require(city().roads().size()==count && city().economy().roadSpending()==spending,"High surface cancellation atomic");
                // Regression: flat existing road still picks and opens a new section.
                camera.focus(70,80,grade+1); Thread.sleep(700);
                menu(1); point(65,80); x("key","Escape");
                require(tools.tool==-1,"Flat road selection and Escape");
                x("key","F10"); Thread.sleep(2000);
                Files.writeString(out.resolve("results.json"),"{\"status\":\"passed\",\"platform\":\"Linux X11 inherited role display; Mesa\",\"profile\":\"isolated synthetic offline city\",\"checks\":[\"unequal-height endpoint preview and real road placement\",\"MAX_Y surface selection and atomic cancel\",\"existing flat road selection and Escape\"]}\n");
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
