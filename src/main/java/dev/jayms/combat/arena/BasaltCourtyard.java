package dev.jayms.combat.arena;

import dev.jayms.*;
import dev.jayms.combat.Vec3;
import dev.jayms.net.Blocks;
import dev.jayms.physics.VoxelQueries;
import java.util.List;

/**
 * Original, opt-in practice fixture. One block is one world unit. All coordinates are local;
 * +Y is up, +Z runs from NORTH to SOUTH, and yaw uses Camera's +X=0, +Z=90 convention.
 * Cell ranges are half-open; cell reflection is x -> -x-1, z -> -z-1; point reflection is -x,-z.
 * Floor cells have y=0, so ordinary feet are at y=1. No combat tuning is implied by this map.
 *
 * <p>Use {@link #generate()} in a dedicated, nonstreaming world. This does not change terrain,
 * saves, the protocol, or entry points. Keep all resident chunks until close. The match owner
 * handles participants, opt-in, spectator permissions, projectile sweeps and containment above
 * the eight-block walls. Landing queries are geometry policy, not a complete CombatWorld.
 *
 * <p>Stable integration landmarks: flat exchanges (0,1,-8)/(0,1,8); safe pearl destination
 * (6,1,0); rejected destination (12,1,12), inside cover; interception ray from (12,1.5,5)
 * toward +Z hits cover at z=10. Platform feet (23,3,0); access steps (23,2,+/-6).
 * Detached spectator feet (0,11,+/-44) have no route into the arena and are rejected as
 * participant landings. Item 2 supplies the final multiplayer controller and scenarios.
 */
public final class BasaltCourtyard {
    public static final String ID = "basalt-courtyard-v1";
    public static final String NAME = "Basalt Courtyard";
    public static final int WALL_HEIGHT = 8;
    public static final Bounds FOOTPRINT = new Bounds(-32, -32, -48, 32, 128, 48);
    public static final Bounds PLAY = new Bounds(-31, 1, -39, 31, 24, 39);
    public static final Bounds FLAT_LANE = new Bounds(-8, 1, -36, 8, 16, 36);
    public static final List<Bounds> COVER = List.of(
            new Bounds(-14, 1, -14, -10, 2, -10),
            new Bounds(10, 1, -14, 14, 2, -10),
            new Bounds(-14, 1, 10, -10, 2, 14),
            new Bounds(10, 1, 10, 14, 2, 14));
    public static final List<Bounds> PLATFORMS = List.of(
            new Bounds(-26, 1, -5, -20, 3, 5),
            new Bounds(20, 1, -5, 26, 3, 5));

