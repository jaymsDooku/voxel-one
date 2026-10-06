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
public class AviationSmoke {
    static Main game = new Main();
    static volatile Throwable failure;
    static Path out;
    static String windowId;
    static volatile String capture;
    static volatile String captured;
    static volatile long frames;
    static volatile Matrix4f screenMatrix;
    static volatile CityFrame observedCity;
    static volatile boolean setup;
    static volatile boolean setupDone;
    static Object get(String name) throws Exception {
        Field f = Main.class.getDeclaredField(name); f.setAccessible(true); return f.get(game);
    }
    static void set(String name,Object value) throws Exception {
        Field f = Main.class.getDeclaredField(name); f.setAccessible(true); f.set(game,value);
    }
    static CityFrame city() throws Exception {
        if(observedCity!=null)return observedCity;
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
                .transform(new Vector4f(x,city().roads().get(0).y()+1.03f,z,1));
        int px = java.lang.Math.round((p.x/p.w*.5f+.5f)*width());
        int py = java.lang.Math.round((.5f-p.y/p.w*.5f)*height());
        require(px>16 && px<width()-16 && py>=130 && py<height()-200,"Road point within viewport: world="+x+","+z+" screen="+px+","+py);
        click(px,py);
    }
    static void airportMenu(int row) throws Exception {
        click((int)(16+7.5f*(width()-32)/9f),height()-180);
        require(((CityTools)get("cityTools")).tool==6,"Special menu opens");
        int rowHeight=java.lang.Math.max(12,java.lang.Math.min(28,(height()-340)/12));
        click(80,140+rowHeight*row+rowHeight/2);
        require(((CityTools)get("cityTools")).tool==row-1,"Airport menu action");
    }
    static void prepare() throws Exception {
        var world=(World)get("world");var local=(LocalGame)get("local");
        int grade=world.terrain().column(8,24).height();var seed=local.city.frame();
        var edits=new ArrayList<Protocol.Edit>();
        for(int x=40;x<=175;x++)for(int z=49;z<=97;z++) {
            edits.add(new Protocol.Edit(x,grade,z,Blocks.DIRT));
            for(int y=grade+1;y<=Terrain.MAX_Y;y++)if(world.sample(x,y,z)!=0)edits.add(new Protocol.Edit(x,y,z,0));
        }
        for(var edit:edits){world.apply(edit);local.edits.put(edit.key(),edit);}
        var roads=new ArrayList<CityFrame.Road>();for(int x=40;x<=175;x++) {
            roads.add(new CityFrame.Road(x,50,grade));var edit=new Protocol.Edit(x,grade,50,Blocks.DIRT);world.apply(edit);local.edits.put(edit.key(),edit);
        }
        var citizens=new ArrayList<CityFrame.Citizen>();int index=0;
        for(var c:seed.citizens()) citizens.add(new CityFrame.Citizen(c.id(),c.name(),c.cohort(),52.5f+index++*2,grade+1.01f,50.5f,0,0,90,500,0,0,0,"Ready"));
        var ground=new CitySimulation.Ground(){
            public int type(int x,int y,int z){return world.sample(x,y,z);}
            public boolean occupied(int x,int y,int z,int w,int d){return CityOccupancy.overlaps(local.city.frame(),x,y,z,w,7,d);}
            public void apply(List<Protocol.Edit> batch){for(var edit:batch){world.apply(edit);local.edits.put(edit.key(),edit);}}
        };
        local.city=new CitySimulation(new GameConfig(true,false,1200,10),ground,world.terrain(),
                new CityFrame(new GameConfig(true,false,1200,10),0,roads,List.of(),List.of(),citizens,List.of(),seed.economy()));
        setupDone=true;
    }
    public static void main(String[] args) throws Exception {
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
                setup=true; for(int i=0;i<900&&!setupDone;i++)Thread.sleep(100);require(setupDone,"Synthetic city setup");
                var camera=(IsometricCamera)get("overview");
                camera.focus(70,80,city().roads().get(0).y()+1);
                camera.zoom(11);
                Thread.sleep(1500);
                // Build two airports using the production special menu and X11 clicks.
                airportMenu(9); camera.focus(62,58,city().roads().get(0).y()+1); camera.zoom(3); x("getwindowfocus");
                point(50,52); require(city().buildings().size()==1,"First airport permitted via menu");
                int origin=city().buildings().get(0).id();
                // One airport has no flight destination; edge is also exercised through the UI.
                ((CityTools)get("cityTools")).selectedCitizen=city().citizens().get(0).id();
                airportMenu(11); point(52,53);
                require(((String)get("notice")).contains("second airport"),"Single airport rejects flight: "+get("notice"));
                airportMenu(9); camera.focus(140,58,city().roads().get(0).y()+1); camera.zoom(3); x("getwindowfocus");
                point(130,52); require(city().buildings().size()==2,"Second airport permitted via menu");
                airportMenu(10); point(132,54);
                require(city().buildings().get(1).capacity()==16,"Mayor adds second runway via menu");
                ((CityTools)get("cityTools")).selectedCitizen=0;
                camera.focus(148,73,city().roads().get(0).y()+1);camera.zoom(-3);x("getwindowfocus");
                capture="airport-expanded.png";
                long captureDeadline=System.currentTimeMillis()+60000;
                while(!"airport-expanded.png".equals(captured) && System.currentTimeMillis()<captureDeadline) Thread.sleep(50);
                require("airport-expanded.png".equals(captured),"Expanded airport capture completed before camera moves");
                // Add a synthetic access road; reject exchange placement through real UI.
                var localGame=(LocalGame)get("local");
                localGame.city.command(new CityCommand(CityCommand.ROAD,0,List.of(new Polygon.Point(120,90),new Polygon.Point(128,90))),1,null);
                x("getwindowfocus");
                double overlapBudget=city().economy().budget();
                var overlapEdits=new HashMap<>(localGame.edits);
                camera.focus(140,85,city().roads().get(0).y()+1);camera.zoom(3);x("getwindowfocus");
                click((int)(16+8.5f*(width()-32)/9f),height()-180);point(130,85);
                require(((String)get("notice")).contains("occupied"),"Exchange inside second runway rejected");
                require(city().economy().budget()==overlapBudget,"Rejected exchange preserves treasury");
                require(localGame.edits.equals(overlapEdits),"Rejected exchange preserves voxels");
                capture="airport-exchange-rejected.png";
                long rejectionDeadline=System.currentTimeMillis()+60000;
                while(!"airport-exchange-rejected.png".equals(captured) && System.currentTimeMillis()<rejectionDeadline)Thread.sleep(50);
                require("airport-exchange-rejected.png".equals(captured),"Rejection image saved");
                // Regression: standard City hall permit still works beside the road.
                camera.focus(104,56,city().roads().get(0).y()+1);camera.zoom(3);x("getwindowfocus");
                click((int)(16+7.5f*(width()-32)/9f),height()-180);
                ((CityTools)get("cityTools")).specialKind=0;((CityTools)get("cityTools")).specialLevel=3;
                point(100,52); require(city().buildings().stream().anyMatch(b->b.type()==6),"City hall permit regression");
                int inspect=city().citizens().get(0).id();
                // Inspect citizen with a real screen click, then choose a destination.
                var focusCitizen=city().citizens().stream().filter(c->c.id()==inspect).findFirst().orElseThrow();
                camera.focus(focusCitizen.x(),focusCitizen.z(),focusCitizen.y());camera.zoom(-1);x("getwindowfocus");
                click((int)(16+.5f*(width()-32)/9f),height()-180);
                var citizen=city().citizens().stream().filter(c->c.id()==inspect).findFirst().orElseThrow();
                var cp=new Matrix4f(screenMatrix).transform(new Vector4f(citizen.x(),citizen.y()+1,citizen.z(),1));
                // Send move and click together. Waiting two rendered frames between them
                // would aim behind a moving commuter on a slow software-rendered display.
                x("mousemove","--window",windowId,Integer.toString(java.lang.Math.round((cp.x/cp.w*.5f+.5f)*width())),
                        Integer.toString(java.lang.Math.round((.5f-cp.y/cp.w*.5f)*height())),"click","1");
                int person=((CityTools)get("cityTools")).selectedCitizen;
                require(person!=0 && city().citizens().stream().anyMatch(c->c.id()==person&&c.age()>18),
                        "Adult citizen selected by inspect click: selected="+person);
                // Crowded commuters can share a screen point. Use the adult actually selected by the UI.
                airportMenu(11);camera.focus(140,58,city().roads().get(0).y()+1);camera.zoom(3);x("getwindowfocus");point(132,53);
                require(!city().aviation().flights().isEmpty(),"Flight booked via destination click");
                boolean boarding=false,flying=false,arrived=false,recording=false,recorded=false;
                long until=System.currentTimeMillis()+240000;
                while(System.currentTimeMillis()<until && !arrived) {
                    var frame=city();var c=frame.citizens().stream().filter(v->v.id()==person).findFirst().orElseThrow();
                    if(c.activity().startsWith("Boarding")) {
                        if(!boarding) {
                            var flight=frame.aviation().flights().get(0);
                            var originAirport=frame.buildings().stream().filter(b->b.id()==flight.origin()).findFirst().orElseThrow();
                            camera.focus(originAirport.x()+16,originAirport.z()+14,originAirport.y());camera.zoom(-1);x("getwindowfocus");
                            capture="airport-boarding.png";
                        }
                        boarding=true;
                    }
                    if(c.activity().startsWith("Flying")) {
                        if(!flying) { camera.focus(c.x(),c.z(),c.y());camera.zoom(3); }
                        flying=true;
                        if(!recording && !recorded) { x("key","F10");recording=true; }
                        if(recording && frame.aviation().flights().get(0).clock()>10) { x("key","F10");recording=false;recorded=true; }
                        camera.pan(c.x()-camera.focusX(),c.z()-camera.focusZ());
                        // Focus elevation follows the actual flight path, rather than ground.
                        camera.focus(c.x(),c.z(),c.y());camera.zoom(3);
                        if(frame.aviation().flights().get(0).clock()>8 && frame.aviation().flights().get(0).clock()<12) capture="airport-flight.png";
                    }
                    if(c.activity().startsWith("Arrived")) {arrived=true;camera.focus(c.x(),c.z(),c.y());camera.zoom(3);capture="airport-arrival.png";}
                    Thread.sleep(120);
                }
                require(boarding&&flying&&arrived,"Passenger walked, boarded, flew and arrived: boarding="+boarding+", flying="+flying+", arrived="+arrived+", elapsed="+city().elapsed()+", flights="+city().aviation().flights());
                require(city().citizens().stream().filter(c->c.id()==person).allMatch(c->c.x()>130&&c.x()<136),"Citizen at destination terminal");
                Thread.sleep(1200);
                x("windowsize",windowId,"1280","640");x("getwindowfocus");
                click((int)(16+7.5f*(width()-32)/9f),height()-180);
                require(((CityTools)get("cityTools")).tool==6,"Compact Special menu opens");
                capture="airport-compact-menu.png";x("getwindowfocus");
                click(80,427);require(((CityTools)get("cityTools")).tool==10,"Compact menu flight choice works above toolbar");
                x("windowsize",windowId,"1280","720");x("getwindowfocus");
                x("key","F9");click(920,60);
                require(((MayorDashboard)get("mayorDashboard")).tab==6,"District tab preserved");
                click(440,180);
                require(city().population().population()==1_000_000,"District settlement command remains distinct from runway command");
                click(40,285);
                require(city().population().agents().size()==64,"District focus command remains distinct from flight command");
                capture="airport-regional-regression.png";
                long districtCaptureDeadline=System.currentTimeMillis()+60000;
                while(!"airport-regional-regression.png".equals(captured) && System.currentTimeMillis()<districtCaptureDeadline) Thread.sleep(50);
                require("airport-regional-regression.png".equals(captured),"District capture saved");
                if(recording)x("key","F10");Thread.sleep(2500);
                Files.writeString(out.resolve("results.json"),"{\"status\":\"passed\",\"platform\":\"Linux X11 inherited role display; Mesa\",\"profile\":\"isolated synthetic offline city and flattened sites\",\"steps\":[\"Special menu Airport: place first terminal and runway\",\"Book selected citizen: single airport rejected without mutation\",\"Place second airport and expand it to two runways\",\"Place City hall through original permit menu\",\"Inspect adult citizen; Book flight; click destination airport\",\"Observe walking, boarding, curved flight and arrival at second terminal\",\"At 1280 x 640 open Special menu and select Book flight above toolbar\",\"Districts: settle 1000000 and focus 64 nearby residents\"],\"expected\":\"Two voxel airports, expanded runway, visible jet, citizen arrives, existing permits work\",\"observed\":\"All checks passed in running Main with X11 input\",\"recording\":\"production F10 recorder\"}\n");
            } catch(Throwable e) {
                failure=e;
                try { Files.writeString(out.resolve("failure.txt"),e.getClass().getSimpleName()+": "+e.getMessage()); } catch(Exception ignored) {}
            } finally { if(handle!=0) glfwSetWindowShouldClose(handle,true); }
        });
        driver.setDaemon(true);driver.start();
        game.run(new Main.FrameObserver() {
            public void beforeFrame(Main main) throws Exception {
                // Finish production meshes before a still capture, rather than recording a
                // temporary distant-terrain fallback while the normal mesh budget catches up.
                if(capture!=null)for(var chunk:((World)get("world")).getLoadedChunks().values())chunk.checkMesh();
            }
            public void afterFrame(Main main) throws Exception {
                observedCity=((LocalGame)get("local")).city.frame();
                if(setup&&!setupDone)prepare();
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
                captured=name;
            }
        });
        driver.join(1000);
        if(failure!=null) throw new AssertionError("Airport playtest failed",failure);
        require(Files.exists(out.resolve("results.json")),"Driver finished");
    }
}
