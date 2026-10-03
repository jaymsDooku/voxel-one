package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

import dev.jayms.ui.BuildingInfo;
import org.junit.jupiter.api.Test;
import java.util.List;

class BuildingInfoTest {
    @Test
    void overviewStaysShortWhileResidentsAndInventoriesRemainAccessible() {
        var city = CityMetricsTest.fixture();
        var ui = new BuildingInfo();
        ui.show(1, 0);
        var overview = ui.sectionLines(city);
        assertTrue(overview.stream().anyMatch(s -> s.startsWith("Residents: 2 /")));
        assertFalse(overview.stream().anyMatch(s -> s.contains("Working in mine")));
        assertFalse(overview.stream().anyMatch(s -> s.contains("MATERIALS")));
        ui.key(GLFW_KEY_RIGHT, GLFW_PRESS);
        assertTrue(ui.sectionLines(city).stream().anyMatch(s -> s.contains("Alex")));
        assertTrue(ui.sectionLines(city).stream().anyMatch(s -> s.contains("Sam")));
        ui.tab = 3;
        assertTrue(ui.sectionLines(city).stream().anyMatch(s -> s.contains("OWNER MATERIALS")));
        ui.tab = 4;
        assertTrue(ui.sectionLines(city).stream().anyMatch(s -> s.contains("historical construction materials")));
    }

    @Test
    void tabsResetScrollSupportMouseAndKeyboardAndReopeningResetsSelection() {
        var ui = new BuildingInfo();
        ui.show(2, 0);
        ui.firstRow = 99;
        ui.click(640, 100, 1280, 720);
        assertEquals(2, ui.tab);
        assertEquals(0, ui.firstRow);
        assertTrue(ui.sectionLines(CityMetricsTest.fixture()).stream().anyMatch(s -> s.contains("Mining Co")));
        ui.tab = 0;
        ui.key(GLFW_KEY_LEFT, GLFW_PRESS);
        assertEquals(4, ui.tab);
        ui.key(GLFW_KEY_TAB, GLFW_PRESS);
        assertEquals(0, ui.tab);
        ui.show(1, 0);
        assertEquals(0, ui.tab);
        assertEquals(0, ui.firstRow);
        ui.click(950, 50, 1280, 720);
        assertFalse(ui.open);
    }

    @Test
    void unavailablePropertiesAndEmptySectionsHaveClearMessages() {
        var ui = new BuildingInfo();
        ui.show(999, 0);
        ui.tab = 4;
        assertEquals(List.of("This property is no longer available."), ui.sectionLines(CityMetricsTest.fixture()));
        ui.show(3, 0);
        ui.tab = 1;
        assertEquals(List.of("No people details for this property."), ui.sectionLines(CityMetricsTest.fixture()));
    }

    @Test
    void specialBuildingsRetainNamesLevelsAndDistinctOwnershipKinds() {
        var city = CityMetricsTest.fixture();
        int[] zones = {0, -1, -2};
        int[] owners = {0, 1, 19};
        String[] names = {"City government", "Alex", "Mining Co"};
        for (int i = 0; i < zones.length; i++) {
            var building = new dev.jayms.net.city.CityFrame.Building(99, zones[i],
                    dev.jayms.net.city.SpecialBuildings.type(1, 2), 10, 24, 10, 0, owners[i]);
            var frame = new dev.jayms.net.city.CityFrame(city.config(), city.elapsed(),
                    city.roads(), city.zones(), List.of(building), city.citizens(), city.horses(),
                    city.economy(), city.addresses(), city.agriculture());
            var ui = new BuildingInfo();
            ui.show(99, 0);
            assertTrue(ui.sectionLines(frame).contains("Primary school level 2 building #99"));
            assertTrue(ui.sectionLines(frame).contains("Owner: " + names[i]));
            assertTrue(ui.sectionLines(frame).contains("Level: 2"));
        }
    }

    @Test
    void wrappingPreservesLongNamesAndAllDetails() {
        assertEquals(List.of("Property", "value $100", "| Rent $2"),
                BuildingInfo.wrappedLines(List.of("Property value $100 | Rent $2"), 10, String::length));
        var parts = BuildingInfo.wrappedLines(List.of("VeryLongUnbrokenName"), 5, String::length);
        assertEquals("VeryLongUnbrokenName", String.join("", parts));
        assertTrue(parts.stream().allMatch(s -> s.length() <= 5));
        var indented = BuildingInfo.wrappedLines(List.of("  VeryLongUnbrokenName"), 5, String::length);
        assertTrue(indented.stream().noneMatch(String::isBlank));
        assertEquals("VeryLongUnbrokenName", String.join("", indented).stripLeading());
    }
}
