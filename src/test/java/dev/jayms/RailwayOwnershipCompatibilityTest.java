package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Literal ownership suffix from reviewed base format 13; no railway writer used. */
class RailwayOwnershipCompatibilityTest {
    @TempDir Path temp;
    private Path baseSave(CityFrame frame, String name) throws Exception {
        Path file = temp.resolve(name);
        try (var out = new DataOutputStream(Files.newOutputStream(file))) {
            out.writeInt(0x4349543D);
            frame.write(out, 12);
            var owners = RoadOwnership.forFrame(frame);
            out.writeInt(owners.size());
            for (var owner : owners) {
                out.writeInt(owner.street()); out.writeBoolean(owner.inferred());
                out.writeInt(owner.cells().size());
                for (var c : owner.cells()) {
                    out.writeInt(c.x()); out.writeInt(c.z()); out.writeByte(c.type());
                    out.writeInt(c.surface()); out.writeLong(c.paint());
                }
            }
        }
        return file;
    }
    @Test void exactEmptyBaseSuffixLoadsWithoutRailwayBytes() throws Exception {
        var loaded = CitySimulation.load(baseSave(CityFrame.empty(GameConfig.cityGame()), "empty.city"));
        assertEquals(Railway.State.empty(), loaded.railway());
        assertTrue(loaded.addresses().roadFootprints().isEmpty());
    }
    @Test void ownedRoadsLoadResaveAndRetainEditDeleteCommands() throws Exception {
        var ground = new CityTest.Ground();
        var city = new CityTest().simulation(ground);
        for (int type = 0; type < 4; type++) {
            int z = 120 + type * 16;
            assertTrue(city.command(new CityCommand(CityCommand.ROAD, type,
                    List.of(new Polygon.Point(120,z),new Polygon.Point(140,z))),1,null).contains("built"));
        }
        var original = city.frame();
        var loaded = CitySimulation.load(baseSave(original, "owned.city"));
        assertEquals(original.roads(), loaded.roads());
        assertEquals(original.addresses().roadFootprints(), loaded.addresses().roadFootprints());
        assertEquals(Railway.State.empty(), loaded.railway());
        var restored = new CitySimulation(loaded.config(), ground, ground.terrain, loaded);
        Path saved = temp.resolve("migrated.city"); restored.save(saved);
        try (var in = new DataInputStream(Files.newInputStream(saved))) {
            assertEquals(0x4349543E, in.readInt());
            var current = CityFrame.read(in,14);
            assertEquals(original.addresses().roadFootprints(), current.addresses().roadFootprints());
            assertEquals(original.roads(), current.roads()); assertEquals(-1,in.read());
        }
        assertEquals(12,CityCommand.DELETE_ROAD); assertEquals(13,CityCommand.EDIT_ROAD);
        assertEquals(14,CityCommand.RAIL); assertEquals(26,Protocol.VERSION);
        int street = loaded.addresses().roadFootprints().get(0).street();
        assertTrue(restored.command(new CityCommand(CityCommand.EDIT_ROAD,street,
                List.of(new Polygon.Point(1,0))),1,null).startsWith("Road section edited:"));
        assertEquals("Road section deleted",restored.command(new CityCommand(CityCommand.DELETE_ROAD,street,List.of()),1,null));
    }
}
