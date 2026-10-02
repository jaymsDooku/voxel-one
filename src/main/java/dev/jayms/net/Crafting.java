package dev.jayms.net;

import java.util.*;

/** The server-owned recipe catalog. Clients submit only a stable recipe ID. */
public final class Crafting {
    public record Recipe(int id, String name, Map<Integer, Integer> inputs, int output, int count) {
        public Recipe {
            inputs = Map.copyOf(inputs);
        }

        public boolean available(Inventory inventory) {
            return inputs.entrySet().stream()
                    .allMatch(e -> inventory.countType(e.getKey()) >= e.getValue());
        }

        public String ingredients(Inventory inventory) {
            return inputs.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(
                            e ->
                                    Blocks.name(e.getKey())
                                            + " "
                                            + inventory.countType(e.getKey())
                                            + "/"
                                            + e.getValue())
                    .collect(java.util.stream.Collectors.joining(" | "));
        }
    }

    public record Result(
            boolean accepted, Inventory inventory, int output, int excess, String message) {}

    private static final List<Recipe> RECIPES;

    static {
        List<Recipe> recipes = new ArrayList<>();
        for (int material :
                new int[] {
                    1, 2, 3, 4, 5, 6, 7, Blocks.PLANKS, Blocks.BRICKS, Blocks.GLASS, Blocks.LED
                })
            for (int depth = 1; depth <= 4; depth++) {
                int parent = Blocks.piece(material, depth - 1),
                        child = Blocks.piece(material, depth);
                add(recipes, "Cut " + Blocks.name(parent), Map.of(parent, 1), child, 8);
                add(recipes, "Join " + Blocks.name(child), Map.of(child, 8), parent, 1);
            }
        add(recipes, "Saw planks", Map.of(Blocks.WOOD, 1), Blocks.PLANKS, 4);
        add(recipes, "Make bricks", Map.of(Blocks.STONE, 4), Blocks.BRICKS, 4);
        add(recipes, "Make glass", Map.of(Blocks.SAND, 4), Blocks.GLASS, 4);
        add(recipes, "Grow grass", Map.of(Blocks.DIRT, 1, Blocks.LEAVES, 1), Blocks.GRASS, 1);
        add(recipes, "Compost grass", Map.of(Blocks.GRASS, 1), Blocks.DIRT, 1);
        add(
                recipes,
                "Flower pot",
                Map.of(Blocks.SAND, 3, Blocks.DIRT, 1, Blocks.LEAVES, 1),
                Blocks.FLOWER_POT,
                1);
        add(recipes, "LED light", Map.of(Blocks.GLASS, 1, Blocks.STONE, 1), Blocks.LED, 1);
        RECIPES = List.copyOf(recipes);
    }

    private static void add(
            List<Recipe> recipes,
            String name,
            Map<Integer, Integer> inputs,
            int output,
            int count) {
        recipes.add(new Recipe(recipes.size(), name, inputs, output, count));
    }

    public static List<Recipe> recipes() {
        return RECIPES;
    }

    public static Recipe recipe(int id) {
        return id >= 0 && id < RECIPES.size() ? RECIPES.get(id) : null;
    }

    public static Result prepare(Inventory inventory, int id) {
        Recipe recipe = recipe(id);
        if (recipe == null) return new Result(false, inventory, 0, 0, "Unknown recipe.");
        if (!recipe.available(inventory))
            return new Result(
                    false, inventory, 0, 0, "Missing materials: " + recipe.ingredients(inventory));
        Inventory next = inventory.copy();
        next.consume(recipe.inputs());
        int excess = next.add(recipe.output(), recipe.count());
        return new Result(
                true,
                next,
                recipe.output(),
                excess,
                "Crafted "
                        + recipe.count()
                        + " "
                        + Blocks.name(recipe.output())
                        + (excess > 0 ? ". " + excess + " dropped on the ground." : "."));
    }

    private Crafting() {}
}
