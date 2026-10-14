import dev.jayms.Main;
import dev.jayms.GameIdentity;
import dev.jayms.net.city.GameConfig;
import dev.jayms.render.GraphicsProfile;
import dev.jayms.recording.VideoRecording;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.nio.file.*;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/** Production renderer smoke through its existing observer API. Synthetic state only. */
public final class NativeRenderProbe {
    static Object field(Object target,String name)throws Exception {
        var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);
    }
    static void set(Object target,String name,Object value)throws Exception {
        var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);
    }
    public static void main(String[] args)throws Exception {
        Path profile=Path.of(args[0]).toAbsolutePath(),output=Path.of(args[1]).toAbsolutePath();
        Files.createDirectories(profile);Files.createDirectories(output);
        System.setProperty("user.home",profile.toString());
        String mode=args[2];boolean city=!mode.equals("sandbox");
        if(!java.util.Set.of("city","sandbox","editor").contains(mode))throw new IllegalArgumentException("Unknown native smoke mode");
        var preferences=GraphicsProfile.preset(GraphicsProfile.Preset.LOW);
        preferences.save(profile.resolve(".voxel-one/graphics.properties"));
        Main game=new Main();set(game,"gameConfig",city?GameConfig.cityGame():GameConfig.sandbox());
        set(game,"offlineSave",GameIdentity.of(city).savePath(profile.resolve(".voxel-one"),null));
        if(mode.equals("editor")){Object editor=field(game,"engineEditor");editor.getClass().getField("open").setBoolean(editor,true);}
        long start=System.nanoTime();long[] handle={0},started={0};int[] frames={0};String[] renderer={"unavailable"};
        try(VideoRecording movie=new VideoRecording(output,900,600)) {
            game.run(new Main.FrameObserver() {
                public void started(Main actual)throws Exception {
                    Object window=field(actual,"window");handle[0]=((Number)window.getClass().getMethod("getHandle").invoke(window)).longValue();
                    glfwSetWindowSize(handle[0],900,600);glfwPollEvents();started[0]=System.nanoTime();
                    renderer[0]=glGetString(GL_RENDERER);
                }
                public void afterFrame(Main actual)throws Exception {
                    int[] width={0},height={0};glfwGetFramebufferSize(handle[0],width,height);
                    if(width[0]<1 || height[0]<1)throw new AssertionError("Native framebuffer unavailable");
                    ByteBuffer pixels=BufferUtils.createByteBuffer(width[0]*height[0]*4);
                    glReadPixels(0,0,width[0],height[0],GL_RGBA,GL_UNSIGNED_BYTE,pixels);
                    BufferedImage image=new BufferedImage(width[0],height[0],BufferedImage.TYPE_INT_RGB);
                    for(int y=0;y<height[0];y++)for(int x=0;x<width[0];x++) {
                        int p=((height[0]-1-y)*width[0]+x)*4;
                        image.setRGB(x,y,(pixels.get(p)&255)<<16|(pixels.get(p+1)&255)<<8|(pixels.get(p+2)&255));
                    }
                    if(frames[0]==0)ImageIO.write(image,"png",output.resolve("native-"+mode+".png").toFile());
                    BufferedImage bounded=new BufferedImage(900,600,BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics=bounded.createGraphics();graphics.drawImage(image,0,0,900,600,null);graphics.dispose();
                    byte[] rgb=new byte[900*600*3];
                    for(int y=0;y<600;y++)for(int x=0;x<900;x++){int v=bounded.getRGB(x,599-y),p=(y*900+x)*3;rgb[p]=(byte)(v>>16);rgb[p+1]=(byte)(v>>8);rgb[p+2]=(byte)v;}
                    movie.offer(rgb,(System.nanoTime()-started[0])/1_000_000);
                    if(++frames[0]>=4)glfwSetWindowShouldClose(handle[0],true);
                }
            });
            if(frames[0]<4)throw new AssertionError("Native renderer exited before four frames");
            System.out.println("Playtest: production native "+mode+" renderer completed four frames in "+((System.nanoTime()-start)/1_000_000)+" ms; renderer "+renderer[0]+"; LOW preset; no smooth-performance claim.");
        } catch(Throwable error) {
            String message=String.valueOf(error.getMessage()).split("[\\r\\n]",2)[0];
            System.out.println("HUB_CHECK_FAILURE: Native "+mode+" renderer: "+error.getClass().getSimpleName()+": "+message.substring(0,Math.min(160,message.length())));
            System.exit(1);
        }
    }
}