    /** Axis-aligned half-open local volume. */
    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Bounds {
            if (minX >= maxX || minY >= maxY || minZ >= maxZ)
                throw new IllegalArgumentException("Empty bounds");
        }
        public boolean contains(int x, int y, int z) {
            return x >= minX && x < maxX && y >= minY && y < maxY && z >= minZ && z < maxZ;
        }
    }

    public enum Side { NORTH, SOUTH }
    public record Start(Vec3 feet, float yawDegrees) {}

    public static Start spawn(Side side) {
        return switch (side) {
            case NORTH -> new Start(new Vec3(0, 1, -28), 90);
            case SOUTH -> new Start(new Vec3(0, 1, 28), -90);
        };
    }

    /** Three equal-distance slots per side. Slot order mirrors through the origin. */
    public static List<Start> teamStarts(Side side) {
        Start center = spawn(side);
        int sign = side == Side.NORTH ? 1 : -1;
        return List.of(new Start(new Vec3(-2 * sign, 1, center.feet.z()), center.yawDegrees),
                center, new Start(new Vec3(2 * sign, 1, center.feet.z()), center.yawDegrees));
    }

    public static Start spectator(Side side) {
        return side == Side.NORTH ? new Start(new Vec3(0, 11, -44), 90)
                : new Start(new Vec3(0, 11, 44), -90);
    }

    /** Fresh geometry; existing block materials are used as a restrained stone/white/ochre palette. */
    public static int blockAt(int x, int y, int z) {
        if (!FOOTPRINT.contains(x, y, z) || y < 0 || y >= 32) return Blocks.AIR;
        int ax = x < 0 ? -x - 1 : x, az = z < 0 ? -z - 1 : z;
        if (az < 40) {
            if (y == 0) {
                if (ax == 8 || az == 28 && ax < 4) return Blocks.SNOW;
                if (ax < 8) return Blocks.STONE;
                return (ax / 4 + az / 4) % 2 == 0 ? Blocks.STONE : Blocks.BRICKS;
            }
            if ((ax == 31 || az == 39) && y <= WALL_HEIGHT)
                return y == WALL_HEIGHT ? Blocks.SNOW : Blocks.STONE;
            if (ax >= 10 && ax < 14 && az >= 10 && az < 14 && y == 1)
                return Blocks.SNOW;
            if (ax >= 20 && ax < 26) {
                if (az < 5 && y <= 2) return y == 2 ? Blocks.SAND : Blocks.STONE;
                if (az >= 5 && az < 7 && y == 1) return Blocks.SAND;
            }
        }
        // Two detached galleries, with a two-block gap beyond each enclosing wall.
        if (ax < 8 && az >= 42) {
            if (y == 10) return Blocks.STONE;
            if ((ax == 7 || az == 47) && y > 10 && y <= 12) return Blocks.SNOW;
        }
        return Blocks.AIR;
    }

    public static Fixture generate() {
        World world = new World(0);
        for (int cx = -2; cx < 2; cx++)
            for (int cz = -3; cz < 3; cz++)
                // Full engine-height columns keep natural fallback terrain out of lighting queries.
                for (int cy = -2; cy < 8; cy++) {
                    Chunk chunk = new Chunk();
                    for (int x = 0; x < 16; x++)
                        for (int z = 0; z < 16; z++)
                            for (int y = 0; y < 16; y++) {
                                int type = blockAt(cx * 16 + x, cy * 16 + y, cz * 16 + z);
                                if (type != Blocks.AIR) chunk.setBlock(x, y, z, type);
                            }
                    world.addChunk(new ChunkPos(cx, cy, cz), chunk);
                }
        return new Fixture(world);
    }

    public static final class Fixture implements AutoCloseable {
        private final World world;
        private Fixture(World world) { this.world = world; }
        public World world() { return world; }

        /** Exact current resident geometry, including edits and partial blocks. Unloaded is solid. */
        public boolean collides(VoxelQueries.Box body) {
            requireOpen();
            return VoxelQueries.collides(world, body);
        }

        /** Participant landing only: whole body within play bounds, clear, and fully supported.
         * Supports floor, cover and platforms. Dimensions are bounded to avoid unbounded queries.
         * Rejects unsupported air, outside, spectators, walls, nonfinite and oversized bodies.
         */
        public boolean safeLanding(Vec3 feet, double halfWidth, double height) {
            requireOpen();
            if (feet == null || !Double.isFinite(halfWidth) || !Double.isFinite(height)
                    || halfWidth <= 0 || halfWidth > 2 || height <= 0 || height > 4) return false;
            double x0 = feet.x() - halfWidth, x1 = feet.x() + halfWidth;
            double z0 = feet.z() - halfWidth, z1 = feet.z() + halfWidth;
            if (x0 < PLAY.minX || x1 > PLAY.maxX || z0 < PLAY.minZ || z1 > PLAY.maxZ
                    || feet.y() < PLAY.minY || feet.y() + height > PLAY.maxY) return false;
            if (collides(new VoxelQueries.Box((float)x0, (float)feet.y(), (float)z0,
                    (float)x1, (float)(feet.y() + height), (float)z1))) return false;
            // Full-cell map surfaces only. A changed partial/model/water cell is not trusted support.
            int y = (int)Math.floor(feet.y() - 0.001);
            if (Math.abs(feet.y() - (y + 1)) > 0.001) return false;
            for (int x = (int)Math.floor(x0); x < Math.ceil(x1); x++)
                for (int z = (int)Math.floor(z0); z < Math.ceil(z1); z++) {
                    int type = world.getBlock(x, y, z);
                    if (type == Blocks.AIR || type == Blocks.WATER || type == Blocks.PARTIAL
                            || Blocks.isModel(type) || Blocks.isPiece(type)) return false;
                }
            return true;
        }

        /** Restores all owned cells, including deleted floors, added blocks and partial edits.
         * Does not clear external match/player state. Call between matches on the world owner thread.
         */
        public void reset() {
            requireOpen();
            for (int x = FOOTPRINT.minX; x < FOOTPRINT.maxX; x++)
                for (int z = FOOTPRINT.minZ; z < FOOTPRINT.maxZ; z++)
                    for (int y = FOOTPRINT.minY; y < FOOTPRINT.maxY; y++) {
                        if (!world.isLoaded(x, y, z))
                            throw new IllegalStateException("Arena chunks must remain resident");
                        int expected = blockAt(x, y, z);
                        if (world.getBlock(x, y, z) != expected) world.setBlock(x, y, z, expected);
                    }
        }
        private void requireOpen() {
            if (world.isClosed()) throw new IllegalStateException("Arena closed");
        }
        @Override public void close() throws Exception { world.close(); }
    }
    private BasaltCourtyard() {}
}
