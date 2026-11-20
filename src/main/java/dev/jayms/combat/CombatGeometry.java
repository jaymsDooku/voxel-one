package dev.jayms.combat;

import dev.jayms.combat.CombatEngine.Arena;
import dev.jayms.combat.CombatEngine.Vec3;

final class CombatGeometry {
    static final double BODY_RADIUS = .31;
    static final double BODY_HEIGHT = 1.8;
    static final double EYE_HEIGHT = 1.62;
    private static final double EPSILON = 1e-7;

    private CombatGeometry() {}

    /** The game camera uses east as yaw zero and positive pitch for up. */
    static Vec3 direction(float yaw, float pitch) {
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
        int x = cell(start.x()), y = cell(start.y()), z = cell(start.z());
        int stepX = sign(delta.x()), stepY = sign(delta.y()), stepZ = sign(delta.z());
        double timeX = boundary(start.x(), delta.x(), x, stepX);
        double timeY = boundary(start.y(), delta.y(), y, stepY);
        double timeZ = boundary(start.z(), delta.z(), z, stepZ);
        double incrementX = interval(delta.x()), incrementY = interval(delta.y()), incrementZ = interval(delta.z());
        double time = 0;
        while (time <= 1) {
            if (arena.solid(x, y, z)) return time;
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

    static double firstBody(Vec3 start, Vec3 delta, Vec3 feet) {
        return firstBox(start, delta, feet.add(-BODY_RADIUS, 0, -BODY_RADIUS),
                feet.add(BODY_RADIUS, BODY_HEIGHT, BODY_RADIUS));
    }

    private static double firstBox(Vec3 start, Vec3 delta, Vec3 min, Vec3 max) {
        double entry = 0, exit = 1;
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
        return entry;
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
