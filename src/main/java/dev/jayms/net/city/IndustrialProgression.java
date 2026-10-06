package dev.jayms.net.city;

import dev.jayms.net.Blocks;
import java.util.*;

/** Resource-led industrial eras. Unlock proofs are saved completed batches, never cash or time. */
public final class IndustrialProgression {
    public static final int CLAY = 1201, IRON_ORE = 1202, COAL = 1203, IRON = 1204,
            IRON_TOOLS = 1205, CART = 1206, STEEL = 1207, COPPER = 1208,
            MACHINERY = 1209, ENGINE = 1210, RAIL_TRACK = 1211, FREIGHT_TRAIN = 1212,
            STATION = 1213, OIL = 1214, FUEL = 1215, RUBBER = 1216, TRUCK = 1217,
            BUS = 1218, VEHICLE_PARTS = 1219, CAR = 1220, ELECTRICAL = 1221,
            POWER = 1222, TRAM = 1223, ELECTRIC_RAIL = 1224, METRO = 1225,
            PLASTICS = 1226, ELECTRONICS = 1227, ALLOYS = 1228, MODERN_TRAIN = 1229,
            ADVANCED_VEHICLE = 1230;
    public record Tier(int number, String name, List<String> proofs, String capability) {}
    public static final List<Tier> TIERS = List.of(
            new Tier(1, "Settlement", List.of(), "Houses, basic roads and storage"),
            new Tier(2, "Early Industry", List.of("planks"), "Iron tools and carts"),
            new Tier(3, "Heavy Industry", List.of("industrial-iron-tools", "industrial-clay-bricks"), "Machinery and rail tracks"),
            new Tier(4, "Rail Age", List.of("industrial-machinery", "industrial-steel"), "Freight trains, stations and long-distance logistics"),
            new Tier(5, "Oil Age", List.of("industrial-freight-train", "industrial-station"), "Trucks, buses and fuel-powered industry"),
            new Tier(6, "Automotive Age", List.of("industrial-fuel", "industrial-rubber"), "Cars and larger road freight networks"),
            new Tier(7, "Electrification", List.of("industrial-car", "industrial-vehicle-parts"), "Trams, electric rail, metro and powered factories"),
            new Tier(8, "Advanced Industry", List.of("industrial-electrical-equipment", "industrial-power"), "Modern trains, advanced vehicles and high-density cities"));

    public static boolean enabled(ProductionCatalog catalog) {
        return catalog.recipes().stream().anyMatch(r -> r.id().equals("industrial-steel"));
    }
    public static long completed(CityMaterials.State state, String recipe) {
        return state.batches().stream().filter(b -> b.recipe().equals(recipe)).mapToLong(CityMaterials.Batch::completed).sum();
    }
    public static int tier(CityMaterials.State state) {
        if (!enabled(state.catalog())) return 1;
        int tier = 1;
        for (var next : TIERS.subList(1, TIERS.size())) {
            if (next.proofs().stream().anyMatch(p -> completed(state, p) < 1)) break;
            tier = next.number();
        }
        return tier;
    }
    public static int tier(CityMaterials resources) {
        if (!enabled(resources.catalog)) return 1;
        int tier = 1;
        for (var next : TIERS.subList(1, TIERS.size())) {
            if (next.proofs().stream().anyMatch(p -> resources.completed(p) < 1)) break;
            tier = next.number();
        }
        return tier;
    }
    /** Old default saves counted plank output before they recorded per-recipe batches. */
    public static void importSettlementHistory(CityEconomy economy) {
        if (!enabled(economy.resources.catalog) || economy.resources.completed("planks") > 0) return;
        for (var firm : economy.companies()) if (firm.kind == CityMaterials.LOGGING) {
            long made = economy.resources.production(firm.id).processed() / 4;
            if (made > 0) economy.resources.batch(firm.id, "planks", 0, made);
        }
    }
    public static int required(int kind) {
        return switch (kind) {
            case 32, 33, 34, 49 -> 2;
            case 35, 36, 37, 38 -> 3;
            case 39, 40 -> 4;
            case 41, 42, 43 -> 5;
            case 44, 45 -> 6;
            case 46, 47 -> 7;
            case 48 -> 8;
            default -> 1;
        };
    }
    public static boolean unlocked(CityMaterials resources, int kind) {
        return !enabled(resources.catalog) || tier(resources) >= required(kind);
    }
    public static String missing(CityMaterials.State state) {
        int tier = tier(state);
        if (tier == 8) return "All eras unlocked";
        return TIERS.get(tier).proofs().stream().filter(p -> completed(state, p) == 0)
                .map(p -> state.catalog().recipes().stream().filter(r -> r.id().equals(p)).findFirst()
                        .map(r -> state.catalog().name(r.output())).orElse(p))
                .collect(java.util.stream.Collectors.joining(" + "));
    }

