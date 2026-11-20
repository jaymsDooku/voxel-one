package dev.jayms.combat;

import dev.jayms.combat.CombatEngine.Item;
import dev.jayms.combat.CombatEngine.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThrowablePhysicsTest {
    private static final double EPSILON = 1e-6;
    private static final Vec3 FEET = new Vec3(3, 10, -2);

    static Random noSpread() {
        return new Random(0) {
            @Override public double nextGaussian() { return 0; }
        };
    }

    @Test void pearlLaunchesFromTheSameHandOffsetAtEachYaw() {
        var east = ThrowablePhysics.launch(FEET, 0, 0, Item.PEARL, noSpread());
        assertVector(FEET.add(0, 1.52, .16), east.position());
        assertVector(new Vec3(1.5, 0, 0), east.velocity());

        var south = ThrowablePhysics.launch(FEET, 90, 30, Item.PEARL, noSpread());
        assertVector(FEET.add(-.16, 1.52, 0), south.position());
        assertVector(new Vec3(0, .75, 1.5 * Math.cos(Math.toRadians(30))), south.velocity());
    }

    @Test void splashPotionsBiasOnlyTheVerticalAimBeforeNormalization() {
        double loft = Math.sin(Math.toRadians(20));
        double scale = .5 / Math.hypot(1, loft);
        var flat = ThrowablePhysics.launch(FEET, 0, 0, Item.HEALING, noSpread());
        assertVector(FEET.add(0, 1.52, .16), flat.position());
        assertVector(new Vec3(scale, scale * loft, 0), flat.velocity());

        double horizontal = Math.cos(Math.toRadians(50));
        double vertical = -.5;
        double downScale = .5 / Math.hypot(horizontal, vertical);
        for (Item item : new Item[] {Item.HEALING, Item.POISON, Item.SLOWNESS}) {
            var downward = ThrowablePhysics.launch(FEET, 0, -50, item, noSpread());
            assertVector(new Vec3(downScale * horizontal, downScale * vertical, 0), downward.velocity());
        }
    }

    @Test void launchAddsGaussianSpreadBeforeApplyingSpeed() {
        var standardDeviations = new Random(0) {
            private int calls;

            @Override public double nextGaussian() { return ++calls; }
        };
        var shot = ThrowablePhysics.launch(FEET, 0, 0, Item.PEARL, standardDeviations);
        assertVector(new Vec3((1 + .0075) * 1.5, .015 * 1.5, .0225 * 1.5), shot.velocity());
    }

    @Test void dragActsOnExistingVelocityBeforeGravityInAirAndWater() {
        Vec3 incoming = new Vec3(1, -.25, .5);
        assertVector(new Vec3(.99, -.2775, .495), ThrowablePhysics.nextVelocity(Item.PEARL, incoming, false));
        assertVector(new Vec3(.99, -.2975, .495), ThrowablePhysics.nextVelocity(Item.HEALING, incoming, false));
        assertVector(new Vec3(.8, -.23, .4), ThrowablePhysics.nextVelocity(Item.PEARL, incoming, true));
        assertVector(new Vec3(.8, -.25, .4), ThrowablePhysics.nextVelocity(Item.SLOWNESS, incoming, true));
    }

    static void assertVector(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x(), actual.x(), EPSILON, "x");
        assertEquals(expected.y(), actual.y(), EPSILON, "y");
        assertEquals(expected.z(), actual.z(), EPSILON, "z");
    }
}
