package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Fixtures were written by the exact pre-regional format-10 writer, not this implementation. */
class RegionalSaveCompatibilityTest {
    @TempDir Path temp;
    Path fixture(String name) throws Exception {
        Path file=temp.resolve(name);
        try(var in=getClass().getResourceAsStream("/city/"+name)) {
            assertNotNull(in);Files.copy(in,file);
        }
        return file;
    }
    @Test void exactBaseEmptyFormatTenLoadsWithoutRegionalTailAndUpgrades() throws Exception {
        var old=CitySimulation.load(fixture("base10-empty.city"));
        assertEquals(RegionalPopulation.State.empty(),old.population());
        assertTrue(old.roads().isEmpty());assertTrue(old.citizens().isEmpty());
        var ground=new CityTest.Ground();var simulation=new CitySimulation(old.config(),ground,ground.terrain,old);
        Path upgraded=temp.resolve("empty-upgraded.city");simulation.save(upgraded);
        try(var in=new DataInputStream(Files.newInputStream(upgraded))) {
            assertEquals(0x4349543B,in.readInt());
            assertEquals(simulation.frame(),CityFrame.read(in,11));assertEquals(-1,in.read());
        }
        assertEquals(simulation.frame(),CitySimulation.load(upgraded));
    }
    @Test void exactBasePavedFormatTenRetainsAllLaneTypesWithRegionalFormatEleven() throws Exception {
        var old=CitySimulation.load(fixture("base10-paved.city"));
        assertEquals(List.of(0,1,2,3),old.roads().stream().map(CityFrame.Road::type).toList());
        assertEquals(RegionalPopulation.State.empty(),old.population());
        var ground=new CityTest.Ground();var simulation=new CitySimulation(old.config(),ground,ground.terrain,old);
        assertEquals(old.roads(),simulation.frame().roads());
        simulation.command(new CityCommand(CityCommand.SETTLE_DISTRICT,1_000_000,List.of()),1,null);
        simulation.command(new CityCommand(CityCommand.FOCUS_DISTRICT,1,List.of()),1,null);
        Path upgraded=temp.resolve("paved-regional.city");simulation.save(upgraded);
        var loaded=CitySimulation.load(upgraded);
        assertEquals(old.roads(),loaded.roads());assertEquals(simulation.frame(),loaded);
        assertEquals(1_000_000,loaded.population().population());assertEquals(64,loaded.population().agents().size());
        var restored=new CitySimulation(loaded.config(),ground,ground.terrain,loaded);
        assertEquals(loaded.roads(),restored.frame().roads());
        assertEquals(loaded.population(),restored.frame().population());
        assertThrows(IOException.class,()->loaded.write(new DataOutputStream(new ByteArrayOutputStream()),10));
    }
    @Test void formatTenAndElevenHaveSeparateTailsAndTruncationStillFails() throws Exception {
        byte[] original=Files.readAllBytes(fixture("base10-paved.city"));
        try(var in=new DataInputStream(new ByteArrayInputStream(original))) {
            assertEquals(0x4349543A,in.readInt());var frame=CityFrame.read(in,10);
            assertEquals(-1,in.read());assertEquals(RegionalPopulation.State.empty(),frame.population());
        }
        Path truncated=temp.resolve("truncated10.city");Files.write(truncated,Arrays.copyOf(original,original.length-1));
        assertThrows(IOException.class,()->CitySimulation.load(truncated));
        var current=new ByteArrayOutputStream();var out=new DataOutputStream(current);
        out.writeInt(0x4349543B);CityFrame.empty(GameConfig.cityGame()).write(out);
        Path broken=temp.resolve("truncated11.city");Files.write(broken,Arrays.copyOf(current.toByteArray(),current.size()-1));
        assertThrows(IOException.class,()->CitySimulation.load(broken));
    }
    @Test void pavedBlockIdsValidateEncodeAndProduceWorldColors() {
        assertEquals(187,Blocks.ASPHALT);assertEquals(188,Blocks.ROAD_LINE_X);assertEquals(189,Blocks.ROAD_LINE_Z);
        var voxels=new WorldVoxels(new Terrain(42));
        for(int type:new int[]{187,188,189}) {
            var edit=new Protocol.Edit(type-180,30,24,type);
            assertTrue(Blocks.valid(type));assertTrue(edit.valid());
            assertDoesNotThrow(()->WorldVoxels.color(type));assertNotEquals(0,WorldVoxels.color(type));
            assertEquals(type,WorldVoxels.decode(WorldVoxels.encode(type)));
            voxels.apply(edit);assertEquals(type,voxels.type(edit.x(),edit.y(),edit.z()));
        }
    }
}
