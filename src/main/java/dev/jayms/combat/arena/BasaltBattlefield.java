package dev.jayms.combat.arena;

import dev.jayms.net.Blocks;

/** Symmetric 144 by 112 block battlefield with three approach lanes and flank cover. */
public final class BasaltBattlefield {
    public static final int HALF_WIDTH = 72, HALF_LENGTH = 56;
    public static final int FLOOR_Y = 72, CEILING_Y = 90;
    private static final int GALLERY_Y = 83;

    private BasaltBattlefield() {}

    public static BasaltCourtyard.Spawn spawn(int side, int member) {
        if (side < 0 || side > 1 || member < 0 || member >= 30)
            throw new IllegalArgumentException("Invalid battlefield team slot");
        int position = member % 15;
        int column = (position + 1) / 2 * (position % 2 == 0 ? -1 : 1);
        int row = member / 15;
        int sign = side == 0 ? 1 : -1;
        return new BasaltCourtyard.Spawn(column * 6 + .5f, FLOOR_Y + 1,
                sign * (38.5f + row * 5), side == 0 ? -90 : 90, 0);
    }

    public static BasaltCourtyard.Spawn spectator() {
        return new BasaltCourtyard.Spawn(0, GALLERY_Y + 1, 61.5f, -90, -13);
    }

    public static int block(int x, int y, int z) {
        if (x < -HALF_WIDTH || x >= HALF_WIDTH || z < -HALF_LENGTH || z >= HALF_LENGTH)
            return gallery(x, y, z);
        int side = mirror(x), end = mirror(z), layer = y - FLOOR_Y;
        boolean wall = side == HALF_WIDTH - 1 || end == HALF_LENGTH - 1;
        if (layer == -1) return Blocks.STONE;
        if (layer == 0) {
            if (end == 0 || end == 36) return Blocks.SAND;
            boolean lane = side < 6 || side >= 24 && side < 31 || side >= 53 && side < 59;
            return lane ? Blocks.ASPHALT : Blocks.STONE;
        }
        if (wall && layer >= 1 && layer <= 6) {
            if (layer == 4 && (side + end) % 11 == 0) return Blocks.LED;
            return layer == 1 || layer == 6 ? Blocks.STONE : Blocks.BRICKS;
        }
        if (layer < 1) return Blocks.AIR;
        // Tall central landmarks and lower cover on the flanks leave clear routes between them.
        if (side >= 14 && side < 19 && end >= 10 && end < 15 && layer <= 5)
            return layer == 5 ? Blocks.STONE : Blocks.BRICKS;
        if (side >= 35 && side < 44 && end >= 19 && end < 22 && layer <= 3)
            return layer == 3 ? Blocks.STONE : Blocks.BRICKS;
        if (side >= 44 && side < 50 && end >= 4 && end < 8 && layer <= 2)
            return layer == 2 ? Blocks.STONE : Blocks.BRICKS;
        return Blocks.AIR;
    }

    private static int gallery(int x, int y, int z) {
        if (x < -12 || x >= 12 || z < 58 || z >= 66) return Blocks.AIR;
        if (y == GALLERY_Y) return Blocks.PLANKS;
        if (y == GALLERY_Y + 1 && (x == -12 || x == 11 || z == 65)) return Blocks.STONE;
        return Blocks.AIR;
    }

    private static int mirror(int coordinate) {
        return coordinate < 0 ? -coordinate - 1 : coordinate;
    }
}
