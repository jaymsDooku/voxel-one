import dev.jayms.*;
import dev.jayms.player.*;
import dev.jayms.render.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.*;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWNativeX11;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Real Main render loop and X11 key callbacks. The replay world disables loading only in this fixture. */
public class GraphicsMainPlaytest {
    static long menuEditRevision;
    static Main game=new Main();static Path out;static int frame,stage;static long changed;static String windowId;static RenderPipeline original;static boolean cityMode;static int gKeys,received,expected;static org.lwjgl.glfw.GLFWWindowFocusCallback originalFocus;static org.lwjgl.glfw.GLFWKeyCallback originalKeys;
    static Object get(String name)throws Exception{var f=Main.class.getDeclaredField(name);f.setAccessible(true);return f.get(game);}
    static void set(String name,Object v)throws Exception{var f=Main.class.getDeclaredField(name);f.setAccessible(true);f.set(game,v);}
    static void require(boolean b,String m){if(!b){try{Files.writeString(out.resolve("failure.txt"),m+"\n");}catch(Exception ignored){}throw new AssertionError(m);}}
    static void input(String... args)throws Exception{var c=new ArrayList<String>();c.add("xdotool");c.addAll(List.of(args));var p=new ProcessBuilder(c).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();require(p.waitFor()==0,"X11 input");}
    static void keys(String... keys)throws Exception{for(String key:keys){expected++;input("key",key);}}
    static void image(String name)throws Exception{int w=(int)get("framebufferWidth"),h=(int)get("framebufferHeight");var bytes=java.nio.ByteBuffer.allocateDirect(w*h*4);glReadPixels(0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,bytes);var img=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_RGB);for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=(x+y*w)*4;img.setRGB(x,h-y-1,(bytes.get(i)&255)<<16|(bytes.get(i+1)&255)<<8|(bytes.get(i+2)&255));}javax.imageio.ImageIO.write(img,"png",out.resolve(name).toFile());}
    public static void main(String[] args)throws Exception{
        out=Path.of(args[0]);cityMode=args.length>1&&args[1].equals("city");Files.createDirectories(out);GraphicsProfile.preset(GraphicsProfile.Preset.LOW).save(Controls.directory().resolve("graphics.properties"));
        set("offlineSave",Path.of("target/graphics-profile/synthetic.dat").toAbsolutePath());set("gameConfig",cityMode?GameConfig.cityGame():GameConfig.sandbox());((EngineEditor)get("engineEditor")).open=false;glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);
        game.run(new Main.FrameObserver(){
            public void started(Main g)throws Exception{
                long handle=((dev.jayms.window.Window)get("window")).getHandle();windowId=Long.toString(GLFWNativeX11.glfwGetX11Window(handle));originalKeys=glfwSetKeyCallback(handle,null);glfwSetKeyCallback(handle,(h,k,sc,a,m)->{if(a==GLFW_PRESS){received++;if(k==GLFW_KEY_G)gKeys++;}originalKeys.invoke(h,k,sc,a,m);});originalFocus=glfwSetWindowFocusCallback(handle,null);glfwSetWindowSizeLimits(handle,320,240,GLFW_DONT_CARE,GLFW_DONT_CARE);glfwSetWindowSize(handle,640,480);input("windowfocus",windowId);
                ((World)get("world")).close();World world=new World(){@Override public void stream(float x,float z,int budget){}};for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.addChunk(new ChunkPos(x,4,z),new Chunk());set("world",world);set("renderOnlyReplay",true);
                for(int x=2;x<15;x++)for(int z=2;z<15;z++)world.apply(new Protocol.Edit(x,72,z,Blocks.BRICKS));for(int x=3;x<14;x++)for(int y=73;y<78;y++)world.apply(new Protocol.Edit(x,y,3,Blocks.PLANKS));world.apply(new Protocol.Edit(7,74,4,Blocks.LED).withColor(0xff3040));
                Player p=new Player(new Vector3f(8.5f,74.5f,14.5f),-90,-12,(Camera)get("camera"));p.toggleFlight();set("player",p);set("isometric",false);((ControlsMenu)get("menu")).open=false;var capture=Main.class.getDeclaredMethod("setCaptured",boolean.class);capture.setAccessible(true);capture.invoke(game,true);original=(RenderPipeline)get("rendering");changed=System.nanoTime();
            }
            public void beforeFrame(Main g)throws Exception{((ControlsMenu)get("menu")).graphics.density(stage==2?2:1);}
            public void afterFrame(Main g)throws Exception{
                frame++;require(((DetailedMeshScheduler)get("detailedMeshes")).error.isEmpty(),"Detailed upload has no hidden error");if(frame==5&&originalFocus!=null){glfwSetWindowFocusCallback(((dev.jayms.window.Window)get("window")).getHandle(),originalFocus);originalFocus=null;}require(glGetError()==GL_NO_ERROR,"Main GL state clean");var menu=(ControlsMenu)get("menu");var graphics=(GraphicsController)get("graphics");var ui=menu.graphics;var renderer=(RenderPipeline)get("rendering");long handle=((dev.jayms.window.Window)get("window")).getHandle();
                if(System.nanoTime()-changed>90_000_000_000L)throw new AssertionError("Input timeout phase "+stage);if(System.nanoTime()-changed<700_000_000L||received<expected)return;
                boolean advanced=true;
                switch(stage){
                    case 0 -> {if(frame<12){advanced=false;break;}require(renderer.lightingReady(),"Synthetic lighting ready");int width=renderer.actualWidth();try{renderer.prepareTargets(Integer.MAX_VALUE,480);throw new AssertionError("Oversized target should fail");}catch(IllegalArgumentException expected){}require(renderer.actualWidth()==width,"Rejected target allocation keeps working target");keys("Escape");}
                    case 1 -> {require(menu.open,"Escape opens Controls");image("graphics-controls-entry.png");if(cityMode)require(menu.saves!=null,"City Saves retained");menuEditRevision=((World)get("world")).editsVersion();input("mousemove","--window",windowId,"385","42");input("click","1");}
                    case 2 -> {require(((World)get("world")).editsVersion()==menuEditRevision,"Menu click cannot edit the world");require(ui.open,"Graphics opens through mouse; callbacks="+gKeys+" menu="+menu.open+" editing="+menu.editing()+" engine="+((EngineEditor)get("engineEditor")).open);image("graphics-display-200-percent.png");keys("Right","c");}
                    case 3 -> {require(!ui.open&&get("rendering")==original,"Cancel preserves renderer and display draft");keys("g");}
                    case 4 -> {keys("Down","Down","Down","Down","Left","a");}
                    case 5 -> {require(get("rendering")!=original,"Apply swaps prepared renderer");require(Math.abs(renderer.settings.renderScale-.85f)<.01,"UI scale maps to actual render target");require(graphics.transaction.requested().preset()==GraphicsProfile.Preset.CUSTOM,"Manual change selects Custom");require(GraphicsProfile.load(Controls.directory().resolve("graphics.properties")).profile().get(GraphicsProfile.Key.SCALE).equals("0.85"),"Relaunch profile load preserves applied scale");image("graphics-applied.png");keys("c","g","Right","a");}
                    case 6 -> {require(graphics.transaction.pending(),"Borderless requires confirmation");require(!GraphicsProfile.load(Controls.directory().resolve("graphics.properties")).profile().get(GraphicsProfile.Key.DISPLAY_MODE).equals("BORDERLESS"),"Risky display not persisted early");image("graphics-confirm.png");keys("Escape");}
                    case 7 -> {require(!graphics.transaction.pending()&&graphics.transaction.requested().get(GraphicsProfile.Key.DISPLAY_MODE).equals("WINDOWED"),"Revert restores last good display");keys("c","g","Right","a");}
                    case 8 -> {require(graphics.transaction.pending(),"Second display change pending");keys("Return");}
                    case 9 -> {require(!graphics.transaction.pending(),"Keep confirms display");require(GraphicsProfile.load(Controls.directory().resolve("graphics.properties")).profile().get(GraphicsProfile.Key.DISPLAY_MODE).equals("BORDERLESS"),"Only confirmed display persists");keys("Tab","Tab","Tab","Tab","F9");}
                    case 10 -> {require(((PerformanceRecorder)get("performance")).active(),"F9 starts actual engine performance capture");image("graphics-performance.png");keys("F9");glfwSetWindowSize(handle,333,271);}
                    case 11 -> {require(!((PerformanceRecorder)get("performance")).active(),"F9 stops and exports capture");if((int)get("framebufferWidth")!=333||(int)get("framebufferHeight")!=271){advanced=false;break;}image("graphics-small.png");keys("c","Escape","F6");}
                    case 12 -> {require(!menu.open&&!ui.open,"Graphics exit restores game input");require((boolean)get("isometric"),"F6 camera regression");image("graphics-isometric.png");keys("Escape","r");}
                    case 13 -> {require(menu.rendering.open,"Rendering route preserved after Graphics swaps");keys("Right");}
                    case 14 -> {require(Math.abs(graphics.transaction.requested().number(GraphicsProfile.Key.SCALE)-.9f)<.01,"Legacy edit updates requested Graphics profile");require(Math.abs(GraphicsProfile.load(Controls.directory().resolve("graphics.properties")).profile().number(GraphicsProfile.Key.SCALE)-.9f)<.01,"Legacy shared edit persists in Graphics profile");image("graphics-legacy-rendering.png");keys("Down","Down","Down","Down","Down","Right");}
                    case 15 -> {require(!renderer.settings.particles,"Legacy particle control maps to active renderer");keys("Escape","g","a");}
                    case 16 -> {require(!renderer.settings.particles,"Legacy-only particle value survives Graphics replacement");keys("c","r");}
                    case 17 -> {require(menu.rendering.open,"Rendering supplier follows latest replacement");Path file=Controls.directory().resolve("graphics.properties");Files.move(file,file.resolveSibling("graphics.backup"));Files.createDirectory(file);original=renderer;keys("Up","Up","Up","Up","Up","Right");}
                    case 18 -> {require(renderer==original,"Failed legacy save keeps renderer");require(Math.abs(renderer.settings.renderScale-.9f)<.01,"Failed legacy Apply restores shared render state");require(Math.abs(graphics.transaction.requested().number(GraphicsProfile.Key.SCALE)-.9f)<.01,"Failed legacy Apply keeps requested profile");Path file=Controls.directory().resolve("graphics.properties");Files.delete(file);Files.move(file.resolveSibling("graphics.backup"),file);image("graphics-legacy-rollback.png");keys("Escape","Escape");}
                    case 19 -> {require(!menu.open,"Legacy exit restores controls");Files.writeString(out.resolve("results.txt"),"Playtest: PASS. Production Main ("+(cityMode?"City":"Sandbox")+") on Linux Mesa llvmpipe OpenGL 3.3; isolated synthetic profile; actual Escape/G/R input; draft Cancel, real scale Apply, atomic persistence/reload, display Revert/Keep, oversized target rollback, F9 start/stop, 333x271 edge, F6 camera regression, legacy shared preference persistence, particle preservation across swaps, injected legacy save failure rollback; GL errors absent across "+frame+" frames. Windows/device performance not measured.\n");glfwSetWindowShouldClose(handle,true);}
                    default -> advanced=false;
                }
                if(advanced){stage++;changed=System.nanoTime();}if(frame>350||System.nanoTime()-changed>90_000_000_000L)throw new AssertionError("Graphics phase timeout "+stage);
            }
        });require(stage==20,"Graphics workflow completed");System.out.println("Graphics production Playtest passed");
    }
}
