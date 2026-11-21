package dev.jayms.combat.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArenaCombatTraceTest {
    @Test void paperTravelAndClientPresentationStayWithinMeasuredBounds() {
        var run = ArenaCombatTrace.run(false, false);
        assertTrue(run.physicsError() <= 1e-5, "Regular hit physics");
        assertTrue(run.sprintError() <= 1e-5, "Sprint hit physics");
        for (int fps : new int[]{60, 144}) {
            int localPauses = 0, remotePauses = 0, active = 0, phases = 0;
            for (var replay : run.replays()) {
                if (replay.fps() != fps) continue;
                var local = replay.local();
                var remote = replay.remote();
                String scenario = " at " + fps + " fps, phase " + replay.phase();
                assertTrue(local.onset >= 0 && remote.onset >= 0, "Missing onset" + scenario);
                if (replay.phase() < 0) {
                    assertTrue(local.onset <= 34 && remote.onset <= 67, "Clean onset" + scenario);
                    assertTrue(local.maxError <= .24 && remote.maxError <= .00001, "Clean pose error" + scenario);
                    assertTrue(local.maxStep <= .15 && remote.maxStep <= .18, "Clean pose jump" + scenario);
                } else {
                    phases++;
                    active += local.movingFrames;
                    assertEquals(local.movingFrames, remote.movingFrames);
                    localPauses += local.pauses;
                    remotePauses += remote.pauses;
                    assertTrue(local.maxError <= .33 && remote.maxError <= .71, "Jitter pose error" + scenario);
                    assertTrue(local.maxStep <= .40 && remote.maxStep <= (fps == 60 ? .23 : .11), "Jitter pose jump" + scenario);
                }
            }
            assertEquals(8, phases);
            assertEquals(8 * (fps == 60 ? 38 : 94), active);
            // Pool equal-duration schedules. A missing first movement snapshot stalls phase zero longer.
            assertTrue(remotePauses / (double)active <= (fps == 60 ? .05 : .07), "Remote moving-window pauses at " + fps);
            assertTrue(localPauses / (double)active <= (fps == 60 ? .13 : .18), "Local moving-window pauses at " + fps);
        }
    }
}
