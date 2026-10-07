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
public class CarrierRoutesSmoke {
    static Main game = new Main();
    static volatile Throwable failure;
    static Path out;
    static String windowId;
    static volatile String capture;
    static volatile Protocol.Edit pendingEdit;
    static volatile ShippingRoutes.Ship finalPose;
    static double arrivalClock,returnClock;
    static volatile long frames;
    static volatile Matrix4f screenMatrix;
    static Object get(String name) throws Exception {
        Field f = Main.class.getDeclaredField(name); f.setAccessible(true); return f.get(game);
    }
    static void set(String name,Object value) throws Exception {
        Field f = Main.class.getDeclaredField(name); f.setAccessible(true); f.set(game,value);
    }
    static CityFrame city() throws Exception {
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
    static void edit(Protocol.Edit value) throws Exception {
        pendingEdit=value;
        long deadline=System.nanoTime()+30_000_000_000L;
        while(pendingEdit!=null&&System.nanoTime()<deadline)Thread.sleep(50);
        require(pendingEdit==null,"Render-thread edit completed");
    }
    static void photograph(String name) throws Exception {
        capture=name;long deadline=System.nanoTime()+30_000_000_000L;
        while(!Files.exists(out.resolve(name))&&System.nanoTime()<deadline)Thread.sleep(50);
        require(Files.exists(out.resolve(name)),"Fresh capture saved: "+name);
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
    static void menu(int kind) throws Exception {
        click((int)(16+7.5f*(width()-32)/9f),height()-180);
        require(((CityTools)get("cityTools")).tool==6,"Special buildings menu opened");
        click(80,154+28*kind);
        require(((CityTools)get("cityTools")).specialKind==kind,"Selected special building");
    }
    static int[] site(Terrain t) {
        for(int x=-100;x<120;x+=6) for(int z=200;z<250;z++) {
            boolean ok=true;
            for(int dx=0;dx<6 && ok;dx++) for(int dz=-1;dz<=20 && ok;dz++) {
                if(dz<3) ok &= t.fields(x+dx,z+dz).waterLevel()<t.column(x+dx,z+dz).height();
                if(dz>=14 && dz<20) ok &= t.ocean(x+dx,z+dz) && t.column(x+dx,z+dz).height()<=12;
                for(int y=27;y<=95 && ok;y++) if(t.block(x+dx,y,z+dz)!=0) ok=false;
            }
            if(ok) return new int[]{x,z};
        }
        throw new AssertionError("No coastal site");
    }
    static int[] coastal;
    static int[] second;
    static void fixture(Path save) throws Exception {
        var t=new Terrain(Terrain.DEFAULT_SEED); coastal=site(t);
        second=site(new Terrain(Terrain.DEFAULT_SEED));
        for(int x=coastal[0]+96;x<coastal[0]+300;x+=6) {
            boolean ok=true;
            for(int dx=0;dx<6;dx++)for(int dz=-1;dz<=20;dz++) {
                if(dz<3)ok &= t.fields(x+dx,coastal[1]+dz).waterLevel()<t.column(x+dx,coastal[1]+dz).height();
                if(dz>=14)ok &= t.ocean(x+dx,coastal[1]+dz)&&t.column(x+dx,coastal[1]+dz).height()<=12;
                for(int y=27;y<=95;y++)if(t.block(x+dx,y,coastal[1]+dz)!=0)ok=false;
            }
            if(ok){second=new int[]{x,coastal[1]};break;}
        }
        require(second[0]!=coastal[0],"Second coastal site");
        var cfg=new GameConfig(true,false,1200,10);
        var roads=new ArrayList<CityFrame.Road>();
        for(int x=coastal[0];x<coastal[0]+6;x++) roads.add(new CityFrame.Road(x,coastal[1]-2,26));
        for(int x=second[0];x<second[0]+6;x++)roads.add(new CityFrame.Road(x,second[1]-2,26));
        for(int x=40;x<46;x++) roads.add(new CityFrame.Road(x,78,26));
        roads.add(new CityFrame.Road(8,24,26));
        try(var stream=new java.io.DataOutputStream(Files.newOutputStream(save.resolveSibling(save.getFileName()+".city")))) {
            stream.writeInt(0x4349543C);
            new CityFrame(cfg,0,roads,List.of(),List.of(),List.of(),List.of()).write(stream);
        }
        var local=new LocalGame(save,Terrain.DEFAULT_SEED);
        for(var r:roads) WorldVoxels.remember(local.edits,new Protocol.Edit(r.x(),26,r.z(),Blocks.DIRT));
        for(int dx=0;dx<6;dx++) for(int dz=-1;dz<=7;dz++) for(int y=27;y<=95;y++)
            WorldVoxels.remember(local.edits,new Protocol.Edit(40+dx,y,80+dz,0));
        local.save();
    }
    static void captureFrame() throws Exception {
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
    }
    public static void main(String[] args) throws Exception {
        out=Path.of(args[0]); Files.createDirectories(out);
        fixture(out.resolve("synthetic-world.dat"));
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
                var camera=(IsometricCamera)get("overview");
                // Inland rejection through the production menu and native click.
                camera.focus(43,84,27); camera.zoom(11); x("getwindowfocus");
                menu(6); point(40.2f,80.2f);
                require(city().buildings().isEmpty(),"Inland port rejected atomically");
                require(((String)get("notice")).contains("open ocean"),"Inland rejection explains coast requirement: "+get("notice"));
                capture="carrier-routes-inland-rejection.png"; Thread.sleep(1000);
                // Technical college still uses its old kind and level controls.
                menu(5); click(80,154+28*7); click(80,154+28*7); point(40.2f,80.2f);
                require(city().buildings().stream().anyMatch(b -> b.type()==22),"Technical college level 3 regression");
                x("key","Escape");
                camera.focus(coastal[0],coastal[1],27); camera.zoom(4);
                x("getwindowfocus"); Thread.sleep(3500);
                x("key","F10");
                menu(6); capture="carrier-routes-coastal-menu.png"; Thread.sleep(1000);
                point(coastal[0]+.2f,coastal[1]+.2f);
                require(((String)get("notice")).equals("Permitted Coastal port"),"Coastal permit: "+get("notice"));
                var port=city().buildings().stream().filter(b -> b.type()==SpecialBuildings.PORT).findFirst().orElseThrow();
                var world=(World)get("world");
                require(world.sample(coastal[0]+1,15,coastal[1]+14)==Blocks.STONE,"Carrier hull present");
                require(world.sample(coastal[0],14,coastal[1]+15)==Blocks.WATER,"Ocean beside berth preserved");
                int count=city().buildings().size(); point(coastal[0]+.2f,coastal[1]+.2f);
                require(city().buildings().size()==count,"Overlapping port rejected");
                x("key","Escape");
                camera.focus(coastal[0]+3,coastal[1]+10,22); camera.zoom(-2); x("getwindowfocus");
                capture="carrier-routes-port-carrier.png"; Thread.sleep(1800);
                camera.rotate(1); x("getwindowfocus"); capture="carrier-routes-port-reverse.png"; Thread.sleep(1500);
                camera.rotate(-1); x("getwindowfocus");
                var service=(ShippingRoutes)get("shippingRoutes");
                require(service.ships(city(),world).isEmpty(),"Single port has no shipping route");
                camera.focus(second[0],second[1],27); camera.zoom(2); x("getwindowfocus"); Thread.sleep(1500);
                menu(6); point(second[0]+.2f,second[1]+.2f);
                require(((String)get("notice")).equals("Permitted Coastal port"),"Second coastal permit");
                require(service.routes(city(),world).size()==1,"Water route created automatically");
                x("key","Escape");
                var open=service.routes(city(),world);
                var corridor=open.get(0).points().get(1);
                int obstacleX=(int)corridor.x(),obstacleZ=(int)corridor.z();
                camera.focus(obstacleX,obstacleZ,16);x("getwindowfocus");
                edit(new Protocol.Edit(obstacleX,15,obstacleZ,Blocks.STONE));
                require(world.sample(obstacleX,15,obstacleZ)==Blocks.STONE,"Real world stone obstruction exists");
                require(service.routes(city(),world).isEmpty(),"Cached production route invalidated by edit");
                require(new ShippingRoutes().routes(city(),world).isEmpty(),"Fresh route rejects edited world obstacle");
                require(service.ships(city(),world).isEmpty(),"Blocked service does not render a sailing ship");
                photograph("carrier-routes-obstacle-blocked.png");
                edit(new Protocol.Edit(obstacleX,15,obstacleZ,0));
                require(service.routes(city(),world).size()==1,"Removing obstacle restores production route");
                require(new ShippingRoutes().routes(city(),world).size()==1,"Fresh route restored after removal");
                photograph("carrier-routes-obstacle-cleared.png");
                var before=service.ships(city(),world).get(0);
                camera.focus(before.x(),before.z(),16);camera.zoom(-2);x("getwindowfocus");
                capture="carrier-routes-sailing.png"; Thread.sleep(2500);
                var after=service.ships(city(),world).get(0);
                require(java.lang.Math.hypot(after.x()-before.x(),after.z()-before.z())>2,"Carrier moves in running game");
                capture="carrier-routes-moved.png";Thread.sleep(1000);
                camera.rotate(1);x("getwindowfocus");capture="carrier-routes-bridge.png";Thread.sleep(1500);
                x("key","F10"); Thread.sleep(2000);
                var route=service.routes(city(),world).get(0);
                var destination=route.points().get(route.points().size()-1);
                boolean arrived=false;
                for(int i=0;i<800;i++) {
                    var ship=service.ships(city(),world).get(0);
                    camera.focus(ship.x(),ship.z(),16);
                    if(!ship.sailing()&&java.lang.Math.hypot(ship.x()-destination.x(),ship.z()-destination.z())<.1) {arrived=true;break;}
                    Thread.sleep(500);
                }
                require(arrived,"Carrier completes water route to destination anchorage");
                arrivalClock=city().elapsed();
                camera.focus(destination.x(),destination.z()-14,20);camera.zoom(-2);
                photograph("carrier-routes-arrival.png");
                boolean returning=false;
                for(int i=0;i<480;i++) {
                    var ship=service.ships(city(),world).get(0);
                    if(ship.sailing()&&ship.z()>destination.z()+1) {returning=true;break;}
                    Thread.sleep(500);
                }
                require(returning,"Carrier leaves destination on return voyage after dock wait");
                returnClock=city().elapsed();
                require(returnClock>arrivalClock,"Real city time advanced through destination dwell");
                photograph("carrier-routes-return.png");
                finalPose=service.ships(city(),world).get(0);
                Files.writeString(out.resolve("results.json"),"{\"status\":\"passed\",\"platform\":\"Linux X11 inherited role display; Mesa\",\"profile\":\"isolated synthetic offline city on generated version 3 terrain\",\"checks\":[\"actual menu clicks reject inland port\",\"technical college level 3 regression\",\"actual coastal port permit\",\"carrier hull and containers in world\",\"ocean beside berth preserved\",\"overlap rejected\",\"two camera angles\",\"single port edge: no service\",\"second port actual permit click creates water route\",\"carrier moves more than 2 blocks in running application\",\"complete outbound voyage reaches destination and dwells\",\"return voyage starts after destination dwell\",\"cached and fresh routes reject real world stone edit\",\"removing obstacle restores routes\"],\"recording\":\"production F10 recorder\",\"siteX\":"+coastal[0]+",\"siteZ\":"+coastal[1]+"}\n");
            } catch(Throwable e) {
                failure=e;
                try { Files.writeString(out.resolve("failure.txt"),e.getClass().getSimpleName()+": "+e.getMessage()); } catch(Exception ignored) {}
            } finally { if(handle!=0) glfwSetWindowShouldClose(handle,true); }
        });
        driver.setDaemon(true);driver.start();
        game.run(new Main.FrameObserver() {
            public void beforeFrame(Main main) throws Exception {
                var edit=pendingEdit;
                if(edit!=null) {
                    ((World)get("world")).apply(edit);
                    WorldVoxels.remember(((LocalGame)get("local")).edits,edit);
                    pendingEdit=null;
                }
            }
            public void afterFrame(Main main) throws Exception { captureFrame(); }
        });
        driver.join(1000);
        if(failure!=null) throw new AssertionError("Shipping playtest failed",failure);
        require(Files.exists(out.resolve("results.json")),"Driver finished");
        var restored = new LocalGame(out.resolve("synthetic-world.dat"),42);
        require(restored.generatorVersion==3,"Saved ocean generator version");
        require(restored.edits.values().stream().anyMatch(e -> e.x()==coastal[0]+1 && e.y()==15
                && e.z()==coastal[1]+14 && e.type()==Blocks.STONE),"Carrier persists across game shutdown and reload");
        var savedCity=CitySimulation.load(out.resolve("synthetic-world.dat.city"));
        require(savedCity.buildings().stream().anyMatch(b -> b.type()==SpecialBuildings.PORT),"Saved port permit reloads");
        var restoredWorld=new World(restored.seed,new dev.jayms.net.model.ModelLibrary(),restored.generatorVersion);
        restored.edits.values().forEach(restoredWorld::apply);
        var reloadedShips=new ShippingRoutes().ships(savedCity,restoredWorld);
        require(reloadedShips.size()==1,"Saved ports restore automatic shipping service using replayed edits");
        require(savedCity.elapsed()>=returnClock,"Reload preserves elapsed return voyage time");
        require(reloadedShips.get(0).equals(ShippingRoutes.sample(new ShippingRoutes().routes(savedCity,restoredWorld).get(0),savedCity.elapsed())),"Reload pose follows persisted clock");
        // Restart the production application, not just the snapshot codec.
        game=new Main();set("offlineSave",out.resolve("synthetic-world.dat"));
        set("gameConfig",new GameConfig(true,false,1200,10));
        var reloadDriver=new Thread(() -> {
            long handle=0;
            try {
                for(int i=0;i<240&&get("player")==null;i++)Thread.sleep(500);
                require(get("player")!=null,"Reloaded application started");
                handle=((dev.jayms.window.Window)get("window")).getHandle();
                windowId=Long.toString(org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window(handle));
                x("windowfocus",windowId);x("windowsize",windowId,"1280","720");
                if(((ControlsMenu)get("menu")).open)x("key","Escape");
                var liveWorld=(World)get("world");var service=(ShippingRoutes)get("shippingRoutes");
                require(city().elapsed()>=savedCity.elapsed(),"Restarted application resumes saved city clock");
                require(service.ships(city(),liveWorld).size()==1,"Restarted renderer restores shipping service");
                var pose=service.ships(city(),liveWorld).get(0);
                ((IsometricCamera)get("overview")).focus(pose.x(),pose.z(),16);
                x("getwindowfocus");photograph("carrier-routes-reloaded.png");
            } catch(Throwable e) {
                failure=e;
                try {Files.writeString(out.resolve("failure.txt"),e.getClass().getSimpleName()+": "+e.getMessage());}catch(Exception ignored){}
            } finally {if(handle!=0)glfwSetWindowShouldClose(handle,true);}
        });
        reloadDriver.setDaemon(true);reloadDriver.start();
        game.run(new Main.FrameObserver(){public void afterFrame(Main main)throws Exception{captureFrame();}});
        reloadDriver.join(1000);if(failure!=null)throw new AssertionError("Native reload failed",failure);
        require(Files.exists(out.resolve("carrier-routes-reloaded.png")),"Restarted application capture exists");
        String report=Files.readString(out.resolve("results.json")).trim();
        Files.writeString(out.resolve("results.json"),report.substring(0,report.length()-1)+",\"saveReload\":\"passed\",\"nativeRestart\":\"passed\",\"arrivalClock\":"+arrivalClock+",\"returnClock\":"+returnClock+",\"reloadedElapsed\":"+savedCity.elapsed()+"}\n");
    }
}
