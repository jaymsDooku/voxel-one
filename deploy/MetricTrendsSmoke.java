package dev.jayms;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

import dev.jayms.net.city.*;
import dev.jayms.ui.*;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import javax.imageio.ImageIO;

/** Synthetic public metric fixture; captures the actual OpenGL dashboard without account data. */
public final class MetricTrendsSmoke {
    public static void main(String[] args) throws Exception {
        if (!glfwInit()) throw new AssertionError("GLFW unavailable");
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(1280, 720, "Metric chart checks", 0, 0);
        if (window == 0) throw new AssertionError("Window unavailable");
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        var dashboard = new MayorDashboard();
        var f = CityMetricsTest.fixture();
        CityFrame current = f;
        for (int i = 0; i < 80; i++) {
            var e = f.economy();
            var state =
                    new CityEconomy.State(
                            e.budget() - i * 12,
                            i * 12,
                            e.landRevenue(),
                            e.rentClock(),
                            e.firms(),
                            e.plots(),
                            e.properties(),
                            e.contracts(),
                            e.businesses(),
                            e.resources());
            current =
                    new CityFrame(
                            f.config(),
                            i * 5,
                            f.roads(),
                            f.zones(),
                            f.buildings(),
                            f.citizens(),
                            f.horses(),
                            state,
                            f.addresses(),
                            f.agriculture());
            dashboard.history.observe(current);
        }
        dashboard.trends.open = true;
        try (var overlay = new Overlay()) {
            for (int domain = 0; domain < 4; domain++) {
                dashboard.trends.click(24 + (1280 - 48) / 4f * domain + 5, 150, 1280);
                overlay.begin(1280, 720);
                dashboard.render(overlay, 1280, 720, current, "F9", true);
                overlay.end();
                glFinish();
                var pixels = BufferUtils.createByteBuffer(1280 * 720 * 4);
                glReadPixels(0, 0, 1280, 720, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                var image = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < 720; y++)
                    for (int x = 0; x < 1280; x++) {
                        int p = ((719 - y) * 1280 + x) * 4;
                        image.setRGB(
                                x,
                                y,
                                ((pixels.get(p) & 255) << 16)
                                        | ((pixels.get(p + 1) & 255) << 8)
                                        | (pixels.get(p + 2) & 255));
                    }
                ImageIO.write(
                        image,
                        "png",
                        Path.of(args[0], "metric-trends-" + domain + ".png").toFile());
                if (glGetError() != GL_NO_ERROR) throw new AssertionError("OpenGL error");
            }
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
        System.out.println(
                "PASS: four dashboard domains rendered using synthetic snapshots at 1280x720");
    }
}
