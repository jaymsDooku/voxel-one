package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

import dev.jayms.net.city.*;
import dev.jayms.ui.BuildingInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

class CapitalInspectorTest {
    @Test
    void exchangeDetailsKeepInspectorTabsNavigationAndCompleteWrappedText() {
        var seed = CityMetricsTest.fixture();
        var office = new CityFrame.Building(99, 0, SpecialBuildings.EXCHANGE, 50, 32, 52, 4, 0);
        var city = new CityFrame(seed.config(), seed.elapsed(), seed.roads(), seed.zones(),
                List.of(office), seed.citizens(), seed.horses(), seed.economy());
        var inspector = new BuildingInfo();
        inspector.show(99, 0);
        var overview = inspector.sectionLines(city);
        assertTrue(overview.stream().anyMatch(s -> s.contains("Stock exchange")));
        assertTrue(overview.stream().anyMatch(s -> s.contains("Graduate staff")));
        String details = overview.stream().filter(s -> s.startsWith("Four university")).findFirst().orElseThrow();
        var wrapped = BuildingInfo.wrappedLines(List.of(details), 20, s -> s.length());
        assertEquals(details.replace(" ", ""), String.join("", wrapped).replace(" ", ""));
        inspector.firstRow = 12;
        inspector.key(GLFW_KEY_RIGHT, GLFW_PRESS);
        assertEquals(1, inspector.tab);
        assertEquals(0, inspector.firstRow);
        inspector.key(GLFW_KEY_LEFT, GLFW_PRESS);
        assertEquals(overview, inspector.sectionLines(city));
        inspector.click(640, 100, 1280, 720);
        assertEquals(2, inspector.tab);
    }
}
