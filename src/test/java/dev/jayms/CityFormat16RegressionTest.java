package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.net.city.parcel.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CityFormat16RegressionTest {
    @TempDir Path temp;

    @Test void version16HeaderLoadsAndResavesParcelLayoutWithoutLoss() throws Exception {
        var polygon=CityTest.box(80,82,24,24);
        var layout=ParcelPortfolio.Algorithm.GRID.generate(new ParcelGenerator.Request(
                polygon.cells(),Set.of(),144,42,c->1,List.of()));
        var zone=new CityFrame.Zone(1,0,polygon,ParcelPortfolio.Algorithm.GRID.ordinal(),layout);
        var frame=new CityFrame(GameConfig.cityGame(),12,List.of(),List.of(zone),List.of(),List.of(),List.of());
        var path=temp.resolve("version16.city");
        try(var out=new DataOutputStream(Files.newOutputStream(path))) {
            out.writeInt(0x43495440); frame.write(out,16);
        }
        var loaded=CitySimulation.load(path);
        assertEquals(frame,loaded);
        assertEquals(layout,loaded.zones().get(0).parcels());
        var ground=new CityTest.Ground();
        var sim=new CitySimulation(loaded.config(),ground,ground.terrain,loaded);
        sim.save(path);
        try(var in=new DataInputStream(Files.newInputStream(path))) { assertEquals(0x43495441,in.readInt()); }
        assertEquals(sim.frame(),CitySimulation.load(path));
        assertEquals(28,Protocol.VERSION);
    }

    @Test void version16StressSnapshotAndOlderStressSaveBothLoad() throws Exception {
        var store=new CitySaves(temp.resolve("original.dat"));
        var world=store.createStressGrid(42);
        var original=CitySimulation.load(CitySaves.sidecar(world,".city"));
        var path=temp.resolve("stress-v16.city");
        try(var out=new DataOutputStream(Files.newOutputStream(path))) {
            out.writeInt(0x43495440);original.write(out,16);
        }
        var loaded=CitySimulation.load(path);
        assertEquals(original.stressGrid(),loaded.stressGrid());
        assertEquals(1_000_000,loaded.zones().size());
        assertEquals(original,loaded);
        try(var in=new DataInputStream(Files.newInputStream(CitySaves.sidecar(world,".city")))) {
            assertEquals(0x4349543F,in.readInt());
        }
        assertEquals(original,CitySimulation.load(CitySaves.sidecar(world,".city")));
    }
}
