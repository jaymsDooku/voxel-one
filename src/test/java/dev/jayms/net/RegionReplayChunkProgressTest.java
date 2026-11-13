package dev.jayms.net;

import dev.jayms.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegionReplayChunkProgressTest {
    @TempDir Path folder;
    static final ChunkPos REGION=new ChunkPos(0,10,0);
    RegionReplaySpool spool(List<Protocol.Edit> rows)throws Exception {
        return RegionReplaySpool.stage(folder,new DataInputStream(new ByteArrayInputStream(
                RegionReplayLoaderTest.encodeRows(rows))),e->true,()->false);
    }
    @Test void implicitCellsAdvanceWithoutSourceRowsAndFinalCountIsNotAuthority()throws Exception {
        var terrain=new Terrain(42);var counts=new ArrayList<Integer>();
        try(var source=spool(List.of());var cache=new RegionReplayCache(folder,source,1,0,0,e->{fail();return false;})) {
            var result=RegionReplayChunk.load(cache,REGION,terrain,0,()->false,counts::add);
            assertEquals(0,result.editedCells());assertTrue(result.geometry().isEmpty());assertEquals(4097,counts.size());
            for(int i=0;i<counts.size();i++)assertEquals(i,counts.get(i));
            var last=new AtomicInteger();
            assertThrows(IOException.class,()->RegionReplayChunk.load(cache,REGION,terrain,0,()->false,n->{last.set(n);if(n==4096)terrain.stressGrid(null);}));
            assertEquals(4096,last.get()); // Final source check still rejects.
        }
    }
    @Test void callbackCancellationStopsBeforeNextCellAndRetryResetsProgress()throws Exception {
        var terrain=new Terrain(42);var cancelled=new AtomicBoolean();var last=new AtomicInteger(-1);
        try(var source=spool(List.of());var cache=new RegionReplayCache(folder,source,1,0,0,e->true)) {
            assertThrows(CancellationException.class,()->RegionReplayChunk.load(cache,REGION,terrain,0,cancelled::get,n->{last.set(n);if(n==128)cancelled.set(true);}));
            assertEquals(128,last.get());cancelled.set(false);
            var counts=new ArrayList<Integer>();assertNotNull(RegionReplayChunk.load(cache,REGION,terrain,0,cancelled::get,counts::add));
            assertEquals(0,counts.get(0));assertEquals(4096,counts.get(counts.size()-1));
        }
    }
    @Test void ownerObservesProgressWhileWorkerBlockedAndCancellationStillWins()throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var armed=new AtomicBoolean();
        var edit=new Protocol.Edit(0,160,8,Blocks.STONE); // local key128
        var worker=new RegionReplayWorker(folder,(cancel,progress)-> {
            try{return new RegionReplayWorker.Source(spool(List.of(edit)),new Terrain(42),e->{
                if(armed.getAndSet(false)){entered.countDown();try{RegionReplayWorkerTest.release(release);}catch(IOException failure){throw new UncheckedIOException(failure);}}return true;
            },RegionReplayWorkerTest.REV);}catch(IOException failure){throw failure;}catch(Exception failure){throw new IOException(failure);}
        },2,1,28,1);
        try(var world=new World(42)) {
            var warm=worker.submit(REGION,new RegionReplayPage.CellRange(0,1),1,RegionReplayWorkerTest.REV);RegionReplayWorkerTest.done(warm);
            assertEquals(0,warm.completedCells());armed.set(true);
            var request=worker.submitChunkFenced(REGION,1,RegionReplayWorkerTest.REV,world);
            assertTrue(entered.await(10,TimeUnit.SECONDS));assertEquals(128,request.completedCells());assertEquals(RegionReplayWorker.Phase.LOADING,request.outcome().phase());
            assertThrows(IllegalStateException.class,()->request.chunkResult(RegionReplayWorkerTest.REV,world));
            assertTrue(request.cancel());release.countDown();
            var retry=worker.submitChunkFenced(REGION,1,RegionReplayWorkerTest.REV,world);RegionReplayWorkerTest.done(retry);
            assertEquals(RegionReplayWorker.Phase.CANCELLED,request.outcome().phase());assertEquals(128,request.completedCells());
            assertThrows(CancellationException.class,()->request.chunkResult(RegionReplayWorkerTest.REV,world));
            assertEquals(4096,retry.completedCells());assertEquals(1,retry.chunkResult(RegionReplayWorkerTest.REV,world).editedCells());
            assertTrue(world.getLoadedChunks().isEmpty());assertFalse(world.isLoaded(0,160,0));
        }finally{release.countDown();RegionReplayWorkerTest.closed(worker);try(var paths=Files.list(folder)){assertEquals(0,paths.count());}}
    }
}
