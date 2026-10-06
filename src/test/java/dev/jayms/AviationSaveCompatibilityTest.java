package dev.jayms;

import dev.jayms.net.Protocol;
import dev.jayms.net.city.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class AviationSaveCompatibilityTest {
    @TempDir Path temp;
    @Test void actualReviewedBaseSaveRetainsAirportsRunwaysAndFlight() throws Exception {
        // Written by base 1ecbf565c449d7b5c96a621310c1a012b80ae1aa; not a fabricated header.
        Path save=Path.of("src/test/resources/base-v12-aviation-city.dat");
        try(var in=new DataInputStream(Files.newInputStream(save))) { assertEquals(0x4349543C,in.readInt()); }
        assertEquals(25,Protocol.VERSION);
        var state=CitySimulation.load(save);
        assertEquals(Files.readString(save.resolveSibling("base-v12-aviation-state.txt")).trim(),state.aviation().toString());
        assertEquals(2,state.buildings().stream().filter(b->b.type()==SpecialBuildings.AIRPORT).count());
        assertEquals(2,Aviation.runways(state.buildings().get(0)));
        assertEquals(1,state.aviation().flights().size());
        var bytes=new ByteArrayOutputStream(); state.write(new DataOutputStream(bytes));
        var network=CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(state,network,"Version13 network frame roundtrip from base version12 save");
        var fixture=AviationTest.fixture();
        var city=new CitySimulation(state.config(),fixture.ground(),fixture.terrain(),state);
        assertEquals(state.aviation(),city.frame().aviation());
        assertEquals(state.roads(),city.frame().roads());
        Path resave=temp.resolve("city.dat");city.save(resave);
        var restored=CitySimulation.load(resave);
        assertEquals(state.aviation(),restored.aviation());
        assertEquals(state.buildings(),restored.buildings());
        try(var in=new DataInputStream(Files.newInputStream(resave))) { assertEquals(0x4349543E,in.readInt()); }
    }
}
