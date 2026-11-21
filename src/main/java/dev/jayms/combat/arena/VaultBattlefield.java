package dev.jayms.combat.arena;

import dev.jayms.combat.CombatEngine.Vec3;
import dev.jayms.net.Blocks;

import java.util.ArrayList;
import java.util.List;

/** Vault siege terrain and the ground routes connecting its two objectives. */
public final class VaultBattlefield {
    public static final int HALF_WIDTH = 96, HALF_LENGTH = 72;
    public static final int FLOOR_Y = 72, TRENCH_Y = 68, CEILING_Y = 106;
    private static final int CORE_Z = 43;
    private static final int SIDE_TRENCH_INNER = 44, SIDE_TRENCH_OUTER = 48, REAR_TRENCH = 68;
    private static final int FIRST_SIDE_RAMP = 14, RAMP_SPACING = 18;
    private static final int[] LANE_X = {-27, 0, 27};
    private static final Vec3 BREACH_MARKER = feet(0, 20);
    private static final Vec3 CORE_MARKER = feet(0, 44);
    private static final List<Lane> LANES = List.of(routes(0), routes(1), routes(2));

    private VaultBattlefield() {}

    public static BasaltCourtyard.Spawn spawn(int side, int member) {
        if (side < 0 || side > 1 || member < 0 || member >= 30)
            throw new IllegalArgumentException("Invalid vault battle team slot");
        int column = member / 3 % 5;
        int offset = (column + 1) / 2 * (column % 2 == 0 ? -2 : 2);
        int row = member / 15;
        return new BasaltCourtyard.Spawn(LANE_X[laneForSpawn(member)] + offset + .5f, FLOOR_Y + 1,
                side == 0 ? 27.5f + row * 2 : -62.5f + row * 3, side == 0 ? -90 : 90, 0);
    }

    public static int laneForSpawn(int member) {
        return switch (member % 3) {
            case 0 -> 1;
            case 1 -> 0;
            default -> 2;
        };
    }

    public static BasaltCourtyard.Spawn spectator() {
        return new BasaltCourtyard.Spawn(88.5f, 99, -2.5f, 177, -16);
    }

    public static Vec3 breachMarker() { return BREACH_MARKER; }
    public static Vec3 coreMarker() { return CORE_MARKER; }

    public static boolean inBreachZone(Vec3 feet) {
        return feet.x() >= -12 && feet.x() < 13 && feet.z() >= 19 && feet.z() < 24 && atGroundLevel(feet);
    }

    public static boolean inCoreZone(Vec3 feet) {
        return feet.x() >= -7 && feet.x() < 8 && feet.z() >= 39 && feet.z() < 49 && atGroundLevel(feet);
    }

    public static List<Vec3> attackRoute(int lane, boolean core) {
        return core ? LANES.get(lane).attackCore() : LANES.get(lane).attackBreach();
    }

    public static List<Vec3> defendRoute(int lane, boolean hold, boolean core) {
        Lane routes = LANES.get(lane);
        if (core) return hold ? routes.holdCore() : routes.interceptCore();
        return hold ? routes.holdBreach() : routes.interceptBreach();
    }

    /** Returns a route from the trench floor to a battlefield rally lane. */
    public static List<Vec3> escapeRoute(Vec3 start, int lane) {
        int x = (int) Math.floor(start.x()), z = (int) Math.floor(start.z());
        if (!trench(x, z)) return List.of();

        int direction = x < 0 ? -1 : 1;
        int outside = direction * (SIDE_TRENCH_OUTER + 2);
        Vec3 rally = feet(LANE_X[lane], 8);
        if (z >= REAR_TRENCH) {
            return List.of(trenchFeet(x, REAR_TRENCH), feet(outside, REAR_TRENCH), feet(outside, 8), rally);
        }
        if (Math.abs(x) >= SIDE_TRENCH_INNER) {
            int inner = direction * SIDE_TRENCH_INNER;
            int rampZ = FIRST_SIDE_RAMP + RAMP_SPACING * Math.round((float) (z - FIRST_SIDE_RAMP) / RAMP_SPACING);
            return List.of(trenchFeet(inner, z), trenchFeet(inner, rampZ), feet(outside, rampZ), feet(outside, 8), rally);
        }
        int rampX = direction * (Math.abs(x) < LANE_X[2] ? 17 : 38);
        return List.of(trenchFeet(x, 15), trenchFeet(rampX, 15), feet(rampX, 8), rally);
    }

    private static boolean atGroundLevel(Vec3 feet) {
        return feet.y() >= FLOOR_Y + .99 && feet.y() < FLOOR_Y + 3;
    }

