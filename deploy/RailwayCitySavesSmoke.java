import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.*;
import java.lang.reflect.Field;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/** Bounded native playtest using real X11 input and isolated synthetic saves. */
public class RailwayCitySavesSmoke implements Main.FrameObserver {
    Main game;
    int step, starts;
    long due, firstWindow;
    Path root, evidence;
    String capture;
    CityFrame savedCity;
    static Object get(Object o,String name) throws Exception {
        Field f=o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o);
    }
    static void set(Object o,String name,Object value) throws Exception {
        Field f=o.getClass().getDeclaredField(name); f.setAccessible(true); f.set(o,value);
    }
    void require(boolean ok,String message) { if (!ok) throw new AssertionError(message); }
    void x(String... args) throws Exception {
        var command=new ArrayList<String>(); command.add("xdotool"); command.addAll(List.of(args));
        var p=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        p.getInputStream().readAllBytes(); require(p.waitFor()==0,"X11 input: "+args[0]+" at stage "+step+" start "+starts);
    }
    String id() throws Exception {
        var p=new ProcessBuilder("xdotool","search","--name","^Voxel One").start();
        String id=new String(p.getInputStream().readAllBytes()).trim(); p.waitFor();
        return id.lines().findFirst().orElseThrow();
    }
    void key(String text) throws Exception {
        var args=new ArrayList<String>(List.of("key","--window",id()));
        args.addAll(List.of(text.split(" "))); x(args.toArray(String[]::new));
    }
    void type(String text) throws Exception { x("type","--window",id(),"--clearmodifiers",text); }
    void click(int dx,int dy) throws Exception {
        var w=(dev.jayms.window.Window)get(game,"window"); int[] size=w.getSize();
        x("mousemove","--window",id(),Integer.toString(size[0]/2-300+dx),Integer.toString(size[1]/2-290+dy));
        x("click","1");
    }
    ControlsMenu menu() throws Exception { return (ControlsMenu)get(game,"menu"); }
    LocalGame local() throws Exception { return (LocalGame)get(game,"local"); }
    Path active() throws Exception { return (Path)get(game,"offlineSave"); }
    public void started(Main value) throws Exception {
        game=value; starts++;
        long handle = ((dev.jayms.window.Window)get(game,"window")).getHandle();
        if (starts == 1) firstWindow = handle;
        require(handle == firstWindow, "Save switches keep the window and GL context");
        if (starts==1) Files.writeString(root.resolve("graphics-environment.txt"), "Renderer: "+glGetString(GL_RENDERER)+"\nOpenGL: "+glGetString(GL_VERSION)+"\n");
        x("windowfocus",id());
        if(starts==1) {
            set(local().city, "elapsed", 200d);
            int grade = local().city.frame().roads().get(0).y();
            set(local().city, "railway", new Railway(new Railway.State(
                    List.of(new Railway.Track(150,150,grade), new Railway.Track(151,150,grade),
                            new Railway.Track(152,150,grade)), List.of())));
            // A stored city can be opened from a launch whose default mode is sandbox.
            set(game, "gameConfig", GameConfig.sandbox());
        }
        due=System.nanoTime()+1_000_000_000L;
    }
    public void beforeFrame(Main value) throws Exception {
        if (System.nanoTime()<due) return;
        due=System.nanoTime()+700_000_000L;
        Files.writeString(root.resolve("playtest-stage.txt"), "stage="+step+", starts="+starts+"\n");
        switch(step++) {
            case 0 -> { if (!menu().open) key("Escape"); }
            case 1 -> { require(menu().open,"Escape opens controls"); click(470,32); }
            case 2 -> { require(menu().saves.open,"Save menu opened by mouse"); click(100,372); }
            case 3 -> { require(Files.exists(CitySaves.sidecar(active(),".city")),"Save current writes city"); savedCity = CitySimulation.load(CitySaves.sidecar(active(),".city")); type("Harbour"); }
            case 4 -> click(100,480);
            case 5 -> { require(starts==2 && active().getParent().getFileName().toString().equals("Harbour"),"Copy becomes active"); require(local().city.frame().elapsed()>=200,"Copy restores simulation clock"); require(local().city.frame().roads().equals(savedCity.roads()),"Copy restores roads"); require(local().city.frame().railway().equals(savedCity.railway()),"Copy restores railway state"); require(local().city.frame().buildings().size()==savedCity.buildings().size(),"Copy restores buildings"); key("Escape"); }
            case 6 -> { if(!menu().open) key("Escape"); else click(470,32); }
            case 7 -> { if(!menu().saves.open) click(470,32); else type("Harbour"); }
            case 8 -> { if(((String)get(menu().saves,"name")).isEmpty()) type("Harbour"); click(420,480); }
            case 9 -> { require(starts==2,"Duplicate did not switch"); require(((String)get(menu().saves,"message")).contains("already exists"),"Duplicate gives clear error"); capture="city-saves-duplicate.png"; }
            case 10 -> click(420,480);
            case 11 -> { require(starts==3 && active().getParent().getFileName().toString().equals("Hills"),"New city becomes active"); require(local().city.frame().elapsed()<30,"New simulation starts fresh"); require(local().city.frame().railway().tracks().isEmpty(),"New city has independent empty railway state"); require(local().city.frame().config().city(),"New simulation retains active city mode despite sandbox launch defaults"); set(local().city,"elapsed",50d); if(!menu().open) key("Escape"); }
            case 12 -> click(470,32);
            case 13 -> { require(menu().saves.open,"Reopen menu"); capture="city-saves-menu.png"; click(80,110); }
            case 14 -> click(420,372);
            case 15 -> { require(starts==4 && active().equals(root.resolve("offline-city.dat")),"Original city loaded"); require(local().city.frame().elapsed()>=200,"Original simulation restored"); require(local().city.frame().roads().equals(savedCity.roads()),"Original roads restored"); require(local().city.frame().railway().equals(savedCity.railway()),"Original railway state restored"); require(local().city.frame().buildings().size()==savedCity.buildings().size(),"Original buildings restored"); var store = (CitySaves)get(game,"citySaves"); var slots = store.list(); require(slots.size()==3,"Three independent simulations persist"); for (var slot : slots) { var saved = CitySimulation.load(CitySaves.sidecar(slot.world(),".city")); require(slot.name().equals("Hills") ? saved.elapsed()>=50 && saved.elapsed()<80 : saved.elapsed()>=200,"Simulation clocks remain isolated: "+slot.name()); } if(!menu().open) key("Escape"); }
            case 16 -> { if(menu().open) key("Escape"); }
            case 17 -> { require(!menu().open,"Escape resumes play"); key("F6"); }
            case 18 -> { require(!(boolean)get(game,"isometric"),"Existing view toggle works after load");
                Files.writeString(evidence.resolve("city-saves-playtest.txt"),"PASS: Native Linux X11/OpenGL playtest with isolated synthetic profile. Real xdotool input opened Controls > City saves, saved current, copied Harbour, rejected duplicate Harbour without switching, created fresh Hills, listed three saves, loaded Original city and restored its clock and railway state; copy retained railway state and new city had independent empty tracks, resumed play and toggled view with F6. Screenshot captured from this implementation. No personal profile used.\n");
                glfwSetWindowShouldClose(((dev.jayms.window.Window)get(game,"window")).getHandle(),true); }
            default -> throw new AssertionError("Harness did not finish");
        }
    }
    public void afterFrame(Main value) throws Exception {
        if(capture==null) return;
        int w=(int)get(game,"framebufferWidth"),h=(int)get(game,"framebufferHeight");
        glFinish();
        ByteBuffer data=ByteBuffer.allocateDirect(w*h*4); glReadPixels(0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,data);
        var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) { int i=(y*w+x)*4; image.setRGB(x,h-y-1,((data.get(i)&255)<<16)|((data.get(i+1)&255)<<8)|(data.get(i+2)&255)); }
        boolean duplicate = capture.equals("city-saves-duplicate.png");
        ImageIO.write(image,"png",evidence.resolve(capture).toFile()); capture=null;
        if (duplicate) {
            key("BackSpace BackSpace BackSpace BackSpace BackSpace BackSpace BackSpace");
            type("Hills");
        }
    }
    public static void main(String[] args) throws Exception {
        var test=new RailwayCitySavesSmoke(); test.root=Path.of(args[0]).toAbsolutePath(); test.evidence=Path.of(args[1]);
        Files.createDirectories(test.root); Files.createDirectories(test.evidence);
        Main game=new Main(); set(game,"offlineSave",test.root.resolve("offline-city.dat")); set(game,"gameConfig",GameConfig.cityGame());
        glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11); game.run(test);
        test.require(test.step==19,"All workflow checks executed");
        System.out.println("PASS: save, copy, duplicate, new, list, load, resume, view regression");
    }
}
