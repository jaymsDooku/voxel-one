import dev.jayms.*;
import dev.jayms.net.city.GameConfig;
import dev.jayms.ui.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;

/** Synthetic offline game; real X11 clicks through production GLFW callbacks. */
public class HudActionBarSmoke {
    static Main game = new Main();
    static volatile Throwable failure;
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
    static void capture(String id, Path path) throws Exception {
        long next = frames.get() + 2;
        await(() -> frames.get() >= next, "Rendered capture frame");
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
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]); Files.createDirectories(out);
        glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11);
        boolean sandbox = args.length > 1 && args[1].equals("sandbox");
        String resultName = sandbox ? "hud-sandbox-smoke-result.txt" : "hud-smoke-result.txt";
        set("offlineSave", Path.of(sandbox ? "target/hud-profile/sandbox-world.dat"
                : "target/hud-profile/smoke-world.dat").toAbsolutePath());
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
                if (!(boolean)get("isometric")) x("key", "F6");
                await(() -> (boolean)get("isometric"), "Sky view entered");
                if (sandbox) {
                    capture(id,out.resolve("hud-sandbox-sky.png"));
                    verifyPlayerHud(out.resolve("hud-sandbox-sky.png"),false);
                    x("key","e"); await(() -> ((InventoryHud)get("inventoryHud")).open,"Sandbox inventory opens");
                    capture(id,out.resolve("hud-sandbox-inventory.png"));
                    verifyPlayerHud(out.resolve("hud-sandbox-inventory.png"),false);
                    x("key","e"); await(() -> !((InventoryHud)get("inventoryHud")).open,"Sandbox inventory closes");
                    x("key","F6"); await(() -> !(boolean)get("isometric"),"Sandbox walking restored");
                    capture(id,out.resolve("hud-sandbox-walking.png"));
                    verifyPlayerHud(out.resolve("hud-sandbox-walking.png"),true);
                    Files.writeString(out.resolve(resultName),"Playtest: PASS. Synthetic sandbox sky view hides player HUD; inventory opens and closes while HUD stays hidden; walking restores player HUD. Real X11 input and screenshot pixel checks.\n");
                    return;
                }
                CityTools tools = (CityTools)get("cityTools");
                int[] expected = {-1,4,0,1,2,3,5,6,7};
                for (int i=0;i<9;i++) {
                    click(id,i,1280,720); final int wanted=expected[i];
                    await(() -> tools.tool==wanted, "Tool input timeout " + i); require(tools.tool==expected[i], "Icon selects tool " + i + "; observed="+tools.tool+"; framebuffer="+get("framebufferWidth")+"x"+get("framebufferHeight"));
                }
                click(id,1,1280,720);
                x("mousemove","--window",id,"80","155"); x("click","1");
                await(() -> tools.tool==4 && !tools.roadMenu,"Road submenu input");
                require(tools.tool==4 && tools.roadType==0,"Road submenu selection");
                capture(id,out.resolve("hud-compact-roads.png"));
                x("key","Escape"); await(() -> tools.tool==-1,"Road cancel input"); require(tools.tool==-1,"Escape cancels road tool");
                click(id,2,1280,720);
                await(() -> tools.tool==0,"Zone tool input");
                x("mousemove","--window",id,"400","350"); x("click","1");
                await(() -> points(tools).size()==1,"World zone input");
                require(points(tools).size()==1,"World click starts zone polygon");
                x("key","Escape"); await(() -> points(tools).isEmpty(),"Zone cancel input"); require(points(tools).isEmpty(),"Escape clears zone polygon");
                x("mousemove","--window",id,"80",""+(int)(CityActionBar.top(720)+20)); x("click","1");
                require(tools.tool==-1,"Outside centered dock cannot select tool");
                x("mousemove","--window",id,""+(int)(CityActionBar.left(1280)+3.5f*44),"682");
                capture(id,out.resolve("hud-compact-overview.png"));
                verifyPlayerHud(out.resolve("hud-compact-overview.png"),false);
                x("windowsize",id,"640","640");
                await(() -> (int)get("framebufferWidth")==640 && (int)get("framebufferHeight")==640,"Resize input");
                require((int)get("framebufferWidth")==640,"Resize callback applied");
                click(id,8,640,640); await(() -> tools.tool==7,"Narrow dock input"); require(tools.tool==7,"Narrow viewport last icon reachable");
                capture(id,out.resolve("hud-compact-narrow.png"));
                x("key","e"); await(() -> ((InventoryHud)get("inventoryHud")).open,"Inventory input"); require(((InventoryHud)get("inventoryHud")).open,"Inventory opens in overview");
                capture(id,out.resolve("hud-inventory-regression.png"));
                verifyPlayerHud(out.resolve("hud-inventory-regression.png"),false);
                x("key","e"); await(() -> !((InventoryHud)get("inventoryHud")).open,"Inventory close input"); x("key","F6"); await(() -> !(boolean)get("isometric"),"Walk input"); require(!(boolean)get("isometric"),"Walking mode restored");
                capture(id,out.resolve("hud-walking-regression.png"));
                verifyPlayerHud(out.resolve("hud-walking-regression.png"),true);
                Files.writeString(out.resolve(resultName),"PASS: nine real icon clicks; road submenu; zone world click/cancel; outside dock; 640x640 resize; inventory; walking mode.\n");
            } catch(Throwable e) { failure=e;
                try { Files.writeString(out.resolve(resultName),"FAIL: "+e.getClass().getSimpleName()+": "+e.getMessage()+"\n"); } catch(Exception ignored) {}
            } finally { if(handle!=0) glfwSetWindowShouldClose(handle,true); }
        });
        driver.setDaemon(true); driver.start(); game.run(new Main.FrameObserver() {
            public void afterFrame(Main app) { frames.incrementAndGet(); }
        }); driver.join(1000);
        if(failure!=null) throw new AssertionError("HUD playtest failed");
        require(Files.exists(out.resolve(resultName)),"Driver completed");
    }
}
