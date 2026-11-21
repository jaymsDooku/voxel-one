package dev.jayms.combat.practice;

import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.CombatEngine.Vec3;
import dev.jayms.combat.CombatGeometry;
import dev.jayms.combat.CombatRules;
import dev.jayms.player.PlayerDimensions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import static org.junit.jupiter.api.Assertions.*;

class SparringPartnerTest {
    @Test void rejectsAnOverlapBehindTheBotEvenIfTheExpandedHitboxContainsItsEyes() {
        var eye = new Vec3(0, 73 + PlayerDimensions.EYE_HEIGHT, 0);
        var target = new Vec3(-.15, 73, 0);
        double reach = CombatRules.NO_DEBUFF.reach();
        var wrongWay = CombatGeometry.direction(0, 0).scale(reach);
        assertEquals(0, CombatGeometry.firstMeleeBody(eye, wrongWay, target));
        assertFalse(PracticeMatch.aimedAt(eye, 0, 0, target, reach));
        assertTrue(PracticeMatch.aimedAt(eye, 180, 0, target, reach));
    }

    @Test void opponentLandsHitsWithAlignedAimAndSkipsOutOfReachClicks(TestReporter reporter) {
        var match = new PracticeMatch(PracticeMatch.Kit.NO_DEBUFF, true);
        match.ready(1);
        while (match.view(1).phase() == PracticeMatch.Phase.COUNTDOWN) match.tick();

        int outOfReachClicks = 0, hits = 0;
        for (int step = 1; step <= 200 && match.view(1).phase() == PracticeMatch.Phase.FIGHT; step++) {
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
            boolean clickDue = after.tick() * CombatRules.AUTO_CLICK_CPS / PracticeMatch.TICKS_PER_SECOND
                    > before.tick() * CombatRules.AUTO_CLICK_CPS / PracticeMatch.TICKS_PER_SECOND;
            if (clickDue && Math.hypot(offset.x(), offset.z()) > CombatRules.NO_DEBUFF.reach() + 1) {
                assertEquals(bot.swingTick(), turned.swingTick(), "The bot should approach before swinging");
                outOfReachClicks++;
            }
            if (turned.hits() > bot.hits()) {
                assertTrue(previouslyAimed, "The player should already see the bot looking at them before a hit");
                assertTrue(intersectsForward(eye, turned, player), "The attack ray must still face the player");
                hits++;
            }
        }
        assertTrue(outOfReachClicks >= 2, "The route must start outside sword range");
        assertTrue(hits >= 3, "The bot must still land repeated hits while tracking the player");
        reporter.publishEntry("Basalt Courtyard", outOfReachClicks + " clicks withheld outside sword range, "
                + hits + " hits with aligned aim");
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
