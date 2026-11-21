package dev.jayms.combat.practice;

import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.CombatEngine.Item;
import dev.jayms.combat.practice.PracticeMatch.Fighter;
import dev.jayms.combat.practice.PracticeMatch.Kit;
import dev.jayms.combat.practice.PracticeMatch.Phase;
import dev.jayms.combat.practice.PracticeMatch.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TeamfightingLabTest {
    @Test void fourPlayersFillBothTeamsAndFiveSpectates() {
        var match = new PracticeMatch(Kit.TEAMFIGHTING_LAB, false);
        for (int i = 1; i <= 5; i++) assertEquals(i, match.join("Player " + i));
        assertEquals(5, match.view(5).spectatorId());
        assertEquals(4, match.view(1).fighters().size());
        assertEquals(0, fighter(match, 1).side());
        assertEquals(1, fighter(match, 2).side());
        assertEquals(0, fighter(match, 3).side());
        assertEquals(1, fighter(match, 4).side());
        assertNotEquals(fighter(match, 1).x(), fighter(match, 3).x());
        assertNotEquals(fighter(match, 2).x(), fighter(match, 4).x());
        for (int id = 1; id <= 3; id++) match.ready(id);
        match.ready(5);
        assertEquals(Phase.WAITING, match.view(1).phase());
        match.ready(4);
        assertEquals(Phase.COUNTDOWN, match.view(1).phase());
    }

    @Test void friendlyFireIsBlockedAndTeammatesCanHealWithSplash() {
        var match = start(false);
        match.input(1, new Intent(1, 0, 0, 0, 0, false, false, true, false, 0));
        match.tick();
        assertTrue(fighter(match, 1).swinging());
        assertEquals(0, fighter(match, 1).hits());
        assertEquals(20, fighter(match, 3).health());
        assertEquals(-1, fighter(match, 3).hurtTick());

        int sequence = 2;
        while (sequence < 220 && fighter(match, 2).health() == 20) {
            chase(match, 1, 2, sequence++);
            match.tick();
        }
        assertTrue(fighter(match, 2).health() < 20, "Enemy should take damage");
        assertTrue(fighter(match, 1).hits() > 0);
        assertEquals(20, fighter(match, 3).health());
        assertEquals(Item.HEALING, match.view(4).inventory().get(5).item());
        match.input(4, new Intent(1, 0, -76, 0, 0, false, false, false, true, 5));
        for (int i = 0; i < 12; i++) match.tick();
        assertEquals(1, fighter(match, 4).used());
        assertEquals(20, fighter(match, 2).health());
    }

    @Test void knockoutEliminatesOneFighterAndTheRoundEndsWhenTheirTeamFalls() {
        var match = start(false);
        int sequence = 1;
        for (int target : new int[]{2, 4}) {
            while (sequence < 900 && fighter(match, target).health() > 0) {
                chase(match, 1, target, sequence++);
                match.tick();
            }
            assertEquals(0, fighter(match, target).health(), "Expected knockout of fighter " + target);
            if (target == 2) {
                assertEquals(Phase.FIGHT, match.view(1).phase());
                assertEquals(2, match.view(2).spectatorId());
                assertTrue(match.view(2).inventory().isEmpty());
                assertEquals(0, match.view(4).spectatorId());
            }
        }
        assertEquals(Phase.RESULT, match.view(1).phase());
        assertEquals(Result.KNOCKOUT, match.view(1).result());
        assertEquals(0, match.view(1).winningTeam());
        while (match.view(1).ticksLeft() > 0) match.tick();
        for (int id = 1; id <= 4; id++) match.ready(id);
        assertEquals(Phase.COUNTDOWN, match.view(1).phase());
        assertTrue(match.view(1).fighters().stream().allMatch(f -> f.health() == 20 && f.remaining() == 31));
    }

    @Test void teamResultRemainsVisibleToSpectatorsAfterTheWinnersLeave() {
        var match = start(false);
        int spectator = match.join("Spectator");
        match.leave(1);
        assertEquals(Phase.FIGHT, match.view(spectator).phase());
        match.leave(3);
        assertEquals(Result.FORFEIT, match.view(spectator).result());
        assertEquals(1, match.view(spectator).winningTeam());
        match.leave(2);
        match.leave(4);
        var result = match.view(spectator);
        assertTrue(result.fighters().isEmpty());
        assertEquals(Phase.RESULT, result.phase());
        assertEquals(1, result.winningTeam());
    }

    @Test void offlineBothTeamsHaveBotsAndTheyEngage() {
        var match = start(true);
        assertEquals("Aster", fighter(match, 3).name());
        assertEquals(fighter(match, 1).side(), fighter(match, 3).side());
        for (int sequence = 1; sequence <= 200 && match.view(1).phase() == Phase.FIGHT; sequence++) {
            chase(match, 1, fighter(match, 2).health() > 0 ? 2 : 4, sequence);
            match.tick();
        }
        assertTrue(fighter(match, 3).hits() > 0, "The ally must engage");
        assertTrue(fighter(match, 2).hits() + fighter(match, 4).hits() > 0, "The opponents must engage");
    }

    private static PracticeMatch start(boolean offline) {
        var match = new PracticeMatch(Kit.TEAMFIGHTING_LAB, offline);
        if (offline) {
            match.ready(1);
        } else {
            for (int i = 1; i <= 4; i++) assertEquals(i, match.join("Fighter " + i));
            for (int i = 1; i <= 4; i++) match.ready(i);
        }
        while (match.view(1).phase() == Phase.COUNTDOWN) match.tick();
        assertEquals(Phase.FIGHT, match.view(1).phase());
        return match;
    }

    private static Fighter fighter(PracticeMatch match, int id) {
        return match.view(1).fighters().stream().filter(f -> f.id() == id).findFirst().orElseThrow();
    }

    private static void chase(PracticeMatch match, int attackerId, int targetId, int sequence) {
        Fighter attacker = fighter(match, attackerId), target = fighter(match, targetId);
        double dx = target.x() - attacker.x(), dz = target.z() - attacker.z();
        double range = Math.hypot(dx, dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
        float pitch = (float) Math.toDegrees(Math.atan2(target.y() - attacker.y() - .25, range));
        boolean click = sequence * 9 / 20 > (sequence - 1) * 9 / 20;
        match.input(attackerId, new Intent(sequence, yaw, pitch, range > 2.3 ? 1 : 0, 0,
                false, false, click, false, 0));
    }
}