    private static Lane routes(int lane) {
        int x = LANE_X[lane];
        int direction = lane - 1;
        List<Vec3> approach = List.of(feet(x, -54), feet(x, -44), feet(x, -31), feet(x, -11),
                feet(x, 8), feet(x, 20));
        List<Vec3> toCore = lane == 1
                ? List.of(feet(x, 28), feet(x, 36), feet(0, 44))
                : List.of(feet(x, 28), feet(x, CORE_Z), feet(direction * 6, CORE_Z));
        return new Lane(extend(approach, List.of(feet(direction * 9, 20))), extend(approach, toCore),
                List.of(feet(x, 28), feet(x, 22), feet(direction * 8, 22)),
                List.of(feet(x, 28), feet(x, 20), feet(x, 6)),
                toCore, List.of(feet(x, 28), feet(x, lane == 1 ? 34 : 35)));
    }

    private static List<Vec3> extend(List<Vec3> approach, List<Vec3> destination) {
        var route = new ArrayList<>(approach);
        route.addAll(destination);
        return List.copyOf(route);
    }

    private static Vec3 feet(int x, int z) {
        return new Vec3(x + .5, FLOOR_Y + 1, z + .5);
    }

    private static Vec3 trenchFeet(int x, int z) {
        return new Vec3(x + .5, trenchFloor(x, z) + 1, z + .5);
    }

    private record Lane(List<Vec3> attackBreach, List<Vec3> attackCore, List<Vec3> holdBreach,
                        List<Vec3> interceptBreach, List<Vec3> holdCore, List<Vec3> interceptCore) {}

    public static int block(int x, int y, int z) {
        if (x < -HALF_WIDTH || x >= HALF_WIDTH || z < -HALF_LENGTH || z >= HALF_LENGTH
                || y < TRENCH_Y || y >= CEILING_Y) return Blocks.AIR;
        int height = y - FLOOR_Y;
        int side = Math.abs(x);
        if (height < 0) return belowGrade(x, y, z);
        if (height == 0) return ground(x, z);
        if ((x == -HALF_WIDTH || x == HALF_WIDTH - 1 || z == -HALF_LENGTH || z == HALF_LENGTH - 1)
                && height <= 5) return height == 5 ? Blocks.STONE : Blocks.ASPHALT;
        if (x >= 87 && x <= 92 && z >= -7 && z <= 2) {
            if (height == 26) return Blocks.STONE;
            if (height == 27 && (x == 92 || z == -7 || z == 2)) return Blocks.LED;
            if (height < 26 && x == 91 && (z == -6 || z == 1)) return Blocks.ASPHALT;
        }

        int staging = stagingVault(x, z, height);
        if (staging != Blocks.AIR) return staging;

        int bunker = coreBunker(x, z, height);
        if (bunker != Blocks.AIR) return bunker;

        int tower = watchtower(x, z, height);
        if (tower != Blocks.AIR) return tower;

        int outerRing = ring(x, z, height, 43, 25, 7);
        if (outerRing != Blocks.AIR) return outerRing;

        int innerRing = ring(x, z, height, 34, 17, 5);
        if (innerRing != Blocks.AIR) return innerRing;

        if (z >= -40 && z <= 7) return battlefieldCover(side, z, height);
        return Blocks.AIR;
    }

    private static int belowGrade(int x, int y, int z) {
        return !trench(x, z) || y <= trenchFloor(x, z) ? Blocks.STONE : Blocks.AIR;
    }

    private static int ground(int x, int z) {
        int side = Math.abs(x);
        if (trench(x, z)) return trenchFloor(x, z) == FLOOR_Y ? Blocks.STONE : Blocks.AIR;
        if (z >= 19 && z < 24 && x >= -12 && x < 13) {
            return x == -12 || x == 12 || z == 19 || z == 23 ? Blocks.LED : Blocks.STONE;
        }
        if (z >= 39 && z < 49 && x >= -7 && x < 8) {
            return x == -7 || x == 7 || z == 39 || z == 48 ? Blocks.LED : Blocks.ASPHALT;
        }
        int fromLane = distanceToLane(x);
        if (fromLane == 4 && z > -48 && z < 16 && Math.floorMod(z, 4) < 2) return Blocks.SAND;
        if (fromLane <= 3 && z >= -63 && z < 33) return Blocks.ASPHALT;
        if (z >= -66 && z < -47 && side <= 37) return Blocks.PLANKS;
        if (z >= 17 && side <= 43) return Blocks.STONE;
        if ((side >= 54 || z >= 65) && Math.floorMod(x / 5 + z / 5, 4) != 0) return Blocks.GRASS;
        return Math.floorMod(x / 5 + z / 4, 5) == 0 ? Blocks.MINERAL : Blocks.STONE;
    }

    private static boolean trench(int x, int z) {
        int side = Math.abs(x);
        return side <= SIDE_TRENCH_OUTER && z >= 11 && z <= 15 && distanceToLane(x) > 4
                || side >= SIDE_TRENCH_INNER && side <= SIDE_TRENCH_OUTER && z >= 16 && z <= REAR_TRENCH + 1
                || side < SIDE_TRENCH_INNER && z >= REAR_TRENCH && z <= REAR_TRENCH + 1;
    }

