package dev.jayms.net;

import dev.jayms.ChunkPos;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegionReplayPageTest {
    @TempDir Path folder;
    static final ChunkPos REGION=new ChunkPos(-2,11,-1);
    RegionReplaySpool spool(List<Protocol.Edit> rows)throws Exception {
        return RegionReplaySpool.stage(folder,new DataInputStream(new ByteArrayInputStream(
                RegionReplayLoaderTest.encodeRows(rows))),e->true,()->false);
    }
    static List<Protocol.Edit> mixed() {
        var rows=new ArrayList<Protocol.Edit>();
        rows.add(new Protocol.Edit(-17,180,-1,Blocks.STONE));
        rows.add(new Protocol.Edit(32,180,32,Blocks.GLASS)); // unrelated region
        rows.add(new Protocol.Edit(-18,180,-1,0));
        rows.add(new Protocol.Edit(-17,180,-1,0,2,0,0,0));
        rows.add(new Protocol.Edit(-19,180,-1,Blocks.piece(Blocks.GLASS,1),1,1,0,1));
        rows.add(new Protocol.Edit(32,180,32,0));
        rows.add(new Protocol.Edit(-17,180,-1,Blocks.piece(Blocks.LED,4),4,0,0,0,0xa1b2c3));
        rows.add(new Protocol.Edit(-19,180,-1,1031));
        return rows;
    }
    static RegionReplayPage load(RegionReplaySpool spool)throws Exception {
        return RegionReplayPage.load(spool,REGION,new Terrain(42),4096,64,e->true,()->false);
    }
    @Test void recurringSectionsMatchIndependentReplayAndRemainDetachedAfterSpoolClose()throws Exception {
        var rows=mixed();RegionReplayPage page;
        var expectedHistory=new LinkedHashMap<String,Protocol.Edit>();
        var expectedWorld=new WorldVoxels(new Terrain(42));
        for(var edit:rows)if(ChunkPos.fromBlock(edit.x(),edit.y(),edit.z()).equals(REGION)) {
            WorldVoxels.rememberValidated(expectedHistory,edit);expectedWorld.apply(edit);
        }
        try(var spool=spool(rows)){page=load(spool);}
        assertEquals(REGION,page.region());assertEquals(new ArrayList<>(expectedHistory.entrySet()),new ArrayList<>(page.history().entrySet()));
        assertEquals(3,page.cells().size());
        for(var entry:page.cells().entrySet()) {
            var p=entry.getKey();assertTrue(expectedWorld.cell(p.x(),p.y(),p.z()).sameValues(entry.getValue().tree()));
            assertThrows(IllegalStateException.class,()->entry.getValue().tree().fill(0,0,0,16,16,16,0));
        }
        assertThrows(UnsupportedOperationException.class,()->page.cells().clear());
        assertThrows(UnsupportedOperationException.class,()->page.history().clear());
        assertThrows(UnsupportedOperationException.class,()->page.history().entrySet().iterator().next().setValue(rows.get(0)));
        var installed=new WorldVoxels(new Terrain(42));
        for(var entry:page.cells().entrySet()){var p=entry.getKey();installed.installCellImage(p.x(),p.y(),p.z(),entry.getValue());}
        installed.apply(new Protocol.Edit(-17,180,-1,0));
        assertTrue(expectedWorld.cell(-17,180,-1).sameValues(page.cells().get(new WorldVoxels.CellPosition(-17,180,-1)).tree()));
        assertFalse(installed.cell(-17,180,-1).sameValues(page.cells().get(new WorldVoxels.CellPosition(-17,180,-1)).tree()));
        try(var files=Files.list(folder)){assertEquals(0,files.count());}
    }
    @Test void rowBudgetCountsOverwritesAndRejectsBeforePayloadPredicateOrDelivery()throws Exception {
        var row=new Protocol.Edit(-17,180,-1,Blocks.STONE);var calls=new AtomicInteger();
        try(var spool=spool(Collections.nCopies(4097,row))) {
            assertThrows(IOException.class,()->RegionReplayPage.load(spool,REGION,new Terrain(42),4096,64,e->{calls.incrementAndGet();return true;},()->false));
            assertEquals(0,calls.get());
            assertThrows(IOException.class,()->spool.visitRegion(REGION,4096,e->{calls.incrementAndGet();return true;},()->false,(r,s)->fail()));
            assertEquals(0,calls.get());
        }
        try(var spool=spool(Collections.nCopies(4096,row))) {
            assertEquals(1,load(spool).history().size());
            assertThrows(IOException.class,()->RegionReplayPage.load(spool,REGION,new Terrain(42),4095,64,e->true,()->false));
        }
    }
    @Test void cellBoundaryMissingRegionAndTightenedBudgets()throws Exception {
        var rows=new ArrayList<Protocol.Edit>();
        for(int i=0;i<65;i++)rows.add(new Protocol.Edit(-32+(i&15),176+(i>>4),-1,0));
        try(var spool=spool(rows.subList(0,64))) {
            var page=load(spool);assertEquals(64,page.cells().size());
            for(var image:page.cells().values())assertEquals(0,image.tree().get(0,0,0));
            assertThrows(IOException.class,()->RegionReplayPage.load(spool,REGION,new Terrain(42),64,63,e->true,()->false));
            var empty=RegionReplayPage.load(spool,new ChunkPos(0,0,0),new Terrain(42),0,0,e->{fail();return false;},()->false);
            assertTrue(empty.history().isEmpty());assertTrue(empty.cells().isEmpty());
            for(int[] cap:List.of(new int[]{-1,1},new int[]{4097,1},new int[]{1,-1},new int[]{1,65}))
                assertThrows(IllegalArgumentException.class,()->RegionReplayPage.load(spool,REGION,new Terrain(42),cap[0],cap[1],e->true,()->false));
        }
        try(var spool=spool(rows)){assertThrows(IOException.class,()->load(spool));}
    }
    @Test void fragmentedTerrainBackedGeometryMatchesEveryVoxel()throws Exception {
        var rows=new ArrayList<>(RegionReplayLoaderTest.fragmented(-17).subList(0,4096));
        // No clear: fractional edits must retain implicit terrain under holes.
        rows.set(0,new Protocol.Edit(-17,180,-1,Blocks.piece(Blocks.GLASS,1),1,0,0,0));
        rows.removeIf(e->e.depth()==4&&e.ix()==15&&e.iy()==15&&e.iz()==15);
        rows.replaceAll(e->new Protocol.Edit(e.x(),Terrain.MIN_Y,e.z(),e.type(),e.depth(),e.ix(),e.iy(),e.iz(),e.color()));
        assertNotEquals(0,new Terrain(42).block(-17,Terrain.MIN_Y,-1));
        var expected=new WorldVoxels(new Terrain(42));rows.forEach(expected::apply);
        try(var spool=spool(rows)) {
            var actual=RegionReplayPage.load(spool,new ChunkPos(-2,-2,-1),new Terrain(42),4096,64,e->true,()->false).cells().get(new WorldVoxels.CellPosition(-17,Terrain.MIN_Y,-1)).tree();
            assertTrue(expected.cell(-17,Terrain.MIN_Y,-1).sameValues(actual));
            assertEquals(WorldVoxels.encode(new Terrain(42).block(-17,Terrain.MIN_Y,-1)),actual.get(15,15,15));
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
                assertEquals(expected.cell(-17,Terrain.MIN_Y,-1).get(x,y,z),actual.get(x,y,z));
        }
    }
    @Test void earlyAndLateCancellationOrPolicyFailureReturnNoPageAndAllowRetry()throws Exception {
        try(var spool=spool(mixed())) {
            var total=new AtomicInteger();
            RegionReplayPage.load(spool,REGION,new Terrain(42),4096,64,e->true,()->{total.incrementAndGet();return false;});
            for(int cutoff:List.of(1,5,total.get()/2,total.get()-3,total.get())) {
                var checks=new AtomicInteger();
                assertThrows(CancellationException.class,()->RegionReplayPage.load(spool,REGION,new Terrain(42),4096,64,e->true,()->checks.incrementAndGet()>=cutoff));
                assertEquals(3,load(spool).cells().size());
            }
            assertThrows(IOException.class,()->RegionReplayPage.load(spool,REGION,new Terrain(42),4096,64,e->!Blocks.structureModel(e.type()),()->false));
            assertEquals(3,load(spool).cells().size());
        }
    }
}
