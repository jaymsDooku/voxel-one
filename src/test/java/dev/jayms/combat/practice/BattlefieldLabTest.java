package dev.jayms.combat.practice;

import dev.jayms.combat.CombatEngine.Vec3;
import dev.jayms.combat.CombatGeometry;
import dev.jayms.combat.arena.BasaltBattlefield;
import dev.jayms.combat.arena.PracticeTerrain;
import dev.jayms.combat.practice.PracticeMatch.Fighter;
import dev.jayms.combat.practice.PracticeMatch.Kit;
import dev.jayms.combat.practice.PracticeMatch.Phase;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class BattlefieldLabTest {
    @Test void battlefieldHasSixtyDistinctSupportedSpawnsAndRealCover() {
        var terrain = PracticeTerrain.BATTLEFIELD;
        var positions = new HashSet<Vec3>();
        for (int team = 0; team < 2; team++) {
            for (int seat = 0; seat < 30; seat++) {
                var spawn = terrain.spawn(team, seat);
                var feet = new Vec3(spawn.x(), spawn.y(), spawn.z());
                assertTrue(positions.add(feet));
                assertTrue(terrain.contains(feet));
                assertTrue(terrain.solid((int) Math.floor(feet.x()), BasaltBattlefield.FLOOR_Y,
                        (int) Math.floor(feet.z())));
                assertNotNull(CombatGeometry.posePath(terrain, feet, feet, 1));
            }
        }
        assertEquals(60, positions.size());
        assertTrue(Double.isFinite(CombatGeometry.firstBlock(terrain, new Vec3(36.5, 74, 16.5), new Vec3(0, 0, 8))));
        assertFalse(Double.isFinite(CombatGeometry.firstBlock(terrain, new Vec3(.5, 74, 16.5), new Vec3(0, 0, 8))));
    }

    @Test void offlineFullRosterTradesAndUsesPotionsOnBothTeams() {
        var match = new PracticeMatch(Kit.TEAMFIGHTING_30V30, true);
        var lobby = match.view(1);
        assertEquals(60, lobby.fighters().size());
        assertEquals(59, lobby.fighters().stream().filter(Fighter::ready).count());
        for (int team = 0; team < 2; team++) {
            int side = team;
            assertEquals(30, lobby.fighters().stream().filter(f -> f.side() == side).count());
        }
        match.ready(1);
        while (match.view(1).phase() == Phase.COUNTDOWN) match.tick();
        for (int tick = 0; tick < 400; tick++) match.tick();
        var view = match.view(1);
        assertEquals(Phase.FIGHT, view.phase());
        assertEquals(60, view.fighters().size());
        assertTrue(view.fighters().stream().filter(f -> f.hits() > 0).count() >= 48);
        for (int team = 0; team < 2; team++) {
            int side = team;
            assertTrue(view.fighters().stream().anyMatch(f -> f.side() == side && f.used() > 0));
        }
        assertTrue(view.lastSplash() > 0);
    }
}
