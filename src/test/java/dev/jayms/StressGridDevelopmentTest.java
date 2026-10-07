package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StressGridDevelopmentTest {
    @TempDir Path dir;
    @Test void explicitBenchmarkExpansionIsPopulatedPersistentAndRepeatSafe() throws Exception {
        var store = new CitySaves(dir.resolve("original.dat"));
        var path = store.createStressGrid(42);
        var frame = CitySimulation.load(CitySaves.sidecar(path,".city"));
        var ground = new CityTest.Ground(); ground.terrain.stressGrid(frame.stressGrid());
        var sim = new CitySimulation(frame.config(),ground,ground.terrain,frame);
        // A player-placed block on the first candidate must survive expansion.
        var v = frame.stressGrid().zone(StressGrid.indexForRank(0)).polygon().vertices().get(0);
        var edit = new Protocol.Edit((int)v.x()+1,33,(int)v.z()+1,Blocks.GLASS);
        var factory = frame.stressGrid().zone(StressGrid.indexForRank(600000)).polygon().vertices().get(0);
        var buried = new Protocol.Edit((int)factory.x()+3,31,(int)factory.z()+4,Blocks.GLASS);
        ground.apply(List.of(edit,buried));
        sim.developStressGrid();
        var developed = sim.frame();
        int[] expected = {192,80,80,48};
        for(int type=0;type<4;type++) {
            final int t=type;
            assertEquals(expected[type],developed.buildings().stream().filter(b->b.type()==t).count());
        }
        assertEquals(128,developed.citizens().size());
        assertEquals(48,developed.agriculture().farms().size());
        assertEquals(96,developed.agriculture().fields().size());
        assertEquals(Blocks.GLASS,ground.type(edit.x(),edit.y(),edit.z()));
        assertEquals(Blocks.GLASS,ground.type(buried.x(),buried.y(),buried.z()));
        assertTrue(developed.citizens().stream().filter(c->c.home()>0).count()>=110);
        assertTrue(developed.economy().properties().stream().filter(p->p.operator()>0).count()>=208);
        int edits = ground.edits.size();
        sim.developStressGrid();
        assertEquals(developed.buildings(),sim.frame().buildings());
        assertEquals(developed.citizens(),sim.frame().citizens());
        assertEquals(edits,ground.edits.size());
        for(int n=0;n<5;n++) sim.advance(1);
        sim.save(CitySaves.sidecar(path,".city"));
        var loaded=CitySimulation.load(CitySaves.sidecar(path,".city"));
        assertEquals(sim.frame().buildings(),loaded.buildings());
        assertEquals(sim.frame().citizens(),loaded.citizens());
        assertEquals(sim.frame().agriculture(),loaded.agriculture());
        assertEquals(sim.frame().economy(),loaded.economy());
        var version16 = dir.resolve("populated-stress-v16.city");
        try (var out = new DataOutputStream(Files.newOutputStream(version16))) {
            out.writeInt(0x43495440);
            loaded.write(out,16);
        }
        assertEquals(loaded,CitySimulation.load(version16),"Version 16 preserves the full populated stress city");
        var ordinaryGround=new CityTest.Ground();
        var ordinary=new CitySimulation(GameConfig.cityGame(),ordinaryGround,ordinaryGround.terrain,null);
        var before=ordinary.frame();
        assertThrows(IllegalArgumentException.class,ordinary::developStressGrid);
        assertEquals(before,ordinary.frame());
    }
    @Test void normalWorkersDevelopGridAndSaveAllDevelopedState() throws Exception {
        var store=new CitySaves(dir.resolve("original.dat"));
        var path=store.createStressGrid(42);
        var frame=CitySimulation.load(CitySaves.sidecar(path,".city"));
        var ground=new CityTest.Ground(); ground.terrain.stressGrid(frame.stressGrid());
        var sim=new CitySimulation(frame.config(),ground,ground.terrain,frame);
        assertEquals(12,sim.frame().citizens().size());
        for(int n=0;n<240;n++) sim.advance(1);
        var developed=sim.frame();
        assertFalse(developed.buildings().isEmpty(),"Normal workers must finish grid buildings; projects="+developed.economy().plots());
        assertTrue(developed.buildings().stream().anyMatch(b->b.type()==0));
        assertTrue(developed.citizens().stream().anyMatch(c->c.home()>0));
        assertTrue(developed.economy().resources().projects().stream().anyMatch(p->p.consumed()));
        assertFalse(ground.edits.isEmpty());
        sim.save(CitySaves.sidecar(path,".city"));
        var reloaded=CitySimulation.load(CitySaves.sidecar(path,".city"));
        assertEquals(developed.buildings(),reloaded.buildings());
        assertEquals(developed.citizens(),reloaded.citizens());
        assertEquals(developed.horses(),reloaded.horses());
        assertEquals(developed.economy(),reloaded.economy());
        assertEquals(developed.agriculture(),reloaded.agriculture());
        assertEquals(developed.zones(),reloaded.zones());
        assertTrue(reloaded.economy().plots().stream().anyMatch(p->Math.abs(p.x()-8)>256 || Math.abs(p.z()-24)>256),"Projects use outer rings beyond old city limits");
        var resumed=new CitySimulation(reloaded.config(),ground,ground.terrain,reloaded);
        int people=resumed.frame().citizens().size(); resumed.advance(1);
        assertEquals(people,resumed.frame().citizens().size(),"No founders or stock reseeded on reload");
        assertEquals(developed.buildings(),resumed.frame().buildings());
    }
    @Test void developedGridSitesRejectWrongPlotAndOrdinarySnapshotsRetainBounds() throws Exception {
        var store=new CitySaves(dir.resolve("original.dat"));
        var f=CitySimulation.load(CitySaves.sidecar(store.createStressGrid(42),".city"));
        var v=f.stressGrid().zone(999999).polygon().vertices().get(0);
        var building=new CityFrame.Building(1,1000000,3,(int)v.x(),33,(int)v.z()+1,2,0);
        var good=new CityFrame(f.config(),0,List.of(),f.zones(),List.of(building),List.of(),List.of(),f.economy(),f.addresses(),f.agriculture(),f.population(),f.aviation(),f.railway(),f.stressGrid());
        var bytes=new ByteArrayOutputStream(); good.write(new DataOutputStream(bytes),15);
        assertEquals(List.of(building),CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),15).buildings());
        assertThrows(IllegalArgumentException.class,()->new CityFrame(f.config(),0,List.of(),f.zones(),List.of(new CityFrame.Building(1,1,3,building.x(),33,building.z(),2,0)),List.of(),List.of(),f.economy(),f.addresses(),f.agriculture(),f.population(),f.aviation(),f.railway(),f.stressGrid()));
        var ordinary=new CityFrame(f.config(),0,List.of(),List.of(),List.of(building),List.of(),List.of(),f.economy(),f.addresses(),f.agriculture(),f.population(),f.aviation(),f.railway());
        bytes.reset(); ordinary.write(new DataOutputStream(bytes),15);
        assertThrows(IOException.class,()->CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),15));
    }
}
