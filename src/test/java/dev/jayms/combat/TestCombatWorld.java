package dev.jayms.combat;

import java.util.OptionalDouble;

/** Synthetic empty floor and optional full-height wall. No game profile or account data. */
final class TestCombatWorld implements CombatWorld {
    double wallX = Double.POSITIVE_INFINITY;
    double bound = 64;
    boolean landingAllowed = true;
    @Override public OptionalDouble firstSolid(Vec3 from, Vec3 to, double radius) {
        double fraction = Double.POSITIVE_INFINITY;
        if (from.y() < radius) fraction = 0;
        else if (to.y() <= radius && to.y() < from.y())
            fraction = (from.y() - radius) / (from.y() - to.y());
        if (Double.isFinite(wallX)) {
            if (Math.abs(from.x() - wallX) <= radius) fraction = 0;
            else if (from.x() < wallX - radius && to.x() >= wallX - radius)
                fraction = Math.min(fraction, (wallX - radius - from.x()) / (to.x() - from.x()));
            else if (from.x() > wallX + radius && to.x() <= wallX + radius)
                fraction = Math.min(fraction, (wallX + radius - from.x()) / (to.x() - from.x()));
        }
        return fraction <= 1 ? OptionalDouble.of(fraction) : OptionalDouble.empty();
    }
    @Override public boolean safeLanding(Vec3 feet, double halfWidth, double height) {
        return landingAllowed && Math.abs(feet.x()) + halfWidth < bound
                && Math.abs(feet.z()) + halfWidth < bound && feet.y() == 0
                && Math.abs(feet.x() - wallX) > halfWidth;
    }
    @Override public Motion move(Vec3 feet, Vec3 velocity, double halfWidth, double height) {
        Vec3 next = feet.add(velocity);
        double x = Math.max(-bound + halfWidth, Math.min(bound - halfWidth, next.x()));
        if (feet.x() < wallX - halfWidth && x >= wallX - halfWidth) x = wallX - halfWidth - .001;
        if (feet.x() > wallX + halfWidth && x <= wallX + halfWidth) x = wallX + halfWidth + .001;
        double y = Math.max(0, next.y());
        double z = Math.max(-bound + halfWidth, Math.min(bound - halfWidth, next.z()));
        return new Motion(new Vec3(x, y, z), new Vec3(x == next.x() ? velocity.x() : 0,
                y == 0 ? 0 : velocity.y(), z == next.z() ? velocity.z() : 0), y == 0);
    }
}
