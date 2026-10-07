package dev.jayms;

import dev.jayms.net.city.*;
import dev.jayms.net.Blocks;
import java.util.*;
import java.io.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MixedRoadOwnershipTest {
    @TempDir Path temp;
    final List<Polygon.Point> horizontal=List.of(new Polygon.Point(90,90),new Polygon.Point(110,90));
    final List<Polygon.Point> vertical=List.of(new Polygon.Point(100,80),new Polygon.Point(100,100));
    String build(CitySimulation city,int type,List<Polygon.Point> path) {
        return city.command(new CityCommand(CityCommand.ROAD,type,path),1,null);
    }
    Set<Polygon.Cell> cells(CityFrame frame) {
        var cells=new HashSet<Polygon.Cell>(); for(var r:frame.roads()) if(r.x()>85 && r.z()>75) cells.add(new Polygon.Cell(r.x(),r.z()));
        return cells;
    }
    void assertSurvivor(CitySimulation city,CityTest.Ground ground,int type,List<Polygon.Point> route) {
        var expected=RoadGeometry.surfaces(route,type);
        assertEquals(expected.keySet(),cells(city.frame()));
        for(var r:city.frame().roads()) if(expected.containsKey(new Polygon.Cell(r.x(),r.z()))) {
            assertEquals(type,r.type());
            assertEquals(expected.get(new Polygon.Cell(r.x(),r.z())).intValue(),ground.type(r.x(),r.y(),r.z()));
        }
    }
    @Test void mixedCrossingDeleteLeavesOnlyActualSurvivorInBothBuildOrders() {
        for(int wide:new int[]{2,3}) for(int narrow:new int[]{0,1}) for(boolean reverse:new boolean[]{false,true}) {
            var ground=new CityTest.Ground(); var city=new CityTest().simulation(ground);
            if(reverse) { build(city,narrow,vertical); build(city,wide,horizontal); }
            else { build(city,wide,horizontal); build(city,narrow,vertical); }
            int id=city.frame().addresses().nearest(95,90).id();
            assertEquals(RoadGeometry.surfaces(horizontal,wide).size(),RoadGeometry.section(city.frame(),id).size());
            assertEquals("Road section deleted",city.command(new CityCommand(CityCommand.DELETE_ROAD,id,List.of()),1,null));
            assertSurvivor(city,ground,narrow,vertical);
            assertEquals(Blocks.DIRT,ground.type(95,city.frame().roads().get(0).y(),90));
        }
    }
    @Test void narrowingKeepsTrueCrossingAndRemovesAllOldShoulders() {
        for(boolean reverse:new boolean[]{false,true}) {
            var ground=new CityTest.Ground(); var city=new CityTest().simulation(ground);
            if(reverse) { build(city,1,vertical); build(city,3,horizontal); }
            else { build(city,3,horizontal); build(city,1,vertical); }
            int id=city.frame().addresses().nearest(95,90).id();
            assertTrue(city.command(new CityCommand(CityCommand.EDIT_ROAD,id,List.of(new Polygon.Point(1,0))),1,null).contains("edited"));
            var expected=new HashSet<>(RoadGeometry.surfaces(horizontal,1).keySet()); expected.addAll(RoadGeometry.surfaces(vertical,1).keySet());
            assertEquals(expected,cells(city.frame())); assertEquals(117,expected.size());
            assertTrue(city.frame().roads().stream().filter(r->expected.contains(new Polygon.Cell(r.x(),r.z()))).allMatch(r->r.type()==1));
            city.command(new CityCommand(CityCommand.DELETE_ROAD,id,List.of()),1,null);
            assertSurvivor(city,ground,1,vertical);
        }
    }
    @Test void ownershipAndHiddenMaterialsSurviveSaveAndNetworkRoundTrips() throws Exception {
        var ground=new CityTest.Ground(); var city=new CityTest().simulation(ground);
        build(city,1,vertical); build(city,3,horizontal);
        var before=city.frame(); var file=temp.resolve("mixed.city"); city.save(file);
        try(var in=new DataInputStream(Files.newInputStream(file))) { assertEquals(0x4349543D,in.readInt()); }
        var loaded=CitySimulation.load(file); assertEquals(before,loaded);
        var bytes=new ByteArrayOutputStream(); before.write(new DataOutputStream(bytes));
        assertEquals(before,CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        var restored=new CitySimulation(loaded.config(),ground,ground.terrain,loaded);
        int id=loaded.addresses().nearest(95,90).id();
        restored.command(new CityCommand(CityCommand.DELETE_ROAD,id,List.of()),1,null);
        assertSurvivor(restored,ground,1,vertical);
    }
    @Test void legacyFormat12MixedCrossingMigratesWithoutChangingVisibleRoads() throws Exception {
        var ground=new CityTest.Ground(); var city=new CityTest().simulation(ground);
        build(city,3,horizontal); build(city,1,vertical);
        var frame=city.frame(); var file=temp.resolve("old-format12.city");
        try(var out=new DataOutputStream(Files.newOutputStream(file))) { out.writeInt(0x4349543C); frame.write(out,12); }
        var legacy=CitySimulation.load(file); assertEquals(frame.roads(),legacy.roads());
        assertTrue(legacy.addresses().roadFootprints().stream().allMatch(RoadOwnership.Footprint::inferred));
        var restored=new CitySimulation(legacy.config(),ground,ground.terrain,legacy);
        restored.command(new CityCommand(CityCommand.DELETE_ROAD,legacy.addresses().nearest(95,90).id(),List.of()),1,null);
        assertSurvivor(restored,ground,1,vertical);
    }
    @Test void longChainedStreetCanResizeSaveReloadAndDelete() throws Exception {
        var ground=new CityTest.Ground(); var city=new CityTest().simulation(ground);
        city.economy.budget=100000; // Synthetic funds isolate geometry from budget rejection.
        for(int x=20;x<180;x+=20)
            assertTrue(build(city,3,List.of(new Polygon.Point(x,90),new Polygon.Point(x+20,90))).contains("built:"));
        int id=city.frame().addresses().nearest(30,90).id();
        assertEquals(1127,RoadGeometry.section(city.frame(),id).size());
        assertTrue(city.command(new CityCommand(CityCommand.EDIT_ROAD,id,List.of(new Polygon.Point(2,0))),1,null).contains("edited"));
        assertEquals(805,RoadGeometry.section(city.frame(),id).size());
        assertTrue(city.command(new CityCommand(CityCommand.EDIT_ROAD,id,List.of(new Polygon.Point(3,0))),1,null).contains("edited"));
        assertEquals(1127,RoadGeometry.section(city.frame(),id).size());
        var save=temp.resolve("long.city");city.save(save);
        var loaded=CitySimulation.load(save); assertEquals(city.frame(),loaded);
        var restored=new CitySimulation(loaded.config(),ground,ground.terrain,loaded);
        assertEquals("Road section deleted",restored.command(new CityCommand(CityCommand.DELETE_ROAD,id,List.of()),1,null));
        assertTrue(RoadGeometry.section(restored.frame(),id).isEmpty());
    }
    @Test void longWideningStillHonorsCityCapacityWithoutMutation() {
        var ground=new CityTest.Ground(); var city=new CityTest().simulation(ground);city.economy.budget=100000;
        for(int x=20;x<180;x+=20) build(city,2,List.of(new Polygon.Point(x,90),new Polygon.Point(x+20,90)));
        var f=city.frame();var roads=new ArrayList<>(f.roads());
        for(int x=20;x<240 && roads.size()<8192;x++) for(int z=120;z<200 && roads.size()<8192;z++)
            roads.add(new CityFrame.Road(x,z,f.roads().get(0).y(),1));
        assertEquals(8192,roads.size());
        var full=new CityFrame(f.config(),f.elapsed(),roads,f.zones(),f.buildings(),f.citizens(),f.horses(),f.economy(),f.addresses(),f.agriculture(),f.population(),f.aviation());
        var restored=new CitySimulation(full.config(),ground,ground.terrain,full);
        int id=restored.frame().addresses().nearest(30,90).id();var before=restored.frame();var edits=new LinkedHashMap<>(ground.edits);
        assertEquals("Road too long: use shorter sections",restored.command(new CityCommand(CityCommand.EDIT_ROAD,id,List.of(new Polygon.Point(3,0))),1,null));
        assertEquals(before,restored.frame());assertEquals(edits,ground.edits);
    }
    @Test void placementLimitAndOccupiedLongEditStillRejectWithoutMutation() {
        var ground=new CityTest.Ground(); var city=new CityTest().simulation(ground);
        var before=city.frame(); var edits=new LinkedHashMap<>(ground.edits);
        assertEquals("Road too long: use shorter sections",build(city,3,List.of(new Polygon.Point(20,90),new Polygon.Point(180,90))));
        assertEquals(before,city.frame());assertEquals(edits,ground.edits);
        for(int x=20;x<180;x+=20) build(city,3,List.of(new Polygon.Point(x,90),new Polygon.Point(x+20,90)));
        int id=city.frame().addresses().nearest(30,90).id();before=city.frame();edits=new LinkedHashMap<>(ground.edits);ground.occupied=true;
        assertEquals("Road would intersect a player",city.command(new CityCommand(CityCommand.EDIT_ROAD,id,List.of(new Polygon.Point(2,0))),1,null));
        assertEquals(before,city.frame());assertEquals(edits,ground.edits);
    }
    @Test void partialWidthUpgradeOnNamedRouteKeepsEachOwnedCellType() {
        var ground=new CityTest.Ground(); var city=new CityTest().simulation(ground);
        build(city,1,horizontal); build(city,3,List.of(new Polygon.Point(110,90),new Polygon.Point(120,90)));
        int id=city.frame().addresses().nearest(95,90).id();
        var owner=city.frame().addresses().roadFootprints().stream().filter(f->f.street()==id).findFirst().orElseThrow();
        assertTrue(owner.cells().stream().anyMatch(c->c.x()==95 && c.type()==1));
        assertTrue(owner.cells().stream().anyMatch(c->c.x()==115 && c.type()==3));
        build(city,1,vertical);
        city.command(new CityCommand(CityCommand.DELETE_ROAD,id,List.of()),1,null);
        assertSurvivor(city,ground,1,vertical);
    }
}
