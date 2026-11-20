package dev.jayms.combat.client;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ArenaSprintToggleTest {
    @Test void shortTapsToggleAndIgnoringKeyRepeatKeepsTheOriginalStartTime() {
        var sprint = new ArenaSprintToggle();
        assertFalse(sprint.requested());
        sprint.press(0);
        sprint.press(ms(200));
        sprint.release(ms(300), false, -1);
        assertTrue(sprint.requested());
        assertEquals("Sprint (Toggled)", sprint.status());
        sprint.stopRun();
        assertTrue(sprint.requested(), "Releasing forward does not clear toggle sprint");
        sprint.press(ms(400));
        sprint.release(ms(420), true, -1);
        assertFalse(sprint.requested());
    }

    @Test void aLongHoldFinishesOnlyTheRunAlreadyUnderway() {
        var sprint = new ArenaSprintToggle();
        sprint.press(0);
        sprint.press(ms(200));
        sprint.release(ms(301), true, -1);
        assertTrue(sprint.requested());
        assertTrue(sprint.continuingRun());
        sprint.stopRun();
        assertFalse(sprint.requested());
        sprint.press(ms(500));
        sprint.release(ms(801), false, -1);
        assertFalse(sprint.requested(), "A hold while idle does not arm the next run");
    }

    @Test void suspensionPreservesACompletedTapAndCancelsAnUnfinishedHold() {
        var sprint = new ArenaSprintToggle();
        sprint.press(0);
        sprint.suspend();
        sprint.release(ms(100), true, -1);
        assertFalse(sprint.requested());
        sprint.press(ms(150));
        sprint.release(ms(200), false, -1);
        sprint.suspend();
        assertTrue(sprint.requested());
        sprint.press(ms(220));
        assertFalse(sprint.requested());
        sprint.suspend();
        sprint.release(ms(240), false, -1);
        assertTrue(sprint.requested(), "Cancelling an unfinished second gesture restores the earlier toggle");
    }

    @Test void tappingAgainCancelsAContinuingRun() {
        var sprint = new ArenaSprintToggle();
        sprint.press(0);
        sprint.release(ms(500), true, -1);
        sprint.press(ms(510));
        sprint.release(ms(530), true, -1);
        assertFalse(sprint.requested());
    }

    @Test void aNewWallContactEndsAContinuingRunEvenAfterTheCollisionFrameHasPassed() {
        var sprint = new ArenaSprintToggle();
        sprint.press(0);
        sprint.release(ms(301), true, 10);
        assertFalse(sprint.updateRun(true, 10));
        assertTrue(sprint.requested());
        assertTrue(sprint.updateRun(true, 14));
        assertFalse(sprint.requested());
    }

    private static long ms(long milliseconds) { return TimeUnit.MILLISECONDS.toNanos(milliseconds); }
}
