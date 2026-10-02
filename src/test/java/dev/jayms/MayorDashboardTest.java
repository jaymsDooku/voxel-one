package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

import dev.jayms.ui.*;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.*;

class MayorDashboardTest {
    @Test
    void filtersSortAndSearchExposeCitizensWithActualNeeds() {
        var f = CityMetricsTest.fixture();
        var ui = new MayorDashboard();
        assertEquals(List.of(1, 2, 4, 3), ui.rows(f).stream().map(c -> c.id()).toList());
        ui.filter = 2;
        assertEquals(List.of(1, 2), ui.rows(f).stream().map(c -> c.id()).toList());
        ui.filter = 3;
        assertEquals(List.of(2, 4), ui.rows(f).stream().map(c -> c.id()).toList());
        ui.filter = 4;
        assertEquals(2, ui.rows(f).size());
        ui.filter = 1;
        assertEquals(3, ui.rows(f).size());
        ui.filter = 0;
        ui.sortBySavings = true;
        assertEquals(List.of(1, 3, 2, 4), ui.rows(f).stream().map(c -> c.id()).toList());
        ui.search = "SKILLED";
        assertEquals("Sam", ui.rows(f).get(0).name());
        ui.search = "obstructed";
        assertEquals("Robin", ui.rows(f).get(0).name());
        ui.search = "unknown";
        assertTrue(ui.rows(f).isEmpty());
    }

    @Test
    void dashboardTabsSearchInputScrollingAndCloseAreReadOnly() {
        var f = CityMetricsTest.fixture();
        var ui = new MayorDashboard();
        ui.show();
        ui.click(640, 100, 1280, 720, f, id -> fail("Tab must not inspect citizen"));
        assertEquals(2, ui.tab);
        ui.click(40, 190, 1280, 720, f, id -> fail());
        assertTrue(ui.searchFocus);
        ui.character('A');
        ui.character('l');
        assertEquals("Al", ui.search);
        ui.key(GLFW_KEY_BACKSPACE, GLFW_PRESS);
        assertEquals("A", ui.search);
        ui.key(GLFW_KEY_ENTER, GLFW_PRESS);
        assertFalse(ui.searchFocus);
        ui.scroll(-1);
        assertEquals(3, ui.firstRow);
        ui.scroll(1);
        assertEquals(0, ui.firstRow);
        ui.key(GLFW_KEY_PAGE_DOWN, GLFW_PRESS);
        assertEquals(8, ui.firstRow);
        ui.key(GLFW_KEY_PAGE_UP, GLFW_PRESS);
        assertEquals(0, ui.firstRow);
        ui.click(1200, 60, 1280, 720, f, id -> fail());
        assertFalse(ui.open);
        assertEquals(f, CityMetricsTest.fixture());
    }

    @Test
    void planningDashboardButtonDoesNotCreateWorldCommands() {
        var tools = new CityTools();
        tools.tool = 3;
        var commands = new ArrayList<dev.jayms.net.city.CityCommand>();
        tools.click(
                1180,
                85,
                1280,
                720,
                new Matrix4f(),
                new Matrix4f(),
                CityMetricsTest.fixture(),
                commands::add);
        assertTrue(tools.dashboardRequested);
        assertTrue(commands.isEmpty());
        assertEquals(3, tools.tool);
    }
}
