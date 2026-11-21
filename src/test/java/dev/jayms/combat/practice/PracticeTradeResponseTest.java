package dev.jayms.combat.practice;

import dev.jayms.combat.CombatEngine.AttackView;
import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.CombatEngine.Vec3;
import dev.jayms.combat.CombatGeometry;
import dev.jayms.combat.CombatRules;
import dev.jayms.combat.practice.PracticeMatch.Fighter;
import dev.jayms.player.PlayerDimensions;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** A head-on trade from the real Basalt spawns, with ordinary independent player inputs. */
class PracticeTradeResponseTest {
    private static final CombatRules RULES = CombatRules.NO_DEBUFF;

    @ParameterizedTest
    @ValueSource(ints = {8, 9, 10})
    void headOnHitsSeparateBothBodiesAndPermitTheNextTrade(int cps, TestReporter reporter) {
        var forward = trade(cps, 1);
        var mirror = trade(cps, 2);
        assertEquals(forward.size(), mirror.size());

        // Swap senders across the arena center. Receipt order may distinguish roles, actor ID may not.
        for (int i = 0; i < forward.size(); i++) {
            Step a = forward.get(i), b = mirror.get(i);
            assertEquals(a.first.z(), -b.first.z(), .00001);
            assertEquals(a.second.z(), -b.second.z(), .00001);
            assertEquals(a.first.y(), b.first.y(), .00001);
            assertEquals(a.second.y(), b.second.y(), .00001);
            assertEquals(a.first.hurtTick(), b.first.hurtTick());
            assertEquals(a.second.hurtTick(), b.second.hurtTick());
            assertEquals(a.first.health(), b.first.health(), .00001);
            assertEquals(a.second.health(), b.second.health(), .00001);
        }

        var exchanges = forward.stream().filter(s -> s.first.hurtTick() == s.tick || s.second.hurtTick() == s.tick).toList();
        assertTrue(exchanges.size() >= 3, "Both players must be able to re-engage from spawn");
        for (Step s : exchanges.subList(0, 3)) {
            assertEquals(s.tick, s.first.hurtTick(), "Missing first player's full hit reaction");
            assertEquals(s.tick, s.second.hurtTick(), "Missing second player's full hit reaction");
            assertEquals(s.tick, s.first.impulseTick());
            assertEquals(s.tick, s.second.impulseTick());
        }
        Step opening = exchanges.get(0);
        Step second = exchanges.get(1);
        double firstAway = Math.copySign(1, opening.first.z() - opening.second.z()) * opening.first.vz();
        double secondAway = Math.copySign(1, opening.second.z() - opening.first.z()) * opening.second.vz();
        assertTrue(firstAway > 0 && secondAway > 0, "Both bodies must move away on impact");
        assertEquals(.4208, opening.first.vy(), .00001);
        assertEquals(.4208, opening.second.vy(), .00001);
        // EntityLiving.a halves carried motion; EntityHuman.attack damps only the successful
        // sprint attacker's X/Z by .6. Applying both in receipt order to equal approach speeds
        // gives a .4 * launch difference after equal forward steering on the impact step.
        assertEquals(.4 * (RULES.knockback() + RULES.sprintKnockback()), firstAway - secondAway, .00001);

        var arc = forward.stream().filter(s -> s.tick >= opening.tick && s.tick < second.tick).toList();
        double peakGap = arc.stream().mapToDouble(Step::gap).max().orElseThrow();
        Step lostReach = arc.stream().filter(s -> !inReach(s.first, s.second)).findFirst().orElseThrow();
        Step regained = forward.stream().filter(s -> s.tick > lostReach.tick && inReach(s.first, s.second)).findFirst().orElseThrow();
        assertTrue(peakGap > opening.gap() + .25, "The impulse must continue separating bodies beyond its first step");
        assertTrue(regained.tick < second.tick);
        // Paper EntityLiving.damageEntity uses the upper half of maxNoDamageTicks
        // for equal raw damage. A held-W, synced pair should resume dealing damage
        // within one click interval after both protection and lost reach clear.
        for (int i = 1; i < 3; i++)
            assertTrue(exchanges.get(i).tick - exchanges.get(i - 1).tick >= RULES.hurtTicks());
        long earliest = Math.max(opening.tick + RULES.hurtTicks(), regained.tick + 1);
        assertTrue(second.tick >= earliest && second.tick < earliest + (int) Math.ceil(20.0 / cps));
        reporter.publishEntry("Basalt Courtyard", String.format(Locale.ROOT,
                "%d CPS; full exchanges at %d/%d/%d; first and second sender launch %.3f/%.3f blocks;"
                        + " peak center gap %.3f; reach restored %d ticks after impact",
                cps, opening.number, second.number, exchanges.get(2).number, firstAway, secondAway,
                peakGap, regained.tick - opening.tick));
    }

    private record Step(int number, long tick, Fighter first, Fighter second) {
        double gap() { return Math.abs(first.z() - second.z()); }
    }

    private static List<Step> trade(int cps, int firstId) {
        var match = new PracticeMatch(PracticeMatch.Kit.NO_DEBUFF, false);
        match.ready(match.join("North"));
        match.ready(match.join("South"));
        while (match.view(firstId).phase() == PracticeMatch.Phase.COUNTDOWN) match.tick();
        var trace = new ArrayList<Step>();
        for (int n = 1; n <= 85; n++) {
            var before = match.view(firstId);
            assertEquals(PracticeMatch.Phase.FIGHT, before.phase());
            Fighter first = before.fighters().get(firstId - 1), second = before.fighters().get(2 - firstId);
            boolean swing = n * cps / 20 > (n - 1) * cps / 20;
            match.input(first.id(), input(n, before.tick(), first, second, swing));
            match.input(second.id(), input(n, before.tick(), second, first, swing));
            match.tick();
            var next = match.view(firstId);
            trace.add(new Step(n, next.tick(), next.fighters().get(firstId - 1), next.fighters().get(2 - firstId)));
        }
        return trace;
    }

    // The mirrored fighters approach on the clear central lane at the same elevation.
    private static boolean inReach(Fighter self, Fighter other) {
        Vec3 eye = new Vec3(self.x(), self.y() + PlayerDimensions.EYE_HEIGHT, self.z());
        Vec3 ray = new Vec3(0, 0, Math.copySign(RULES.reach(), other.z() - self.z()));
        return Double.isFinite(CombatGeometry.firstMeleeBody(eye, ray, new Vec3(other.x(), other.y(), other.z())));
    }

    private static Intent input(long sequence, long tick, Fighter self, Fighter other, boolean swing) {
        float yaw = self.z() < other.z() ? 90 : -90;
        var aim = swing ? new AttackView(inReach(self, other) ? other.id() : 0, tick, tick) : null;
        return new Intent(sequence, yaw, 0, 1, 0, false, true, swing, false, 0, aim);
    }
}
