package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.CityTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.joml.Matrix4f;
import java.nio.file.Path;
import java.io.*;
import java.util.*;

class RoadTypesTest {
    @TempDir Path temp;
    CityCommand road(int type, float x, float z, float bx, float bz) {
        return new CityCommand(CityCommand.ROAD, type, List.of(new Polygon.Point(x,z), new Polygon.Point(bx,bz)));
    }
    @Test void pavedWidthsSurfacesBudgetAndRestart() throws Exception {
        var ground = new CityTest.Ground();
        var city = new CityTest().simulation(ground);
        for (int type = 1; type <= 3; type++) {
            int z = 90 + type * 12;
            final int expectedType = type;
            double before = city.frame().economy().roadSpending();
            assertTrue(city.command(road(type, 90,z,100,z),1,null).contains("built"));
            var section = city.frame().roads().stream().filter(r -> r.x() == 95 && Math.abs(r.z()-z) <= 4).toList();
            assertEquals(RoadTypes.width(type), section.size());
            assertTrue(section.stream().allMatch(r -> r.type() == expectedType));
            assertEquals(type, section.stream().filter(r -> ground.type(r.x(),r.y(),r.z()) == Blocks.ROAD_LINE_X).count());
            assertEquals(11 * RoadTypes.width(type) * CityEconomy.ROAD_COST,
                    city.frame().economy().roadSpending()-before);
            assertTrue(city.command(road(type,90,z,100,z),1,null).contains("paid $0"));
        }
        city.save(temp.resolve("city.dat"));
        var loaded = CitySimulation.load(temp.resolve("city.dat"));
        assertEquals(city.frame().roads(), loaded.roads());
        var restored = new CitySimulation(loaded.config(),ground,ground.terrain,loaded);
        assertEquals(loaded.roads(), restored.frame().roads());
        var bytes = new ByteArrayOutputStream();
        city.frame().write(new DataOutputStream(bytes),9);
        assertTrue(CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),9)
                .roads().stream().allMatch(r -> r.type() == 0));
    }
    @Test void dirtUpgradeInvalidTypeZeroLengthAndCollisionAreAtomic() {
        var ground = new CityTest.Ground();
        var city = new CityTest().simulation(ground);
        city.command(road(0,90,90,100,90),1,null);
        assertTrue(city.command(road(1,90,90,100,90),1,null).contains("built"));
        assertTrue(city.frame().roads().stream().filter(r -> r.x() == 95 && Math.abs(r.z()-90)<=1)
                .allMatch(r -> r.type() == 1));
        var before = city.frame();
        assertTrue(city.command(road(4,120,90,130,90),1,null).contains("Invalid road type"));
        assertTrue(city.command(road(1,120,90,120,90),1,null).contains("differ"));
        ground.occupied = true;
        assertTrue(city.command(road(3,120,90,130,90),1,null).contains("intersect"));
        assertEquals(before.roads(),city.frame().roads());
        assertEquals(before.economy().budget(),city.frame().economy().budget());
    }
    @Test void verticalAndBentRoadsFollowEndpoints() {
        var ground = new CityTest.Ground();
        var city = new CityTest().simulation(ground);
        assertTrue(city.command(road(3,120,120,120,135),1,null).contains("built"));
        var section = city.frame().roads().stream().filter(r -> r.z()==128 && Math.abs(r.x()-120)<=4).toList();
        assertEquals(7,section.size());
        assertEquals(3,section.stream().filter(r -> ground.type(r.x(),r.y(),r.z())==Blocks.ROAD_LINE_Z).count());
        assertTrue(city.command(road(2,150,150,160,160),1,null).contains("built"));
        assertTrue(city.frame().roads().stream().anyMatch(r -> r.x()==160 && r.z()==155 && r.type()==2));
        assertTrue(city.frame().roads().stream().anyMatch(r -> r.x()==158 && r.z()==150));
        assertFalse(city.frame().roads().stream().anyMatch(r -> r.x()==155 && r.z()==155));
    }
    @Test void routeUsesFloorAndUnequalLegsInNegativeCoordinates() {
        assertEquals(List.of(new Polygon.Point(-11,-21), new Polygon.Point(-3,-21), new Polygon.Point(-3,-17)),
                RoadRoute.points(List.of(new Polygon.Point(-10.2f,-20.2f), new Polygon.Point(-2.2f,-16.2f))));
        assertEquals(List.of(new Polygon.Point(10,20), new Polygon.Point(18,20)),
                RoadRoute.points(List.of(new Polygon.Point(10,20), new Polygon.Point(18,20))));
    }
    @Test void secondLegCollisionLeavesFirstLegAndBudgetUntouched() {
        var ground = new CityTest.Ground() {
            @Override public boolean occupied(int x, int y, int z, int width, int depth) {
                return x == 170 && z == 160;
            }
        };
        var city = new CityTest().simulation(ground);
        var before = city.frame();
        assertTrue(city.command(road(2,150,150,170,170),1,null).contains("intersect"));
        assertEquals(before.roads(), city.frame().roads());
        assertEquals(before.economy().budget(), city.frame().economy().budget());
    }
    @Test void allTypesBendInBothDirectionsAndRejectSameCell() {
        for (int type = 0; type <= 3; type++) {
            var ground = new CityTest.Ground();
            var city = new CityTest().simulation(ground);
            assertTrue(city.command(road(type,150,150,170,170),1,null).contains("built"));
            assertTrue(city.frame().roads().stream().anyMatch(r -> r.x()==160 && r.z()==150));
            assertTrue(city.frame().roads().stream().anyMatch(r -> r.x()==170 && r.z()==160));
            assertFalse(city.frame().roads().stream().anyMatch(r -> r.x()==160 && r.z()==160));
            assertTrue(city.command(road(type,130,130,110,110),1,null).contains("built"));
            assertTrue(city.frame().roads().stream().anyMatch(r -> r.x()==120 && r.z()==130));
            assertTrue(city.frame().roads().stream().anyMatch(r -> r.x()==110 && r.z()==120));
            var before = city.frame();
            assertTrue(city.command(road(type,190.1f,190.1f,190.8f,190.8f),1,null).contains("differ"));
            ground.occupied = true;
            assertTrue(city.command(road(type,190,190,210,210),1,null).contains("intersect"));
            assertEquals(before.roads(), city.frame().roads());
            assertEquals(before.economy().budget(), city.frame().economy().budget());
        }
    }
    @Test void menuConsumesClicksSelectsTypeAndEscapeCancels() {
        var ui = new CityTools();
        var frame = CityFrame.empty(GameConfig.cityGame());
        var commands = new ArrayList<CityCommand>();
        ui.click(200,530,1280,720,new Matrix4f(),new Matrix4f(),frame,commands::add);
        assertTrue(ui.roadMenu);
        assertNull(ui.cursorPoint(700,300,1280,720,new Matrix4f(),new Matrix4f(),frame));
        ui.click(40,230,1280,720,new Matrix4f(),new Matrix4f(),frame,commands::add);
        assertFalse(ui.roadMenu);
        assertEquals(3,ui.roadType);
        assertEquals(4,ui.tool);
        assertTrue(commands.isEmpty());
        assertTrue(ui.key(256,commands::add));
        assertEquals(-1,ui.tool);
    }
}
