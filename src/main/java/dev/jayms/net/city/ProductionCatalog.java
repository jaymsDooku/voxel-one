package dev.jayms.net.city;

import dev.jayms.net.Blocks;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Game-owned manufacturing data. Quantities use CityMaterials.UNIT; tools are durable stock. */
public record ProductionCatalog(
        List<Product> products, List<Recipe> recipes, List<Equipment> equipment) {
    public record Product(int id, String name, double price) {}

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
                    || p.price > 10000) throw new IllegalArgumentException("Invalid product");
        if (!ids.contains(CityMaterials.FOOD))
            throw new IllegalArgumentException("Food product is required");
        var recipeIds = new HashSet<String>();
        for (var r : recipes) {
            if (r.id.isBlank()
                    || r.id.length() > 48
                    || !recipeIds.add(r.id)
                    || r.companyKind < 2
                    || r.companyKind > CityMaterials.TOOLS
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
                    || e.companyKind > CityMaterials.TOOLS
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
        return recipes(kind).stream()
                .mapToInt(Recipe::output)
                .findFirst()
                .orElse(CityMaterials.output(kind));
    }

    public int capacity(int kind) {
        return recipes(kind).stream()
                .mapToInt(Recipe::capacity)
                .findFirst()
                .orElse(CityMaterials.capacity(kind));
    }

    public String outputs(int kind) {
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
        static final ProductionCatalog CATALOG = createCityGame();
    }

    private static ProductionCatalog createCityGame() {
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
                                Double.parseDouble(required(data, "product." + id + ".price"))));
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
            return new ProductionCatalog(products, recipes, equipment);
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
        out.writeInt(products.size());
        for (var p : products) {
            out.writeInt(p.id);
            out.writeUTF(p.name);
            out.writeDouble(p.price);
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
    }

    public static ProductionCatalog read(DataInput in) throws IOException {
        try {
            var products = new ArrayList<Product>();
            for (int n = count(in, 64); n > 0; n--)
                products.add(new Product(in.readInt(), in.readUTF(), in.readDouble()));
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
            return new ProductionCatalog(products, recipes, equipment);
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