    private static int trenchFloor(int x, int z) {
        int side = Math.abs(x);
        // Front ramps ascend toward the battlefield. Side ramps ascend away from the ring wall.
        if (z >= 11 && z <= 15 && (side >= 16 && side <= 18 || side >= 37 && side <= 39))
            return FLOOR_Y - (z - 11);
        if (side >= SIDE_TRENCH_INNER && side <= SIDE_TRENCH_OUTER
                && (Math.floorMod(z - FIRST_SIDE_RAMP + 1, RAMP_SPACING) <= 2 || z >= REAR_TRENCH))
            return TRENCH_Y + side - SIDE_TRENCH_INNER;
        return TRENCH_Y;
    }

    private static int ring(int x, int z, int height, int width, int depth, int top) {
        int side = Math.abs(x), end = Math.abs(z - CORE_Z + 1);
        if (side > width || end > depth || (side < width - 1 && end < depth - 1) || height > top) return Blocks.AIR;
        boolean gate = (z < CORE_Z && distanceToLane(x) <= 3) || (z > CORE_Z && side <= 3)
                || Math.abs(z - CORE_Z) <= 2;
        if (gate && height <= top - 2) return Blocks.AIR;
        if (height == top && !gate && (x + z) % 3 == 0) return Blocks.AIR;
        if (height == 3 && (gate || Math.floorMod(x + z, 11) == 0)) return Blocks.LED;
        return height == 1 || height == top - 1 ? Blocks.STONE : Blocks.ASPHALT;
    }

    private static int coreBunker(int x, int z, int height) {
        int side = Math.abs(x), end = Math.abs(z - CORE_Z);
        int tier = Math.min(17 - side, 14 - end);
        if (side <= 2 && end <= 2 && height > 14 && height <= 21) {
            return height == 21 || side == 2 && end == 2 ? Blocks.LED : Blocks.STONE;
        }
        if (height > tier) return Blocks.AIR;
        boolean chamber = side <= 8 && z >= 38 && z <= 49;
        boolean entrances = z < CORE_Z && side <= 3 || end <= 3;
        if (chamber && height <= 6 || entrances && height <= 4) return Blocks.AIR;
        if (height == tier) return tier % 4 == 0 ? Blocks.STONE : Blocks.ASPHALT;
        if (height == 5 && entrances && (side == 3 || end == 3)) return Blocks.LED;
        return Blocks.ASPHALT;
    }

    private static int watchtower(int x, int z, int height) {
        int fromTower = Math.abs(Math.abs(x) - 37);
        if (fromTower <= 2 && z >= 51 && z <= 65 && height <= (67 - z) / 2) {
            return height == (67 - z) / 2 ? Blocks.STONE : Blocks.ASPHALT;
        }
        if (fromTower > 4 || z < 39 || z > 50) return Blocks.AIR;
        if (height == 8) return Blocks.PLANKS;
        if (height == 9 && (fromTower == 4 || z == 39 || z == 50 && fromTower > 2)) return Blocks.STONE;
        if (fromTower == 3 && (z == 41 || z == 48) && height < 8) return height == 7 ? Blocks.LED : Blocks.ASPHALT;
        return Blocks.AIR;
    }

    private static int stagingVault(int x, int z, int height) {
        int side = Math.abs(x);
        if (side > 38 || z < -68 || z > -46) return Blocks.AIR;
        if (z <= -65 && side <= 14 && height <= 9 - Math.max(0, side - 9)) {
            if (side <= 3 && height <= 4 && z > -68) return Blocks.AIR;
            return height >= 7 ? Blocks.SAND : Blocks.BRICKS;
        }
        boolean bastion = Math.abs(side - 14) <= 4 && z >= -51;
        int top = bastion ? 11 : 6;
        boolean wall = side >= 37 || z == -68 || z >= -47;
        if ((!wall && !bastion) || height > top) return Blocks.AIR;
        if (z >= -47 && distanceToLane(x) <= 3 && height <= 4) return Blocks.AIR;
        if (height == top && (x + z) % 3 == 0) return Blocks.AIR;
        if (height == 4 && Math.floorMod(x + z, 7) == 0) return Blocks.LED;
        return height == 1 || height == top - 1 ? Blocks.SAND : Blocks.BRICKS;
    }

    private static int battlefieldCover(int side, int z, int height) {
        if (side >= 10 && side <= 15 && z >= -22 && z <= -18 && height <= 4
                || side >= 14 && side <= 17 && z >= -2 && z <= 5 && height <= 2
                || side >= 38 && side <= 48 && z >= -35 && z <= -32 && height <= 3
                || side >= 42 && side <= 46 && z >= -14 && z <= -7 && height <= 4) {
            return height == 1 ? Blocks.STONE : Blocks.ASPHALT;
        }
        if (side >= 60 && side <= 78 && z >= -28 && z <= -23 && height <= 3) return Blocks.BRICKS;
        return Blocks.AIR;
    }

    private static int distanceToLane(int x) {
        return Math.min(Math.abs(x), Math.abs(Math.abs(x) - LANE_X[2]));
    }
}
