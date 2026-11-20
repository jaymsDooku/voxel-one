package dev.jayms.combat;

import dev.jayms.combat.CombatEngine.Arena;
import dev.jayms.combat.CombatEngine.Vec3;
import dev.jayms.player.PlayerDimensions;

public final class CombatGeometry {
    static final double BODY_RADIUS = PlayerDimensions.RADIUS;
    static final double BODY_HEIGHT = PlayerDimensions.HEIGHT;
    static final double EYE_HEIGHT = PlayerDimensions.EYE_HEIGHT;
    private static final double THROWABLE_TARGET_MARGIN = .3F;
    private static final double EPSILON = 1e-7;
    private static final double CONTACT_EPSILON = .0001;

    private CombatGeometry() {}

    /** The game camera uses east as yaw zero and positive pitch for up. */
    public static Vec3 direction(float yaw, float pitch) {
        double azimuth = Math.toRadians(yaw), elevation = Math.toRadians(pitch);
        double horizontal = Math.cos(elevation);
        return new Vec3(Math.cos(azimuth) * horizontal, Math.sin(elevation), Math.sin(azimuth) * horizontal);
    }

    static boolean free(Arena arena, Vec3 feet) {
        if (!arena.contains(feet)) return false;
        for (int x = cell(feet.x() - BODY_RADIUS + EPSILON); x <= cell(feet.x() + BODY_RADIUS - EPSILON); x++) {
            for (int y = cell(feet.y() + EPSILON); y <= cell(feet.y() + BODY_HEIGHT - EPSILON); y++) {
                for (int z = cell(feet.z() - BODY_RADIUS + EPSILON); z <= cell(feet.z() + BODY_RADIUS - EPSILON); z++) {
                    if (arena.solid(x, y, z)) return false;
                }
            }
        }
        return true;
    }

    static boolean supported(Arena arena, Vec3 feet) {
        for (int x = cell(feet.x() - BODY_RADIUS + EPSILON); x <= cell(feet.x() + BODY_RADIUS - EPSILON); x++) {
            for (int z = cell(feet.z() - BODY_RADIUS + EPSILON); z <= cell(feet.z() + BODY_RADIUS - EPSILON); z++) {
                if (arena.solid(x, cell(feet.y() - .025), z)) return true;
            }
        }
        return false;
    }

    static Vec3 move(Arena arena, int playerId, Vec3 feet, double amount, int axis) {
        if (amount == 0) return feet;
        int steps = Math.max(1, (int) Math.ceil(Math.abs(amount) / .2));
        double step = amount / steps;
        Vec3 current = feet;
        for (int i = 0; i < steps; i++) {
            Vec3 next = axisOffset(current, step, axis);
            if (!free(arena, next) || !arena.canEnter(playerId, next)) {
                double clear = 0, blocked = step;
                for (int split = 0; split < 15; split++) {
                    double mid = (clear + blocked) * .5;
                    Vec3 candidate = axisOffset(current, mid, axis);
                    if (free(arena, candidate) && arena.canEnter(playerId, candidate)) clear = mid;
                    else blocked = mid;
                }
                return axisOffset(current, clear, axis);
            }
            current = next;
        }
        return current;
    }

    static Vec3 axisOffset(Vec3 point, double amount, int axis) {
        return switch (axis) {
            case 0 -> point.add(amount, 0, 0);
            case 1 -> point.add(0, amount, 0);
            case 2 -> point.add(0, 0, amount);
            default -> throw new IllegalArgumentException("Invalid axis");
        };
    }

    /** Walk the crossed voxels and return the first impact fraction in [0,1]. */
    static double firstBlock(Arena arena, Vec3 start, Vec3 delta) {
        return firstBlock(start, delta, arena::solid);
    }

    static double firstThrowableBlock(Arena arena, Vec3 start, Vec3 delta) {
        return firstBlock(start, delta, arena::throwableSolid);
    }

    private static double firstBlock(Vec3 start, Vec3 delta, BlockTest blocks) {
        int x = cell(start.x()), y = cell(start.y()), z = cell(start.z());
        int stepX = sign(delta.x()), stepY = sign(delta.y()), stepZ = sign(delta.z());
        double timeX = boundary(start.x(), delta.x(), x, stepX);
        double timeY = boundary(start.y(), delta.y(), y, stepY);
        double timeZ = boundary(start.z(), delta.z(), z, stepZ);
        double incrementX = interval(delta.x()), incrementY = interval(delta.y()), incrementZ = interval(delta.z());
        double time = 0;
        while (time <= 1) {
            if (blocks.solid(x, y, z)) return time;
            if (timeX <= timeY && timeX <= timeZ) {
                time = timeX;
                x += stepX;
                timeX += incrementX;
            } else if (timeY <= timeZ) {
                time = timeY;
                y += stepY;
                timeY += incrementY;
            } else {
                time = timeZ;
                z += stepZ;
                timeZ += incrementZ;
            }
        }
        return Double.POSITIVE_INFINITY;
    }

    @FunctionalInterface
    private interface BlockTest {
        boolean solid(int x, int y, int z);
    }

    public static double firstBody(Vec3 start, Vec3 delta, Vec3 feet) {
        return firstBox(start, delta, feet.add(-BODY_RADIUS, 0, -BODY_RADIUS),
                feet.add(BODY_RADIUS, BODY_HEIGHT, BODY_RADIUS), true);
    }

