import dev.jayms.*;
import dev.jayms.player.*;
import dev.jayms.render.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.EngineEditor;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWNativeX11;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Exercises the production Main loop with a synthetic gallery and real X11 inputs. */
public class RenderingMainPlaytest {
    static final Main game=new Main();static Path out;static int frame,stage;static long changed;static int retries,recordingFrame,f6Presses;
    static Vector3f start;static String windowId;
    static org.lwjgl.glfw.GLFWWindowFocusCallback initialFocus;
    static org.lwjgl.glfw.GLFWKeyCallback originalKeys;
    static Object get(String name)throws Exception{var f=Main.class.getDeclaredField(name);f.setAccessible(true);return f.get(game);}
    static void set(String name,Object v)throws Exception{var f=Main.class.getDeclaredField(name);f.setAccessible(true);f.set(game,v);}
    static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
    static void input(String... args)throws Exception{var c=new ArrayList<String>();c.add("xdotool");c.addAll(List.of(args));var p=new ProcessBuilder(c).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();require(p.waitFor()==0,"X11 input");}
    static void image(String name)throws Exception{
        int w=(int)get("framebufferWidth"),h=(int)get("framebufferHeight");var bytes=java.nio.ByteBuffer.allocateDirect(w*h*4);glReadPixels(0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,bytes);
        var img=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=(x+y*w)*4;img.setRGB(x,h-y-1,(bytes.get(i)&255)<<16|(bytes.get(i+1)&255)<<8|(bytes.get(i+2)&255));}
        javax.imageio.ImageIO.write(img,"png",out.resolve(name).toFile());
    }
    public static void main(String[] args)throws Exception{
        out=Path.of(args[0]);Files.createDirectories(out);set("offlineSave",Path.of("target/render-profile/synthetic-city.dat").toAbsolutePath());set("gameConfig",GameConfig.sandbox());((EngineEditor)get("engineEditor")).open=false;
        glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);
        game.run(new Main.FrameObserver(){
            public void started(Main g)throws Exception{
                long handle=((dev.jayms.window.Window)get("window")).getHandle();glfwSetWindowSizeLimits(handle,320,240,GLFW_DONT_CARE,GLFW_DONT_CARE);glfwSetWindowSize(handle,640,400);
                windowId=Long.toString(GLFWNativeX11.glfwGetX11Window(handle));
                originalKeys=glfwSetKeyCallback(handle,null);
                glfwSetKeyCallback(handle,(h,key,scancode,action,mods)->{if(key==GLFW_KEY_F6&&action==GLFW_PRESS)f6Presses++;originalKeys.invoke(h,key,scancode,action,mods);});
                initialFocus=glfwSetWindowFocusCallback(handle,null);input("windowfocus",windowId);input("keyup","F6","w","F10");
                ((dev.jayms.ui.ControlsMenu)get("menu")).open=false;
                var capture=Main.class.getDeclaredMethod("setCaptured",boolean.class);capture.setAccessible(true);capture.invoke(game,true);
                World world=(World)get("world");
                for(int x=2;x<15;x++)for(int z=2;z<15;z++)world.apply(new Protocol.Edit(x,72,z,Blocks.STONE));
                for(int x=3;x<14;x++)for(int z=3;z<10;z++)world.apply(new Protocol.Edit(x,78,z,Blocks.PLANKS));
                for(int x=3;x<14;x++)for(int y=73;y<78;y++)world.apply(new Protocol.Edit(x,y,3,Blocks.BRICKS));
                world.apply(new Protocol.Edit(5,74,4,Blocks.LED).withColor(0xff3040));world.apply(new Protocol.Edit(8,74,4,Blocks.LED).withColor(0x30ff90));world.apply(new Protocol.Edit(11,74,4,Blocks.LED).withColor(0x3050ff));
                for(int x=5;x<=11;x++)for(int z=10;z<=13;z++)world.apply(new Protocol.Edit(x,73,z,Blocks.WATER));
                for(var e:world.getLoadedChunks().entrySet())if(e.getKey().chunkX()==0&&e.getKey().chunkZ()==0)e.getValue().checkMesh();
                Player p=new Player(new Vector3f(8.5f,74.5f,14.5f),-90,-12,(dev.jayms.Camera)get("camera"));p.toggleFlight();set("player",p);set("isometric",false);capture.invoke(game,true);
                changed=System.nanoTime();
            }
            public void afterFrame(Main g)throws Exception{
                frame++;if(frame==5&&initialFocus!=null){glfwSetWindowFocusCallback(((dev.jayms.window.Window)get("window")).getHandle(),initialFocus);initialFocus=null;}
                require(glGetError()==GL_NO_ERROR,"Production Main GL state is clean");
                RenderPipeline r=(RenderPipeline)get("rendering");Player p=(Player)get("player");long handle=((dev.jayms.window.Window)get("window")).getHandle();
                if(frame%10==0)System.out.println("Render phase="+stage+" lighting="+r.lightingReady()+" menu="+((dev.jayms.ui.ControlsMenu)get("menu")).open+" captured="+get("captured"));
                if(stage==0&&((dev.jayms.ui.ControlsMenu)get("menu")).open){input("windowfocus",windowId);input("key","Escape");}
                if(stage==0&&frame>=15&&r.lightingReady()&&!((dev.jayms.ui.ControlsMenu)get("menu")).open){
                    require(r.particleCount()==48,"Three LED emitters in production loop");image("rendering-main-gallery.png");start=p.position();recordingFrame=frame;input("key","F10");input("keydown","w");changed=System.nanoTime();stage++;
                }else if(stage==1&&frame-recordingFrame>=8&&System.nanoTime()-changed>2_000_000_000L){
                    input("keyup","w");require(p.position().distance(start)>.05,"Real W input moves camera");image("rendering-main-motion.png");input("key","F10");input("keydown","F6");changed=System.nanoTime();stage++;
                }else if(stage==2&&System.nanoTime()-changed>1_000_000_000L){
                    input("keyup","F6");
                    if(!(boolean)get("isometric")){
                        System.out.println("Render F6 check: presses="+f6Presses+" menu="+((dev.jayms.ui.ControlsMenu)get("menu")).open+" editor="+((dev.jayms.ui.ModelEditor)get("editor")).open+" inventory="+((dev.jayms.ui.InventoryHud)get("inventoryHud")).open);
                        require(retries++<3,"F6 isometric regression");input("windowfocus",windowId);
                        if(((dev.jayms.ui.ControlsMenu)get("menu")).open)input("key","Escape");
                        stage=22;changed=System.nanoTime();return;
                    }image("rendering-main-isometric.png");r.settings.renderScale=.65f;r.resetHistory();glfwSetWindowSize(handle,333,271);changed=System.nanoTime();stage++;
                }else if(stage==22&&System.nanoTime()-changed>500_000_000L){input("keydown","F6");stage=2;changed=System.nanoTime();
                }else if(stage==3&&System.nanoTime()-changed>1_000_000_000L){
                    require((int)get("framebufferWidth")==333&&(int)get("framebufferHeight")==271,"Odd window size edge");image("rendering-main-resize.png");input("key","Escape");changed=System.nanoTime();stage++;
                }else if(stage==4&&System.nanoTime()-changed>800_000_000L){
                    require(((dev.jayms.ui.ControlsMenu)get("menu")).open,"Escape menu regression");image("rendering-main-menu.png");input("key","Escape");
                    Files.writeString(out.resolve("rendering-main-results.txt"),"Playtest: PASS. Production Main, Linux assigned X11 display, synthetic voxel profile. LED gallery, GPU particles, water, actual W camera motion, F10 recording, F6 isometric, temporal upscale at odd 333x271 resize, Escape menu; no GL errors across "+frame+" frames. Backend="+r.gpuDriven()+".\n");
                    glfwSetWindowShouldClose(handle,true);stage++;
                }
                if(frame>180||System.nanoTime()-changed>120_000_000_000L)throw new AssertionError("Main playtest phase timeout "+stage);
            }
        });if(originalKeys!=null)originalKeys.free();require(stage==5,"Production workflow completed");System.out.println("Production renderer Playtest passed");
    }
}
