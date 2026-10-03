import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.*;

import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

import javax.imageio.ImageIO;

/** Actual engine Overlay rendered with Mesa EGL; no desktop or private world data required. */
public final class CapitalRenderingSmoke {
    static BufferedImage capture(int w, int h) {
        ByteBuffer data = MemoryUtil.memAlloc(w * h * 4);
        try {
            glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, data);
            var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int i = ((h - y - 1) * w + x) * 4;
                    image.setRGB(
                            x,
                            y,
                            (data.get(i) & 255) << 16
                                    | (data.get(i + 1) & 255) << 8
                                    | (data.get(i + 2) & 255));
                }
            return image;
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_NULL);
        if (!glfwInit()) throw new IllegalStateException("Cannot initialize GLFW null platform");
        glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_EGL_CONTEXT_API);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(1280, 800, "Company exchange visual check", 0, 0);
        if (window == 0) throw new IllegalStateException("Cannot create EGL context");
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        var terrain = new Terrain(Terrain.DEFAULT_SEED);
        var voxels = new WorldVoxels(terrain);
        var ground =
                new CitySimulation.Ground() {
                    public int type(int x, int y, int z) {
                        return voxels.type(x, y, z);
                    }

                    public boolean occupied(int x, int y, int z, int w, int d) {
                        return false;
                    }

                    public void apply(List<Protocol.Edit> edits) {
                        for (var e : edits) voxels.apply(e);
                    }
                };
        var city =
                new CitySimulation(
                        new GameConfig(true, false, 1200, 10),
                        ground,
                        terrain,
                        null,
                        ProductionCatalog.toolEra());
        var pose = new Protocol.Pose(1, 8, 40, 24, 0, 0);
        String built =
                city.command(
                        new CityCommand(
                                CityCommand.EXCHANGE, 0, List.of(new Polygon.Point(30, 27))),
                        1,
                        pose);
        if (!built.contains("built")) throw new AssertionError(built);
        for (int i = 0; i < 120; i++) city.advance(1);
        if (!city.economy.capital.exchange.operational())
            throw new AssertionError("Natural staffing did not open exchange");
        var book = city.economy.capital.exchange;
        int company = city.economy.companies().get(0).id;
        var founder = book.listing(company).founder();
        var investor = book.listing(city.economy.companies().get(1).id).founder();
        book.goPublic(company, founder, 200, 500);
        book.submit(company, investor, true, 40, 600);
        book.submit(company, founder, false, 100, 650);
        book.submit(company, investor, true, 20, 450);
        var ui = new MayorDashboard();
        ui.open = true;
        ui.tab = 5;
        try (var overlay = new Overlay()) {
            glViewport(0, 0, 1280, 800);
            glClearColor(.025f, .04f, .06f, 1);
            glClear(GL_COLOR_BUFFER_BIT);
            overlay.begin(1280, 800);
            ui.render(overlay, 1280, 800, city.frame(), "F8", true);
            overlay.end();
            ImageIO.write(
                    capture(1280, 800), "png", out.resolve("company-exchange-book.png").toFile());
            ui.capital.view = 1;
            glClear(GL_COLOR_BUFFER_BIT);
            overlay.begin(1280, 800);
            ui.render(overlay, 1280, 800, city.frame(), "F8", true);
            overlay.end();
            ImageIO.write(
                    capture(1280, 800), "png", out.resolve("company-exchange-owners.png").toFile());
            if (glGetError() != GL_NO_ERROR) throw new AssertionError("OpenGL error");
            Path clips = Path.of("target/capital-clips");
            try (var recorder = new dev.jayms.recording.ScreenRecorder(clips)) {
                org.lwjgl.glfw.GLFWKeyCallbackI f10 =
                        (handle, key, scan, action, mods) -> {
                            if (key == GLFW_KEY_F10 && action == GLFW_PRESS)
                                recorder.toggle(1280, 800);
                        };
                glfwSetKeyCallback(window, f10);
                f10.invoke(window, GLFW_KEY_F10, 0, GLFW_PRESS, 0);
                ui.capital.view = 0;
                ui.capital.actorIndex = city.economy.capital.state().investors().size() + 2;
                ui.capital.quantity = "160";
                ui.capital.price = "5.00";
                for (int frame = 0; frame < 180; frame++) {
                    if (frame == 30 || frame == 110)
                        ui.capital.click(
                                450, 355, 1280, 800, city.frame(), c -> city.command(c, 1, pose));
                    if (frame == 60 || frame == 140)
                        ui.capital.click(
                                100, 400, 1280, 800, city.frame(), c -> city.command(c, 1, pose));
                    if (frame == 90) {
                        ui.capital.quantity = "20";
                        ui.capital.price = "7.00";
                    }
                    if (frame == 160) ui.capital.view = 2;
                    glClear(GL_COLOR_BUFFER_BIT);
                    overlay.begin(1280, 800);
                    ui.render(overlay, 1280, 800, city.frame(), "F8", true);
                    overlay.end();
                    recorder.capture(1280, 800);
                    glfwPollEvents();
                    Thread.sleep(33);
                }
                f10.invoke(window, GLFW_KEY_F10, 0, GLFW_PRESS, 0);
            }
            try (var files = Files.list(clips)) {
                var clip =
                        files.filter(p -> p.toString().endsWith(".mp4"))
                                .max(Comparator.comparing(Path::toString))
                                .orElseThrow();
                if (Files.size(clip) > 6_000_000)
                    throw new AssertionError("Clip exceeds artifact limit");
                Files.copy(
                        clip,
                        out.resolve("company-exchange-trading.mp4"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            if (book.listing(company).lastPrice() != 650)
                throw new AssertionError(
                        "Scripted trades did not move price to resting sell limit");
            Files.writeString(
                    out.resolve("company-exchange-render.json"),
                    "{\"status\":\"passed\",\"scope\":\"Actual engine UI at 1280x800 with Mesa EGL"
                        + " null platform; fresh deterministic city, naturally commuting graduate"
                        + " staff, staged offering and investor orders\",\"renderer\":\""
                            + glGetString(GL_RENDERER)
                            + "\"}\n");
            System.out.println("Rendered exchange book and ownership screens successfully.");
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }
}
