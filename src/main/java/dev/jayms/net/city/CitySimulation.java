package dev.jayms.net.city;

import static dev.jayms.net.city.Polygon.*;

import dev.jayms.net.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Fixed-step city systems: construction, households/jobs, needs/economy and road travel. */
public final class CitySimulation {
    public interface Ground {
        int type(int x, int y, int z);

        void apply(List<Protocol.Edit> edits);

        boolean occupied(int x, int y, int z, int width, int depth);

        default boolean playerOccupied(int x, int y, int z, int width, int depth) {
            return occupied(x, y, z, width, depth);
        }
    }

    public static final String[] COHORTS = {"Labourers", "Skilled workers", "Prosperous settlers"};
    public static final String[] ZONES = {"Residential", "Commercial", "Industrial"};

    public static final class Position {
        public float x, y, z, yaw, phase;

        Position(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static final class Household {
        public String name;
        public int cohort, home, job, horse;

        Household(String name, int cohort) {
            this.name = name;
            this.cohort = cohort;
        }
    }

    public static final class Needs {
        public float hunger = 90, money;

        Needs(float money) {
            this.money = money;
        }
    }

    public static final class Travel {
        public int target;
        public double retryAt, mealUntil;
        public String activity = "Looking for home / work";
        public final ArrayDeque<Cell> route = new ArrayDeque<>();
    }

    public static final class Mount {
        public int rider;
    }

    public final Ecs ecs = new Ecs();
    private final Ground ground;
    private final GameConfig config;
    private final Map<Cell, Integer> roads = new LinkedHashMap<>();
    private final List<CityFrame.Zone> zones = new ArrayList<>();
    private final List<CityFrame.Building> buildings = new ArrayList<>();
    private double elapsed, accumulator, nextBuild;
    private int zoneIds, buildingIds, grade;
    public final CityEconomy economy;
    private boolean founding = true;

    public CitySimulation(GameConfig config, Ground ground, Terrain terrain, CityFrame saved) {
        this.config = config;
        this.ground = ground;
        grade = Math.max(-26, Math.min(88, terrain.column(8, 24).height()));
        if (saved != null) {
            restore(saved);
            economy = new CityEconomy(ecs, saved.economy());
            economy.adopt(buildings);
            founding = false;
            return;
        }
        if (!config.city()) {
            economy = new CityEconomy(ecs, null);
            founding = false;
            return;
        }
        road(List.of(new Point(-10, 24), new Point(44, 24)));
        road(List.of(new Point(8, 8), new Point(8, 45)));
        zone(
                0,
                new Polygon(
                        List.of(
                                new Point(12, 26),
                                new Point(40, 26),
                                new Point(40, 42),
                                new Point(12, 42))));
        zone(
                1,
                new Polygon(
                        List.of(
                                new Point(12, 13),
                                new Point(24, 13),
                                new Point(24, 23),
                                new Point(12, 23))));
        zone(
                2,
                new Polygon(
                        List.of(
                                new Point(-8, 13),
                                new Point(4, 13),
                                new Point(4, 23),
                                new Point(-8, 23))));
        for (int i = 0; i < 12; i++) {
            int id = ecs.create();
            ecs.put(
                    id,
                    Position.class,
                    new Position(9.5f + i % 3, grade + 1.01f, 24.5f + i / 3 * .15f));
            ecs.put(
                    id,
                    Household.class,
                    new Household(
                            new String[] {
                                        "Alex", "Robin", "Morgan", "Sam", "Taylor", "Jamie",
                                        "River", "Casey", "Avery", "Rowan", "Jordan", "Sky"
                                    }
                                    [i],
                            i / 4));
            ecs.put(id, Needs.class, new Needs(12 + i / 4 * 24));
            ecs.put(id, Travel.class, new Travel());
        }
        for (int i = 0; i < 6; i++) {
            int id = ecs.create();
            ecs.put(id, Position.class, new Position(2.5f + i, grade + 1.01f, 24.5f));
            ecs.put(id, Mount.class, new Mount());
        }
        economy = new CityEconomy(ecs, null);
        founding = false;
    }

    private void restore(CityFrame f) {
        elapsed = f.elapsed();
        nextBuild = elapsed + 2;
        for (var r : f.roads()) roads.put(new Cell(r.x(), r.z()), r.y());
        zones.addAll(f.zones());
        buildings.addAll(f.buildings());
        zoneIds = zones.stream().mapToInt(CityFrame.Zone::id).max().orElse(0);
        buildingIds = buildings.stream().mapToInt(CityFrame.Building::id).max().orElse(0);
        for (var c : f.citizens()) {
            ecs.restore(c.id());
            var p = new Position(c.x(), c.y(), c.z());
            p.yaw = c.yaw();
            p.phase = c.phase();
            ecs.put(c.id(), Position.class, p);
            var h = new Household(c.name(), c.cohort());
            h.home = c.home();
            h.job = c.job();
            h.horse = c.horse();
            ecs.put(c.id(), Household.class, h);
            var n = new Needs(c.money());
            n.hunger = c.hunger();
            ecs.put(c.id(), Needs.class, n);
            ecs.put(c.id(), Travel.class, new Travel());
        }
        for (var h : f.horses()) {
            ecs.restore(h.id());
            var p = new Position(h.x(), h.y(), h.z());
            p.yaw = h.yaw();
            p.phase = h.phase();
            ecs.put(h.id(), Position.class, p);
            var m = new Mount();
            m.rider = h.rider() < 0 ? h.rider() : 0;
            ecs.put(h.id(), Mount.class, m);
        }
    }

    public GameConfig config() {
        return config;
    }

    public void advance(double dt) {
        if (!Double.isFinite(dt) || dt <= 0) return;
        accumulator += Math.min(dt, 2);
        while (accumulator >= .1) {
            step(.1f);
            accumulator -= .1;
        }
    }

    private void step(float dt) {
        elapsed += dt;
        if (!config.city()) return;
        if (elapsed >= nextBuild) {
            construct();
            nextBuild = elapsed + 4;
        }
        economy.rent(dt / config.daySeconds());
        finishProjects();
        assign();
        for (int id : ecs.query(Position.class, Household.class, Needs.class, Travel.class)) {
            var p = ecs.get(id, Position.class);
            var h = ecs.get(id, Household.class);
            var n = ecs.get(id, Needs.class);
            var t = ecs.get(id, Travel.class);
            var time = config.time(elapsed);
            // Needs and wages follow simulated hours, independent of configured day length.
            float hours = (float) (dt * 24 / config.daySeconds());
            n.hunger =
                    Math.max(
                            0, n.hunger - hours * (time.period() == CityTime.Period.NIGHT ? 2 : 5));
            if (t.mealUntil > elapsed) {
                t.activity = "Eating at shop";
                continue;
            }
            if (t.retryAt > elapsed) {
                t.activity = "Waiting for a clear route";
                continue;
            }
            boolean working = time.period() == CityTime.Period.WORKDAY;
            float mealThreshold = working ? 65 : 85;
            int mealShop =
                    time.shopsOpen() && n.hunger < mealThreshold && n.money >= 3 ? find(1) : 0;
            boolean eating = mealShop != 0;
            int target = eating ? mealShop : working && h.job != 0 ? h.job : h.home;
            if (target == 0) {
                t.target = 0;
                t.route.clear();
                t.activity = working ? "Needs home / work" : "No home for the night";
                continue;
            }
            t.activity =
                    target == 0
                            ? "Needs home / work"
                            : eating
                                    ? "Going to shop"
                                    : target == h.job && working
                                            ? (h.job < 0
                                                    ? "Going to construction site"
                                                    : "Commuting to work")
                                            : "Going home";
            if (target != t.target) {
                t.target = target;
                t.route.clear();
                var b = workplace(target);
                if (b != null) journey(id, p, b, t);
            }
            if (!t.route.isEmpty()) {
                travel(id, p, h, t, dt);
                continue;
            }
            var b = workplace(target);
            if (b == null) continue;
            if (!(p.x > b.x() && p.x < b.x() + 6 && p.z > b.z() && p.z < b.z() + 7)) {
                t.activity = "No connected road route";
                t.target = -9999;
                t.retryAt = elapsed + 2;
                continue;
            }
            if (target < 0 && working && !eating) {
                var project = economy.project(-target);
                if (project != null && economy.wage(project.developer(), dt * .15, n)) {
                    economy.work(project.id(), dt);
                    t.activity = "Building for developer";
                } else t.activity = "Developer cannot afford wages";
            } else if (b.type() == 2 && working && !eating) {
                t.activity = "Working in mine";
                var property = economy.property(b.id());
                if (property == null
                        || !economy.wage(property.operator(), hours * (1.8 + h.cohort * .3), n)) {
                    t.activity = "Employer cannot afford wages";
                    continue;
                }
                int stock = Math.min(1000, b.stock() + 1);
                replaceStock(b, stock);
                for (var shop : new ArrayList<>(buildings))
                    if (shop.type() == 1
                            && shop.stock() < 80
                            && stock > 0
                            && ((int) (elapsed * 10)) % 20 == 0
                            && economy.delivery(b.id(), shop.id())) {
                        replaceStock(shop, shop.stock() + 1);
                        replaceStock(building(b.id()), --stock);
                    }
            } else if (b.type() == 1 && working && !eating) {
                var property = economy.property(b.id());
                t.activity =
                        economy.wage(property.operator(), hours * 1.8, n)
                                ? "Working in shop"
                                : "Employer cannot afford wages";
            } else if (b.type() == 1 && eating) {
                if (n.hunger < 95 && n.money >= 3 && b.stock() > 0) {
                    n.money -= 3;
                    economy.meal(b.id(), 3);
                    n.hunger = Math.min(100, n.hunger + 35);
                    replaceStock(b, b.stock() - 1);
                    t.target = -9999;
                    t.mealUntil = elapsed + Math.min(3, config.daySeconds() / 240);
                }
                t.activity =
                        n.money < 3
                                ? "Cannot afford food"
                                : b.stock() == 0 ? "Shop needs mine deliveries" : "Eating at shop";
                if (n.money < 3 && h.job != 0) {
                    t.target = h.job;
                    var job = workplace(h.job);
                    journey(id, p, job, t);
                }
            } else
                t.activity =
                        time.period() == CityTime.Period.NIGHT
                                ? "Sleeping at home"
                                : time.period() == CityTime.Period.MORNING
                                        ? "Morning at home"
                                        : working ? "Looking for work" : "Relaxing at home";
        }
    }

    private void replaceStock(CityFrame.Building b, int stock) {
        int i = buildings.indexOf(b);
        if (i >= 0)
            buildings.set(
                    i,
                    new CityFrame.Building(
                            b.id(), b.zone(), b.type(), b.x(), b.y(), b.z(), b.capacity(), stock));
    }

    private void assign() {
        for (var b : buildings) if (b.type() != 0) economy.operate(b);
        for (var plot : economy.plots)
            if (plot.building() == 0) {
                for (int id : ecs.query(Household.class)) {
                    if (occupants(-plot.id(), false) >= 2) break;
                    var h = ecs.get(id, Household.class);
                    if (h.job >= 0) {
                        h.job = -plot.id();
                        var t = ecs.get(id, Travel.class);
                        t.target = -9999;
                        t.route.clear();
                    }
                }
            }
        for (int id : ecs.query(Household.class)) {
            var h = ecs.get(id, Household.class);
            if (h.job < 0 && economy.project(-h.job) == null) h.job = 0;
            if (h.home > 0
                    && economy.contracts.stream()
                            .noneMatch(
                                    c ->
                                            c.partyKind() == CityEconomy.CITIZEN
                                                    && c.party() == id
                                                    && c.building() == h.home)) {
                var home = building(h.home);
                if (home == null || !economy.house(id, home)) h.home = 0;
            }
            if (h.home == 0) {
                for (var b : buildings)
                    if (b.type() == 0
                            && occupants(b.id(), true) < b.capacity()
                            && economy.house(id, b)) {
                        h.home = b.id();
                        break;
                    }
            }
            if (h.job == 0) {
                for (var plot : economy.plots)
                    if (plot.building() == 0 && occupants(-plot.id(), false) < 2) {
                        h.job = -plot.id();
                        break;
                    }
                if (h.job == 0)
                    for (var b : buildings)
                        if (b.type() != 0
                                && economy.property(b.id()).operator() != 0
                                && occupants(b.id(), false) < (b.type() == 1 ? 2 : b.capacity())) {
                            h.job = b.id();
                            break;
                        }
            }
        }
    }

    private long occupants(int building, boolean home) {
        return ecs.query(Household.class).stream()
                .filter(
                        id -> {
                            var h = ecs.get(id, Household.class);
                            return (home ? h.home : h.job) == building;
                        })
                .count();
    }

    private CityFrame.Building workplace(int id) {
        if (id >= 0) return building(id);
        var p = economy.project(-id);
        return p == null
                ? null
                : new CityFrame.Building(id, p.zone(), p.type(), p.x(), p.y() - 1, p.z(), 2, 0);
    }

    private int find(int type) {
        return buildings.stream()
                .filter(b -> b.type() == type && b.stock() > 0)
                .mapToInt(CityFrame.Building::id)
                .findFirst()
                .orElse(0);
    }

    private CityFrame.Building building(int id) {
        return buildings.stream().filter(b -> b.id() == id).findFirst().orElse(null);
    }

    private int entrance(CityFrame.Building b) {
        var north = nearest(b.x() + 2.5f, b.z() - .5f);
        var south = nearest(b.x() + 2.5f, b.z() + 7.5f);
        if (north == null || south == null) return b.z() - 1;
        double nd = Math.hypot(north.x() - b.x() - 2, north.z() - b.z() + 1),
                sd = Math.hypot(south.x() - b.x() - 2, south.z() - b.z() - 7);
        return nd <= sd ? b.z() - 1 : b.z() + 7;
    }

    private void journey(int id, Position p, CityFrame.Building destination, Travel t) {
        float x = p.x, z = p.z;
        for (var b : buildings)
            if (x > b.x() && x < b.x() + 6 && z > b.z() && z < b.z() + 7) {
                int door = entrance(b);
                if (b.type() == 2 && door > b.z()) {
                    t.route.add(new Cell(b.x() + 1, b.z() + 2));
                    t.route.add(new Cell(b.x() + 1, b.z() + 5));
                    t.route.add(new Cell(b.x() + 2, b.z() + 5));
                    t.route.add(new Cell(b.x() + 2, b.z() + 6));
                } else {
                    // Join the clear centre aisle before heading for the door. Diagonal shortcuts
                    // from a station can cross the shop's shelves.
                    t.route.add(new Cell(b.x() + 2, (int) Math.floor(z)));
                    t.route.add(new Cell(b.x() + 2, door < b.z() ? b.z() + 1 : b.z() + 5));
                }
                t.route.add(new Cell(b.x() + 2, door));
                x = b.x() + 2.5f;
                z = door + .5f;
                break;
            }
        int door = entrance(destination);
        var path = route(x, z, destination.x() + 2.5f, door + .5f);
        if (path.isEmpty()) {
            t.route.clear();
            return;
        }
        t.route.addAll(path);
        if (destination.type() == 2 && door > destination.z()) {
            t.route.add(new Cell(destination.x() + 2, destination.z() + 6));
            t.route.add(new Cell(destination.x() + 2, destination.z() + 5));
            t.route.add(new Cell(destination.x() + 1, destination.z() + 5));
            t.route.add(new Cell(destination.x() + 1, destination.z() + 2));
        } else
            t.route.add(
                    new Cell(
                            destination.x() + 2,
                            door < destination.z() ? destination.z() + 1 : destination.z() + 5));
        t.route.add(new Cell(destination.x() + 2, destination.z() + 1 + (id / 4) % 2));
        t.route.add(new Cell(destination.x() + 1 + id % 4, destination.z() + 1 + (id / 4) % 2));
    }

    private void travel(int id, Position p, Household h, Travel t, float dt) {
        var site = building(t.target);
        if (h.horse != 0
                && site != null
                && Math.hypot(p.x - site.x() - 2.5f, p.z - entrance(site) - .5f) < 2) {
            ecs.get(h.horse, Mount.class).rider = 0;
            h.horse = 0;
            p.y -= .75f;
        }

        if (h.horse == 0
                && h.cohort > 0
                && roads.containsKey(new Cell((int) Math.floor(p.x), (int) Math.floor(p.z)))) {
            for (int horse : ecs.query(Mount.class)) {
                var m = ecs.get(horse, Mount.class);
                var hp = ecs.get(horse, Position.class);
                if (m.rider == 0 && distance(p, hp) < 4) {
                    m.rider = -id;
                    h.horse = horse;
                    break;
                }
            }
        }
        float speed = h.horse == 0 ? 2.2f : 5.5f;
        var target = t.route.peek();
        float dx = target.x() + .5f - p.x,
                dz = target.z() + .5f - p.z,
                dist = (float) Math.hypot(dx, dz);
        float nx = dist < speed * dt ? target.x() + .5f : p.x + dx / dist * speed * dt,
                nz = dist < speed * dt ? target.z() + .5f : p.z + dz / dist * speed * dt;
        if (!passable(nx, nz)) {
            t.activity = "Route obstructed";
            t.target = -9999;
            t.route.clear();
            t.retryAt = elapsed + 2;
            return;
        }
        if (dist < speed * dt) {
            p.x = target.x() + .5f;
            p.z = target.z() + .5f;
            t.route.remove();
        } else {
            p.x += dx / dist * speed * dt;
            p.z += dz / dist * speed * dt;
        }
        p.y =
                roads.getOrDefault(new Cell((int) Math.floor(p.x), (int) Math.floor(p.z)), grade)
                        + 1.01f;
        for (var b : buildings)
            if (p.x > b.x() && p.x < b.x() + 6 && p.z > b.z() && p.z < b.z() + 7) {
                p.y = b.y() + 1.01f;
                break;
            }
        p.yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
        p.phase += speed * dt * 2.66f;
        if (h.horse != 0) {
            var hp = ecs.get(h.horse, Position.class);
            hp.x = p.x;
            hp.y = p.y;
            hp.z = p.z;
            hp.yaw = p.yaw;
            hp.phase = p.phase;
            p.y += .75f;
            if (t.route.isEmpty()) {
                ecs.get(h.horse, Mount.class).rider = 0;
                h.horse = 0;
                p.y -= .75f;
            }
        }
    }

    private static float distance(Position a, Position b) {
        return (float)
                Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2) + Math.pow(a.z - b.z, 2));
    }

