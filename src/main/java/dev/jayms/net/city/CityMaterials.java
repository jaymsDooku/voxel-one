package dev.jayms.net.city;

import dev.jayms.net.*;

import java.io.*;
import java.util.*;

/** Privately owned, volume-exact supplies. Reserved stock still belongs to its developer. */
public final class CityMaterials {
    public static final long UNIT = 4096;
    public static final int LOGGING = 3, MASONRY = 4, GLASSWORKS = 5, LIGHTING = 6, FARM = 7;
    public static final int FOOD = 1001, YARD = 100000;

    public record Amount(int material, long units) {}

    public record Stock(int ownerKind, int owner, int material, long units) {}

    public record Production(
            int company, double progress, long harvested, long processed, String status) {}

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

    public record State(List<Stock> stocks, List<Production> production, List<Project> projects) {
        public State {
            stocks = List.copyOf(stocks);
            production = List.copyOf(production);
            projects = List.copyOf(projects);
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

    public CityMaterials(State state) {
        for (var s : state.stocks) stocks.put(new Key(s.ownerKind, s.owner, s.material), s.units);
        for (var p : state.production) production.put(p.company, p);
        for (var p : state.projects) projects.put(p.plot, p);
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
            default -> "Business";
        };
    }

    public static String name(int material) {
        return material == FOOD ? "Food" : Blocks.name(material);
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
            default -> Blocks.STONE;
        };
    }

    public static int capacity(int kind) {
        return kind == LIGHTING ? 32 : 512;
    }

    public static double price(int material) {
        return material == Blocks.LED
                ? .8
                : material == FOOD || material == Blocks.WOOD
                        ? .5
                        : material == Blocks.GLASS ? .4 : .3;
    }

    public long available(int kind, int owner, int material) {
        return stocks.getOrDefault(new Key(kind, owner, material), 0L);
    }

    public void add(int kind, int owner, int material, long units) {
        if (kind < 0
                || kind > 1
                || owner < 1
                || !validMaterial(material)
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
        var recipe =
                Crafting.recipes().stream()
                        .filter(
                                r ->
                                        r.output() == output
                                                && r.inputs().keySet().stream()
                                                        .noneMatch(Blocks::isPiece))
                        .findFirst()
                        .orElse(null);
        if (recipe == null
                || recipe.inputs().entrySet().stream()
                        .anyMatch(e -> available(0, company, e.getKey()) < e.getValue() * UNIT))
            return 0;
        for (var e : recipe.inputs().entrySet())
            remove(0, company, e.getKey(), e.getValue() * UNIT);
        add(0, company, output, recipe.count() * UNIT);
        return recipe.count();
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
                List.copyOf(projects.values()));
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

    private static boolean validMaterial(int id) {
        return id == FOOD
                || id == Blocks.STONE
                || id == Blocks.SAND
                || id == Blocks.WOOD
                || id == Blocks.PLANKS
                || id == Blocks.BRICKS
                || id == Blocks.GLASS
                || id == Blocks.LED;
    }

    public static void write(DataOutput out, State state) throws IOException {
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
    }

    public static State read(DataInput in) throws IOException {
        var stocks = new ArrayList<Stock>();
        var keys = new HashSet<Key>();
        for (int i = 0, n = count(in, 4096); i < n; i++) {
            int kind = in.readUnsignedByte(), owner = positive(in), material = in.readInt();
            long units = units(in);
            if (kind > 1 || !validMaterial(material) || !keys.add(new Key(kind, owner, material)))
                throw new IOException("Invalid material owner / duplicate stock");
            stocks.add(new Stock(kind, owner, material, units));
        }
        var production = new ArrayList<Production>();
        var companyIds = new HashSet<Integer>();
        for (int i = 0, n = count(in, 16); i < n; i++) {
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
            if (kind > FARM || reserved && consumed || !plotIds.add(plot))
                throw new IOException("Invalid material reservation");
            var materials = new ArrayList<Amount>();
            var materialIds = new HashSet<Integer>();
            for (int j = 0, m = count(in, 8); j < m; j++) {
                int material = in.readInt();
                long units = units(in);
                if (!validMaterial(material) || material == FOOD || !materialIds.add(material))
                    throw new IOException("Invalid building material");
                materials.add(new Amount(material, units));
            }
            projects.add(new Project(plot, kind, reserved, consumed, materials));
        }
        return new State(stocks, production, projects);
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
