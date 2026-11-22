package dev.jayms.combat;

import dev.jayms.combat.CombatEngine.Vec3;
import java.util.random.RandomGenerator;

/** Stock bow and arrow values from Paper 1.7's ItemBow and EntityArrow. */
final class BowPhysics {
    static final int GROUND_LIFETIME = 1_200, PICKUP_DELAY = 7;
    private static final double SPREAD = (double) .0075f;

    private BowPhysics() {}

    static float force(int heldTicks) {
        float charge = heldTicks / (float) CombatEngine.BOW_FULL_DRAW_TICKS;
        float force = (charge * charge + 2 * charge) / 3;
        return force < .1 ? 0 : Math.min(force, 1);
    }

    static Vec3 origin(Vec3 feet, float yaw) {
        return feet.add(0, CombatGeometry.EYE_HEIGHT - .1f, 0)
                .add(CombatGeometry.direction(yaw + 90, 0).scale(.16f));
    }

    static Vec3 launchVelocity(float yaw, float pitch, float force, RandomGenerator random) {
        return CombatGeometry.direction(yaw, pitch)
                .add(spread(random), spread(random), spread(random)).scale(force * 2 * 1.5f);
    }

    private static double spread(RandomGenerator random) {
        return random.nextGaussian() * (random.nextBoolean() ? -SPREAD : SPREAD);
    }

    static Vec3 nextVelocity(Vec3 recent, boolean inWater) {
        return recent.scale(inWater ? .8f : .99f).add(0, -.05f, 0);
    }

    static int damage(Vec3 velocity, boolean critical, RandomGenerator random) {
        float speed = (float) velocity.length();
        int base = (int) Math.ceil((double) speed * 2);
        return critical ? base + random.nextInt(base / 2 + 2) : base;
    }
}
