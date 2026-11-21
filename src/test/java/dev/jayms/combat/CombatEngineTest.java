package dev.jayms.combat;

import dev.jayms.combat.CombatEngine.Arena;
import dev.jayms.combat.CombatEngine.AttackView;
import dev.jayms.combat.CombatEngine.Effect;
import dev.jayms.combat.CombatEngine.EventType;
import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.CombatEngine.Item;
import dev.jayms.combat.CombatEngine.PoseView;
import dev.jayms.combat.CombatEngine.Stack;
import dev.jayms.combat.CombatEngine.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static dev.jayms.combat.ThrowablePhysicsTest.assertVector;
import static dev.jayms.combat.ThrowablePhysicsTest.noSpread;

class CombatEngineTest {
    private static final Vec3 ORIGIN = new Vec3(.5, 1, .5);

    private static final double NORMAL_PUSH = CombatRules.NO_DEBUFF.knockback();
    private static final double SPRINT_BONUS = CombatRules.NO_DEBUFF.sprintKnockback();
    private static final double SPRINT_PUSH = NORMAL_PUSH + SPRINT_BONUS;

    @ParameterizedTest
    @ValueSource(doubles = {0, .009})
    void nearlyCoincidentTradersGetIndependentBaseDirections(double separation) {
        var engine = new CombatEngine(CombatRules.NO_DEBUFF, field(false, true), new Random(0));
        assertTrue(engine.join(1, ORIGIN, 0));
        assertTrue(engine.join(2, ORIGIN.add(0, 0, separation), 0));
        assertTrue(input(engine, 1, 0, 90, 0, 0, false, true, false, 0));
        assertTrue(input(engine, 2, 0, -90, 0, 0, false, true, false, 0));
        engine.tick();

        Vec3 first = engine.snapshot(1).velocity(), second = engine.snapshot(2).velocity();
        assertEquals(NORMAL_PUSH, Math.hypot(first.x(), first.z()), 1e-6);
        assertEquals(NORMAL_PUSH, Math.hypot(second.x(), second.z()), 1e-6);
        // Paper's seeded, accepted samples for the first hit (fighter 2) and return hit.
        assertEquals(-.896712 * NORMAL_PUSH, second.x(), 1e-6);
        assertEquals(.442615 * NORMAL_PUSH, second.z(), 1e-6);
        assertEquals(.845456 * NORMAL_PUSH, first.x(), 1e-6);
        assertEquals(.534046 * NORMAL_PUSH, first.z(), 1e-6);
    }

    @Test void baseDirectionUsesActualOffsetAtTheSamplingThreshold() {
        var engine = new CombatEngine(CombatRules.NO_DEBUFF, field(false, true), new Random(0));
        assertTrue(engine.join(1, new Vec3(0, 1, 0), 0));
        assertTrue(engine.join(2, new Vec3(0, 1, .01), 0));
        assertTrue(input(engine, 1, 0, 0, 0, 0, false, true, false, 0));
        engine.tick();
        Vec3 launch = engine.snapshot(2).velocity();
        assertEquals(0, launch.x(), 1e-6);
        assertEquals(NORMAL_PUSH, launch.z(), 1e-6);
    }

    @Test void inventoryQuickMoveMergesAcrossSectionsWithoutDroppingOverflow() {
        CombatEngine engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2);
        var kit = new ArrayList<>(engine.kit(1));
        kit.set(1, new Stack(Item.PEARL, 62));
        kit.set(18, new Stack(Item.PEARL, 20));
        assertTrue(engine.setKit(1, kit));
        assertTrue(engine.reset(1, ORIGIN, 0));

        assertTrue(engine.quickMoveInventory(1, 18));
        var moved = engine.snapshot(1).inventory();
        assertEquals(new Stack(Item.PEARL, 64), moved.get(1));
        assertEquals(new Stack(Item.PEARL, 18), moved.get(18));
        assertEquals(kit.get(1), engine.kit(1).get(1));
        assertFalse(engine.quickMoveInventory(1, 18));
        assertFalse(engine.quickMoveInventory(1, 36));
        assertEquals(moved, engine.snapshot(1).inventory());

