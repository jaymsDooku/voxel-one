import dev.jayms.*;
import dev.jayms.net.city.GameConfig;
import dev.jayms.ui.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;

/** Synthetic offline game; real X11 clicks through production GLFW callbacks. */
public class SandboxPlaytest {
    static Main game = new Main();
    static volatile Throwable failure;
    static int fixtureY;
    static final java.util.concurrent.ConcurrentLinkedQueue<java.util.concurrent.FutureTask<Void>> jobs = new java.util.concurrent.ConcurrentLinkedQueue<>();
    static void onFrame(java.util.concurrent.Callable<Void> action) throws Exception {
        var job = new java.util.concurrent.FutureTask<Void>(action); jobs.add(job);
        job.get(90, java.util.concurrent.TimeUnit.SECONDS);
    }
    static dev.jayms.player.Player player() throws Exception { return (dev.jayms.player.Player)get("player"); }
    static dev.jayms.net.LocalGame local() throws Exception { return (dev.jayms.net.LocalGame)get("local"); }
    static void warmFixture() throws Exception {
        var w=(dev.jayms.World)get("world");
        for(var entry:w.getLoadedChunks().entrySet())
            if(entry.getKey().chunkX()==0 && entry.getKey().chunkZ()==1)entry.getValue().checkMesh();
    }
    static void firstPerson(String id, Path out) throws Exception {
        require(!(boolean)get("isometric"), "Sandbox starts first person");
        require(!local().city.frame().config().city() && local().city.frame().citizens().isEmpty()
                && local().city.frame().buildings().isEmpty(), "No City bootstrap");
        x("key", "F9"); x("key", "F9"); require(!((MayorDashboard)get("mayorDashboard")).open,"No mayor UI");
        capture(id,out.resolve("voxel-sandbox-first-launch.png"));
        onFrame(() -> {
            var w = (dev.jayms.World)get("world");
            fixtureY = Math.min(90, w.terrain().column(8,24).height()+2);
            for(int dx=5;dx<=11;dx++) for(int dz=19;dz<=27;dz++)
                { w.apply(new dev.jayms.net.Protocol.Edit(dx,fixtureY,dz,dev.jayms.net.Blocks.STONE));
                  for(int dy=1;dy<=4;dy++)w.apply(new dev.jayms.net.Protocol.Edit(dx,fixtureY+dy,dz,0)); }
            w.apply(new dev.jayms.net.Protocol.Edit(8,fixtureY+2,21,dev.jayms.net.Blocks.STONE));
            var p = new dev.jayms.player.Player(new org.joml.Vector3f(8.5f,fixtureY+1,24.5f),-90,0,(dev.jayms.Camera)get("camera"));
            p.toggleFlight(); set("player",p); warmFixture(); return null;
        });
        capture(id,out.resolve("voxel-sandbox-fixture.png"));
        x("key","F10");
        x("click","1");
        await(() -> local().drops.size()==1,"Break creates drop");
        onFrame(() -> { warmFixture(); return null; });
        capture(id,out.resolve("voxel-sandbox-break-drop.png"));
        x("keydown","w");
        try { await(() -> local().drops.isEmpty() && local().inventory.countType(dev.jayms.net.Blocks.STONE)==1,"W movement near drop picks up Stone"); }
        finally { x("keyup","w"); }
        onFrame(() -> {
            player().driveSeat(new org.joml.Vector3f(8.5f,fixtureY+1,24.5f),-90); player().toggleFlight();
            ((dev.jayms.World)get("world")).apply(new dev.jayms.net.Protocol.Edit(8,fixtureY+2,21,dev.jayms.net.Blocks.STONE));
            return null;
        });
        x("click","3");
        await(() -> local().inventory.countType(dev.jayms.net.Blocks.STONE)==0,"Placement consumes Stone");
        require(((dev.jayms.World)get("world")).sample(8,fixtureY+2,22)==dev.jayms.net.Blocks.STONE,"Placed block at aimed face");
        x("click","3");
        require(local().inventory.countType(dev.jayms.net.Blocks.STONE)==0,"Empty slot cannot create blocks");
        onFrame(() -> { warmFixture(); return null; });
        capture(id,out.resolve("voxel-sandbox-place.png"));
        x("key","F10");
        x("key","f"); await(() -> !player().flying(),"Flight off");
        x("key","f"); await(() -> player().flying(),"Flight on");
        float y=player().position().y;
        x("keydown","space");
        try { await(() -> player().position().y>y+.5f,"Flight rises via Space"); }
        finally { x("keyup","space"); }
        capture(id,out.resolve("voxel-sandbox-flight.png"));
        x("key","e"); await(() -> ((InventoryHud)get("inventoryHud")).open,"Inventory opens");
        onFrame(() -> {
            local().inventory.add(dev.jayms.net.Blocks.GRASS,1);
            var hud=(InventoryHud)get("inventoryHud");hud.openBook();hud.selectRecipe(0);
            return null;
        });
        // Setup jobs run before rendering. Wait for the book to draw before reading its hit boxes.
        renderedFrames();
        // The recipe book replaced the old fixed-coordinate craft panel. Read its rendered hit box.
        float[] craftButton=new float[2];
        onFrame(() -> {
            var hud=(InventoryHud)get("inventoryHud");
            Field hits=InventoryHud.class.getDeclaredField("hits");hits.setAccessible(true);
            boolean found=false;
            for(Object hit:(java.util.List<?>)hits.get(hud)) {
                var action=hit.getClass().getDeclaredMethod("action");action.setAccessible(true);
                if(!action.invoke(hit).equals("craft"))continue;
                String[] names={"x","y","w","h"};float[] bounds=new float[4];
                for(int i=0;i<4;i++){var method=hit.getClass().getDeclaredMethod(names[i]);method.setAccessible(true);bounds[i]=(float)method.invoke(hit);}
                craftButton[0]=bounds[0]+bounds[2]/2;craftButton[1]=bounds[1]+bounds[3]/2;found=true;break;
            }
            require(found,"Rendered recipe craft action available");return null;
        });
        x("mousemove","--window",id,""+(int)craftButton[0],""+(int)craftButton[1]); x("click","1");
        await(() -> local().inventory.countType(dev.jayms.net.Blocks.piece(dev.jayms.net.Blocks.GRASS,1))==8,"Craft cuts Grass into 8 fractional blocks");
        capture(id,out.resolve("voxel-sandbox-craft.png"));
        x("click","1");
        require(local().inventory.countType(dev.jayms.net.Blocks.piece(dev.jayms.net.Blocks.GRASS,1))==8,"Missing ingredients do not craft");
        x("key","e");
    }
    static final java.util.concurrent.atomic.AtomicLong frames = new java.util.concurrent.atomic.AtomicLong();
    static Object get(String name) throws Exception {
        Field f = Main.class.getDeclaredField(name); f.setAccessible(true); return f.get(game);
    }
    static void set(String name, Object value) throws Exception {
        Field f = Main.class.getDeclaredField(name); f.setAccessible(true); f.set(game, value);
    }
    static String x(String... args) throws Exception {
        var cmd = new ArrayList<String>(); cmd.add("xdotool"); cmd.addAll(List.of(args));
        var p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        String result = new String(p.getInputStream().readAllBytes()).trim();
        if (p.waitFor() != 0) throw new AssertionError("X input failed: " + args[0]);
        Thread.sleep(1000); return result;
    }
    static java.util.List<?> points(CityTools tools) throws Exception {
        Field f = CityTools.class.getDeclaredField("points"); f.setAccessible(true);
        return (java.util.List<?>)f.get(tools);
    }
    static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void await(java.util.concurrent.Callable<Boolean> condition, String label) throws Exception {
        for (int i=0;i<200;i++) { if (condition.call()) return; Thread.sleep(100); }
        throw new AssertionError(label);
    }
    static void click(String id, int index, int width, int height) throws Exception {
        x("mousemove", "--window", id, "" + (int)(CityActionBar.left(width) + (index + .5f)*CityActionBar.cell(width)),
                "" + (int)(CityActionBar.top(height) + 20)); x("click", "1");
    }
    static void renderedFrames() throws Exception {
        long next = frames.get() + 2;
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(90);
        while(frames.get()<next&&System.nanoTime()<deadline)Thread.sleep(100);
        require(frames.get()>=next,"Rendered capture frame: expected >="+next+", observed "+frames.get());
    }
    static void capture(String id, Path path) throws Exception {
        renderedFrames();
        var p = new ProcessBuilder("import", "-window", id, path.toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        require(p.waitFor() == 0, "Screenshot capture");
    }
    static void verifyPlayerHud(Path screenshot, boolean visible) throws Exception {
        var image = javax.imageio.ImageIO.read(screenshot.toFile());
        int left = image.getWidth()/2 - 252, top = image.getHeight()-72;
        int redPixels = 0;
        for (int y=top-26;y<top-13;y++) for (int x=left+90;x<left+427;x++) {
            int rgb=image.getRGB(x,y);
            if (Math.abs(((rgb>>>16)&255)-230)<=2 && Math.abs(((rgb>>>8)&255)-59)<=2
                    && Math.abs((rgb&255)-77)<=2) redPixels++;
        }
        require(visible ? redPixels>2000 : redPixels<200,
                "Player health HUD "+(visible ? "visible" : "hidden")+"; red pixels="+redPixels);
    }
    static void exerciseSandboxInventory(String id) throws Exception {
        var inventory = new dev.jayms.net.Inventory();
        inventory.add(dev.jayms.net.Blocks.STONE,7);
        ((dev.jayms.net.LocalGame)get("local")).inventory = inventory;
        int w=(int)get("framebufferWidth"), h=(int)get("framebufferHeight");
        int x=w/2-268+26, y=h/2-175;
        x("mousemove","--window",id,""+x,""+(y+210)); x("click","1");
        x("mousemove","--window",id,""+x,""+(y+20)); x("click","1");
        await(() -> inventory.count(0)==0 && inventory.type(9)==dev.jayms.net.Blocks.STONE
                && inventory.count(9)==7,"Sandbox inventory stack moved by real clicks");
    }
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]); Files.createDirectories(out);
        glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11);
        boolean sandbox = args.length > 1 && args[1].equals("sandbox");
        String resultName = sandbox ? "voxel-sandbox-smoke-result.txt" : "voxel-city-entry-result.txt";
        set("offlineSave", Path.of(sandbox ? "target/sandbox-profile/sandbox-world.dat"
                : "target/sandbox-profile/smoke-world.dat").toAbsolutePath());
        set("gameConfig", sandbox ? GameConfig.sandbox() : GameConfig.cityGame());
        var driver = new Thread(() -> {
            long handle = 0;
            try {
                String id = "";
                for (int i = 0; i < 180; i++) {
                    if (get("player") != null && (int)get("fps") >= 0) {
                        Thread.sleep(800); handle = ((dev.jayms.window.Window)get("window")).getHandle();
                        id = x("search", "--name", "^Voxel One").lines().findFirst().orElseThrow(); break;
                    }
                    Thread.sleep(500);
                }
                require(!id.isEmpty(), "Engine ready"); x("windowfocus", id);
                if (((ControlsMenu)get("menu")).open) x("key", "Escape");
                if (sandbox) firstPerson(id,out);
                if (!(boolean)get("isometric")) x("key", "F6");
                await(() -> (boolean)get("isometric"), "Sky view entered");
                if (sandbox) {
                    capture(id,out.resolve("voxel-sandbox-sky.png"));
                    verifyPlayerHud(out.resolve("voxel-sandbox-sky.png"),false);
                    x("key","e"); await(() -> ((InventoryHud)get("inventoryHud")).open,"Sandbox inventory opens");
                    exerciseSandboxInventory(id);
                    capture(id,out.resolve("voxel-sandbox-inventory.png"));
                    verifyPlayerHud(out.resolve("voxel-sandbox-inventory.png"),false);
                    x("key","e"); await(() -> !((InventoryHud)get("inventoryHud")).open,"Sandbox inventory closes");
                    x("key","F6"); await(() -> !(boolean)get("isometric"),"Sandbox walking restored");
                    capture(id,out.resolve("voxel-sandbox-walking.png"));
                    verifyPlayerHud(out.resolve("voxel-sandbox-walking.png"),true);
                    Files.writeString(out.resolve(resultName),"Playtest: PASS. Linux X11 software OpenGL, isolated synthetic profile. First launch first person, no citizens/buildings/mayor UI; real left-click break/drop, W movement toward drop and pickup, right-click placement consumes item, empty-slot rejection; F flight toggle and Space rise; E inventory and real recipe craft (1 Grass to 8 fractional pieces), missing-material rejection; F10 short recording. Synthetic near-surface block fixture; its column meshes are warmed on the render thread for capture. Sandbox sky view hides player HUD; inventory opens, moves 7 Stone from hotbar slot 0 to inventory slot 9 by real clicks, and closes while HUD stays hidden; walking restores player HUD. Real X11 input and screenshot pixel checks.\n");
                    return;
                }
                CityTools tools = (CityTools)get("cityTools");
                require(local().city.frame().config().city() && !local().city.frame().citizens().isEmpty(), "City bootstrap retained");
                capture(id,out.resolve("voxel-city-planning-entry.png"));
                verifyPlayerHud(out.resolve("voxel-city-planning-entry.png"),false);
                click(id,1,(int)get("framebufferWidth"),(int)get("framebufferHeight"));
                await(() -> tools.tool==4,"City road planning tool remains accessible");
                x("key","Escape"); await(() -> tools.tool==-1,"City tool cancel");
                x("key","F6"); await(() -> !(boolean)get("isometric"),"City first person still accessible");
                capture(id,out.resolve("voxel-city-walking-entry.png"));
                verifyPlayerHud(out.resolve("voxel-city-walking-entry.png"),true);
                Files.writeString(out.resolve(resultName),"Playtest: PASS. Linux X11 software OpenGL; isolated City profile. City opens in planning view with citizens; player health HUD hidden; real click selects road tool, Escape cancels, F6 returns to first person with player health visible. Separate City save from Sandbox.\n");
            } catch(Throwable e) { failure=e;
                try { Files.writeString(out.resolve(resultName),"FAIL: "+e.getClass().getSimpleName()+": "+e.getMessage()+"\n"); } catch(Exception ignored) {}
            } finally { if(handle!=0) glfwSetWindowShouldClose(handle,true); }
        });
        driver.setDaemon(true); driver.start(); game.run(new Main.FrameObserver() {
            public void beforeFrame(Main app) { for(var job=jobs.poll();job!=null;job=jobs.poll())job.run(); }
            public void afterFrame(Main app) { frames.incrementAndGet(); }
        }); driver.join(1000);
        if(failure!=null) throw new AssertionError("HUD playtest failed");
        require(Files.exists(out.resolve(resultName)),"Driver completed");
    }
}
