import dev.jayms.Main;
import dev.jayms.net.city.GameConfig;
import dev.jayms.window.Window;
import dev.jayms.render.GraphicsProfile;
import dev.jayms.ui.Controls;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Current production desktop reference; diagnostic software rendering, no qualification claim. */
public final class BattleBaselineSmoke {
    static Main game = new Main();
    static void set(String name, Object value) throws Exception { Field f = Main.class.getDeclaredField(name); f.setAccessible(true); f.set(game, value); }
    static Object get(String name) throws Exception { Field f = Main.class.getDeclaredField(name); f.setAccessible(true); return f.get(game); }
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]); Files.createDirectories(out); boolean city = args[1].equals("city");
        // A bounded LOW reference keeps software rendering inside VPS resource caps.
        // This writes only the harness's synthetic user.home, never a real profile.
        GraphicsProfile.preset(GraphicsProfile.Preset.LOW)
                .with(GraphicsProfile.Key.RESOLUTION, "960x640@60")
                .with(GraphicsProfile.Key.DETAIL, "16")
                .with(GraphicsProfile.Key.HORIZON, "512")
                .with(GraphicsProfile.Key.SHADOWS, "OFF")
                .save(Controls.directory().resolve("graphics.properties"));
        set("offlineSave", out.resolve("synthetic-" + args[1] + ".dat"));
        set("gameConfig", new GameConfig(city, false, 1200, 10));
        set("framebufferWidth", 960); set("framebufferHeight", 640);
        List<Double> frames = new ArrayList<>(); long[] clock = {0, 0}; int[] count = {0}; String[] renderer = {""};
        long loadingStarted = System.nanoTime();
        game.run(new Main.FrameObserver() {
            public void loading(Main main, String stage) {
                if (System.nanoTime() - loadingStarted > 45_000_000_000L)
                    throw new IllegalStateException("Desktop reference loading exceeded 45 seconds; qualification pending");
            }
            public void started(Main main) { renderer[0] = glGetString(GL_RENDERER); clock[1] = System.nanoTime(); }
            public void afterFrame(Main main) throws Exception {
                long now = System.nanoTime(); count[0]++;
                // The VPS default renderer can take seconds per frame. Keep this
                // diagnostic bounded and report the small sample count explicitly.
                if (clock[0] != 0 && count[0] > 2) frames.add((now - clock[0]) / 1e6);
                clock[0] = now;
                if (count[0] >= 17 || now - clock[1] > 80_000_000_000L)
                    glfwSetWindowShouldClose(((Window)get("window")).getHandle(), true);
            }
        });
        if (frames.size() < 3) throw new AssertionError("Insufficient desktop baseline frames");
        Collections.sort(frames);
        Files.writeString(out.resolve(args[1] + "-baseline.json"), "{\"game\":\"" + args[1] + "\",\"renderer\":\"" + renderer[0]
                + "\",\"settings\":\"Production LOW reference; detail 16, horizon 512, shadows OFF; synthetic offline home, 960x640, fixed daytime, includes terrain streaming\",\"samples\":" + frames.size()
                + ",\"frameMedianMs\":" + frames.get(frames.size()/2) + ",\"frameP95Ms\":" + frames.get((int)(frames.size()*.95))
                + ",\"qualification\":\"Diagnostic VPS software rendering only; physical GPU pending\"}\n");
    }
}
