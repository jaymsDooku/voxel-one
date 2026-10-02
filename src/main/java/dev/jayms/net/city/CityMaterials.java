package dev.jayms.net.city;

import dev.jayms.net.*;

import java.io.*;
import java.util.*;

/** Privately owned, volume-exact supplies. Reserved stock still belongs to its developer. */
public final class CityMaterials {
    public static final long UNIT = 4096;
    public static final int LOGGING = 3,
            MASONRY = 4,
            GLASSWORKS = 5,
            LIGHTING = 6,
            FARM = 7,
            TOOLS = 8,
            SUGARCANE_FARM = 9,
            CATTLE_FARM = 10,
            MILL = 11,
            BAKERY = 12,
            SUGARWORKS = 13,
            CAKE_FACTORY = 14;
    public static final int MAX_KIND = CAKE_FACTORY;
    public static final int WHEAT = 1101,
            CARROT = 1102,
            SUGARCANE = 1103,
            BEEF = 1104,
            MILK = 1105,
            FLOUR = 1106,
            BREAD = 1107,
            SUGAR = 1108,
            CARROT_CAKE = 1109;

    public static boolean farmer(int kind) {
        return kind == FARM || kind == SUGARCANE_FARM || kind == CATTLE_FARM;
    }

    public static final int FOOD = 1001, PICKAXE = 1002, AXE = 1003, YARD = 100000;

    public record Amount(int material, long units) {}

    public record Stock(int ownerKind, int owner, int material, long units) {}

    public record Production(
            int company, double progress, long harvested, long processed, String status) {}

    public record Batch(int company, String recipe, double progress, long completed) {}

    public record Project(
            int plot,
            int businessKind,
            boolean reserved,
            boolean consumed,
            List<Amount> materials) {
        public Project {
            materials = List.copyOf(materials);
        }
    }

    public record State(
            List<Stock> stocks,
            List<Production> production,
            List<Project> projects,
            ProductionCatalog catalog,
            List<Batch> batches) {
        public State(List<Stock> stocks, List<Production> production, List<Project> projects) {
            this(stocks, production, projects, ProductionCatalog.cityGame(), List.of());
        }

        public State(
                List<Stock> stocks,
                List<Production> production,
                List<Project> projects,
                ProductionCatalog catalog) {
            this(stocks, production, projects, catalog, List.of());
        }

        public State {
            stocks = List.copyOf(stocks);
            production = List.copyOf(production);
            projects = List.copyOf(projects);
            batches = List.copyOf(batches);
        }

        public static State empty() {
            return new State(List.of(), List.of(), List.of());
        }

        public long available(int kind, int owner, int material) {
            return stocks.stream()
                    .filter(s -> s.ownerKind == kind && s.owner == owner && s.material == material)
                    .mapToLong(Stock::units)
                    .sum();
        }

        public Project project(int plot) {
            return projects.stream().filter(p -> p.plot == plot).findFirst().orElse(null);
        }
    }

    private record Key(int kind, int owner, int material) {}

    private final Map<Key, Long> stocks = new LinkedHashMap<>();
    private final Map<Integer, Production> production = new LinkedHashMap<>();
    private final Map<Integer, Project> projects = new LinkedHashMap<>();
    public ProductionCatalog catalog;
    private final Map<String, Batch> batches = new LinkedHashMap<>();

    public CityMaterials(State state) {
        catalog = state.catalog;
        for (var s : state.stocks) stocks.put(new Key(s.ownerKind, s.owner, s.material), s.units);
        for (var p : state.production) production.put(p.company, p);
        for (var p : state.projects) projects.put(p.plot, p);
        for (var b : state.batches) batches.put(b.company + ":" + b.recipe, b);
    }

