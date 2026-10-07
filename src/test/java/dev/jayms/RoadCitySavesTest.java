package dev.jayms;

import dev.jayms.net.CitySaves;
import dev.jayms.net.LocalGame;
import dev.jayms.net.city.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RoadCitySavesTest {
    @TempDir Path folder;

    @Test void copiedRoadOwnershipCanBeEditedWithoutChangingOriginal() throws Exception {
        Path original = folder.resolve("offline-city.dat");
        new LocalGame(original, 42).save();
        var ground = new CityTest.Ground();
        var city = new CityTest().simulation(ground);
        assertTrue(city.command(new CityCommand(CityCommand.ROAD, 3,
                List.of(new Polygon.Point(90, 90), new Polygon.Point(110, 90))), 1, null).contains(" built:"));
        assertTrue(city.command(new CityCommand(CityCommand.ROAD, 1,
                List.of(new Polygon.Point(100, 80), new Polygon.Point(100, 100))), 1, null).contains(" built:"));
        city.save(CitySaves.sidecar(original, ".city"));
        var before = CitySimulation.load(CitySaves.sidecar(original, ".city"));
        var saves = new CitySaves(original);
        Path copy = saves.create("Road copy", original, 99);
        var loaded = CitySimulation.load(CitySaves.sidecar(copy, ".city"));
        assertEquals(before, loaded);
        var restored = new CitySimulation(loaded.config(), ground, ground.terrain, loaded);
        int id = loaded.addresses().nearest(95, 90).id();
        assertTrue(restored.command(new CityCommand(CityCommand.EDIT_ROAD, id,
                List.of(new Polygon.Point(1, 0))), 1, null).contains("edited"));
        assertEquals("Road section deleted", restored.command(
                new CityCommand(CityCommand.DELETE_ROAD, id, List.of()), 1, null));
        restored.save(CitySaves.sidecar(copy, ".city"));
        var after = CitySimulation.load(CitySaves.sidecar(copy, ".city"));
        assertEquals(RoadGeometry.surfaces(List.of(new Polygon.Point(100, 80),
                new Polygon.Point(100, 100)), 1).keySet(),
                after.roads().stream().filter(r -> r.x() > 85 && r.z() > 75)
                        .map(r -> new Polygon.Cell(r.x(), r.z())).collect(java.util.stream.Collectors.toSet()));
        assertEquals(before, CitySimulation.load(CitySaves.sidecar(original, ".city")));
        assertThrows(java.io.IOException.class, () -> saves.create("road copy", copy, 0));
        assertEquals(after, CitySimulation.load(CitySaves.sidecar(copy, ".city")));
        Path fresh = saves.create("Fresh city", null, 99);
        assertFalse(Files.exists(CitySaves.sidecar(fresh, ".city")));
        CitySaves.validate(copy, 0);
        assertEquals(3, saves.list().size());
    }
}
