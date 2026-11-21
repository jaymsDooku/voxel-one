package dev.jayms.combat.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaAutoClickerTest {
    @Test void ninePerSecondAtDifferentRenderRates() {
        for (int fps : new int[]{20, 30, 60, 144}) {
            var clicks = new ArenaAutoClicker();
            assertFalse(clicks.poll(0, true));
            clicks.toggle();
            int edges = 0;
            for (long frame = 0; frame < fps * 10; frame++)
                if (clicks.poll(frame * 1_000_000_000L / fps, true)) edges++;
            assertEquals(90, edges);
        }
    }

    @Test void disabledAndSuspendedClocksNeverReplayMissedClicks() {
        var clicks = new ArenaAutoClicker();
        clicks.toggle();
        assertTrue(clicks.poll(0, true));
        assertTrue(clicks.poll(5_000_000_000L, true));
        assertFalse(clicks.poll(5_000_000_000L, true));
        assertFalse(clicks.poll(5_100_000_000L, false));
        assertTrue(clicks.poll(6_000_000_000L, true));
        assertFalse(clicks.poll(6_100_000_000L, true));
        clicks.toggle();
        assertFalse(clicks.poll(8_000_000_000L, true));
    }
}
