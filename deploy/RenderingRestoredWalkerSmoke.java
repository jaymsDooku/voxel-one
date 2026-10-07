import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.GameConfig;
import dev.jayms.player.Player;
import dev.jayms.window.Window;
import org.joml.Vector3f;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Exercises the changed walker in the running sandbox with real W/Space input. */
public class RenderingRestoredWalkerSmoke {
    static Main game=new Main();static Path out;static volatile long window;static volatile float px,py;static volatile boolean grounded,menuOpen;static volatile Throwable failure;static volatile String capture,captured;
    static Object get(String key)throws Exception{Field f=Main.class.getDeclaredField(key);f.setAccessible(true);return f.get(game);}
    static void set(String key,Object value)throws Exception{Field f=Main.class.getDeclaredField(key);f.setAccessible(true);f.set(game,value);}
    static void require(boolean b,String text){if(!b)throw new AssertionError(text);}
    static String x(String...args)throws Exception{var cmd=new ArrayList<String>();cmd.add("xdotool");cmd.addAll(List.of(args));var p=new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();String text=new String(p.getInputStream().readAllBytes()).trim();require(p.waitFor()==0,"X11 input "+args[0]);return text;}
    static void shot(String name)throws Exception{capture=name;for(int i=0;i<200&&!name.equals(captured);i++)Thread.sleep(30);require(name.equals(captured),"Capture "+name);}
    public static void main(String[]args)throws Exception{
        out=Path.of(args[0]);Files.createDirectories(out);set("offlineSave",out.resolve("synthetic-walker-world.dat"));set("gameConfig",new GameConfig(false,false,1200,10));
        Thread driver=new Thread(()->{try{while(window==0)Thread.sleep(50);String id=Long.toString(org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window(window));x("windowfocus",id);long readyDeadline=System.currentTimeMillis()+20000;while(!grounded&&System.currentTimeMillis()<readyDeadline)Thread.sleep(30);require(grounded,"Gravity lands on synthetic floor: y="+py);if(menuOpen){x("key","--window",id,"Escape");long menuDeadline=System.currentTimeMillis()+10000;while(menuOpen&&System.currentTimeMillis()<menuDeadline)Thread.sleep(25);require(!menuOpen,"Resume controls menu after window focus");}shot("physics-walker-start.png");x("keydown","--window",id,"w");long deadline=System.currentTimeMillis()+20000;while(px<7.25f&&System.currentTimeMillis()<deadline)Thread.sleep(20);x("keyup","--window",id,"w");require(px>=7.25f,"Walker reaches half-voxel stair: x="+px+" y="+py);require(py>109.45f&&py<109.6f,"Walker steps onto 0.5 block without jump: y="+py);shot("physics-walker-step.png");x("keydown","--window",id,"w");long wallDeadline=System.currentTimeMillis()+10000;while(px<9.65f&&System.currentTimeMillis()<wallDeadline)Thread.sleep(25);x("keyup","--window",id,"w");Thread.sleep(250);require(px<9.701f&&px>9.65f,"Tall wall stops walker: x="+px);require(py<109.1f,"Walker descends to floor after stair");shot("physics-walker-wall.png");x("keydown","--window",id,"space");long jumpDeadline=System.currentTimeMillis()+20000;while(py<109.1f&&System.currentTimeMillis()<jumpDeadline)Thread.sleep(20);x("keyup","--window",id,"space");require(py>109.1f,"Jump regression launches walker: y="+py);long landDeadline=System.currentTimeMillis()+45000;while((!grounded||Math.abs(py-109)>.01)&&System.currentTimeMillis()<landDeadline)Thread.sleep(30);require(grounded&&Math.abs(py-109)<.01,"Jump lands safely: y="+py);shot("physics-walker-landed.png");Files.writeString(out.resolve("walker-results.json"),"{\n  \"Playtest\": \"Production Main offline sandbox; Linux X11 inherited role display; Mesa; isolated synthetic profile\",\n  \"steps\": [\"Gravity lands on floor\",\"Hold W: climb 0.5-block partial voxel without jumping\",\"Hold W: stop at 3-block wall with no overlap\",\"Space: jump and land on original floor\"],\n  \"expected\": \"Step at y=109.5; wall stops at x=9.7; jump lands at y=109\",\n  \"observed\": \"All assertions passed\"\n}\n");}catch(Throwable e){failure=e;try{Files.writeString(out.resolve("walker-failure.txt"),e.getClass().getSimpleName()+": "+e.getMessage());}catch(Exception ignored){}}finally{if(window!=0)glfwSetWindowShouldClose(window,true);}},"walker-native-input");driver.start();
        game.run(new Main.FrameObserver(){public void started(Main main)throws Exception{
            World w=(World)get("world");w.stream(8,8,9);
            for(int xx=0;xx<16;xx++)for(int z=0;z<16;z++){w.setBlock(xx,108,z,Blocks.STONE);for(int y=109;y<115;y++)w.setBlock(xx,y,z,0);}
            for(int z=6;z<=10;z++)w.apply(new Protocol.Edit(7,109,z,Blocks.STONE,1,0,0,0));
            for(int y=109;y<=112;y++)for(int z=0;z<16;z++)w.setBlock(10,y,z,Blocks.STONE);
            Player p=(Player)get("player");p.driveSeat(new Vector3f(4,109.01f,8),0);p.toggleView();set("firstMouse",true);window=((Window)get("window")).getHandle();
        }
        public void beforeFrame(Main main)throws Exception{if(capture!=null)for(var c:((World)get("world")).getLoadedChunks().values())c.checkMesh();}
        public void afterFrame(Main main)throws Exception{Player p=(Player)get("player");px=p.position().x;py=p.position().y;grounded=p.grounded();menuOpen=((dev.jayms.ui.ControlsMenu)get("menu")).open;if(capture!=null&&!capture.equals(captured)){int width=(int)get("framebufferWidth"),height=(int)get("framebufferHeight");var bytes=java.nio.ByteBuffer.allocateDirect(width*height*3);glReadPixels(0,0,width,height,GL_RGB,GL_UNSIGNED_BYTE,bytes);var image=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_RGB);for(int y=0;y<height;y++)for(int xx=0;xx<width;xx++){int k=(y*width+xx)*3;image.setRGB(xx,height-y-1,(bytes.get(k)&255)<<16|(bytes.get(k+1)&255)<<8|(bytes.get(k+2)&255));}javax.imageio.ImageIO.write(image,"png",out.resolve(capture).toFile());captured=capture;}}
        });driver.join();if(failure!=null)throw new AssertionError("Walker playtest failed",failure);
    }
}
