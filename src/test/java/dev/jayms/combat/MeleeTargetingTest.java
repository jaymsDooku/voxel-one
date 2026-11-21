package dev.jayms.combat;

import dev.jayms.combat.CombatEngine.Arena;
import dev.jayms.combat.CombatEngine.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MeleeTargetingTest {
    private static final Vec3 FEET = new Vec3(0, 0, 0);
    private static final Vec3 EYE = new Vec3(0, CombatGeometry.EYE_HEIGHT, 0);
    private static final Vec3 RAY = new Vec3(3, 0, 0);

    @Test
    void survivalPickUsesThreeBlocksToTheExpandedFace() {
        assertEquals(3, CombatRules.NO_DEBUFF.reach());
        assertEquals(3, CombatRules.DEBUFF.reach());
        assertTrue(Double.isFinite(CombatGeometry.firstMeleeBody(EYE, RAY, new Vec3(3.399, 0, .399))));
        assertFalse(Double.isFinite(CombatGeometry.firstMeleeBody(EYE, RAY, new Vec3(3.401, 0, 0))));
        assertFalse(Double.isFinite(CombatGeometry.firstMeleeBody(EYE, RAY, new Vec3(2, 0, .401))));
        assertFalse(Double.isFinite(CombatGeometry.firstBody(EYE, RAY, new Vec3(2, 0, .399))));
    }

    @Test
    void verticalMarginAndOverlappingEyeUseTheSelectionBox() {
        Vec3 target = new Vec3(2, 0, 0);
        for (double height : new double[]{-.099, 1.899}) {
            assertTrue(Double.isFinite(CombatGeometry.firstMeleeBody(new Vec3(0, height, 0), RAY, target)));
        }
        assertEquals(0, CombatGeometry.firstMeleeBody(EYE, RAY, FEET));
    }

    @Test
    void anUpwardSwingMeasuresThreeBlocksAlongTheEyeRay() {
        Vec3 ray = CombatGeometry.direction(0, 30).scale(3);
        Vec3 limit = EYE.add(ray).add(0, .1, 0);
        assertTrue(Double.isFinite(CombatGeometry.firstMeleeBody(EYE, ray, limit.add(0, -.0001, 0))));
        assertFalse(Double.isFinite(CombatGeometry.firstMeleeBody(EYE, ray, limit.add(0, .0001, 0))));
    }

    @Test
    void liveAndDisplayedSwingsEnforceThreeBlocksAlongTheDiagonalAndStopAtCover() {
        for (boolean shown : new boolean[]{false, true}) {
            for (CombatRules kit : new CombatRules[]{CombatRules.NO_DEBUFF, CombatRules.DEBUFF}) {
                // Both leading faces intersect a 45-degree ray at the same distance.
                double edge = 3 / Math.sqrt(2) + .4;
                assertEquals(1, swingHits(kit, shown, false, 45, new Vec3(edge - .0001, 0, edge - .0001)));
                assertEquals(0, swingHits(kit, shown, false, 45, new Vec3(edge + .0001, 0, edge + .0001)));
                assertEquals(0, swingHits(kit, shown, true, 0, new Vec3(2.5, 0, 0)));
            }
        }
    }

    private static int swingHits(CombatRules rules, boolean shown, boolean wall, float yaw, Vec3 target) {
        var engine = new CombatEngine(rules, arena(wall));
        assertTrue(engine.join(1, FEET, yaw));
        assertTrue(engine.join(2, target, 0));
        engine.tick();
        long tick = engine.currentTick();
        var view = shown ? new CombatEngine.AttackView(2, tick, tick) : null;
        assertTrue(engine.submit(1, new CombatEngine.Intent(1, yaw, 0, 0, 0, false, false, true, false, 0, view)));
        engine.tick();
        return engine.snapshot(1).stats().hits();
    }

    @Test
    void packetAcceptanceUsesCurrentThreeDimensionalDistanceAndEyeVisibility() {
        Arena clear = arena(false), wall = arena(true);
        assertTrue(CombatGeometry.withinMeleePacketRange(clear, FEET, new Vec3(5.999, 0, 0)));
        assertFalse(CombatGeometry.withinMeleePacketRange(clear, FEET, new Vec3(6, 0, 0)));
        assertFalse(CombatGeometry.withinMeleePacketRange(clear, FEET, new Vec3(5, 4, 0)));
        assertTrue(CombatGeometry.withinMeleePacketRange(wall, FEET, new Vec3(2.999, 0, 0)));
        assertFalse(CombatGeometry.withinMeleePacketRange(wall, FEET, new Vec3(3, 0, 0)));
        assertTrue(CombatGeometry.firstBlock(wall, EYE, RAY)
                < CombatGeometry.firstMeleeBody(EYE, RAY, new Vec3(2.5, 0, 0)));
    }

    @Test
    void historicalEdgePickCanLandAfterTheVisibleTargetSprintsAway() {
        CombatEngine engine = new CombatEngine(CombatRules.NO_DEBUFF, arena(false));
        assertTrue(engine.join(1, FEET, 0));
        assertTrue(engine.join(2, new Vec3(3.39, 0, .39), 0));
        engine.tick();
        long shown = engine.currentTick();
        assertTrue(engine.submit(2, new CombatEngine.Intent(0, 0, 0, 1, 0, false, true, false, false, 0)));
        for (int i = 0; i < 5; i++) engine.tick();
        double currentDistance = engine.snapshot(2).feet().minus(FEET).length();
        assertTrue(currentDistance > 4.05 && currentDistance < 6, "Current distance " + currentDistance);
        attackShown(engine, 2, shown);
        assertEquals(1, engine.snapshot(1).stats().hits());
        assertTrue(engine.snapshot(2).health() < 20);
    }

    @Test
    void historicalClaimMustIdentifyTheNearestIntersectedFighter() {
        CombatEngine engine = new CombatEngine(CombatRules.NO_DEBUFF, arena(false));
        assertTrue(engine.join(1, FEET, 0));
        assertTrue(engine.join(2, new Vec3(3.35, 0, 0), 0));
        assertTrue(engine.join(3, new Vec3(2, 0, .39), 0));
        engine.tick();
        long shown = engine.currentTick();
        attackShown(engine, 2, shown);
        assertEquals(0, engine.snapshot(1).stats().hits());
        attackShown(engine, 3, shown);
        assertTrue(engine.snapshot(3).health() < 20);
        assertEquals(20, engine.snapshot(2).health());
    }

    private static void attackShown(CombatEngine engine, int target, long tick) {
        var view = new CombatEngine.AttackView(target, tick, tick);
        assertTrue(engine.submit(1, new CombatEngine.Intent(engine.currentTick(), 0, 0, 0, 0,
                false, false, true, false, 0, view)));
        engine.tick();
    }

    private static Arena arena(boolean wall) {
        return new Arena() {
            public boolean solid(int x, int y, int z) { return y < 0 || wall && x == 1 && y == 1 && z == 0; }
            public boolean contains(Vec3 feet) { return true; }
        };
    }
}
