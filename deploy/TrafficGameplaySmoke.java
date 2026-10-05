import static org.lwjgl.glfw.GLFW.*;

import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Real engine, synthetic city fixtures, real X11 input and production F10 capture. */
public class TrafficGameplaySmoke {
    static final Main game = new Main();
    static volatile Throwable failure;

    static Object get(String name) throws Exception {
        Field f = Main.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(game);
    }

    static void set(String name, Object value) throws Exception {
        Field f = Main.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(game, value);
    }

    static String x(String... args) throws Exception {
        var cmd = new ArrayList<String>();
        cmd.add("xdotool");
        cmd.addAll(List.of(args));
        var p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        String result = new String(p.getInputStream().readAllBytes()).trim();
        if (p.waitFor() != 0) throw new AssertionError("X11 input failed: " + args[0]);
        Thread.sleep(350);
        return result;
    }

    static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static void screenshot(Path out) throws Exception {
        var p =
                new ProcessBuilder("import", "-window", "root", out.toString())
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
        require(p.waitFor() == 0, "Capture actual game window");
    }

    static Method travel;

    static void move(CitySimulation city, int id) throws Exception {
        var t = city.ecs.get(id, CitySimulation.Travel.class);
        if (t.route.isEmpty()) return;
        travel.invoke(
                city,
                id,
                city.ecs.get(id, CitySimulation.Position.class),
                city.ecs.get(id, CitySimulation.Household.class),
                t,
                .1f);
    }

