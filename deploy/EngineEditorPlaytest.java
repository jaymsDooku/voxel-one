import dev.jayms.Main;
import dev.jayms.ui.*;
import dev.jayms.net.city.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;

/** Synthetic profile, real X11 keys/clicks, production render loop. */
public class EngineEditorPlaytest {
    static Main game = new Main();
    static volatile Throwable failure;
    static volatile String capture;
    static volatile int frames;
    static Path out;
    static Object get(String name) throws Exception { var f=Main.class.getDeclaredField(name); f.setAccessible(true); return f.get(game); }
    static void set(String name,Object value) throws Exception { var f=Main.class.getDeclaredField(name); f.setAccessible(true); f.set(game,value); }
    static EngineEditor workspace() throws Exception { return (EngineEditor)get("engineEditor"); }
    static double elapsed() throws Exception { return ((dev.jayms.net.LocalGame)get("local")).city.frame().elapsed(); }
    interface Condition { boolean test() throws Exception; }
    static void await(Condition condition,String message) throws Exception {
        long deadline=System.nanoTime()+30_000_000_000L;
        while(!condition.test() && System.nanoTime()<deadline)Thread.sleep(100);
        require(condition.test(),message);
    }
    static void require(boolean b,String message) { if(!b) throw new AssertionError(message); }
    static String x(String... args) throws Exception {
        var cmd=new ArrayList<String>();cmd.add("xdotool");cmd.addAll(List.of(args));
        var p=new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        String result=new String(p.getInputStream().readAllBytes()).trim();
        require(p.waitFor()==0,"X11 input succeeded");Thread.sleep(1800);return result;
    }
    static void image(String name) throws Exception {
        capture=name; for(int i=0;i<100 && !Files.exists(out.resolve(name));i++) Thread.sleep(100);
        require(Files.exists(out.resolve(name)),"Fresh image captured");
    }
    static void captureFrame() throws Exception {
        frames++;
        String name=capture;if(name==null)return;capture=null;
        int w=(int)get("framebufferWidth"),h=(int)get("framebufferHeight");
        var pixels=java.nio.ByteBuffer.allocateDirect(w*h*4);
        org.lwjgl.opengl.GL33.glReadPixels(0,0,w,h,org.lwjgl.opengl.GL33.GL_RGBA,org.lwjgl.opengl.GL33.GL_UNSIGNED_BYTE,pixels);
        var img=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=(y*w+x)*4;img.setRGB(x,h-y-1,(pixels.get(i)&255)<<16|(pixels.get(i+1)&255)<<8|(pixels.get(i+2)&255));}
        javax.imageio.ImageIO.write(img,"png",out.resolve(name).toFile());
    }
    public static void main(String[] args) throws Exception {
        out=Path.of(args[0]);Files.createDirectories(out);
        set("offlineSave",Path.of("target/editor-profile/synthetic-city.dat").toAbsolutePath());
        set("gameConfig",GameConfig.cityGame());workspace().open=true;
        glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);
        var driver=new Thread(()->{
            long handle=0;
            try {
                for(int i=0;i<240 && frames<3;i++)Thread.sleep(500);
                require(frames>=3,"Game render loop ready");
                Thread.sleep(2500);
                handle=((dev.jayms.window.Window)get("window")).getHandle();
                String id=Long.toString(org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window(handle));x("windowfocus",id);
                await(()->workspace().open,"Workspace startup");
                boolean initialView=(boolean)get("isometric");
                double start=elapsed();x("key","w");x("key","F6");require(workspace().open && (boolean)get("isometric")==initialView,"Workspace consumes gameplay keys");
                require(elapsed()==start,"Local simulation paused");
                x("key","Up");await(()->workspace().selected==3,"Selection wraps at first node");
                x("key","Down");await(()->workspace().selected==0,"Selection wraps at last node");
                image("engine-editor-workspace.png");
                x("key","Return");await(()->!workspace().open,"Play enters game");await(()->elapsed()>start,"City time resumes");
                x("key","F6");await(()->(boolean)get("isometric")!=initialView,"Isometric game control preserved");
                image("engine-editor-city-play.png");
                x("key","F11");await(()->workspace().open,"Return to workspace");
                x("key","m");await(()->!workspace().open && ((ModelEditor)get("editor")).open,"Asset editor opens");
                image("engine-editor-assets.png");
                x("key","F11");await(()->workspace().open && !((ModelEditor)get("editor")).open,"F11 returns directly from asset tool without Escape");
                require(!(boolean)get("captured"),"Workspace keeps pointer released");
                image("engine-editor-asset-return.png");
                x("key","m");await(()->((ModelEditor)get("editor")).open,"Asset tool reopens");
                x("mousemove","--window",id,"1000","115");x("click","1");
                x("key","ctrl+a");x("type","--clearmodifiers","Synthetic draft");
                x("key","F11");await(()->workspace().open && !((ModelEditor)get("editor")).open,"F11 exits focused model name");
                x("key","m");await(()->((ModelEditor)get("editor")).open,"Asset draft reopens");
                require(((ModelEditor)get("editor")).snapshot().name().equals("Synthetic draft"),"Focused model name draft preserved");
                x("key","Escape");await(()->!((ModelEditor)get("editor")).open,"Escape still closes asset tool");
                x("key","F11");await(()->workspace().open,"Workspace reopens after Escape");
                x("mousemove","--window",id,"100","75");x("click","1");await(()->!workspace().open,"Play mouse button works");
                x("key","Escape");await(()->((ControlsMenu)get("menu")).open,"Game save/control menu regression");
                x("key","Escape");await(()->!((ControlsMenu)get("menu")).open,"Game menu closes");
                Files.writeString(out.resolve("engine-editor-playtest-result.txt"),"PASS: startup, local pause, input isolation, selection wrap, play, resume, F6 regression, return, model tool, direct F11 return, focused name draft retention, Escape regression, mouse Play, controls menu.\n");
            } catch(Throwable e){failure=e;}
            finally{if(handle!=0)glfwSetWindowShouldClose(handle,true);}
        });driver.setDaemon(true);driver.start();
        game.run(new Main.FrameObserver(){public void afterFrame(Main g)throws Exception{captureFrame();}});
        driver.join(1000);if(failure!=null)throw new AssertionError("Editor playtest failed",failure);
        require(Files.exists(out.resolve("engine-editor-playtest-result.txt")),"Driver completed");
    }
}
