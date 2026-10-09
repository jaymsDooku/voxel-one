package dev.jayms;

import dev.jayms.net.Protocol;
import dev.jayms.net.city.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Regression checks for the reviewed aviation base's format-12 saves and protocol-22 IDs. */
class RoadBaseCompatibilityTest {
    @TempDir Path temp;

    @Test void protocol22AviationPacketsKeepTheirMeaningAndAlignment() throws Exception {
        assertEquals(28,Protocol.VERSION);
        assertEquals(10,CityCommand.RUNWAY); assertEquals(11,CityCommand.FLIGHT);
        assertEquals(12,CityCommand.DELETE_ROAD); assertEquals(13,CityCommand.EDIT_ROAD);
        var bytes=new ByteArrayOutputStream(); var out=new DataOutputStream(bytes);
        // Literal packet layouts from the reviewed protocol-22 base.
        out.writeByte(10); out.writeInt(7); out.writeByte(0);
        out.writeByte(11); out.writeInt(8); out.writeByte(0); out.writeByte(0); out.writeInt(9);
        out.writeInt(0x12345678);
        var in=new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        var runway=CityCommand.read(in); var flight=CityCommand.read(in);
        assertEquals(CityCommand.RUNWAY,runway.kind()); assertEquals(7,runway.value());
        assertEquals(CityCommand.FLIGHT,flight.kind()); assertEquals(8,flight.value());
        assertEquals(9,flight.ownerId()); assertEquals(0x12345678,in.readInt());
        for(var c:List.of(runway,flight,new CityCommand(CityCommand.DELETE_ROAD,7,List.of()),
                new CityCommand(CityCommand.EDIT_ROAD,7,List.of(new Polygon.Point(3,0))))) {
            bytes.reset(); c.write(new DataOutputStream(bytes));
            assertEquals(c,CityCommand.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        }
    }

    @Test void reviewedBaseFormat12AirportSaveLoadsAndResaves() throws Exception {
        var fixture=AviationTest.fixture(); var city=fixture.city();
        assertTrue(AviationTest.command(city,AviationTest.permit(50)).contains("Permitted"));
        var frame=city.frame(); var file=temp.resolve("base-format12.city");
        try(var out=new DataOutputStream(Files.newOutputStream(file))) {
            out.writeInt(0x4349543C); frame.write(out,12);
        }
        var loaded=CitySimulation.load(file);
        assertEquals(frame.roads(),loaded.roads());
        assertEquals(frame.buildings(),loaded.buildings());
        assertEquals(frame.aviation(),loaded.aviation());
        var restored=new CitySimulation(loaded.config(),fixture.ground(),fixture.terrain(),loaded);
        var saved=temp.resolve("restored.city"); restored.save(saved);
        try(var in=new DataInputStream(Files.newInputStream(saved))) { assertEquals(0x43495441,in.readInt()); }
        assertEquals(loaded.buildings(),CitySimulation.load(saved).buildings());
        assertEquals(loaded.aviation(),CitySimulation.load(saved).aviation());
    }
}