    static void scenario(CitySimulation city, boolean mounted, Path out) throws Exception {
        var ids = city.ecs.query(CitySimulation.Position.class, CitySimulation.Household.class);
        require(ids.size() >= 4, "Four live travellers");
        var horses = city.ecs.query(CitySimulation.Mount.class);
        require(horses.size() >= 4, "Four live horses");
        for (int id : ids) {
            var p = city.ecs.get(id, CitySimulation.Position.class);
            p.x = 100;
            p.z = 100;
            var t = city.ecs.get(id, CitySimulation.Travel.class);
            t.retryAt = Double.POSITIVE_INFINITY;
            t.route.clear();
            t.target = 0;
            city.ecs.get(id, CitySimulation.Household.class).horse = 0;
        }
        for (int horse : horses) {
            city.ecs.get(horse, CitySimulation.Mount.class).rider = 0;
            var p = city.ecs.get(horse, CitySimulation.Position.class);
            p.x = 100;
            p.z = 100;
        }
        var traffic = new RoadTraffic(city.frame().addresses().streets());
        var forward = List.of(new Polygon.Cell(0, 24), new Polygon.Cell(1, 24));
        var reverse = List.of(new Polygon.Cell(1, 24), new Polygon.Cell(0, 24));
        float forwardZ = traffic.lanes(forward, mounted).get(0).z();
        float reverseZ = traffic.lanes(reverse, mounted).get(0).z();
        require(
                Math.abs(forwardZ - (mounted ? 25f : 25.75f)) < .001,
                "Derived forward lane has the required carriageway/pavement side");
        require(
                Math.abs(reverseZ - (mounted ? 24f : 23.25f)) < .001,
                "Derived reverse lane has the required carriageway/pavement side");
        var world = (World) get("world");
        int grade = city.frame().roads().get(0).y();
        for (int half = 0; half < 6; half++) {
            int expected = half == 0 || half == 5 ? Blocks.STONE : Blocks.DIRT;
            require(
                    world.region(Protocol.Edit.at(0, grade + .5, 23 + half * .5, 0, 1))
                            == Blocks.piece(expected, 1),
                    "Actual road has two one-metre dirt lanes and one half-metre pavement per"
                        + " side");
        }
        float gap = mounted ? .65f : .5f;
        for (int i = 0; i < 4; i++) {
            int id = ids.get(i);
            var p = city.ecs.get(id, CitySimulation.Position.class);
            p.x = i == 3 ? 4.5f : 1.5f - i * (mounted ? 1f : .65f);
            p.z = i == 3 ? reverseZ : forwardZ;
            p.y = city.frame().roads().get(0).y() + 1.01f;
            p.yaw = i == 3 ? 180 : 0;
            var t = city.ecs.get(id, CitySimulation.Travel.class);
            t.route.add(new RoadTraffic.Waypoint(i == 3 ? -15.5f : 20.5f, p.z));
            if (mounted) {
                int horse = horses.get(i);
                city.ecs.get(id, CitySimulation.Household.class).horse = horse;
                city.ecs.get(horse, CitySimulation.Mount.class).rider = -id;
                var hp = city.ecs.get(horse, CitySimulation.Position.class);
                hp.x = p.x;
                hp.y = p.y;
                hp.z = p.z;
                hp.yaw = p.yaw;
                p.y += .75f;
            }
        }
        var camera = (IsometricCamera) get("overview");
        camera.focus(4, 24.5f, city.frame().roads().get(0).y() + 1.01f);
        camera.zoom(20);
        set(
                "notice",
                mounted
                        ? "Traffic test: horse queue; leader held"
                        : "Traffic test: pavement queue; leader held");
        Thread.sleep(700);
        x("key", "F10");
        for (int i = 1; i < 3; i++) {
            int id = ids.get(i);
            var p = city.ecs.get(id, CitySimulation.Position.class);
            float before = p.x;
            move(city, id);
            require(p.x == before, "Follower must wait behind held leader");
            require(
                    city.ecs
                            .get(id, CitySimulation.Travel.class)
                            .activity
                            .equals("Waiting for traffic"),
                    "Traffic waiting state");
        }
        float reverseBefore = city.ecs.get(ids.get(3), CitySimulation.Position.class).x;
        move(city, ids.get(3));
        require(
                city.ecs.get(ids.get(3), CitySimulation.Position.class).x < reverseBefore,
                "Opposite lane remains free");
        Thread.sleep(1000);
        screenshot(
                out.resolve(
                        mounted
                                ? "road-traffic-game-horses-waiting.png"
                                : "road-traffic-game-pedestrians-waiting.png"));
        set(
                "notice",
                mounted
                        ? "Traffic test: horse queue released"
                        : "Traffic test: pavement queue released");
        for (int step = 0; step < 20; step++) {
            for (int i = 0; i < 4; i++) move(city, ids.get(i));
            for (int i = 1; i < 3; i++) {
                var a = city.ecs.get(ids.get(i - 1), CitySimulation.Position.class);
                var b = city.ecs.get(ids.get(i), CitySimulation.Position.class);
                require(
                        a.x > b.x && Math.hypot(a.x - b.x, a.z - b.z) >= gap - .0001,
                        "Single file, no overtaking or collision");
            }
            for (int i = 0; i < 4; i++)
                require(
                        !city.ecs.get(ids.get(i), CitySimulation.Travel.class).route.isEmpty(),
                        "Route retained during travel");
            Thread.sleep(250);
        }
        for (int i = 0; i < 3; i++) {
            var p = city.ecs.get(ids.get(i), CitySimulation.Position.class);
            require(p.x > 1.5f - i * (mounted ? 1f : .65f), "Every queued traveller resumes");
            require(Math.abs(p.z - (mounted ? 25f : 25.75f)) < .001, "Forward lane retained");
        }
        require(
                Math.abs(
                                city.ecs.get(ids.get(3), CitySimulation.Position.class).z
                                        - (mounted ? 24f : 23.25f))
                        < .001,
                "Reverse lane retained");
        screenshot(
                out.resolve(
                        mounted
                                ? "road-traffic-game-horses-resumed.png"
                                : "road-traffic-game-pedestrians-resumed.png"));
        x("key", "F10");
        Thread.sleep(2500);
    }

