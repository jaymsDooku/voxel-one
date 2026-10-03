package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

import dev.jayms.ui.*;

import org.junit.jupiter.api.Test;

class BusinessDashboardTest {
    @Test
    void businessFiltersSearchAndSelectionUseActualLocations() {
        var f = CityMetricsTest.fixture();
        var ui = new BusinessDashboard();
        assertEquals(2, ui.rows(f).size());
        ui.filter = 2;
        assertEquals(1, ui.rows(f).size());
        assertEquals(1, ui.rows(f).get(0).building().type());
        ui.filter = 3;
        assertEquals(2, ui.rows(f).get(0).building().type());
        ui.filter = 0;
        ui.search = "MINING";
        assertEquals(1, ui.rows(f).size());
        ui.search = "not-a-business";
        assertTrue(ui.rows(f).isEmpty());
    }

    @Test
    void newMayorBusinessTabKeepsPopulationNavigationAndTextInputSeparate() {
        var f = CityMetricsTest.fixture();
        var ui = new MayorDashboard();
        ui.show();
        ui.click(960, 100, 1280, 720, f, id -> fail());
        assertEquals(4, ui.tab);
        ui.click(100, 230, 1280, 720, f, id -> fail());
        assertTrue(ui.searchFocus);
        ui.character('M');
        assertEquals("M", ui.businesses.search);
        assertEquals("", ui.search);
        ui.key(GLFW_KEY_ENTER, GLFW_PRESS);
        assertFalse(ui.searchFocus);
        ui.click(530, 100, 1280, 720, f, id -> fail());
        assertEquals(2, ui.tab);
        ui.click(960, 100, 1280, 720, f, id -> fail());
        ui.click(100, 230, 1280, 720, f, id -> fail());
        ui.close();
        assertFalse(ui.businesses.searchFocus);
    }

    @Test
    void companyViewAndScrollRemainReadOnly() {
        var f = CityMetricsTest.fixture();
        var ui = new BusinessDashboard();
        ui.click(1100, 150, 1280, 720);
        assertEquals(1, ui.view);
        ui.scroll(-1);
        assertEquals(2, ui.firstRow);
        ui.key(GLFW_KEY_PAGE_UP, GLFW_PRESS);
        assertEquals(0, ui.firstRow);
        ui.click(40, 150, 1280, 720);
        assertEquals(0, ui.view);
        ui.click(400, 190, 1280, 720);
        assertEquals(1, ui.filter);
        assertEquals(f, CityMetricsTest.fixture());
    }
}
