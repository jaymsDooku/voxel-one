package dev.jayms.net.city;

import dev.jayms.net.city.parcel.ParcelGenerator.Parcel;
import dev.jayms.net.city.parcel.ParcelPortfolio;
import dev.jayms.net.city.parcel.ZoneParceling;

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
    public static final String[] ZONES = {
        "Residential", "Commercial", "Industrial", "Agricultural"
    };

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
        public int target, railOrigin, railDestination;
        public int passingPoints;
        public double railCooldown;
        public double retryAt, mealUntil;
        public String activity = "Looking for home / work";
        public final ArrayDeque<Cell> route = new ArrayDeque<>();
        public boolean accessRoute;
        public boolean clearanceBack;
        public int laneCells;
        public boolean mountedLane;
        public List<Cell> lanePath=List.of();
        public final ArrayDeque<Integer> laneSources=new ArrayDeque<>();
        public void clearRoadLanes() {
            lanes.clear();laneSources.clear();lanePath=List.of();laneCells=0;clearanceBack=false;
        }
        public final ArrayDeque<RoadTraffic.Waypoint> lanes = new ArrayDeque<>();
    }

    public static final class Mount {
        public int rider;
    }

    public final RegionalPopulation population;
    public final Ecs ecs = new Ecs();
    private final Ground ground;
    private final Terrain terrain;
    private boolean migrateLanePavements = true;
    private final RoadSpacing roadSpacing = RoadSpacing.configured();
    private final GameConfig config;
    private final CityHarvesting harvesting;
    private Railway railway = new Railway();
    private final Map<Cell, Integer> roads = new LinkedHashMap<>();
    private final Map<Cell, Integer> roadTypes = new LinkedHashMap<>();
    private List<CityFrame.Zone> zones = new ArrayList<>();
    private StressGrid stressGrid;
    private final List<CityFrame.Building> buildings = new ArrayList<>();
    private double elapsed, accumulator, nextBuild;
    private int zoneIds, buildingIds, grade;
    private long marketDay = -1;
    private final Set<Integer> graduationReviews = new HashSet<>();
    private int marketBuildings = -1;
    private CityAddresses addresses = new CityAddresses(CityAddresses.empty());
    public final CityEconomy economy;
    private boolean founding = true, migrateMaterials;
    public final Agriculture agriculture;
    private final List<Aviation.Flight> flights = new ArrayList<>();
    private int flightIds;

    public CitySimulation(GameConfig config, Ground ground, Terrain terrain, CityFrame saved) {
        this(config, ground, terrain, saved, ProductionCatalog.cityGame());
    }

    public CitySimulation(
            GameConfig config,
            Ground ground,
            Terrain terrain,
            CityFrame saved,
            ProductionCatalog catalog) {
        this.config = config;
        population = new RegionalPopulation(saved == null ? RegionalPopulation.State.empty() : saved.population());
        this.ground = ground;
        this.terrain = terrain;
        harvesting =
                new CityHarvesting(
                        ground,
                        terrain,
                        (x, z) ->
                                roadContains(new Cell(x, z))
                                        || stressGrid != null && stressGrid.plotAt(x,z) >= 0
                                        || stressGrid == null && zones.stream()
                                                .anyMatch(
                                                        zone ->
                                                                zone.polygon()
                                                                        .contains(x + .5f, z + .5f))
                                        || buildings.stream()
                                                .anyMatch(
                                                        b ->
                                                                x >= b.x() - 2
                                                                        && x <= b.x() + StructureBlueprint.width(b.type()) + 2
                                                                        && z >= b.z() - 2
                                                                        && z <= b.z() + (b.type() == SpecialBuildings.AIRPORT ? Aviation.depth(Aviation.runways(b)) : StructureBlueprint.depth(b.type())) + 2));
        grade = Math.max(-26, Math.min(88, terrain.column(8, 24).height()));
        if (saved != null) {
            restore(saved);
            economy = new CityEconomy(ecs, saved.economy());
            economy.adopt(buildings);
            if (saved.economy().capital().equals(CityCapital.State.empty()))
                economy.capital.graduates.addAll(
                        saved.citizens().stream()
                                .skip(8)
                                .limit(4)
                                .map(CityFrame.Citizen::id)
                                .toList());
            boolean newGrid = stressGrid != null && saved.citizens().isEmpty()
                    && saved.buildings().isEmpty() && saved.economy().plots().isEmpty();
            if (newGrid) {
                seedFounders();
                seedGridSupplies();
                economy.capital.graduates.addAll(ecs.query(Household.class).stream().skip(8).limit(4).toList());
            }
            agriculture = new Agriculture(ecs, newGrid
                    ? Agriculture.State.migration(economy.resources.catalog) : saved.agriculture(), terrain);
            migrateMaterials = saved.economy().resources().equals(CityMaterials.State.empty());
            founding = false;
            return;
        }
        if (!config.city()) {
            economy = new CityEconomy(ecs, null, catalog);
            agriculture = new Agriculture(ecs, Agriculture.State.empty(), terrain);
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
        seedFounders();
        economy = new CityEconomy(ecs, null, catalog);
        agriculture = new Agriculture(ecs, Agriculture.State.empty(), terrain);
        // The founding settlement includes four graduates; education is independent of income.
        economy.capital.graduates.addAll(
                ecs.query(Household.class).stream().skip(8).limit(4).toList());
        if (catalog.agriculture()) {
            // Founding city infrastructure is free as before; every subsequent extension is paid.
            road(List.of(new Point(-10, 24), new Point(-64, 24)));
            road(List.of(new Point(8, 45), new Point(8, 64)));
            road(List.of(new Point(8, 43), new Point(88, 43)));
            zone(
                    2,
                    new Polygon(
                            List.of(
                                    new Point(-60, 26),
                                    new Point(4, 26),
                                    new Point(4, 41),
                                    new Point(-60, 41))));
            zone(
                    2,
                    new Polygon(
                            List.of(
                                    new Point(-60, 13),
                                    new Point(-10, 13),
                                    new Point(-10, 23),
                                    new Point(-60, 23))));
            zone(
                    3,
                    new Polygon(
                            List.of(
                                    new Point(12, 45),
                                    new Point(88, 45),
                                    new Point(88, 63),
                                    new Point(12, 63))));
            agriculture.initialize(economy, grade);
        }
        founding = false;
    }

    /** One finite starting stock: the paved grid has no nearby forest to bootstrap logging. */
    /** Explicit benchmark fixture expansion. Never runs in ordinary cities or on reload. */
    public String developStressGrid() {
        if (stressGrid == null) throw new IllegalArgumentException("Load Stress Test Grid first.");
        agriculture.initialize(economy, grade);
        int[] targets = {192, 80, 80, 48}, starts = {0, 400000, 600000, 800000};
        int added = 0;
        for (int type = 0; type < 4; type++) {
            final int t = type;
            int count = (int)buildings.stream().filter(b -> b.type() == t).count();
            for (int rank = starts[type]; count < targets[type] && rank < starts[type] + 8192; rank++) {
                if (buildings.size() + economy.plots.stream().filter(p -> p.building() == 0).count() >= 512) break;
                var zone = stressGrid.zone(StressGrid.indexForRank(rank));
                var v = zone.polygon().vertices().get(0);
                int x = (int)v.x()+1, z = (int)v.z()+1, y = grade+1;
                if (buildings.stream().anyMatch(b -> b.zone() == zone.id())
                        || economy.plots.stream().anyMatch(p -> p.zone() == zone.id())
                        || ground.occupied(x,y,z,StructureBlueprint.width(type),StructureBlueprint.depth(type))) continue;
                // Check every blueprint edit, including porches and underground factory work.
                var blueprint = StructureBlueprint.generate(type,kindForBenchmark(type),x,y,z);
                boolean clear = true;
                for (var edit : blueprint)
                    if (ground.type(edit.x(),edit.y(),edit.z()) != terrain.block(edit.x(),edit.y(),edit.z())) clear = false;
                if (!clear) continue;
                int kind = type == 3 ? CityMaterials.FARM : type;
                ground.apply(blueprint);
                var b = new CityFrame.Building(++buildingIds,zone.id(),type,x,y,z,type == 0 ? 4 : type == 3 ? 2 : 16,0);
                buildings.add(b);
                economy.adopt(List.of(b));
                if (type != 0) {
                    var firm = economy.companies().stream().filter(c -> c.kind == kind).findFirst().orElseThrow();
                    var property = economy.property(b.id());
                    economy.properties.set(economy.properties.indexOf(property),new CityEconomy.Property(
                            b.id(),CityEconomy.COMPANY,firm.id,firm.id,property.price(),property.rent()));
                    economy.businesses.open(b.id(),firm.id);
                }
                agriculture.completed(b,economy);
                count++; added++;
            }
        }
        var homes = buildings.stream().filter(b -> b.type() == 0).toList();
        int people = ecs.query(Household.class).size(), newcomers = 0;
        while (people < 128 && !homes.isEmpty()) {
            var home = homes.get(people % homes.size());
            if (occupants(home.id(),true) >= home.capacity()) break;
            int id = ecs.create();
            var h = new Household("Grid resident " + id, people % 3);
            ecs.put(id,Household.class,h);
            ecs.put(id,Position.class,new Position(home.x()+2.5f,home.y()+1.01f,home.z()+2.5f));
            ecs.put(id,Needs.class,new Needs(72));
            ecs.put(id,Travel.class,new Travel());
            ecs.put(id,CitizenLife.class,CitizenLife.founder(people % 12));
            if (economy.house(id,home)) h.home = home.id();
            people++; newcomers++;
        }
        migrateLanePavements = true;
        return "Added " + added + " buildings and " + newcomers + " residents. Save current to keep.";
    }

    private static int kindForBenchmark(int type) { return type == 3 ? CityMaterials.FARM : type; }

    private void seedGridSupplies() {
        for (var firm : economy.companies()) {
            if (firm.kind==CityEconomy.DEVELOPER) {
                for (int type=0; type<3; type++) for (var amount:CityMaterials.requirements(type,type))
                    economy.resources.add(CityEconomy.COMPANY,firm.id,amount.material(),amount.units()*8);
            } else if (CityMaterials.farmer(firm.kind)) {
                for (var amount:CityMaterials.requirements(3,firm.kind))
                    economy.resources.add(CityEconomy.COMPANY,firm.id,amount.material(),amount.units()*2);
            }
        }
    }

    /** The grid uses the same founding households and horses as an ordinary city. */
    private void seedFounders() {
        for (int i = 0; i < 12; i++) {
            int id = ecs.create();
            ecs.put(
                    id,
                    Position.class,
                    new Position(9.5f + i * (Math.max(roadSpacing.pedestrians(), roadSpacing.mounted()) + .1f),
                            grade + 1.01f, 25.1f));
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
            ecs.put(id, CitizenLife.class, CitizenLife.founder(i));
        }
        for (int i = 0; i < 6; i++) {
            int id = ecs.create();
            ecs.put(id, Position.class, new Position(8.5f, grade + 1.01f, 21.8f - i * (roadSpacing.mounted() + .1f)));
            ecs.put(id, Mount.class, new Mount());
        }
    }

    private void restore(CityFrame f) {
        stressGrid = f.stressGrid();
        if (stressGrid != null) { grade = stressGrid.grade(); terrain.stressGrid(stressGrid); }
        railway = new Railway(f.railway());
        var savedAddresses=f.addresses();
        if(savedAddresses.streets().isEmpty() && !f.roads().isEmpty())
            savedAddresses=CityAddresses.migrate(f.roads(),f.buildings());
        addresses = new CityAddresses(new CityAddresses.State(savedAddresses.streets(),savedAddresses.addresses(),
                f.addresses().roadFootprints().isEmpty()?RoadOwnership.infer(new CityFrame(f.config(),f.elapsed(),f.roads(),f.zones(),
                        f.buildings(),f.citizens(),f.horses(),f.economy(),savedAddresses,f.agriculture(),f.population(),f.aviation(),f.railway(),f.stressGrid()))
                        :f.addresses().roadFootprints()));
        elapsed = f.elapsed();
        flights.addAll(f.aviation().flights());
        flightIds = flights.stream().mapToInt(Aviation.Flight::id).max().orElse(0);
        nextBuild = elapsed + 2;
        for (var r : f.roads()) {
            var cell = new Cell(r.x(), r.z());
            roads.put(cell, r.y());
            roadTypes.put(cell, r.type());
        }
        if (stressGrid == null) zones.addAll(f.zones());
        else zones = stressGrid.zones();
        buildings.addAll(f.buildings());
        zoneIds = stressGrid == null ? zones.stream().mapToInt(CityFrame.Zone::id).max().orElse(0) : StressGrid.COUNT;
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
            var life = new CitizenLife(c.age(), c.gender(), c.education());
            life.study = c.study(); life.spouse = c.spouse(); life.mother = c.mother();
            life.father = c.father(); life.school = c.school(); life.lastBirthAge = c.lastBirthAge();
            ecs.put(c.id(), CitizenLife.class, life);
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

    public CitizenLife life(int id) {
        var value = ecs.get(id, CitizenLife.class);
        if (value == null) {
            var h = ecs.get(id, Household.class);
            value = new CitizenLife(24 + (id - 1) % 8,
                    id % 2 != 0 ? CitizenLife.Gender.FEMALE : CitizenLife.Gender.MALE,
                    h.cohort == 0 ? CitizenLife.Education.NONE : h.cohort == 1 ? CitizenLife.Education.TECHNICAL : CitizenLife.Education.UNIVERSITY);
            ecs.put(id, CitizenLife.class, value);
        }
        return value;
    }

    /** Career gates apply to every assignment path, including farms and mobile crews. */
    public boolean eligible(int job, int id) {
        var l = life(id);
        if (!l.adult() || job == 0) return false;
        var b = building(job);
        if (b != null && b.type() == SpecialBuildings.EXCHANGE)
            return l.education == CitizenLife.Education.UNIVERSITY;
        if (b != null && SpecialBuildings.special(b.type())) return false;
        if (b != null && b.type() == 1)
            return l.education.ordinal() >= CitizenLife.Education.SECONDARY.ordinal();
        var firm = economy.company(employer(job));
        if (job > 0 && firm != null && economy.resources.catalog.recipes(firm.kind).stream()
                .anyMatch(ProductionCatalog.Recipe::requiresFactory))
            return l.education == CitizenLife.Education.TECHNICAL;
        return true;
    }

    private int schoolKind(CitizenLife l) {
        if (l.age >= 5 && l.age < 12 && l.education == CitizenLife.Education.NONE) return 1;
        if (l.age >= 12 && l.age < 18 && l.education == CitizenLife.Education.PRIMARY) return 2;
        if (l.age >= 18 && l.age < 22 && l.education == CitizenLife.Education.SECONDARY) return 3;
        return -1;
    }

    private void lifeTick(float dt) {
        var ids = new ArrayList<>(ecs.query(Household.class));
        for (int id : ids) {
            var l = life(id);
            int before = schoolKind(l);
            l.age += dt / (config.daySeconds() * CitizenLife.DAYS_PER_YEAR);
            int kind = schoolKind(l);
            if (kind != before) { l.study = 0; l.school = 0; }
            var h = ecs.get(id, Household.class);
            if (h.job != 0 && !eligible(h.job, id)) {
                h.job = 0;
                var t = ecs.get(id, Travel.class); t.target = -9999; t.route.clear();t.clearRoadLanes();
            }
            if (l.education == CitizenLife.Education.UNIVERSITY) economy.capital.graduates.add(id);
            else economy.capital.graduates.remove(id);
            var school = building(l.school);
            if (l.school != 0 && school == null) l.study = 0;
            if (school == null || kind < 0 || (SpecialBuildings.kind(school.type()) != kind
                    && !(kind == 3 && SpecialBuildings.kind(school.type()) == 5))) l.school = 0;
            if (kind >= 0 && l.school == 0) {
                final int stage = kind;
                // Students choose the closest open place; both tertiary paths require secondary.
                var p = ecs.get(id, Position.class);
                l.school = buildings.stream().filter(b -> SpecialBuildings.special(b.type())
                        && b.type() != SpecialBuildings.EXCHANGE
                        && (SpecialBuildings.kind(b.type()) == stage
                            || stage == 3 && SpecialBuildings.kind(b.type()) == 5))
                        .filter(b -> ids.stream().filter(other -> life(other).school == b.id()).count() < b.capacity())
                        .min(Comparator.comparingDouble((CityFrame.Building b) ->
                            Math.pow(b.x() - p.x, 2) + Math.pow(b.z() - p.z, 2)).thenComparingInt(CityFrame.Building::id))
                        .map(CityFrame.Building::id).orElse(0);
            }
            if (l.age <= 18 || l.spouse != 0) continue;
            for (int partner : ids) {
                var other = life(partner);
                if (partner == id || other.age <= 18 || other.spouse != 0 || other.gender == l.gender
                        || partner == l.mother || partner == l.father || id == other.mother || id == other.father
                        || l.mother != 0 && l.mother == other.mother || l.father != 0 && l.father == other.father) continue;
                l.spouse = partner; other.spouse = id; break;
            }
        }
        for (int id : ids) {
            var l = life(id); var h = ecs.get(id, Household.class);
            if (l.spouse != 0 && h.home != 0 && agriculture.company(id) == 0
                    && agriculture.company(l.spouse) == 0 && ecs.get(l.spouse, Household.class).home != h.home) {
                var home = building(h.home);
                var dependents = ids.stream().filter(child -> life(child).mother == l.spouse
                        && !life(child).adult()).toList();
                if (home != null && occupants(home.id(), true) + 1 + dependents.size() < home.capacity()
                        && economy.house(l.spouse, home)) {
                    economy.contracts.removeIf(c -> c.partyKind() == CityEconomy.CITIZEN
                            && c.party() == l.spouse && !c.sale() && c.building() != home.id());
                    ecs.get(l.spouse, Household.class).home = h.home;
                    for (int child : dependents) ecs.get(child, Household.class).home = h.home;
                }
            }
            if (l.gender != CitizenLife.Gender.FEMALE || l.age <= 18 || l.age >= 45
                    || l.spouse == 0 || life(l.spouse).age <= 18 || l.age - l.lastBirthAge < 2
                    || h.home == 0 || ecs.get(l.spouse, Household.class).home != h.home
                    || ecs.query(Household.class).size() >= 128) continue;
            var home = building(h.home);
            if (home == null || occupants(home.id(), true) >= home.capacity()) continue;
            int child = ecs.create(); var p = ecs.get(id, Position.class);
            ecs.put(child, Position.class, new Position(p.x, p.y, p.z));
            var household = new Household("Child " + child, h.cohort); household.home = h.home;
            ecs.put(child, Household.class, household);
            ecs.put(child, Needs.class, new Needs(0)); ecs.put(child, Travel.class, new Travel());
            var newborn = new CitizenLife(0, child % 2 == 0 ? CitizenLife.Gender.FEMALE : CitizenLife.Gender.MALE,
                    CitizenLife.Education.NONE);
            newborn.mother = id; newborn.father = l.spouse;
            ecs.put(child, CitizenLife.class, newborn); l.lastBirthAge = l.age;
        }
    }

    public void advance(double dt) {
        if(migrateLanePavements) {var edits=new ArrayList<Protocol.Edit>();pave(edits);ground.apply(edits);migrateLanePavements=false;}
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
        population.advance(dt, config.daySeconds());
        economy.logisticsSites(buildings);
        economy.logisticsClock(elapsed * 24 / config.daySeconds());
        if (agriculture.pending()) agriculture.initialize(economy, grade);
        agriculture.tick(dt * 24 / config.daySeconds());
        if (elapsed >= nextBuild) {
            construct();
            nextBuild = elapsed + 4;
        }
        if (migrateMaterials) {
            for (var b : buildings)
                if (b.type() == 1 && b.stock() > 0) {
                    var p = economy.property(b.id());
                    if (p != null && p.operator() != 0)
                        economy.resources.add(
                                0,
                                p.operator(),
                                CityMaterials.FOOD,
                                b.stock() * CityMaterials.UNIT);
                }
            for (var b : new ArrayList<>(buildings)) if (b.type() == 2) replaceStock(b, 0);
            migrateMaterials = false;
        }
        economy.ensureIndustries();
        economy.capital.ensureCompanies();
        economy.businesses.beginDay(config.time(elapsed).day());
        economy.rent(dt / config.daySeconds());
        finishProjects();
        boolean marketReview = marketDay != config.time(elapsed).day();
        if (marketReview || marketBuildings != buildings.size()) {
            marketDay = config.time(elapsed).day();
            marketBuildings = buildings.size();
            economy.priceProperties(buildings);
        }
        railTick(dt);
        lifeTick(dt);

        assign();
        aviationTick(dt);
        if (marketReview || hasUnpaidWorkers() || !graduationReviews.isEmpty()) chooseJobs(marketReview);
        for (int id : ecs.query(Position.class, Household.class, Needs.class, Travel.class)) {
            var p = ecs.get(id, Position.class);
            var h = ecs.get(id, Household.class);
            var n = ecs.get(id, Needs.class);
            var t = ecs.get(id, Travel.class);
            if (flights.stream().anyMatch(f -> f.citizen() == id)) continue;
            var time = config.time(elapsed);
            if (railway.aboard(id)) { t.activity="Riding steam train"; continue; }
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
            boolean working = onShift(id, time);
            var life = life(id);
            boolean studying = life.school != 0 && time.hour() >= 8 && time.hour() < 14;
            float mealThreshold = working || studying ? 65 : 85;
            int mealShop = time.shopsOpen() && n.hunger < mealThreshold ? cheapestMeal(n, time) : 0;
            boolean eating = mealShop != 0;
            int target = eating ? mealShop : studying ? life.school : working && h.job != 0 ? h.job : h.home;
            if (target == 0) {
                t.target = 0;
                t.route.clear();t.clearRoadLanes();
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
            if (studying && !eating) t.activity = "Going to " + SpecialBuildings.name(building(life.school).type());
            if (target != t.target) {
                t.target = target;
                t.accessRoute=false;
                t.route.clear();t.clearRoadLanes();
                t.railOrigin=t.railDestination=0;
                var b = workplace(target);
                if (b != null && elapsed >= t.railCooldown && h.horse == 0) planRail(p,b,t);
                if (b != null && (b.id()>=CityMaterials.YARD
                        ? Math.hypot(p.x-yardStationX(b,id),p.z-26.5f)>.15
                        : !(p.x > b.x() && p.x < b.x() + 6 && p.z > b.z() && p.z < b.z() + 7)))
                    journey(id,p,t.railOrigin!=0 ? building(t.railOrigin) : b,t);
            }
            if (t.railOrigin != 0) {
                var station=building(t.railOrigin); var destination=building(t.railDestination);
                if(station==null||destination==null||!railway.served(station,buildings)||!railway.connected(station,destination)) {
                    t.railOrigin=t.railDestination=0;t.target=0;t.route.clear();t.clearRoadLanes();continue;
                }
                if(!t.route.isEmpty()) {t.activity="Walking to rail station";travel(id,p,h,t,dt);continue;}
                if(Math.hypot(p.x-station.x()-2.5f,p.z-station.z()-2.5f)>5) {
                    t.railOrigin=t.railDestination=0;t.target=0;t.railCooldown=elapsed+15;continue;
                }
                t.activity=railway.board(id,t.railOrigin,t.railDestination)?"Boarding steam train":"Waiting at rail station";
                continue;
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
                if (project != null && project.type() == 3 && !ready(project)) {
                    var supplies = economy.resources.project(project.id());
                    long soil =
                            supplies.materials().stream()
                                    .filter(a -> a.material() == Blocks.DIRT)
                                    .mapToLong(CityMaterials.Amount::units)
                                    .sum();
                    if (economy.resources.available(0, project.developer(), Blocks.DIRT) < soil
                            && economy.wage(
                                    project.developer(),
                                    hours * economy.labourRate(project.developer(), 2.1),
                                    n)) {
                        var prod = economy.resources.production(project.developer());
                        double progress = prod.progress() + hours * 64;
                        int digs = (int) progress;
                        progress -= digs;
                        long dug = 0;
                        for (int i = 0;
                                i < digs
                                        && economy.resources.available(
                                                        0, project.developer(), Blocks.DIRT)
                                                < soil;
                                i++)
                            if (harvesting.harvest(Blocks.DIRT)) {
                                economy.resources.add(
                                        0, project.developer(), Blocks.DIRT, CityMaterials.UNIT);
                                dug++;
                            }
                        economy.resources.production(
                                project.developer(), progress, dug, 0, "Preparing farm soil");
                        t.activity = "Preparing family farm soil";
                    } else t.activity = "Farm needs building materials";
                } else if (project != null
                        && economy.resources.reserve(project)
                        && economy.wage(
                                project.developer(),
                                hours * economy.labourRate(project.developer(), 1.8),
                                n)) {
                    economy.work(project.id(), dt);
                    t.activity =
                            project.type() == 3 ? "Building family farm" : "Building for developer";
                } else t.activity = "Builder cannot afford wages";
            } else if ((b.type() == 2 || b.type() == 3) && working && !eating) {
                int company = employer(h.job);
                var firm = economy.company(company);
                if (firm == null) continue;
                int account = economy.account(company);
                t.activity =
                        firm.kind == CityEconomy.MINE
                                ? "Working in mine"
                                : "Working: " + economy.resources.catalog.businesses().sector(firm.kind);
                double pay = hours * economy.labourRate(company, 1.8 + h.cohort * .3);
                if (!economy.wage(company, pay, n)) {
                    economy.businesses.missedWage(account);
                    t.activity = "Employer cannot afford wages";
                    continue;
                }
                economy.businesses.wage(account, pay, hours);
                if (b.type() == 3) agriculture.work(b, economy, hours, ground);
                else if (!agriculture.enabled() || !CityMaterials.farmer(firm.kind))
                    harvesting.work(economy, firm, hours);
                if (b.id() < CityMaterials.YARD)
                    replaceStock(
                            b,
                            (int)
                                    (economy.resources.available(
                                                    0,
                                                    company,
                                                    economy.resources.catalog.output(firm.kind))
                                            / CityMaterials.UNIT));
            } else if (studying && b.id() == life.school && !eating) {
                t.activity = "Studying at " + SpecialBuildings.name(b.type());
                life.study += hours / (6 * CitizenLife.DAYS_PER_YEAR);
                double required = SpecialBuildings.kind(b.type()) == 1 ? 6 : SpecialBuildings.kind(b.type()) == 2 ? 5 : 3;
                if (life.study + 1e-6 >= required) {
                    life.education = switch (SpecialBuildings.kind(b.type())) {
                        case 1 -> CitizenLife.Education.PRIMARY;
                        case 2 -> CitizenLife.Education.SECONDARY;
                        case 3 -> CitizenLife.Education.UNIVERSITY;
                        default -> CitizenLife.Education.TECHNICAL;
                    };
                    life.study = 0; life.school = 0;
                    if (life.adult()) graduationReviews.add(id);
                    if (life.education == CitizenLife.Education.UNIVERSITY) economy.capital.graduates.add(id);
                }
            } else if (b.type() == SpecialBuildings.EXCHANGE && working && !eating) {
                double salary = hours * exchangeLabourRate(economy.capital.graduates.contains(id));
                if (economy.budget >= salary) {
                    economy.budget -= salary;
                    n.money += (float) salary;
                    t.activity =
                            economy.capital.graduates.contains(id)
                                    ? "Exchange analyst (graduate)"
                                    : "Exchange office support";
                } else t.activity = "Exchange cannot afford wages";
            } else if (b.type() == 1 && working && !eating) {
                t.activity =
                        economy.businessWage(
                                        b.id(),
                                        hours
                                                * economy.labourRate(
                                                        economy.property(b.id()).operator(), 1.8),
                                        hours,
                                        n)
                                ? "Working in shop"
                                : "Employer cannot afford wages";
            } else if (b.type() == 1 && eating) {
                var food =
                        shopReady(b, time) && n.hunger < 95 && b.stock() > 0
                                ? economy.buyMeal(id, b.id(), mealNutrition(n), b.stock())
                                : null;
                if (food != null) {
                    replaceStock(b, b.stock() - food.portions());
                    t.target = -9999;
                    t.mealUntil = elapsed + Math.min(3, config.daySeconds() / 240);
                    t.activity = "Eating at shop";
                } else {
                    t.activity =
                            b.stock() == 0
                                    ? "Shop needs farm deliveries"
                                    : !shopReady(b, time)
                                            ? "Waiting for shop staff"
                                            : "Cannot afford food";
                    t.target = -9999;
                }
            } else
                t.activity =
                        time.period() == CityTime.Period.NIGHT
                                ? "Sleeping at home"
                                : time.period() == CityTime.Period.MORNING
                                        ? "Morning at home"
                                        : working ? "Looking for work" : "Relaxing at home";
        }
        restock();
        refreshExchange();
    }

    private boolean onShift(int citizen, CityTime time) {
        if (!life(citizen).adult() || life(citizen).school != 0 && time.hour() >= 8 && time.hour() < 14) return false;
        var h = ecs.get(citizen, Household.class);
        var b = building(h.job);
        if (b == null || b.type() != 1) return time.period() == CityTime.Period.WORKDAY;
        var staff =
                ecs.query(Household.class).stream()
                        .filter(id -> ecs.get(id, Household.class).job == b.id())
                        .toList();
        int shift = staff.indexOf(citizen) % 2;
        return CityBusinesses.shopShift(time.hour(), shift);
    }

    private boolean shopReady(CityFrame.Building b, CityTime time) {
        var property = economy.property(b.id());
        if (!time.shopsOpen() || property == null || property.operator() == 0) return false;
        var firm = economy.company(property.operator());
        if (firm == null || firm.cash <= 0) return false;
        return ecs.query(Household.class, Position.class).stream()
                .anyMatch(
                        id -> {
                            var h = ecs.get(id, Household.class);
                            var p = ecs.get(id, Position.class);
                            return h.job == b.id()
                                    && onShift(id, time)
                                    && p.x > b.x()
                                    && p.x < b.x() + 6
                                    && p.z > b.z()
                                    && p.z < b.z() + 7;
                        });
    }

    private void replaceStock(CityFrame.Building b, int stock) {
        int i = buildings.indexOf(b);
        if (i >= 0)
            buildings.set(
                    i,
                    new CityFrame.Building(
                            b.id(), b.zone(), b.type(), b.x(), b.y(), b.z(), b.capacity(), stock));
    }

    private int mealNutrition(Needs needs) {
        return (int) Math.max(1, Math.min(35, Math.ceil(100 - needs.hunger)));
    }

    private int cheapestMeal(Needs needs, CityTime time) {
        return buildings.stream()
                .filter(b -> b.type() == 1 && b.stock() > 0 && shopReady(b, time))
                .filter(
                        b ->
                                economy.cheapestFood(
                                                economy.property(b.id()).operator(),
                                                needs.money,
                                                mealNutrition(needs), b.stock())
                                        != null)
                .min(
                        Comparator.comparingDouble(
                                        (CityFrame.Building b) ->
                                                economy.cheapestFood(
                                                                economy.property(b.id()).operator(),
                                                                needs.money,
                                                                mealNutrition(needs), b.stock())
                                                        .price())
                                .thenComparingInt(CityFrame.Building::id))
                .map(CityFrame.Building::id)
                .orElse(0);
    }

    private long foodStock(int company) {
        return economy.resources.catalog.food().stream()
                .mapToLong(id -> economy.resources.available(0, company, id) / CityMaterials.UNIT)
                .sum();
    }

    private void restock() {
        for (var b : new ArrayList<>(buildings))
            if (b.type() == 1 && b.stock() < 80) {
                var p = economy.property(b.id());
                if (p == null || p.operator() == 0) continue;
                long allocated =
                        buildings.stream()
                                .filter(
                                        shop ->
                                                shop.type() == 1
                                                        && economy.property(shop.id()) != null
                                                        && economy.property(shop.id()).operator()
                                                                == p.operator())
                                .mapToLong(CityFrame.Building::stock)
                                .sum();
                if (agriculture.enabled()) {
                    economy.restockFood(p.operator(), allocated + 16);
                    if (foodStock(p.operator()) > allocated) replaceStock(b, b.stock() + 1);
                } else if (economy.purchase(
                        p.operator(), CityMaterials.FOOD, (allocated + 1) * CityMaterials.UNIT))
                    replaceStock(b, b.stock() + 1);
            }
        for (var p : economy.plots) if (p.building() == 0) economy.supply(p);
    }

    private int employer(int job) {
        if (job >= CityMaterials.YARD) return job - CityMaterials.YARD;
        var p = economy.property(job);
        return p == null ? 0 : p.operator();
    }

    private boolean ready(CityEconomy.Plot plot) {
        var p = economy.resources.project(plot.id());
        return p != null && p.reserved();
    }

    private void assign() {
        assignExchange();

        // Retire the prototype farm's off-plot job when the saved default game upgrades.
        if (agriculture.enabled())
            for (int id : ecs.query(Household.class)) {
                var h = ecs.get(id, Household.class);
                var company = economy.company(employer(h.job));
                var b = building(h.job);
                if (agriculture.company(id) == 0
                        && company != null
                        && CityMaterials.farmer(company.kind)
                        && (b == null || b.type() != 3)) {
                    h.job = 0;
                    var travel = ecs.get(id, Travel.class);
                    travel.target = -9999;
                    travel.route.clear();
                    travel.clearRoadLanes();
                }
            }
        for (var b :
                buildings.stream()
                        .filter(b -> b.type() != 0 && economy.property(b.id()) != null)
                        .sorted(
                                Comparator.comparingDouble(
                                                (CityFrame.Building b) ->
                                                        b.type() == 1
                                                                ? economy.property(b.id()).price()
                                                                : economy.property(b.id()).rent())
                                        .thenComparingInt(CityFrame.Building::id))
                        .toList()) economy.operate(b);
        assignFarmers();
        // Mobile crews can harvest before their own premises exist, avoiding a supply deadlock.
        for (var firm : economy.companies())
            if (firm.kind >= 2 && IndustrialProgression.unlocked(economy.resources, firm.kind)
                    && (!agriculture.enabled() || !CityMaterials.farmer(firm.kind))) {
                int workplace =
                        buildings.stream()
                                .filter(
                                        b -> {
                                            var p = economy.property(b.id());
                                            return p != null && p.operator() == firm.id;
                                        })
                                .mapToInt(CityFrame.Building::id)
                                .findFirst()
                                .orElse(CityMaterials.YARD + firm.id);
                if (workplace >= CityMaterials.YARD
                        && !economy.resources.catalog.recipes(firm.kind).isEmpty()
                        && economy.resources.catalog.recipes(firm.kind).stream()
                                .allMatch(ProductionCatalog.Recipe::requiresFactory)) continue;
                var crew =
                        ecs.query(Household.class).stream()
                                .filter(id -> employer(ecs.get(id, Household.class).job) == firm.id)
                                .toList();
                if (!crew.isEmpty()) {
                    for (int id : crew) {
                        var h = ecs.get(id, Household.class);
                        if (h.job != workplace) {
                            h.job = workplace;
                            var t = ecs.get(id, Travel.class);
                            t.target = -9999;
                            t.route.clear();t.clearRoadLanes();
                        }
                    }
                    continue;
                }
                for (int id : ecs.query(Household.class)) {
                    var h = ecs.get(id, Household.class);
                    if (agriculture.company(id) != 0) continue;
                    int current = employer(h.job);
                    if (!canPayJob(workplace, id)) continue;
                    // A new graduate workplace may recruit from a shop while leaving
                    // one worker there; eligible graduates need not wait for unemployment.
                    if (h.job == 0
                            || h.job > 0
                                    && h.job < CityMaterials.YARD
                                    && current != 0
                                    && economy.company(current).kind >= 2
                                    && occupants(h.job, false) > 1
                            || current != 0
                                    && economy.resources.catalog.recipes(firm.kind).stream()
                                            .anyMatch(ProductionCatalog.Recipe::requiresFactory)
                                    && building(h.job) != null
                                    && building(h.job).type() == 1
                                    && occupants(h.job, false) > 1
                                    && careerRank(workplace, id) > careerRank(h.job, id)
                                    && jobRate(workplace, id) >= jobRate(h.job, id)) {
                        h.job = workplace;
                        var t = ecs.get(id, Travel.class);
                        t.target = -9999;
                        t.route.clear();t.clearRoadLanes();
                        break;
                    }
                }
            }
        for (var b : buildings)
            if (b.type() == 1 && economy.property(b.id()).operator() != 0)
                for (int id : ecs.query(Household.class)) {
                    if (occupants(b.id(), false) >= 2) break;
                    var h = ecs.get(id, Household.class);
                    if (agriculture.company(id) != 0) continue;
                    int company = employer(h.job);
                    if (!canPayJob(b.id(), id)) continue;
                    if (h.job == 0
                            || h.job > 0
                                    && h.job < CityMaterials.YARD
                                    && company != 0
                                    && economy.company(company).kind >= 2
                                    && occupants(h.job, false) > 1) {
                        h.job = b.id();
                        var t = ecs.get(id, Travel.class);
                        t.target = -9999;
                        t.route.clear();t.clearRoadLanes();
                    }
                }
        for (var plot : economy.plots)
            if (plot.building() == 0 && ready(plot)) {
                for (int id : ecs.query(Household.class)) {
                    if (occupants(-plot.id(), false) >= 2) break;
                    var h = ecs.get(id, Household.class);
                    if (agriculture.company(id) != 0) continue;
                    if (!canPayJob(-plot.id(), id)) continue;
                    if (h.job == 0
                            || h.job > 0
                                    && h.job < CityMaterials.YARD
                                    && employer(h.job) != 0
                                    && economy.company(employer(h.job)).kind >= 2
                                    && occupants(h.job, false) > 1) {
                        h.job = -plot.id();
                        var t = ecs.get(id, Travel.class);
                        t.target = -9999;
                        t.route.clear();t.clearRoadLanes();
                    }
                }
            }
        for (int id : ecs.query(Household.class)) {
            var h = ecs.get(id, Household.class);
            if (agriculture.company(id) != 0) continue;
            var l = life(id);
            if (!l.adult() && l.mother != 0) {
                h.home = ecs.get(l.mother, Household.class).home;
                var parent = ecs.get(l.mother, Needs.class);
                var needs = ecs.get(id, Needs.class);
                float grant = (float)Math.min(Math.max(0, 4 - needs.money), Math.min(parent.money, .02));
                parent.money -= grant; needs.money += grant;
                continue;
            }
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
                for (var b :
                        buildings.stream()
                                .filter(b -> b.type() == 0)
                                .sorted(
                                        Comparator.comparingDouble(
                                                        (CityFrame.Building b) ->
                                                                economy.housingCost(id, b.id()))
                                                .thenComparingInt(CityFrame.Building::id))
                                .toList())
                    if (b.type() == 0
                            && occupants(b.id(), true) < b.capacity()
                            && economy.house(id, b)) {
                        h.home = b.id();
                        break;
                    }
            }
            if (h.home != 0)
                economy.purchase(CityEconomy.CITIZEN, id, Blocks.WOOD, CityMaterials.UNIT);
            if (h.job == 0) {
                for (var plot : economy.plots)
                    if (plot.building() == 0
                            && ready(plot)
                            && occupants(-plot.id(), false) < 2
                            && canPayJob(-plot.id(), id)) {
                        h.job = -plot.id();
                        break;
                    }
                if (h.job == 0)
                    for (var b : buildings)
                        if (b.type() != 0 && !SpecialBuildings.special(b.type())
                                && economy.property(b.id()).operator() != 0
                                && occupants(b.id(), false) < (b.type() == 1 ? 2 : b.capacity())
                                && canPayJob(b.id(), id)) {
                            h.job = b.id();
                            break;
                        }
            }
        }
    }

    private record LabourQuote(int company, double reference) {}

    private double jobRate(int job, int citizen) { return jobRate(job,citizen,null); }

    private double jobRate(int job, int citizen, Map<LabourQuote,Double> quotes) {
        var workplace = building(job);
        if (workplace != null && workplace.type() == SpecialBuildings.EXCHANGE)
            return exchangeLabourRate(economy.capital.graduates.contains(citizen));
        int cohort = ecs.get(citizen, Household.class).cohort;
        var plot = job < 0 ? economy.project(-job) : null;
        int company = plot == null ? employer(job) : plot.developer();
        if (company == 0) return 0;
        var b = building(job);
        double reference = plot != null || b != null && b.type() == 1 ? 1.8 : 1.8 + cohort * .3;
        return quotes == null ? economy.labourRate(company,reference)
                : quotes.computeIfAbsent(new LabourQuote(company,reference),
                        quote -> economy.labourRate(quote.company(),quote.reference()));
    }

    private boolean canPayJob(int job, int citizen) { return canPayJob(job,citizen,null); }

    private boolean canPayJob(int job, int citizen, Map<LabourQuote,Double> quotes) {
        if (!eligible(job, citizen)) return false;
        var workplace = building(job);
        if (workplace != null && workplace.type() == SpecialBuildings.EXCHANGE)
            return economy.budget >= jobRate(job, citizen, quotes);
        var plot = job < 0 ? economy.project(-job) : null;
        var firm = economy.company(plot == null ? employer(job) : plot.developer());
        return firm != null && firm.cash >= jobRate(job, citizen, quotes);
    }

    private boolean hasUnpaidWorkers() {
        return ecs.query(Household.class).stream()
                .anyMatch(
                        id -> {
                            var h = ecs.get(id, Household.class);
                            var workplace = building(h.job);
                            return h.job != 0
                                    && (workplace == null || !SpecialBuildings.special(workplace.type())
                                            || workplace.type() == SpecialBuildings.EXCHANGE)
                                    && agriculture.company(id) == 0
                                    && !canPayJob(h.job, id);
                        });
    }

    /** Paid crews review daily; unpaid workers can seek a funded offer on any tick. */
    private void chooseJobs(boolean reviewPaid) {
        var offers = new LinkedHashMap<Integer, Integer>();
        for (var plot : economy.plots)
            if (plot.building() == 0 && ready(plot)) offers.put(-plot.id(), 2);
        for (var b : buildings)
            if (b.type() == SpecialBuildings.EXCHANGE) offers.put(b.id(), 4);
            else if (b.type() != 0
                    && economy.property(b.id()) != null
                    && economy.property(b.id()).operator() != 0)
                offers.put(b.id(), b.type() == 1 ? 2 : b.capacity());
        for (var firm : economy.companies()) {
            if (firm.kind < 2 || !IndustrialProgression.unlocked(economy.resources, firm.kind)
                    || agriculture.enabled() && CityMaterials.farmer(firm.kind)) continue;
            int yard = CityMaterials.YARD + firm.id;
            if (buildings.stream()
                            .noneMatch(b -> economy.property(b.id()) != null
                                    && economy.property(b.id()).operator() == firm.id)
                    && economy.resources.catalog.recipes(firm.kind).stream()
                            .noneMatch(ProductionCatalog.Recipe::requiresFactory))
                offers.put(yard, 1);
        }
        for (int id : ecs.query(Household.class)) {
            var h = ecs.get(id, Household.class);
            if (agriculture.company(id) != 0) continue;
            var current = building(h.job);
            if (current != null && SpecialBuildings.special(current.type())
                    && current.type() != SpecialBuildings.EXCHANGE) continue;
            int minimum = current != null && current.type() == 1 ? 2 : 1;
            // Offers from one firm share a rate. Recompute after each citizen can change jobs.
            Map<LabourQuote,Double> quotes = new HashMap<>();
            boolean paid = canPayJob(h.job, id, quotes);
            if (paid && !graduationReviews.contains(id) && (!reviewPaid || occupants(h.job, false) <= minimum
                    && (current == null || current.type() != SpecialBuildings.EXCHANGE))) continue;
            int best =
                    offers.keySet().stream()
                            .filter(job -> job != h.job && occupants(job, false) < offers.get(job))
                            .filter(job -> exchangeVacancy(job, id) && canPayJob(job, id, quotes))
                            .max(
                                    Comparator.comparingInt((Integer job) -> graduationReviews.contains(id) ? careerRank(job, id) : 0)
                                            .thenComparingDouble(job -> jobRate(job, id, quotes))
                                            .thenComparingInt(job -> -job))
                            .orElse(0);
            if (best != 0 && (!paid || graduationReviews.contains(id) && careerRank(best, id) > careerRank(h.job, id)
                    || jobRate(best, id, quotes) > jobRate(h.job, id, quotes) * 1.2)) {
                h.job = best;
                var travel = ecs.get(id, Travel.class);
                travel.target = -9999;
                travel.route.clear();
                    travel.clearRoadLanes();
            }
        }
        if (reviewPaid && IndustrialProgression.enabled(economy.resources.catalog)
                && IndustrialProgression.tier(economy.resources) >= 6)
            for (int id : ecs.query(Needs.class)) {
                var needs = ecs.get(id, Needs.class);
                if (needs.money > 100) {
                    if (IndustrialProgression.tier(economy.resources) >= 8)
                        economy.purchase(CityEconomy.CITIZEN, id, IndustrialProgression.ADVANCED_VEHICLE, CityMaterials.UNIT);
                    if (economy.resources.available(CityEconomy.CITIZEN, id, IndustrialProgression.ADVANCED_VEHICLE) == 0)
                        economy.purchase(CityEconomy.CITIZEN, id, IndustrialProgression.CAR, CityMaterials.UNIT);
                }
                if (economy.resources.available(CityEconomy.CITIZEN, id, IndustrialProgression.CAR) >= CityMaterials.UNIT
                        || economy.resources.available(CityEconomy.CITIZEN, id, IndustrialProgression.ADVANCED_VEHICLE) >= CityMaterials.UNIT)
                    economy.purchase(CityEconomy.CITIZEN, id, IndustrialProgression.FUEL, 4 * CityMaterials.UNIT);
            }
        graduationReviews.clear();
    }

    private int careerRank(int job, int citizen) {
        var b = building(job);
        if (b != null && (b.type() == SpecialBuildings.EXCHANGE || b.type() == 2 && life(citizen).education == CitizenLife.Education.TECHNICAL)) return 2;
        return b != null && b.type() == 1 ? 1 : 0;
    }

    private void assignFarmers() {
        if (!agriculture.enabled()) return;
        for (var family : agriculture.state().families()) {
            var owned =
                    buildings.stream()
                            .filter(
                                    b ->
                                            b.type() == 3
                                                    && economy.property(b.id()).owner()
                                                            == family.company())
                            .toList();
            var project =
                    economy.plots.stream()
                            .filter(
                                    p ->
                                            p.type() == 3
                                                    && p.developer() == family.company()
                                                    && p.building() == 0)
                            .findFirst()
                            .orElse(null);
            for (int i = 0; i < family.members().size(); i++) {
                int id = family.members().get(i);
                var h = ecs.get(id, Household.class);
                h.home = owned.isEmpty() ? 0 : owned.get(0).id();
                int job =
                        project != null
                                ? -project.id()
                                : owned.isEmpty() ? 0 : owned.get(i % owned.size()).id();
                if (!eligible(job, id)) job = 0;
                if (h.job != job) {
                    h.job = job;
                    var t = ecs.get(id, Travel.class);
                    t.target = -9999;
                    t.route.clear();t.clearRoadLanes();
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
        if (id >= CityMaterials.YARD) {
            var firm = economy.company(id - CityMaterials.YARD);
            return firm == null
                    ? null
                    : new CityFrame.Building(
                            id, 0, 2, -8 + Math.floorMod(firm.kind - 2, 8) * 6
                                    + (firm.kind == CityMaterials.GLASSWORKS ? 1 : 0), grade, 23, 1, 0);
        }
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

    private void railTick(float dt) {
        railway.tick(dt, buildings, new Railway.Riders() {
            public boolean exists(int id) { return ecs.get(id, Household.class) != null; }
            public void move(int id, float x, float y, float z, String activity, boolean arrived) {
                var p=ecs.get(id,Position.class);var t=ecs.get(id,Travel.class);
                if(p==null||t==null)return;
                p.x=x;p.y=y;p.z=z;t.activity=activity;
                if(arrived){t.target=0;t.route.clear();t.clearRoadLanes();t.railOrigin=0;t.railDestination=0;t.railCooldown=elapsed+15;}
            }
        });

    }

    private void planRail(Position p, CityFrame.Building destination, Travel t) {
        if(Math.hypot(p.x-destination.x(),p.z-destination.z())<24)return;
        var stations=buildings.stream().filter(b->b.type()==SpecialBuildings.RAIL_STATION&&railway.served(b,buildings)).toList();
        var source=stations.stream().min(Comparator.comparingDouble(b->Math.hypot(p.x-b.x()-2.5f,p.z-b.z()-2.5f))).orElse(null);
        var end=stations.stream().min(Comparator.comparingDouble(b->Math.hypot(destination.x()-b.x(),destination.z()-b.z()))).orElse(null);
        if(source!=null&&end!=null&&source.id()!=end.id()&&Math.hypot(p.x-source.x()-2.5f,p.z-source.z()-2.5f)<24
                &&Math.hypot(destination.x()-end.x(),destination.z()-end.z())<24&&railway.connected(source,end)) {
            t.railOrigin=source.id();t.railDestination=end.id();
        }
    }

    private void journey(int id, Position p, CityFrame.Building destination, Travel t) {
        t.passingPoints = 0;
        float x = p.x, z = p.z;
        for (var b : buildings)
            if (x > b.x() && x < b.x() + 6 && z > b.z() && z < b.z() + 7) {
                int door = entrance(b);
                if (quarry(b) && door > b.z()) {
                    // A worker already near the rear door must not walk diagonally back across
                    // the shaft. Interior workers first join the safe side aisle at their own Z.
                    if (z < b.z() + 5) {
                        t.route.add(new Cell(b.x() + 1, (int) Math.floor(z)));
                        t.route.add(new Cell(b.x() + 1, b.z() + 5));
                    }
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
        if (destination.id() >= CityMaterials.YARD) {
            float station=yardStationX(destination,id);
            prepareYardAccess(station);
            // Work takes place inside the yard, leaving road lanes clear of idle staff.
            if(p.x>destination.x() && p.x<destination.x()+6 && p.z>23 && p.z<30) {
                t.accessRoute=true;
                t.route.add(new Cell((int)Math.floor(station),26));
            } else t.route.addAll(route(x,z,station,26.5f));
            return;
        }
        int door = entrance(destination);
        var path = route(x, z, destination.x() + 2.5f, door + .5f);
        if (path.isEmpty()) {
            t.route.clear();t.clearRoadLanes();
            return;
        }
        t.route.addAll(path);
        if (quarry(destination) && door > destination.z()) {
            t.route.add(new Cell(destination.x() + 2, destination.z() + 6));
            t.route.add(new Cell(destination.x() + 2, destination.z() + 5));
            t.route.add(new Cell(destination.x() + 1, destination.z() + 5));
            t.route.add(new Cell(destination.x() + 1, destination.z() + 2));
        } else
            t.route.add(
                    new Cell(
                            destination.x() + 2,
                            door < destination.z() ? destination.z() + 1 : destination.z() + 5));
        if (SpecialBuildings.special(destination.type()) && destination.type() != SpecialBuildings.EXCHANGE) {
            int kind = SpecialBuildings.kind(destination.type());
            if (kind == 1 || kind == 2 || kind == 3 || kind == 5 || destination.type() == SpecialBuildings.RAIL_STATION) {
                // School desks occupy the side stations used by houses and shops.
                t.route.add(new Cell(destination.x() + 2, destination.z() + 3));
                return;
            }
        }
        t.route.add(new Cell(destination.x() + 2, destination.z() + 1 + (id / 4) % 2));
        t.route.add(new Cell(destination.x() + 1 + id % 4, destination.z() + 1 + (id / 4) % 2));
    }

    private void prepareYardAccess(float station) {
        int x=(int)Math.floor(station),z=26;
        if(passable(station,z+.5f) || buildings.stream().anyMatch(b -> x>=b.x() && x<b.x()+6
                && z>=b.z() && z<b.z()+7)) return;
        // Virtual yards previously worked from the levelled road. Give their off-road
        // standing cell the same footing and headroom, including on legacy terrain.
        ground.apply(List.of(new Protocol.Edit(x,grade,z,Blocks.DIRT),
                new Protocol.Edit(x,grade+1,z,Blocks.AIR),new Protocol.Edit(x,grade+2,z,Blocks.AIR)));
    }

    private float yardStationX(CityFrame.Building yard,int citizen) {
        var shared=economy.companies().stream().filter(f -> -8+Math.floorMod(f.kind-2,8)*6
                +(f.kind==CityMaterials.GLASSWORKS ? 1 : 0)==yard.x()).toList();
        if(shared.size()<2) return yard.x()+2.5f;
        int index=0;
        for(var firm:shared) {if(CityMaterials.YARD+firm.id==yard.id()) break;index++;}
        return yard.x()+new float[]{2.5f,3.5f,1.5f,4.5f}[index%4];
    }

    private boolean beginLaneSegment(Travel t,Household h,Position p) {
        if(t.accessRoute || t.passingPoints!=0 || !t.lanes.isEmpty() || t.route.size()<2
                || !publicRoad(p.x,p.z)) return false;
        var first=t.route.peek();
        if(!roads.containsKey(first) || !publicRoad(first.x()+.5f,first.z()+.5f)) return false;
        var topology=new RoadTraffic(addresses.state(buildings).streets(),roads.keySet());
        if(!topology.center(first)) return false;
        var cells=new ArrayList<Cell>();
        for(var cell:t.route) {
            if(!topology.center(cell) || !publicRoad(cell.x()+.5f,cell.z()+.5f)
                    || !cells.isEmpty() && !topology.connected(cells.get(cells.size()-1),cell)) break;
            cells.add(cell);
        }
        if(cells.size()<2) return false;
        t.laneCells=cells.size();
        t.lanePath=List.copyOf(cells);t.mountedLane=h.horse!=0;
        t.laneSources.clear();
        for(var point:topology.lanePlan(cells,t.mountedLane)) {
            t.lanes.add(point.point());t.laneSources.add(point.sourceIndex());
        }
        return true;
    }

    private void refreshLaneType(Travel t,Household h) {
        boolean mounted=h.horse!=0;
        if(t.laneCells==0 || t.laneSources.isEmpty() || t.mountedLane==mounted) return;
        int source=t.laneSources.peek();
        var points=new RoadTraffic(addresses.state(buildings).streets(),roads.keySet())
                .lanePlan(t.lanePath,mounted);
        t.lanes.clear();t.laneSources.clear();t.mountedLane=mounted;
        for(var point:points) if(point.sourceIndex()>=source) {
            t.lanes.add(point.point());t.laneSources.add(point.sourceIndex());
        }
        // A pavement miter may trim the final cell of a short leg. Keep its physical
        // endpoint when changing lanes, instead of restarting the road segment.
        if(t.lanes.isEmpty() && !points.isEmpty()) {
            var end=points.get(points.size()-1);t.lanes.add(end.point());t.laneSources.add(end.sourceIndex());
        }
    }

    private static void consumeLanePoint(Travel t) {
        t.lanes.remove();
        if(!t.laneSources.isEmpty())t.laneSources.remove();
    }

    private void finishLaneSegment(Travel t) {
        if(t.laneCells==0) {t.route.clear();t.clearRoadLanes();return;}
        for(int i=0;i<t.laneCells && !t.route.isEmpty();i++)t.route.remove();
        t.clearRoadLanes();
        // Building and yard aisles retain the base collision-safe local access paths.
        t.accessRoute=!t.route.isEmpty();
    }

    private boolean nearRoad(float x, float z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        // A joining or yielding traveller can still occupy pavement stopping space
        // while its centre lies just outside the constructed road cells.
        for (int cx = bx - 1; cx <= bx + 1; cx++)
            for (int cz = bz - 1; cz <= bz + 1; cz++) {
                if (!roads.containsKey(new Cell(cx, cz))) continue;
                float dx = Math.max(0, Math.max(cx - x, x - cx - 1));
                float dz = Math.max(0, Math.max(cz - z, z - cz - 1));
                if (Math.hypot(dx, dz) <= .65f) return true;
            }
        return false;
    }

    private float laneGap(Household h, Travel t, Position p, Household other, Travel ot, Position op) {
        float gap = Math.max(h.horse == 0 ? roadSpacing.pedestrians() : roadSpacing.mounted(),
                other.horse == 0 ? roadSpacing.pedestrians() : roadSpacing.mounted());
        // Clearance along one lane must not reserve a neighbouring lane for an idle citizen.
        if ((ot == null || ot.lanes.isEmpty()) && !t.lanes.isEmpty()) {
            var points=t.lanes.iterator();var a=points.next();
            var next=points.hasNext()?points.next():a;
            double dx=next.x()-a.x(),dz=next.z()-a.z();
            if(Math.hypot(dx,dz)<.0001) {dx=a.x()-p.x;dz=a.z()-p.z;}
            double length=Math.hypot(dx,dz);
            if(length>0 && (Math.abs(dx)<.0001 || Math.abs(dz)<.0001)
                    && Math.abs(dx*(op.z-a.z())-dz*(op.x-a.x()))/length>=.5-.00001)
                return Math.min(gap,h.horse!=0 && other.horse!=0 ? .65f : .5f);
        }
        // Independent carriageway and pavement streams keep their own longitudinal gap.
        if(ot!=null && !ot.lanes.isEmpty() && !t.lanes.isEmpty()) {
            var ai=t.lanes.iterator();var a=ai.next();
            var bi=ot.lanes.iterator();var b=bi.next();
            var an=ai.hasNext()?ai.next():a;
            var bn=bi.hasNext()?bi.next():b;
            double dx=an.x()-a.x(),dz=an.z()-a.z(),ex=bn.x()-b.x(),ez=bn.z()-b.z();
            if(Math.hypot(dx,dz)<.0001) {dx=a.x()-p.x;dz=a.z()-p.z;}
            if(Math.hypot(ex,ez)<.0001) {ex=b.x()-op.x;ez=b.z()-op.z;}
            double length=Math.hypot(dx,dz),otherLength=Math.hypot(ex,ez);
            if(length>0 && otherLength>0 && Math.abs(dx*ez-dz*ex)<.001*length*otherLength
                    && Math.abs(dx*(b.z()-a.z())-dz*(b.x()-a.x()))/length>=.5-.00001)
                return Math.min(gap,h.horse!=0 && other.horse!=0 ? .65f : .5f);
        }
        return gap;
    }

    private void laneTravel(int id, Position p, Household h, Travel t, float dt) {
        if (t.lanes.isEmpty()) return;
        if (Math.hypot(t.lanes.peek().x()-p.x,t.lanes.peek().z()-p.z)<.0001) {
            consumeLanePoint(t);
            if(t.lanes.isEmpty()) finishLaneSegment(t);
            return;
        }
        double transit = publicRoad(p.x,p.z) ? economy.passengerSpeed(id, dt * 24 / config.daySeconds()) : 2.2;
        float speed = (float)(Math.max(transit,h.horse == 0 ? 2.2 : 5.5) * 1200 / config.daySeconds());
        float remaining=dt;
        while(remaining>.000001f && !t.lanes.isEmpty()) {
            var target=t.lanes.peek();
            float distance=(float)Math.hypot(target.x()-p.x,target.z()-p.z);
            if(distance<.0001f) {consumeLanePoint(t);if(t.lanes.isEmpty())finishLaneSegment(t);continue;}
            float slice=Math.min(remaining,Math.min(.25f,distance)/speed);
            float x=p.x,z=p.z;
            laneTravelStep(id,p,h,t,slice,speed);
            remaining-=slice;
            if(p.x==x && p.z==z) break;
        }
        if(t.lanes.isEmpty() && remaining>.000001f && !t.route.isEmpty()) travel(id,p,h,t,remaining);
    }

    private void laneTravelStep(int id, Position p, Household h, Travel t, float dt, float speed) {
        if(t.lanes.isEmpty()) {finishLaneSegment(t);return;}
        var target = t.lanes.peek();
        float dx = target.x() - p.x, dz = target.z() - p.z, dist = (float) Math.hypot(dx, dz);
        if (dist < .0001f) {
            // Reached waypoints consume no road space and must not wait for a future merge.
            consumeLanePoint(t);
            if(t.lanes.isEmpty()) finishLaneSegment(t);
            return;
        }
        float nx = dist <= speed * dt ? target.x() : p.x + dx / dist * speed * dt,
                nz = dist <= speed * dt ? target.z() : p.z + dz / dist * speed * dt;
        boolean onRoad =
                nearRoad(p.x, p.z) || nearRoad(nx, nz);
        var trafficIds = onRoad ? ecs.query(Position.class, Household.class) : List.<Integer>of();
        t.clearanceBack=false;
        boolean clearing = false;
        boolean yielding = false, forwardClearance = false;
        for (int other : trafficIds) {
            if (other == id) continue;
            var op = ecs.get(other, Position.class);
            var ot = ecs.get(other, Travel.class);
            if (!nearRoad(op.x,op.z) && (ot==null || ot.lanes.isEmpty())) continue;
            if(ot!=null && ot.clearanceBack) {
                var ahead=ot.lanes.stream().filter(w -> Math.hypot(w.x()-op.x,w.z()-op.z)>.0001)
                        .findFirst().orElse(null);
                if(ahead!=null) {
                    float length=(float)Math.hypot(ahead.x()-op.x,ahead.z()-op.z);
                    float ax=op.x-(ahead.x()-op.x)/length*.25f;
                    float az=op.z-(ahead.z()-op.z)/length*.25f;
                    float gap=laneGap(h,t,p,ecs.get(other,Household.class),ot,op);
                    float rx=p.x-dx/dist*speed*dt,rz=p.z-dz/dist*speed*dt;
                    // A queued follower must leave room for the joining leader to back out.
                    if(RoadTraffic.blocks(op.x,op.z,ax,az,p.x,p.z,gap)
                            && Math.hypot(rx-op.x,rz-op.z)>Math.hypot(p.x-op.x,p.z-op.z)
                            && passable(rx,rz)) {
                        nx=rx;nz=rz;dx=-dx;dz=-dz;clearing=true;t.clearanceBack=true;break;
                    }
                }
            }
            if (ot != null
                    && RoadTraffic.yields(id, p.x, p.z, t.lanes, other, op.x, op.z, ot.lanes,
                            h.horse!=0 && ecs.get(other,Household.class).horse!=0 ? .65f : .5f)) {
                // Yielding must also leave clearance for the priority traveller. A stream
                // already inside its stopping space backs out along its approach, retaining
                // the route, instead of reserving a crossing neither stream can enter.
                // Building access paths can lie outside road cells; safe retreat there
                // must remain possible when a joining traveller blocks the pavement.
                var ahead =
                        ot.lanes.stream()
                                .filter(w -> Math.hypot(w.x() - op.x, w.z() - op.z) > .0001)
                                .findFirst()
                                .orElse(null);
                float gap = laneGap(h, t, p, ecs.get(other, Household.class), ecs.get(other, Travel.class), op);
                float rx = p.x - dx / dist * speed * dt, rz = p.z - dz / dist * speed * dt;
                if (ahead != null) {
                    float length = (float) Math.hypot(ahead.x() - op.x, ahead.z() - op.z);
                    // Each actor can sweep up to a quarter metre. Use that actor's
                    // stopping step, not the yielding rider's shorter time slice.
                    float step = Math.min(length, .25f);
                    float ax = op.x + (ahead.x() - op.x) / length * step;
                    float az = op.z + (ahead.z() - op.z) / length * step;
                    // Clear stopping space in the route's forward direction when that
                    // safely moves away; retreat would move into the priority stream here.
                    if(RoadTraffic.blocks(op.x,op.z,ax,az,p.x,p.z,gap)
                            && Math.hypot(nx-op.x,nz-op.z)>Math.hypot(p.x-op.x,p.z-op.z)
                            && passable(nx,nz)) {forwardClearance=true;break;}
                    if (RoadTraffic.blocks(op.x, op.z, ax, az, p.x, p.z, gap)
                            && Math.hypot(rx - op.x, rz - op.z) > Math.hypot(p.x - op.x, p.z - op.z)
                            && passable(rx, rz)) {
                        nx = rx;
                        nz = rz;
                        dx = -dx;
                        dz = -dz;
                        clearing = true;
                        t.clearanceBack=true;
                        break;
                    }
                }
                yielding = true;
            }
        }
        if(yielding && !clearing && !forwardClearance) {
            t.activity = "Waiting for traffic";
            return;
        }
        for (int other : trafficIds) {
            if (other == id) continue;
            var op = ecs.get(other, Position.class);
            var ot=ecs.get(other,Travel.class);
            if (!nearRoad(op.x,op.z) && (ot==null || ot.lanes.isEmpty())) continue;
            float gap = laneGap(h, t, p, ecs.get(other, Household.class), ecs.get(other, Travel.class), op);
            if (RoadTraffic.blocks(p.x, p.z, nx, nz, op.x, op.z, gap)) {
                t.activity = "Waiting for traffic";
                return;
            }
        }
        // Rider households already reserve their mount's space. Unoccupied and
        // player-ridden mounts have no household actor, so sweep against them here.
        for (int horse : ecs.query(Position.class, Mount.class)) {
            if (horse == h.horse) continue;
            var mount = ecs.get(horse, Mount.class);
            var rider = mount.rider < 0 ? ecs.get(-mount.rider, Household.class) : null;
            if (rider != null && rider.horse == horse) continue;
            var hp = ecs.get(horse, Position.class);
            if (RoadTraffic.blocks(p.x, p.z, nx, nz, hp.x, hp.z, .65f)) {
                t.activity = "Waiting for traffic";
                return;
            }
        }
        if (!passable(nx, nz)) {
            t.activity = "Route obstructed";
            t.target = -9999;
            t.clearRoadLanes();
            t.route.clear();
            t.retryAt = elapsed + 2;
            return;
        }
        if (!clearing && dist <= speed * dt) {
            p.x = target.x();
            p.z = target.z();
            consumeLanePoint(t);
            if(t.lanes.isEmpty()) finishLaneSegment(t);
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
            if (t.lanes.isEmpty()) {
                int horse = h.horse;
                ecs.get(horse, Mount.class).rider = 0;
                h.horse = 0;
                parkHorse(horse,p);
                p.y -= .75f;
            }
        }
    }

    private boolean quarry(CityFrame.Building b) {
        return b.type() == 2 && ground.type(b.x() + 2, b.y(), b.z() + 3) == Blocks.AIR;
    }

    private void travel(int id, Position p, Household h, Travel t, float dt) {
        var site = workplace(t.target);
        if (h.horse != 0
                && site != null
                && Math.hypot(p.x - site.x() - 2.5f, p.z - entrance(site) - .5f) < 2) {
            int horse = h.horse;
            ecs.get(horse, Mount.class).rider = 0;
            h.horse = 0;
            p.y -= .75f;
            parkHorse(horse, p);
        }

        if (h.horse == 0
                && h.cohort > 0
                && (site == null || Math.hypot(p.x - site.x() - 2.5f, p.z - entrance(site) - .5f) >= 2)
                && roadContains(new Cell((int) Math.floor(p.x), (int) Math.floor(p.z)))) {
            for (int horse : ecs.query(Mount.class)) {
                var m = ecs.get(horse, Mount.class);
                var hp = ecs.get(horse, Position.class);
                if (m.rider == 0 && distance(p, hp) < 4) {
                    // Mounting must not enlarge an existing walking gap into an overlap.
                    h.horse = horse;
                    if (!roadClear(id, p, h, p.x, p.z)) {
                        h.horse = 0;
                        continue;
                    }
                    m.rider = -id;
                    break;
                }
            }
        }
        refreshLaneType(t,h);
        beginLaneSegment(t,h,p);
        if (!t.lanes.isEmpty()) {
            laneTravel(id,p,h,t,dt);
            return;
        }
        // Only road routes use the private passenger fleet. Collision checks still apply.
        double transit = roadContains(new Cell((int) Math.floor(p.x), (int) Math.floor(p.z)))
                && !t.accessRoute && !t.route.isEmpty() ? economy.passengerSpeed(id, dt * 24 / config.daySeconds()) : 2.2;
        // Travel must keep pace with needs and schedules when the city clock is accelerated.
        float speed = (float)(Math.max(transit, h.horse == 0 ? 2.2 : 5.5) * 1200 / config.daySeconds());
        float remaining = speed * dt, dx = 0, dz = 0, travelled = 0;
        boolean replanned = false;
        float waypointRadius = Math.max(roadSpacing.pedestrians(), roadSpacing.mounted()) + .15f;
        while (remaining > .0001f && !t.route.isEmpty()) {
            if(beginLaneSegment(t,h,p)) {
                // Keep the gait for private approach movement even if the road queue waits.
                p.phase += travelled * 2.66f;
                if(travelled>0) p.yaw=(float)Math.toDegrees(Math.atan2(dz,dx));
                laneTravel(id,p,h,t,remaining/speed);
                return;
            }
            var target = t.route.peek();
            // Road cells guide passing users without forcing them through occupied centres.
            while (t.route.size() > 1
                    && publicRoad(target.x() + .5f, target.z() + .5f)
                    && Math.hypot(target.x() + .5f - p.x, target.z() + .5f - p.z)
                            < (t.passingPoints > 0 ? .15f : waypointRadius)) {
                var points = t.route.iterator();
                points.next();
                var next = points.next();
                // Keep door alignment, but do not turn back to a road point already passed.
                boolean alignedAhead = next.x() == target.x()
                        && Math.abs(p.x - target.x() - .5f) < .12f
                        && (p.z - target.z() - .5f) * (next.z() - target.z()) > 0
                        || next.z() == target.z()
                        && Math.abs(p.z - target.z() - .5f) < .12f
                        && (p.x - target.x() - .5f) * (next.x() - target.x()) > 0;
                if ((!roadApproach(p.x, p.z) || !publicRoad(next.x() + .5f, next.z() + .5f))
                        && !alignedAhead) break;
                if (!clearTerrainPath(p.x, p.z, next.x() + .5f, next.z() + .5f)) break;
                consumeWaypoint(t);
                target = t.route.peek();
            }
            if (t.target >= CityMaterials.YARD && !t.accessRoute && t.route.size() == 1
                    && Math.hypot(target.x() + .5f - p.x, target.z() + .5f - p.z) < waypointRadius) {
                t.route.clear();t.clearRoadLanes();
                break;
            }
            dx = target.x() + .5f - p.x;
            dz = target.z() + .5f - p.z;
            float dist = (float) Math.hypot(dx, dz);
            if (dist < .0001f) {
                consumeWaypoint(t);
                continue;
            }
            // Retain upstream's small collision steps, including on accelerated days.
            float movement = Math.min(Math.min(remaining, dist), .25f);
            float nx = p.x + dx / dist * movement, nz = p.z + dz / dist * movement;
            if (!passable(nx, nz)) {
                t.activity = "Route obstructed";
                t.target = -9999;
                t.route.clear();t.clearRoadLanes();
                t.retryAt = elapsed + 2;
                break;
            }
            if (!roadClear(id, p, h, nx, nz)) {
                if (!replanned && roadApproach(p.x, p.z)) {
                    replanned = true;
                    if (passingRoute(id, p, h, t)) continue;
                }
                // Keep the route while waiting. Try clear passing directions, right first.
                boolean moved = false;
                double best = Double.POSITIVE_INFINITY;
                for (int angle : new int[] {30, -30, 60, -60, 90, -90, 120, -120, 150, -150, 180}) {
                    double radians = Math.toRadians(angle);
                    float sx = p.x + (float) ((dx * Math.cos(radians) + dz * Math.sin(radians)) / dist * movement);
                    float sz = p.z + (float) ((dz * Math.cos(radians) - dx * Math.sin(radians)) / dist * movement);
                    double distanceToTarget = Math.hypot(target.x() + .5f - sx, target.z() + .5f - sz);
                    if (distanceToTarget < best && (passingGround(sx, sz) || !publicRoad(p.x,p.z)) && passable(sx, sz)
                            && roadClear(id, p, h, sx, sz)) {
                        nx = sx;
                        nz = sz;
                        best = distanceToTarget;
                        moved = true;
                    }
                }
                if (!moved) {
                    t.activity = "Waiting for road clearance";
                    break;
                }
            }
            dx = nx - p.x;
            dz = nz - p.z;
            travelled += (float) Math.hypot(dx, dz);
            p.x = nx;
            p.z = nz;
            remaining -= movement;
            if (Math.hypot(p.x - target.x() - .5f, p.z - target.z() - .5f) < .0001)
                consumeWaypoint(t);
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
        p.phase += travelled * 2.66f;
        if (h.horse != 0) {
            var hp = ecs.get(h.horse, Position.class);
            hp.x = p.x;
            hp.y = p.y;
            hp.z = p.z;
            hp.yaw = p.yaw;
            hp.phase = p.phase;
            p.y += .75f;
            if (t.route.isEmpty()) {
                int horse = h.horse;
                ecs.get(horse, Mount.class).rider = 0;
                h.horse = 0;
                p.y -= .75f;
                parkHorse(horse, p);
            }
        }
    }

    private void parkHorse(int horse, Position rider) {
        // Routine dismounts use clear ground beside the road, never a travel lane.
        var candidates = new ArrayList<RoadTraffic.Waypoint>();
        for (int ix=-8;ix<=8;ix++) for (int iz=-8;iz<=8;iz++) {
            float x=rider.x+ix*.5f,z=rider.z+iz*.5f;
            double distance=Math.hypot(x-rider.x,z-rider.z);
            if(distance<1.4 || distance>4 || nearRoad(x,z) || !passable(x,z)
                    || blocksDoorApproach(x,z)) continue;
            candidates.add(new RoadTraffic.Waypoint(x,z));
        }
        candidates.sort(Comparator.comparingDouble(w -> Math.hypot(w.x()-rider.x,w.z()-rider.z)));
        for(var point:candidates) {
            boolean occupied=false;
            for(int other:ecs.query(Position.class,Household.class)) {
                var p=ecs.get(other,Position.class);
                if(Math.hypot(point.x()-p.x,point.z()-p.z)<.65) {occupied=true;break;}
            }
            for(int other:ecs.query(Position.class,Mount.class)) {
                if(other==horse)continue;
                var p=ecs.get(other,Position.class);
                if(Math.hypot(point.x()-p.x,point.z()-p.z)<.65) {occupied=true;break;}
            }
            if(occupied)continue;
            var hp=ecs.get(horse,Position.class);
            hp.x=point.x();hp.z=point.z();hp.y=grade+1.01f;
            return;
        }
    }

    /** Keep parked mounts out of the aisle between a building door and the road. */
    private boolean blocksDoorApproach(float x, float z) {
        for (var b : buildings)
            if (Math.abs(x - b.x() - 2.5f) < roadSpacing.mounted() + .25f
                    && z >= b.z() - 4 && z <= b.z() + 11) return true;
        return false;
    }

    private static void consumeWaypoint(Travel t) {
        t.route.remove();
        t.passingPoints = Math.max(0, t.passingPoints - 1);
    }

    /** Rejoin the road ahead using a clear lane, rather than oscillating beside a worker. */
    private boolean passingRoute(int id, Position p, Household h, Travel t) {
        Cell goal = null;
        int prefix = 0, goalPrefix = 0;
        for (var cell : t.route) {
            if (++prefix > 6 || !publicRoad(cell.x() + .5f, cell.z() + .5f)) break;
            var probe = new Position(cell.x() + .5f, p.y, cell.z() + .5f);
            if (passable(probe.x, probe.z) && roadClear(id, probe, h, probe.x, probe.z)) {
                goal = cell;
                goalPrefix = prefix;
            }
        }
        if (goal == null) return false;
        var start = new Cell((int) Math.floor(p.x), (int) Math.floor(p.z));
        var directions = new ArrayList<Cell>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            if (dx != 0 || dz != 0) directions.add(new Cell(dx, dz));
        int headingX = goal.x() - start.x(), headingZ = goal.z() - start.z();
        // Opposing traffic chooses opposite sides instead of planning into the same lane.
        directions.sort(Comparator.comparingInt((Cell offset) -> headingX * offset.z() - headingZ * offset.x())
                .thenComparingInt(offset -> -headingX * offset.x() - headingZ * offset.z()));
        var parents = new HashMap<Cell, Cell>();
        var queue = new ArrayDeque<Cell>();
        parents.put(start, start);
        queue.add(start);
        while (!queue.isEmpty() && parents.size() < 256) {
            var cell = queue.remove();
            if (cell.equals(goal)) break;
            for (var offset : directions) {
                var next = new Cell(cell.x() + offset.x(), cell.z() + offset.z());
                if (parents.containsKey(next) || !passingGround(next.x() + .5f, next.z() + .5f)
                        || !passable(next.x() + .5f, next.z() + .5f)) continue;
                var probe = new Position(next.x() + .5f, p.y, next.z() + .5f);
                var from = cell.equals(start) ? p : new Position(cell.x() + .5f, p.y, cell.z() + .5f);
                if (!clearTerrainPath(from.x, from.z, probe.x, probe.z)
                        || !roadClear(id, from, h, probe.x, probe.z)) continue;
                parents.put(next, cell);
                queue.add(next);
            }
        }
        if (!parents.containsKey(goal) || goal.equals(start)) return false;
        var path = new ArrayList<Cell>();
        for (var cell = goal; !cell.equals(start); cell = parents.get(cell)) path.add(cell);
        Collections.reverse(path);
        for (int i = 0; i < goalPrefix; i++) t.route.remove();
        for (int i = path.size() - 1; i >= 0; i--) t.route.addFirst(path.get(i));
        t.passingPoints = path.size();
        return true;
    }

    /** Wide gaps can leave the pavement briefly on clear ground to bypass a queue. */
    private boolean passingGround(float x, float z) {
        if (roadApproach(x, z)) return true;
        if (Math.max(roadSpacing.pedestrians(), roadSpacing.mounted()) < 2
                || buildings.stream().anyMatch(b -> x > b.x() && x < b.x() + 6
                        && z > b.z() && z < b.z() + 7)) return false;
        int cx = (int)Math.floor(x), cz = (int)Math.floor(z);
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
            if (roadContains(new Cell(cx + dx, cz + dz))) return true;
        return false;
    }

    private boolean roadApproach(float x, float z) {
        if (buildings.stream().anyMatch(b -> x > b.x() && x < b.x() + 6
                && z > b.z() && z < b.z() + 7)) return false;
        int cx = (int) Math.floor(x), cz = (int) Math.floor(z);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            if (roadContains(new Cell(cx + dx, cz + dz))) return true;
        return false;
    }

    private boolean publicRoad(float x, float z) {
        return roadContains(new Cell((int) Math.floor(x), (int) Math.floor(z)))
                && buildings.stream().noneMatch(b -> x > b.x() && x < b.x() + 6
                        && z > b.z() && z < b.z() + 7);
    }

    /** Check the swept step, so a fast rider cannot skip through a waiting walker. */
    private boolean roadClear(int id, Position p, Household h, float nx, float nz) {
        if (!passingGround(p.x,p.z) && !passingGround(nx,nz)) return true;
        float ownGap = h.horse == 0 ? roadSpacing.pedestrians() : roadSpacing.mounted();
        var ownTravel=ecs.get(id,Travel.class);
        boolean localAccess=ownTravel!=null && (ownTravel.accessRoute || ownTravel.passingPoints>0
                || ownTravel.lanes.isEmpty() && !publicRoad(p.x,p.z));
        for (int other : ecs.query(Position.class, Household.class)) {
            if (other == id) continue;
            var q = ecs.get(other, Position.class);
            if (!passingGround(q.x, q.z)) continue;
            var household = ecs.get(other, Household.class);
            float gap = Math.max(ownGap, household.horse == 0
                    ? roadSpacing.pedestrians() : roadSpacing.mounted());
            // Private doors and forecourts use body clearance. Longitudinal queue gaps
            // apply after joining a road lane, not across a narrow building exit.
            if(localAccess)
                gap=Math.min(gap,h.horse!=0 && household.horse!=0 ? .65f : .5f);
            if (!clearStep(p.x, p.z, nx, nz, q.x, q.z, gap)) return false;
        }
        for (int horse : ecs.query(Position.class, Mount.class)) {
            var mount = ecs.get(horse, Mount.class);
            // NPC riders are already counted by their household position.
            if (mount.rider < 0 || horse == h.horse) continue;
            var q = ecs.get(horse, Position.class);
            float gap=localAccess ? Math.min(.65f,Math.max(ownGap,roadSpacing.mounted()))
                    : Math.max(ownGap,roadSpacing.mounted());
            if (passingGround(q.x,q.z) && !clearStep(p.x,p.z,nx,nz,q.x,q.z,gap)) return false;
        }
        return true;
    }

    static boolean clearStep(float x, float z, float nx, float nz,
            float qx, float qz, float gap) {
        if (gap == 0) return true;
        double before = Math.hypot(x - qx, z - qz);
        double after = Math.hypot(nx - qx, nz - qz);
        double dx = nx - x, dz = nz - z, length = dx * dx + dz * dz;
        double along = length == 0 ? 0 : Math.max(0, Math.min(1,
                ((qx - x) * dx + (qz - z) * dz) / length));
        double closest = Math.hypot(x + along * dx - qx, z + along * dz - qz);
        // Already overlapping pairs may separate, never move closer.
        if (before < gap - .00001)
            return after > before + .00001 && closest >= before - .00001;
        return closest >= gap - .00001;
    }

    private static float distance(Position a, Position b) {
        return (float)
                Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2) + Math.pow(a.z - b.z, 2));
    }

    public List<Cell> route(float x, float z, float tx, float tz) {
        var topology = new RoadTraffic(addresses.state(buildings).streets(), roads.keySet());
        boolean directional = topology.nearest(x,z) != null;
        Cell a = directional ? topology.nearest(x,z) : nearest(x,z);
        Cell b = directional ? topology.nearest(tx,tz) : nearest(tx,tz);
        if (a == null || b == null) return List.of();
        if (stressGrid != null) {
            var result = new ArrayList<>(stressGrid.route(a,b));
            if (result.isEmpty() || result.stream().anyMatch(c -> !passable(c.x()+.5f,c.z()+.5f))) return List.of();
            result.add(new Cell((int)Math.floor(tx),(int)Math.floor(tz)));
            return result;
        }
        var parents = new HashMap<Cell, Cell>();
        var queue = new ArrayDeque<Cell>();
        queue.add(a);
        parents.put(a, a);
        while (!queue.isEmpty()) {
            var c = queue.remove();
            if (c.equals(b)) break;
            for (var next : neighbours(c))
                if (roadContains(next) && (!directional || topology.connected(c,next))
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

    private boolean clearTerrainPath(float x, float z, float nx, float nz) {
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(nx - x, nz - z) / .25));
        for (int i = 1; i <= steps; i++)
            if (!passable(x + (nx - x) * i / steps, z + (nz - z) * i / steps)) return false;
        return true;
    }

    private boolean passable(float x, float z) {
        float y = grade + 1.01f;
        for (var b : buildings)
            if (x >= b.x() && x < b.x() + 6 && z >= b.z() && z < b.z() + 7) {
                y = b.y() + 1.01f;
                break;
            }
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z), by = (int) Math.floor(y);
        return ground.type(bx, by, bz) == 0
                && ground.type(bx, by + 1, bz) == 0
                && ground.type(bx, by - 1, bz) != 0;
    }

    private boolean roadContains(Cell cell) {
        return roads.containsKey(cell) || stressGrid != null && stressGrid.road(cell.x(),cell.z());
    }

    private Cell nearest(float x, float z) {
        if (stressGrid != null) return stressGrid.nearestRoad(x,z);
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
            if (stressGrid != null && (c.kind() == CityCommand.ROAD || c.kind() == CityCommand.DELETE_ROAD || c.kind() == CityCommand.EDIT_ROAD || c.kind() == CityCommand.RAIL || c.kind() == CityCommand.ZONE || c.kind() == CityCommand.PARCEL || c.kind() == CityCommand.SPECIAL || c.kind() == CityCommand.EXCHANGE))
                return "Prebuilt grid roads and zoning are fixed; buildings develop and can be inspected or demolished.";
            for (var point : c.points()) if (Math.abs(point.x()-8)>256 || Math.abs(point.z()-24)>256)
                throw new IllegalArgumentException("City limits: 512 x 512 blocks around spawn");
            String result = switch (c.kind()) {
                case CityCommand.ROAD -> road(c.points(), c.value());
                case CityCommand.DELETE_ROAD -> changeRoad(c.value(), -1);
                case CityCommand.EDIT_ROAD -> changeRoad(c.value(), (int)c.points().get(0).x());
                case CityCommand.RAIL -> rail(c.points());
                case CityCommand.ZONE -> zone(c.value()%4, new Polygon(c.points()), c.value()/4-1);
                case CityCommand.PARCEL -> repartition(c.value(),(int)c.points().get(0).x());
                case CityCommand.SPECIAL -> c.value() == SpecialBuildings.AIRPORT ? airport(c) : special(c);
                case CityCommand.RUNWAY -> expandAirport(c);
                case CityCommand.FLIGHT -> bookFlight(c);
                case CityCommand.RIDE -> ride(player, c.value(), pose);
                case CityCommand.DEMOLISH -> demolish(c.value());
                case CityCommand.EXCHANGE -> buildExchange(c.points());
                case CityCommand.CAPITAL -> capitalCommand(c.capital());
                case CityCommand.SETTLE_DISTRICT -> settleDistrict(c.value());
                case CityCommand.FOCUS_DISTRICT -> { population.focus(c.value()); yield "District focus updated"; }
                default -> "Unknown city tool";
            };
            railTick(0);
            return result;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    private String settleDistrict(int count) {
        int district=population.state().groups().stream().mapToInt(RegionalPopulation.Group::district).max().orElse(0)+1;
        int x=8+(district%32)*512, z=24+(district/32+1)*512;
        int y=terrain.surfaceHeight(x,z)+1;
        int id=population.settle(count,y);
        return "District " + id + " settled with " + count + " immigrants";
    }

    /** Caller must enforce offline cheat mode. Normal demolition keeps player safety checks. */
    public String demolishForOfflineBlast(int id) { return demolish(id, true); }

    private String demolish(int id) { return demolish(id, false); }

    private String demolish(int id, boolean blast) {
        var b = building(id);
        if (b == null) return "Building is no longer available";
        if (!blast && b.type() == SpecialBuildings.PORT)
            for (int y=Geography.SEA_LEVEL; y<b.y(); y+=7)
                if (ground.playerOccupied(b.x(),y,b.z(),StructureBlueprint.width(b.type()),StructureBlueprint.depth(b.type())))
                    return "Move players out of the building before demolition";
        if (!blast && ground.playerOccupied(
                b.x(),
                b.y(),
                b.z(),
                StructureBlueprint.width(b.type()),
                (b.type() == SpecialBuildings.AIRPORT ? Aviation.depth(Aviation.runways(b)) : StructureBlueprint.depth(b.type()))))
            return "Move players out of the building before demolition";
        var property = economy.property(id);
        var company = property == null ? null : economy.company(property.operator());
        int kind = company == null ? b.type() : company.kind;
        if (b.type() == 0 && b.capacity() == 8) {
            kind = economy.plots.stream().filter(p -> p.building() == b.id())
                    .map(p -> economy.resources.project(p.id())).filter(Objects::nonNull)
                    .mapToInt(CityMaterials.Project::businessKind).findFirst().orElse(kind);
        }
        var edits = new ArrayList<Protocol.Edit>();
        for (var e : b.type() == SpecialBuildings.AIRPORT ? Aviation.blueprint(b.x(), b.y(), b.z(), Aviation.runways(b)) : SpecialBuildings.special(b.type())
                ? StructureBlueprint.special(b.type(), b.x(), b.y(), b.z())
                : StructureBlueprint.generate(b.type(), kind, b.x(), b.y(), b.z()))
            // Excavated mine shafts are terrain, not structure to remove.
            if (e.y() >= b.y() || b.type() == SpecialBuildings.PORT)
                edits.add(e.withType(b.type() == SpecialBuildings.PORT && e.y() <= Geography.SEA_LEVEL
                        ? terrain.block(e.x(),e.y(),e.z()) : 0));
        // Crops and livestock pens may extend beyond the barn blueprint.
        if (b.type() == 3)
            for (int x = 0; x < StructureBlueprint.width(3); x++)
                for (int z = 0; z < StructureBlueprint.depth(3); z++)
                    edits.add(new Protocol.Edit(b.x() + x, b.y() + 1, b.z() + z, 0));
        ground.apply(edits);
        for (var f : new ArrayList<>(flights)) if (f.origin() == id || f.destination() == id) {
            var safe = building(f.origin() == id ? f.destination() : f.origin());
            var p = ecs.get(f.citizen(), Position.class);
            var h = ecs.get(f.citizen(), Household.class);
            if (h != null && h.horse != 0) { var m = ecs.get(h.horse, Mount.class); if (m != null) m.rider = 0; h.horse = 0; }
            if (p != null) { p.x = (safe == null ? b.x() : safe.x()) + 2.5f; p.y = (safe == null ? b.y() : safe.y()) + 1.01f; p.z = (safe == null ? b.z() : safe.z()) - .5f; }
            flights.remove(f);
        }
        buildings.remove(b);
        addresses.demolish(id);
        for (var plot : new ArrayList<>(economy.plots))
            if (plot.building() == id) {
                economy.resources.forgetProject(plot.id());
                economy.plots.remove(plot);
            }
        economy.properties.removeIf(p -> p.building() == id);
        economy.contracts.removeIf(c -> c.building() == id);
        economy.businesses.close(id);
        agriculture.demolish(id);
        for (int citizen : ecs.query(Household.class, Travel.class)) {
            var h = ecs.get(citizen, Household.class);
            var t = ecs.get(citizen, Travel.class);
            if (h.home == id) h.home = 0;
            if (h.job == id) h.job = 0;
            t.target = -9999;
            t.route.clear();t.clearRoadLanes();
        }
        refreshExchange();
        return "Building demolished; zoned land can redevelop (no material refund)";
    }

    private void airportSite(int x, int z, int start, int end, int ignore, boolean entranceRequired) {
        boolean access = false;
        if (grade + 10 > Terrain.MAX_Y) throw new IllegalArgumentException("Airport exceeds world height");
        if (economy.overlaps(x, z + start, Aviation.WIDTH, end - start)) throw new IllegalArgumentException("Airport overlaps an owned plot");
        for (int dx = 0; dx < Aviation.WIDTH; dx++) for (int dz = start; dz < end; dz++) {
            int cx = x + dx, cz = z + dz;
            if (Math.abs((long)cx-8)>256 || Math.abs((long)cz-24)>256) throw new IllegalArgumentException("Airport outside city limits");
            if (railway.contains(cx, cz)) throw new IllegalArgumentException("Airport cannot cover rails");
            if (roadContains(new Cell(cx, cz))) throw new IllegalArgumentException("Airport cannot cover roads");
            for (var zone : zones) if (zone.polygon().contains(cx+.5f,cz+.5f)) throw new IllegalArgumentException("Airport cannot cover zones");
            for (var b : buildings) if (b.id() != ignore && cx >= b.x()-1 && cx <= b.x()+StructureBlueprint.width(b.type())
                    && cz >= b.z()-2 && cz <= b.z()+(b.type() == SpecialBuildings.AIRPORT ? Aviation.depth(Aviation.runways(b)) : StructureBlueprint.depth(b.type())))
                throw new IllegalArgumentException("Airport overlaps another building or entrance");
            if (dx < 6 && dz == -1 && roadContains(new Cell(cx, cz-1))) access = true;
            if (ground.occupied(cx,grade+1,cz,1,1)) throw new IllegalArgumentException("Airport would intersect a player");
            for (int y=grade+1; y<=Terrain.MAX_Y; y++) if (ground.type(cx,y,cz)!=0) throw new IllegalArgumentException("Clear the airport site first");
        }
        if (entranceRequired && !access) throw new IllegalArgumentException("Front entrance must touch a road");
    }

    private String airport(CityCommand c) {
        if (c.points().size()!=1 || c.ownerKind()!=0 || c.ownerId()!=0) throw new IllegalArgumentException("Airports need a city-owned permit");
        if (buildings.size()>=512 || buildings.stream().filter(b -> b.type()==SpecialBuildings.AIRPORT).count()>=Aviation.MAX_AIRPORTS)
            throw new IllegalArgumentException("Airport limit reached");
        int x=(int)Math.floor(c.points().get(0).x()), z=(int)Math.floor(c.points().get(0).z());
        airportSite(x,z,-1,Aviation.depth(1),0,true);
        if (economy.budget<Aviation.AIRPORT_COST) throw new IllegalArgumentException("Treasury needs 2000 for an airport");
        var edits=new ArrayList<Protocol.Edit>();
        for(int dx=0;dx<Aviation.WIDTH;dx++) for(int dz=-1;dz<Aviation.depth(1);dz++) level(x+dx,z+dz,edits);
        edits.addAll(Aviation.blueprint(x,grade+1,z,1));
        ground.apply(edits); economy.budget-=Aviation.AIRPORT_COST;
        buildings.add(new CityFrame.Building(++buildingIds,0,SpecialBuildings.AIRPORT,x,grade+1,z,8,0));
        return "Permitted Airport with 1 runway";
    }

    private String expandAirport(CityCommand c) {
        var b=building(c.value());
        if(b==null || b.type()!=SpecialBuildings.AIRPORT || !c.points().isEmpty()) throw new IllegalArgumentException("Select an airport to expand");
        int count=Aviation.runways(b);
        if(count>=Aviation.MAX_RUNWAYS) throw new IllegalArgumentException("Airport already has 3 runways");
        int start=Aviation.depth(count), end=Aviation.depth(count+1);
        airportSite(b.x(),b.z(),start,end,b.id(),false);
        if(economy.budget<Aviation.RUNWAY_COST) throw new IllegalArgumentException("Treasury needs 1000 for a runway");
        var edits=new ArrayList<Protocol.Edit>();
        for(int dx=0;dx<Aviation.WIDTH;dx++) for(int dz=start;dz<end;dz++) level(b.x()+dx,b.z()+dz,edits);
        for(var e:Aviation.blueprint(b.x(),b.y(),b.z(),count+1)) if(e.z()>=b.z()+start) edits.add(e);
        ground.apply(edits); economy.budget-=Aviation.RUNWAY_COST;
        buildings.set(buildings.indexOf(b),new CityFrame.Building(b.id(),0,b.type(),b.x(),b.y(),b.z(),8*(count+1),0));
        return "Airport expanded to " + (count+1) + " runways";
    }

    private int availableRunway(CityFrame.Building b) {
        for (int r=0;r<Aviation.runways(b);r++) {
            final int lane=r;
            if (flights.stream().noneMatch(f -> f.origin()==b.id() && f.originRunway()==lane
                    || f.destination()==b.id() && f.destinationRunway()==lane)) return r;
        }
        return -1;
    }

    private boolean runwayAvailable(CityFrame.Building b) {
        return flights.stream().filter(f -> f.origin()==b.id() || f.destination()==b.id()).count()<Aviation.runways(b);
    }

    private String bookFlight(CityCommand c) {
        var destination=building(c.value()); int id=c.ownerId();
        var p=ecs.get(id,Position.class); var h=ecs.get(id,Household.class); var t=ecs.get(id,Travel.class);
        if(destination==null || destination.type()!=SpecialBuildings.AIRPORT || h==null || !c.points().isEmpty() || c.ownerKind()!=0)
            throw new IllegalArgumentException("Select a citizen, then a destination airport");
        if(!life(id).adult()) throw new IllegalArgumentException("Only adult citizens can book flights");
        if(flights.stream().anyMatch(f -> f.citizen()==id)) throw new IllegalArgumentException("Citizen already has a flight");
        var origins=buildings.stream().filter(b -> b.type()==SpecialBuildings.AIRPORT && b.id()!=destination.id())
                .sorted(Comparator.comparingDouble(b -> Math.hypot(b.x()-p.x,b.z()-p.z))).toList();
        if(origins.isEmpty()) throw new IllegalArgumentException("Build a second airport for flights");
        if(!runwayAvailable(destination)) throw new IllegalArgumentException("Destination runways are busy");
        for(var origin:origins) if(runwayAvailable(origin)) {
            var trial=new Travel(); trial.target=origin.id(); journey(id,p,origin,trial);
            boolean inside=p.x>origin.x() && p.x<origin.x()+6 && p.z>origin.z() && p.z<origin.z()+7;
            if(trial.route.isEmpty() && !inside) continue;
            if(h.horse!=0) { var mount=ecs.get(h.horse,Mount.class); if(mount!=null) mount.rider=0; h.horse=0; p.y-=.75f; }
            t.target=origin.id(); t.route.clear();t.clearRoadLanes(); t.route.addAll(trial.route); t.activity="Going to airport";
            flights.add(new Aviation.Flight(++flightIds,origin.id(),destination.id(),id,availableRunway(origin),availableRunway(destination),0,0));
            return "Flight booked: walk to airport, board, then fly";
        }
        throw new IllegalArgumentException("No connected origin airport with a free runway");
    }

    private void aviationTick(float dt) {
        for(var old:new ArrayList<>(flights)) {
            var f=old.tick(dt); var a=building(f.origin()); var b=building(f.destination());
            var p=ecs.get(f.citizen(),Position.class); var h=ecs.get(f.citizen(),Household.class); var t=ecs.get(f.citizen(),Travel.class);
            if(p==null || h==null || a==null || b==null) { flights.remove(old); continue; }
            if(f.stage()==0) {
                t.activity="Going to airport";
                if(t.target!=a.id()) { t.target=a.id(); t.route.clear();t.clearRoadLanes(); journey(f.citizen(),p,a,t); }
                if(!t.route.isEmpty()) travel(f.citizen(),p,h,t,dt);
                if(p.x>a.x() && p.x<a.x()+6 && p.z>a.z() && p.z<a.z()+7 && t.route.isEmpty()) f=f.stage(1);
                else if(f.clock()>120 || t.route.isEmpty()) { flights.remove(old); t.target=-9999; t.activity="Flight cancelled: route blocked"; continue; }
            }
            if(f.stage()==1) {
                t.activity="Boarding passenger jet";
                if(h.horse!=0) { var m=ecs.get(h.horse,Mount.class); if(m!=null)m.rider=0; h.horse=0; }
                if(f.clock()>=3) f=f.stage(2);
            }
            if(f.stage()==2) {
                double progress=Math.min(1,f.clock()/Aviation.duration(a,b));
                var sample=FlightPath.sample(a.x()+8,a.y()+1,a.z()+18+Aviation.RUNWAY_SPACING*f.originRunway(),b.x()+28,b.y()+1,b.z()+18+Aviation.RUNWAY_SPACING*f.destinationRunway(),progress);
                p.x=sample.x();p.y=sample.y();p.z=sample.z();p.yaw=sample.yaw();
                t.activity="Flying to airport #"+b.id();
                if(progress>=1) { f=f.stage(3); p.x=b.x()+2.5f;p.y=b.y()+1.01f;p.z=b.z()+1.5f; }
            }
            if(f.stage()==3) {
                t.activity="Arrived at airport #"+b.id();
                if(f.clock()>=3) { flights.remove(old); t.target=-9999;t.route.clear();t.clearRoadLanes();continue; }
            }
            flights.set(flights.indexOf(old),f);
        }
    }

    private boolean specialCell(int x, int z) {
        return buildings.stream().anyMatch(b -> SpecialBuildings.special(b.type())
                && x >= b.x() && x < b.x() + StructureBlueprint.width(b.type())
                && z >= b.z()-1 && z <= b.z()+(b.type() == SpecialBuildings.AIRPORT ? Aviation.depth(Aviation.runways(b)) : StructureBlueprint.depth(b.type())));
    }

    private String special(CityCommand command) {
        int type = command.value();
        if ((!SpecialBuildings.special(type) || type == SpecialBuildings.EXCHANGE || type == SpecialBuildings.AIRPORT) || command.points().size() != 1 || buildings.size() >= 512)
            throw new IllegalArgumentException("Invalid special building permit");
        int kind = command.ownerKind(), id = command.ownerId();
        var snapshot = frame();
        if (kind < 0 || kind > 2
                || (kind == 0 && id != 0)
                || (kind == 1 && snapshot.citizens().stream().noneMatch(c -> c.id() == id))
                || (kind == 2 && snapshot.economy().firms().stream().noneMatch(f -> f.id() == id)))
            throw new IllegalArgumentException("Select an existing owner");
        int x = (int)Math.floor(command.points().get(0).x()), z = (int)Math.floor(command.points().get(0).z());
        boolean port = type == SpecialBuildings.PORT;
        int depth = StructureBlueprint.depth(type);
        if (port) {
            for (int dx=0;dx<6;dx++) {
                for (int dz=-1;dz<3;dz++) {
                    int cx=x+dx, cz=z+dz;
                    if (terrain.fields(cx,cz)==null || terrain.fields(cx,cz).waterLevel() >= terrain.column(cx,cz).height())
                        throw new IllegalArgumentException("Port entrance must be on dry coastal land");
                }
                for (int dz=14;dz<depth;dz++) {
                    int cx=x+dx, cz=z+dz;
                    if (!terrain.ocean(cx,cz) || terrain.column(cx,cz).height() > Geography.SEA_LEVEL-2
                            || ground.type(cx,Geography.SEA_LEVEL,cz)!=Blocks.WATER)
                        throw new IllegalArgumentException("Port requires open ocean behind its dock (+Z)");
                }
            }
        }
        boolean access = false;
        if (grade + 7 > Terrain.MAX_Y) throw new IllegalArgumentException("Building exceeds world height");
        if (economy.overlaps(x, z, 6, depth)) throw new IllegalArgumentException("Building overlaps an owned plot");
        for (int dx = 0; dx < 6; dx++) for (int dz = -1; dz <= depth; dz++) {
            int cx = x+dx, cz = z+dz;
            if (Math.abs((long)cx-8)>256 || Math.abs((long)cz-24)>256)
                throw new IllegalArgumentException("Building outside city limits");
            if (railway.contains(cx,cz)) throw new IllegalArgumentException("Building cannot cover rails");
            if (roadContains(new Cell(cx,cz))) throw new IllegalArgumentException("Building cannot cover roads");
            for (var zone : zones) if (zone.polygon().contains(cx+.5f,cz+.5f))
                throw new IllegalArgumentException("Building cannot cover zones");
            for (var b : buildings) if (cx >= b.x()-1 && cx <= b.x()+StructureBlueprint.width(b.type())
                    && cz >= b.z()-2 && cz <= b.z()+(b.type() == SpecialBuildings.AIRPORT ? Aviation.depth(Aviation.runways(b)) : StructureBlueprint.depth(b.type())))
                throw new IllegalArgumentException("Building overlaps another building or entrance");
            if (dz == -1 && roadContains(new Cell(cx,cz-1))) access = true;
            if (port && dz>=3) {
                for (int y=Geography.SEA_LEVEL; y<=grade+7; y++) {
                    int block=ground.type(cx,y,cz);
                    if (block!=0 && block!=Blocks.WATER && y>terrain.column(cx,cz).height())
                        throw new IllegalArgumentException("Clear the dock and carrier berth first");
                    if (ground.occupied(cx,y,cz,1,1)) throw new IllegalArgumentException("Dock would intersect a player");
                }
            }
            if (ground.occupied(cx,grade+1,cz,1,1)) throw new IllegalArgumentException("Building would intersect a player");
            // level() clears the whole column above grade, not just the building height.
            // Validate that entire volume before collecting or applying any placement edits.
            for (int y = grade + 1; y <= Terrain.MAX_Y; y++) if (ground.type(cx,y,cz)!=0)
                throw new IllegalArgumentException("Clear the building site first");
        }
        if (!access) throw new IllegalArgumentException("Front entrance must touch a road");
        if (type >= SpecialBuildings.RAIL_STATION && !railway.contains(x+2,z+8))
            throw new IllegalArgumentException("Rail building needs a track at its rear dock (x+2, z+8)");
        if (type == SpecialBuildings.RAIL_DEPOT && buildings.stream().filter(b -> b.type()==type).count() >= 32)
            throw new IllegalArgumentException("Rail depot limit reached");
        var edits = new ArrayList<Protocol.Edit>();
        for (int dx=0;dx<6;dx++) for(int dz=-1;dz<=(port ? 2 : 7);dz++) level(x+dx,z+dz,edits);
        edits.addAll(StructureBlueprint.special(type,x,grade+1,z));
        ground.apply(edits);
        buildings.add(new CityFrame.Building(++buildingIds,-kind,type,x,grade+1,z,8*SpecialBuildings.level(type),id));
        return "Permitted " + SpecialBuildings.name(type);
    }

    /** Treasury-funded offers use separate qualified analyst and support labour pools. */
    public double exchangeLabourRate(boolean graduate) {
        long positions = buildings.stream()
                .filter(b -> b.type() == SpecialBuildings.EXCHANGE).count() * (graduate ? 4 : 0);
        long supply = ecs.query(Household.class).stream()
                .filter(id -> economy.capital.graduates.contains(id) == graduate)
                .filter(id -> agriculture.company(id) == 0)
                .filter(id -> {
                    int job = ecs.get(id, Household.class).job;
                    var b = building(job);
                    return job == 0 || b != null && b.type() == SpecialBuildings.EXCHANGE;
                }).count();
        double pressure = Math.max(.25, Math.min(4, Math.sqrt((positions + 1.0) / (supply + 1.0))));
        return (graduate ? 2.7 : 1.8) * pressure;
    }

    private boolean exchangeVacancy(int job, int citizen) {
        var b = building(job);
        if (b == null || b.type() != SpecialBuildings.EXCHANGE) return true;
        boolean graduate = economy.capital.graduates.contains(citizen);
        long filled = ecs.query(Household.class).stream()
                .filter(id -> ecs.get(id, Household.class).job == job)
                .filter(id -> economy.capital.graduates.contains(id) == graduate).count();
        return graduate && filled < 4;
    }

    private void assignExchange() {
        for (var b : buildings)
            if (b.type() == SpecialBuildings.EXCHANGE) {
                for (int id : ecs.query(Household.class, Needs.class, Travel.class)) {
                    var h = ecs.get(id, Household.class);
                    boolean graduate = economy.capital.graduates.contains(id);
                    long qualified =
                            ecs.query(Household.class).stream()
                                    .filter(
                                            c ->
                                                    ecs.get(c, Household.class).job == b.id()
                                                            && economy.capital.graduates.contains(
                                                                    c))
                                    .count();
                    long support = occupants(b.id(), false) - qualified;
                    if (h.job != b.id()
                            && occupants(b.id(), false) < b.capacity()
                            && (graduate ? qualified < 4 : support < 1)
                            && agriculture.company(id) == 0
                            && canPayJob(b.id(), id)
                            && (!canPayJob(h.job, id)
                                    || jobRate(b.id(), id) > jobRate(h.job, id) * 1.2)) {
                        h.job = b.id();
                        var t = ecs.get(id, Travel.class);
                        t.target = -9999;
                        t.route.clear();t.clearRoadLanes();
                    }
                }
            }
    }

    public void refreshExchange() {
        boolean available = false;
        for (var b : buildings)
            if (b.type() == SpecialBuildings.EXCHANGE) {
                long onSite = 0, graduates = 0;
                for (int id : ecs.query(Household.class, Position.class, Travel.class)) {
                    var p = ecs.get(id, Position.class);
                    var t = ecs.get(id, Travel.class);
                    if (ecs.get(id, Household.class).job == b.id()
                            && onShift(id, config.time(elapsed))
                            && p.x > b.x()
                            && p.x < b.x() + 6
                            && p.z > b.z()
                            && p.z < b.z() + 7
                            && (t.activity.equals("Exchange analyst (graduate)")
                                    || t.activity.equals("Exchange office support"))) {
                        onSite++;
                        if (economy.capital.graduates.contains(id)) graduates++;
                    }
                }
                if (onSite >= 2 && graduates >= 2 && graduates * 2 > onSite && economy.budget > 0)
                    available = true;
            }
        economy.capital.exchange.operational(available);
    }

    private String capitalCommand(CityCommand.Capital c) {
        refreshExchange();
        var book = economy.capital.exchange;
        var owner = new CityStockExchange.Owner(c.ownerKind(), c.owner());
        return switch (c.action()) {
            case 0 -> {
                book.goPublic(c.company(), owner, c.shares(), c.price());
                yield "Public offering entered; sold shares fund the company";
            }
            case 1, 2 -> {
                book.submit(c.company(), owner, c.action() == 1, c.shares(), c.price());
                yield "Share order accepted";
            }
            case 3 ->
                    book.cancel(c.order(), owner)
                            ? "Order cancelled; reservation released"
                            : "Order not owned by this investor";
            default -> throw new IllegalArgumentException("Invalid exchange action");
        };
    }

    private String buildExchange(List<Point> points) {
        if (points.size() != 1) throw new IllegalArgumentException("Choose one exchange location");
        int x = (int) Math.floor(points.get(0).x()), z = (int) Math.floor(points.get(0).z());
        if (buildings.size() >= 512 || buildings.stream().anyMatch(b -> b.type() == SpecialBuildings.EXCHANGE))
            throw new IllegalArgumentException("The city already has a stock exchange");
        if (Math.abs((long) x - 8) > 250 || Math.abs((long) z - 24) > 250 || economy.budget < 600)
            throw new IllegalArgumentException("Exchange needs $600 and a site within city limits");
        var road = nearest(x + 2.5f, z - .5f);
        if (road == null || Math.hypot(road.x() - x - 2, road.z() - z + 1) > 6)
            throw new IllegalArgumentException("Exchange needs road access near its front door");
        if (ground.occupied(x, grade + 1, z, 6, 7)
                || ground.playerOccupied(x, grade + 1, z, 6, 7)
                || economy.overlaps(x, z)
                || buildings.stream()
                        .anyMatch(
                                b ->
                                        x - 1 < b.x() + StructureBlueprint.width(b.type()) + 1
                                                && x + 7 > b.x() - 1
                                                && z - 1
                                                        < b.z()
                                                                + (b.type() == SpecialBuildings.AIRPORT ? Aviation.depth(Aviation.runways(b)) : StructureBlueprint.depth(b.type()))
                                                                + 1
                                                && z + 8 > b.z() - 1)
                || roads.keySet().stream()
                        .anyMatch(c -> c.x() >= x && c.x() < x + 6 && c.z() >= z && c.z() < z + 7))
            throw new IllegalArgumentException("Exchange site is occupied");
        var edits = new ArrayList<Protocol.Edit>();
        for (int dx = 0; dx < 6; dx++)
            for (int dz = -1; dz <= 7; dz++) level(x + dx, z + dz, edits);
        edits.addAll(StructureBlueprint.generate(SpecialBuildings.EXCHANGE, x, grade + 1, z));
        ground.apply(edits);
        economy.budget -= 600;
        var building = new CityFrame.Building(++buildingIds, 0, SpecialBuildings.EXCHANGE, x, grade + 1, z, 4, 0);
        buildings.add(building);
        addresses.state(buildings);
        assignExchange();
        refreshExchange();
        return "Stock exchange built for $600; four university graduate office roles";
    }

    private String rail(List<Point> points) {
        if (points.size() != 2) throw new IllegalArgumentException("Rails need two endpoints");
        int x=(int)Math.floor(points.get(0).x()), z=(int)Math.floor(points.get(0).z());
        int bx=(int)Math.floor(points.get(1).x()), bz=(int)Math.floor(points.get(1).z());
        if (Math.abs((long)x-bx)+Math.abs((long)z-bz)>256) throw new IllegalArgumentException("Rail too long: use shorter sections");
        var cells=new LinkedHashSet<Cell>();
        var route = RoadRoute.railPoints(points);
        for (int i = 1; i < route.size(); i++) {
            var a = route.get(i - 1);
            var b = route.get(i);
            x = (int) a.x(); z = (int) a.z();
            bx = (int) b.x(); bz = (int) b.z();
            while (true) {
                cells.add(new Cell(x, z));
                if (x == bx && z == bz) break;
                if (x != bx) x += Integer.signum(bx - x);
                else z += Integer.signum(bz - z);
            }
        }
        long fresh=cells.stream().filter(c->!railway.contains(c.x(),c.z())).count();
        if(railway.size()+fresh>8192) throw new IllegalArgumentException("Rail track limit reached");
        for(var c:cells) {
            if(Math.abs((long)c.x()-8)>256||Math.abs((long)c.z()-24)>256)throw new IllegalArgumentException("Rail outside city limits");
            if(roadContains(c))throw new IllegalArgumentException("Rail cannot cover roads");
            if(specialCell(c.x(),c.z())||buildings.stream().anyMatch(b->c.x()>=b.x()-1&&c.x()<=b.x()+StructureBlueprint.width(b.type())&&c.z()>=b.z()-2&&c.z()<=b.z()+StructureBlueprint.depth(b.type())))throw new IllegalArgumentException("Rail cannot cover buildings or entrances");
            if(economy.overlaps(c.x(),c.z(),1,1)||zones.stream().anyMatch(v->v.polygon().contains(c.x()+.5f,c.z()+.5f)))throw new IllegalArgumentException("Rail cannot cover owned plots or zones");
            if(ground.occupied(c.x(),grade+1,c.z(),1,1))throw new IllegalArgumentException("Rail would intersect a player");
            for(int y=grade+1;y<=Terrain.MAX_Y;y++)if(ground.type(c.x(),y,c.z())!=0)throw new IllegalArgumentException("Clear the rail site first");
        }
        if(!economy.roads((int)fresh*2))return "Mayor budget too low for rail: needs $"+(fresh*8);
        var edits=new ArrayList<Protocol.Edit>();for(var c:cells)if(!railway.contains(c.x(),c.z()))level(c.x(),c.z(),edits);
        ground.apply(edits);railway.add(cells,grade);
        return "Rail built | Mayor paid $"+(fresh*8);
    }

    private String road(List<Point> points) { return road(points, 0); }

    private String road(List<Point> points, int type) { return road(points,type,0); }

    private String road(List<Point> points, int type, int editing) {
        RoadTypes.validate(type);
        if (points.size() < 2) throw new IllegalArgumentException("Roads need two endpoints");
        points = RoadRoute.points(points);
        for (int i=1;i<points.size();i++)
            if ((int)Math.floor(points.get(i-1).x()) == (int)Math.floor(points.get(i).x())
                    && (int)Math.floor(points.get(i-1).z()) == (int)Math.floor(points.get(i).z()))
                throw new IllegalArgumentException("Road endpoints must differ");
        var surfaces = RoadGeometry.surfaces(points,type);
        var cells = surfaces.keySet();
        // Chaining can merge valid placements into a street larger than one placement.
        // Existing streets may be edited within the same bounded city/ownership capacity.
        if (cells.size() > (editing == 0 ? 768 : 8192)
                || roads.size() + cells.stream().filter(c -> !roadContains(c)).count() > 8192)
            throw new IllegalArgumentException("Road too long: use shorter sections");
        for (var cell : cells) {
            if (railway.contains(cell.x(), cell.z())) throw new IllegalArgumentException("Road cannot cover rails");
            if (specialCell(cell.x(), cell.z())) throw new IllegalArgumentException("Road cannot cover a special building");
            if (Math.abs(cell.x() - 8) > 256 || Math.abs(cell.z() - 24) > 256)
                throw new IllegalArgumentException("Road outside city limits");
            for (var zone : zones)
                if (zone.polygon().contains(cell.x() + .5f, cell.z() + .5f))
                    throw new IllegalArgumentException("Road would cross an existing zone");
            if (ground.occupied(cell.x(), grade + 1, cell.z(), 1, 1))
                throw new IllegalArgumentException("Road would intersect a player");
        }
        int newCells = (int) cells.stream().filter(c -> !roadContains(c)).count();
        int changedCells = (int) cells.stream().filter(c -> !roadContains(c)
                || roadTypes.getOrDefault(c, 0) != type).count();
        var nextAddresses = new CityAddresses(addresses.state(buildings));
        var existingStreet = addresses.state(buildings).nearest(points.get(0).x(), points.get(0).z());
        String name = editing != 0 ? addresses.state(buildings).streetName(editing) : newCells == 0 && existingStreet != null
                ? existingStreet.name() : nextAddresses.road(points, type);
        int streetId=editing!=0?editing:newCells==0 && existingStreet!=null?existingStreet.id():
                nextAddresses.state(buildings).streets().stream().filter(s->s.name().equals(name)).findFirst().orElseThrow().id();
        nextAddresses.paintRoad(streetId,type,surfaces,editing!=0);
        if (!founding && !economy.roads(changedCells))
            return "Mayor budget too low for road: needs $"
                    + (int) (changedCells * CityEconomy.ROAD_COST);
        var edits = new ArrayList<Protocol.Edit>();
        for (var c : cells) {
            if (!roadContains(c)) level(c.x(), c.z(), edits);
            // Repainting overlapping cells lets the mayor upgrade an existing dirt road.
            edits.add(new Protocol.Edit(c.x(), grade, c.z(), surfaces.get(c)));
            roads.put(c, grade);
            roadTypes.put(c, type);
        }
        ground.apply(edits);
        addresses = nextAddresses;
        pave(edits);
        ground.apply(edits);
        return founding
                ? RoadTypes.NAMES[type] + " built: " + name
                : RoadTypes.NAMES[type] + " built: "
                        + name
                        + " | Mayor paid $"
                        + (int) (changedCells * CityEconomy.ROAD_COST);
    }

    private String changeRoad(int id, int type) {
        var before=frame();
        var section=RoadGeometry.section(before,id);
        if(section.isEmpty()) return "Road section is no longer available";
        for(var r:section) if(ground.occupied(r.x(),r.y()+1,r.z(),1,1))
            return "Road would intersect a player";
        if(type>=0) {
            RoadTypes.validate(type);
            var street=before.addresses().streets().stream().filter(s->s.id()==id).findFirst().orElseThrow();
            var result=road(street.route(),type,id);
            if(!result.contains(" built:")) return result;
        } else addresses.removeStreet(id);
        // Repaint from surviving owners, including the hidden road under a crossing.
        var visible=RoadOwnership.visible(addresses.state(buildings).roadFootprints());
        var edits=new ArrayList<Protocol.Edit>();
        for(var r:section) {
            var cell=new Cell(r.x(),r.z()); var remaining=visible.get(cell);
            if(remaining==null) {
                roads.remove(cell); roadTypes.remove(cell);
                edits.add(new Protocol.Edit(r.x(),r.y(),r.z(),Blocks.DIRT));
            } else {
                roadTypes.put(cell,remaining.type());
                edits.add(new Protocol.Edit(r.x(),r.y(),r.z(),remaining.surface()));
            }
        }
        pave(edits);
        ground.apply(edits);
        return type<0 ? "Road section deleted" : "Road section edited: " + RoadTypes.NAMES[type];
    }

    private void pave(List<Protocol.Edit> edits) {
        var traffic = new RoadTraffic(addresses.state(buildings).streets(), roads.keySet());
        for (var c : roads.keySet()) {
            if (roadTypes.getOrDefault(c,0) != 0) continue;
            int y = roads.get(c);
            // Keep road and pavement headroom clear of neighbouring roof overhangs.
            if (buildings.stream().noneMatch(b -> c.x() >= b.x() && c.x() < b.x()+6
                    && c.z() >= b.z() && c.z() < b.z()+7)) {
                edits.add(new Protocol.Edit(c.x(),y+1,c.z(),Blocks.AIR));
                edits.add(new Protocol.Edit(c.x(),y+2,c.z(),Blocks.AIR));
            }
            edits.add(
                    new Protocol.Edit(
                            c.x(), y, c.z(), traffic.center(c) ? Blocks.DIRT : Blocks.STONE));
            if (traffic.center(c)) continue;
            // Two one-metre dirt lanes and two half-metre pavements fit the saved three-cell
            // strip.
            // The engine's existing half cubes preserve both the footprint and full-height footing.
            for (var adjacent : neighbours(c)) {
                if (!traffic.center(adjacent)) continue;
                int dx = adjacent.x() - c.x(), dz = adjacent.z() - c.z();
                for (int ix = 0; ix < 2; ix++)
                    for (int iz = 0; iz < 2; iz++)
                        if (dx != 0 && ix == (dx > 0 ? 1 : 0) || dz != 0 && iz == (dz > 0 ? 1 : 0))
                            for (int iy = 0; iy < 2; iy++)
                                edits.add(
                                        Protocol.Edit.at(
                                                c.x() + ix * .5,
                                                y + iy * .5,
                                                c.z() + iz * .5,
                                                Blocks.piece(Blocks.DIRT, 1),
                                                1));
            }
        }
    }

    private void level(int x, int z, List<Protocol.Edit> edits) {
        for (int y = Terrain.MAX_Y; y > grade; y--)
            if (ground.type(x, y, z) != 0) edits.add(new Protocol.Edit(x, y, z, 0));
        int bottom = grade;
        while (bottom > Terrain.MIN_Y && ground.type(x, bottom, z) == 0) bottom--;
        for (int y = bottom + 1; y < grade; y++) edits.add(new Protocol.Edit(x, y, z, Blocks.DIRT));
        edits.add(new Protocol.Edit(x, grade, z, Blocks.DIRT));
    }

    private String repartition(int id,int algorithm) {
        var old=zones.stream().filter(z->z.id()==id).findFirst().orElseThrow(()->new IllegalArgumentException("Zone not found"));
        if(buildings.stream().anyMatch(b->b.zone()==id)||economy.plots.stream().anyMatch(p->p.zone()==id))
            throw new IllegalArgumentException("Parcel changes need a zone without buildings or purchased plots");
        var draft=new CityFrame.Zone(id,old.type(),old.polygon(),algorithm,old.parcels());
        var layout=ZoneParceling.generate(draft,roads.keySet(),this::parcelCost);
        var updated=new CityFrame.Zone(id,old.type(),old.polygon(),algorithm,layout);
        zones.set(zones.indexOf(old),updated);
        return parcelReport(updated);
    }
    private double parcelCost(Cell c) {
        int h=terrain.surfaceHeight(c.x(),c.z());
        return 1 + ParcelPortfolio.neighbours(c).stream()
            .mapToInt(n->Math.abs(h-terrain.surfaceHeight(n.x(),n.z()))).max().orElse(0);
    }
    private String parcelReport(CityFrame.Zone zone) {
        int sites=ZoneParceling.sites(zone).size();
        return ParcelPortfolio.Algorithm.values()[zone.algorithm()].label
            + ": " + zone.parcels().size() + " parcels, " + sites + " building sites"
            + (sites==0?"; try merge or another layout":"");
    }
    private String zone(int type, Polygon polygon) { return zone(type,polygon,-1); }
    private String zone(int type, Polygon polygon,int algorithm) {
        if (type < 0 || type > 3 || zones.size() >= 128)
            throw new IllegalArgumentException("Invalid zone type or city zone limit reached");
        var cells = polygon.cells();
        boolean adjacent = false;
        for (var c : cells) {
            if (railway.contains(c.x(), c.z())) throw new IllegalArgumentException("Zones cannot cover rails");
            if (specialCell(c.x(), c.z())) throw new IllegalArgumentException("Zones cannot cover special buildings");
            if (roadContains(c))
                throw new IllegalArgumentException("Zones cannot cover roads");
            if (neighbours(c).stream().anyMatch(roads::containsKey)) adjacent = true;
            for (var z : zones)
                if (z.polygon().contains(c.x() + .5f, c.z() + .5f))
                    throw new IllegalArgumentException("Zones cannot overlap");
        }
        if (!adjacent) throw new IllegalArgumentException("Zone must touch a dirt road");
        boolean fits = false;
        for (var c : cells)
            if (fits(polygon, c.x(), c.z(), type)) {
                fits = true;
                break;
            }
        if (!fits)
            throw new IllegalArgumentException(
                    type == 3
                            ? "Leave room for a 12 x 14 farm and its entrance"
                            : "Leave room for a 6 x 7 building and its entrance");
        var added=new CityFrame.Zone(zoneIds+1,type,polygon);
        if(algorithm>=0) {
            var draft=new CityFrame.Zone(zoneIds+1,type,polygon,algorithm,List.of());
            added=new CityFrame.Zone(draft.id(),type,polygon,algorithm,
                ZoneParceling.generate(draft,roads.keySet(),this::parcelCost));
        }
        zoneIds++;zones.add(added);
        if(algorithm>=0)return ZONES[type]+" zone created; "+parcelReport(added);
        return ZONES[type]
                + (type == 3
                        ? " zone created; farming families assess demand"
                        : " zone created; private developers assess demand");
    }

    private boolean fits(Polygon polygon, int x, int z) {
        return fits(polygon, x, z, 0);
    }

    private boolean fits(Polygon polygon, int x, int z, int type) {
        int width = StructureBlueprint.width(type), depth = StructureBlueprint.depth(type);
        for (int dx = 0; dx < width; dx++)
            for (int dz = -1; dz <= depth; dz++)
                if (!polygon.contains(x + dx + .5f, z + dz + .5f)) return false;
        if (economy != null && economy.overlaps(x, z, width, depth)) return false;
        for (var b : buildings)
            if (x - 1 < b.x() + StructureBlueprint.width(b.type()) + 1
                    && x + width + 1 > b.x() - 1
                    && z - 2 < b.z() + (b.type() == SpecialBuildings.AIRPORT ? Aviation.depth(Aviation.runways(b)) : StructureBlueprint.depth(b.type())) + 1
                    && z + depth + 1 > b.z() - 2) return false;
        return true;
    }

    private final int[] gridConstructionCursor = {0,400_000,600_000,800_000};
    private Iterable<CityFrame.Zone> constructionZones() {
        if (stressGrid == null) return zones;
        var candidates = new ArrayList<CityFrame.Zone>();
        int[] begin = {0,400_000,600_000,800_000}, end = {400_000,600_000,800_000,1_000_000};
        for (int type=0; type<4; type++) if (demand(type)) {
            // Advance only when visited: unvisited candidates must not be lost on a successful purchase.
            int rank=gridConstructionCursor[type];
            for (int n=0; n<64; n++) {
                candidates.add(stressGrid.zone(StressGrid.indexForRank(rank)));
                rank++; if(rank==end[type]) rank=begin[type];
            }
        }
        return candidates;
    }

    private boolean visitConstructionZone(CityFrame.Zone zone) {
        if (stressGrid != null) {
            int index=zone.id()-1, type=zone.type();
            int next=StressGrid.rank(index%1000,index/1000)+1;
            int[] begin={0,400_000,600_000,800_000}, end={400_000,600_000,800_000,1_000_000};
            gridConstructionCursor[type]=next==end[type]?begin[type]:next;
        }
        return true;
    }

    private List<Cell> availableParcelSites(CityFrame.Zone zone) {
        if(zone.parcels().isEmpty())return ZoneParceling.sites(zone);
        var occupied=new HashSet<Cell>();
        for(var p:economy.plots)if(p.zone()==zone.id())occupied.add(new Cell(p.x(),p.z()));
        for(var b:buildings)if(b.zone()==zone.id())occupied.add(new Cell(b.x(),b.z()));
        var result=new ArrayList<Cell>();
        for(var parcel:zone.parcels()) {
            if(occupied.stream().anyMatch(parcel.cells()::contains))continue;
            for(var c:parcel.cells())if(ZoneParceling.fits(parcel,c.x(),c.z(),zone.type()))result.add(c);
        }
        return result;
    }

    private void construct() {
        if (buildings.size() + economy.plots.stream().filter(p -> p.building() == 0).count() >= 512)
            return;
        for (var zone : constructionZones())
            if (visitConstructionZone(zone) && demand(zone.type()))
                for (var c : availableParcelSites(zone))
                    if (fits(zone.polygon(), c.x(), c.z(), zone.type())) {
                        int x = c.x(), z = c.z();
                        int width = StructureBlueprint.width(zone.type()),
                                depth = StructureBlueprint.depth(zone.type());
                        if (ground.occupied(x, grade + 1, z, width, depth)) continue;
                        var edits = new ArrayList<Protocol.Edit>();
                        for (int dx = 0; dx < width; dx++)
                            for (int dz = -1; dz <= depth; dz++) level(x + dx, z + dz, edits);
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
                                if (!roadContains(cell)) level(cell.x(), cell.z(), edits);
                        }
                        int purpose =
                                zone.type() == 2
                                        ? industrialDemand()
                                        : zone.type() == 3 ? agriculturalDemand() : zone.type();
                        var plot =
                                economy.buyPlot(zone.id(), zone.type(), x, grade + 1, z, purpose);
                        if (plot == null) return;
                        if (zone.type() == 2 || zone.type() == 3) {
                            economy.resources.plan(plot, purpose);
                        } else economy.resources.plan(plot, zone.type());
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
                            .filter(b -> b.type() == 0 || b.type() == 3)
                            .mapToInt(CityFrame.Building::capacity)
                            .sum();
            return capacity + projects * 4 < ecs.query(Household.class).size();
        }
        if (type == 2) return industrialDemand() != 0;
        if (type == 3) return agriculturalDemand() != 0;
        return projects == 0 && buildings.stream().noneMatch(b -> b.type() == type);
    }

    private int agriculturalDemand() {
        if (!agriculture.enabled()) return 0;
        for (int kind :
                new int[] {
                    CityMaterials.FARM, CityMaterials.SUGARCANE_FARM, CityMaterials.CATTLE_FARM
                }) {
            int k = kind;
            if (economy.companies().stream().noneMatch(c -> c.kind == k)) continue;
            long plots =
                    economy.plots.stream()
                            .filter(p -> p.type() == 3 && economy.company(p.developer()).kind == k)
                            .count();
            if (plots == 0) return k;
        }
        // Expansion is a second paid plot, triggered by low stock after the founding day.
        if (elapsed > config.daySeconds())
            for (int kind : new int[] {CityMaterials.FARM, CityMaterials.SUGARCANE_FARM}) {
                int k = kind;
                var firm =
                        economy.companies().stream()
                                .filter(f -> f.kind == k)
                                .findFirst()
                                .orElse(null);
                if (firm == null) continue;
                if (economy.plots.stream()
                                        .filter(p -> p.type() == 3 && p.developer() == firm.id)
                                        .count()
                                < 2
                        && economy.resources.available(
                                        0,
                                        firm.id,
                                        kind == CityMaterials.FARM
                                                ? CityMaterials.WHEAT
                                                : CityMaterials.SUGARCANE)
                                < 64 * CityMaterials.UNIT) return kind;
            }
        return 0;
    }

    private int industrialDemand() {
        for (var type : economy.resources.catalog.businesses().types()) {
            int kind = type.id();
            if (kind < 2 || !IndustrialProgression.unlocked(economy.resources, kind)
                    || economy.companies().stream().noneMatch(c -> c.kind == kind)) continue;
            if (agriculture.enabled() && CityMaterials.farmer(kind)) continue;
            final int k = kind;
            boolean exists =
                    economy.plots.stream()
                                    .anyMatch(
                                            p ->
                                                    p.type() == 2
                                                            && (economy.resources.project(p.id())
                                                                                    == null
                                                                            ? 2
                                                                            : economy.resources
                                                                                    .project(p.id())
                                                                                    .businessKind())
                                                                    == k)
                            || buildings.stream()
                                    .anyMatch(
                                            b ->
                                                    b.type() == 2
                                                            && economy.property(b.id()) != null
                                                            && economy.company(
                                                                            economy.property(b.id())
                                                                                    .operator())
                                                                    != null
                                                            && economy.company(
                                                                                    economy.property(
                                                                                                    b
                                                                                                            .id())
                                                                                            .operator())
                                                                            .kind
                                                                    == k);
            if (!exists) return k;
        }
        return 0;
    }

    private void finishProjects() {
        for (var p : new ArrayList<>(economy.plots))
            if (p.building() == 0 && p.work() >= 8 && ready(p)) {
                if (ground.playerOccupied(
                        p.x(),
                        p.y(),
                        p.z(),
                        StructureBlueprint.width(p.type()),
                        StructureBlueprint.depth(p.type()))) continue;
                for (int id : ecs.query(Household.class, Position.class)) {
                    var pos = ecs.get(id, Position.class);
                    boolean inside = pos.x > p.x() && pos.x < p.x() + 6
                            && pos.z > p.z() && pos.z < p.z() + 7;
                    boolean porchBeam = (Math.floor(pos.x) == p.x() || Math.floor(pos.x) == p.x() + 5)
                            && Math.floor(pos.z) == p.z() - 1;
                    if (inside || porchBeam) {
                        // Move construction workers onto the completed building's interior floor.
                        // Its aisle keeps their assigned stations clear; no enclosing wall is
                        // placed over them. Include the north porch beams, which occupy
                        // the voxel row immediately outside the main building footprint.
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
                ground.apply(
                        StructureBlueprint.generate(
                                p.type(),
                                economy.resources.project(p.id()).businessKind(),
                                p.x(),
                                p.y(),
                                p.z()));
                if (!economy.resources.consume(p))
                    throw new IllegalStateException("Missing reserved materials");
                var b =
                        new CityFrame.Building(
                                ++buildingIds,
                                p.zone(),
                                p.type(),
                                p.x(),
                                p.y(),
                                p.z(),
                                p.type() == 3 ? 2 : p.type() == 0
                                        ? economy.resources.project(p.id()).businessKind() == 50 ? 8 : 4 : 16,
                                0);
                buildings.add(b);
                migrateLanePavements=true;
                economy.completed(p, b.id());
                agriculture.completed(b, economy);
                for (int id : ecs.query(Household.class)) {
                    var h = ecs.get(id, Household.class);
                    if (h.job == -p.id()) {
                        h.job = 0;
                        var t = ecs.get(id, Travel.class);
                        t.target = -9999;
                        t.route.clear();t.clearRoadLanes();
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
        roads.forEach((c, y) -> rs.add(new CityFrame.Road(c.x(), c.z(), y, roadTypes.getOrDefault(c, 0))));
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
                            t.activity, life(id).age, life(id).gender, life(id).education,
                            life(id).study, life(id).spouse, life(id).mother, life(id).father,
                            life(id).school, life(id).lastBirthAge));
        }
        var hs = new ArrayList<CityFrame.Horse>();
        for (int id : ecs.query(Mount.class)) {
            var p = ecs.get(id, Position.class);
            hs.add(
                    new CityFrame.Horse(
                            id, p.x, p.y, p.z, p.yaw, p.phase, ecs.get(id, Mount.class).rider));
        }
        return new CityFrame(
                config,
                elapsed,
                rs,
                zones,
                buildings,
                cs,
                hs,
                economy.state(),
                addresses.state(buildings),
                agriculture.state(), population.state(), new Aviation.State(flights), railway.state(), stressGrid);
    }

    public static CityFrame load(Path file) throws IOException {
        if (file == null || !Files.exists(file)) return null;
        try (var in = new DataInputStream(Files.newInputStream(file))) {
            int magic = in.readInt();
            if (magic != 0x43495431
                    && magic != 0x43495432
                    && magic != 0x43495433
                    && magic != 0x43495434
                    && magic != 0x43495435
                    && magic != 0x43495436
                    && magic != 0x43495437
                    && magic != 0x43495438
                    && magic != 0x43495439
                    && magic != 0x4349543A
                    && magic != 0x4349543B && magic != 0x4349543C && magic != 0x4349543D && magic != 0x4349543E && magic != 0x4349543F && magic != 0x43495440 && magic != 0x43495441) throw new IOException("Invalid city save");
            return CityFrame.read(
                    in,
                    magic == 0x43495431
                            ? 1
                            : magic == 0x43495432
                                    ? 2
                                    : magic == 0x43495433
                                            ? 3
                                            : magic == 0x43495434
                                                    ? 4
                                                    : magic == 0x43495435
                                                            ? 5
                                                            : magic == 0x43495436 ? 6 : magic == 0x43495437 ? 7 : magic == 0x43495438 ? 8 : magic == 0x43495439 ? 9 : magic == 0x4349543A ? 10 : magic == 0x4349543B ? 11 : magic == 0x4349543C ? 12 : magic == 0x4349543D ? 13 : magic == 0x4349543E ? 14 : magic == 0x4349543F ? 15 : magic == 0x43495440 ? 16 : 17);
        }
    }

    public void save(Path file) throws IOException {
        if (file == null) return;
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (var out = new DataOutputStream(Files.newOutputStream(tmp))) {
            boolean parcels=stressGrid == null && zones.stream().anyMatch(z->!z.parcels().isEmpty());
            out.writeInt(0x43495441);
            frame().write(out,17);
        }
        try {
            Files.move(
                    tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
