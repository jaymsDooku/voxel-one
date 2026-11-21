package dev.jayms.combat.practice;

import dev.jayms.combat.CombatEngine;
import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.CombatEngine.Vec3;
import dev.jayms.combat.CombatGeometry;
import dev.jayms.combat.CombatRules;
import dev.jayms.combat.arena.BasaltCourtyard;
import dev.jayms.player.PlayerDimensions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SparringPartnerTest {
    @Test void rejectsAnOverlapBehindTheBotEvenIfTheExpandedHitboxContainsItsEyes() {
        var eye = new Vec3(0, 73 + PlayerDimensions.EYE_HEIGHT, 0);
        var target = new Vec3(-.15, 73, 0);
        double reach = CombatRules.NO_DEBUFF.reach();
        var wrongWay = CombatGeometry.direction(0, 0).scale(reach);
        assertEquals(0, CombatGeometry.firstMeleeBody(eye, wrongWay, target));
        assertFalse(SparringPartner.aimedAt(eye, 0, 0, target, reach));
        assertTrue(SparringPartner.aimedAt(eye, 180, 0, target, reach));
    }

    @Test void alignedInputVariesBetweenEightAndTenClicksAndStrafesBothWays() {
        var engine = trainingPair();
        var partner = new SparringPartner(CombatRules.NO_DEBUFF.reach(), new Random(41));
        var bot = engine.snapshot(2);
        var player = engine.snapshot(1);
        var rates = new HashSet<Integer>();
        var strafeDirections = new HashSet<Integer>();
        for (int second = 0; second < 20; second++) {
            int clicks = 0;
            for (int n = 1; n <= CombatEngine.TICKS_PER_SECOND; n++) {
                var intent = partner.input(second * CombatEngine.TICKS_PER_SECOND + n, bot, player);
                if (intent.attack()) clicks++;
                assertTrue(intent.forward() > 0 && intent.sprint(), "Misses must never trigger a W-tap");
                strafeDirections.add((int) Math.signum(intent.strafe()));
            }
            assertTrue(clicks >= 8 && clicks <= 10);
            rates.add(clicks);
        }
        assertEquals(Set.of(8, 9, 10), rates);
        assertEquals(Set.of(-1, 1), strafeDirections);
    }

    @Test void confirmedHitReleasesForwardForOneTickAndRearmsSprint() {
        var engine = trainingPair();
        var partner = new SparringPartner(CombatRules.NO_DEBUFF.reach(), new Random(41));
        engine.submit(2, new Intent(1, 90, 0, 1, 0, false, true, true, false, 0));
        engine.tick();
        var bot = engine.snapshot(2);
        var player = engine.snapshot(1);
        assertEquals(1, bot.stats().hits());
        var release = partner.input(2, bot, player);
        assertEquals(0, release.forward());
        assertFalse(release.sprint());
        assertNotEquals(0, release.strafe(), "The bot can keep strafing through the W release");
        var repress = partner.input(3, bot, player);
        assertTrue(repress.forward() > 0 && repress.sprint());
    }

    @Test void opponentLandsHitsWithAlignedAimAndSkipsOutOfReachClicks(TestReporter reporter) {
        var match = new PracticeMatch(PracticeMatch.Kit.NO_DEBUFF, true);
        match.ready(1);
        while (match.view(1).phase() == PracticeMatch.Phase.COUNTDOWN) match.tick();

        int approachTicks = 0, hits = 0, sprintHits = 0;
        for (int step = 1; step <= 280 && match.view(1).phase() == PracticeMatch.Phase.FIGHT; step++) {
            var before = match.view(1);
            var player = before.fighters().get(0);
            var bot = before.fighters().get(1);
            var offset = feet(bot).minus(feet(player));
            float yaw = (float) Math.toDegrees(Math.atan2(offset.z(), offset.x()));
            match.input(1, new Intent(step, yaw, 0, 1, step / 34 % 2 == 0 ? .65f : -.65f,
                    false, true, false, false, 0));
            match.tick();
            var after = match.view(1);
            var turned = after.fighters().get(1);
            assertTrue(Math.abs(Math.IEEEremainder(turned.yaw() - bot.yaw(), 360)) <= 30.001);

            var eye = feet(bot).add(0, PlayerDimensions.EYE_HEIGHT, 0);
            boolean previouslyAimed = intersectsForward(eye, bot, player);
            if (Math.hypot(offset.x(), offset.z()) > CombatRules.NO_DEBUFF.reach() + 1) {
                assertEquals(bot.swingTick(), turned.swingTick(), "The bot should approach before swinging");
                approachTicks++;
            }
            if (turned.hits() > bot.hits()) {
                assertTrue(previouslyAimed, "The player should already see the bot looking at them before a hit");
                assertTrue(intersectsForward(eye, turned, player), "The attack ray must still face the player");
                hits++;
                if (after.fighters().get(0).vy() > CombatRules.NO_DEBUFF.lift()) sprintHits++;
            }
        }
        assertTrue(approachTicks >= 2, "The route must start outside sword range");
        assertTrue(hits >= 3, "The bot must still land repeated hits while tracking the player");
        assertTrue(sprintHits >= 3, "Confirm W-tap follow-ups by measuring sprint launches. Observed " + sprintHits);
        reporter.publishEntry("Basalt Courtyard", hits + " hits with aligned aim, " + sprintHits + " sprint launches");
    }

    private static CombatEngine trainingPair() {
        var engine = new CombatEngine(CombatRules.NO_DEBUFF, new CombatEngine.Arena() {
            @Override public boolean solid(int x, int y, int z) { return BasaltCourtyard.solid(x, y, z); }
            @Override public boolean contains(Vec3 point) { return BasaltCourtyard.contains(point.x(), point.y(), point.z()); }
        });
        double floor = BasaltCourtyard.spawn(0).y();
        assertTrue(engine.join(1, new Vec3(0, floor, 1), -90));
        assertTrue(engine.join(2, new Vec3(0, floor, -1), 90));
        return engine;
    }

    private static boolean intersectsForward(Vec3 eye, PracticeMatch.Fighter aim, PracticeMatch.Fighter target) {
        Vec3 delta = feet(target).minus(eye);
        Vec3 ray = CombatGeometry.direction(aim.yaw(), aim.pitch()).scale(CombatRules.NO_DEBUFF.reach());
        return ray.x() * delta.x() + ray.z() * delta.z() > 0
                && Double.isFinite(CombatGeometry.firstMeleeBody(eye, ray, feet(target)));
    }

    private static Vec3 feet(PracticeMatch.Fighter fighter) {
        return new Vec3(fighter.x(), fighter.y(), fighter.z());
    }
}