        kit.set(10, new Stack(Item.EMPTY, 0));
        assertTrue(engine.setKit(1, kit));
        assertTrue(engine.quickMoveKit(1, 1));
        assertEquals(new Stack(Item.PEARL, 64), engine.kit(1).get(18));
        assertEquals(new Stack(Item.PEARL, 18), engine.kit(1).get(10));
        assertEquals(new Stack(Item.EMPTY, 0), engine.kit(1).get(1));
    }

    @Test void meleeUsesAimReachOcclusionAndHurtImmunity() {
        CombatEngine engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2.5);
        assertTrue(input(engine, 1, 0, 0, 0, 0, false, true, false, 0));
        assertFalse(input(engine, 1, 0, 0, 0, 0, false, true, false, 0));
        assertFalse(input(engine, 1, 1, Float.NaN, 0, 0, false, true, false, 0));
        assertFalse(input(engine, 1, 1, 0, 0, 1.1f, false, true, false, 0));
        assertFalse(input(engine, 1, 1, 0, 0, 0, false, true, false, 36));
        engine.tick();
        assertEquals(18.5, engine.snapshot(2).health(), .00001);
        assertEquals(List.of(199, 199, 199, 199), engine.snapshot(2).durability());
        assertEquals(1, engine.snapshot(1).stats().hits());
        assertTrue(input(engine, 1, 1, 0, 0, 0, false, true, false, 0));
        engine.tick();
        assertEquals(18.5, engine.snapshot(2).health(), .00001);
        assertEquals(1, engine.snapshot(1).stats().hits());
        assertTrue(engine.drainEvents().stream().anyMatch(event -> event.type() == EventType.SWING));

        CombatEngine wall = duel(CombatRules.NO_DEBUFF, field(true, true), 2.5);
        input(wall, 1, 0, 0, 0, 0, false, true, false, 0);
        wall.tick();
        assertEquals(20, wall.snapshot(2).health());

        CombatEngine distant = duel(CombatRules.NO_DEBUFF, field(false, true), 4.5);
        input(distant, 1, 0, 0, 0, 0, false, true, false, 0);
        distant.tick();
        assertEquals(20, distant.snapshot(2).health());

        CombatEngine unaimed = duel(CombatRules.NO_DEBUFF, field(false, true), 2.5);
        input(unaimed, 1, 0, 18, 0, 0, false, true, false, 0);
        unaimed.tick();
        assertEquals(20, unaimed.snapshot(2).health());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void blockAndHeldSlotAreSampledInClickReceiptOrder(int scenario) {
        var engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2);
        boolean initiallyBlocking = scenario != 0;
        assertTrue(input(engine, 2, 0, 180, 0, 0, false, false, initiallyBlocking, 0));
        engine.tick();
        if (scenario == 4) tick(engine, 12); // The abandoned block expires at the input timeout.
        assertTrue(input(engine, 1, 0, 0, 0, 0, false, true, false, 0));
        if (scenario != 3 && scenario != 4) {
            assertTrue(input(engine, 2, 1, 180, 0, 0, false, false, scenario != 1, scenario == 2 ? 5 : 0));
        }
        engine.tick();
        assertEquals(initiallyBlocking && scenario != 4 ? 19.09 : 18.5, engine.snapshot(2).health(), .00001);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void separateClicksResolveInOrderAndOnlyARearmedSprintAddsAnotherBonus(boolean rearm) {
        var engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2);
        assertTrue(engine.join(3, new Vec3(.5, 1, 2.5), 0));
        assertTrue(input(engine, 1, 0, 180, 0, 1, true, true, false, 0)); // Miss.
        assertTrue(input(engine, 1, 1, 0, 0, 1, true, true, false, 0));
        if (rearm) assertTrue(input(engine, 1, 2, 0, 0, 0, false, false, false, 0));
        assertTrue(input(engine, 1, 3, 90, 0, 1, true, true, false, 0));
        engine.tick();
        assertEquals(2, engine.snapshot(1).stats().hits());
        assertEquals(.4208, engine.snapshot(2).velocity().y(), .00001);
        assertEquals(rearm ? .4208 : .3208, engine.snapshot(3).velocity().y(), .00001);
    }

    @Test void pendingClicksAreBoundedAndClearedAcrossControlAndActorLifetimes() {
        var engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2);
        for (int i = 0; i < 24; i++) assertTrue(input(engine, 1, i, 180, 0, 0, false, true, false, 0));
        engine.tick();
        assertEquals(16, engine.drainEvents().stream().filter(event -> event.type() == EventType.SWING).count());
        assertTrue(input(engine, 1, 24, 0, 0, 0, false, true, false, 0));
        engine.clearInput(1);
        assertTrue(input(engine, 1, 25, 0, 0, 0, false, true, false, 0));
        assertTrue(engine.reset(1, ORIGIN, 0));
        assertTrue(input(engine, 1, 26, 0, 0, 0, false, true, false, 0));
        engine.leave(2);
        assertTrue(engine.join(2, new Vec3(2, 1, .5), 180));
        engine.tick();
        assertEquals(0, engine.snapshot(1).stats().hits());
        assertEquals(1, engine.drainEvents().stream().filter(event -> event.type() == EventType.SWING).count());
    }

    @Test void laterMovementPacketDoesNotReplaceTheClickAimOrSlot() {
        var engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2);
        assertTrue(input(engine, 1, 0, 0, 0, 0, false, true, false, 0));
        assertTrue(input(engine, 1, 1, 90, 0, 0, false, false, false, 5));
        engine.tick();
        assertEquals(18.5, engine.snapshot(2).health(), .00001);
        assertEquals(90, engine.snapshot(1).yaw());
        assertEquals(5, engine.snapshot(1).slot());
    }

    @Test void aimedSwingHitsTheServerPoseThatWasShownDuringAStrafe() {
        for (boolean useHistory : List.of(false, true)) {
            var engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2);
            assertTrue(engine.reset(2, new Vec3(2.5, 1, -.85), 0));
            assertTrue(engine.submit(2, new Intent(0, 0, 0, 0, 1, false, false, false, false, 0)));
            tick(engine, 7);
            Vec3 displayed = engine.snapshot(2).feet();
            tick(engine, 2);
            float yaw = (float) Math.toDegrees(Math.atan2(displayed.z() - ORIGIN.z(), displayed.x() - ORIGIN.x()));
            var shown = useHistory ? new AttackView(2, 7.25, 7) : null;
            assertTrue(engine.submit(1, new Intent(0, yaw, 0, 0, 0, false, false, true, false, 0, shown)));
            engine.tick();
            assertEquals(useHistory ? 1 : 0, engine.snapshot(1).stats().hits());
        }
    }

    @Test void historyStillRejectsOccludedDisallowedOrExpiredTargets() {
        var blocked = duel(CombatRules.NO_DEBUFF, field(true, true), 2.5);
        var friendly = duel(CombatRules.NO_DEBUFF, field(false, false), 2.5);
        var invalid = duel(CombatRules.NO_DEBUFF, field(false, true), 2.5);
        for (var engine : List.of(blocked, friendly, invalid)) tick(engine, 10);
        for (var engine : List.of(blocked, friendly)) {
            assertTrue(engine.submit(1, new Intent(0, 0, 0, 0, 0, false, false, true, false, 0,
                    new AttackView(2, 10, 10))));
            engine.tick();
            assertEquals(0, engine.snapshot(1).stats().hits());
        }
        for (var shown : List.of(new AttackView(2, 1, 1), new AttackView(2, 13, 13), new AttackView(3, 12, 12))) {
            assertTrue(invalid.submit(1, new Intent(invalid.currentTick(), 0, 0, 0, 0, false, false, true, false, 0, shown)));
            invalid.tick();
            assertEquals(0, invalid.snapshot(1).stats().hits());
        }
        assertTrue(invalid.reset(2, new Vec3(3, 1, .5), 0));
        assertTrue(invalid.submit(1, new Intent(20, 0, 0, 0, 0, false, false, true, false, 0,
                new AttackView(2, 13, 13))));
        invalid.tick();
        assertEquals(0, invalid.snapshot(1).stats().hits());
    }

    @Test void historyReconstructsTheSamePoseWhenTheClientMissesSnapshotsDuringATurn() {
        for (boolean useGap : List.of(false, true)) {
            var engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2);
            assertTrue(engine.reset(2, new Vec3(2.5, 1, -.85), 0));
            engine.submit(2, new Intent(0, 0, 0, 0, 1, false, false, false, false, 0));
            tick(engine, 7);
            double first = engine.snapshot(2).feet().z();
            engine.tick();
            engine.submit(2, new Intent(1, 0, 0, 0, -1, false, false, false, false, 0));
            tick(engine, 2);
            double last = engine.snapshot(2).feet().z();
            double displayed = first + (last - first) / 3;
            float yaw = (float) Math.toDegrees(Math.atan2(displayed - .35 - ORIGIN.z(), 2));
            var view = new AttackView(2, new PoseView(8, 8, 8),
                    useGap ? new PoseView(7, 10, 8) : new PoseView(8, 8, 8));
            assertTrue(engine.submit(1, new Intent(0, yaw, 0, 0, 0, false, false, true, false, 0, view)));
            engine.tick();
            assertEquals(useGap ? 1 : 0, engine.snapshot(1).stats().hits());
        }
    }

    @Test void strongerSprintHitAddsOnlyTheSprintPushInsideTheHurtWindow() {
        var engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2);
        input(engine, 1, 0, 0, 0, 0, false, true, false, 5);
        engine.tick();
        assertEquals(19.68, engine.snapshot(2).health(), .00001);
        assertEquals(1, engine.snapshot(2).impulseTick());
        assertVector(new Vec3(NORMAL_PUSH, .3208, 0), engine.snapshot(2).velocity());
        input(engine, 1, 1, 0, 0, 1, true, true, false, 0);
        engine.tick();
        assertEquals(18.5, engine.snapshot(2).health(), .00001);
        assertEquals(1, engine.snapshot(2).hurtTick());
        assertEquals(2, engine.snapshot(2).impulseTick());
        double strongerLift = (.3208 - .08) * .98 + .10;
        assertVector(new Vec3(NORMAL_PUSH * .546 + SPRINT_BONUS, strongerLift, 0), engine.snapshot(2).velocity());
        assertEquals(-1, engine.snapshot(1).impulseTick());
        assertEquals(List.of(198, 198, 198, 198), engine.snapshot(2).durability());
        assertFalse(engine.snapshot(1).sprinting());
        assertEquals(2, engine.snapshot(1).stats().hits());
    }

    @Test void blockingAndArmorApplyAfterTheRawDamageComparison() {
        var engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2);
        assertTrue(engine.reset(2, new Vec3(2.5, 1, .5), 0));
        assertTrue(input(engine, 2, 0, 0, 0, 0, false, false, true, 0));
        assertTrue(input(engine, 1, 0, 0, 0, 0, false, true, false, 0));
        engine.tick();
        // Paper sword blocking also mitigates an attack from behind.
        double initialHealth = 19.09;
        assertEquals(initialHealth, engine.snapshot(2).health(), .00001);
        assertEquals(List.of(199, 199, 199, 199), engine.snapshot(2).durability());

        assertTrue(input(engine, 2, 1, 0, 0, 0, false, false, false, 0));
        assertTrue(input(engine, 1, 1, 0, 0, 0, false, true, false, 0));
        engine.tick();
        assertEquals(initialHealth, engine.snapshot(2).health(), .00001);
        assertEquals(1, engine.snapshot(1).stats().hits());

        assertTrue(engine.applyEffect(1, Effect.STRENGTH, 40));
        assertTrue(input(engine, 2, 2, 0, 0, 0, false, false, true, 0));
        assertTrue(input(engine, 1, 2, 0, 0, 0, false, true, false, 0));
        engine.tick();
        double extraRaw = CombatRules.NO_DEBUFF.swordDamage() * 1.3; // Strength I adds 130% of the raw sword hit.
        assertEquals(initialHealth - (1 + extraRaw) * .5 * .32, engine.snapshot(2).health(), .00001);
        assertEquals(List.of(198, 198, 198, 198), engine.snapshot(2).durability());
        assertEquals(1, engine.snapshot(2).hurtTick());
        assertEquals(2, engine.snapshot(1).stats().hits());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void firstReceivedLethalClickWinsRegardlessOfActorId(int first) {
        var engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2);
        int second = 3 - first;
        for (int id : List.of(first, second)) {
            assertTrue(engine.damage(0, id, 19));
            assertTrue(input(engine, id, 0, id == 1 ? 0 : 180, 0, 0, false, true, false, 0));
        }
        engine.tick();
        assertEquals(1, engine.snapshot(first).health());
        assertEquals(0, engine.snapshot(second).health());
        assertEquals(1, engine.snapshot(first).stats().hits());
        assertEquals(0, engine.snapshot(second).stats().hits());
    }

    @Test void chestplateAndResistanceMitigateAfterBlockingAndWearUsesBlockedDamage() {
        var engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2);
        assertTrue(engine.equipArmor(2, .80, List.of(0, 0, 1, 0)));
        assertTrue(engine.applyEffect(2, Effect.RESISTANCE, 20));
        assertTrue(input(engine, 2, 0, 180, 0, 0, false, false, true, 0));
        assertTrue(input(engine, 1, 0, 0, 0, 0, false, true, false, 0));
        engine.tick();
        // Blocking leaves 2.84375 raw, followed by chestplate mitigation and Resistance I.
        assertEquals(18.453, engine.snapshot(2).health(), .00001);
        assertEquals(List.of(0, 0, 0, 0), engine.snapshot(2).durability());
    }

    @Test void effectsPulseOnRemainingDurationAndPoisonParticipatesInHurtImmunity() {
        var engine = duel(CombatRules.DEBUFF, field(false, true), 2);
        tick(engine, 7);
        assertTrue(engine.damage(0, 1, 5));
        assertTrue(engine.applyEffect(1, Effect.REGENERATION, 51));
        assertTrue(engine.applyEffect(2, Effect.POISON, 26));
        engine.tick();
        assertEquals(15, engine.snapshot(1).health());
        assertEquals(20, engine.snapshot(2).health());
        assertTrue(input(engine, 1, 0, 0, 0, 0, false, true, false, 0));
        engine.tick();
        assertEquals(16, engine.snapshot(1).health());
        // One poison damage grants immunity, so the sword's remaining 3.6875 raw points reach armor.
        assertEquals(17.82, engine.snapshot(2).health(), .00001);
        assertEquals(-1, engine.snapshot(2).impulseTick());
    }

    @Test void speedAndSlownessCombineAsLevelOneAttributeMultipliers() {
        var engine = duel(CombatRules.DEBUFF, field(false, true), 6);
        assertTrue(engine.applyEffect(2, Effect.SPEED, 40));
        assertTrue(engine.applyEffect(2, Effect.SLOWNESS, 40));
        for (int id : List.of(1, 2)) assertTrue(input(engine, id, 0, 0, 0, 1, false, false, false, 0));
        engine.tick();
        assertEquals((engine.snapshot(1).feet().x() - .5) * 1.02,
                engine.snapshot(2).feet().x() - 6, .00001);
    }

    @Test void aClickThatStartsUsingAnItemCannotAddSprintKnockback() {
        var engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2);
        assertTrue(input(engine, 1, 0, 0, 0, 1, true, true, true, 3));
        engine.tick();
        assertEquals(1, engine.snapshot(1).stats().hits());
        assertEquals(NORMAL_PUSH, engine.snapshot(2).velocity().x(), .00001);
    }

    @Test void aWallScrapeReportsHorizontalCollisionEvenWhileTheFighterSlides() {
        var engine = new CombatEngine(CombatRules.NO_DEBUFF, field(true, true));
        assertTrue(engine.join(1, new Vec3(.68, 1, -.5), 70));
        assertTrue(input(engine, 1, 0, 70, 0, 1, true, false, false, 0));
        tick(engine, 3);
        var actor = engine.snapshot(1);
        assertTrue(actor.velocity().z() > .12);
        assertEquals(engine.currentTick(), actor.lastHorizontalWallTick());
        assertFalse(actor.sprinting());
        assertTrue(input(engine, 1, 1, 90, 0, 1, true, false, false, 0));
        engine.tick();
        assertEquals(engine.currentTick() - 1, engine.snapshot(1).lastHorizontalWallTick());
    }

    @Test void sprintResetRearmsBoostWhileHoldingSprintAfterAHitDoesNot() {
        for (boolean release : List.of(false, true)) {
            var engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2.2);
            input(engine, 1, 0, 0, 0, 1, true, true, false, 0);
            engine.tick();
            assertFalse(engine.snapshot(1).sprinting());
            assertEquals(SPRINT_PUSH, engine.snapshot(2).velocity().x(), .00001);
            assertEquals(.098, engine.snapshot(1).velocity().x(), .00001);

            // Move the victim back into melee range after the first boost without touching attacker input.
            tick(engine, 8);
            double targetX = engine.snapshot(1).feet().x() + 1.7;
            assertTrue(engine.reset(2, new Vec3(targetX, 1, .5), 180));
            if (release) input(engine, 1, 1, 0, 0, 1, false, false, false, 0);
            input(engine, 1, 2, 0, 0, 1, true, true, false, 0);
            engine.tick();
            assertEquals(2, engine.snapshot(1).stats().hits());
            assertEquals(2, engine.snapshot(1).stats().longestCombo());
            assertEquals(release ? SPRINT_PUSH : NORMAL_PUSH, engine.snapshot(2).velocity().x(), .00001);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void wTapsChainThreeSprintHitsWithoutRepositioning(boolean passiveDefender) {
        var engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2.0);
        if (passiveDefender) assertTrue(engine.applyEffect(1, Effect.SPEED, 50));
        long sequence = 0;
        for (int hit = 1; hit <= 3; hit++) {
            if (!passiveDefender) assertTrue(input(engine, 2, hit, 180, 0, 1, false, false, false, 0));
            assertTrue(input(engine, 1, sequence++, 0, 0, 1, true, true, false, 0));
            engine.tick();
            assertEquals(hit, engine.snapshot(1).stats().hits());
            assertEquals(1 + (hit - 1) * 13, engine.snapshot(2).hurtTick());

            assertTrue(engine.snapshot(2).velocity().x() > NORMAL_PUSH, "W release rearms sprint knockback");
            assertEquals(.4208, engine.snapshot(2).velocity().y(), .00001);
            assertTrue(input(engine, 1, sequence++, 0, 0, 0, true, false, false, 0));
            engine.tick();

            for (int betweenHits = 0; betweenHits < 11; betweenHits++) {
                double separation = engine.snapshot(2).feet().x() - engine.snapshot(1).feet().x();
                assertTrue(input(engine, 1, sequence++, 0, 0, separation > 2.3 ? 1 : 0, true, false, false, 0));
                engine.tick();
                if (betweenHits < 3) assertTrue(engine.snapshot(2).velocity().x() > 0, "Launch carries across ticks");
            }
            assertTrue(engine.snapshot(2).grounded(), "Victim lands before the next W-tap");
        }
        assertEquals(3, engine.snapshot(1).stats().longestCombo());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fullMeleeHitCoastsToTheConfiguredDistanceOnFlatGround(boolean sprint) {
        for (CombatRules rules : List.of(CombatRules.NO_DEBUFF, CombatRules.DEBUFF)) {
            for (float yaw : new float[]{0, 25, 45, 90, 180, -45}) {
                var arena = new CombatEngine(rules, field(false, true));
                Vec3 heading = CombatGeometry.direction(yaw, 0);
                Vec3 victimStart = ORIGIN.add(heading.scale(2));
                assertTrue(arena.join(1, ORIGIN, yaw));
                assertTrue(arena.join(2, victimStart, yaw + 180));
                tick(arena, 3);
                assertTrue(input(arena, 1, 0, yaw, 0, sprint ? 1 : 0, sprint, true, false, 0));
                arena.tick();
                assertEquals(1, arena.snapshot(1).stats().hits());
                tick(arena, 30);
                var victim = arena.snapshot(2);
                assertTrue(victim.grounded());
                assertEquals(0, victim.velocity().length(), .00001);
                Vec3 travel = victim.feet().minus(victimStart);
                // Paper cuts each axis below .005 blocks/tick, so headings may differ slightly.
                assertEquals(sprint ? 3 : 1, Math.hypot(travel.x(), travel.z()), .01, "yaw " + yaw);
            }
        }
    }

    @Test void knockbackMaintainsMomentumAcrossLaunchAndLanding() {
        CombatEngine engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2.5);
        input(engine, 1, 0, 0, 0, 1, true, true, false, 0);
        engine.tick();
        assertEquals(1, engine.snapshot(2).hurtTick());
        assertEquals(1, engine.snapshot(2).impulseTick());
        assertVector(new Vec3(SPRINT_PUSH * .546, (.4208 - .08) * .98, 0), engine.snapshot(2).hitImpulse());
        var launched = engine.snapshot(2);
        assertEquals(SPRINT_PUSH, launched.velocity().x(), .00001);
        assertEquals(.4208, launched.velocity().y(), .00001);
        assertFalse(launched.grounded());
        engine.tick();
        assertEquals(SPRINT_PUSH * .546, engine.snapshot(2).velocity().x(), .00001);
        assertEquals((.4208 - .08) * .98, engine.snapshot(2).velocity().y(), .00001);

        int airborneTicks = 0;
        double peak = 1;
        while (!engine.snapshot(2).grounded() && airborneTicks++ < 20) {
            peak = Math.max(peak, engine.snapshot(2).feet().y());
            engine.tick();
        }
        var landed = engine.snapshot(2);
        assertTrue(landed.grounded());
        assertEquals(1, landed.feet().y(), .00001);
        assertEquals(1.253030, peak - 1, .001);
        assertEquals(10, airborneTicks);
        assertTrue(landed.velocity().x() > .06);
        engine.tick();
        double carried = engine.snapshot(2).velocity().x();
        assertEquals(landed.velocity().x() * .91, carried, .00001);
        engine.tick();
        assertEquals(carried * .546, engine.snapshot(2).velocity().x(), .00001);
        double coast = engine.snapshot(2).velocity().x();
        engine.tick();
        assertEquals(coast * .546, engine.snapshot(2).velocity().x(), .00001);
        assertEquals(1, engine.snapshot(2).hurtTick());
    }

    @Test void hitCombinesRadialPushWithRetainedOpposingAndSidewaysVelocity() {
        var engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2.2);
        assertTrue(engine.submit(2, new Intent(0, 0, 0, -.5f, .5f, false, false, false, false, 0)));
        engine.tick();
        var victim = engine.snapshot(2);
        Vec3 offset = victim.feet().minus(ORIGIN);
        double distance = Math.hypot(offset.x(), offset.z());
        assertEquals(20, victim.health(), .00001);
        assertTrue(victim.velocity().x() < 0 && victim.velocity().z() > 0);

        assertTrue(input(engine, 2, 1, 0, 0, 0, false, false, false, 0));
        assertTrue(input(engine, 1, 0, 0, 0, 0, false, true, false, 0));
        engine.tick();
        Vec3 launch = engine.snapshot(2).velocity();
        assertEquals(offset.x() * NORMAL_PUSH / distance + victim.velocity().x() * .546 * .5, launch.x(), .00001);
        assertEquals(offset.z() * NORMAL_PUSH / distance + victim.velocity().z() * .546 * .5, launch.z(), .00001);
        assertEquals(.3208, launch.y(), .00001);
    }

    @Test void baseLiftCapsBeforeTheIndependentSprintAddition() {
        var engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2.2);
        assertTrue(engine.submit(2, new Intent(0, 180, 0, 0, 0, true, false, false, false, 0)));
        engine.tick();
        assertEquals(.42, engine.snapshot(2).velocity().y(), .00001);
        assertTrue(input(engine, 1, 0, 0, 0, 1, true, true, false, 0));
        engine.tick();
        var victim = engine.snapshot(2);
        assertEquals(2, victim.impulseTick());
        assertVector(new Vec3(SPRINT_PUSH, .46, 0), victim.velocity());
    }

    @Test void simultaneousSprintHitsComposeSlowdownAndKnockbackInReceiptOrder() {
        var engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2.2);
        assertTrue(input(engine, 1, 0, 0, 0, 1, true, true, false, 0));
        assertTrue(input(engine, 2, 0, 180, 0, 1, true, true, false, 0));
        engine.tick();
        assertEquals(18.5, engine.snapshot(1).health(), .00001);
        assertEquals(18.5, engine.snapshot(2).health(), .00001);
        assertEquals(-SPRINT_PUSH + .098, engine.snapshot(1).velocity().x(), .00001);
        assertEquals(SPRINT_PUSH * .6 - .098, engine.snapshot(2).velocity().x(), .00001);
        assertEquals(.4208, engine.snapshot(1).velocity().y(), .00001);
        assertEquals(.4208, engine.snapshot(2).velocity().y(), .00001);
    }

    @Test void hitLiftCannotChangeAnotherPlayersFallingCriticalInTheSameTick() {
        CombatEngine engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2.0);
        assertTrue(engine.submit(1, new Intent(0, 0, 0, 0, 0, true, false, false, false, 0)));
        assertTrue(engine.submit(2, new Intent(0, 180, 0, 0, 0, true, false, false, false, 0)));
        tick(engine, 7);
        double fallingSpeed = engine.snapshot(1).velocity().y();
        assertTrue(fallingSpeed < 0);
        input(engine, 1, 1, 0, 0, 0, false, true, false, 0);
        input(engine, 2, 1, 180, 0, 0, false, true, false, 0);
        engine.tick();
        assertEquals(17.75, engine.snapshot(1).health(), .00001);
        assertEquals(17.75, engine.snapshot(2).health(), .00001);
        assertEquals(8, engine.snapshot(1).hurtTick());
        assertEquals(8, engine.snapshot(2).hurtTick());
        double expectedLift = .36 + (fallingSpeed - .08) * .98 * .5;
        assertEquals(expectedLift, engine.snapshot(1).velocity().y(), .00001);
        assertEquals(expectedLift, engine.snapshot(2).velocity().y(), .00001);
    }

    @Test void aFallingSprintAttackCanStillCriticallyHit() {
        CombatEngine engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2.0);
        assertTrue(engine.submit(1, new Intent(0, 0, 0, 0, 0, true, false, false, false, 0)));
        assertTrue(engine.submit(2, new Intent(0, 180, 0, 0, 0, true, false, false, false, 0)));
        tick(engine, 7);
        assertFalse(engine.snapshot(1).grounded());
        assertTrue(input(engine, 1, 1, 0, 0, 1, true, true, false, 0));
        engine.tick();
        assertTrue(engine.snapshot(1).velocity().y() < 0);
        assertEquals(17.75, engine.snapshot(2).health(), .00001);
        assertFalse(engine.snapshot(1).sprinting());
        assertEquals(SPRINT_PUSH, engine.snapshot(2).velocity().x(), .00001);
    }

    @Test void highImpulseRespectsWallAndKeepsSidewaysAirControl() {
        Arena wall = new Arena() {
            @Override public boolean solid(int x, int y, int z) { return y <= 0 || x == 4 && y <= 4; }
            @Override public boolean contains(Vec3 feet) { return Math.abs(feet.x()) < 25; }
        };
        var base = CombatRules.NO_DEBUFF;
        var heavy = new CombatRules(false, base.reach(), base.hurtTicks(), base.swordDamage(), base.armorReduction(),
                1, 1, base.lift(), base.healing(), base.splashRadius(), base.pearlCooldown(), base.pearlDamage(),
                base.speedDuration(), base.fireResistanceDuration(), base.poisonDuration(), base.slownessDuration());
        CombatEngine engine = duel(heavy, wall, 2.5);
        input(engine, 1, 0, 0, 0, 1, true, true, false, 0);
        engine.tick();
        assertTrue(engine.submit(2, new Intent(0, 0, 0, 0, 1, false, false, false, false, 0)));
        engine.tick();
        var alongWall = engine.snapshot(2);
        assertEquals(3.70, alongWall.feet().x(), .00001);
        assertEquals(.0196, alongWall.velocity().z(), .00001);
        assertFalse(alongWall.grounded());
        tick(engine, 4);
        assertEquals(alongWall.feet().x(), engine.snapshot(2).feet().x(), .00001);
        assertTrue(engine.snapshot(2).feet().z() > alongWall.feet().z() + .2);
        assertEquals(0, engine.snapshot(2).velocity().x(), .00001);
    }

    @Test void starvationMarksActualDamageWithoutKillingOrCreatingAnAttacker() {
        var engine = new CombatEngine(CombatRules.NO_DEBUFF, field(false, true));
        assertTrue(engine.join(1, ORIGIN, 0));
        for (int i = 0; i < 16_000 && engine.snapshot(1).food() > 0; i++) {
            assertTrue(engine.submit(1, new Intent(i, 0, 0, 1, 0, true, true, false, false, 0)));
            engine.tick();
        }
        assertEquals(0, engine.snapshot(1).food());
        tick(engine, 80);
        var hurt = engine.snapshot(1);
        assertTrue(hurt.health() < 20);
        assertEquals(0, hurt.hurtTick() % 80);
        assertTrue(hurt.hurtTick() > 0 && engine.currentTick() - hurt.hurtTick() < 80);
        tick(engine, 20 * 80);
        var starving = engine.snapshot(1);
        assertEquals(1, starving.health());
        assertEquals(0, starving.killerId());
        assertTrue(engine.drainEvents().stream().noneMatch(event -> event.type() == EventType.DEATH));
        tick(engine, 80);
        assertEquals(starving.hurtTick(), engine.snapshot(1).hurtTick());
    }

    @Test void healingSplashCanHelpBothPlayersAndUsesSlotOnlyOnce() {
        CombatEngine engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2);
        assertTrue(engine.damage(0, 1, 10));
        assertTrue(engine.damage(0, 2, 10));
        assertTrue(input(engine, 1, 0, 0, -89, 0, false, false, true, 5));
        for (int tick = 1; tick <= 7; tick++) {
            assertTrue(input(engine, 1, tick, 0, -89, 0, false, false, true, 5));
            engine.tick();
        }
        assertEquals(Item.EMPTY, engine.snapshot(1).inventory().get(5).item());
        assertEquals(1, engine.snapshot(1).stats().potsUsed());
        assertEquals(30, engine.snapshot(1).stats().potsRemaining());
        assertEquals(17, engine.snapshot(1).health());
        assertTrue(engine.snapshot(2).health() > 12);
        assertTrue(engine.snapshot(1).health() > engine.snapshot(2).health());
        assertEquals(1, engine.drainEvents().stream().filter(event -> event.type() == EventType.SPLASH).count());
        assertTrue(engine.swapInventory(1, 20, 5));
        assertEquals(Item.HEALING, engine.snapshot(1).inventory().get(5).item());
        assertEquals(Item.EMPTY, engine.snapshot(1).inventory().get(20).item());
    }

    @Test void splashUsesPreviousFlightPositionAndCanReachPastCover() {
        Arena cover = new Arena() {
            @Override public boolean solid(int x, int y, int z) {
                return y <= 0 || (x == 1 && y <= 4) || (x == -2 && y == 4);
            }
            @Override public boolean contains(Vec3 feet) { return true; }
        };
        CombatEngine engine = new CombatEngine(CombatRules.NO_DEBUFF, cover, noSpread());
        var behindWall = new Vec3(2.4, 1, .66);
        var aboveSplash = new Vec3(-1.5, 5, .66);
        assertTrue(engine.join(1, ORIGIN, 0));
        assertTrue(engine.join(2, behindWall, 180));
        assertTrue(engine.join(3, aboveSplash, 0));
        assertTrue(engine.damage(0, 2, 10));
        assertTrue(engine.damage(0, 3, 10));
        assertTrue(input(engine, 1, 0, 0, 0, 0, false, false, true, 5));
        engine.tick();
        var beforeContact = engine.projectiles().get(0).position();
        assertTrue(beforeContact.x() < 1);
        engine.tick();
        var splash = engine.drainEvents().stream().filter(event -> event.type() == EventType.SPLASH)
                .findFirst().orElseThrow();
        assertVector(beforeContact, splash.position());
        assertEquals(4, splash.amount());
        assertEquals(10 + Math.round(8 * (1 - behindWall.minus(beforeContact).length() / 4)),
                engine.snapshot(2).health());
        assertTrue(aboveSplash.minus(beforeContact).length() < 4, "Height limits candidates inside the radius");
        assertEquals(10, engine.snapshot(3).health());
    }

    @Test void directPotionHitAppliesTheFullEffect() {
        CombatEngine engine = new CombatEngine(CombatRules.NO_DEBUFF, field(false, true), noSpread());
        assertTrue(engine.join(1, ORIGIN, 0));
        assertTrue(engine.join(2, new Vec3(2, 1, .5), 180));
        assertTrue(engine.damage(0, 1, 10));
        assertTrue(engine.damage(0, 2, 10));
        assertTrue(input(engine, 1, 0, 0, -10, 0, false, false, true, 5));
        tick(engine, 3);
        assertTrue(engine.snapshot(1).health() < 18);
        assertEquals(18, engine.snapshot(2).health());
    }

    @Test void splashRoundsDurationsAndDiscardsEffectsOfTwentyTicksOrLess() {
        CombatEngine engine = new CombatEngine(CombatRules.DEBUFF, field(false, true), noSpread());
        var near = new Vec3(3.8, 1, .66);
        var far = new Vec3(-2.95, 1, .66);
        assertTrue(engine.join(1, ORIGIN, 0));
        assertTrue(engine.join(2, near, 180));
        assertTrue(engine.join(3, far, 0));
        assertTrue(input(engine, 1, 0, 0, -89, 0, false, false, true, 5));
        tick(engine, 3);
        var splash = engine.drainEvents().stream().filter(event -> event.type() == EventType.SPLASH)
                .findFirst().orElseThrow().position();
        int poison = CombatRules.DEBUFF.poisonDuration();
        assertEquals((int) Math.round(poison * (1 - near.minus(splash).length() / 4)),
                engine.snapshot(2).effects().get(Effect.POISON));
        assertEquals(20, Math.round(poison * (1 - far.minus(splash).length() / 4)));
        assertFalse(engine.snapshot(3).effects().containsKey(Effect.POISON));
    }

    @Test void missedSplashDoesNotHealAndPoisonCannotKill() {
        CombatEngine noDebuff = duel(CombatRules.NO_DEBUFF, field(false, true), 4.5);
        noDebuff.damage(0, 1, 10);
        input(noDebuff, 1, 0, 180, 15, 0, false, false, true, 5);
        tick(noDebuff, 25);
        assertEquals(10, noDebuff.snapshot(1).health());
        assertTrue(noDebuff.drainEvents().stream().anyMatch(event -> event.type() == EventType.SPLASH));
        assertFalse(noDebuff.setKit(1, CombatRules.DEBUFF.openingKit()));

        CombatEngine debuff = duel(CombatRules.DEBUFF, field(false, true), 2);
        debuff.damage(0, 2, 18.5);
        input(debuff, 1, 0, 0, -10, 0, false, false, true, 5);
        tick(debuff, 7);
        assertTrue(debuff.snapshot(2).effects().containsKey(Effect.POISON));
        int poisonDuration = debuff.snapshot(2).effects().get(Effect.POISON);
        tick(debuff, poisonDuration % 25 + 1);
        assertEquals(.5, debuff.snapshot(2).health(), .0001);
        long poisonHit = debuff.currentTick();
        assertEquals(poisonHit, debuff.snapshot(2).hurtTick());
        tick(debuff, 25);
        assertEquals(poisonHit, debuff.snapshot(2).hurtTick());
        assertEquals(0, debuff.snapshot(2).killerId());

        CombatEngine team = duel(CombatRules.DEBUFF, field(false, false), 2);
        input(team, 1, 0, 0, -10, 0, false, false, true, 5);
        tick(team, 7);
        assertFalse(team.snapshot(2).effects().containsKey(Effect.POISON));
        assertFalse(team.damage(1, 2, 3));
    }

    @Test void drinksRequireContinuousUseAndSpeedExpires() {
        CombatEngine engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2.5);
        for (int tick = 0; tick < 12; tick++) {
            input(engine, 1, tick, 0, 0, 0, false, false, true, 3);
            engine.tick();
        }
        input(engine, 1, 12, 0, 0, 0, false, false, false, 3);
        engine.tick();
        assertEquals(Item.SPEED, engine.snapshot(1).inventory().get(3).item());
        for (int tick = 13; tick < 45; tick++) {
            input(engine, 1, tick, 0, 0, 0, false, false, true, 3);
            engine.tick();
        }
        assertEquals(CombatRules.NO_DEBUFF.speedDuration(), engine.snapshot(1).effects().get(Effect.SPEED));
        assertEquals(Item.EMPTY, engine.snapshot(1).inventory().get(3).item());
        tick(engine, CombatRules.NO_DEBUFF.speedDuration() - 1);
        assertEquals(1, engine.snapshot(1).effects().get(Effect.SPEED));
        engine.tick();
        assertFalse(engine.snapshot(1).effects().containsKey(Effect.SPEED));
        assertTrue(engine.applyEffect(1, Effect.FIRE_RESISTANCE, 2));
        assertFalse(engine.burn(1, 3));
        tick(engine, 2);
        assertTrue(engine.burn(1, 3));
        assertEquals(17, engine.snapshot(1).health());
    }

    @Test void pearlsConsumeOnThrowAndStayOnTheNearSideOfCover() {
        CombatEngine engine = new CombatEngine(CombatRules.NO_DEBUFF, field(true, true), noSpread());
        engine.join(1, ORIGIN, 0);
        input(engine, 1, 0, 0, -10, 0, false, false, true, 1);
        engine.tick();
        assertEquals(15, engine.snapshot(1).inventory().get(1).count());
        assertEquals(320, engine.snapshot(1).pearlCooldownTicks());
        assertEquals(15, engine.snapshot(1).health());
        assertEquals(1, engine.snapshot(1).hurtTick());
        assertEquals(1, engine.snapshot(1).motionEpoch());
        assertEquals(new Vec3(0, 0, 0), engine.snapshot(1).velocity());
        assertVector(new Vec3(.5, 2.52, .66), engine.snapshot(1).feet());
        assertFalse(engine.snapshot(1).grounded());
        assertTrue(engine.drainEvents().stream().anyMatch(event -> event.type() == EventType.TELEPORT));
        input(engine, 1, 1, 0, -10, 0, false, false, false, 1);
        engine.tick();
        input(engine, 1, 2, 0, -10, 0, false, false, true, 1);
        engine.tick();
        assertEquals(15, engine.snapshot(1).inventory().get(1).count());
        assertEquals(318, engine.snapshot(1).pearlCooldownTicks());
    }

    @Test void aPearlTeleportsWithoutChargingAWeakerHitInsideTheHurtWindow() {
        Arena wall = new Arena() {
            @Override public boolean solid(int x, int y, int z) { return y <= 0 || x == 2 && y <= 4; }
            @Override public boolean contains(Vec3 feet) { return true; }
        };
        CombatEngine engine = new CombatEngine(CombatRules.NO_DEBUFF, wall, noSpread());
        assertTrue(engine.join(1, ORIGIN, 0));
        assertTrue(engine.join(2, new Vec3(1.5, 1, .5), 0));
        assertTrue(engine.applyEffect(1, Effect.STRENGTH, 20));
        assertTrue(input(engine, 1, 0, 0, 0, 0, false, true, false, 0));
        assertTrue(input(engine, 2, 0, 0, 0, 0, false, false, true, 1));
        engine.tick();

        var landed = engine.snapshot(2);
        assertEquals(16.55, landed.health(), .00001);
        assertEquals(1, landed.motionEpoch());
        assertEquals(15, landed.inventory().get(1).count());
        assertEquals(-1, landed.impulseTick());
        assertVector(new Vec3(0, 0, 0), landed.hitImpulse());
        assertTrue(engine.drainEvents().stream().anyMatch(event -> event.type() == EventType.TELEPORT));
    }

    @Test void fallsRoundUpWithoutSendingACombatImpulse() {
        var engine = new CombatEngine(CombatRules.NO_DEBUFF, field(false, true));
        assertTrue(engine.join(1, new Vec3(.5, 4.6, .5), 0));
        for (int i = 0; i < 20 && !engine.snapshot(1).grounded(); i++) engine.tick();

        var landed = engine.snapshot(1);
        assertTrue(landed.grounded());
        assertEquals(19, landed.health());
        assertEquals(engine.currentTick(), landed.hurtTick());
        assertEquals(-1, landed.impulseTick());
        assertVector(new Vec3(0, 0, 0), landed.hitImpulse());
        assertEquals(List.of(200, 200, 200, 200), landed.durability());
    }

    @Test void aWeakerFallDoesNotDamageOrRefreshHurtAfterAnAirborneSwordHit() {
        var engine = new CombatEngine(CombatRules.NO_DEBUFF, field(false, true));
        assertTrue(engine.join(1, new Vec3(.5, 5.1, .5), 0));
        assertTrue(engine.join(2, new Vec3(2, 5.1, .5), 180));
        tick(engine, 8);
        assertTrue(input(engine, 1, 0, 0, 0, 0, false, true, false, 0));
        engine.tick();
        assertEquals(17.75, engine.snapshot(2).health(), .00001);
        for (int i = 0; i < 9 && !engine.snapshot(2).grounded(); i++) engine.tick();

        var landed = engine.snapshot(2);
        assertTrue(landed.grounded());
        assertEquals(17.75, landed.health(), .00001);
        assertEquals(9, landed.hurtTick());
        assertEquals(9, landed.impulseTick());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void pearlInterceptedByAPlayerReactsOnlyWhenHarmIsAllowed(boolean hostile) {
        CombatEngine engine = new CombatEngine(CombatRules.NO_DEBUFF, field(false, hostile), noSpread());
        assertTrue(engine.join(1, ORIGIN, 0));
        assertTrue(engine.join(2, new Vec3(3, 1, .5), 180));
        assertTrue(input(engine, 1, 0, 0, 0, 0, false, false, true, 1));
        engine.tick();
        Vec3 beforeContact = engine.projectiles().get(0).position();
        engine.tick();
        assertVector(beforeContact, engine.snapshot(1).feet());
        assertEquals(15, engine.snapshot(1).health());
        assertEquals(20, engine.snapshot(2).health());
        assertEquals(hostile ? engine.currentTick() : -1, engine.snapshot(2).hurtTick());
        assertEquals(0, engine.snapshot(1).stats().hits());
        assertEquals(hostile, engine.drainEvents().stream().anyMatch(event -> event.type() == EventType.HIT
                && event.item() == Item.PEARL && event.targetId() == 2 && event.amount() == 0));
        assertTrue(engine.projectiles().isEmpty());
        engine.tick();
        assertEquals(hostile ? .4 : 0, engine.snapshot(2).velocity().x(), .00001);
    }

    @Test void pearlOutsideBoundsFailsAndResetPreservesSequenceAndKitLayout() {
        Arena compact = new Arena() {
            @Override public boolean solid(int x, int y, int z) { return y <= 0; }
            @Override public boolean contains(Vec3 feet) { return feet.x() >= 0 && feet.x() < 1 && feet.y() >= 1; }
        };
        CombatEngine engine = new CombatEngine(CombatRules.NO_DEBUFF, compact);
        engine.join(1, ORIGIN, 0);
        input(engine, 1, 0, 0, 0, 0, false, false, true, 1);
        engine.tick();
        assertTrue(engine.drainEvents().stream().anyMatch(event -> event.type() == EventType.REJECTED_PEARL));
        assertEquals(ORIGIN, engine.snapshot(1).feet());
        assertEquals(20, engine.snapshot(1).health());
        assertEquals(0, engine.snapshot(1).motionEpoch());
        assertEquals(-1, engine.snapshot(1).hurtTick());
        assertTrue(engine.swapKit(1, 0, 8));
        assertEquals(Item.SWORD, engine.snapshot(1).inventory().get(0).item());
        assertTrue(engine.reset(1, ORIGIN, 0));
        assertEquals(Item.SWORD, engine.snapshot(1).inventory().get(8).item());
        assertEquals(16, engine.snapshot(1).inventory().get(1).count());
        assertEquals(0, engine.snapshot(1).pearlCooldownTicks());
        assertEquals(1, engine.snapshot(1).motionEpoch());
        assertEquals(-1, engine.snapshot(1).hurtTick());
        assertFalse(input(engine, 1, 0, 0, 0, 0, false, false, true, 1));
    }

    @Test void bowDrawSpendsArrowAndProjectileCannotPassThroughCover() {
        for (boolean cover : List.of(false, true)) {
            CombatEngine engine = duel(CombatRules.NO_DEBUFF, field(cover, true), 7.5);
            List<Stack> kit = new ArrayList<>(CombatRules.NO_DEBUFF.openingKit());
            kit.set(0, new Stack(Item.BOW, 1));
            kit.set(35, new Stack(Item.ARROW, 5));
            assertTrue(engine.setKit(1, kit));
            assertTrue(engine.reset(1, ORIGIN, 0));
            for (int tick = 0; tick < 22; tick++) {
                input(engine, 1, tick, 0, 0, 0, false, false, true, 0);
                engine.tick();
            }
            input(engine, 1, 22, 0, 0, 0, false, false, false, 0);
            tick(engine, 10);
            assertEquals(4, engine.snapshot(1).inventory().get(35).count());
            if (cover) assertEquals(20, engine.snapshot(2).health());
            else {
                assertTrue(engine.snapshot(2).health() < 19);
                assertEquals(.4, engine.snapshot(2).hitImpulse().x(), .00001);
            }
        }
    }

    @Test void movingPlayersCanLeadHealingAndTagPoliciesBlockMovementAndPearls() {
        CombatEngine engine = duel(CombatRules.NO_DEBUFF, field(false, true), 12);
        engine.damage(0, 1, 10);
        for (int i = 0; i < 8; i++) {
            input(engine, 1, i, 0, -53, 1, true, false, i == 4, 5);
            engine.tick();
        }
        assertTrue(engine.snapshot(1).feet().x() > 2);
        assertTrue(engine.snapshot(1).health() > 16);
        assertTrue(engine.drainEvents().stream().anyMatch(event -> event.type() == EventType.SPLASH));

        Arena protection = new Arena() {
            @Override public boolean solid(int x, int y, int z) { return y <= 0; }
            @Override public boolean contains(Vec3 feet) { return Math.abs(feet.x()) < 30; }
            @Override public boolean canEnter(int playerId, Vec3 feet) { return feet.x() < 2; }
        };
        CombatEngine tagged = new CombatEngine(CombatRules.NO_DEBUFF, protection);
        tagged.join(1, ORIGIN, 0);
        input(tagged, 1, 0, 0, 0, 1, true, false, true, 1);
        tick(tagged, 16);
        assertTrue(tagged.snapshot(1).feet().x() < 2);
        assertTrue(tagged.drainEvents().stream().anyMatch(event -> event.type() == EventType.REJECTED_PEARL));
    }

    @Test void detachingInputCancelsUseAndFiniteGrantsRequireSpace() {
        CombatEngine engine = duel(CombatRules.NO_DEBUFF, field(false, true), 7);
        assertFalse(engine.grant(1, new Stack(Item.HEALING, 1)));
        assertTrue(engine.grant(1, new Stack(Item.PEARL, 3)));
        assertEquals(19, engine.snapshot(1).inventory().get(1).count());
        assertFalse(engine.grant(1, new Stack(Item.PEARL, 64)));
        input(engine, 1, 0, 0, -89, 0, false, false, true, 5);
        engine.clearInput(1);
        engine.tick();
        assertEquals(Item.HEALING, engine.snapshot(1).inventory().get(5).item());
        input(engine, 1, 1, 0, -89, 0, false, false, true, 5);
        engine.tick();
        assertTrue(engine.grant(1, new Stack(Item.HEALING, 1)));
        assertEquals(Item.HEALING, engine.snapshot(1).inventory().get(5).item());
    }

    private static CombatEngine duel(CombatRules rules, Arena field, double farX) {
        CombatEngine engine = new CombatEngine(rules, field);
        assertTrue(engine.join(1, ORIGIN, 0));
        assertTrue(engine.join(2, new Vec3(farX, 1, .5), 180));
        return engine;
    }

    private static Arena field(boolean wall, boolean hostile) {
        return new Arena() {
            @Override public boolean solid(int x, int y, int z) {
                return y <= 0 || (wall && x == 1 && y <= 4 && z >= -2 && z <= 2);
            }
            @Override public boolean contains(Vec3 feet) {
                return Math.abs(feet.x()) < 30 && Math.abs(feet.z()) < 30 && feet.y() < 20;
            }
            @Override public boolean canHarm(int attackerId, int targetId) { return hostile; }
        };
    }

    private static void tick(CombatEngine engine, int count) {
        for (int tick = 0; tick < count; tick++) engine.tick();
    }

    private static boolean input(CombatEngine engine, int player, long sequence, float yaw, float pitch,
                                 float forward, boolean sprint, boolean attack, boolean use, int slot) {
        return engine.submit(player, new Intent(sequence, yaw, pitch, forward, 0, false, sprint, attack, use, slot));
    }
}