    public static String sector(int kind) {
        return switch (kind) {
            case 0 -> "Property developer";
            case 1 -> "Food market";
            case 2 -> "Stone quarry";
            case LOGGING -> "Logging mill";
            case MASONRY -> "Brickworks";
            case GLASSWORKS -> "Sand pit / glassworks";
            case LIGHTING -> "Lighting workshop";
            case FARM -> "Crop farm";
            case TOOLS -> "Tool factory";
            case SUGARCANE_FARM -> "Sugarcane farm";
            case CATTLE_FARM -> "Cattle farm";
            case MILL -> "Flour mill";
            case BAKERY -> "Bakery";
            case SUGARWORKS -> "Sugar refinery";
            case CAKE_FACTORY -> "Cake bakery";
            default -> "Business";
        };
    }

    public static String name(int material) {
        return ProductionCatalog.cityGame().name(material);
    }

    public static String quantity(long units) {
        return units % UNIT == 0
                ? Long.toString(units / UNIT)
                : String.format(Locale.ROOT, "%.3f", units / (double) UNIT);
    }

    public static int output(int kind) {
        return switch (kind) {
            case LOGGING -> Blocks.PLANKS;
            case MASONRY -> Blocks.BRICKS;
            case GLASSWORKS -> Blocks.GLASS;
            case LIGHTING -> Blocks.LED;
            case 1, FARM -> FOOD;
            case TOOLS -> PICKAXE;
            case SUGARCANE_FARM -> SUGARCANE;
            case CATTLE_FARM -> MILK;
            default -> Blocks.STONE;
        };
    }

    public static int capacity(int kind) {
        return kind == TOOLS ? 16 : kind == LIGHTING ? 32 : 512;
    }

    public static double price(int material) {
        return ProductionCatalog.cityGame().price(material);
    }

    public long available(int kind, int owner, int material) {
        return stocks.getOrDefault(new Key(kind, owner, material), 0L);
    }

    public void add(int kind, int owner, int material, long units) {
        if (kind < 0
                || kind > 1
                || owner < 1
                || !catalog.valid(material)
                || units < 0
                || units > 1_000_000_000L)
            throw new IllegalArgumentException("Invalid material stock");
        long next = Math.addExact(available(kind, owner, material), units);
        if (next > 1_000_000_000L) throw new IllegalArgumentException("Material storage full");
        if (next > 0) stocks.put(new Key(kind, owner, material), next);
    }

    public boolean remove(int kind, int owner, int material, long units) {
        if (units <= 0 || available(kind, owner, material) < units) return false;
        var key = new Key(kind, owner, material);
        long next = stocks.get(key) - units;
        if (next == 0) stocks.remove(key);
        else stocks.put(key, next);
        return true;
    }

    public Project plan(CityEconomy.Plot plot, int businessKind) {
        return projects.computeIfAbsent(
                plot.id(),
                id ->
                        new Project(
                                id,
                                businessKind,
                                false,
                                false,
                                requirements(plot.type(), businessKind)));
    }

    public Project project(int plot) {
        return projects.get(plot);
    }

    public boolean reserve(CityEconomy.Plot plot) {
        var p = plan(plot, plot.type());
        if (p.consumed) return false;
        if (p.reserved) return true;
        if (p.materials.stream()
                .anyMatch(
                        a ->
                                available(CityEconomy.COMPANY, plot.developer(), a.material)
                                        < a.units)) return false;
        for (var a : p.materials)
            remove(CityEconomy.COMPANY, plot.developer(), a.material, a.units);
        projects.put(p.plot, new Project(p.plot, p.businessKind, true, false, p.materials));
        return true;
    }

    public boolean consume(CityEconomy.Plot plot) {
        var p = projects.get(plot.id());
        if (p == null || !p.reserved || p.consumed) return false;
        projects.put(p.plot, new Project(p.plot, p.businessKind, false, true, p.materials));
        return true;
    }

    /** Use the same recipes as player crafting; work cannot create inputs that are missing. */
    public int craft(int company, int output) {
        var configured =
                catalog.recipes().stream()
                        .filter(r -> r.output() == output)
                        .findFirst()
                        .orElse(null);
        if (configured != null) return craft(company, configured);
        return 0;
    }

