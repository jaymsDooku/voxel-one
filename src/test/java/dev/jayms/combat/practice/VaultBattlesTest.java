package dev.jayms.combat.practice;

import dev.jayms.combat.CombatEngine;
import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.CombatEngine.Vec3;
import dev.jayms.combat.CombatRules;
import dev.jayms.combat.arena.PracticeTerrain;
import dev.jayms.combat.arena.VaultBattlefield;
import dev.jayms.combat.practice.PracticeMatch.Fighter;
import dev.jayms.combat.practice.PracticeMatch.Kit;
import dev.jayms.combat.practice.PracticeMatch.Phase;
import dev.jayms.combat.practice.PracticeMatch.Result;
import dev.jayms.combat.practice.PracticeMatch.VaultStage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static dev.jayms.combat.practice.PracticeMatch.TICKS_PER_SECOND;
import static dev.jayms.combat.practice.VaultObjective.*;
import static org.junit.jupiter.api.Assertions.*;

class VaultBattlesTest {
    @Test void unoccupiedAndContestedTicksPauseCaptureWithoutUndoingIt() {
        var objective = new VaultObjective();
        for (int i = 0; i < RING_SECONDS * TICKS_PER_SECOND - 1; i++) assertFalse(objective.advance(true, false));
        assertEquals(VaultStage.OUTER_RING, objective.stage());
        var progress = objective.status(Phase.FIGHT, Result.NONE);
        assertTrue(progress.get(1).startsWith("Securing"));
        assertTrue(progress.get(1).contains("4.9 / 5 s"));
        for (int i = 0; i < 200; i++) assertFalse(objective.advance(true, true));
        assertTrue(objective.status(Phase.FIGHT, Result.NONE).get(1).startsWith("Contested"));
        for (int i = 0; i < 200; i++) assertFalse(objective.advance(false, false));
        assertTrue(objective.status(Phase.FIGHT, Result.NONE).get(1).contains("4.9 / 5 s"));
        assertFalse(objective.advance(false, true));
        assertEquals(VaultStage.OUTER_RING, objective.stage());
        assertFalse(objective.advance(true, false));
        assertEquals(VaultStage.VAULT_CORE, objective.stage());
        assertTrue(objective.status(Phase.FIGHT, Result.NONE).get(1).contains("0.0 / 8 s"));

        for (int i = 0; i < CORE_SECONDS * TICKS_PER_SECOND - 1; i++) assertFalse(objective.advance(true, false));
        assertEquals(VaultStage.VAULT_CORE, objective.stage());
        assertTrue(objective.advance(true, false));
        assertEquals(VaultStage.CAPTURED, objective.stage());
        assertFalse(objective.advance(true, false));
    }

    @Test void attackerWalksBothObjectivesWithContestedRingAndRematchResetsStage() {
        var match = start(false);
        var defend = VaultBattlefield.defendRoute(1, true, false);
        walkAlong(match, 1, defend, 1);
        var breach = VaultBattlefield.attackRoute(1, false);
        long sequence = walkAlong(match, 2, breach, 1);
        assertTrue(VaultBattlefield.inBreachZone(feet(fighter(match, 1))));
        assertTrue(VaultBattlefield.inBreachZone(feet(fighter(match, 2))));
        tick(match, (RING_SECONDS + 1) * TICKS_PER_SECOND);
        assertEquals(VaultStage.OUTER_RING, match.view(2).vaultStage());
        assertTrue(match.view(2).status().get(1).startsWith("Contested - 0.0"));
        match.leave(1);

        tick(match, RING_SECONDS * TICKS_PER_SECOND - 1);
        assertEquals(VaultStage.OUTER_RING, match.view(2).vaultStage());
        match.tick();
        assertEquals(VaultStage.VAULT_CORE, match.view(2).vaultStage());
        var core = VaultBattlefield.attackRoute(1, true);
        int lastShared = core.indexOf(VaultBattlefield.breachMarker());
        assertTrue(lastShared >= 0);
        walkAlong(match, 2, core.subList(lastShared + 1, core.size()), sequence);
        assertTrue(VaultBattlefield.inCoreZone(feet(fighter(match, 2))));
        tick(match, CORE_SECONDS * TICKS_PER_SECOND);
        assertEquals(Result.OBJECTIVE, match.view(2).result());
        assertEquals(ATTACKERS, match.view(2).winningTeam());
        assertEquals(VaultStage.CAPTURED, match.view(2).vaultStage());

        tick(match, match.view(2).ticksLeft());
        assertEquals(61, match.join("Replacement"));
        for (int id = 2; id <= 61; id++) match.ready(id);
        assertEquals(Phase.COUNTDOWN, match.view(2).phase());
        assertEquals(VaultStage.OUTER_RING, match.view(2).vaultStage());
        assertEquals(0, match.view(2).winner());
        assertFalse(VaultBattlefield.inBreachZone(feet(fighter(match, 2))));
    }

