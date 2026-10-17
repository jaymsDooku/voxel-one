import dev.jayms.*;
import dev.jayms.player.Player;
import dev.jayms.render.*;
import dev.jayms.window.Window;
import java.nio.file.*;
import java.util.concurrent.CancellationException;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Real startup UI, first playable lighting and native Escape cancellation. Synthetic profile only. */
public class StreamingBootstrap {
    static Path out;static String mode;static int terrainFrames,lightingFrames,frames;static boolean entered,cancelSent;static World observedWorld;static long began;static String renderer;
    static Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    static void set(Object object,String name,Object value)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void key(Main main,String command,String name)throws Exception{
        long handle=((Window)field(main,"window")).getHandle();
        var process=new ProcessBuilder("xdotool",command,"--window",Long.toString(org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window(handle)),name).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        check(process.waitFor()==0,"Native keyboard input");
    }
    static void capture(Main main,String name)throws Exception{
        int w=(int)field(main,"framebufferWidth"),h=(int)field(main,"framebufferHeight");var pixels=java.nio.ByteBuffer.allocateDirect(w*h*3);glReadPixels(0,0,w,h,GL_RGB,GL_UNSIGNED_BYTE,pixels);
        var image=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=(x+y*w)*3;image.setRGB(x,h-y-1,(pixels.get(i)&255)<<16|(pixels.get(i+1)&255)<<8|(pixels.get(i+2)&255));}
        javax.imageio.ImageIO.write(image,"png",out.resolve(name).toFile());
    }
    public static void main(String[] args)throws Exception{
        out=Path.of(args[0]);Files.createDirectories(out);mode=args[1];began=System.nanoTime();var game=new Main();
        GraphicsProfile.preset(GraphicsProfile.Preset.BALANCED).with(GraphicsProfile.Key.RESOLUTION,"640x480@60").save(dev.jayms.ui.Controls.directory().resolve("graphics.properties"));
        set(game,"offlineSave",out.resolve("synthetic.dat"));set(game,"seed",748291L);set(game,"gameConfig",dev.jayms.net.city.GameConfig.sandbox());
        float[] start=new float[2];
        try{
            game.run(new Main.FrameObserver(){
                public void loading(Main main,String stage)throws Exception{
                    observedWorld=(World)field(main,"world");renderer=glGetString(GL_RENDERER);
                    if(stage.equals("terrain"))terrainFrames++;
                    else if(stage.equals("lighting")){
                        if(lightingFrames++==0){capture(main,"loading.png");if(mode.equals("cancel")){key(main,"keydown","Escape");cancelSent=true;}}
                    }
                }
                public void started(Main main)throws Exception{
                    entered=true;check(!mode.equals("cancel"),"Escape must cancel before gameplay");
                    var p=((Player)field(main,"player")).position();start[0]=p.x;start[1]=p.z;
                    check(((RenderPipeline)field(main,"rendering")).nearLightingReady((World)field(main,"world"),p.x,p.z),"First playable area has current GI and known nearby sky");
                    key(main,"keydown","w");
                }
                public void afterFrame(Main main)throws Exception{
                    if(frames==0)capture(main,"first-playable.png");
                    if(++frames==3){key(main,"keyup","w");var p=((Player)field(main,"player")).position();check(Math.abs(p.x-start[0])+Math.abs(p.z-start[1])>.01f,"Walking works after lighting readiness");glfwSetWindowShouldClose(((Window)field(main,"window")).getHandle(),true);}
                    check(glGetError()==GL_NO_ERROR,"Startup GL error absent");
                }
            });
            check(mode.equals("ready")&&entered&&frames==3,"Ready workflow finished");
        }catch(CancellationException cancelled){check(mode.equals("cancel")&&cancelSent&&!entered,"Expected native Escape cancellation during lighting");}
        finally{if(cancelSent)new ProcessBuilder("xdotool","keyup","Escape").redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start().waitFor();}
        check(lightingFrames>0&&observedWorld!=null&&observedWorld.isClosed(),"Loading loop ran and world workers closed on exit");
        Files.writeString(out.resolve("result.txt"),"Playtest: PASS. mode="+mode+"; Linux/"+renderer+"; Java"+System.getProperty("java.version")+"; synthetic seed748291 Balanced requested640x480. terrainUIframes="+terrainFrames+"; lightingUIframes="+lightingFrames+"; gameplayFrames="+frames+"; elapsedMs="+(System.nanoTime()-began)/1e6+". Expected responsive loading UI, current first-playable lighting and working walk, or native Escape before entry; observed expected behavior and closed world workers.\n");
        System.out.println(Files.readString(out.resolve("result.txt")));
    }
}
