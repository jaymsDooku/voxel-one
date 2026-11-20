import dev.jayms.*;
import dev.jayms.building.*;
import dev.jayms.net.city.GameConfig;
import dev.jayms.player.Player;
import dev.jayms.window.Window;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWKeyCallback;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Native building inspection with representative views and an engine recording. */
public final class BuildingPreviewWindowsPlaytest implements Main.FrameObserver {
    private final Path output;
    private World world;
    private Player player;
    private GLFWKeyCallback keys;
    private long window, since, previous;
    private int stage;
    private boolean cedar, westminster, grand;

    private BuildingPreviewWindowsPlaytest(Path output) { this.output=output; }

    public static void main(String[] args) throws Exception {
        Path output=Files.createDirectories(Path.of(args[1]).toAbsolutePath());
        System.setProperty("user.home",Files.createDirectory(output.resolve("profile")).toString());
        var game=new Main();
        var building=BuildingCompiler.compile(BuildingApi.readRecipe(Path.of(args[0])));
        field("buildingPreview").set(game,building);
        field("offlineSave").set(game,output.resolve("unused.dat"));
        field("gameConfig").set(game,new GameConfig(false,false,1200,16));
        field("renderOnlyReplay").setBoolean(game,true);
        var probe=new BuildingPreviewWindowsPlaytest(output);
        probe.cedar=building.name().equals("cedar-court-entry");
        probe.grand=building.name().equals("westminster-grand");
        probe.westminster=probe.grand || building.name().equals("westminster");
        game.run(probe);
        if (probe.stage!=(probe.westminster ? 3 : 2) || Files.exists(output.resolve("unused.dat")))
            throw new AssertionError("Preview did not finish or wrote a world save");
        Files.writeString(output.resolve("results.txt"),
                probe.grand ? "PASS: Captured the monument river range, window bay and masonry frieze in the native client. The plot stayed unsaved.\n" : probe.westminster
                        ? "PASS: Windows client captured the Thames elevation, Elizabeth Tower detail and an aerial view of the courts and hall. The plot stayed unsaved.\n"
                        : "PASS: Windows client rendered the compiled building, walked through its entrance, and left the inspection plot unsaved.\n");
    }

    @Override public void started(Main game) throws Exception {
        world=(World)field("world").get(game);
        window=((Window)field("window").get(game)).getHandle();
        glfwSetWindowSize(window,westminster ? 800 : 960,westminster ? 450 : 600);
        keys=glfwSetKeyCallback(window,null);
        glfwSetKeyCallback(window,keys);
        var cursor = glfwSetCursorPosCallback(window,null);
        if (cursor != null) cursor.free();
        if (grand) placeFlying(game,145,50,-30,86,1);
        else if (westminster) placeFlying(game,71,54,-47,90,-2);
        else if (cedar) place(game,5,33.04f,-8,90,9);
        else place(game,13,33.04f,-25,90,15);
        since=previous=System.nanoTime();
    }

    @Override public void beforeFrame(Main game) throws Exception {
        long now=System.nanoTime();
        float dt=Math.min(.04f,(now-previous)/1_000_000_000f);
        previous=now;
        if (westminster && stage==1) {
            float t=Math.min(1,(now-since)/3_000_000_000f);
            if (grand) placeFlying(game,145-10*t,50-5*t,-30+47*t,86+4*t,1+4*t);
            else placeFlying(game,71-61*t,54+3*t,-47+37*t,90,-2+11*t);
        }
        if (!westminster && stage==1 && player.position().z<(cedar ? 4.5f : 8))
            player.step(world,dt,1,0,false,true);
    }

    @Override public void afterFrame(Main game) throws Exception {
        long elapsed=System.nanoTime()-since;
        if (westminster) {
            inspectWestminster(game,elapsed);
            return;
        }
        if (stage==0 && elapsed>2_400_000_000L) {
            capture(game,cedar ? "cedar-exterior.png" : "breakwater-exterior.png");
            if (cedar) place(game,5,33.04f,-5,90,-6);
            else place(game,17,33.04f,-8,90,-6);
            pressRecord();
            since=previous=System.nanoTime();
            stage=1;
        } else if (stage==1) {
            if (elapsed>16_000_000_000L) throw new AssertionError("Could not walk through entrance at "+player.position());
            if (player.position().z>=(cedar ? 4.5f : 8) && elapsed>2_000_000_000L) {
                if (player.collides(world)) throw new AssertionError("Lobby is obstructed");
                capture(game,cedar ? "cedar-interior.png" : "breakwater-lobby.png");
                pressRecord();
                stage=2;
                glfwSetWindowShouldClose(window,true);
            }
        }
    }

    private void inspectWestminster(Main game,long elapsed) throws Exception {
        if (stage==0 && elapsed>2_500_000_000L) {
            capture(game,"westminster-thames.png");
            pressRecord();
            stage=1;
            since=System.nanoTime();
        } else if (stage==1 && elapsed>4_000_000_000L) {
            capture(game,grand ? "westminster-wall-bay.png" : "westminster-clock.png");
            if (grand) placeFlying(game,138,49,25,103,5);
            else placeFlying(game,78,118,90,-90,-48);
            stage=2;
            since=System.nanoTime();
        } else if (stage==2 && elapsed>3_500_000_000L) {
            capture(game,grand ? "westminster-masonry.png" : "westminster-courts.png");
            pressRecord();
            stage=3;
            glfwSetWindowShouldClose(window,true);
        }
    }

    private void placeFlying(Main game,float x,float y,float z,float yaw,float pitch) throws Exception {
        place(game,x,y,z,yaw,pitch);
        player.toggleFlight();
    }

    private void place(Main game,float x,float y,float z,float yaw,float pitch) throws Exception {
        player=new Player(new Vector3f(x,y,z),yaw,pitch,(Camera)field("camera").get(game));
        field("player").set(game,player);
    }

    private void pressRecord() { keys.invoke(window,GLFW_KEY_F10,0,GLFW_PRESS,0); }

    private void capture(Main game,String file) throws Exception {
        int w=field("framebufferWidth").getInt(game), h=field("framebufferHeight").getInt(game);
        var pixels=BufferUtils.createByteBuffer(w*h*4);
        glReadPixels(0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
        var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
        for (int y=0; y<h; y++) for (int x=0; x<w; x++) {
            int i=(x+y*w)*4;
            image.setRGB(x,h-y-1,((pixels.get(i)&255)<<16)|((pixels.get(i+1)&255)<<8)|(pixels.get(i+2)&255));
        }
        ImageIO.write(image,"png",output.resolve(file).toFile());
    }

    private static Field field(String name) throws Exception {
        var field=Main.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