    @Test void defendersWinWhenClockExpiresAndAttackersWinIfDefendersWithdraw() {
        var timeout = start(false);
        for (int id = 3; id <= 60; id++) timeout.leave(id);
        int round = 8 * 60 * TICKS_PER_SECOND;
        assertEquals(round, timeout.view(1).ticksLeft());
        tick(timeout, round - 1);
        assertEquals(Phase.FIGHT, timeout.view(1).phase());
        timeout.tick();
        assertEquals(Result.TIMEOUT, timeout.view(1).result());
        assertEquals(DEFENDERS, timeout.view(1).winningTeam());

        var forfeit = start(false);
        int spectator = forfeit.join("Spectator");
        for (int defender = 1; defender <= 59; defender += 2) forfeit.leave(defender);
        assertEquals(Result.FORFEIT, forfeit.view(spectator).result());
        assertEquals(ATTACKERS, forfeit.view(spectator).winningTeam());
        for (int attacker = 2; attacker <= 60; attacker += 2) forfeit.leave(attacker);
        assertEquals(ATTACKERS, forfeit.view(spectator).winningTeam());
    }

    @Test void offlineAttackersUseTheirLanesAndBothTeamsEngage() {
        var match = start(true);
        assertEquals(ATTACKERS, fighter(match, 1).side());
        assertEquals(30, match.view(1).fighters().stream().filter(f -> f.side() == ATTACKERS).count());
        assertEquals(30, match.view(1).fighters().stream().filter(f -> f.side() == DEFENDERS).count());
        tick(match, 30 * TICKS_PER_SECOND);
        List<Fighter> fighters = match.view(1).fighters();
        assertTrue(fighters.stream().anyMatch(f -> f.side() == DEFENDERS && f.hits() > 0));
        assertTrue(fighters.stream().anyMatch(f -> f.side() == ATTACKERS && f.hits() > 0));
        for (int lane = 0; lane < 3; lane++) {
            double laneX = VaultBattlefield.attackRoute(lane, false).get(0).x();
            assertTrue(fighters.stream().anyMatch(f -> f.side() == ATTACKERS && Math.abs(f.x() - laneX) < 10
                    && f.z() > -15), "Expected attackers to get through lane " + lane);
        }
    }

    @Test void aTrappedBotClimbsOutAndRejoinsTheSiegeThroughItsLane() {
        var terrain = PracticeTerrain.VAULT_BATTLEFIELD;
        var rules = CombatRules.NO_DEBUFF;
        var starts = List.of(new Vec3(12.5, 69, 13.5), new Vec3(46.5, 69, 25.5), new Vec3(15.5, 69, 68.5));
        for (Vec3 start : starts) {
            var engine = new CombatEngine(rules, terrain);
            var sparring = new SparringPartner(rules, terrain, new Random(1));
            var bot = new VaultBot(ATTACKERS, 0, terrain, rules);
            assertTrue(engine.join(1, start, 90));
            for (int tick = 1; tick <= 1300 && !VaultBattlefield.inBreachZone(engine.snapshot(1).feet()); tick++) {
                stepBot(engine, bot, sparring, tick);
            }
            assertTrue(VaultBattlefield.inBreachZone(engine.snapshot(1).feet()),
                    "Bot stayed at " + engine.snapshot(1).feet() + " after starting in the trench at " + start);
        }
    }