    static void crossing(CitySimulation city, Path out) throws Exception {
        var ids = city.ecs.query(CitySimulation.Position.class, CitySimulation.Household.class);
        var camera = (IsometricCamera) get("overview");
        camera.focus(8.5f, 24.5f, city.frame().roads().get(0).y() + 1.01f);
        camera.zoom(20);
        set("notice", "Traffic regression: crossing yields and clears");
        x("key", "F10");
        for (boolean reverseOrder : new boolean[] {false, true}) {
            for (int id : ids) {
                var p = city.ecs.get(id, CitySimulation.Position.class);
                p.x = 100;
                p.z = 100;
                city.ecs.get(id, CitySimulation.Household.class).horse = 0;
                var t = city.ecs.get(id, CitySimulation.Travel.class);
                t.retryAt = Double.POSITIVE_INFINITY;
                t.target = 0;
                t.route.clear();
            }
            for (int horse : city.ecs.query(CitySimulation.Mount.class)) {
                city.ecs.get(horse, CitySimulation.Mount.class).rider = 0;
                var p = city.ecs.get(horse, CitySimulation.Position.class);
                p.x = 100;
                p.z = 100;
            }
            for (int i = 0; i < 2; i++) {
                int id = ids.get(i);
                var p = city.ecs.get(id, CitySimulation.Position.class);
                p.x = i == 0 ? 8.15f : 8.5f;
                p.z = i == 0 ? 24.5f : 24.1f;
                p.y = city.frame().roads().get(0).y() + 1.01f;
                city.ecs
                        .get(id, CitySimulation.Travel.class)
                        .route
                        .add(
                                new RoadTraffic.Waypoint(
                                        i == 0 ? 10.5f : 8.5f, i == 0 ? 24.5f : 26.5f));
            }
            Thread.sleep(750);
            screenshot(out.resolve("road-traffic-game-crossing-" + reverseOrder + "-start.png"));
            boolean backedOut = false;
            for (int step = 0; step < 30; step++) {
                for (int n = 0; n < 2; n++) {
                    int id = ids.get(reverseOrder ? 1 - n : n);
                    var p = city.ecs.get(id, CitySimulation.Position.class);
                    float before = p.z;
                    move(city, id);
                    if (id == ids.get(1) && p.z < before) backedOut = true;
                    var a = city.ecs.get(ids.get(0), CitySimulation.Position.class);
                    var b = city.ecs.get(ids.get(1), CitySimulation.Position.class);
                    require(
                            Math.hypot(a.x - b.x, a.z - b.z) >= .5f - .0001,
                            "Crossing must retain spacing in both update orders");
                    require(
                            !city.ecs
                                    .get(id, CitySimulation.Travel.class)
                                    .activity
                                    .equals("Route obstructed"),
                            "Crossing remains passable");
                }
                Thread.sleep(150);
            }
            require(backedOut, "Yielding traveller clears priority stopping space");
            for (int i = 0; i < 2; i++)
                require(
                        city.ecs.get(ids.get(i), CitySimulation.Travel.class).route.isEmpty(),
                        "Both crossing routes complete");
            screenshot(out.resolve("road-traffic-game-crossing-" + reverseOrder + "-cleared.png"));
        }
        x("key", "F10");
        Thread.sleep(2500);
    }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11);
        set("gameConfig", GameConfig.cityGame());
        set("offlineSave", out.resolve("synthetic-world.dat"));
        travel =
                CitySimulation.class.getDeclaredMethod(
                        "travel",
                        int.class,
                        CitySimulation.Position.class,
                        CitySimulation.Household.class,
                        CitySimulation.Travel.class,
                        float.class);
        travel.setAccessible(true);
        var driver =
                new Thread(
                        () -> {
                            long handle = 0;
                            try {
                                String id = "";
                                for (int i = 0; i < 180; i++) {
                                    if (get("player") != null && (int) get("fps") >= 0) {
                                        Thread.sleep(1000);
                                        handle =
                                                ((dev.jayms.window.Window) get("window"))
                                                        .getHandle();
                                        id =
                                                x("search", "--name", "^Voxel One")
                                                        .lines()
                                                        .findFirst()
                                                        .orElseThrow();
                                        break;
                                    }
                                    Thread.sleep(500);
                                }
                                require(!id.isEmpty(), "Engine ready");
                                x("windowfocus", id);
                                if (((dev.jayms.ui.ControlsMenu) get("menu")).open)
                                    x("key", "Escape");
                                require((boolean) get("isometric"), "City isometric view");
                                var city = ((LocalGame) get("local")).city;
                                scenario(city, false, out);
                                scenario(city, true, out);
                                crossing(city, out);
                                Files.writeString(
                                        out.resolve("results.json"),
                                        "{\"status\":\"passed\",\"platform\":\"Linux Xvfb"
                                            + " Mesa\",\"application\":\"Main offline"
                                            + " city\",\"input\":\"real xdotool GLFW callbacks and"
                                            + " F10\",\"scenarioControl\":\"synthetic ECS fixtures,"
                                            + " automatic replanning held; production travel"
                                            + " invoked at 0.1 seconds\",\"checks\":[\"pedestrians"
                                            + " wait and resume\",\"horses wait and"
                                            + " resume\",\"opposite lane remains"
                                            + " free\",\"single-file spacing\",\"no"
                                            + " overtaking\",\"directional lanes"
                                            + " retained\",\"routes retained\",\"actual"
                                            + " dirt/pavement geometry and derived lane"
                                            + " sides\",\"crossing yields and drains in both update"
                                            + " orders\"]}\n");
                            } catch (Throwable e) {
                                failure = e;
                            } finally {
                                if (handle != 0) glfwSetWindowShouldClose(handle, true);
                            }
                        });
        driver.setDaemon(true);
        driver.start();
        game.run();
        driver.join(1000);
        if (failure != null)
            throw new AssertionError("Traffic gameplay validation failed", failure);
        require(Files.exists(out.resolve("results.json")), "Driver completed");
    }
}
