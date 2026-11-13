package dev.jayms.net;

import dev.jayms.ChunkPos;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegionReplayWindowTest {
    @TempDir Path folder;
    static final ChunkPos REGION=new ChunkPos(-2,-2,-1);
    static Protocol.Edit whole(int key,int type) {
        return new Protocol.Edit(-32+(key&15),-32+(key>>8),-16+((key>>4)&15),type);
    }
    static List<Protocol.Edit> fixture() {
        var rows=new ArrayList<Protocol.Edit>();
        for(int i=0;i<4096;i++)rows.add(whole(i,new int[]{0,Blocks.STONE,Blocks.GLASS,1031}[i&3]));
        // Reverse recurring-cell order spans section boundaries and page ranges.
        for(int i=4095;i>=0;i--) {
            var p=whole(i,0);
            rows.add(new Protocol.Edit(p.x(),p.y(),p.z(),Blocks.piece(Blocks.LED,4),4,i&15,(i>>4)&15,(i>>8)&15,i*37));
        }
        return rows;
    }
    RegionReplaySpool spool(List<Protocol.Edit> rows)throws Exception {
        return RegionReplaySpool.stage(folder,new DataInputStream(new ByteArrayInputStream(RegionReplayLoaderTest.encodeRows(rows))),e->true,()->false);
    }
    static RegionReplayPage load(RegionReplaySpool spool,RegionReplayPage.CellRange range,int decoded,int retained)throws Exception {
        return RegionReplayPage.loadWindow(spool,REGION,range,new Terrain(42),decoded,retained,e->true,()->false);
    }
    @Test void sixtyFourWindowsCoverAll4096NegativeRegionCellsWithoutWholeRegionMaterialization()throws Exception {
        var rows=fixture();var expected=new WorldVoxels(new Terrain(42));rows.forEach(expected::apply);
        var installed=new WorldVoxels(new Terrain(42));var keys=new HashSet<WorldVoxels.CellPosition>();
        try(var spool=spool(rows)) {
            assertThrows(IOException.class,()->RegionReplayPage.load(spool,REGION,new Terrain(42),4096,64,e->true,()->false));
            for(int first=0;first<4096;first+=64) {
                var range=new RegionReplayPage.CellRange(first,first+64);var page=load(spool,range,8192,128);
                assertEquals(REGION,page.region());assertEquals(range,page.range());assertEquals(64,page.cells().size());
                var ordered=new LinkedHashMap<String,Protocol.Edit>();
                for(var row:rows)if(range.contains(row))WorldVoxels.rememberValidated(ordered,row);
                assertEquals(new ArrayList<>(ordered.entrySet()),new ArrayList<>(page.history().entrySet()));
                for(var entry:page.cells().entrySet()) {
                    var p=entry.getKey();assertTrue(keys.add(p));
                    assertTrue(expected.cell(p.x(),p.y(),p.z()).sameValues(entry.getValue().tree()));
                    installed.installCellImage(p.x(),p.y(),p.z(),entry.getValue());
                }
            }
        }
        assertEquals(4096,keys.size());
        // Installed geometry remains usable after all temporary spool files close.
        for(var p:keys)assertTrue(expected.cell(p.x(),p.y(),p.z()).sameValues(installed.cell(p.x(),p.y(),p.z())));
        try(var files=Files.list(folder)){assertEquals(0,files.count());}
    }
    @Test void decodedAdmissionPrecedesPolicyAndSelectedAdmissionCountsOverwrites()throws Exception {
        var selected=whole(0,Blocks.STONE);var outside=whole(4095,1031);var calls=new AtomicInteger();
        var range=new RegionReplayPage.CellRange(0,64);
        try(var spool=spool(List.of(selected,outside))) {
            assertThrows(IOException.class,()->RegionReplayPage.loadWindow(spool,REGION,range,new Terrain(42),1,4096,e->{calls.incrementAndGet();return true;},()->false));
            assertEquals(0,calls.get());
            var page=RegionReplayPage.loadWindow(spool,REGION,range,new Terrain(42),2,1,e->{calls.incrementAndGet();return true;},()->false);
            assertEquals(2,calls.get());assertEquals(1,page.history().size());
            // Excluded cells still undergo full selected-region policy validation.
            assertThrows(IOException.class,()->RegionReplayPage.loadWindow(spool,REGION,range,new Terrain(42),2,1,e->!Blocks.structureModel(e.type()),()->false));
            assertEquals(1,load(spool,range,2,1).cells().size());
        }
        try(var spool=spool(Collections.nCopies(4097,selected))) {
            assertThrows(IOException.class,()->load(spool,range,4097,4096));
            assertTrue(load(spool,new RegionReplayPage.CellRange(64,128),4097,0).history().isEmpty());
        }
    }
    @Test void rangeCoverageIncludesImplicitCellsAndBoundsCannotOverflow()throws Exception {
        for(int[] range:List.of(new int[]{-1,1},new int[]{0,0},new int[]{1,0},new int[]{0,4097},new int[]{Integer.MAX_VALUE,Integer.MIN_VALUE}))
            assertThrows(IllegalArgumentException.class,()->new RegionReplayPage.CellRange(range[0],range[1]));
        try(var spool=spool(List.of(whole(4095,0)))) {
            var range=new RegionReplayPage.CellRange(0,64);var empty=load(spool,range,1,0);
            assertEquals(range,empty.range());assertTrue(empty.cells().isEmpty());assertTrue(empty.history().isEmpty());
            var last=load(spool,new RegionReplayPage.CellRange(4095,4096),1,1);
            assertEquals(1,last.cells().size());assertEquals(0,last.cells().values().iterator().next().tree().get(0,0,0));
            assertThrows(IllegalArgumentException.class,()->load(spool,new RegionReplayPage.CellRange(0,65),1,1));
            assertThrows(IllegalArgumentException.class,()->load(spool,range,-1,1));
            assertThrows(IllegalArgumentException.class,()->load(spool,range,LocalGame.MAX_SAVED_EDITS+1,1));
            assertThrows(IllegalArgumentException.class,()->load(spool,range,1,4098));
        }
    }
    @Test void cancellationWhileReadingExcludedCellsOrPreparingImagesAllowsRetry()throws Exception {
        var rows=new ArrayList<Protocol.Edit>();rows.add(whole(0,Blocks.STONE));
        rows.addAll(Collections.nCopies(200,whole(4095,Blocks.GLASS)));
        var range=new RegionReplayPage.CellRange(0,64);
        try(var spool=spool(rows)) {
            var cancelled=new AtomicBoolean();var calls=new AtomicInteger();
            assertThrows(CancellationException.class,()->RegionReplayPage.loadWindow(spool,REGION,range,new Terrain(42),201,1,e->{if(calls.incrementAndGet()==5)cancelled.set(true);return true;},cancelled::get));
            var total=new AtomicInteger();RegionReplayPage.loadWindow(spool,REGION,range,new Terrain(42),201,1,e->true,()->{total.incrementAndGet();return false;});
            var late=new AtomicInteger();
            assertThrows(CancellationException.class,()->RegionReplayPage.loadWindow(spool,REGION,range,new Terrain(42),201,1,e->true,()->late.incrementAndGet()>=total.get()-2));
            assertEquals(1,load(spool,range,201,1).cells().size());
        }
    }
    @Test void corruptedExcludedCellInSameRegionStillRejectsAndCanBeRetried()throws Exception {
        try(var spool=spool(List.of(whole(0,Blocks.STONE),whole(4095,Blocks.GLASS)))) {
            Path file;
            try(var paths=Files.list(folder)){file=paths.filter(p->p.getFileName().toString().startsWith(".replay-spool-")).findFirst().orElseThrow();}
            var original=Files.readAllBytes(file);var changed=original.clone();changed[changed.length-1]^=1;
            Files.write(file,changed);
            assertThrows(IOException.class,()->load(spool,new RegionReplayPage.CellRange(0,64),2,1));
            Files.write(file,original);
            assertEquals(1,load(spool,new RegionReplayPage.CellRange(0,64),2,1).cells().size());
        }
    }
    @Test void fullColouredCellClearAnd4096LeavesFitWithoutDroppingAnyRow()throws Exception {
        var p=whole(0,0);var rows=new ArrayList<Protocol.Edit>();rows.add(p);
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
            rows.add(new Protocol.Edit(p.x(),p.y(),p.z(),Blocks.piece(Blocks.LED,4),4,x,y,z,(x+16*z+256*y)*37));
        var range=new RegionReplayPage.CellRange(0,1);
        try(var spool=spool(rows)) {
            var page=load(spool,range,4097,4097);assertEquals(4097,page.history().size());assertEquals(1,page.cells().size());
            var expected=new LinkedHashMap<String,Protocol.Edit>();rows.forEach(e->WorldVoxels.rememberValidated(expected,e));
            assertEquals(new ArrayList<>(expected.entrySet()),new ArrayList<>(page.history().entrySet()));
            var image=page.cells().values().iterator().next().tree();assertEquals(4096,image.leafCount());
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
                assertEquals(WorldVoxels.encode(new Protocol.Edit(p.x(),p.y(),p.z(),Blocks.piece(Blocks.LED,4),4,x,y,z,(x+16*z+256*y)*37)),image.get(x,y,z));
            assertThrows(IOException.class,()->load(spool,range,4097,4096));
        }
        rows.add(rows.get(1));
        try(var spool=spool(rows)){assertThrows(IOException.class,()->load(spool,range,4098,4097));}
    }
}
