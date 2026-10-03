import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.*;
import org.joml.*;
import org.lwjgl.opengl.GL;
import java.util.List;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Real OpenGL overlay capture for the direction guide, without private game state. */
public class BuildingGuideSmoke {
    public static void main(String[] args) throws Exception {
        org.lwjgl.glfw.GLFWErrorCallback.createPrint(System.err).set();
        glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_NULL);
        if (!glfwInit()) throw new AssertionError("GLFW init");
        glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_EGL_CONTEXT_API);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(1280, 720, "Building guide", 0, 0);
        if (window == 0) throw new AssertionError("Context creation");
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        System.out.println("Renderer: " + glGetString(GL_RENDERER) + " | " + glGetString(GL_VERSION));
        Files.createDirectories(Path.of(args[0]));
        for (int angle = 0; angle < 4; angle++) {
            var projection = new Matrix4f().ortho(-105, 105, -100, 100, .1f, 500);
            double rotation = angle * java.lang.Math.PI / 2 + java.lang.Math.PI / 4;
            var view = new Matrix4f().lookAt(140 * (float)java.lang.Math.cos(rotation), 180,
                    24 + 140 * (float)java.lang.Math.sin(rotation), 0, 24, 24, 0, 1, 0);
            var roads = new ArrayList<CityFrame.Road>();
            for (int x = -60; x <= 60; x++) for (int z = 33; z <= 35; z++)
                roads.add(new CityFrame.Road(x, z, 23));
            var addresses = new CityAddresses.State(List.of(new CityAddresses.Street(1, "Fixture road",
                    List.of(new Polygon.Point(-60, 34), new Polygon.Point(60, 34)))), List.of());
            var base = new CityFrame(GameConfig.cityGame(), 0, roads, List.of(), List.of(), List.of(), List.of());
            var city = new CityFrame(base.config(), 0, roads, List.of(), List.of(), List.of(), List.of(),
                    base.economy(), addresses);
            for (int scenario = 0; scenario < 4; scenario++) {
                var tools = new CityTools();
                tools.tool = scenario == 0 ? 4 : 0;
                var commands = new ArrayList<CityCommand>();
                var start = screen(0, scenario == 2 ? 36 : 24, projection, view);
                tools.click(start.x, start.y, 1280, 720, projection, view, city, commands::add);
                float x = scenario == 2 ? 20.5f : scenario == 3 ? 60.5f : 30.5f;
                float z = scenario == 2 ? 35.5f : 24;
                var cursor = screen(x, z, projection, view);
                var expected = new Polygon.Point(scenario == 2 ? 20.5f : scenario == 3 ? 60 : 30, scenario == 2 ? 36 : 24);
                if (!expected.equals(tools.cursorPoint(cursor.x, cursor.y, 1280, 720, projection, view, city)))
                    throw new AssertionError("Preview snapping angle " + angle + " scenario " + scenario);
                tools.hover(cursor.x, cursor.y, 1280, 720, projection, view, city);
                glClearColor(.04f, .08f, .09f, 1);
                glClear(GL_COLOR_BUFFER_BIT);
                try (var overlay = new Overlay()) {
                    overlay.begin(1280, 720);
                    for (var road : roads) {
                        var r = screen(road.x() + .5f, road.z() + .5f, projection, view);
                        overlay.rectangle(r.x - 3, r.y - 2, 6, 4, .4f, .3f, .2f, 1);
                    }
                    tools.render(overlay, 1280, 720, projection, view, city, true);
                    overlay.end();
                }
                capture(Path.of(args[0], "building-guide-angle-" + angle + "-scenario-" + scenario + ".png"));
                tools.click(cursor.x, cursor.y, 1280, 720, projection, view, city, commands::add);
                if (scenario != 0) {
                    var corner = screen(0, scenario == 2 ? 60 : 50, projection, view);
                    tools.click(corner.x, corner.y, 1280, 720, projection, view, city, commands::add);
                    tools.key(GLFW_KEY_ENTER, commands::add);
                }
                if (commands.size() != 1 || !expected.equals(commands.get(0).points().get(1)))
                    throw new AssertionError("Click differs from preview");
                if (scenario == 2) {
                    var ground = new CitySimulation.Ground() {
                        public int type(int x, int y, int z) { return 0; }
                        public void apply(List<Protocol.Edit> edits) {}
                        public boolean occupied(int x, int y, int z, int width, int depth) { return false; }
                    };
                    var simulation = new CitySimulation(city.config(), ground,
                            new Terrain(Terrain.DEFAULT_SEED), city);
                    String response = simulation.command(commands.get(0), 1,
                            new Protocol.Pose(1, 8, 40, 24, 0, 0));
                    if (!response.contains("created")) throw new AssertionError("Roadside zone rejected: " + response);
                }

            }
        }
        glfwDestroyWindow(window);
        glfwTerminate();
        System.out.println("PASS: 16 OpenGL captures; preview/click snapping verified at four camera angles");
    }
    private static Vector2f screen(float x, float z, Matrix4f projection, Matrix4f view) {
        var p = new Matrix4f(projection).mul(view).transform(new Vector4f(x, 24.03f, z, 1));
        return new Vector2f((p.x / p.w * .5f + .5f) * 1280, (.5f - p.y / p.w * .5f) * 720);
    }
    private static void capture(Path out) throws Exception {
        var data = ByteBuffer.allocateDirect(1280 * 720 * 4);
        glReadPixels(0, 0, 1280, 720, GL_RGBA, GL_UNSIGNED_BYTE, data);
        var image = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 720; y++) for (int x = 0; x < 1280; x++) {
            int i = (y * 1280 + x) * 4;
            image.setRGB(x, 719 - y, (data.get(i) & 255) << 16
                    | (data.get(i + 1) & 255) << 8 | (data.get(i + 2) & 255));
        }
        if (glGetError() != GL_NO_ERROR) throw new AssertionError("OpenGL error");
        ImageIO.write(image, "png", out.toFile());
    }

}
