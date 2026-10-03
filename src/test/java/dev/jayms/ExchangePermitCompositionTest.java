package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.city.CityCommand;
import dev.jayms.ui.CityTools;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class ExchangePermitCompositionTest {
    @Test
    void toolbarOffersCatalogAndExchangeWithIndependentPlacementCommands() {
        var fixture = new CityToolsTest();
        var tools = new CityTools();
        var commands = new ArrayList<CityCommand>();
        float width = (1280 - 32) / 9f;
        tools.click(16 + 7.5f * width, 534, 1280, 720,
                fixture.projection, fixture.view, fixture.frame, commands::add);
        assertEquals(6, tools.tool);
        fixture.click(tools, 0, 24, commands);
        assertEquals(CityCommand.SPECIAL, commands.get(0).kind());
        assertEquals(4, commands.get(0).value());
        tools.click(16 + 8.5f * width, 534, 1280, 720,
                fixture.projection, fixture.view, fixture.frame, commands::add);
        assertEquals(7, tools.tool);
        fixture.click(tools, 0, 24, commands);
        assertEquals(CityCommand.EXCHANGE, commands.get(1).kind());
        assertEquals(1, commands.get(1).points().size());
        assertEquals(-1, tools.tool);
    }
}