    /** A complete batch is checked before any input is consumed. */
    public int craft(int company, ProductionCatalog.Recipe recipe) {
        if (!catalog.recipes().contains(recipe)
                || available(0, company, recipe.output()) + recipe.count() * UNIT
                        > recipe.capacity() * UNIT
                || recipe.inputs().entrySet().stream()
                        .anyMatch(e -> available(0, company, e.getKey()) < e.getValue() * UNIT))
            return 0;
        for (var e : recipe.inputs().entrySet())
            remove(0, company, e.getKey(), e.getValue() * UNIT);
        add(0, company, recipe.output(), recipe.count() * UNIT);
        return recipe.count();
    }

    public double productivity(int company, int kind) {
        var equipment = catalog.equipment(kind);
        return equipment != null && available(0, company, equipment.product()) >= UNIT
                ? equipment.multiplier()
                : 1;
    }

    public Batch batch(int company, String recipe) {
        return batches.getOrDefault(company + ":" + recipe, new Batch(company, recipe, 0, 0));
    }

    public void batch(int company, String recipe, double progress, long completed) {
        var old = batch(company, recipe);
        batches.put(
                company + ":" + recipe,
                new Batch(company, recipe, progress, old.completed + completed));
    }

    public Production production(int company) {
        return production.getOrDefault(company, new Production(company, 0, 0, 0, "Awaiting crew"));
    }

    public void production(
            int company, double progress, long harvested, long processed, String status) {
        var old = production(company);
        production.put(
                company,
                new Production(
                        company,
                        progress,
                        old.harvested + harvested,
                        old.processed + processed,
                        status));
    }

    public State state() {
        return new State(
                stocks.entrySet().stream()
                        .map(
                                e ->
                                        new Stock(
                                                e.getKey().kind,
                                                e.getKey().owner,
                                                e.getKey().material,
                                                e.getValue()))
                        .toList(),
                List.copyOf(production.values()),
                List.copyOf(projects.values()),
                catalog,
                List.copyOf(batches.values()));
    }

    public static List<Amount> requirements(int type) {
        return requirements(type, type);
    }

    public static List<Amount> requirements(int type, int businessKind) {
        var cells = new LinkedHashMap<String, Protocol.Edit>();
        for (var e : StructureBlueprint.generate(type, businessKind, 0, 0, 0))
            cells.put(
                    e.x() + ":" + e.y() + ":" + e.z() + ":" + e.depth() + ":" + e.ix() + ":"
                            + e.iy() + ":" + e.iz(),
                    e);
        var totals = new TreeMap<Integer, Long>();
        for (var e : cells.values())
            if (e.type() != Blocks.AIR)
                totals.merge(Blocks.material(e.type()), UNIT >> (e.depth() * 3), Long::sum);
        return totals.entrySet().stream().map(e -> new Amount(e.getKey(), e.getValue())).toList();
    }

    static boolean buildingMaterial(int id) {
        return id == Blocks.DIRT
                || id == Blocks.STONE
                || id == Blocks.SAND
                || id == Blocks.WOOD
                || id == Blocks.PLANKS
                || id == Blocks.BRICKS
                || id == Blocks.GLASS
                || id == Blocks.LED;
    }

    public static void write(DataOutput out, State state) throws IOException {
        write(out, state, 6);
    }

    public static void write(DataOutput out, State state, int version) throws IOException {
        out.writeInt(state.stocks.size());
        for (var s : state.stocks) {
            out.writeByte(s.ownerKind);
            out.writeInt(s.owner);
            out.writeInt(s.material);
            out.writeLong(s.units);
        }
        out.writeInt(state.production.size());
        for (var p : state.production) {
            out.writeInt(p.company);
            out.writeDouble(p.progress);
            out.writeLong(p.harvested);
            out.writeLong(p.processed);
            out.writeUTF(p.status);
        }
        out.writeInt(state.projects.size());
        for (var p : state.projects) {
            out.writeInt(p.plot);
            out.writeByte(p.businessKind);
            out.writeBoolean(p.reserved);
            out.writeBoolean(p.consumed);
            out.writeInt(p.materials.size());
            for (var a : p.materials) {
                out.writeInt(a.material);
                out.writeLong(a.units);
            }
        }
        if (version >= 5) {
            state.catalog.write(out, version);
            out.writeInt(state.batches.size());
            for (var b : state.batches) {
                out.writeInt(b.company);
                out.writeUTF(b.recipe);
                out.writeDouble(b.progress);
                out.writeLong(b.completed);
            }
        }
    }

