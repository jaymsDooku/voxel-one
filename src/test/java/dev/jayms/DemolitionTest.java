package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.BuildingInfo;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;

class DemolitionTest {
    CitySimulation city(CityTest.Ground ground) {
        var city =
                new CitySimulation(
                        GameConfig.cityGame(),
                        ground,
                        ground.terrain,
                        null,
                        ProductionCatalog.cityGame());
        for (int i = 0; i < 400; i++) city.advance(1);
        return city;
    }

    CityCommand demolish(int id) {
        return new CityCommand(CityCommand.DEMOLISH, id, List.of());
    }

    @Test
    void commandRoundTripsAndRejectsMalformedRequests() throws Exception {
        var bytes = new ByteArrayOutputStream();
        demolish(7).write(new DataOutputStream(bytes));
        assertEquals(
                demolish(7),
                CityCommand.read(
                        new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        assertThrows(IllegalArgumentException.class, () -> demolish(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CityCommand(CityCommand.DEMOLISH, 1, List.of(new Polygon.Point(0, 0))));
    }

    @Test
    void demolitionClearsStructuresReferencesAndSurvivesReload() throws Exception {
        var ground = new CityTest.Ground();
        var city = city(ground);
        var before = city.frame();
        assertTrue(before.buildings().stream().anyMatch(b -> b.type() == 3));
        for (var b : before.buildings()) {
            ground.apply(List.of(new Protocol.Edit(b.x() + 2, b.y() - 2, b.z() + 3, Blocks.STONE)));
            assertTrue(city.command(demolish(b.id()), 1, null).startsWith("Building demolished"));
            assertEquals(0, ground.type(b.x() + 1, b.y() + 3, b.z() + 1));
            assertEquals(Blocks.STONE, ground.type(b.x() + 2, b.y() - 2, b.z() + 3));
            assertEquals(
                    0,
                    ground.voxels.region(
                            Protocol.Edit.at(b.x() + .5, b.y() + 1.1, b.z() - .1, 0, 1)));
        }
        var after = city.frame();
        assertTrue(after.buildings().isEmpty());
        // Traffic can leave some planned plots unfinished. Demolishing built structures
        // removes their plots and projects while preserving unrelated construction.
        var remainingPlots =
                before.economy().plots().stream().filter(p -> p.building() == 0).toList();
        assertEquals(remainingPlots, after.economy().plots());
        assertTrue(after.economy().properties().isEmpty());
        assertTrue(after.economy().contracts().isEmpty());
        var remainingPlotIds = new HashSet<Integer>();
        for (var plot : remainingPlots) remainingPlotIds.add(plot.id());
        assertEquals(
                before.economy().resources().projects().stream()
                        .filter(p -> remainingPlotIds.contains(p.plot()))
                        .toList(),
                after.economy().resources().projects());
        assertTrue(after.agriculture().fields().isEmpty());
        assertTrue(after.agriculture().cows().isEmpty());
        assertTrue(after.agriculture().farms().isEmpty());
        assertTrue(after.citizens().stream().allMatch(c -> c.home() == 0 && c.job() <= 0));
        assertEquals(before.zones(), after.zones());
        var bytes = new ByteArrayOutputStream();
        after.write(new DataOutputStream(bytes));
        var saved =
                CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(after, saved);
        var restored = new CitySimulation(saved.config(), ground, ground.terrain, saved);
        restored.advance(1);
        assertTrue(
                city.command(demolish(before.buildings().get(0).id()), 1, null)
                        .contains("no longer"));
    }

    @Test
    void occupiedBuildingIsProtectedAndInspectorRequiresConfirmation() {
        var ground = new CityTest.Ground();
        var city = city(ground);
        var b = city.frame().buildings().get(0);
        ground.occupied = true;
        var before = city.frame();
        assertTrue(city.command(demolish(b.id()), 1, null).contains("players"));
        assertEquals(before, city.frame());
        var ui = new BuildingInfo();
        var sent = new ArrayList<CityCommand>();
        ui.show(b.id(), 0);
        ui.click(300, 630, 1280, 720, before, sent::add);
        assertTrue(sent.isEmpty());
        assertTrue(ui.confirmDemolition);
        ui.click(300, 630, 1280, 720, before, sent::add);
        assertEquals(List.of(demolish(b.id())), sent);
        assertFalse(ui.open);
        ui.show(0, 1);
        ui.click(300, 630, 1280, 720, before, sent::add);
        assertEquals(1, sent.size());
    }
}
