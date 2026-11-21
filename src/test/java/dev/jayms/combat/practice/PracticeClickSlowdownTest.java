package dev.jayms.combat.practice;

import dev.jayms.combat.CombatEngine;
import dev.jayms.combat.CombatEngine.AttackView;
import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.CombatEngine.PlayerState;
import dev.jayms.combat.CombatEngine.Vec3;
import dev.jayms.combat.CombatRules;
import dev.jayms.combat.arena.BasaltCourtyard;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Matched counterhits in the central lane of Basalt Courtyard. */
class PracticeClickSlowdownTest {
    private static final CombatRules RULES = CombatRules.NO_DEBUFF;
    private static final double EPSILON = .00001;
    private static final CombatEngine.Arena COURTYARD = new CombatEngine.Arena() {
        @Override public boolean solid(int x, int y, int z) { return BasaltCourtyard.solid(x, y, z); }
        @Override public boolean contains(Vec3 feet) { return BasaltCourtyard.contains(feet.x(), feet.y(), feet.z()); }
    };

    private enum Click { TARGET, AIR, NONE }
    private record Sample(PlayerState attacker, double horizontalStep) {}

    @ParameterizedTest
    @ValueSource(ints = {8, 9, 10})
    void protectedSprintClickDampsAnAlreadyReceivedCounterhit(int cps, TestReporter reporter) {
        var clickTicks = new ArrayList<Integer>();
        for (int n = 1; n <= CombatEngine.TICKS_PER_SECOND; n++) {
            if (n * cps / CombatEngine.TICKS_PER_SECOND > (n - 1) * cps / CombatEngine.TICKS_PER_SECOND) {
                clickTicks.add(n + 1); // Save one initial pose before issuing displayed-target clicks.
            }
        }
        assertEquals(cps, clickTicks.size());
        double sprintTargetedStep = 0, sprintAirStep = 0, normalTargetedStep = 0, normalAirStep = 0;
        for (int slot = 1; slot < clickTicks.size(); slot++) {
            int opener = clickTicks.get(slot - 1), exchange = clickTicks.get(slot);
            for (int side : new int[] {1, 2}) {
                for (boolean sprintCounter : new boolean[] {false, true}) {
                    double basePush = RULES.knockback() + (sprintCounter ? RULES.sprintKnockback() : 0);
                    var targetedAfter = sample(opener, exchange, side, Click.TARGET, true, true, sprintCounter);
                    var targetedBefore = sample(opener, exchange, side, Click.TARGET, true, false, sprintCounter);
                    var targetedWithoutHit = sample(opener, exchange, side, Click.TARGET, false, true, sprintCounter);
                    var air = sample(opener, exchange, side, Click.AIR, true, true, sprintCounter);
                    var airWithoutHit = sample(opener, exchange, side, Click.AIR, false, true, sprintCounter);
                    var noClick = sample(opener, exchange, side, Click.NONE, true, true, sprintCounter);
                    // The no-counter runs measure the step due to the same input and sprint state alone.
                    double pushAfter = targetedAfter.horizontalStep - targetedWithoutHit.horizontalStep;
                    double pushBefore = targetedBefore.horizontalStep - targetedWithoutHit.horizontalStep;
                    double pushOnAir = air.horizontalStep - airWithoutHit.horizontalStep;
                    assertEquals(basePush, pushOnAir, EPSILON);
                    assertEquals(air.horizontalStep, noClick.horizontalStep, EPSILON);
                    assertEquals(.6 * basePush, pushAfter, EPSILON);
                    assertEquals(basePush, pushBefore, EPSILON);
                    assertEquals(air.attacker.velocity().y(), targetedAfter.attacker.velocity().y(), EPSILON);
                    assertEquals(air.attacker.velocity().y(), targetedBefore.attacker.velocity().y(), EPSILON);
                    if (sprintCounter) {
                        sprintTargetedStep = pushAfter;
                        sprintAirStep = pushOnAir;
                    } else {
                        normalTargetedStep = pushAfter;
                        normalAirStep = pushOnAir;
                    }
                }
            }
        }
        reporter.publishEntry("Basalt Courtyard", String.format(Locale.ROOT,
                "%d CPS, %d successive click pairs, both mirrored lanes and receipt orders. Counterhit displacement after"
                        + " subtracting steering: normal %.6f to %.6f blocks; sprint %.6f to %.6f blocks;"
                        + " vertical unchanged. Clicking first preserves the later impulse.",
                cps, clickTicks.size() - 1, normalAirStep, normalTargetedStep, sprintAirStep, sprintTargetedStep));
    }

    private static Sample sample(int opener, int exchange, int attackerId, Click click,
                                 boolean counter, boolean counterFirst, boolean sprintCounter) {
        var engine = new CombatEngine(RULES, COURTYARD);
        int defenderId = 3 - attackerId;
        double floor = BasaltCourtyard.spawn(0).y();
        assertTrue(engine.join(1, new Vec3(0, floor, 1), -90));
        assertTrue(engine.join(2, new Vec3(0, floor, -1), 90));
        for (int tick = 1; tick < exchange; tick++) {
            if (tick == opener) submit(engine, tick, attackerId, defenderId, false, Click.TARGET);
            engine.tick(tick);
        }
        var before = engine.snapshot(attackerId);
        var protectedDefender = engine.snapshot(defenderId);
        assertEquals(1, before.stats().hits(), "The opener should create hurt protection");
        assertEquals(opener, protectedDefender.hurtTick());
        if (counter && counterFirst) submit(engine, exchange, defenderId, attackerId, sprintCounter, Click.TARGET);
        submit(engine, exchange, attackerId, defenderId, true, click);
        if (counter && !counterFirst) submit(engine, exchange, defenderId, attackerId, sprintCounter, Click.TARGET);
        engine.tick(exchange);
        var attacker = engine.snapshot(attackerId);
        var defender = engine.snapshot(defenderId);
        assertEquals(1, attacker.stats().hits(), "The second attack is rejected by hurt protection");
        assertEquals(protectedDefender.health(), defender.health(), EPSILON);
        assertEquals(opener, defender.hurtTick());
        assertEquals(opener, defender.impulseTick());
        assertEquals(counter ? 1 : 0, defender.stats().hits());
        assertEquals(counter ? 18.5 : 20, attacker.health(), EPSILON);
        assertEquals(counter ? exchange : -1, attacker.hurtTick());
        if (click == Click.TARGET) assertFalse(attacker.sprinting());
        else assertTrue(attacker.sprinting());
        return new Sample(attacker, (attackerId == 1 ? 1 : -1) * (attacker.feet().z() - before.feet().z()));
    }

    private static void submit(CombatEngine engine, int tick, int id, int target, boolean sprint, Click click) {
        Vec3 delta = engine.snapshot(target).feet().minus(engine.snapshot(id).feet());
        float yaw = (float) Math.toDegrees(Math.atan2(delta.z(), delta.x()));
        float pitch = (float) Math.toDegrees(Math.atan2(delta.y(), Math.hypot(delta.x(), delta.z())));
        if (click != Click.TARGET) yaw += 90;
        AttackView view = click == Click.NONE ? null
                : new AttackView(click == Click.TARGET ? target : 0, tick - 1, tick - 1);
        assertTrue(engine.submit(id, new Intent(tick, yaw, pitch, sprint ? 1 : 0, 0,
                false, sprint, click != Click.NONE, false, 0, view)));
    }
}
