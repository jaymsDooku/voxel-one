package dev.jayms.combat.practice;

import dev.jayms.combat.CombatEngine.Arena;
import dev.jayms.combat.CombatEngine.AttackView;
import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.CombatEngine.Vec3;
import dev.jayms.combat.CombatGeometry;
import dev.jayms.combat.CombatRules;
import dev.jayms.combat.arena.BasaltCourtyard;
import dev.jayms.combat.practice.PracticeMatch.Fighter;
import dev.jayms.combat.practice.PracticeMatch.Phase;
import dev.jayms.player.PlayerDimensions;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContestedPracticeTest {
    private static final CombatRules RULES = CombatRules.NO_DEBUFF;

    private static final Arena COURTYARD = new Arena() {
        @Override public boolean solid(int x, int y, int z) { return BasaltCourtyard.solid(x, y, z); }
        @Override public boolean contains(Vec3 p) { return BasaltCourtyard.contains(p.x(), p.y(), p.z()); }
    };

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void equalApproachAndClicksTradeInEitherReceiptOrder(boolean reverse) {
        var exchanges = duel(1, reverse, false, 9, 9, 0).stream().filter(t -> t.hit || t.counterHit).toList();
        assertTrue(exchanges.size() >= 3);
        assertTrue(exchanges.subList(0, 3).stream().allMatch(t -> t.hit && t.counterHit), () -> exchanges.subList(0, 3).toString());
    }

    @ParameterizedTest
    @CsvSource({"1,false", "1,true", "2,false", "2,true"})
    void fourFullWTapHitsAgainstAnOpponentWhoKeepsSwinging(int attacker, boolean reverse, TestReporter reporter) {
        var hits = new ArrayList<Integer>();
        int eligibleMisses = 0;
        for (Tick t : duel(attacker, reverse, true, 9, 9, 10)) {
            if (t.counterHit) {
                hits.clear();
                eligibleMisses = 0;
                continue;
            }
            if (!t.exchanged) continue;
            if (t.counter.intent.attack() && t.counterVulnerable && t.counter.horizontalLowerBound > RULES.reach()) {
                eligibleMisses++;
            }
            if (!t.fullHit) continue; // A stronger critical inside the hurt window is not a fresh knockback.
            if (!hits.isEmpty()) {
                int gap = t.number - hits.get(hits.size() - 1);
                if (gap > 26 || eligibleMisses == 0) hits.clear();
                assertTrue(gap >= RULES.hurtTicks());
            }
            eligibleMisses = 0;
            if (!t.receiverSprintLift || !t.bothAlive) {
                hits.clear();
                continue;
            }
            assertTrue(t.attack.distance <= RULES.reach());
            hits.add(t.number);
            if (hits.size() == 4) {
                reporter.publishEntry("Basalt Courtyard", "Unanswered full sprint hits on fight ticks " + hits);
                return;
            }
        }
        fail("Expected four live sprint hits with an eligible out-of-range counter between each pair");
    }

    private record Aim(Intent intent, double distance, double horizontalLowerBound) {}
    private record Tick(int number, Aim attack, Aim counter, boolean exchanged, boolean hit, boolean fullHit, boolean counterHit,
                        boolean counterVulnerable, boolean receiverSprintLift, boolean bothAlive) {}

    @ParameterizedTest
    @CsvSource({"8,8","8,9","8,10","9,8","9,9","9,10","10,8","10,9","10,10"})
    void bothClickClocksContinueDuringKnockbackAndMisses(int cps, int counterCps) {
        for (int phase : new int[]{0, 5, 10, 15}) {
            for (boolean reverse : List.of(false, true)) {
                var trace = duel(1, reverse, true, cps, counterCps, phase);
                assertTrue(trace.stream().anyMatch(Tick::exchanged));
                assertEquals(trace.size() * cps / 20, trace.stream().filter(t -> t.attack.intent.attack()).count());
                assertEquals((trace.size() * counterCps + phase) / 20,
                        trace.stream().filter(t -> t.counter.intent.attack()).count());
                assertTrue(trace.stream().allMatch(t -> t.counter.intent.forward() == 1));
            }
        }
    }

    private static List<Tick> duel(int attacker, boolean reverse, boolean useSpacing, int cps, int counterCps, int phase) {
        var match = new PracticeMatch(PracticeMatch.Kit.NO_DEBUFF, false);
        match.ready(match.join("West"));
        match.ready(match.join("East"));
        while (match.view(1).phase() == Phase.COUNTDOWN) match.tick();
        assertEquals(Phase.FIGHT, match.view(1).phase());

        var trace = new ArrayList<Tick>();
        int defender = 3 - attacker;
        int previousHits = 0;
        for (int number = 1; number <= 240; number++) {
            var view = match.view(attacker);
            if (view.phase() != Phase.FIGHT) break;
            var self = view.fighters().get(attacker - 1);
            var other = view.fighters().get(defender - 1);
            boolean exchanged = self.hits() > 0 && other.hits() > 0;
            var attack = aim(view.tick(), number, self, other, useSpacing && exchanged,
                    self.hits() > previousHits, cps, 0);
            var counter = aim(view.tick(), number, other, self, false, false, counterCps, phase);
            previousHits = self.hits();
            int firstSender = reverse ? 2 : 1;
            if (firstSender == attacker) {
                match.input(attacker, attack.intent);
                match.input(defender, counter.intent);
            } else {
                match.input(defender, counter.intent);
                match.input(attacker, attack.intent);
            }
            match.tick();
            var outcome = match.view(attacker);
            var nextSelf = outcome.fighters().get(attacker - 1);
            var nextOther = outcome.fighters().get(defender - 1);
            boolean hit = nextSelf.hits() > self.hits();
            boolean fullHit = hit && nextOther.hurtTick() == outcome.tick();
            // The defender never jumps. Account for downward momentum on an airborne catch.
            double carriedY = other.y() == BasaltCourtyard.FLOOR_Y + 1 ? -.0784 : (other.vy() - .08) * .98;
            double sprintLift = Math.min(RULES.lift(), RULES.lift() + carriedY / 2) + .10;
            trace.add(new Tick(number, attack, counter, exchanged, hit, fullHit, nextOther.hits() > other.hits(),
                    self.hurtTick() < 0 || outcome.tick() - self.hurtTick() >= RULES.hurtTicks(),
                    nextOther.impulseTick() == outcome.tick() && Math.abs(nextOther.vy() - sprintLift) < .001,
                    nextOther.health() > 0 && nextSelf.health() > 0));
        }
        return trace;
    }

    private static Aim aim(long tick, int number, Fighter self, Fighter other,
                           boolean useSpacing, boolean release, int cps, int phase) {
        double dx = other.x() - self.x(), dz = other.z() - self.z();
        double gap = Math.hypot(dx, dz);
        double eyeY = self.y() + PlayerDimensions.EYE_HEIGHT;
        // Both track the nearest height on the body. The lower player can look straight ahead at an airborne target.
        double aimY = Math.max(other.y() + .1, Math.min(eyeY, other.y() + PlayerDimensions.HEIGHT));
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
        double nearFace = Math.max(.001, gap - PlayerDimensions.RADIUS - .1);
        float pitch = (float) Math.toDegrees(Math.atan2(aimY - eyeY, nearFace));
        Vec3 eye = new Vec3(self.x(), eyeY, self.z());
        Vec3 ray = CombatGeometry.direction(yaw, pitch).scale(32);
        Vec3 target = new Vec3(other.x(), other.y(), other.z());
        double body = CombatGeometry.firstMeleeBody(eye, ray, target);
        double distance = body * ray.length();
        int selected = distance <= RULES.reach() && body < CombatGeometry.firstBlock(COURTYARD, eye, ray)
                ? other.id() : 0;
        // Even an optimally pitched ray must cover this much along one horizontal axis.
        double horizontalLowerBound = Math.max(Math.abs(dx), Math.abs(dz)) - PlayerDimensions.RADIUS - .1F;
        // Both players keep clicking even when out of reach or releasing W.
        boolean swing = (number * cps + phase) / 20 > ((number - 1) * cps + phase) / 20;
        float forward = 1;
        if (useSpacing) {
            // Step in to swing, release W after contact, and back up if the gap is already closing.
            boolean advance = swing && nearFace <= RULES.reach() || gap >= 3.55;
            forward = release ? 0 : advance ? 1 : -1;
        }
        return new Aim(new Intent(number, yaw, pitch, forward, 0, false, forward > 0, swing, false, 0,
                new AttackView(selected, tick, tick)), distance, horizontalLowerBound);
    }
}