    /** Extend only the game default. Custom and saved catalogs keep their own identities. */
    public static ProductionCatalog extend(ProductionCatalog base) {
        var products = new ArrayList<>(base.products());
        String[] names = {"Clay", "Iron ore", "Coal", "Iron", "Iron tools", "Cart", "Steel", "Copper",
                "Machinery", "Engine", "Rail tracks", "Freight train", "Station kit", "Oil", "Fuel", "Rubber",
                "Truck", "Bus", "Vehicle parts", "Car", "Electrical equipment", "Electricity", "Tram",
                "Electric rail", "Metro", "Plastics", "Electronics", "Alloys", "Modern train", "Advanced vehicle"};
        for (int i = 0; i < names.length; i++) products.add(new ProductionCatalog.Product(1201 + i, names[i], 1 + i * .5));
        var recipes = new ArrayList<>(base.recipes());
        // Extraction consumes physical soil, stone or sand purchased from finite terrain harvesters.
        add(recipes, "clay", 32, CLAY, 2, Map.of(Blocks.DIRT, 2), false);
        add(recipes, "iron-ore", 33, IRON_ORE, 2, Map.of(Blocks.STONE, 3), false);
        add(recipes, "coal", 33, COAL, 2, Map.of(Blocks.STONE, 3, Blocks.WOOD, 1), false);
        add(recipes, "clay-bricks", 34, Blocks.BRICKS, 4, Map.of(CLAY, 2, COAL, 1), true);
        add(recipes, "iron", 34, IRON, 2, Map.of(IRON_ORE, 2, COAL, 1), true);
        add(recipes, "iron-tools", 34, IRON_TOOLS, 1, Map.of(IRON, 2, Blocks.WOOD, 1), true);
        add(recipes, "cart", 34, CART, 1, Map.of(IRON, 1, Blocks.PLANKS, 2), true);
        add(recipes, "steel", 35, STEEL, 2, Map.of(IRON, 2, COAL, 2), true);
        add(recipes, "glass", 36, Blocks.GLASS, 4, Map.of(Blocks.SAND, 2, COAL, 1), true);
        add(recipes, "copper", 37, COPPER, 2, Map.of(IRON_ORE, 2, COAL, 1), true);
        add(recipes, "machinery", 38, MACHINERY, 1, Map.of(STEEL, 2, COPPER, 1, IRON_TOOLS, 1), true);
        add(recipes, "rail-tracks", 38, RAIL_TRACK, 4, Map.of(STEEL, 2, Blocks.PLANKS, 2), true);
        add(recipes, "engine", 39, ENGINE, 1, Map.of(STEEL, 2, MACHINERY, 1), true);
        add(recipes, "freight-train", 39, FREIGHT_TRAIN, 1, Map.of(ENGINE, 1, STEEL, 3, RAIL_TRACK, 4), true);
        add(recipes, "station", 40, STATION, 1, Map.of(Blocks.BRICKS, 4, RAIL_TRACK, 4, MACHINERY, 1), true);
        add(recipes, "oil", 41, OIL, 2, Map.of(Blocks.SAND, 3, MACHINERY, 1), false);
        add(recipes, "fuel", 42, FUEL, 4, Map.of(OIL, 2, COAL, 1), true);
        add(recipes, "rubber", 43, RUBBER, 2, Map.of(OIL, 1, FUEL, 1), true);
        add(recipes, "truck", 43, TRUCK, 1, Map.of(ENGINE, 1, STEEL, 2, RUBBER, 2), true);
        add(recipes, "bus", 43, BUS, 1, Map.of(ENGINE, 1, STEEL, 2, RUBBER, 2, Blocks.GLASS, 2), true);
        add(recipes, "automotive-engine", 44, ENGINE, 2, Map.of(STEEL, 2, MACHINERY, 1, FUEL, 1), true);
        add(recipes, "vehicle-parts", 45, VEHICLE_PARTS, 2, Map.of(STEEL, 2, RUBBER, 2, Blocks.GLASS, 1), true);
        add(recipes, "car", 45, CAR, 1, Map.of(ENGINE, 1, VEHICLE_PARTS, 2, FUEL, 1), true);
        add(recipes, "power", 46, POWER, 8, Map.of(COAL, 2, FUEL, 1), true);
        add(recipes, "electrical-equipment", 47, ELECTRICAL, 2, Map.of(COPPER, 2, Blocks.GLASS, 1, POWER, 1), true);
        add(recipes, "tram", 47, TRAM, 1, Map.of(ELECTRICAL, 2, VEHICLE_PARTS, 2, RAIL_TRACK, 2), true);
        add(recipes, "electric-rail", 47, ELECTRIC_RAIL, 1, Map.of(ELECTRICAL, 2, FREIGHT_TRAIN, 1), true);
        add(recipes, "metro", 47, METRO, 1, Map.of(ELECTRICAL, 3, RAIL_TRACK, 4, ENGINE, 1), true);
        add(recipes, "plastics", 48, PLASTICS, 2, Map.of(OIL, 2, ELECTRICAL, 1), true);
        add(recipes, "electronics", 48, ELECTRONICS, 2, Map.of(COPPER, 2, PLASTICS, 1, ELECTRICAL, 1), true);
        add(recipes, "alloys", 48, ALLOYS, 2, Map.of(STEEL, 2, COPPER, 1, POWER, 1), true);
        add(recipes, "modern-train", 48, MODERN_TRAIN, 1, Map.of(ELECTRONICS, 2, ALLOYS, 2, ELECTRIC_RAIL, 1), true);
        add(recipes, "advanced-vehicle", 48, ADVANCED_VEHICLE, 1, Map.of(ELECTRONICS, 2, ALLOYS, 2, CAR, 1), true);
        return new ProductionCatalog(products, recipes, base.equipment(), businesses(base.businesses()));
    }
    public static BusinessCatalog businesses(BusinessCatalog base) {
        var types = new ArrayList<>(base.types());
        var companies = new ArrayList<>(base.companies());
        String[] industries = {"Clay pit", "Ore and coal mine", "Kiln and smelter", "Steel mill", "Glassworks",
                "Copper foundry", "Machine shop", "Locomotive works", "Station works", "Oil well", "Refinery",
                "Chemical plant", "Engine factory", "Car factory", "Power plant", "Electrical factory", "Advanced manufacturing"};
        for (int i = 0; i < industries.length; i++) {
            int kind = 32 + i;
            types.add(new BusinessCatalog.Type(kind, industries[i], List.of(), 8, 128));
            companies.add(new BusinessCatalog.Company("industrial-" + kind, industries[i], kind, 2500));
        }
        // A dedicated soil company makes clay reachable without inventing free raw stocks.
        types.add(new BusinessCatalog.Type(49, "Soil quarry", List.of(Blocks.DIRT), 64, 512));
        companies.add(new BusinessCatalog.Company("industrial-soil", "Soil quarry", 49, 2000));
        return new BusinessCatalog(types, companies);
    }
    private static void add(List<ProductionCatalog.Recipe> recipes, String id, int kind, int output,
                            int count, Map<Integer, Integer> inputs, boolean factory) {
        recipes.add(new ProductionCatalog.Recipe("industrial-" + id, kind, output, count, inputs, 8, 128, factory));
    }
    private IndustrialProgression() {}
}
