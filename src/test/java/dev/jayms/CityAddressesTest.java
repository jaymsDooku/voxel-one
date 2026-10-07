package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.CityTools;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.util.*;

class CityAddressesTest {
    @TempDir Path directory;

    @Test
    void namesNumbersAndRoadExtensionsAreStableAcrossSaveAndNetwork() throws Exception {
        var g = new CityTest.Ground();
        var s = new CityTest().simulation(g);
        for (int i = 0; i < 180; i++) s.advance(1);
        var before = s.frame();
        assertEquals(
                List.of("Oak Road", "River Lane"),
                before.addresses().streets().stream().map(CityAddresses.Street::name).toList());
        assertEquals(before.buildings().size(), before.addresses().addresses().size());
        var names = new HashSet<String>();
        for (var b : before.buildings()) {
            String name = before.addresses().buildingName(b.id());
            assertTrue(name.matches("[1-9][0-9]* (Oak Road|River Lane)"), name);
            assertTrue(names.add(name));
        }
        var pose = new Protocol.Pose(1, 8, 40, 24, 0, 0);
        assertTrue(
                s.command(
                                new CityCommand(
                                        CityCommand.ROAD,
                                        0,
                                        List.of(
                                                new Polygon.Point(44, 24),
                                                new Polygon.Point(70, 24))),
                                1,
                                pose)
                        .contains("Oak Road"));
        assertEquals(before.addresses().addresses(), s.frame().addresses().addresses());
        assertEquals(2, s.frame().addresses().streets().size());
        Path save = directory.resolve("named.city");
        s.save(save);
        var saved = CitySimulation.load(save);
        assertEquals(s.frame(), saved);
        var bytes = new ByteArrayOutputStream();
        saved.write(new DataOutputStream(bytes));
        assertEquals(
                saved,
                CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        assertEquals(
                saved.addresses(),
                new CitySimulation(saved.config(), g, g.terrain, saved).frame().addresses());
        var tools = new CityTools();
        assertTrue(
                tools.selectRay(
                        new Vector3f(40.5f, 80, 24.5f), new Vector3f(0, -1, 0), 100, saved));
        assertEquals("Oak Road", saved.addresses().streetName(tools.selectedStreet));
    }

    @Test
    void oldCityFourMigratesWithoutChangingBuildingsCashOrClock() throws Exception {
        var g = new CityTest.Ground();
        var s = new CityTest().simulation(g);
        for (int i = 0; i < 180; i++) s.advance(1);
        Path save = directory.resolve("old.city");
        try (var out = new DataOutputStream(Files.newOutputStream(save))) {
            out.writeInt(0x43495434);
            s.frame().write(out, 4);
        }
        var old = CitySimulation.load(save);
        assertEquals(s.frame().buildings(), old.buildings());
        assertEquals(CityCapitalTest.withoutCapital(s.frame().economy()), old.economy());
        assertEquals(s.frame().elapsed(), old.elapsed());
        assertEquals(s.frame().addresses().streets(), old.addresses().streets());
        assertEquals(s.frame().addresses().addresses(), old.addresses().addresses());
        assertTrue(old.addresses().roadFootprints().stream().allMatch(RoadOwnership.Footprint::inferred));
        assertEquals(
                old.addresses(),
                new CitySimulation(old.config(), g, g.terrain, old).frame().addresses());
    }

    @Test
    void duplicateAddressesAndUnknownRoadsAreRejected() throws Exception {
        var buildings =
                List.of(
                        new CityFrame.Building(1, 1, 0, 0, 30, 0, 4, 0),
                        new CityFrame.Building(2, 1, 0, 8, 30, 0, 4, 0));
        var street =
                new CityAddresses.Street(
                        1,
                        "Oak Road",
                        List.of(new Polygon.Point(-10, 24), new Polygon.Point(44, 24)));
        for (var addresses :
                List.of(
                        List.of(
                                new CityAddresses.Address(1, 1, 1),
                                new CityAddresses.Address(2, 1, 1)),
                        List.of(
                                new CityAddresses.Address(1, 9, 1),
                                new CityAddresses.Address(2, 1, 2)))) {
            var bytes = new ByteArrayOutputStream();
            CityAddresses.write(
                    new DataOutputStream(bytes),
                    new CityAddresses.State(List.of(street), addresses));
            assertThrows(
                    IOException.class,
                    () ->
                            CityAddresses.read(
                                    new DataInputStream(
                                            new ByteArrayInputStream(bytes.toByteArray())),
                                    buildings));
        }
    }
}