    public static State read(DataInput in) throws IOException {
        return read(in, 6);
    }

    public static State read(DataInput in, int version) throws IOException {
        var stocks = new ArrayList<Stock>();
        var keys = new HashSet<Key>();
        for (int i = 0, n = count(in, 4096); i < n; i++) {
            int kind = in.readUnsignedByte(), owner = positive(in), material = in.readInt();
            long units = units(in);
            if (kind > 1 || !keys.add(new Key(kind, owner, material)))
                throw new IOException("Invalid material owner / duplicate stock");
            stocks.add(new Stock(kind, owner, material, units));
        }
        var production = new ArrayList<Production>();
        var companyIds = new HashSet<Integer>();
        for (int i = 0, n = count(in, 64); i < n; i++) {
            int company = positive(in);
            double progress = in.readDouble();
            long harvested = in.readLong(), processed = in.readLong();
            String status = in.readUTF();
            if (!Double.isFinite(progress)
                    || progress < 0
                    || progress >= 1
                    || harvested < 0
                    || processed < 0
                    || status.length() > 64
                    || !companyIds.add(company))
                throw new IOException("Invalid resource production");
            production.add(new Production(company, progress, harvested, processed, status));
        }
        var projects = new ArrayList<Project>();
        var plotIds = new HashSet<Integer>();
        for (int i = 0, n = count(in, 512); i < n; i++) {
            int plot = positive(in), kind = in.readUnsignedByte();
            boolean reserved = in.readBoolean(), consumed = in.readBoolean();
            if (kind > MAX_KIND || reserved && consumed || !plotIds.add(plot))
                throw new IOException("Invalid material reservation");
            var materials = new ArrayList<Amount>();
            var materialIds = new HashSet<Integer>();
            for (int j = 0, m = count(in, 8); j < m; j++) {
                int material = in.readInt();
                long units = units(in);
                if (!buildingMaterial(material) || !materialIds.add(material))
                    throw new IOException("Invalid building material");
                materials.add(new Amount(material, units));
            }
            projects.add(new Project(plot, kind, reserved, consumed, materials));
        }
        var catalog =
                version >= 5 ? ProductionCatalog.read(in, version) : ProductionCatalog.toolEra();
        for (var stock : stocks)
            if (!catalog.valid(stock.material)) throw new IOException("Unknown stock product");
        var batches = new ArrayList<Batch>();
        var batchKeys = new HashSet<String>();
        if (version >= 5)
            for (int n = count(in, 1024); n > 0; n--) {
                int company = positive(in);
                String recipe = in.readUTF();
                double progress = in.readDouble();
                long completed = in.readLong();
                if (!batchKeys.add(company + ":" + recipe)
                        || catalog.recipes().stream().noneMatch(r -> r.id().equals(recipe))
                        || !Double.isFinite(progress)
                        || progress < 0
                        || progress >= 1
                        || completed < 0) throw new IOException("Invalid manufacturing batch");
                batches.add(new Batch(company, recipe, progress, completed));
            }
        return new State(stocks, production, projects, catalog, batches);
    }

    private static int positive(DataInput in) throws IOException {
        int value = in.readInt();
        if (value < 1) throw new IOException("Invalid material reference");
        return value;
    }

    private static int count(DataInput in, int max) throws IOException {
        int value = in.readInt();
        if (value < 0 || value > max) throw new IOException("Invalid material count");
        return value;
    }

    private static long units(DataInput in) throws IOException {
        long value = in.readLong();
        if (value <= 0 || value > 1_000_000_000L)
            throw new IOException("Invalid material quantity");
        return value;
    }
}
