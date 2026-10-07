import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.*;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;

/** Real Main, native input, isolated saves. Writes only explicit assertions as evidence. */
public final class StressGridSmoke extends CitySavesSmoke {
    @Override String id() throws Exception {
        long handle=((dev.jayms.window.Window)get(game,"window")).getHandle();
        return Long.toString(org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window(handle));
    }
    @Override void key(String text) throws Exception {
        x("windowfocus",id());
        var args=new ArrayList<String>(List.of("key")); args.addAll(List.of(text.split(" "))); x(args.toArray(String[]::new));
    }
    final List<String> checks=new ArrayList<>();
    double gridElapsed;
    int rendered, waitFrames;
    void pass(String note) { checks.add(note); }
    void captured(boolean value) throws Exception {
        var method=Main.class.getDeclaredMethod("setCaptured",boolean.class); method.setAccessible(true); method.invoke(game,value);
    }
    void map(int x,int z) throws Exception {
        int[] size=((dev.jayms.window.Window)get(game,"window")).getSize();
        x("mousemove","--window",id(),Integer.toString(size[0]-224+x),Integer.toString(180+z)); x("click","1");
    }
    void verifyFocus(int type) throws Exception {
        var camera=(IsometricCamera)get(game,"overview");
        var grid=local().city.frame().stressGrid();
        int plot=grid.plotAt((int)camera.focusX(),(int)camera.focusZ());
        require(plot>=0 && grid.zone(plot).type()==type,"Map visits requested ring "+type);
        var world=(World)get(game,"world");
        var v=grid.zone(plot).polygon().vertices().get(0);
        require(world.sample((int)v.x()-1,grid.grade(),(int)v.z())==grid.surface((int)v.x()-1,(int)v.z()),"Road surface beside visited plot");
        pass("Map visited "+CitySimulation.ZONES[type]+" plot #"+(plot+1)+"; adjacent generated road surface matched; displayed FPS="+get(game,"fps")+".");
    }
    @Override public void started(Main value) throws Exception {
        super.started(value);
        if(starts>1) { captured(false); ((IsometricCamera)get(game,"overview")).zoom(-2); }
    }
    @Override public void beforeFrame(Main value) throws Exception {
        if(System.nanoTime()<due || rendered<waitFrames) return;
        due=System.nanoTime()+700_000_000L;
        Files.writeString(root.resolve("playtest-stage.txt"),"stage="+step+", starts="+starts+"\n");
        switch(step++) {
            case 0 -> { if(!menu().open) key("Escape"); }
            case 1 -> { require(menu().open,"Controls opened"); click(470,32); }
            case 2 -> { require(menu().saves.open,"City saves opened"); click(100,372); }
            case 3 -> {
                savedCity=CitySimulation.load(CitySaves.sidecar(active(),".city"));
                var entries=((CitySaves)get(game,"citySaves")).list();
                require(entries.size()==2 && entries.get(1).name().equals(StressGrid.NAME),"Preset installed beside Original city");
                require(savedCity.stressGrid()==null,"Original is an ordinary city");
                capture="stress-grid-saves.png"; pass("City saves listed Original city and automatically installed Stress Test Grid.");
            }
            case 4 -> click(80,140);
            case 5 -> click(420,372);
            case 6 -> {
                require(starts==2 && active().getParent().getFileName().toString().equals(StressGrid.NAME),"Preset loaded through native menu");
                var frame=local().city.frame(); require(frame.stressGrid()!=null && frame.zones().size()==1000000,"Million zones active");
                require(frame.citizens().isEmpty() && frame.buildings().isEmpty(),"Vacant zone benchmark has no invented population");
                require(frame.zones().get(500500).type()==0,"Central residential zone");
                pass("Loaded full 1000 x 1000 vacant plot grid through the save menu; 1,000,000 zones active.");
                waitFrames=rendered+44;
            }
            case 7 -> { capture="stress-grid-residential.png"; key("F10"); x("keydown","w"); waitFrames=rendered+12; }
            case 8 -> { x("keyup","w"); key("F10"); map(170,100); waitFrames=rendered+44; }
            case 9 -> { verifyFocus(1); capture="stress-grid-commercial.png"; }
            case 10 -> { map(181,100); waitFrames=rendered+44; }
            case 11 -> { verifyFocus(2); capture="stress-grid-industrial.png"; }
            case 12 -> { map(196,100); waitFrames=rendered+44; }
            case 13 -> { verifyFocus(3); capture="stress-grid-agricultural.png"; }
            case 14 -> { map(199,199); waitFrames=rendered+44; }
            case 15 -> {
                verifyFocus(3);
                var camera=(IsometricCamera)get(game,"overview");
                require(local().city.frame().stressGrid().plotAt((int)camera.focusX(),(int)camera.focusZ())==999999,"Last plot accessible");
                capture="stress-grid-last-plot.png"; pass("Visited plot #1,000,000 at the far corner; adjacent road surface matched.");
            }
            case 16 -> pass("Terrain had 44 rendered frames to stream before each ring screenshot; recorded camera pan with production F10.");
            case 17 -> { if(!menu().open) key("Escape"); }
            case 18 -> { require(menu().open,"Controls reopened"); click(470,32); }
            case 19 -> click(100,372);
            case 20 -> { gridElapsed=CitySimulation.load(CitySaves.sidecar(active(),".city")).elapsed(); type(StressGrid.NAME); }
            case 21 -> click(420,480);
            case 22 -> {
                require(starts==2,"Duplicate keeps active save");
                require(((String)get(menu().saves,"message")).contains("already exists"),"Duplicate gives error");
                capture="stress-grid-duplicate.png"; pass("Duplicate Stress Test Grid rejected without switching or overwriting.");
            }
            case 23 -> click(80,140);
            case 24 -> click(420,372);
            case 25 -> {
                require(starts==3 && local().city.frame().stressGrid()!=null,"Reload restored grid descriptor");
                require(local().city.frame().elapsed()>=gridElapsed,"Reload preserved elapsed simulation time");
                require(local().city.frame().zones().get(999999).type()==3,"Reload preserved far plot");
                pass("Save/reload restored million-plot layout and elapsed simulation time."); if(!menu().open) key("Escape");
            }
            case 26 -> click(470,32);
            case 27 -> click(80,110);
            case 28 -> click(420,372);
            case 29 -> {
                require(starts==4 && active().equals(root.resolve("offline-city.dat")),"Returned to Original city");
                require(local().city.frame().stressGrid()==null,"Ordinary city has no procedural grid");
                require(local().city.frame().roads().equals(savedCity.roads()),"Original roads unchanged");
                require(local().city.frame().citizens().size()==savedCity.citizens().size(),"Original local citizens retained");
                key("F6");
            }
            case 30 -> { require(!(boolean)get(game,"isometric"),"F6 view regression passed"); key("F6"); }
            case 31 -> { capture="stress-grid-original-regression.png"; pass("Original city restored roads and local citizens; F6 view toggle still works."); }
            case 32 -> glfwSetWindowShouldClose(((dev.jayms.window.Window)get(game,"window")).getHandle(),true);
            default -> throw new AssertionError("Driver did not finish");
        }
    }
    @Override public void afterFrame(Main value) throws Exception {
        String pending=capture; rendered++; super.afterFrame(value);
        if("stress-grid-industrial.png".equals(pending)) {
            var image=javax.imageio.ImageIO.read(evidence.resolve(pending).toFile()); int grass=0,pixels=0;
            for(int y=200;y<image.getHeight()-140;y++) for(int x=0;x<image.getWidth()-236;x++) {
                int rgb=image.getRGB(x,y),r=(rgb>>16)&255,g=(rgb>>8)&255,b=rgb&255;
                if(g>90 && g>r*1.3 && g>b*1.2) grass++;pixels++;
            }
            require(grass>pixels/3,"Flat industrial plots retain outdoor light in captured framebuffer");
            pass("Industrial framebuffer lighting regression: "+grass+" of "+pixels+" inspected pixels show lit grass.");
        }
    }
    public static void main(String[] args) throws Exception {
        var test=new StressGridSmoke(); test.root=Path.of(args[0]).toAbsolutePath(); test.evidence=Path.of(args[1]);
        Files.createDirectories(test.root);Files.createDirectories(test.evidence);
        Main game=new Main();set(game,"offlineSave",test.root.resolve("offline-city.dat"));set(game,"gameConfig",GameConfig.cityGame());
        glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);
        try {
            game.run(test); test.require(test.step==33,"All workflow steps executed");
            String report="Playtest: PASS. Linux native Main, inherited assigned X11 DISPLAY/XAUTHORITY, Mesa software OpenGL, isolated synthetic user.home. Browser playtesting does not apply to this native Java application.\n"+
                    "Input: xdotool save menu, ring map, F6 and production F10 recorder.\n"+
                    "Expected: automatic independent preset; exact million zones; reachable rings and far corner; duplicate rejected; save/reload preserved; original city and view toggle retained.\n"+
                    "Observed: "+String.join("\nObserved: ",test.checks)+"\nRendered frames: "+test.rendered+"\n";
            Files.writeString(test.evidence.resolve("stress-grid-playtest.txt"),report);
        } catch(Throwable e) {
            Files.writeString(test.root.resolve("assertion-failure.txt"),"stage="+test.step+", starts="+test.starts+", "+e.getClass().getSimpleName()+": "+e.getMessage()+"\n"); throw e;
        }
    }
}
