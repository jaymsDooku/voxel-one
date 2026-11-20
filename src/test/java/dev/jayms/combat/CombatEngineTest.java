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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CombatEngineTest {
    private static final Vec3 ORIGIN = new Vec3(.5, 1, .5);

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
        assertEquals(17.44, engine.snapshot(2).health(), .00001);
        assertEquals(List.of(198, 198, 198, 198), engine.snapshot(2).durability());
        assertEquals(1, engine.snapshot(1).stats().hits());
        assertTrue(input(engine, 1, 1, 0, 0, 0, false, true, false, 0));
        engine.tick();
        assertEquals(17.44, engine.snapshot(2).health(), .00001);
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
        input(unaimed, 1, 0, 12, 0, 0, false, true, false, 0);
        unaimed.tick();
        assertEquals(20, unaimed.snapshot(2).health());
    }

    @Test void laterMovementPacketDoesNotReplaceTheClickAimOrSlot() {
        var engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2);
        assertTrue(input(engine, 1, 0, 0, 0, 0, false, true, false, 0));
        assertTrue(input(engine, 1, 1, 90, 0, 0, false, false, false, 5));
        engine.tick();
        assertEquals(17.44, engine.snapshot(2).health(), .00001);
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
            engine.tick();
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
            float yaw = (float) Math.toDegrees(Math.atan2(displayed - .25 - ORIGIN.z(), 2));
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
        input(engine, 1, 1, 0, 0, 1, true, true, false, 0);
        engine.tick();
        assertEquals(17.44, engine.snapshot(2).health(), .00001);
        assertFalse(engine.snapshot(1).sprinting());
        engine.tick();
        assertEquals(.35 * .68 + .25, engine.snapshot(2).velocity().x(), .00001);
        assertEquals(.128184, engine.snapshot(2).velocity().y(), .00001);
        assertEquals(2, engine.snapshot(1).stats().hits());
    }

    @Test void aClickThatStartsUsingAnItemCannotAddSprintKnockback() {
        var engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2);
        assertTrue(input(engine, 1, 0, 0, 0, 1, true, true, true, 3));
        engine.tick();
        assertEquals(1, engine.snapshot(1).stats().hits());
        engine.tick();
        assertEquals(.35, engine.snapshot(2).velocity().x(), .00001);
    }

    @Test void sprintResetRearmsBoostWhileHoldingSprintAfterAHitDoesNot() {
        for (boolean release : List.of(false, true)) {
            var engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2.2);
            input(engine, 1, 0, 0, 0, 1, true, true, false, 0);
            engine.tick();
            assertFalse(engine.snapshot(1).sprinting());
            engine.tick();
            assertEquals(.6, engine.snapshot(2).velocity().x(), .00001);
            assertEquals(.1274 + .1274 * .546 * .6, engine.snapshot(1).velocity().x(), .00001);

            // Move the victim back into melee range after the first boost without touching attacker input.
            tick(engine, 8);
            double targetX = engine.snapshot(1).feet().x() + 1.7;
            assertTrue(engine.reset(2, new Vec3(targetX, 1, .5), 180));
            if (release) input(engine, 1, 1, 0, 0, 1, false, false, false, 0);
            input(engine, 1, 2, 0, 0, 1, true, true, false, 0);
            engine.tick();
            engine.tick();
            assertEquals(2, engine.snapshot(1).stats().hits());
            assertEquals(2, engine.snapshot(1).stats().longestCombo());
            assertEquals(release ? .6 : .35, engine.snapshot(2).velocity().x(), .00001);
        }
    }

    @Test void wTapsChainThreeSprintHitsAtTheHurtWindowWithoutRepositioning() {
        var engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2.0);
        long sequence = 0;
        for (int hit = 1; hit <= 3; hit++) {
            assertTrue(input(engine, 1, sequence++, 0, 0, 1, true, true, false, 0));
            engine.tick();
            assertEquals(hit, engine.snapshot(1).stats().hits());
            assertEquals(1 + (hit - 1) * 10, engine.snapshot(2).hurtTick());

            assertTrue(input(engine, 1, sequence++, 0, 0, 0, true, false, false, 0));
            engine.tick();
            assertTrue(engine.snapshot(2).velocity().x() >= .59, "W release rearms sprint knockback");
            assertEquals(.2108, engine.snapshot(2).velocity().y(), .00001);

            assertTrue(input(engine, 1, sequence++, 0, 0, 1, true, false, false, 0));
            for (int betweenHits = 0; betweenHits < 8; betweenHits++) {
                engine.tick();
                assertTrue(engine.snapshot(2).velocity().x() > .07, "Victim keeps moving between hits");
            }
            assertTrue(engine.snapshot(2).grounded(), "Victim lands before the next hit window");
            assertTrue(engine.snapshot(2).velocity().x() > .14, "Horizontal movement carries past landing");
        }
        assertEquals(3, engine.snapshot(1).stats().longestCombo());
    }

    @Test void knockbackMaintainsMomentumAcrossLaunchAndLanding() {
        CombatEngine engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2.5);
        input(engine, 1, 0, 0, 0, 1, true, true, false, 0);
        engine.tick();
        assertEquals(0, engine.snapshot(2).velocity().x());
        assertEquals(1, engine.snapshot(2).hurtTick());

        engine.tick();
        var launched = engine.snapshot(2);
        assertEquals(.6, launched.velocity().x(), .00001);
        assertEquals(.2108, launched.velocity().y(), .00001);
        assertFalse(launched.grounded());
        engine.tick();
        assertEquals(.408, engine.snapshot(2).velocity().x(), .00001);
        assertEquals(.128184, engine.snapshot(2).velocity().y(), .00001);

        int airborneTicks = 0;
        double peak = 1;
        while (!engine.snapshot(2).grounded() && airborneTicks++ < 20) {
            peak = Math.max(peak, engine.snapshot(2).feet().y());
            engine.tick();
        }
        var landed = engine.snapshot(2);
        assertTrue(landed.grounded());
        assertEquals(1, landed.feet().y(), .00001);
        assertEquals(.38620432, peak - 1, .001);
        assertTrue(airborneTicks < 7);
        assertTrue(landed.velocity().x() > .06);
        engine.tick();
        double carried = engine.snapshot(2).velocity().x();
        assertEquals(landed.velocity().x() * .91, carried, .00001);
        engine.tick();
        assertEquals(carried * .68, engine.snapshot(2).velocity().x(), .00001);
        double coast = engine.snapshot(2).velocity().x();
        engine.tick();
        assertEquals(coast * .546, engine.snapshot(2).velocity().x(), .00001);
        assertEquals(1, engine.snapshot(2).hurtTick());
    }

    @Test void hitCombinesRadialPushWithRetainedOpposingAndSidewaysVelocity() {
        var engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2.2);
        assertTrue(engine.submit(2, new Intent(0, 0, 0, -.5f, .5f, false, false, false, false, 0)));
        assertTrue(input(engine, 1, 0, 0, 0, 0, false, true, false, 0));
        engine.tick();
        var victim = engine.snapshot(2);
        Vec3 offset = victim.feet().minus(ORIGIN);
        double distance = Math.hypot(offset.x(), offset.z());
        assertEquals(17.44, victim.health(), .00001);
        assertTrue(victim.velocity().x() < 0 && victim.velocity().z() > 0);

        assertTrue(input(engine, 2, 1, 0, 0, 0, false, false, false, 0));
        engine.tick();
        Vec3 launch = engine.snapshot(2).velocity();
        assertEquals(offset.x() * .35 / distance + victim.velocity().x() * .546 * .5, launch.x(), .00001);
        assertEquals(offset.z() * .35 / distance + victim.velocity().z() * .546 * .5, launch.z(), .00001);
        assertEquals(.2108, launch.y(), .00001);
    }

    @Test void simultaneousSprintHitsApplySelfSlowdownBeforeIncomingKnockback() {
        var engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2.2);
        assertTrue(input(engine, 1, 0, 0, 0, 1, true, true, false, 0));
        assertTrue(input(engine, 2, 0, 180, 0, 1, true, true, false, 0));
        engine.tick();
        assertEquals(17.44, engine.snapshot(1).health(), .00001);
        assertEquals(17.44, engine.snapshot(2).health(), .00001);
        assertTrue(input(engine, 1, 1, 0, 0, 0, false, false, false, 0));
        assertTrue(input(engine, 2, 1, 180, 0, 0, false, false, false, 0));
        engine.tick();

        double push = .6 - .1274 * .546 * .6 * .5;
        assertEquals(-push, engine.snapshot(1).velocity().x(), .00001);
        assertEquals(push, engine.snapshot(2).velocity().x(), .00001);
        assertEquals(.2108, engine.snapshot(1).velocity().y(), .00001);
        assertEquals(.2108, engine.snapshot(2).velocity().y(), .00001);
    }

    @Test void hitLiftCannotChangeAnotherPlayersFallingCriticalInTheSameTick() {
        CombatEngine engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2.0);
        assertTrue(engine.submit(1, new Intent(0, 0, 0, 0, 0, true, false, false, false, 0)));
        assertTrue(engine.submit(2, new Intent(0, 180, 0, 0, 0, true, false, false, false, 0)));
        tick(engine, 6);
        input(engine, 1, 1, 0, 0, 0, false, true, false, 0);
        input(engine, 2, 1, 180, 0, 0, false, true, false, 0);
        engine.tick();
        assertEquals(16.16, engine.snapshot(1).health(), .00001);
        assertEquals(16.16, engine.snapshot(2).health(), .00001);
        assertEquals(7, engine.snapshot(1).hurtTick());
        assertEquals(7, engine.snapshot(2).hurtTick());
        assertTrue(engine.snapshot(1).velocity().y() < 0);
        assertTrue(engine.snapshot(2).velocity().y() < 0);
        double expectedLift = .25 + (engine.snapshot(1).velocity().y() - .08) * .98 * .5;
        engine.tick();
        assertEquals(expectedLift, engine.snapshot(1).velocity().y(), .00001);
        assertEquals(expectedLift, engine.snapshot(2).velocity().y(), .00001);
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
        assertEquals(4 - .31, alongWall.feet().x(), .00001);
        assertEquals(.098, alongWall.velocity().z(), .00001);
        assertFalse(alongWall.grounded());
        tick(engine, 4);
        assertEquals(alongWall.feet().x(), engine.snapshot(2).feet().x(), .00001);
        assertTrue(engine.snapshot(2).feet().z() > alongWall.feet().z() + .3);
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
        assertTrue(engine.snapshot(1).health() > 17.5);
        assertTrue(engine.snapshot(2).health() > 12);
        assertTrue(engine.snapshot(1).health() > engine.snapshot(2).health());
        assertEquals(1, engine.drainEvents().stream().filter(event -> event.type() == EventType.SPLASH).count());
        assertTrue(engine.swapInventory(1, 20, 5));
        assertEquals(Item.HEALING, engine.snapshot(1).inventory().get(5).item());
        assertEquals(Item.EMPTY, engine.snapshot(1).inventory().get(20).item());
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
        tick(debuff, 20);
        assertEquals(1, debuff.snapshot(2).health(), .0001);
        assertEquals(25, debuff.snapshot(2).hurtTick());
        tick(debuff, 23);
        assertEquals(25, debuff.snapshot(2).hurtTick());
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
        CombatEngine engine = new CombatEngine(CombatRules.NO_DEBUFF, field(true, true));
        engine.join(1, ORIGIN, 0);
        input(engine, 1, 0, 0, -10, 0, false, false, true, 1);
        engine.tick();
        assertEquals(15, engine.snapshot(1).inventory().get(1).count());
        assertEquals(320, engine.snapshot(1).pearlCooldownTicks());
        assertEquals(18, engine.snapshot(1).health());
        assertEquals(1, engine.snapshot(1).hurtTick());
        assertEquals(1, engine.snapshot(1).motionEpoch());
        assertEquals(new Vec3(0, 0, 0), engine.snapshot(1).velocity());
        assertTrue(engine.snapshot(1).feet().x() > .5);
        assertTrue(engine.snapshot(1).feet().x() < 1);
        assertTrue(engine.drainEvents().stream().anyMatch(event -> event.type() == EventType.TELEPORT));
        input(engine, 1, 1, 0, -10, 0, false, false, false, 1);
        engine.tick();
        input(engine, 1, 2, 0, -10, 0, false, false, true, 1);
        engine.tick();
        assertEquals(15, engine.snapshot(1).inventory().get(1).count());
        assertEquals(318, engine.snapshot(1).pearlCooldownTicks());
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
            else assertTrue(engine.snapshot(2).health() < 19);
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
