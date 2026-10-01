package dev.jayms.net;

public final class Blocks {
    public static final int AIR = 0,
            GRASS = 1,
            DIRT = 2,
            STONE = 3,
            SAND = 4,
            SNOW = 5,
            WOOD = 6,
            LEAVES = 7,
            FLOWER_POT = 8,
            PLANKS = 165,
            BRICKS = 166,
            GLASS = 167,
            PARTIAL = 255;
    private static final String[] NAMES = {
        "Empty", "Grass", "Dirt", "Stone", "Sand", "Snow", "Wood", "Leaves", "Flower pot"
    };
    private static final float[][] COLORS = {
        {0, 0, 0},
        {.30f, .65f, .21f},
        {.48f, .30f, .17f},
        {.55f, .58f, .62f},
        {.88f, .77f, .48f},
        {.91f, .96f, 1},
        {.45f, .28f, .12f},
        {.16f, .45f, .18f},
        {.74f, .35f, .22f}
    };

    public static boolean valid(int type) {
        return type >= 0 && type < 180;
    }

    public static String name(int type) {
        if (isPiece(type)) return name(material(type)) + " 1/" + (1 << depth(type)) + " cube";
        if (type == PLANKS) return "Planks";
        if (type == BRICKS) return "Bricks";
        if (type == GLASS) return "Glass";
        return type < NAMES.length ? NAMES[type] : "Model " + type;
    }

    public static float[] color(int type) {
        type = material(type);
        if (type == PLANKS) return new float[] {.72f, .51f, .29f};
        if (type == BRICKS) return new float[] {.61f, .27f, .21f};
        if (type == GLASS) return new float[] {.66f, .87f, .91f};
        return type < COLORS.length ? COLORS[type].clone() : new float[] {.6f, .4f, .7f};
    }

    public static boolean isModel(int type) {
        return type >= FLOWER_POT && type < 137;
    }

    public static boolean isPiece(int type) {
        return type >= 137 && type <= 164 || type >= 168 && type <= 179;
    }

    public static int depth(int type) {
        return !isPiece(type) ? 0 : (type >= 168 ? type - 168 : type - 137) % 4 + 1;
    }

    public static int material(int type) {
        return !isPiece(type) ? type : type >= 168 ? 165 + (type - 168) / 4 : 1 + (type - 137) / 4;
    }

    public static int piece(int material, int depth) {
        if (depth == 0) return material;
        if (depth < 1 || depth > 4) throw new IllegalArgumentException("Invalid piece depth");
        if (material >= 1 && material <= 7) return 137 + (material - 1) * 4 + depth - 1;
        if (material >= 165 && material <= 167) return 168 + (material - 165) * 4 + depth - 1;
        throw new IllegalArgumentException("Only building materials can be subdivided");
    }

    private Blocks() {}
}
