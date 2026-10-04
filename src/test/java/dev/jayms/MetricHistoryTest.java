package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.city.*;
import dev.jayms.ui.*;

import org.junit.jupiter.api.Test;

class MetricHistoryTest {
    private CityFrame at(double elapsed) {
        var f = CityMetricsTest.fixture();
        return new CityFrame(
                f.config(),
                elapsed,
                f.roads(),
                f.zones(),
                f.buildings(),
                f.citizens(),
                f.horses(),
                f.economy(),
                f.addresses(),
                f.agriculture());
    }

    @Test
    void samplesAuthoritativeMetricsAcrossEveryDomainWithoutMutatingSnapshot() {
        var f = at(0);
        var h = new MetricHistory();
        h.observe(f);
        var values = h.samples().get(0).values();
        assertEquals(4.0, values.get("Population / All / population"));
        assertEquals(f.economy().budget(), values.get("Government / City / Treasury ($)"));
        assertTrue(values.keySet().stream().anyMatch(k -> k.startsWith("Companies / ")));
        assertTrue(values.keySet().stream().anyMatch(k -> k.startsWith("Workplaces / ")));
        assertEquals(
                CityMetrics.from(f).averageSavings(),
                values.get("Population / All / average Savings ($)"));
        assertThrows(UnsupportedOperationException.class, () -> values.put("bad", 1.0));
        assertEquals(f, at(0));
    }

    @Test
    void retainsChangingGovernmentBalancesAtTheirSimulationTimes() {
        var h = new MetricHistory();
        var f = at(0);
        h.observe(f);
        var e = f.economy();
        var changed =
                new CityEconomy.State(
                        -25,
                        100,
                        30,
                        e.rentClock(),
                        e.firms(),
                        e.plots(),
                        e.properties(),
                        e.contracts(),
                        e.businesses(),
                        e.resources());
        h.observe(
                new CityFrame(
                        f.config(),
                        10,
                        f.roads(),
                        f.zones(),
                        f.buildings(),
                        f.citizens(),
                        f.horses(),
                        changed,
                        f.addresses(),
                        f.agriculture()));
        var points = h.samples();
        assertEquals(0, points.get(0).elapsed());
        assertEquals(10, points.get(1).elapsed());
        assertEquals(e.budget(), points.get(0).values().get("Government / City / Treasury ($)"));
        assertEquals(-25, points.get(1).values().get("Government / City / Treasury ($)"));
        assertEquals(
                100,
                points.get(1).values().get("Government / City / Road spending ($, cumulative)"));
    }

    @Test
    void ignoresRepeatedFramesBoundsRetentionAndResetsOnClockRewind() {
        var h = new MetricHistory();
        h.observe(at(0));
        h.observe(at(0));
        h.observe(at(4));
        assertEquals(1, h.samples().size());
        h.observe(at(5));
        assertEquals(2, h.samples().size());
        for (int i = 2; i < 300; i++) h.observe(at(i * 5));
        assertEquals(MetricHistory.LIMIT, h.samples().size());
        assertEquals(300, h.samples().get(0).elapsed());
        h.observe(at(0));
        assertEquals(1, h.samples().size());
        h.observe(at(Double.NaN));
        assertEquals(1, h.samples().size());
    }

    @Test
    void exchangeAndTrendsKeepIndependentInputsAndHistory() {
        var ui = new MayorDashboard();
        ui.open = true;
        ui.submit = command -> fail("Trends must not submit capital commands");
        ui.history.observe(at(0));
        ui.history.observe(at(10));
        ui.click(1150, 100, 1280, 720, at(0), id -> fail());
        assertEquals(5, ui.tab);
        ui.capital.focus = 0;
        ui.capital.firstRow = 4;
        String quantity = ui.capital.quantity;
        ui.click(1040, 60, 1280, 720, at(0), id -> fail());
        assertTrue(ui.trends.open);
        ui.character('7');
        ui.scroll(-1);
        ui.key(org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        ui.click(350, 150, 1280, 720, at(0), id -> fail());
        assertEquals(quantity, ui.capital.quantity);
        assertEquals(4, ui.capital.firstRow);
        assertEquals(2, ui.history.samples().size());
        ui.click(1150, 100, 1280, 720, at(0), id -> fail());
        assertFalse(ui.trends.open);
        assertEquals(5, ui.tab);
        ui.character('7');
        assertEquals(quantity + "7", ui.capital.quantity);
    }

    @Test
    void trendsToggleDoesNotTriggerCitizenActionsOrChangeExistingTabs() {
        var ui = new MayorDashboard();
        ui.click(1040, 60, 1280, 720, at(0), id -> fail());
        assertTrue(ui.trends.open);
        ui.click(40, 150, 1280, 720, at(0), id -> fail());
        assertTrue(ui.trends.open);
        ui.click(530, 100, 1280, 720, at(0), id -> fail());
        assertFalse(ui.trends.open);
        assertEquals(2, ui.tab);
    }
}
