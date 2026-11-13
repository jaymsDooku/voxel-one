package dev.jayms.net;

import dev.jayms.ChunkPos;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegionReplayChunkTest {
    @TempDir Path folder;
    static final ChunkPos REGION=new ChunkPos(-2,-2,-1);
    RegionReplaySpool spool(List<Protocol.Edit> rows)throws Exception {
        return RegionReplaySpool.stage(folder,new DataInputStream(new ByteArrayInputStream(
                RegionReplayLoaderTest.encodeRows(rows))),e->true,()->false);
    }
    @Test void fullNegativeChunkMatchesIndependentGeometryAndOutlivesCache()throws Exception {
        var rows=List.of(new Protocol.Edit(-32,-32,-16,0),
                new Protocol.Edit(-32,-32,-16,Blocks.piece(Blocks.GLASS,1),1,1,0,1),
                new Protocol.Edit(0,0,0,Blocks.STONE),
                new Protocol.Edit(-17,-17,-1,1031),
                new Protocol.Edit(-31,-32,-16,Blocks.piece(Blocks.LED,4),4,1,2,3,0x123456));
        var terrain=new Terrain(42);var expected=new WorldVoxels(terrain);rows.forEach(expected::apply);
        RegionReplayChunk.Snapshot result;
        try(var source=spool(rows);var cache=new RegionReplayCache(folder,source,1,rows.size()*28L,rows.size(),e->true)) {
            result=RegionReplayChunk.load(cache,REGION,terrain,8192,()->false);
            assertEquals(3,result.editedCells());assertEquals(1,cache.retainedRegions());
        }
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
            assertTrue(expected.cell(-32+x,-32+y,-16+z).sameValues(result.geometry().region(x*16,y*16,z*16,16)),"Cell "+x+","+y+","+z);
        assertThrows(IllegalStateException.class,()->result.geometry().fill(0,0,0,1,1,1,1));
        try(var files=Files.list(folder)){assertEquals(0,files.count());}
    }
    @Test void geometryAdmissionCancellationAndRetryDoNotPublishPartialResults()throws Exception {
        var terrain=new Terrain(42);var calls=new AtomicInteger();
        try(var source=spool(List.of());var cache=new RegionReplayCache(folder,source,1,0,0,e->{fail();return false;})) {
            assertThrows(IllegalArgumentException.class,()->RegionReplayChunk.load(cache,REGION,terrain,-1,()->{calls.incrementAndGet();return false;}));
            assertEquals(0,calls.get());assertEquals(0,cache.retainedRegions());
            assertThrows(IOException.class,()->RegionReplayChunk.load(cache,REGION,terrain,1,()->false));
            assertThrows(CancellationException.class,()->RegionReplayChunk.load(cache,REGION,terrain,8192,()->calls.incrementAndGet()>500));
            var retry=RegionReplayChunk.load(cache,REGION,terrain,8192,()->false);
            assertEquals(0,retry.editedCells());assertTrue(retry.stagedCellLeaves()>1);
        }
    }
    @Test void terrainRevisionAndOwnerChangesRejectBeforeReturn()throws Exception {
        var terrain=new Terrain(42);var mutate=new AtomicBoolean();
        var rows=List.of(new Protocol.Edit(-32,-32,-16,Blocks.STONE));
        try(var source=spool(rows);var cache=new RegionReplayCache(folder,source,1,28,1,e->{if(mutate.getAndSet(false))terrain.stressGrid(null);return true;})) {
            cache.load(REGION,new RegionReplayPage.CellRange(0,1),terrain,1,()->false);mutate.set(true);
            assertThrows(IOException.class,()->RegionReplayChunk.load(cache,REGION,terrain,8192,()->false));
            assertEquals(terrain.geometryRevision(),RegionReplayChunk.load(cache,REGION,terrain,8192,()->false).terrainRevision());
            var executor=Executors.newSingleThreadExecutor();
            try{assertTrue(executor.submit(()->{try{RegionReplayChunk.load(cache,REGION,terrain,8192,()->false);return false;}catch(IOException expected){return true;}}).get());}
            finally{executor.shutdownNow();}
            assertThrows(IOException.class,()->RegionReplayChunk.load(cache,new ChunkPos(Integer.MAX_VALUE,0,0),terrain,8192,()->false));
        }
    }
}