    @Test void aBotReplansWhenDisplacedIntoADifferentTrenchDuringEscape() {
        var terrain = PracticeTerrain.VAULT_BATTLEFIELD;
        var rules = CombatRules.NO_DEBUFF;
        var engine = new CombatEngine(rules, terrain);
        var sparring = new SparringPartner(rules, terrain, new Random(1));
        var bot = new VaultBot(ATTACKERS, 0, terrain, rules);
        assertTrue(engine.join(1, new Vec3(12.5, 69, 13.5), 90));
        int tick = 1;
        for (; tick <= 300; tick++) {
            stepBot(engine, bot, sparring, tick);
            Vec3 feet = engine.snapshot(1).feet();
            if (feet.y() >= 73 && feet.z() < 9.1) break;
        }
        assertTrue(tick <= 300, "Bot never reached its first exit");
        assertTrue(engine.reset(1, new Vec3(-33.5, 69, 11.5), -90));
        for (tick++; tick <= 1300 && !VaultBattlefield.inBreachZone(engine.snapshot(1).feet()); tick++) {
            stepBot(engine, bot, sparring, tick);
        }
        assertTrue(VaultBattlefield.inBreachZone(engine.snapshot(1).feet()),
                "Bot stayed at " + engine.snapshot(1).feet() + " after changing trenches");
    }

    private static PracticeMatch start(boolean offline) {
        var match = new PracticeMatch(Kit.VAULT_BATTLES, offline);
        if (offline) {
            match.ready(1);
        } else {
            for (int id = 1; id <= 60; id++) assertEquals(id, match.join("Fighter " + id));
            for (int id = 1; id <= 60; id++) match.ready(id);
        }
        assertEquals(Phase.COUNTDOWN, match.view(1).phase());
        tick(match, match.view(1).ticksLeft());
        assertEquals(Phase.FIGHT, match.view(1).phase());
        return match;
    }

    private static long walkAlong(PracticeMatch match, int id, List<Vec3> route, long sequence) {
        for (Vec3 point : route) {
            for (int i = 0; i < 260 && feet(fighter(match, id)).minus(point).length() > .45; i++) {
                Vec3 offset = point.minus(feet(fighter(match, id)));
                double distance = offset.length();
                float yaw = (float) Math.toDegrees(Math.atan2(offset.z(), offset.x()));
                match.input(id, new Intent(sequence++, yaw, 0, distance > 1.6 ? 1 : .45f, 0,
                        false, distance > 1.6, false, false, 0));
                match.tick();
            }
            assertTrue(feet(fighter(match, id)).minus(point).length() <= .45,
                    "Fighter " + id + " at " + feet(fighter(match, id)) + " could not reach " + point);
        }
        Fighter fighter = fighter(match, id);
        match.input(id, new Intent(sequence++, fighter.yaw(), 0, 0, 0, false, false, false, false, 0));
        return sequence;
    }

    private static Fighter fighter(PracticeMatch match, int id) {
        return match.view(id).fighters().stream().filter(f -> f.id() == id).findFirst().orElseThrow();
    }

    private static Vec3 feet(Fighter fighter) { return new Vec3(fighter.x(), fighter.y(), fighter.z()); }

    private static void stepBot(CombatEngine engine, VaultBot bot, SparringPartner sparring, int tick) {
        engine.submit(1, bot.input(tick, engine.snapshot(1), null, VaultStage.OUTER_RING, sparring));
        engine.tick(tick);
    }

    private static void tick(PracticeMatch match, int count) {
        for (int i = 0; i < count; i++) match.tick();
    }
}
