package dev.jayms;

import dev.jayms.net.city.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class RoadWorkflowTest {
    @TempDir Path temp;
    Polygon.Point p(float x,float z) { return new Polygon.Point(x,z); }
    CityCommand edit(int id,int type) { return new CityCommand(CityCommand.EDIT_ROAD,id,List.of(p(type,0))); }
    @Test void chainKeepsSecondEndpointAndRejectsDuplicateClick() {
        var fixture=new CityToolsTest(); var tools=new dev.jayms.ui.CityTools(); tools.tool=4;
        var commands=new ArrayList<CityCommand>();
        fixture.click(tools,14,26,commands); fixture.click(tools,28.5f,26,commands);
        fixture.click(tools,28.5f,26,commands);
        assertEquals(1,commands.size());
        tools.roadResult(commands.get(0), "Paved road built: Test");
        fixture.click(tools,28.5f,36,commands);
        assertEquals(2,commands.size());
        assertEquals(commands.get(0).points().get(1),commands.get(1).points().get(0));
        tools.key(256,commands::add); assertEquals(-1,tools.tool);
    }
    @Test void snapStopsBeforeZoneForEveryWidthAndDirection() {
        var zone=new CityFrame.Zone(1,0,new Polygon(List.of(p(100,100),p(120,100),p(120,120),p(100,120))));
        var frame=new CityFrame(GameConfig.cityGame(),0,List.of(),List.of(zone),List.of(),List.of(),List.of());
        for(int type=0;type<4;type++) for(var pair:List.of(
                List.of(p(80,110),p(130,110)),List.of(p(110,80),p(110,130)),
                List.of(p(140,110),p(90,110)),List.of(p(80,80),p(130,130)),
                List.of(p(80,80),p(110,130)))) {
            var snap=RoadGeometry.snapZone(pair.get(0),pair.get(1),type,frame);
            if(pair.equals(List.of(p(80,80),p(130,130)))) assertEquals(pair.get(1),snap, "Right-angle route goes around zone");
            else assertNotEquals(pair.get(1),snap);
            assertFalse(RoadGeometry.surfaces(List.of(pair.get(0),snap),type).keySet().stream()
                .anyMatch(c->zone.polygon().contains(c.x()+.5f,c.z()+.5f)));
        }
        assertEquals(p(90,80),RoadGeometry.snapZone(p(80,80),p(90,80),3,frame));
    }
    @Test void selectResizeDeleteAndSaveKeepOtherRoads() throws Exception {
        var ground=new CityTest.Ground(); var city=new CityTest().simulation(ground);
        city.command(new CityCommand(CityCommand.ROAD,1,List.of(p(90,90),p(110,90))),1,null);
        var street=city.frame().addresses().nearest(100,90);
        int id=street.id();
        var selected=RoadGeometry.section(city.frame(),id);
        assertEquals(63,selected.size());
        var other=city.frame().roads().stream().filter(r->!selected.contains(r)).toList();
        assertTrue(city.command(edit(id,3),1,null).contains("edited"));
        assertEquals(147,RoadGeometry.section(city.frame(),id).size());
        assertTrue(city.command(edit(id,1),1,null).contains("edited"));
        assertEquals(63,RoadGeometry.section(city.frame(),id).size());
        assertTrue(city.frame().roads().containsAll(other));
        city.save(temp.resolve("roads.dat"));
        var restored=new CitySimulation(city.frame().config(),ground,ground.terrain,CitySimulation.load(temp.resolve("roads.dat")));
        assertEquals(63,RoadGeometry.section(restored.frame(),id).size());
        assertEquals("Road section deleted",restored.command(new CityCommand(CityCommand.DELETE_ROAD,id,List.of()),1,null));
        assertTrue(RoadGeometry.section(restored.frame(),id).isEmpty());
        assertEquals(other,restored.frame().roads());
        assertTrue(restored.command(edit(id,1),1,null).contains("no longer"));
    }
    @Test void deleteKeepsCrossingRoadConnected() {
        var ground=new CityTest.Ground(); var city=new CityTest().simulation(ground);
        city.command(new CityCommand(CityCommand.ROAD,1,List.of(p(90,90),p(110,90))),1,null);
        city.command(new CityCommand(CityCommand.ROAD,1,List.of(p(100,80),p(100,100))),1,null);
        int horizontal=city.frame().addresses().nearest(95,90).id();
        int vertical=city.frame().addresses().nearest(100,85).id();
        assertNotEquals(horizontal,vertical);
        city.command(new CityCommand(CityCommand.DELETE_ROAD,horizontal,List.of()),1,null);
        for(int z=80;z<=100;z++) {
            final int row=z;
            assertEquals(3,city.frame().roads().stream().filter(r->r.z()==row && Math.abs(r.x()-100)<=1).count());
        }
        assertTrue(city.frame().roads().stream().noneMatch(r->r.x()==95 && r.z()==90));
    }

    @Test void editRejectsOccupiedCellsAtomicallyAndCommandsRoundTrip() throws Exception {
        var ground=new CityTest.Ground(); var city=new CityTest().simulation(ground);
        city.command(new CityCommand(CityCommand.ROAD,1,List.of(p(90,90),p(110,90))),1,null);
        int id=city.frame().addresses().nearest(100,90).id(); var before=city.frame();
        ground.occupied=true;
        assertTrue(city.command(edit(id,3),1,null).contains("intersect"));
        assertTrue(city.command(new CityCommand(CityCommand.DELETE_ROAD,id,List.of()),1,null).contains("intersect"));
        assertEquals(before.roads(),city.frame().roads());
        assertEquals(before.economy().roadSpending(),city.frame().economy().roadSpending());
        for(var c:List.of(edit(id,3),new CityCommand(CityCommand.DELETE_ROAD,id,List.of()))) {
            var bytes=new ByteArrayOutputStream(); c.write(new DataOutputStream(bytes));
            assertEquals(c,CityCommand.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        }
        assertThrows(IllegalArgumentException.class,()->new CityCommand(CityCommand.EDIT_ROAD,id,List.of(p(4,0))));
    }
}
