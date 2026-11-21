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
import java.util.Locale;

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
        var exchanges = duel(1, reverse, false).stream().filter(t -> t.hit || t.counterHit).toList();
        assertTrue(exchanges.size() >= 3);
        assertTrue(exchanges.subList(0, 3).stream().allMatch(t -> t.hit && t.counterHit), () -> exchanges.subList(0, 3).toString());
    }

    @ParameterizedTest
    @CsvSource({"1,false", "1,true", "2,false", "2,true"})
    void wTapperCanFollowUpAgainstAnOpponentWhoKeepsSwinging(int attacker, boolean reverse, TestReporter reporter) {
        boolean traded = false;
        Tick opener = null;
        var missedCounters = new ArrayList<String>();
        for (Tick t : duel(attacker, reverse, true)) {
            if (t.counterHit) {
                traded |= t.fullHit;
                opener = null;
                missedCounters.clear();
                continue;
            }
            if (!traded) continue;
            if (opener != null && t.counter.intent.attack() && t.counterVulnerable && t.counter.horizontalLowerBound > RULES.reach()) {
                missedCounters.add(String.format(Locale.ROOT, "%d (%.2f blocks)", t.number, t.counter.distance));
            }
            if (!t.fullHit) continue; // A stronger critical inside the hurt window is not a fresh knockback.
            if (opener != null && !missedCounters.isEmpty() && t.receiverSprintLift && t.bothAlive
                    && t.number - opener.number <= 26) {
                assertTrue(t.number - opener.number >= RULES.hurtTicks());
                reporter.publishEntry("Basalt Courtyard", String.format(Locale.ROOT,
                        "Full hits %d (%.2f blocks) and %d (%.2f blocks). Eligible counter swings out of reach at %s.",
                        opener.number, opener.attack.distance, t.number, t.attack.distance, missedCounters));
                return;
            }
            opener = t;
            missedCounters.clear();
        }
        fail("Expected a live sprint follow-up after a real trade and an out-of-reach counter swing");
    }

    private record Aim(Intent intent, double distance, double horizontalLowerBound) {}
    private record Tick(int number, Aim attack, Aim counter, boolean hit, boolean fullHit, boolean counterHit,
                        boolean counterVulnerable, boolean receiverSprintLift, boolean bothAlive) {}

    private static List<Tick> duel(int attacker, boolean reverse, boolean useSpacing) {
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
            // Fixed click clocks start at spawn and run through misses, releases and incoming hits.
            var attack = aim(view.tick(), number, self, other, useSpacing ? 7 : 8,
                    useSpacing, useSpacing && self.hits() > previousHits);
            var counter = aim(view.tick(), number, other, self, 8, false, false);
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
            // Only the sprint bonus can exceed the base vertical cap on a fresh hit.
            trace.add(new Tick(number, attack, counter, hit, fullHit, nextOther.hits() > other.hits(),
                    self.hurtTick() < 0 || outcome.tick() - self.hurtTick() >= RULES.hurtTicks(),
                    nextOther.impulseTick() == outcome.tick() && nextOther.impulseY() > (float) RULES.lift(),
                    nextOther.health() > 0 && nextSelf.health() > 0));
        }
        return trace;
    }

    private static Aim aim(long tick, int number, Fighter self, Fighter other,
                           int cps, boolean useSpacing, boolean release) {
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
        boolean swing = number * cps / 20 > (number - 1) * cps / 20;
        // After contact, back up inside 3.2 center blocks and chase outside. Release W once on each confirmed hit.
        float forward = release ? 0 : useSpacing && self.hits() > 0 && gap < 3.2 ? -1 : 1;
        return new Aim(new Intent(number, yaw, pitch, forward, 0, false, !release, swing, false, 0,
                new AttackView(selected, tick, tick)), distance, horizontalLowerBound);
    }
}
