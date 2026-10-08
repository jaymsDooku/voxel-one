import dev.jayms.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;

/** Real X11 input through xdotool and production GLFW callbacks. */
public class IsometricHudSmoke {
    static Main game = new Main();
    static volatile Throwable failure;
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
        Thread.sleep(10000); return result;
    }
    static void await(java.util.concurrent.Callable<Boolean> condition, String message) throws Exception {
        for (int i = 0; i < 120; i++) {
            if (condition.call()) return;
            Thread.sleep(500);
        }
        throw new AssertionError(message);
    }
    static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void capture(Path out, String id, String name) throws Exception {
        Thread.sleep(10000);
        var p = new ProcessBuilder("import", "-window", id, out.resolve(name).toString()).start();
        require(p.waitFor() == 0, "Capture succeeds");
    }
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]); Files.createDirectories(out);
        glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11);
        set("offlineSave", Path.of("target/hud-smoke/world/synthetic.dat"));
        set("gameConfig", new dev.jayms.net.city.GameConfig(true, false, 1200, 10));
        var driver = new Thread(() -> {
            long handle = 0;
            try {
                String id = "";
                for (int i = 0; i < 180; i++) {
                    if (get("player") != null && (int)get("fps") >= 0) {
                        Thread.sleep(500);
                        handle = ((dev.jayms.window.Window)get("window")).getHandle();
                        id = x("search", "--onlyvisible", "--pid", Long.toString(ProcessHandle.current().pid()), "--name", "^Voxel One").lines().findFirst().orElseThrow(); break;
                    }
                    Thread.sleep(500);
                }
                require(!id.isEmpty(), "Engine ready");
                handle = ((dev.jayms.window.Window)get("window")).getHandle();
                glfwSetWindowSize(handle, 800, 600);
                x("windowfocus", id);
                if (((dev.jayms.ui.ControlsMenu)get("menu")).open) x("key", "Escape");
                require((boolean)get("isometric"), "City starts in isometric mode");
                capture(out, id, "isometric-hud-hidden.png");
                x("key", "e");
                await(() -> ((dev.jayms.ui.InventoryHud)get("inventoryHud")).open, "Inventory opens in sky view");
                capture(out, id, "isometric-inventory.png");
                x("key", "Escape");
                await(() -> !((dev.jayms.ui.InventoryHud)get("inventoryHud")).open, "Inventory closes");
                x("key", "F6");
                await(() -> !(boolean)get("isometric"), "Return to player view");
                capture(out, id, "player-hud-restored.png");
                Files.writeString(out.resolve("isometric-hud-results.txt"), "Playtest: PASS. Linux assigned X11 display, Mesa, production Main, isolated synthetic City Builder profile. Actual xdotool GLFW input: city starts in sky view; E opens inventory; Escape closes it; F6 restores player view. Screenshots capture each state for visual validation.\n");
            } catch (Throwable e) { failure = e; try { Files.writeString(out.resolve("isometric-hud-failure.txt"), e.getClass().getSimpleName()+": "+e.getMessage()); } catch(Exception ignored) {} }
            finally { if (handle != 0) glfwSetWindowShouldClose(handle, true); }
        });
        driver.setDaemon(true); driver.start(); game.run(); driver.join(1000);
        if (failure != null) throw new AssertionError("Graphical input validation failed", failure);
        require(Files.exists(out.resolve("isometric-hud-results.txt")), "Driver completed");
    }
}
