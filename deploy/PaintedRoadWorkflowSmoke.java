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
public class PaintedRoadWorkflowSmoke {
    static Thread gameThread;
    static final java.util.concurrent.ConcurrentLinkedQueue<java.util.concurrent.FutureTask<?>> gameTasks =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

    static <T> T onGame(java.util.concurrent.Callable<T> action) throws Exception {
        if (Thread.currentThread() == gameThread) return action.call();
        var task = new java.util.concurrent.FutureTask<T>(action);
        gameTasks.add(task);
        try {
            return task.get(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException | InterruptedException e) {
            task.cancel(false);
            gameTasks.remove(task);
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw e;
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof Exception cause) throw cause;
            if (e.getCause() instanceof Error cause) throw cause;
            throw e;
        }
    }

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
                .transform(new Vector4f(x+.5f,top(x,z),z+.5f,1));
        int px = java.lang.Math.round((p.x/p.w*.5f+.5f)*width());
        int py = java.lang.Math.round((.5f-p.y/p.w*.5f)*height());
        require(px>16 && px<width()-16 && py>=130 && py<height()-200,"Road point within viewport: world="+x+","+z+" screen="+px+","+py);
        click(px,py);
    }
    static float top(float x,float z) throws Exception {
        return onGame(() -> {
            var world=(World)get("world");
            for(int y=Terrain.MAX_Y;y>=Terrain.MIN_Y;y--)
                if(world.sample((int)java.lang.Math.floor(x),y,(int)java.lang.Math.floor(z))!=0) return y+1.04f;
            return Terrain.MIN_Y+1.04f;
        });
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
        gameThread=Thread.currentThread();
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
                camera.zoom(8);
                Thread.sleep(1500);
                onGame(() -> {
                    var world=(World)get("world");int grade=city().roads().get(0).y();
                    for(int px=30;px<=110;px++)for(int pz=150;pz<=260;pz++) {
                        world.setBlock(px,grade,pz,Blocks.DIRT);
                        for(int py=grade+1;py<=Terrain.MAX_Y;py++)if(world.sample(px,py,pz)!=0)world.setBlock(px,py,pz,0);
                    }
                    return null;
                });
                Thread.sleep(2000);
                var local=(LocalGame)get("local");onGame(() -> {local.city.economy.budget=0;return null;});
                camera.focus(60,160,city().roads().get(0).y()+1);x("getwindowfocus");menu(1);x("key","F10");
                point(40,160);point(60,160);
                require(get("notice").toString().contains("budget"),"Budget rejection observed");
                require(city().roads().stream().noneMatch(r->r.x()==50&&r.z()==160),"Rejected road absent");
                capture="road-rejected-anchor.png";x("getwindowfocus");
                onGame(() -> {local.city.economy.budget=100000;return null;});point(80,160);
                require(city().roads().stream().anyMatch(r->r.x()==50&&r.z()==160),"Retry starts at original anchor, no gap");
                require(city().roads().stream().anyMatch(r->r.x()==70&&r.z()==160),"Retry reaches new endpoint");
                camera.focus(80,160,city().roads().get(0).y()+1);x("getwindowfocus");point(100,160);require(city().roads().stream().anyMatch(r->r.x()==90&&r.z()==160),"Successful retry advances chain");
                capture="road-retry-connected.png";x("getwindowfocus");Thread.sleep(2000);
                x("key","Escape");point(60,160);var tools=(CityTools)get("cityTools");require(tools.selectedStreet!=0,"Retry road selectable");
                click(80,218);click(80,154+28*2);require(RoadGeometry.section(city(),tools.selectedStreet).stream().allMatch(r->r.type()==2),"Edit retry road");
                click(80,246);require(city().roads().stream().noneMatch(r->r.x()==50&&r.z()==160),"Delete retry road");
                camera.focus(50,190,city().roads().get(0).y()+1);x("getwindowfocus");menu(1);point(40,180);point(60,200);
                require(city().roads().stream().noneMatch(r->r.x()==50&&r.z()==180),"No unwanted first cardinal leg");
                require(city().roads().stream().noneMatch(r->r.x()==60&&r.z()==190),"No unwanted second cardinal leg");
                require(city().roads().stream().anyMatch(r->r.x()==50&&r.z()==190),"Straight diagonal center built");
                capture="road-diagonal.png";x("getwindowfocus");Thread.sleep(2000);
                x("key","Escape");point(50,190);
                require(tools.selectedStreet!=0,"Diagonal road selectable");
                int diagonalId=tools.selectedStreet;
                click(80,218);click(80,154+28*3);
                require(RoadGeometry.section(city(),diagonalId).stream().allMatch(r->r.type()==3),"Diagonal widens to four lanes");
                require(city().roads().stream().noneMatch(r->r.x()==50&&r.z()==180),"Edit keeps diagonal heading");
                capture="road-diagonal-edited.png";x("getwindowfocus");Thread.sleep(1000);
                click(80,246);require(RoadGeometry.section(city(),diagonalId).isEmpty(),"Diagonal deletion removes section");
                for(int type=0;type<=3;type++) {
                    int base=210+type*10;
                    camera.focus(50,base+3,city().roads().get(0).y()+1);x("getwindowfocus");menu(type);
                    point(40,base);point(60,base+7);
                    require(city().roads().stream().anyMatch(r->r.x()==50&&java.lang.Math.abs(r.z()-(base+3))<=1),"Shallow heading built type "+type);
                    require(city().roads().stream().noneMatch(r->r.x()==55&&r.z()==base),"Shallow heading has no cardinal leg type "+type);
                    x("key","Escape");
                }
                capture="road-shallow.png";x("getwindowfocus");Thread.sleep(1000);
                x("key","F10");Thread.sleep(2000);
                Files.writeString(out.resolve("results.json"),"{\"status\":\"passed\",\"checks\":[\"budget rejection retains anchor\",\"retry has no gap\",\"success advances chain\",\"selection/edit/delete regression\",\"straight diagonal road\",\"diagonal edit/delete\",\"shallow headings for all road types\"],\"profile\":\"isolated synthetic city\",\"platform\":\"Linux inherited X11 Mesa\"}");
            } catch(Throwable e) {
                failure=e;
                try { Files.writeString(out.resolve("failure.txt"),e.getClass().getSimpleName()+": "+e.getMessage()); } catch(Exception ignored) {}
            } finally { if(handle!=0) glfwSetWindowShouldClose(handle,true); }
        });
        driver.setDaemon(true);driver.start();
        game.run(new Main.FrameObserver() {
            public void beforeFrame(Main main) { for(var task=gameTasks.poll();task!=null;task=gameTasks.poll())task.run(); }
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
