package dev.jayms.combat;

import java.util.random.RandomGenerator;

import dev.jayms.combat.CombatEngine.Item;
import dev.jayms.combat.CombatEngine.Vec3;

/** Flight of hand-thrown items at 20 Hz, following Paper 1.7.10. */
final class ThrowablePhysics {
    private static final double HAND_OFFSET = .16;
    private static final double EYE_DROP = .10;
    private static final double SPREAD = .0075;
    private static final double AIR_DRAG = .99;
    private static final double WATER_DRAG = .80;

    private ThrowablePhysics() {}

    record Launch(Vec3 position, Vec3 velocity) {}

    static Launch launch(Vec3 feet, float yaw, float pitch, Item item, RandomGenerator random) {
        double azimuth = Math.toRadians(yaw);
        double x = Math.cos(azimuth);
        double z = Math.sin(azimuth);
        Vec3 position = feet.add(-z * HAND_OFFSET, CombatGeometry.EYE_HEIGHT - EYE_DROP, x * HAND_OFFSET);

        boolean pearl = item == Item.PEARL;
        double horizontal = Math.cos(Math.toRadians(pitch));
        // EntityPotion tilts only the vertical component before normalizing the throw.
        double vertical = Math.sin(Math.toRadians(pitch + (pearl ? 0 : 20)));
        Vec3 aim = new Vec3(x * horizontal, vertical, z * horizontal);
        Vec3 velocity = aim.scale(1 / aim.length())
                .add(random.nextGaussian() * SPREAD, random.nextGaussian() * SPREAD, random.nextGaussian() * SPREAD)
                .scale(pearl ? 1.5 : .5);
        return new Launch(position, velocity);
    }

    static Vec3 nextVelocity(Item item, Vec3 velocity, boolean inWater) {
        return velocity.scale(inWater ? WATER_DRAG : AIR_DRAG).add(0, item == Item.PEARL ? -.03 : -.05, 0);
    }
}
