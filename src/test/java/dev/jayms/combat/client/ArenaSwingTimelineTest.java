package dev.jayms.combat.client;

import dev.jayms.combat.CombatEngine.Item;
import dev.jayms.combat.practice.PracticeMatch;
import dev.jayms.combat.practice.PracticeMatch.Fighter;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ArenaSwingTimelineTest {
    private final ArenaMotionTimeline timeline = new ArenaMotionTimeline();

    @Test void repeatedClicksAdvanceFractionallyAndRestartAtHalfDuration() {
        long start = -1;
        for (long tick = 10; tick <= 15; tick++) {
            start = PracticeMatch.restartSwing(start, tick);
            assertEquals(tick < 13 ? 10 : 13, start);
            observe(tick, start, true, 1, tick * 50);
            assertEquals((tick + .5 - start) / 6, phase(tick + .5), .00001);
        }
        var stalled = timeline.frame(2_000_000_000L);
        assertEquals(1, timeline.sample(1, stalled.remote()).swingProgress());
        observe(16, 13, true, 1, 2100);
        assertEquals(1, phase(16));
    }

    @Test void legacyPeersAnimateOnRisingEdgesAndEpochChangesDiscardOldStarts() {
        observe(20, -1, true, 1, 1000);
        observe(21, -1, true, 1, 1050);
        assertEquals(.25, phase(21.5), .00001);
        observe(22, -1, false, 1, 1100);
        observe(23, -1, true, 1, 1150);
        assertEquals(.5 / 6, phase(23.5), .00001);
        observe(24, 23, true, 2, 1200);
        assertEquals(1, phase(24.5));
    }

    private float phase(double tick) {
        return timeline.sample(1, new ArenaMotionTimeline.Cursor(tick, tick)).swingProgress();
    }

    @Test void lateConfirmationStartsFreshCuesAndPreservesExpiredAndHistoricalCues() {
        timeline.observe(100, List.of(cues(1, 94), cues(2, 94)), 0);
        var old = timeline.frame(249_000_000).remote();
        assertEquals(0, timeline.sample(1, old).hurt());
        assertEquals(1, timeline.sample(1, old).swingProgress());
        timeline.observe(101, List.of(cues(1, 101), cues(2, 94), cues(3, 99)), 249_500_000);
        var fresh = timeline.frame(249_500_000).remote();
        assertEquals(1, timeline.sample(1, fresh).hurt());
        assertEquals(0, timeline.sample(1, fresh).swingProgress());
        assertEquals(0, timeline.sample(2, fresh).hurt());
        assertEquals(1, timeline.sample(2, fresh).swingProgress());
        assertEquals(2.0 / 6, timeline.sample(3, fresh).swingProgress(), .00001);
        assertEquals(1, timeline.sample(1, old).swingProgress());
        assertEquals(1, phase(100));
        assertEquals(0, phase(101));
        var later = timeline.frame(399_500_000).remote();
        assertEquals(.5, timeline.sample(1, later).swingProgress(), .00001);
        assertEquals(.875, timeline.sample(1, later).hurt(), .00001);
        var ended = timeline.frame(549_500_000).remote();
        assertEquals(1, timeline.sample(1, ended).swingProgress());
        assertEquals(.5, timeline.sample(1, ended).hurt(), .00001);
        assertEquals(0, timeline.sample(1, timeline.frame(749_500_000).remote()).hurt());
    }

    private Fighter cues(int id, long start) {
        return new Fighter(id, "fighter", 0, 73, 0, 0, 0, 20, false, true, true, Item.SWORD,
                0, 0, 0, 0, -1, "", 0, 0, 0, start, 1, -1, -1, 0, 0, 0, start);
    }

    private void observe(long tick, long start, boolean swinging, long epoch, long millis) {
        var fighter = new Fighter(1, "fighter", 0, 73, 0, 0, 0, 20, false, swinging, true, Item.SWORD,
                0, 0, 0, 0, -1, "", 0, 0, 0, -1, epoch, -1, -1, 0, 0, 0, start);
        timeline.observe(tick, List.of(fighter), millis * 1_000_000);
    }
}
