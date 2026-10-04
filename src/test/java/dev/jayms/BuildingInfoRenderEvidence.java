package dev.jayms;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

import dev.jayms.net.city.*;
import dev.jayms.ui.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

/** Opt-in capture harness: actual production renderer and handlers, synthetic data only. */
public final class BuildingInfoRenderEvidence {
    private static CityFrame fixture() {
        var citizens = new ArrayList<CityFrame.Citizen>();
        for (int i = 1; i <= 30; i++) {
            String name = i == 1 ? "LongUnbrokenResidentName".repeat(8) : "Resident " + i;
            citizens.add(new CityFrame.Citizen(i, name, 0, 0, 24, 0, 0, 0, 0, 200,
                    1, i < 15 ? 2 : 3, 0,
                    "Walking home after working a shift and collecting supplies for tomorrow"));
        }
        var catalogBase = ProductionCatalog.toolEra();
        var products = new ArrayList<>(catalogBase.products());
        for (int i = 0; i < 16; i++) products.add(new ProductionCatalog.Product(1200 + i,
                "Long inventory product description number " + i, 2));
        products.add(new ProductionCatalog.Product(CityMaterials.WHEAT, "Wheat", 1));
        var catalog = new ProductionCatalog(products, catalogBase.recipes(), catalogBase.equipment());
        var stocks = new ArrayList<CityMaterials.Stock>();
        for (int owner : new int[]{20, 21, 22}) {
            for (var p : products) stocks.add(new CityMaterials.Stock(0, owner, p.id(), 123 * CityMaterials.UNIT));
        }
        var projects = List.of(
                new CityMaterials.Project(4, 0, false, false, CityMaterials.requirements(0)),
                new CityMaterials.Project(5, 0, true, false, CityMaterials.requirements(0)),
                new CityMaterials.Project(6, 0, true, true, CityMaterials.requirements(0)));
        var resources = new CityMaterials.State(stocks,
                List.of(new CityMaterials.Production(21, .5, 125, 40,
                        "Processing privately owned inputs; waiting for the next delivery. ".repeat(5))),
                projects, catalog);
        var economy = new CityEconomy.State(1000, 0, 0, 0,
                List.of(new CityEconomy.Firm(20, "Builders and Property Management", 0, 400, 0, 0, 0, 0),
                        new CityEconomy.Firm(21, "A Very Long Workshop Company Name With Multiple Words", CityMaterials.TOOLS, 3000, 0, 0, 0, 0),
                        new CityEconomy.Firm(22, "Family Farm", CityMaterials.FARM, 1000, 0, 0, 0, 0)),
                List.of(new CityEconomy.Plot(4, 4, 0, 20, 24, 20, 20, 50, 100, 0, 0),
                        new CityEconomy.Plot(5, 5, 0, 30, 24, 20, 20, 50, 100, 4, 0),
                        new CityEconomy.Plot(6, 6, 0, 40, 24, 20, 20, 50, 100, 8, 6)),
                List.of(new CityEconomy.Property(1, 0, 20, 0, 800, 2),
                        new CityEconomy.Property(2, 0, 20, 21, 1200, 4),
                        new CityEconomy.Property(3, 0, 22, 22, 1500, 0),
                        new CityEconomy.Property(6, 0, 20, 0, 900, 2)),
                List.of(), List.of(), resources);
        var buildings = List.of(new CityFrame.Building(1, 1, 0, 0, 24, 0, 40, 0),
                new CityFrame.Building(2, 2, 2, 10, 24, 0, 20, 0),
                new CityFrame.Building(3, 3, 3, 20, 24, 0, 20, 0),
                new CityFrame.Building(6, 6, 0, 40, 24, 20, 4, 0),
                new CityFrame.Building(7, 0, SpecialBuildings.type(0, 3), 50, 24, 20, 0, 0),
                new CityFrame.Building(8, -1, SpecialBuildings.type(1, 2), 60, 24, 20, 0, 1),
                new CityFrame.Building(9, -2, SpecialBuildings.type(4, 3), 70, 24, 20, 0, 21));
        var fields = new ArrayList<Agriculture.Field>();
        for (int i = 0; i < 18; i++) fields.add(new Agriculture.Field(3, 22, CityMaterials.WHEAT,
                20 + i, 24, 1, .72f, i));
        var agriculture = new Agriculture.State(true, false,
                List.of(new Agriculture.Family(22, citizens.subList(15, 30).stream().map(CityFrame.Citizen::id).toList())),
                fields, List.of(new Agriculture.Cow(100, 3, 22, true, 20, 24, 0, 0, 0, 12, 5)),
                List.of(new Agriculture.Farm(3, 22, 0, 0, 2, 1)));
        var addresses = new CityAddresses.State(
                List.of(new CityAddresses.Street(1, "A long street name that wraps across the inspector instead of being silently cut off", List.of())),
                buildings.stream().map(b -> new CityAddresses.Address(b.id(), 1, b.id() * 10)).toList());
        return new CityFrame(GameConfig.cityGame(), 20, List.of(), List.of(), buildings, citizens,
                List.of(), economy, addresses, agriculture);
    }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        GLFWErrorCallback callback = GLFWErrorCallback.createPrint(System.err);
        callback.set();
        glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_NULL);
        if (!glfwInit()) throw new IllegalStateException("GLFW initialization failed");
        glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_EGL_CONTEXT_API);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        var city = fixture();
        int screenshots = 0;
        for (int[] size : new int[][]{{640, 640}, {1280, 720}}) {
            int w = size[0], h = size[1];
            long window = glfwCreateWindow(w, h, "Synthetic inspector verification", 0, 0);
            if (window == 0) throw new IllegalStateException("GLFW window failed");
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            System.out.println(w + "x" + h + " renderer: " + glGetString(GL_RENDERER));
            try (var overlay = new Overlay()) {
                var info = new BuildingInfo();
                for (int property = 1; property <= 9; property++) {
                    info.show(property <= 3 || property >= 6 ? property : 0,
                            property >= 4 && property <= 6 ? property : 0);
                    for (int tab = 0; tab < 5; tab++) {
                        float width = Math.min(760, w - 32), left = (w - width) / 2;
                        info.click(left + 18 + (tab + .5f) * (width - 36) / 5, 100, w, h, city, command -> { throw new AssertionError("Tab sent command"); });
                        if (info.tab != tab || info.firstRow != 0) throw new AssertionError("Tab click failed");
                        capture(overlay, info, city, w, h, output,
                                "building-info-" + w + "x" + h + "-property-" + property + "-tab-" + tab);
                        screenshots++;
                        var wrapped = BuildingInfo.wrappedLines(info.sectionLines(city), width - 36,
                                s -> overlay.textWidth(s, 1.25f));
                        if (wrapped.stream().anyMatch(s -> overlay.textWidth(s, 1.25f) > width - 36))
                            throw new AssertionError("Text exceeds content width");
                        boolean hasBuilding = city.buildings().stream().anyMatch(b -> b.id() == info.building);
                        int visible = Math.max(1, (h - (hasBuilding ? 300 : 240)) / 25);
                        if (wrapped.size() > visible) {
                            for (int step = 0; step < wrapped.size(); step++) info.scroll(-1);
                            capture(overlay, info, city, w, h, output,
                                    "building-info-" + w + "x" + h + "-property-" + property + "-tab-" + tab + "-scrolled");
                            if (info.firstRow != wrapped.size() - visible)
                                throw new AssertionError("Scroll did not reach last detail");
                            int beforePageUp = info.firstRow;
                            info.key(GLFW_KEY_PAGE_UP, GLFW_PRESS);
                            if (info.firstRow != Math.max(0, beforePageUp - 5))
                                throw new AssertionError("Page up failed");
                            screenshots++;
                        }
                    }
                    if (info.building != 0) {
                        float left = (w - Math.min(760, w - 32)) / 2f;
                        var sent = new ArrayList<CityCommand>();
                        info.click(left + 40, h - 80, w, h, city, sent::add);
                        if (!info.confirmDemolition || !sent.isEmpty())
                            throw new AssertionError("Demolition confirmation lost");
                        capture(overlay, info, city, w, h, output,
                                "building-info-" + w + "x" + h + "-property-" + property + "-demolition-confirm");
                        screenshots++;
                        info.click(left + 40, h - 80, w, h, city, sent::add);
                        if (sent.size() != 1 || sent.get(0).kind() != CityCommand.DEMOLISH || info.open)
                            throw new AssertionError("Confirmed demolition did not send command");
                        info.show(property, property == 6 ? 6 : 0);
                    }
                    info.click(w - (w - Math.min(760, w - 32)) / 2f - 52, 50, w, h);
                    if (info.open) throw new AssertionError("Close click failed");
                }
            } finally {
                glfwDestroyWindow(window);
            }
        }
        glfwTerminate();
        callback.free();
        System.out.println("PASS: " + screenshots + " actual inspector renders; tab clicks, wrapping, end scrolling, page up and close at both sizes");
    }

    private static void capture(Overlay overlay, BuildingInfo info, CityFrame city, int w, int h,
            Path output, String name) throws Exception {
        glViewport(0, 0, w, h);
        glClearColor(.12f, .17f, .2f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        overlay.begin(w, h);
        info.render(overlay, w, h, city);
        overlay.end();
        var pixels = BufferUtils.createByteBuffer(w * h * 4);
        glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        if (glGetError() != GL_NO_ERROR) throw new AssertionError("OpenGL render error");
        var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = ((h - 1 - y) * w + x) * 4;
            image.setRGB(x, y, ((pixels.get(i) & 255) << 16)
                    | ((pixels.get(i + 1) & 255) << 8) | (pixels.get(i + 2) & 255));
        }
        ImageIO.write(image, "png", output.resolve(name + ".png").toFile());
    }
}