    static double firstThrowableBody(Vec3 start, Vec3 delta, Vec3 feet) {
        double radius = BODY_RADIUS + THROWABLE_TARGET_MARGIN;
        Vec3 min = feet.add(-radius, -THROWABLE_TARGET_MARGIN, -radius);
        Vec3 max = feet.add(radius, BODY_HEIGHT + THROWABLE_TARGET_MARGIN, radius);
        return firstBox(start, delta, min, max, false);
    }

    /** Collision-safe route shared by rendering and between-tick hit validation. */
    public static PosePath posePath(Arena arena, Vec3 left, Vec3 right, long gap) {
        double distance = left.minus(right).length();
        if (gap < 1 || gap > 3 || distance > 1.75 * gap || !arena.contains(left) || !arena.contains(right)) return null;
        if (distance == 0 || clearSweep(arena, left, right)) return new PosePath(distance, left, right);
        if (gap != 1) return null;
        // Match the movement solver's Y, X, Z clipping at an obstructed diagonal.
        var y = new Vec3(left.x(), right.y(), left.z());
        var x = new Vec3(right.x(), right.y(), left.z());
        if (!clearSweep(arena, left, y) || !clearSweep(arena, y, x) || !clearSweep(arena, x, right)) return null;
        return new PosePath(left.minus(y).length() + y.minus(x).length() + x.minus(right).length(), left, y, x, right);
    }

    public static final class PosePath {
        private final double length;
        private final Vec3[] points;

        private PosePath(double length, Vec3... points) {
            this.length = length;
            this.points = points;
        }

        public Vec3 at(double progress) {
            double travel = length * progress;
            for (int i = 1; i < points.length; i++) {
                Vec3 from = points[i - 1], delta = points[i].minus(from);
                double segment = delta.length();
                if (segment > 0 && travel <= segment) return from.add(delta.scale(travel / segment));
                travel -= segment;
            }
            return points[points.length - 1];
        }
    }

    private static boolean clearSweep(Arena arena, Vec3 left, Vec3 right) {
        int minX = cell(Math.min(left.x(), right.x()) - BODY_RADIUS);
        int maxX = cell(Math.max(left.x(), right.x()) + BODY_RADIUS);
        int minY = cell(Math.min(left.y(), right.y()));
        int maxY = cell(Math.max(left.y(), right.y()) + BODY_HEIGHT);
        int minZ = cell(Math.min(left.z(), right.z()) - BODY_RADIUS);
        int maxZ = cell(Math.max(left.z(), right.z()) + BODY_RADIUS);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (arena.solid(x, y, z) && crossesBody(left, right, x, y, z)) return false;
                }
            }
        }
        return true;
    }

    private static boolean crossesBody(Vec3 left, Vec3 right, int x, int y, int z) {
        // Expand the cell by the body and intersect the three entry and exit intervals.
        Range sx = sweepAxis(left.x(), right.x(), x - BODY_RADIUS, x + 1 + BODY_RADIUS);
        Range sy = sweepAxis(left.y(), right.y(), y - BODY_HEIGHT, y + 1);
        Range sz = sweepAxis(left.z(), right.z(), z - BODY_RADIUS, z + 1 + BODY_RADIUS);
        return Math.max(0, Math.max(sx.entry, Math.max(sy.entry, sz.entry)))
                < Math.min(1, Math.min(sx.exit, Math.min(sy.exit, sz.exit)));
    }

    private record Range(double entry, double exit) {}

    private static Range sweepAxis(double from, double to, double min, double max) {
        min += CONTACT_EPSILON;
        max -= CONTACT_EPSILON;
        if (from == to) return from > min && from < max ? new Range(0, 1) : new Range(1, 0);
        double a = (min - from) / (to - from), b = (max - from) / (to - from);
        return new Range(Math.min(a, b), Math.max(a, b));
    }

    private static double firstBox(Vec3 start, Vec3 delta, Vec3 min, Vec3 max, boolean includeInterior) {
        double entry = includeInterior ? 0 : Double.NEGATIVE_INFINITY;
        double exit = includeInterior ? 1 : Double.POSITIVE_INFINITY;
        for (int axis = 0; axis < 3; axis++) {
            double origin = start.component(axis), direction = delta.component(axis);
            double lo = min.component(axis), hi = max.component(axis);
            if (Math.abs(direction) < EPSILON) {
                if (origin < lo || origin > hi) return Double.POSITIVE_INFINITY;
                continue;
            }
            double from = (lo - origin) / direction, to = (hi - origin) / direction;
            entry = Math.max(entry, Math.min(from, to));
            exit = Math.min(exit, Math.max(from, to));
            if (entry > exit) return Double.POSITIVE_INFINITY;
        }
        // Paper intersects box faces. A segment starting inside hits only if it reaches an exit face.
        double hit = entry >= 0 ? entry : exit;
        return hit >= 0 && hit <= 1 ? hit : Double.POSITIVE_INFINITY;
    }

    private static int cell(double value) {
        return (int) Math.floor(value);
    }

    private static int sign(double value) {
        return value > EPSILON ? 1 : value < -EPSILON ? -1 : 0;
    }

    private static double boundary(double start, double delta, int cell, int step) {
        return step == 0 ? Double.POSITIVE_INFINITY : ((step > 0 ? cell + 1 : cell) - start) / delta;
    }

    private static double interval(double delta) {
        return Math.abs(delta) <= EPSILON ? Double.POSITIVE_INFINITY : Math.abs(1 / delta);
    }
}
