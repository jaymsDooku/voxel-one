package dev.jayms.net.city;

import dev.jayms.net.Blocks;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Game-owned business and manufacturing data. Quantities use CityMaterials.UNIT. */
public record ProductionCatalog(
        List<Product> products,
        List<Recipe> recipes,
        List<Equipment> equipment,
        BusinessCatalog businesses) {
    public ProductionCatalog(
            List<Product> products, List<Recipe> recipes, List<Equipment> equipment) {
        this(
                products,
                recipes,
                equipment,
                BusinessCatalog.defaults(
                        products.stream().anyMatch(p -> p.id() == CityMaterials.WHEAT)));
    }

    public record Product(int id, String name, double price, int nutrition) {
        public Product(int id, String name, double price) {
            this(id, name, price, id == CityMaterials.FOOD ? 35 : 0);
        }
    }

    public record Recipe(
            String id,
            int companyKind,
            int output,
            int count,
            Map<Integer, Integer> inputs,
            double batchesPerHour,
            int capacity,
            boolean requiresFactory) {
        public Recipe {
            inputs = Collections.unmodifiableMap(new TreeMap<>(inputs));
        }
    }

    public record Equipment(int product, int companyKind, double multiplier) {}

    public ProductionCatalog {
        products = List.copyOf(products);
        recipes = List.copyOf(recipes);
        equipment = List.copyOf(equipment);
        if (products.size() > 64 || recipes.size() > 64 || equipment.size() > 32)
            throw new IllegalArgumentException("Production catalog too large");
        var ids = new HashSet<Integer>();
        for (var p : products)
            if (p.id < 1001
                    || p.id > 4095
                    || !ids.add(p.id)
                    || p.name.isBlank()
                    || p.name.length() > 48
                    || !Double.isFinite(p.price)
                    || p.price <= 0
                    || p.price > 10000
                    || p.nutrition < 0
                    || p.nutrition > 100) throw new IllegalArgumentException("Invalid product");
        if (!ids.contains(CityMaterials.FOOD))
            throw new IllegalArgumentException("Food product is required");
        var recipeIds = new HashSet<String>();
        for (var r : recipes) {
            if (r.id.isBlank()
                    || r.id.length() > 48
                    || !recipeIds.add(r.id)
                    || r.companyKind < 2
                    || businesses.type(r.companyKind) == null
                    || !valid(r.output, products)
                    || r.count < 1
                    || r.count > 64
                    || r.inputs.isEmpty()
                    || r.inputs.size() > 8
                    || !Double.isFinite(r.batchesPerHour)
                    || r.batchesPerHour <= 0
                    || r.batchesPerHour > 4096
                    || r.capacity < r.count
                    || r.capacity > 4096)
                throw new IllegalArgumentException("Invalid manufacturing recipe");
            for (var e : r.inputs.entrySet())
                if (!valid(e.getKey(), products) || e.getValue() < 1 || e.getValue() > 64)
                    throw new IllegalArgumentException("Invalid recipe ingredient");
        }
        var equipped = new HashSet<Integer>();
        for (var e : equipment)
            if (!ids.contains(e.product)
                    || e.companyKind < 2
                    || businesses.type(e.companyKind) == null
                    || !equipped.add(e.companyKind)
                    || !Double.isFinite(e.multiplier)
                    || e.multiplier <= 1
                    || e.multiplier > 8)
                throw new IllegalArgumentException("Invalid productivity equipment");
    }

    private static boolean valid(int id, List<Product> products) {
        return CityMaterials.buildingMaterial(id) || products.stream().anyMatch(p -> p.id == id);
    }

    public boolean valid(int id) {
        return valid(id, products);
    }

    public String name(int id) {
        return products.stream()
                .filter(p -> p.id == id)
                .map(Product::name)
                .findFirst()
                .orElse(Blocks.name(id));
    }

    public int nutrition(int id) {
        return products.stream()
                .filter(p -> p.id == id)
                .mapToInt(Product::nutrition)
                .findFirst()
                .orElse(0);
    }

    public boolean agriculture() {
        return valid(CityMaterials.WHEAT);
    }

    public List<Integer> food() {
        return products.stream().filter(p -> p.nutrition > 0).map(Product::id).toList();
    }

    public double price(int id) {
        return products.stream()
                .filter(p -> p.id == id)
                .mapToDouble(Product::price)
                .findFirst()
                .orElse(
                        id == Blocks.LED
                                ? .8
                                : id == Blocks.WOOD ? .5 : id == Blocks.GLASS ? .4 : .3);
    }

    public List<Recipe> recipes(int companyKind) {
        return recipes.stream().filter(r -> r.companyKind == companyKind).toList();
    }

    public int output(int kind) {
        if (agriculture() && kind == CityMaterials.FARM) return CityMaterials.WHEAT;
        var type = businesses.type(kind);
        if (type != null && !type.harvest().isEmpty() && recipes(kind).isEmpty())
            return type.harvest().get(0);
        return recipes(kind).stream()
                .mapToInt(Recipe::output)
                .findFirst()
                .orElse(CityMaterials.output(kind));
    }

    public int capacity(int kind) {
        return recipes(kind).stream()
                .mapToInt(Recipe::capacity)
                .findFirst()
                .orElse(
                        businesses.type(kind) == null
                                ? CityMaterials.capacity(kind)
                                : businesses.type(kind).capacity());
    }

    public String outputs(int kind) {
        if (agriculture() && kind == CityMaterials.FARM) return "Wheat / Carrots";
        if (agriculture() && kind == CityMaterials.CATTLE_FARM) return "Beef / Milk";
        var type = businesses.type(kind);
        if (recipes(kind).isEmpty() && type != null && !type.harvest().isEmpty())
            return type.harvest().stream()
                    .map(this::name)
                    .collect(java.util.stream.Collectors.joining(" / "));
        return recipes(kind).isEmpty()
                ? name(output(kind))
                : recipes(kind).stream()
                        .map(r -> name(r.output()))
                        .distinct()
                        .collect(java.util.stream.Collectors.joining(" / "));
    }

    public Equipment equipment(int kind) {
        return equipment.stream().filter(e -> e.companyKind == kind).findFirst().orElse(null);
    }

    public static ProductionCatalog cityGame() {
        return Defaults.CATALOG;
    }

    private static final class Defaults {
        static final ProductionCatalog LEGACY = createToolEra();
        static final ProductionCatalog CATALOG = createCityGame();
    }

    public static ProductionCatalog toolEra() {
        return Defaults.LEGACY;
    }

    private static ProductionCatalog createCityGame() {
        var old = createToolEra();
        var p = new ArrayList<>(old.products);
        var r = new ArrayList<>(old.recipes);
        p.addAll(
                List.of(
                        new Product(1101, "Wheat", .4),
                        new Product(1102, "Carrots", .5, 25),
                        new Product(1103, "Sugarcane", .4),
                        new Product(1104, "Beef", 1.2, 40),
                        new Product(1105, "Milk", .7, 20),
                        new Product(1106, "Flour", .7),
                        new Product(1107, "Bread", 1, 35),
                        new Product(1108, "Sugar", .6),
                        new Product(1109, "Carrot cake", 2, 60)));
        r.addAll(
                List.of(
                        new Recipe("flour", 11, 1106, 2, Map.of(1101, 1), 12, 128, true),
                        new Recipe("bread", 12, 1107, 2, Map.of(1101, 1), 12, 128, true),
                        new Recipe("sugar", 13, 1108, 2, Map.of(1103, 1), 12, 128, true),
                        new Recipe(
                                "carrot-cake",
                                14,
                                1109,
                                4,
                                Map.of(1106, 1, 1108, 1, 1102, 1, 1105, 1),
                                8,
                                128,
                                true)));
        return new ProductionCatalog(p, r, old.equipment);
    }

    private static ProductionCatalog createToolEra() {
        return new ProductionCatalog(
                List.of(
                        new Product(CityMaterials.FOOD, "Food", .5),
                        new Product(CityMaterials.PICKAXE, "Stone pickaxe", 4),
                        new Product(CityMaterials.AXE, "Stone axe", 4)),
                List.of(
                        new Recipe(
                                "planks",
                                CityMaterials.LOGGING,
                                Blocks.PLANKS,
                                4,
                                Map.of(Blocks.WOOD, 1),
                                256,
                                512,
                                false),
                        new Recipe(
                                "bricks",
                                CityMaterials.MASONRY,
                                Blocks.BRICKS,
                                4,
                                Map.of(Blocks.STONE, 4),
                                256,
                                512,
                                false),
                        new Recipe(
                                "glass",
                                CityMaterials.GLASSWORKS,
                                Blocks.GLASS,
                                4,
                                Map.of(Blocks.SAND, 4),
                                256,
                                512,
                                false),
                        new Recipe(
                                "lighting",
                                CityMaterials.LIGHTING,
                                Blocks.LED,
                                1,
                                Map.of(Blocks.STONE, 1, Blocks.GLASS, 1),
                                256,
                                32,
                                false),
                        new Recipe(
                                "stone-pickaxe",
                                CityMaterials.TOOLS,
                                CityMaterials.PICKAXE,
                                1,
                                Map.of(Blocks.WOOD, 2, Blocks.STONE, 3),
                                2,
                                16,
                                true),
                        new Recipe(
                                "stone-axe",
                                CityMaterials.TOOLS,
                                CityMaterials.AXE,
                                1,
                                Map.of(Blocks.WOOD, 2, Blocks.STONE, 3),
                                2,
                                16,
                                true)),
                List.of(
                        new Equipment(CityMaterials.PICKAXE, CityEconomy.MINE, 2),
                        new Equipment(CityMaterials.AXE, CityMaterials.LOGGING, 2)));
    }

    /**
     * Plain UTF-8 game data; no renderer or player-crafting changes are needed for new products.
     */
    public static ProductionCatalog load(Path path) throws IOException {
        var data = new Properties();
        try (var reader = Files.newBufferedReader(path)) {
            data.load(reader);
        }
        try {
            var products = new ArrayList<Product>();
            for (String value : list(data.getProperty("products", ""))) {
                int id = Integer.parseInt(value);
                products.add(
                        new Product(
                                id,
                                required(data, "product." + id + ".name"),
                                Double.parseDouble(required(data, "product." + id + ".price")),
                                Integer.parseInt(
                                        data.getProperty(
                                                "product." + id + ".nutrition",
                                                id == CityMaterials.FOOD ? "35" : "0"))));
            }
            var recipes = new ArrayList<Recipe>();
            for (String id : list(required(data, "recipes"))) {
                String prefix = "recipe." + id + ".";
                var inputs = new TreeMap<Integer, Integer>();
                for (String value : list(required(data, prefix + "inputs"))) {
                    String[] pair = value.split(":");
                    if (pair.length != 2
                            || inputs.put(Integer.parseInt(pair[0]), Integer.parseInt(pair[1]))
                                    != null)
                        throw new IllegalArgumentException("Duplicate/invalid ingredient");
                }
                String factory = required(data, prefix + "factory");
                if (!factory.equals("true") && !factory.equals("false"))
                    throw new IllegalArgumentException("Factory must be true or false");
                recipes.add(
                        new Recipe(
                                id,
                                Integer.parseInt(required(data, prefix + "company")),
                                Integer.parseInt(required(data, prefix + "output")),
                                Integer.parseInt(required(data, prefix + "count")),
                                inputs,
                                Double.parseDouble(required(data, prefix + "rate")),
                                Integer.parseInt(required(data, prefix + "capacity")),
                                Boolean.parseBoolean(factory)));
            }
            var equipment = new ArrayList<Equipment>();
            for (String value : list(data.getProperty("equipment", ""))) {
                String[] fields = value.split(":");
                if (fields.length != 3) throw new IllegalArgumentException("Invalid equipment");
                equipment.add(
                        new Equipment(
                                Integer.parseInt(fields[0]),
                                Integer.parseInt(fields[1]),
                                Double.parseDouble(fields[2])));
            }
            return new ProductionCatalog(
                    products,
                    recipes,
                    equipment,
                    BusinessCatalog.load(
                            data, products.stream().anyMatch(p -> p.id() == CityMaterials.WHEAT)));
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid production configuration: " + e.getMessage(), e);
        }
    }

    private static List<String> list(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String required(Properties data, String key) {
        String value = data.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
        return value.trim();
    }

    public void write(DataOutput out) throws IOException {
        write(out, 7);
    }

    public void write(DataOutput out, int version) throws IOException {
        out.writeInt(products.size());
        for (var p : products) {
            out.writeInt(p.id);
            out.writeUTF(p.name);
            out.writeDouble(p.price);
            if (version >= 6) out.writeByte(p.nutrition);
        }
        out.writeInt(recipes.size());
        for (var r : recipes) {
            out.writeUTF(r.id);
            out.writeByte(r.companyKind);
            out.writeInt(r.output);
            out.writeInt(r.count);
            out.writeDouble(r.batchesPerHour);
            out.writeInt(r.capacity);
            out.writeBoolean(r.requiresFactory);
            out.writeInt(r.inputs.size());
            for (var e : r.inputs.entrySet()) {
                out.writeInt(e.getKey());
                out.writeInt(e.getValue());
            }
        }
        out.writeInt(equipment.size());
        for (var e : equipment) {
            out.writeInt(e.product);
            out.writeByte(e.companyKind);
            out.writeDouble(e.multiplier);
        }
        if (version >= 7) businesses.write(out);
    }

    public static ProductionCatalog read(DataInput in) throws IOException {
        return read(in, 7);
    }

    public static ProductionCatalog read(DataInput in, int version) throws IOException {
        try {
            var products = new ArrayList<Product>();
            for (int n = count(in, 64); n > 0; n--) {
                int id = in.readInt();
                String name = in.readUTF();
                double price = in.readDouble();
                products.add(
                        new Product(
                                id,
                                name,
                                price,
                                version >= 6
                                        ? in.readUnsignedByte()
                                        : id == CityMaterials.FOOD ? 35 : 0));
            }
            var recipes = new ArrayList<Recipe>();
            for (int n = count(in, 64); n > 0; n--) {
                String id = in.readUTF();
                int kind = in.readUnsignedByte(), output = in.readInt(), amount = in.readInt();
                double rate = in.readDouble();
                int capacity = in.readInt();
                boolean factory = in.readBoolean();
                var inputs = new TreeMap<Integer, Integer>();
                for (int m = count(in, 8); m > 0; m--)
                    if (inputs.put(in.readInt(), in.readInt()) != null)
                        throw new IOException("Duplicate input");
                recipes.add(new Recipe(id, kind, output, amount, inputs, rate, capacity, factory));
            }
            var equipment = new ArrayList<Equipment>();
            for (int n = count(in, 32); n > 0; n--)
                equipment.add(new Equipment(in.readInt(), in.readUnsignedByte(), in.readDouble()));
            return new ProductionCatalog(
                    products,
                    recipes,
                    equipment,
                    version >= 7
                            ? BusinessCatalog.read(in)
                            : BusinessCatalog.defaults(
                                    products.stream()
                                            .anyMatch(p -> p.id() == CityMaterials.WHEAT)));
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid production catalog", e);
        }
    }

    private static int count(DataInput in, int maximum) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > maximum) throw new IOException("Invalid catalog count");
        return n;
    }
}
