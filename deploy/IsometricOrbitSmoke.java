import dev.jayms.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;

/** Real X11 input through xdotool, production GLFW callbacks and F10 recorder. */
public class IsometricOrbitSmoke {
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
        Thread.sleep(350); return result;
    }
    static float yaw() throws Exception { return ((IsometricCamera)get("overview")).camera().yaw(); }
    static boolean dragging() throws Exception {
        var d = get("orbitDrag"); var m = d.getClass().getDeclaredMethod("active");
        m.setAccessible(true); return (boolean)m.invoke(d);
    }
    static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void move() throws Exception { x("mousemove_relative", "--", "400", "0"); }
    static void begin() throws Exception { x("mousedown", "3"); move(); }
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]); Files.createDirectories(out);
        glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11);
        set("offlineSave", out.resolve("smoke-world.dat"));
        var driver = new Thread(() -> {
            long handle = 0;
            try {
                String id = "";
                for (int i = 0; i < 180; i++) {
                    if (get("player") != null && (int)get("fps") >= 0) {
                        Thread.sleep(500);
                        handle = ((dev.jayms.window.Window)get("window")).getHandle();
                        id = x("search", "--name", "^Voxel One").lines().findFirst().orElseThrow(); break;
                    }
                    Thread.sleep(500);
                }
                require(!id.isEmpty(), "Engine ready");
                handle = ((dev.jayms.window.Window)get("window")).getHandle();
                x("windowfocus", id);
                if (((dev.jayms.ui.ControlsMenu)get("menu")).open) x("key", "Escape");
                x("key", "F6");
                require((boolean)get("isometric"), "Isometric mode entered");
                x("key", "F10"); begin(); require(dragging(), "RMB begins orbit");
                double travelled = 0;
                for (int i = 0; i < 10; i++) {
                    float before = yaw(); move(); float after = yaw();
                    double delta = ((after - before + 540) % 360) - 180;
                    require(delta > 1 && delta < 100, "Continuous fractional orbit delta"); travelled += delta;
                }
                require(travelled >= 359, "Full 360 orbit");
                x("mouseup", "3"); require(!dragging(), "Release cancels orbit");
                float before = yaw(); move(); require(yaw() == before, "Released motion ignored");
                begin(); x("key", "e"); require(!dragging(), "Inventory cancels drag");
                before = yaw(); move(); require(yaw() == before, "Inventory motion ignored");
                x("mouseup", "3"); x("key", "e");
                begin(); x("key", "F6"); require(!(boolean)get("isometric") && !dragging(), "View transition cancels");
                before = yaw(); move(); require(yaw() == before, "First person does not orbit");
                x("mouseup", "3"); x("key", "F6");
                begin(); x("windowfocus", "0"); Thread.sleep(400);
                require(!dragging(), "Focus loss cancels drag");
                x("windowfocus", id); x("mouseup", "3");
                before = yaw(); move(); require(yaw() == before, "Focus restore does not resume drag");
                x("key", "Escape"); begin(); require(dragging(), "New drag after menu closure");
                move(); x("mouseup", "3"); x("key", "F10"); Thread.sleep(3000);
                Files.writeString(out.resolve("results.json"), "{\"platform\":\"Linux Xvfb Mesa\",\"status\":\"passed\",\"degreesTravelled\":" + travelled + ",\"checks\":[\"real GLFW RMB orbit past 360 degrees\",\"release\",\"inventory modal\",\"view transitions\",\"focus loss and restore\",\"fresh drag\"],\"recording\":\"engine F10 recorder\"}\n");
            } catch (Throwable e) { failure = e; }
            finally { if (handle != 0) glfwSetWindowShouldClose(handle, true); }
        });
        driver.setDaemon(true); driver.start(); game.run(); driver.join(1000);
        if (failure != null) throw new AssertionError("Graphical input validation failed", failure);
        require(Files.exists(out.resolve("results.json")), "Driver completed");
    }
}
