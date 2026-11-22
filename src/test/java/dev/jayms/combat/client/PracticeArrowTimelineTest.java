package dev.jayms.combat.client;

import dev.jayms.combat.CombatEngine.Item;
import dev.jayms.combat.practice.PracticeMatch.Missile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PracticeArrowTimelineTest {
    @Test void interpolatesFlightToConfirmedImpactAndThenKeepsTheEmbeddedArrowStationary() {
        var arrows = new PracticeArrowTimeline();
        arrows.observe(30, List.of(arrow(3, true, false)), 0);
        assertEquals(3, arrows.sample(0).get(0).x());
        assertTrue(arrows.sample(0).get(0).critical());
        arrows.observe(31, List.of(arrow(5, false, true)), 50_000_000);
        assertEquals(4, arrows.sample(75_000_000).get(0).x());
        assertTrue(arrows.sample(75_000_000).get(0).embedded());
        assertEquals(5, arrows.sample(200_000_000).get(0).x());
        assertEquals(2, arrows.sample(200_000_000).get(0).vx());
        arrows.observe(32, List.of(), 200_000_000);
        assertTrue(arrows.sample(200_000_000).isEmpty());
    }

    @Test void aLegacyFlightCanInferDirectionFromConsecutiveCoordinates() {
        var arrows = new PracticeArrowTimeline();
        arrows.observe(1, List.of(new Missile(4, Item.ARROW, 5, 5, 5)), 0);
        arrows.observe(2, List.of(new Missile(4, Item.ARROW, 5, 4, 7)), 50_000_000);
        var presented = arrows.sample(75_000_000).get(0);
        assertEquals(-1, presented.vy());
        assertEquals(2, presented.vz());
        assertEquals(6, presented.z());
    }

    private static Missile arrow(float x, boolean critical, boolean embedded) {
        return new Missile(1, Item.ARROW, x, 3, 4, 2, 0, 0, critical, embedded);
    }
}
