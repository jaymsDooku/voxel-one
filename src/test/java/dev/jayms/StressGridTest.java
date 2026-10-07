package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StressGridTest {
    @TempDir Path folder;
    @Test void everyRankIsUniqueAndRingsHaveExactSymmetricShares() {
        BitSet seen=new BitSet(StressGrid.COUNT); int[] counts=new int[4];
        for(int z=0;z<1000;z++) for(int x=0;x<1000;x++) {
            int rank=StressGrid.rank(x,z), type=StressGrid.type(x,z);
            assertTrue(rank>=0 && rank<StressGrid.COUNT); assertFalse(seen.get(rank)); seen.set(rank);
            counts[type]++;
            assertEquals(type,StressGrid.type(999-z,x));
            // Boundary shells must split to achieve exact percentages; compare only increasing radius.
            if(x>=500 && x<999 && 2*(x+1)-999>Math.abs(2*z-999)) assertTrue(type<=StressGrid.type(x+1,z));
        }
        assertEquals(StressGrid.COUNT,seen.cardinality());
        assertArrayEquals(new int[]{400000,200000,200000,200000},counts);
        assertEquals(0,StressGrid.type(499,499)); assertEquals(3,StressGrid.type(0,0));
    }
    @Test void plotsFitBuildingsAndKeepRoadsBetweenEveryRowAndColumn() {
        var grid=StressGrid.standard();
        assertEquals(1000000,grid.zones().size()); assertTrue(grid.zones() instanceof RandomAccess);
        assertEquals(grid.zones(),new StressGrid(33).zones());
        for(int z=0;z<1000;z++) for(int x=0;x<1000;x+=97) {
            var zone=grid.zone(z*1000+x); var v=zone.polygon().vertices();
            assertEquals(z*1000+x+1,zone.id());
            assertEquals(16,v.get(1).x()-v.get(0).x()); assertEquals(16,v.get(2).z()-v.get(1).z());
            int bx=(int)v.get(0).x(),bz=(int)v.get(0).z();
            assertEquals(zone.id()-1,grid.plotAt(bx,bz));
            assertFalse(grid.road(bx,bz)); assertFalse(grid.road(bx+15,bz+15));
            assertTrue(grid.road(bx-1,bz)); assertTrue(grid.road(bx+16,bz));
            assertTrue(grid.road(bx,bz-1)); assertTrue(grid.road(bx,bz+16));
            // A 12 x 14 farm plus its two entrance rows fits each 16 x 16 polygon.
            assertTrue(zone.polygon().contains(bx+11.5f,bz+15.5f));
        }
        assertThrows(IndexOutOfBoundsException.class,()->grid.zone(1000000));
        assertThrows(IllegalArgumentException.class,()->new Polygon.Point(1000,24));
    }
    @Test void all1001RoadsContinueAcrossTheWholeGridWithTwoLanesAndDivider() {
        var grid=StressGrid.standard();
        for(int strip=0;strip<=1000;strip++) {
            int x=StressGrid.MIN_X+strip*19, z=StressGrid.MIN_Z+strip*19;
            for(int along=0;along<StressGrid.EXTENT;along++) {
                assertTrue(grid.road(x,StressGrid.MIN_Z+along));
                assertTrue(grid.road(StressGrid.MIN_X+along,z));
            }
            int sample=StressGrid.MIN_Z+3;
            assertEquals(Blocks.ASPHALT,grid.surface(x,sample));
            assertEquals(Blocks.ROAD_LINE_Z,grid.surface(x+1,sample));
            assertEquals(Blocks.ASPHALT,grid.surface(x+2,sample));
        }
        assertEquals(105114009L,grid.roadCells());
        assertFalse(grid.contains(StressGrid.MIN_X-1,StressGrid.MIN_Z));
        assertFalse(grid.contains(StressGrid.MIN_X+StressGrid.EXTENT,StressGrid.MIN_Z));
    }
    @Test void generatedTerrainAndChunksHaveRoadSurfacesAtTheFarCorner() {
        var grid=StressGrid.standard(); var terrain=new Terrain(42); terrain.stressGrid(grid);
        int x=StressGrid.MIN_X+StressGrid.EXTENT-2,z=StressGrid.MIN_Z+StressGrid.EXTENT-2;
        assertEquals(32,terrain.surfaceHeight(x,z)); assertEquals(grid.surface(x,z),terrain.block(x,32,z));
        assertEquals(Blocks.AIR,terrain.block(x,33,z)); assertEquals(Blocks.STONE,terrain.block(x,0,z));
        var p=ChunkPos.fromBlock(x,32,z); var chunk=ChunkGenerator.generate(p,terrain);
        assertEquals(grid.surface(x,z),chunk.getBlock(Math.floorMod(x,16),0,Math.floorMod(z,16)));
        var normal=new Terrain(42); int before=normal.block(8,normal.surfaceHeight(8,24),24);
        normal.stressGrid(grid); normal.stressGrid(null);
        assertEquals(before,normal.block(8,normal.surfaceHeight(8,24),24));
    }
    @Test void namedSaveInstallsOnceCopiesAndPreservesOriginalAndElapsedTime() throws Exception {
        Path original=folder.resolve("offline-city.dat"); new LocalGame(original,42).save();
        byte[] before=Files.readAllBytes(original); var store=new CitySaves(original);
        store.ensureStressGrid(42); var entry=store.list().stream().filter(e->e.name().equals(StressGrid.NAME)).findFirst().orElseThrow();
        var frame=CitySimulation.load(CitySaves.sidecar(entry.world(),".city"));
        assertEquals(StressGrid.standard(),frame.stressGrid()); assertEquals(1000000,frame.zones().size());
        var local=new LocalGame(entry.world(),0); var terrain=new Terrain(local.seed); terrain.stressGrid(frame.stressGrid());
        local.startGame(GameConfig.cityGame(),new CitySimulation.Ground() {
            public int type(int x,int y,int z) { return terrain.block(x,y,z); }
            public void apply(List<Protocol.Edit> edits) { fail("Vacant grid has no construction edits"); }
            public boolean occupied(int x,int y,int z,int width,int depth) { return false; }
        });
        for(int i=0;i<100;i++) local.city.advance(.1);
        var route=local.city.route(8,24,StressGrid.MIN_X+StressGrid.EXTENT-2,StressGrid.MIN_Z+StressGrid.EXTENT-2);
        assertFalse(route.isEmpty(),"Running city simulation routes across the generated road grid");
        assertTrue(route.size()<40000,"Route work follows distance, not grid area");
        assertEquals(new Polygon.Cell(StressGrid.MIN_X+StressGrid.EXTENT-2,StressGrid.MIN_Z+StressGrid.EXTENT-2),route.get(route.size()-1));
        local.save(); double elapsed=local.city.frame().elapsed(); assertTrue(elapsed>=9.9);
        assertEquals(0,local.city.frame().buildings().size());
        store.ensureStressGrid(99);
        assertEquals(elapsed,CitySimulation.load(CitySaves.sidecar(entry.world(),".city")).elapsed());
        Path copy=store.create("Grid Copy",entry.world(),99); CitySaves.validate(copy,0);
        var loaded=CitySimulation.load(CitySaves.sidecar(copy,".city"));
        assertEquals(frame.stressGrid(),loaded.stressGrid()); assertEquals(elapsed,loaded.elapsed());
        assertEquals(frame.zones(),loaded.zones());
        assertThrows(IOException.class,()->store.createStressGrid(99));
        assertArrayEquals(before,Files.readAllBytes(original));
        assertTrue(Files.size(CitySaves.sidecar(copy,".city"))<100000);
    }
    @Test void version14CannotSilentlyDropGridAndVersion15RejectsBadGrade() throws Exception {
        var store=new CitySaves(folder.resolve("offline-city.dat")); var world=store.createStressGrid(42);
        var frame=CitySimulation.load(CitySaves.sidecar(world,".city"));
        assertThrows(IOException.class,()->frame.write(new DataOutputStream(new ByteArrayOutputStream()),14));
        byte[] bytes=Files.readAllBytes(CitySaves.sidecar(world,".city"));
        java.nio.ByteBuffer.wrap(bytes).putInt(bytes.length-4,65);
        Files.write(CitySaves.sidecar(world,".city"),bytes);
        assertThrows(IOException.class,()->CitySaves.validate(world,42));
        assertThrows(IOException.class,()->StressGrid.read(new DataInputStream(new ByteArrayInputStream(new byte[3]))));
    }
    @Test void routesAcrossAndAlongGridUseOnlyConnectedRoadCells() {
        var grid=StressGrid.standard();
        for(float[] pair:List.of(new float[]{8,24,9507,9523},new float[]{-9492,-9476,9507,9523},new float[]{0,0,19,0},new float[]{0,0,0,19})) {
            var from=grid.nearestRoad(pair[0],pair[1]);var to=grid.nearestRoad(pair[2],pair[3]);
            var route=grid.route(from,to); assertFalse(route.isEmpty());
            assertEquals(from,route.get(0));assertEquals(to,route.get(route.size()-1));
            assertTrue(route.size()<40000);
            for(int i=0;i<route.size();i++) {
                var cell=route.get(i);assertTrue(grid.road(cell.x(),cell.z()));
                if(i>0) assertEquals(1,Math.abs(cell.x()-route.get(i-1).x())+Math.abs(cell.z()-route.get(i-1).z()));
            }
        }
        assertNull(grid.nearestRoad(100000,0));
        var same=grid.nearestRoad(8,24);
        assertEquals(List.of(same),grid.route(same,same));
    }
}
