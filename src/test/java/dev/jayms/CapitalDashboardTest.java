package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

import dev.jayms.net.city.*;
import dev.jayms.ui.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class CapitalDashboardTest {
    CityFrame fixture() {
        var g = new CityTest.Ground();
        return new CitySimulation(
                        new GameConfig(true, false, 1200, 10),
                        g,
                        g.terrain,
                        null,
                        ProductionCatalog.toolEra())
                .frame();
    }

    @Test
    void requiresExplicitConfirmationAndAllowsCompanyAndPersonActors() {
        var f = fixture();
        var ui = new CapitalDashboard();
        var sent = new ArrayList<CityCommand>();
        ui.click(100, 355, 1280, 800, f, sent::add);
        assertTrue(sent.isEmpty());
        ui.click(100, 400, 1280, 800, f, sent::add);
        assertEquals(1, sent.size());
        assertEquals(
                f.economy().capital().book().listings().get(0).founder().id(),
                sent.get(0).capital().owner());
        assertEquals(0, sent.get(0).capital().action());
        ui.actorIndex = f.economy().capital().investors().size();
        ui.click(450, 355, 1280, 800, f, sent::add);
        ui.click(100, 400, 1280, 800, f, sent::add);
        assertEquals(0, sent.get(1).capital().ownerKind());
        assertEquals(1, sent.get(1).capital().action());
        ui.click(750, 355, 1280, 800, f, sent::add);
        ui.click(1000, 400, 1280, 800, f, sent::add);
        assertEquals(2, sent.size());
    }

    @Test
    void validatesExactDecimalEntryAndInvalidOrChangedDraftDoesNotSubmit() {
        var f = fixture();
        var ui = new CapitalDashboard();
        var sent = new ArrayList<CityCommand>();
        ui.click(800, 310, 1280, 800, f, sent::add);
        ui.price = "";
        for (char c : "1.25".toCharArray()) ui.character(c);
        assertEquals("1.25", ui.price);
        ui.key(GLFW_KEY_ENTER, GLFW_PRESS);
        ui.click(450, 355, 1280, 800, f, sent::add);
        ui.click(100, 400, 1280, 800, f, sent::add);
        assertEquals(125, sent.get(0).capital().price());
        ui.price = "1.001";
        ui.click(450, 355, 1280, 800, f, sent::add);
        ui.click(100, 400, 1280, 800, f, sent::add);
        assertEquals(1, sent.size());
        ui.price = "1.25";
        ui.click(450, 355, 1280, 800, f, sent::add);
        ui.click(1000, 150, 1280, 800, f, sent::add);
        ui.click(100, 400, 1280, 800, f, sent::add);
        assertEquals(1, sent.size());
    }

    @Test
    void marketNavigationKeepsCitizenSearchAndMarketInputIndependent() {
        var f = fixture();
        var ui = new MayorDashboard();
        ui.show();
        ui.click(1150, 100, 1280, 800, f, id -> fail());
        assertEquals(5, ui.tab);
        ui.click(100, 310, 1280, 800, f, id -> fail());
        assertTrue(ui.searchFocus);
        ui.character('5');
        assertEquals("1005", ui.capital.quantity);
        assertEquals("", ui.search);
        ui.key(GLFW_KEY_ENTER, GLFW_PRESS);
        assertFalse(ui.searchFocus);
    }
}