    public List<Cell> route(float x, float z, float tx, float tz) {
        Cell a = nearest(x, z), b = nearest(tx, tz);
        if (a == null || b == null) return List.of();
        var parents = new HashMap<Cell, Cell>();
        var queue = new ArrayDeque<Cell>();
        queue.add(a);
        parents.put(a, a);
        while (!queue.isEmpty()) {
            var c = queue.remove();
            if (c.equals(b)) break;
            for (var next : neighbours(c))
                if (roads.containsKey(next)
                        && !parents.containsKey(next)
                        && passable(next.x() + .5f, next.z() + .5f)) {
                    parents.put(next, c);
                    queue.add(next);
                }
        }
        if (!parents.containsKey(b)) return List.of();
        var route = new ArrayList<Cell>();
        for (var c = b; !c.equals(a); c = parents.get(c)) route.add(c);
        route.add(a);
        Collections.reverse(route);
        route.add(new Cell((int) Math.floor(tx), (int) Math.floor(tz)));
        return route;
    }

    private boolean passable(float x, float z) {
        float y = grade + 1.01f;
        for (var b : buildings)
            if (x > b.x() && x < b.x() + 6 && z > b.z() && z < b.z() + 7) {
                y = b.y() + 1.01f;
                break;
            }
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z), by = (int) Math.floor(y);
        return ground.type(bx, by, bz) == 0
                && ground.type(bx, by + 1, bz) == 0
                && ground.type(bx, by - 1, bz) != 0;
    }

    private Cell nearest(float x, float z) {
        return roads.keySet().stream()
                .min(
                        Comparator.comparingDouble(
                                c -> Math.pow(c.x() + .5 - x, 2) + Math.pow(c.z() + .5 - z, 2)))
                .orElse(null);
    }

    private static List<Cell> neighbours(Cell c) {
        return List.of(
                new Cell(c.x() + 1, c.z()),
                new Cell(c.x() - 1, c.z()),
                new Cell(c.x(), c.z() + 1),
                new Cell(c.x(), c.z() - 1));
    }

    public String command(CityCommand c, int player, Protocol.Pose pose) {
        if (!config.city()) return "Join Voxel City One to use city tools";
        try {
            return switch (c.kind()) {
                case CityCommand.ROAD -> road(c.points());
                case CityCommand.ZONE -> zone(c.value(), new Polygon(c.points()));
                case CityCommand.RIDE -> ride(player, c.value(), pose);
                default -> "Unknown city tool";
            };
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    private String road(List<Point> points) {
        if (points.size() < 2) throw new IllegalArgumentException("Roads need two endpoints");
        var cells = new LinkedHashSet<Cell>();
        for (int i = 1; i < points.size(); i++) {
            var a = points.get(i - 1);
            var b = points.get(i);
            int x = (int) Math.floor(a.x()),
                    z = (int) Math.floor(a.z()),
                    bx = (int) Math.floor(b.x()),
                    bz = (int) Math.floor(b.z());
            while (true) {
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++) cells.add(new Cell(x + dx, z + dz));
                if (x == bx && z == bz) break;
                if (x != bx) x += Integer.signum(bx - x);
                else z += Integer.signum(bz - z);
            }
        }
        if (cells.size() > 768 || roads.size() + cells.size() > 8192)
            throw new IllegalArgumentException("Road too long: use shorter sections");
        for (var cell : cells) {
            if (Math.abs(cell.x() - 8) > 256 || Math.abs(cell.z() - 24) > 256)
                throw new IllegalArgumentException("Road outside city limits");
            for (var zone : zones)
                if (zone.polygon().contains(cell.x() + .5f, cell.z() + .5f))
                    throw new IllegalArgumentException("Road would cross an existing zone");
            if (ground.occupied(cell.x(), grade + 1, cell.z(), 1, 1))
                throw new IllegalArgumentException("Road would intersect a player");
        }
        int newCells = (int) cells.stream().filter(c -> !roads.containsKey(c)).count();
        if (!founding && !economy.roads(newCells))
            return "Mayor budget too low for road: needs $"
                    + (int) (newCells * CityEconomy.ROAD_COST);
        var edits = new ArrayList<Protocol.Edit>();
        for (var c : cells)
            if (!roads.containsKey(c)) {
                level(c.x(), c.z(), edits);
                roads.put(c, grade);
            }
        ground.apply(edits);
        return founding
                ? "Dirt road built"
                : "Dirt road built | Mayor paid $" + (int) (newCells * CityEconomy.ROAD_COST);
    }

    private void level(int x, int z, List<Protocol.Edit> edits) {
        for (int y = Terrain.MAX_Y; y > grade; y--)
            if (ground.type(x, y, z) != 0) edits.add(new Protocol.Edit(x, y, z, 0));
        int bottom = grade;
        while (bottom > Terrain.MIN_Y && ground.type(x, bottom, z) == 0) bottom--;
        for (int y = bottom + 1; y < grade; y++) edits.add(new Protocol.Edit(x, y, z, Blocks.DIRT));
        edits.add(new Protocol.Edit(x, grade, z, Blocks.DIRT));
    }

    private String zone(int type, Polygon polygon) {
        if (type < 0 || type > 2 || zones.size() >= 128)
            throw new IllegalArgumentException("Invalid zone type or city zone limit reached");
        var cells = polygon.cells();
        boolean adjacent = false;
        for (var c : cells) {
            if (roads.containsKey(c))
                throw new IllegalArgumentException("Zones cannot cover roads");
            if (neighbours(c).stream().anyMatch(roads::containsKey)) adjacent = true;
            for (var z : zones)
                if (z.polygon().contains(c.x() + .5f, c.z() + .5f))
                    throw new IllegalArgumentException("Zones cannot overlap");
        }
        if (!adjacent) throw new IllegalArgumentException("Zone must touch a dirt road");
        boolean fits = false;
        for (var c : cells)
            if (fits(polygon, c.x(), c.z())) {
                fits = true;
                break;
            }
        if (!fits)
            throw new IllegalArgumentException("Leave room for a 6 x 7 building and its entrance");
        zones.add(new CityFrame.Zone(++zoneIds, type, polygon));
        return ZONES[type] + " zone created; private developers assess demand";
    }

    private boolean fits(Polygon polygon, int x, int z) {
        for (int dx = 0; dx < 6; dx++)
            for (int dz = -1; dz <= 7; dz++)
                if (!polygon.contains(x + dx + .5f, z + dz + .5f)) return false;
        if (economy != null && economy.overlaps(x, z)) return false;
        for (var b : buildings)
            if (x - 1 < b.x() + 7 && x + 7 > b.x() - 1 && z - 2 < b.z() + 8 && z + 8 > b.z() - 2)
                return false;
        return true;
    }

    private void construct() {
        if (buildings.size() + economy.plots.stream().filter(p -> p.building() == 0).count() >= 512)
            return;
        for (var zone : zones)
            if (demand(zone.type()))
                for (var c : zone.polygon().cells())
                    if (fits(zone.polygon(), c.x(), c.z())) {
                        int x = c.x(), z = c.z();
                        if (ground.occupied(x, grade + 1, z, 6, 7)) continue;
                        var edits = new ArrayList<Protocol.Edit>();
                        for (int dx = 0; dx < 6; dx++)
                            for (int dz = -1; dz <= 7; dz++) level(x + dx, z + dz, edits);
                        var planned =
                                new CityFrame.Building(
                                        buildingIds + 1,
                                        zone.id(),
                                        zone.type(),
                                        x,
                                        grade + 1,
                                        z,
                                        16,
                                        0);
                        int door = entrance(planned);
                        var access = nearest(x + 2.5f, door + .5f);
                        if (access != null) {
                            float dx = x + 2.5f - access.x() - .5f, dz = door - access.z();
                            int count = Math.max(1, (int) (Math.hypot(dx, dz) * 4));
                            var path = new LinkedHashSet<Cell>();
                            for (int i = 0; i <= count; i++)
                                path.add(
                                        new Cell(
                                                (int) Math.floor(access.x() + .5f + dx * i / count),
                                                (int)
                                                        Math.floor(
                                                                access.z()
                                                                        + .5f
                                                                        + dz * i / count)));
                            boolean blocked =
                                    path.stream()
                                            .anyMatch(
                                                    cell ->
                                                            buildings.stream()
                                                                    .anyMatch(
                                                                            b ->
                                                                                    cell.x()
                                                                                                    >= b
                                                                                                            .x()
                                                                                            && cell
                                                                                                            .x()
                                                                                                    < b
                                                                                                                    .x()
                                                                                                            + 6
                                                                                            && cell
                                                                                                            .z()
                                                                                                    >= b
                                                                                                            .z()
                                                                                            && cell
                                                                                                            .z()
                                                                                                    < b
                                                                                                                    .z()
                                                                                                            + 7));
                            if (blocked) continue;
                            for (var cell : path)
                                if (!roads.containsKey(cell)) level(cell.x(), cell.z(), edits);
                        }
                        var plot = economy.buyPlot(zone.id(), zone.type(), x, grade + 1, z);
                        if (plot == null) return;
                        ground.apply(edits);
                        return;
                    }
    }

    private boolean demand(int type) {
        long projects =
                economy.plots.stream().filter(p -> p.type() == type && p.building() == 0).count();
        if (type == 0) {
            long capacity =
                    buildings.stream()
                            .filter(b -> b.type() == 0)
                            .mapToInt(CityFrame.Building::capacity)
                            .sum();
            return capacity + projects * 4 < ecs.query(Household.class).size();
        }
        return projects == 0 && buildings.stream().noneMatch(b -> b.type() == type);
    }

    private void finishProjects() {
        for (var p : new ArrayList<>(economy.plots))
            if (p.building() == 0 && p.work() >= 8) {
                if (ground.playerOccupied(p.x(), p.y(), p.z(), 6, 7)) continue;
                for (int id : ecs.query(Household.class, Position.class)) {
                    var pos = ecs.get(id, Position.class);
                    if (pos.x > p.x() && pos.x < p.x() + 6 && pos.z > p.z() && pos.z < p.z() + 7) {
                        // Move construction workers onto the completed building's interior floor.
                        // Its aisle keeps their assigned stations clear; no enclosing wall is
                        // placed over them.
                        pos.x = p.x() + 2.5f;
                        pos.z = p.z() + 2.5f;
                        pos.y = p.y() + 1.01f;
                    }
                }
                for (int id : ecs.query(Mount.class, Position.class)) {
                    var hp = ecs.get(id, Position.class);
                    if (hp.x > p.x() && hp.x < p.x() + 6 && hp.z > p.z() && hp.z < p.z() + 7) {
                        var mount = ecs.get(id, Mount.class);
                        if (mount.rider < 0) ecs.get(-mount.rider, Household.class).horse = 0;
                        mount.rider = 0;
                        hp.x = p.x() + 2.5f;
                        hp.z = p.z() - .5f;
                        hp.y = p.y() + .01f;
                    }
                }
                ground.apply(StructureBlueprint.generate(p.type(), p.x(), p.y(), p.z()));
                var b =
                        new CityFrame.Building(
                                ++buildingIds,
                                p.zone(),
                                p.type(),
                                p.x(),
                                p.y(),
                                p.z(),
                                p.type() == 0 ? 4 : 16,
                                p.type() == 1 ? 80 : 0);
                buildings.add(b);
                economy.completed(p, b.id());
                for (int id : ecs.query(Household.class)) {
                    var h = ecs.get(id, Household.class);
                    if (h.job == -p.id()) {
                        h.job = 0;
                        var t = ecs.get(id, Travel.class);
                        t.target = -9999;
                        t.route.clear();
                    }
                }
            }
    }

    private String ride(int rider, int horse, Protocol.Pose p) {
        int current = riddenBy(rider);
        if (current != 0) {
            ecs.get(current, Mount.class).rider = 0;
            return "Dismounted";
        }
        var m = ecs.get(horse, Mount.class);
        var hp = ecs.get(horse, Position.class);
        if (m == null || m.rider != 0) return "Horse is already being ridden";
        if (Math.pow(hp.x - p.x(), 2) + Math.pow(hp.y - p.y(), 2) + Math.pow(hp.z - p.z(), 2) > 25)
            return "Move closer to the horse";
        m.rider = rider;
        return "Mounted horse: WASD to ride; H or right-click to dismount";
    }

    public int riddenBy(int rider) {
        for (int id : ecs.query(Mount.class))
            if (ecs.get(id, Mount.class).rider == rider) return id;
        return 0;
    }

    public void riderMoved(int rider, Protocol.Pose p) {
        int horse = riddenBy(rider);
        if (horse == 0) return;
        var hp = ecs.get(horse, Position.class);
        hp.x = p.x();
        hp.y = p.y() - .75f;
        hp.z = p.z();
        hp.yaw = p.yaw();
        hp.phase = p.walkPhase();
    }

    public void release(int rider) {
        int horse = riddenBy(rider);
        if (horse != 0) ecs.get(horse, Mount.class).rider = 0;
    }

    public CityFrame frame() {
        var rs = new ArrayList<CityFrame.Road>();
        roads.forEach((c, y) -> rs.add(new CityFrame.Road(c.x(), c.z(), y)));
        var cs = new ArrayList<CityFrame.Citizen>();
        for (int id : ecs.query(Household.class)) {
            var p = ecs.get(id, Position.class);
            var h = ecs.get(id, Household.class);
            var n = ecs.get(id, Needs.class);
            var t = ecs.get(id, Travel.class);
            cs.add(
                    new CityFrame.Citizen(
                            id,
                            h.name,
                            h.cohort,
                            p.x,
                            p.y,
                            p.z,
                            p.yaw,
                            p.phase,
                            n.hunger,
                            n.money,
                            h.home,
                            h.job,
                            h.horse,
                            t.activity));
        }
        var hs = new ArrayList<CityFrame.Horse>();
        for (int id : ecs.query(Mount.class)) {
            var p = ecs.get(id, Position.class);
            hs.add(
                    new CityFrame.Horse(
                            id, p.x, p.y, p.z, p.yaw, p.phase, ecs.get(id, Mount.class).rider));
        }
        return new CityFrame(config, elapsed, rs, zones, buildings, cs, hs, economy.state());
    }

    public static CityFrame load(Path file) throws IOException {
        if (file == null || !Files.exists(file)) return null;
        try (var in = new DataInputStream(Files.newInputStream(file))) {
            int magic = in.readInt();
            if (magic != 0x43495431 && magic != 0x43495432)
                throw new IOException("Invalid city save");
            return CityFrame.read(in, magic == 0x43495431);
        }
    }

    public void save(Path file) throws IOException {
        if (file == null) return;
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (var out = new DataOutputStream(Files.newOutputStream(tmp))) {
            out.writeInt(0x43495432);
            frame().write(out);
        }
        try {
            Files.move(
                    tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
