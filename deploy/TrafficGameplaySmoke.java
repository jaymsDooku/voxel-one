import static org.lwjgl.glfw.GLFW.*;

import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.net.city.Polygon.Cell;

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
        if (t.lanes.isEmpty() && t.route.isEmpty()) return;
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
            t.clearRoadLanes();
            t.route.clear();
            t.laneCells=0;
            t.accessRoute=false;
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
            t.lanes.add(new RoadTraffic.Waypoint(i == 3 ? -15.5f : 20.5f, p.z));
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
        require(ids.size()>=5,"A live adjacent-lane traveller");
        int mixed=ids.get(4);
        var mp=city.ecs.get(mixed,CitySimulation.Position.class);
        mp.x=1.5f;mp.z=mounted?25.75f:25f;mp.y=grade+1.01f;
        city.ecs.get(mixed,CitySimulation.Travel.class).lanes.addAll(
                java.util.List.of(new RoadTraffic.Waypoint(20.5f,mp.z),new RoadTraffic.Waypoint(25.5f,mp.z)));
        if(!mounted) {
            int horse=horses.get(0);
            city.ecs.get(mixed,CitySimulation.Household.class).horse=horse;
            city.ecs.get(horse,CitySimulation.Mount.class).rider=-mixed;
            var hp=city.ecs.get(horse,CitySimulation.Position.class);
            hp.x=mp.x;hp.y=mp.y;hp.z=mp.z;mp.y+=.75f;
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
        float mixedBefore=mp.x;
        for(int step=0;step<10;step++)move(city,mixed);
        require(mp.x>mixedBefore,"Mixed same-direction carriageway and pavement stay independent");
        require(Math.abs(mp.z-(mounted?25.75f:25f))<.001,"Mixed traffic keeps its own lane");
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
                        !city.ecs.get(ids.get(i), CitySimulation.Travel.class).lanes.isEmpty(),
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
                t.clearRoadLanes();
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
                        .lanes
                        .add(
                                new RoadTraffic.Waypoint(
                                        i == 0 ? 10.5f : 8.5f, i == 0 ? 24.5f : 26.5f));
            }
            Thread.sleep(2000);
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
                        city.ecs.get(ids.get(i), CitySimulation.Travel.class).lanes.isEmpty(),
                        "Both crossing routes complete");
            Thread.sleep(2000);
            screenshot(out.resolve("road-traffic-game-crossing-" + reverseOrder + "-cleared.png"));
            set("notice", "Traffic edge: short merge keeps first arrival free");
            for(int n=0;n<2;n++) {
                int id=ids.get(n);var p=city.ecs.get(id,CitySimulation.Position.class);
                p.x=n==0 ? 8.53561f : 8.513209f;
                p.z=n==0 ? 23.750948f : 25.677675f;
                var t=city.ecs.get(id,CitySimulation.Travel.class);
                t.route.clear();t.clearRoadLanes();t.accessRoute=false;
                t.lanes.addAll(java.util.List.of(new RoadTraffic.Waypoint(8.5f,25.75f),
                        new RoadTraffic.Waypoint(n==0 ? 12.5f : 13.5f,25.75f)));
            }
            Thread.sleep(2000);
            screenshot(out.resolve("road-traffic-game-short-merge-"+reverseOrder+"-start.png"));
            float earlierX=city.ecs.get(ids.get(1),CitySimulation.Position.class).x;
            move(city,ids.get(1));
            require(city.ecs.get(ids.get(1),CitySimulation.Position.class).x>earlierX,
                    "Tiny corner keeps earlier arrival free");
            for(int step=0;step<40;step++) {
                for(int n=0;n<2;n++) move(city,ids.get(reverseOrder ? 1-n : n));
                var a=city.ecs.get(ids.get(0),CitySimulation.Position.class);
                var b=city.ecs.get(ids.get(1),CitySimulation.Position.class);
                require(Math.hypot(a.x-b.x,a.z-b.z)>=.8f-.0001,
                        "Short merge keeps walking gap");
                Thread.sleep(100);
            }
            for(int n=0;n<2;n++) require(city.ecs.get(ids.get(n),CitySimulation.Travel.class).lanes.isEmpty(),
                    "Both short merge routes complete");
            Thread.sleep(2000);
            screenshot(out.resolve("road-traffic-game-short-merge-"+reverseOrder+"-cleared.png"));
            set("notice", "Traffic edge: offset entries clear with body spacing");
            for(int n=0;n<3;n++) {
                int id=ids.get(n);var p=city.ecs.get(id,CitySimulation.Position.class);
                p.x=new float[]{8.5f,8.130959f,8.300838f}[n];
                p.z=new float[]{24.520006f,25,23.906181f}[n];
                p.y=city.frame().roads().get(0).y()+1.01f;
                city.ecs.get(id,CitySimulation.Household.class).horse=0;
                var t=city.ecs.get(id,CitySimulation.Travel.class);
                t.route.clear();t.clearRoadLanes();t.accessRoute=false;
                float lane=new float[]{23.25f,25,25.75f}[n];
                t.lanes.addAll(java.util.List.of(new RoadTraffic.Waypoint(8.5f,lane),
                        new RoadTraffic.Waypoint(n==0 ? 3.5f : 13.5f,lane)));
            }
            int horse=city.ecs.query(CitySimulation.Mount.class).get(0);
            int rider=ids.get(1);city.ecs.get(rider,CitySimulation.Household.class).horse=horse;
            city.ecs.get(horse,CitySimulation.Mount.class).rider=-rider;
            var rp=city.ecs.get(rider,CitySimulation.Position.class);
            var hp=city.ecs.get(horse,CitySimulation.Position.class);
            hp.x=rp.x;hp.y=rp.y;hp.z=rp.z;rp.y+=.75f;
            Thread.sleep(2000);
            screenshot(out.resolve("road-traffic-game-opposing-merge-"+reverseOrder+"-start.png"));
            for(int step=0;step<50;step++) {
                for(int n=0;n<3;n++) {
                    move(city,ids.get(reverseOrder ? 2-n : n));
                    for(int a=0;a<3;a++)for(int b=a+1;b<3;b++) {
                        var ap=city.ecs.get(ids.get(a),CitySimulation.Position.class);
                        var bp=city.ecs.get(ids.get(b),CitySimulation.Position.class);
                        require(Math.hypot(ap.x-bp.x,ap.z-bp.z)>=.5f-.0001,
                                "Offset entries keep body clearance");
                    }
                }
                if(step==5) require(rp.x>8.130959f,"The reserved stream clears its stopping space");
                Thread.sleep(100);
            }
            for(int n=0;n<3;n++) require(city.ecs.get(ids.get(n),CitySimulation.Travel.class).lanes.isEmpty(),
                    "Offset opposing entries drain in both update orders");
            Thread.sleep(2000);
            screenshot(out.resolve("road-traffic-game-opposing-merge-"+reverseOrder+"-cleared.png"));
            set("notice", "Traffic edge: queued entry leaves room to back out");
            for(int horseId:city.ecs.query(CitySimulation.Mount.class)) {
                city.ecs.get(horseId,CitySimulation.Mount.class).rider=0;
                var hp2=city.ecs.get(horseId,CitySimulation.Position.class);hp2.x=100;hp2.z=100;
            }
            for(int n=0;n<3;n++) {
                int id2=ids.get(n);var p2=city.ecs.get(id2,CitySimulation.Position.class);
                p2.x=n==0 ? 10.39f : 9.5f;p2.z=new float[]{23.25f,23.62f,24.5f}[n];
                p2.y=city.frame().roads().get(0).y()+1.01f;
                city.ecs.get(id2,CitySimulation.Household.class).horse=0;
                var t2=city.ecs.get(id2,CitySimulation.Travel.class);
                t2.route.clear();t2.clearRoadLanes();t2.accessRoute=false;
                t2.lanes.add(new RoadTraffic.Waypoint(9.5f,23.25f));
                t2.lanes.add(new RoadTraffic.Waypoint(3.5f+n,23.25f));
            }
            Thread.sleep(2000);
            screenshot(out.resolve("road-traffic-game-queued-merge-"+reverseOrder+"-start.png"));
            boolean followerRetreated=false;
            for(int step=0;step<100;step++) {
                for(int n=0;n<3;n++) {
                    int id2=ids.get(reverseOrder ? 2-n : n);
                    var p2=city.ecs.get(id2,CitySimulation.Position.class);float z2=p2.z;
                    move(city,id2);
                    if(id2==ids.get(2) && p2.z>z2)followerRetreated=true;
                    for(int a2=0;a2<3;a2++)for(int b2=a2+1;b2<3;b2++) {
                        var ap=city.ecs.get(ids.get(a2),CitySimulation.Position.class);
                        var bp=city.ecs.get(ids.get(b2),CitySimulation.Position.class);
                        require(Math.hypot(ap.x-bp.x,ap.z-bp.z)>=.8f-.0001,
                                "Queued entry keeps walking gap");
                    }
                }
                Thread.sleep(50);
            }
            require(followerRetreated,"Follower leaves room for the joining leader");
            for(int n=0;n<3;n++) require(city.ecs.get(ids.get(n),CitySimulation.Travel.class).lanes.isEmpty(),
                    "Queued entry drains in both update orders");
            Thread.sleep(2000);
            screenshot(out.resolve("road-traffic-game-queued-merge-"+reverseOrder+"-cleared.png"));
            set("notice", "Traffic regression: crossing yields and clears");
        }
        set("notice", "Traffic edge: forecourt passes a parked mount safely");
        for(int other:ids) {
            var p2=city.ecs.get(other,CitySimulation.Position.class);p2.x=100;p2.z=100;
            city.ecs.get(other,CitySimulation.Household.class).horse=0;
        }
        for(int mount:city.ecs.query(CitySimulation.Mount.class)) {
            var hp2=city.ecs.get(mount,CitySimulation.Position.class);hp2.x=100;hp2.z=100;
            city.ecs.get(mount,CitySimulation.Mount.class).rider=0;
        }
        int walker=ids.getFirst(),parked=city.ecs.query(CitySimulation.Mount.class).getFirst();
        var wp=city.ecs.get(walker,CitySimulation.Position.class);wp.x=14.5f;wp.z=22.3f;
        int grade=city.frame().roads().getFirst().y();wp.y=grade+1.01f;
        var gf=CitySimulation.class.getDeclaredField("ground");gf.setAccessible(true);
        var ground=(CitySimulation.Ground)gf.get(city);
        for(int x2=13;x2<=15;x2++)ground.apply(java.util.List.of(
                new dev.jayms.net.Protocol.Edit(x2,grade,22,dev.jayms.net.Blocks.DIRT),
                new dev.jayms.net.Protocol.Edit(x2,grade+1,22,dev.jayms.net.Blocks.AIR),
                new dev.jayms.net.Protocol.Edit(x2,grade+2,22,dev.jayms.net.Blocks.AIR)));
        var parkedPosition=city.ecs.get(parked,CitySimulation.Position.class);
        parkedPosition.x=14.5f;parkedPosition.z=23.7f;parkedPosition.y=grade+1.01f;
        var wt=city.ecs.get(walker,CitySimulation.Travel.class);
        wt.route.clear();wt.clearRoadLanes();wt.accessRoute=false;wt.passingPoints=1;
        wt.route.add(new Cell(14,24));wt.route.add(new Cell(15,24));
        Thread.sleep(2000);screenshot(out.resolve("road-traffic-game-forecourt-start.png"));
        move(city,walker);
        require(wp.z>22.3f,"Forecourt advances toward the road");
        for(int step=0;step<30;step++) {
            move(city,walker);
            require(Math.hypot(wp.x-parkedPosition.x,wp.z-parkedPosition.z)>=.65f-.0001,
                    "Forecourt retains parked mount clearance");
            Thread.sleep(100);
        }
        require(wt.route.isEmpty() && wt.lanes.isEmpty(),"Forecourt route clears the parked mount");
        Thread.sleep(2000);screenshot(out.resolve("road-traffic-game-forecourt-cleared.png"));
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
                                        "{\"status\":\"passed\",\"platform\":\"Linux inherited X11"
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
                                            + " sides\",\"mixed same-direction lanes stay free\",\"crossing yields and drains in both update"
                                            + " orders\",\"short merge clears in both update orders\",\"mixed opposing entries use one merge priority and queued entries clear; forecourt clears parked mount\"]}\n");
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
