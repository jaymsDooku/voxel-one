package dev.jayms.combat.arena;

import dev.jayms.net.Blocks;
import dev.jayms.net.Protocol;
import dev.jayms.net.Terrain;
import dev.jayms.player.PlayerDimensions;

import java.util.function.Consumer;

/**
 * Original practice court with four mirrored cover islands and two low side terraces.
 * All positions are in world blocks and all spawn Y values describe the player's feet.
 */
public final class BasaltCourtyard {
    public static final int MIN_X = -24;
    public static final int MAX_X = 24;
    public static final int MIN_Z = -18;
    public static final int MAX_Z = 18;
    public static final int FLOOR_Y = 72;
    public static final int CEILING_Y = 84;
    private static final double PLAYER_RADIUS = PlayerDimensions.RADIUS;
    private static final double PLAYER_HEIGHT = PlayerDimensions.HEIGHT;
    private static final double GROUND_TOLERANCE = .02;
    private static final int SPECTATOR_FLOOR = FLOOR_Y + 5;

    public record Spawn(float x, float y, float z, float yaw, float pitch) {}

    public static Spawn spawn(int side) {
        return teamSpawn(side, 0);
    }

    /** Places up to five members per side along its rear line without overlapping. */
    public static Spawn teamSpawn(int side, int member) {
        if (side < 0 || side > 1 || member < 0 || member > 4) {
            throw new IllegalArgumentException("Invalid courtyard team slot");
        }
        int sideSign = side == 0 ? 1 : -1;
        int column = (member + 1) / 2 * (member % 2 == 0 ? -1 : 1);
        return new Spawn(column * sideSign * 2, FLOOR_Y + 1, sideSign * 12.5f,
                side == 0 ? -90 : 90, 0);
    }

    public static Spawn spectator() {
        return new Spawn(0, SPECTATOR_FLOOR + 1, 22.5f, -90, -15);
    }

    /** Arena bounds, including the enclosing wall cells. Maxima are exclusive. */
    public static boolean contains(double x, double y, double z) {
        return x >= MIN_X && x < MAX_X && z >= MIN_Z && z < MAX_Z
                && y >= FLOOR_Y && y < CEILING_Y;
    }

    /** Authoritative collision also seals the open-air visual ceiling and exterior. */
    public static boolean solid(int x, int y, int z) {
        return !contains(x, y, z) || block(x, y, z) != Blocks.AIR;
    }

    public static boolean canStand(double x, double y, double z) {
        return canStand(x, y, z, BasaltCourtyard::solid);
    }

    static boolean canStand(double x, double y, double z, SolidSampler sampler) {
        if (!contains(x - PLAYER_RADIUS, y, z - PLAYER_RADIUS)
                || !contains(x + PLAYER_RADIUS, y + PLAYER_HEIGHT, z + PLAYER_RADIUS)) {
            return false;
        }
        int minX = (int) Math.floor(x - PLAYER_RADIUS);
        int maxX = (int) Math.floor(Math.nextDown(x + PLAYER_RADIUS));
        int minZ = (int) Math.floor(z - PLAYER_RADIUS);
        int maxZ = (int) Math.floor(Math.nextDown(z + PLAYER_RADIUS));
        int foot = (int) Math.floor(y);
        int head = (int) Math.floor(Math.nextDown(y + PLAYER_HEIGHT));
        int below = (int) Math.floor(y - GROUND_TOLERANCE);
        boolean supported = false;
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                for (int by = foot; by <= head; by++) {
                    if (sampler.solid(bx, by, bz)) return false;
                }
                supported |= sampler.solid(bx, below, bz);
            }
        }
        return supported;
    }

    /**
     * Emits the layout edits that differ from the supplied terrain. The dedicated
     * server can install them without loading client graphics or chunk classes.
     */
    public static void install(Terrain terrain, Consumer<Protocol.Edit> edits) {
        restore(terrain::block, edits);
    }

    static void restore(BlockSampler sample, Consumer<Protocol.Edit> edits) {
        restoreRegion(MIN_X, MAX_X, MIN_Z, MAX_Z, sample, edits);
        restoreRegion(-8, 8, 19, 26, sample, edits);
    }

    private static void restoreRegion(
            int minX, int maxX, int minZ, int maxZ, BlockSampler sample, Consumer<Protocol.Edit> edits) {
        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                for (int y = FLOOR_Y - 1; y < CEILING_Y; y++) {
                    int planned = block(x, y, z);
                    if (sample.block(x, y, z) != planned) {
                        edits.accept(new Protocol.Edit(x, y, z, planned));
                    }
                }
            }
        }
    }

    /** Canonical render material. Unmanaged cells outside the court and gallery return air. */
    public static int block(int x, int y, int z) {
        if (x < MIN_X || x >= MAX_X || z < MIN_Z || z >= MAX_Z) {
            return gallery(x, y, z);
        }
        int side = mirrored(x);
        int end = mirrored(z);
        int layer = y - FLOOR_Y;
        boolean perimeter = side == MAX_X - 1 || end == MAX_Z - 1;
        if (layer == -1) return perimeter ? Blocks.BRICKS : Blocks.STONE;
        if (layer == 0) return floor(side, end);
        if (perimeter && layer >= 1 && layer <= 5) {
            if (layer == 3 && (side % 7 == 3 || end % 6 == 2)) return Blocks.LED;
            return layer == 1 || layer == 5 ? Blocks.STONE : Blocks.BRICKS;
        }
        if (side >= 8 && side <= 11 && end >= 6 && end <= 8) {
            if (layer == 1) return Blocks.BRICKS;
            if (layer == 2) return Blocks.STONE;
        }
        if (side >= 15 && side <= 20 && end <= 5) {
            if (layer == 1) return Blocks.BRICKS;
            if (layer == 2 && end <= 4) return Blocks.STONE;
        }
        return Blocks.AIR;
    }

    private static int floor(int side, int end) {
        if (side == 5 || (side <= 4 && (end == 0 || end == 12))) return Blocks.SAND;
        if (side >= 13 && side <= 21) return Blocks.ASPHALT;
        return Blocks.STONE;
    }

    private static int gallery(int x, int y, int z) {
        if (x < -8 || x >= 8 || z < 19 || z >= 26) return Blocks.AIR;
        int side = mirrored(x);
        if (y == SPECTATOR_FLOOR) return side == 7 ? Blocks.STONE : Blocks.PLANKS;
        if (y == SPECTATOR_FLOOR + 1 && (side == 7 || z == 25)) return Blocks.BRICKS;
        if (y == SPECTATOR_FLOOR + 2 && side == 7 && (z == 19 || z == 25)) return Blocks.LED;
        if (y >= FLOOR_Y && y < SPECTATOR_FLOOR && (side == 1 || side == 6) && (z == 20 || z == 24)) {
            return Blocks.STONE;
        }
        return Blocks.AIR;
    }

    private static int mirrored(int coordinate) {
        return coordinate < 0 ? -coordinate - 1 : coordinate;
    }

    @FunctionalInterface
    interface SolidSampler {
        boolean solid(int x, int y, int z);
    }

    @FunctionalInterface
    interface BlockSampler {
        int block(int x, int y, int z);
    }

    private BasaltCourtyard() {}
}
