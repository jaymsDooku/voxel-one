package dev.jayms;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

import dev.jayms.net.city.*;
import dev.jayms.ui.*;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

import javax.imageio.ImageIO;

/** Real OpenGL dashboard captures from changing synthetic metrics, without private account data. */
public final class MetricTrendsSmoke {
    private static final String[] DOMAINS = {"Population", "Companies", "Government", "Workplaces"};

    private static CityFrame snapshot(int step) {
        var base = CityMetricsTest.fixture();
        var e = base.economy();
        var firms =
                e.firms().stream()
                        .map(
                                f ->
                                        new CityEconomy.Firm(
                                                f.id(),
                                                f.name(),
                                                f.kind(),
                                                f.cash() + step * 9,
                                                f.land(),
                                                f.materials(),
                                                f.wages() + step * 2,
                                                f.receipts() + step * 11))
                        .toList();
        var totals =
                new CityBusinesses.Totals(
                        step * 15, step * 2, step, step * .5, step * .5, step * 3, step * 2, step,
                        0, 0);
        var accounts =
                List.of(
                        new CityBusinesses.Record(
                                2, 19, 1, 0, totals, new CityBusinesses.Day(1, totals), List.of()));
        var state =
                new CityEconomy.State(
                        e.budget() - step * 12,
                        step * 12,
                        step * 4,
                        e.rentClock(),
                        firms,
                        e.plots(),
                        e.properties(),
                        e.contracts(),
                        accounts,
                        e.resources());
        var citizens = new ArrayList<>(base.citizens());
        for (int n = 0; n < step / 10; n++)
            citizens.add(
                    CityMetricsTest.citizen(
                            100 + n,
                            "Synthetic citizen " + n,
                            n % 3,
                            70,
                            20 + step,
                            1,
                            2,
                            "Working in mine"));
        return new CityFrame(
                base.config(),
                step * 5,
                base.roads(),
                base.zones(),
                base.buildings(),
                citizens,
                base.horses(),
                state,
                base.addresses(),
                base.agriculture());
    }

    public static void main(String[] args) throws Exception {
        boolean offscreen = args.length > 1 && args[1].equals("offscreen");
        long window = 0;
        if (!offscreen) {
            if (!glfwInit()) throw new AssertionError("GLFW unavailable");
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            window = glfwCreateWindow(1280, 720, "Metric chart checks", 0, 0);
            if (window == 0) throw new AssertionError("Window unavailable");
            glfwMakeContextCurrent(window);
        }
        GL.createCapabilities();
        var dashboard = new MayorDashboard();
        CityFrame current = null;
        for (int step = 0; step < 80; step++) {
            current = snapshot(step);
            dashboard.history.observe(current);
        }
        dashboard.trends.open = true;
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        var results = new ArrayList<String>();
        try (var overlay = new Overlay()) {
            for (int[] size : new int[][] {{1280, 720}, {640, 640}}) {
                int w = size[0], h = size[1];
                for (int domain = 0; domain < DOMAINS.length; domain++) {
                    dashboard.trends.click(24 + (w - 48) / 4f * domain + 5, 150, w);
                    var samples = dashboard.history.samples();
                    String prefix = DOMAINS[domain] + " / ";
                    String key =
                            samples.get(0).values().keySet().stream()
                                    .filter(k -> k.startsWith(prefix))
                                    .findFirst()
                                    .orElseThrow();
                    double initial = samples.get(0).values().get(key);
                    double latest = samples.get(samples.size() - 1).values().get(key);
                    if (initial == latest)
                        throw new AssertionError("Fixture metric is static: " + key);
                    glViewport(0, 0, w, h);
                    glClear(GL_COLOR_BUFFER_BIT);
                    overlay.begin(w, h);
                    dashboard.render(overlay, w, h, current, "F9", true);
                    overlay.end();
                    glFinish();
                    var pixels = BufferUtils.createByteBuffer(w * h * 4);
                    glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                    var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                    var chartRows = new HashSet<Integer>();
                    int chartPixels = 0;
                    for (int y = 0; y < h; y++)
                        for (int x = 0; x < w; x++) {
                            int offset = ((h - 1 - y) * w + x) * 4;
                            int r = pixels.get(offset) & 255,
                                    g = pixels.get(offset + 1) & 255,
                                    b = pixels.get(offset + 2) & 255;
                            image.setRGB(x, y, (r << 16) | (g << 8) | b);
                            if (x >= 108 && x <= w - 34 && y >= 308 && y <= h - 108 && r >= 70
                                    && r <= 85 && g >= 210 && g <= 225 && b >= 170 && b <= 190) {
                                chartPixels++;
                                chartRows.add(y);
                            }
                        }
                    if (chartPixels < 300 || chartRows.size() < 100)
                        throw new AssertionError(
                                "Changing line not visible: " + prefix + w + "x" + h);
                    String file =
                            "metric-trends-"
                                    + DOMAINS[domain].toLowerCase(Locale.ROOT)
                                    + "-"
                                    + w
                                    + "x"
                                    + h
                                    + ".png";
                    ImageIO.write(image, "png", out.resolve(file).toFile());
                    results.add(
                            String.format(
                                    Locale.ROOT,
                                    "{\"file\":\"%s\",\"width\":%d,\"height\":%d,\"metric\":\"%s\",\"initial\":%.2f,\"latest\":%.2f,\"samples\":%d,\"chartPixels\":%d,\"chartRows\":%d}",
                                    file,
                                    w,
                                    h,
                                    key,
                                    initial,
                                    latest,
                                    samples.size(),
                                    chartPixels,
                                    chartRows.size()));
                    if (glGetError() != GL_NO_ERROR) throw new AssertionError("OpenGL error");
                }
            }
            String renderer = glGetString(GL_RENDERER), version = glGetString(GL_VERSION);
            Files.writeString(
                    out.resolve("metric-trends-rendering.json"),
                    "{\"result\":\"passed\",\"fixture\":\"Synthetic changing snapshots, not live"
                        + " gameplay\",\"platform\":\"Linux Mesa "
                            + (offscreen ? "surfaceless EGL pbuffer" : "GLFW desktop")
                            + "\","
                            + "\"renderer\":\""
                            + renderer
                            + "\",\"openGL\":\""
                            + version
                            + "\","
                            + "\"captures\":["
                            + String.join(",", results)
                            + "]}\n");
        } finally {
            if (!offscreen) {
                glfwDestroyWindow(window);
                glfwTerminate();
            }
        }
        System.out.println(
                "PASS: eight changing-metric OpenGL captures, four domains at 1280x720 and"
                    + " 640x640");
    }
}
