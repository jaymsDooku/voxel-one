package dev.jayms.net;

public final class Blocks {
    public static final int AIR = 0,
            GRASS = 1,
            DIRT = 2,
            STONE = 3,
            SAND = 4,
            SNOW = 5,
            WOOD = 6,
            LEAVES = 7;
    private static final String[] NAMES = {
        "Empty", "Grass", "Dirt", "Stone", "Sand", "Snow", "Wood", "Leaves"
    };
    private static final float[][] COLORS = {
        {0, 0, 0},
        {.30f, .65f, .21f},
        {.48f, .30f, .17f},
        {.55f, .58f, .62f},
        {.88f, .77f, .48f},
        {.91f, .96f, 1},
        {.45f, .28f, .12f},
        {.16f, .45f, .18f}
    };

    public static boolean valid(int type) {
        return type >= 0 && type < NAMES.length;
    }

    public static String name(int type) {
        return NAMES[type];
    }

    public static float[] color(int type) {
        return COLORS[type].clone();
    }

    private Blocks() {}
}
