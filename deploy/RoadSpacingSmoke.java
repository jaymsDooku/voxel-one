import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;

/** Native app playtest. Isolated local world; actual GLFW input and F10 recording. */
public class RoadSpacingSmoke {
    static Main game;
    static volatile Throwable failure;
    static volatile CityFrame latest;
    static Object get(String name) throws Exception { var f=Main.class.getDeclaredField(name);f.setAccessible(true);return f.get(game); }
    static void set(String name,Object value) throws Exception { var f=Main.class.getDeclaredField(name);f.setAccessible(true);f.set(game,value); }
    static void input(String... args) throws Exception {
        var command=new ArrayList<String>();command.add("xdotool");command.addAll(List.of(args));
        var p=new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        if(p.waitFor()!=0)throw new AssertionError("X input failed");Thread.sleep(350);
    }
    static boolean onFootpath(float x,float z,CityFrame city,Set<String> roads) {
        for(var b:city.buildings())if(x>b.x() && x<b.x()+6 && z>b.z() && z<b.z()+7)return false;
        int cx=(int)Math.floor(x),cz=(int)Math.floor(z);
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(roads.contains((cx+dx)+","+(cz+dz)))return true;
        return false;
    }
    static void await(java.util.concurrent.Callable<Boolean> condition,String message) throws Exception {
        for(int i=0;i<100;i++){if(condition.call())return;Thread.sleep(100);}
        throw new AssertionError(message);
    }
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args[0]);Files.createDirectories(out);
        String pedestrianGap=args.length > 1 ? args[1] : "1.0";
        String mountedGap=args.length > 2 ? args[2] : "1.4";
        RoadSpacing.configure("--pedestrian-spacing",pedestrianGap);
        RoadSpacing.configure("--mounted-spacing",mountedGap);
        game=new Main();glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);
        set("gameConfig",new GameConfig(true,false,1200,10));
        set("productionCatalog",ProductionCatalog.toolEra());
        set("offlineSave",out.resolve("world.dat"));
        var driver=new Thread(()->{
            long handle=0;
            try {
                for(int i=0;i<180 && (get("player")==null || (int)get("fps")<0);i++)Thread.sleep(500);
                require(get("player")!=null && (int)get("fps")>=0,"Engine ready");Thread.sleep(1500);
                handle=((dev.jayms.window.Window)get("window")).getHandle();
                input("search","--onlyvisible","--name","^Voxel One","windowfocus");
                if(((dev.jayms.ui.ControlsMenu)get("menu")).open)input("key","Escape");
                if(!(boolean)get("isometric"))input("key","F6");
                require((boolean)get("isometric"),"Isometric view");
                var local=(LocalGame)get("local");
                var first=latest;require(first.citizens().size()==12,"Starter population");
                var overview=(IsometricCamera)get("overview");
                overview.focus(16,24,first.roads().get(0).y()+1);
                overview.zoom(4);overview.rotateDegrees(180);
                input("key","F10");
                int moving=0, separated=0, mounted=0, overlaps=0;double closest=Double.POSITIVE_INFINITY, minMargin=Double.POSITIVE_INFINITY;
                Map<Integer,CityFrame.Citizen> previous=new HashMap<>();
                for(int sample=0;sample<100;sample++) {
                    Thread.sleep(150);var frame=latest;
                    var cells=new HashSet<String>();for(var r:frame.roads())cells.add(r.x()+","+r.z());
                    for(var c:frame.citizens()) {
                        var old=previous.put(c.id(),c);
                        if(old!=null && Math.hypot(c.x()-old.x(),c.z()-old.z())>.01)moving++;
                        if(c.horse()!=0)mounted++;
                        if(!onFootpath(c.x(),c.z(),frame,cells))continue;
                        for(var d:frame.citizens())if(d.id()>c.id() && onFootpath(d.x(),d.z(),frame,cells)) {
                            double distance=Math.hypot(c.x()-d.x(),c.z()-d.z());
                            if(distance>=Float.parseFloat(pedestrianGap) && distance<4)separated++;
                            if(distance<.25)overlaps++;
                            double gap=c.horse()!=0 || d.horse()!=0 ? Math.max(Float.parseFloat(pedestrianGap),Float.parseFloat(mountedGap)) : Float.parseFloat(pedestrianGap);
                            minMargin=Math.min(minMargin,distance-gap);
                            if(sample>50)closest=Math.min(closest,distance);
                        }
                    }
                }
                input("key","F10");Thread.sleep(2500);
                require(moving>20,"Citizens keep moving with configured spacing");
                require(separated>20,"Visible nearby users have a gap");
                require(closest>=Float.parseFloat(pedestrianGap)-.03,"Configured road clearance holds");
                require(mounted>0,"Mounted traffic exercised");
                require(minMargin>=-.03,"Walking and mounted gaps hold on roads and verges");
                if(Float.parseFloat(pedestrianGap)==0 && Float.parseFloat(mountedGap)==0)
                    require(overlaps>0,"Zero-gap edge permits initially coincident users to move");
                // Regression: native inventory opens and closes after the road playtest.
                input("key","e");await(()->((dev.jayms.ui.InventoryHud)get("inventoryHud")).open,"Inventory opens");
                input("key","e");await(()->!((dev.jayms.ui.InventoryHud)get("inventoryHud")).open,"Inventory closes");
                Files.writeString(out.resolve("results.json"),"{\"platform\":\"Linux X11 Xvfb Mesa software OpenGL\",\"status\":\"passed\",\"pedestrianGapBlocks\":"+pedestrianGap+",\"mountedGapBlocks\":"+mountedGap+",\"movingSamples\":"+moving+",\"nearbySeparatedPairSamples\":"+separated+",\"mountedSamples\":"+mounted+",\"overlapSamples\":"+overlaps+",\"minimumClearanceMarginBlocks\":"+minMargin+",\"lateClosestRoadPairBlocks\":"+(Double.isFinite(closest)?closest:"null")+",\"regression\":\"Native inventory opens and closes\",\"recording\":\"engine F10 recorder\"}\n");
            }catch(Throwable e){failure=e;}
            finally{if(handle!=0)glfwSetWindowShouldClose(handle,true);}
        });driver.setDaemon(true);driver.start();
        game.run(new Main.FrameObserver() {
            int frames;
            public void started(Main running) throws Exception {
                var city=((LocalGame)get("local")).city;
                for(int i=0;i<300;i++)city.advance(1);
                int grade=city.frame().roads().get(0).y();
                set("player",new dev.jayms.player.Player(new org.joml.Vector3f(43.5f,grade+1.01f,24.5f),-90,-20,(Camera)get("camera")));
                int index=0, rider=0;
                for(int id:city.ecs.query(CitySimulation.Household.class,CitySimulation.Position.class)) {
                    var p=city.ecs.get(id,CitySimulation.Position.class);
                    var h=city.ecs.get(id,CitySimulation.Household.class);
                    p.x=9.5f+index++*1.55f;
                    if(Float.parseFloat(pedestrianGap)==0 && index==2)p.x=9.5f;
                    p.z=24.5f;p.y=grade+1.01f;h.horse=0;
                    city.ecs.get(id,CitySimulation.Needs.class).hunger=100;
                    var t=city.ecs.get(id,CitySimulation.Travel.class);
                    t.route.clear();t.passingPoints=0;t.target=-9999;t.retryAt=0;
                    if(rider==0 && h.cohort>0)rider=id;
                }
                require(rider!=0,"Synthetic mounted commuter available");
                int parked=0;
                for(int horseId:city.ecs.query(CitySimulation.Mount.class)) {
                    city.ecs.get(horseId,CitySimulation.Mount.class).rider=0;
                    var hp=city.ecs.get(horseId,CitySimulation.Position.class);
                    hp.x=8.5f;hp.z=21.8f-parked++*1.5f;hp.y=grade+1.01f;
                }
                int horse=city.ecs.query(CitySimulation.Mount.class).get(0);
                var mount=city.ecs.get(horse,CitySimulation.Mount.class);mount.rider=-rider;
                var p=city.ecs.get(rider,CitySimulation.Position.class);
                p.x=4.5f;p.z=24.5f;p.y=grade+1.76f;
                var hp=city.ecs.get(horse,CitySimulation.Position.class);hp.x=p.x;hp.z=p.z;hp.y=grade+1.01f;
                city.ecs.get(rider,CitySimulation.Household.class).horse=horse;
            }
            public void afterFrame(Main running) throws Exception {
                latest=((LocalGame)get("local")).city.frame();
                if(++frames==10) {
                    var pixels=org.lwjgl.BufferUtils.createByteBuffer(1280*720*4);
                    int[] size=((dev.jayms.window.Window)get("window")).getSize();
                    int w=size[0],h=size[1];pixels=org.lwjgl.BufferUtils.createByteBuffer(w*h*4);
                    org.lwjgl.opengl.GL11.glReadPixels(0,0,w,h,org.lwjgl.opengl.GL11.GL_RGBA,org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE,pixels);
                    var image=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_RGB);
                    for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=((h-y-1)*w+x)*4;image.setRGB(x,y,((pixels.get(i)&255)<<16)|((pixels.get(i+1)&255)<<8)|(pixels.get(i+2)&255));}
                    javax.imageio.ImageIO.write(image,"png",out.resolve("road-spacing-native.png").toFile());
                }
            }
        });driver.join(1000);
        if(failure!=null)throw new AssertionError("Road playtest failed",failure);
        require(Files.exists(out.resolve("results.json")),"Playtest complete");
    }
}
