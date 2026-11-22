package dev.jayms.combat;

import dev.jayms.combat.CombatEngine.Arena;
import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.CombatEngine.Item;
import dev.jayms.combat.CombatEngine.Stack;
import dev.jayms.combat.CombatEngine.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BowPhysicsTest {
    private static final Vec3 SPAWN = new Vec3(.5, 1, .5);
    private static final double EPSILON = 1e-6;

    @Test void paperForceOriginDragAndImpactValues() {
        assertEquals(0, BowPhysics.force(2));
        assertEquals(.1075, BowPhysics.force(3), EPSILON);
        assertEquals(5.0 / 12, BowPhysics.force(10), EPSILON);
        assertEquals(1, BowPhysics.force(20));
        assertEquals(1, BowPhysics.force(30));

        Vec3 east = BowPhysics.origin(SPAWN, 0);
        assertEquals(.5, east.x(), EPSILON);
        assertEquals(1 + CombatGeometry.EYE_HEIGHT - .1, east.y(), EPSILON);
        assertEquals(.66, east.z(), EPSILON);
        Vec3 south = BowPhysics.origin(SPAWN, 90);
        assertEquals(.34, south.x(), EPSILON);
        assertEquals(.5, south.z(), EPSILON);

        var random = new Random(5) {
            @Override public double nextGaussian() { return 0; }
            @Override public int nextInt(int bound) { return bound - 1; }
        };
        Vec3 velocity = BowPhysics.launchVelocity(0, 0, BowPhysics.force(20), random);
        assertEquals(new Vec3(3, 0, 0), velocity);
        assertEquals(2.97, BowPhysics.nextVelocity(velocity, false).x(), EPSILON);
        assertEquals(2.4, BowPhysics.nextVelocity(velocity, true).x(), EPSILON);
        assertEquals(-.05, BowPhysics.nextVelocity(velocity, true).y(), EPSILON);
        assertEquals(6, BowPhysics.damage(velocity, false, random));
        assertEquals(10, BowPhysics.damage(velocity, true, random));
        assertEquals(1, BowPhysics.damage(new Vec3(.32, 0, 0), false, random));
    }

    @Test void dryShortAndFullyDrawnBowsOnlySpendAmmoAndWearOnAValidRelease() {
        var dry = engine(field(false));
        arm(dry, 1, SPAWN, 0, CombatEngine.BOW_DURABILITY);
        draw(dry, 1, 20);
        assertEquals(0, dry.snapshot(1).bowDrawTicks());
        release(dry, 1);
        assertTrue(dry.projectiles().isEmpty());
        assertEquals(384, dry.snapshot(1).bowDurability());

        var armed = engine(field(false));
        arm(armed, 1, SPAWN, 2, CombatEngine.BOW_DURABILITY);
        draw(armed, 1, 2);
        release(armed, 1);
        assertTrue(armed.projectiles().isEmpty());
        assertEquals(2, armed.snapshot(1).inventory().get(35).count());
        assertEquals(384, armed.snapshot(1).bowDurability());

        for (int i = 0; i < 25; i++) {
            control(armed, 1, armed.currentTick(), 1, false, true, 0, 0);
            armed.tick();
        }
        assertEquals(20, armed.snapshot(1).bowDrawTicks());
        assertTrue(armed.projectiles().isEmpty());
        control(armed, 1, armed.currentTick(), 1, true, false, 0, 0);
        armed.tick();
        var flight = armed.projectiles().get(0);
        assertTrue(flight.critical());
        assertFalse(flight.embedded());
        assertEquals(2.97, flight.velocity().x(), EPSILON);
        assertTrue(armed.snapshot(1).velocity().x() > 0);
        assertEquals(armed.snapshot(1).feet().x() + 3, flight.position().x(), EPSILON);
        assertEquals(0, armed.snapshot(1).bowDrawTicks());
        assertEquals(1, armed.snapshot(1).inventory().get(35).count());
        assertEquals(383, armed.snapshot(1).bowDurability());
    }

    @Test void anActiveBowAppliesItemUseMovementButAnEmptyBowDoesNot() {
        var dry = engine(field(false));
        var armed = engine(field(false));
        arm(dry, 1, SPAWN, 0, 384);
        arm(armed, 1, SPAWN, 1, 384);
        control(dry, 1, 0, 1, true, true, 0, 0);
        control(armed, 1, 0, 1, true, true, 0, 0);
        dry.tick();
        armed.tick();
        assertTrue(dry.snapshot(1).sprinting());
        assertFalse(armed.snapshot(1).sprinting());
        assertTrue(armed.snapshot(1).velocity().x() < dry.snapshot(1).velocity().x() * .25);
    }

    @Test void cancellationAndSwitchingAwayDoNotShootEvenWithQueuedRelease() {
        var e = engine(field(false));
        arm(e, 1, SPAWN, 1, CombatEngine.BOW_DURABILITY);
        draw(e, 1, 20);
        control(e, 1, e.currentTick(), 0, false, false, 0, 0);
        e.cancelBow(1);
        e.tick();
        assertEquals(0, e.snapshot(1).bowDrawTicks());
        assertTrue(e.projectiles().isEmpty());

        draw(e, 1, 20);
        long sequence = e.currentTick();
        control(e, 1, sequence, 0, false, true, 1, 0);
        control(e, 1, sequence + 1, 0, false, false, 0, 0);
        e.tick();
        assertEquals(0, e.snapshot(1).bowDrawTicks());
        assertTrue(e.projectiles().isEmpty());
        assertEquals(1, e.snapshot(1).inventory().get(35).count());
    }

    @Test void aWallStopsFlightAndOnlyEmbeddedArrowsCanBePickedUpAfterSevenTicks() {
        var e = engine(field(true));
        arm(e, 1, SPAWN, 1, 384);
        assertTrue(e.join(2, new Vec3(7, 1, .5), 180));
        draw(e, 1, 20);
        release(e, 1);
        var arrow = e.projectiles().get(0);
        assertTrue(arrow.embedded());
        assertFalse(arrow.critical());
        assertEquals(2.95, arrow.position().x(), EPSILON);
        assertEquals(20, e.snapshot(2).health());
        assertTrue(e.join(3, new Vec3(2.5, 1, .5), 180));
        for (int tick = 0; tick < 6; tick++) e.tick();
        assertEquals(1, e.projectiles().size());
        e.tick();
        assertEquals(1, e.projectiles().size(), "Full inventory cannot pick up an arrow");
        var kit = new ArrayList<>(CombatRules.NO_DEBUFF.openingKit());
        kit.set(35, new Stack(Item.ARROW, 63));
        assertTrue(e.setKit(3, kit));
        assertTrue(e.reset(3, new Vec3(2.5, 1, .5), 180));
        e.tick();
        assertTrue(e.projectiles().isEmpty());
        assertEquals(64, e.snapshot(3).inventory().get(35).count());
    }

    @Test void groundDespawnAndBowWearSurviveInventoryMoves() {
        var e = engine(field(true));
        arm(e, 1, SPAWN, 2, 1);
        draw(e, 1, 20);
        release(e, 1);
        assertEquals(Item.BOW, e.snapshot(1).inventory().get(0).item());
        assertEquals(0, e.snapshot(1).bowDurability());
        assertTrue(e.quickMoveInventory(1, 0));
        assertEquals(new Stack(Item.BOW, 1, 0), e.snapshot(1).inventory().get(30));
        assertTrue(e.swapInventory(1, 30, 0));
        draw(e, 1, 20);
        release(e, 1);
        assertEquals(Item.EMPTY, e.snapshot(1).inventory().get(0).item());
        assertEquals(2, e.projectiles().size());
        for (long i = 0; i < BowPhysics.GROUND_LIFETIME - 22; i++) e.tick();
        assertEquals(2, e.projectiles().size());
        e.tick();
        assertEquals(1, e.projectiles().size());
        for (int i = 0; i < 21; i++) e.tick();
        assertTrue(e.projectiles().isEmpty());
    }

    @Test void movingArrowsIgnoreFriendlyBodiesAndUseTheExpandedEnemyBoxEvenAfterTheShooterDies() {
        var e = engine(new Arena() {
            @Override public boolean solid(int x, int y, int z) { return y <= 0; }
            @Override public boolean contains(Vec3 point) { return field(false).contains(point); }
            @Override public boolean canHarm(int shooter, int target) { return target != 2; }
        });
        arm(e, 1, SPAWN, 1, 384);
        assertTrue(e.join(2, new Vec3(3.5, 1, .5), 180));
        assertTrue(e.join(3, new Vec3(9.5, 1, 1.18), 180));
        draw(e, 1, 20);
        release(e, 1);
        assertTrue(e.damage(3, 1, 20));
        for (int i = 0; i < 6; i++) e.tick();
        assertEquals(20, e.snapshot(2).health());
        assertEquals(20 - 6 * .32, e.snapshot(3).health(), EPSILON);
        assertEquals(1, e.snapshot(1).stats().hits());
    }

    @Test void cooldownRejectedArrowBouncesAndAnUpwardShotIgnoresTheMovementCeiling() {
        var e = engine(new Arena() {
            @Override public boolean solid(int x, int y, int z) { return y <= 0 || y >= 3; }
            @Override public boolean throwableSolid(int x, int y, int z) { return y <= 0; }
            @Override public boolean contains(Vec3 feet) { return feet.y() < 3; }
            @Override public boolean containsThrowable(Vec3 position) { return field(false).contains(position); }
            @Override public boolean canHarm(int shooter, int target) { return target == 2; }
        });
        arm(e, 1, SPAWN, 2, 384);
        arm(e, 3, SPAWN, 1, 384);
        assertTrue(e.join(2, new Vec3(3.5, 1, .5), 180));
        for (int i = 0; i < 20; i++) {
            control(e, 1, i, 0, false, true, 0, 0);
            control(e, 3, i, 0, false, true, 0, 0);
            e.tick();
        }
        release(e, 1);
        assertEquals(1, e.snapshot(1).stats().hits());
        release(e, 3);
        e.tick();
        assertEquals(0, e.snapshot(3).stats().hits());
        assertTrue(e.projectiles().stream().anyMatch(shot -> shot.ownerId() == 3 && shot.velocity().x() < 0));

        draw(e, 1, 20);
        control(e, 1, e.currentTick(), 0, false, false, 0, 87);
        e.tick();
        assertTrue(e.projectiles().stream().anyMatch(shot -> shot.ownerId() == 1
                && shot.position().y() > 5 && !shot.embedded()));
    }

    @Test void explosiveArrowPassesThroughAnAllyAndOnlyPushesAndDamagesExposedEnemies() {
        var e = explosiveField(false);
        Vec3 ally = new Vec3(1.6, 1, .5);
        assertTrue(e.join(2, ally, 180));
        assertTrue(e.join(3, new Vec3(3.5, 1, .5), 180));
        assertTrue(e.join(4, new Vec3(4.2, 1, 2), 180));
        assertTrue(e.join(5, new Vec3(8, 1, .5), 180));
        var allyArmor = e.snapshot(2).durability();

        draw(e, 1, 20);
        release(e, 1);

        var blast = e.projectiles().get(0);
        assertEquals(Item.EXPLOSIVE_ARROW, blast.item());
        assertTrue(blast.embedded());
        assertTrue(blast.position().x() > 2.5, "The arrow passed the ally before detonating");
        assertEquals(20, e.snapshot(1).health());
        assertEquals(-1, e.snapshot(1).impulseTick());
        assertEquals(20, e.snapshot(2).health());
        assertEquals(allyArmor, e.snapshot(2).durability());
        assertEquals(-1, e.snapshot(2).hurtTick());
        assertEquals(-1, e.snapshot(2).impulseTick());
        assertTrue(e.snapshot(3).health() < e.snapshot(4).health());
        assertTrue(e.snapshot(4).health() < 20);
        assertTrue(e.snapshot(3).hitImpulse().x() > 0);
        assertTrue(e.snapshot(4).hitImpulse().z() > 0);
        assertEquals(20, e.snapshot(5).health());
        assertEquals(2, e.snapshot(1).stats().hits());
    }

    @Test void wallsStopAnExplosiveArrowAndShieldEnemiesBehindThem() {
        var e = explosiveField(true);
        assertTrue(e.join(3, new Vec3(2.3, 1, 1.8), 180));
        assertTrue(e.join(4, new Vec3(4.5, 1, .5), 180));
        draw(e, 1, 20);
        release(e, 1);

        assertTrue(e.projectiles().get(0).embedded());
        assertEquals(20, e.snapshot(4).health());
        assertEquals(-1, e.snapshot(4).impulseTick());
        assertTrue(e.snapshot(3).health() < 20);
        assertEquals(383, e.snapshot(1).bowDurability());
        assertEquals(Item.EMPTY, e.snapshot(1).inventory().get(34).item());
        for (int tick = 0; tick < CombatEngine.ARROW_BLAST_TICKS; tick++) e.tick();
        assertTrue(e.projectiles().isEmpty());
        assertEquals(Item.EMPTY, e.snapshot(1).inventory().get(34).item());
        draw(e, 1, 20);
        release(e, 1);
        assertEquals(Item.ARROW, e.projectiles().get(0).item());
        assertEquals(1, e.snapshot(1).inventory().get(35).count());
    }

    private static CombatEngine explosiveField(boolean wall) {
        var terrain = field(wall);
        var e = engine(new Arena() {
            @Override public boolean solid(int x, int y, int z) { return terrain.solid(x, y, z); }
            @Override public boolean contains(Vec3 point) { return terrain.contains(point); }
            @Override public boolean canHarm(int shooter, int target) { return target != 2; }
        });
        arm(e, 1, SPAWN, 2, 384);
        var kit = new ArrayList<>(e.snapshot(1).inventory());
        kit.set(34, new Stack(Item.EXPLOSIVE_ARROW, 1));
        assertTrue(e.setKit(1, kit));
        assertTrue(e.reset(1, SPAWN, 0));
        return e;
    }

    private static CombatEngine engine(Arena arena) {
        return new CombatEngine(CombatRules.NO_DEBUFF, arena, new Random(2) {
            @Override public double nextGaussian() { return 0; }
            @Override public int nextInt(int bound) { return 0; }
        });
    }

    private static Arena field(boolean cover) {
        return new Arena() {
            @Override public boolean solid(int x, int y, int z) {
                return y <= 0 || cover && x == 3 && y < 6 && z >= -2 && z <= 2;
            }
            @Override public boolean contains(Vec3 p) {
                return Math.abs(p.x()) < 100 && Math.abs(p.z()) < 100 && p.y() < 150 && p.y() >= 0;
            }
        };
    }

    private static void arm(CombatEngine e, int id, Vec3 feet, int arrows, int durability) {
        assertTrue(e.join(id, feet, 0));
        var kit = new ArrayList<>(CombatRules.NO_DEBUFF.openingKit());
        kit.set(0, new Stack(Item.BOW, 1, durability));
        kit.set(30, new Stack(Item.EMPTY, 0));
        if (arrows > 0) kit.set(35, new Stack(Item.ARROW, arrows));
        assertTrue(e.setKit(id, kit));
        assertTrue(e.reset(id, feet, 0));
    }

    private static void draw(CombatEngine e, int id, int ticks) {
        for (int i = 0; i < ticks; i++) {
            control(e, id, e.currentTick(), 0, false, true, 0, 0);
            e.tick();
        }
    }

    private static void release(CombatEngine e, int id) {
        control(e, id, e.currentTick(), 0, false, false, 0, 0);
        e.tick();
    }

    private static void control(CombatEngine e, int id, long sequence, float forward, boolean sprint,
                                boolean use, int slot, float pitch) {
        assertTrue(e.submit(id, new Intent(sequence, 0, pitch, forward, 0, false, sprint, false, use, slot)));
    }
}
