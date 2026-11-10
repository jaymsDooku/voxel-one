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

class RegionReplayCacheTest {
    @TempDir Path folder;
    static final RegionReplayPage.CellRange RANGE=new RegionReplayPage.CellRange(0,1);
    static final List<Protocol.Edit> ROWS=List.of(new Protocol.Edit(0,0,0,Blocks.STONE),
            new Protocol.Edit(16,0,0,Blocks.GLASS),new Protocol.Edit(32,0,0,Blocks.LED));
    static ChunkPos region(int x){return new ChunkPos(x,0,0);}
    RegionReplaySpool spool(List<Protocol.Edit> rows)throws Exception {
        return RegionReplaySpool.stage(folder,new DataInputStream(new ByteArrayInputStream(RegionReplayLoaderTest.encodeRows(rows))),e->true,()->false);
    }
    RegionReplayPage load(RegionReplayCache cache,int x)throws Exception {
        return cache.load(region(x),RANGE,new Terrain(42),1,()->false);
    }
    long copies()throws Exception{try(var files=Files.list(folder)){return files.filter(p->p.getFileName().toString().startsWith(".replay-cells-")).count();}}
    @Test void lruEvictionBoundsFilesAndDetachedPagesOutliveCache()throws Exception {
        var calls=new AtomicInteger();RegionReplayPage retained;
        try(var source=spool(ROWS);var cache=new RegionReplayCache(folder,source,2,56,1,e->{calls.incrementAndGet();return true;})) {
            retained=load(cache,0);load(cache,1);assertEquals(4,calls.get());
            load(cache,0);assertEquals(5,calls.get()); // hit checks selected rows only
            load(cache,2);assertEquals(7,calls.get());assertEquals(2,cache.retainedRegions());assertEquals(56,cache.retainedBytes());assertEquals(2,copies());
            load(cache,0);assertEquals(8,calls.get());
            load(cache,1);assertEquals(10,calls.get()); // region 1 was least recently used
        }
        assertEquals(0,copies());assertEquals(List.of(ROWS.get(0)),new ArrayList<>(retained.history().values()));
        assertThrows(UnsupportedOperationException.class,()->retained.history().clear());
    }
    @Test void byteReservationEvictsBeforeBuildAndFailureCanRetry()throws Exception {
        var reject=new AtomicBoolean();
        try(var source=spool(ROWS);var cache=new RegionReplayCache(folder,source,4,28,1,e->{
            assertDoesNotThrow(()->assertTrue(copies()<=1));return !reject.get();})) {
            load(cache,0);reject.set(true);
            assertThrows(IOException.class,()->load(cache,1));assertEquals(0,cache.retainedRegions());assertEquals(0,cache.retainedBytes());assertEquals(0,copies());
            reject.set(false);load(cache,1);assertEquals(1,cache.retainedRegions());assertEquals(28,cache.retainedBytes());
            // A selected-row failure retains the valid root for retry.
            assertThrows(IOException.class,()->cache.load(region(1),RANGE,new Terrain(42),0,()->false));
            assertEquals(1,cache.retainedRegions());assertEquals(1,load(cache,1).history().size());
        }
    }
    @Test void cancellationOwnershipReentrancyAndClosedSourceRules()throws Exception {
        try(var source=spool(ROWS)) {
            var holder=new RegionReplayCache[1];var active=new AtomicBoolean();
            try(var cache=new RegionReplayCache(folder,source,2,56,1,e->{
                if(active.get())assertThrows(IOException.class,()->holder[0].close());return true;})) {
                holder[0]=cache;active.set(true);load(cache,0);
                assertThrows(CancellationException.class,()->cache.load(region(1),RANGE,new Terrain(42),1,()->true));
                assertEquals(1,cache.retainedRegions());
                var executor=Executors.newSingleThreadExecutor();
                try{assertTrue(executor.submit(()->{try{cache.close();return false;}catch(IOException expected){return true;}}).get());}
                finally{executor.shutdownNow();}
                source.close();assertEquals(1,load(cache,0).history().size());
                assertThrows(IOException.class,()->load(cache,1));assertEquals(1,cache.retainedRegions());
            }
        }
        assertEquals(0,copies());
    }
    @Test void failedDeletionRetainsOwnershipAndMidBuildCancellationCleansPrefix()throws Exception {
        var cancel=new AtomicBoolean();var trigger=new AtomicBoolean();
        try(var source=spool(ROWS);var cache=new RegionReplayCache(folder,source,1,28,1,e->{
            if(trigger.get())cancel.set(true);return true;})) {
            load(cache,0);
            Path path;try(var files=Files.list(folder)){path=files.filter(p->p.getFileName().toString().startsWith(".replay-cells-")).findFirst().orElseThrow();}
            byte[] saved=Files.readAllBytes(path);Files.delete(path);Files.createDirectory(path);Files.writeString(path.resolve("hold"),"synthetic");
            assertThrows(IOException.class,cache::close);assertEquals(1,cache.retainedRegions());assertEquals(28,cache.retainedBytes());
            assertThrows(IOException.class,()->load(cache,1));assertEquals(1,cache.retainedRegions());
            Files.delete(path.resolve("hold"));Files.delete(path);Files.write(path,saved);
            trigger.set(true);
            assertThrows(CancellationException.class,()->cache.load(region(1),RANGE,new Terrain(42),1,cancel::get));
            assertEquals(0,cache.retainedRegions());assertEquals(0,cache.retainedBytes());assertEquals(0,copies());
            trigger.set(false);cancel.set(false);load(cache,1);assertEquals(1,cache.retainedRegions());
        }
        assertEquals(0,copies());
    }
    @Test void emptyRootsStillRespectCountAndBudgetsRejectBeforeIo()throws Exception {
        try(var source=spool(List.of())) {
            assertThrows(IllegalArgumentException.class,()->new RegionReplayCache(folder,source,0,0,0,e->true));
            assertThrows(IllegalArgumentException.class,()->new RegionReplayCache(folder,source,5,0,0,e->true));
            assertThrows(IllegalArgumentException.class,()->new RegionReplayCache(folder,source,1,27,1,e->true));
            var cache=new RegionReplayCache(folder,source,1,0,0,e->true);
            try(cache){load(cache,0);load(cache,1);assertEquals(1,copies());assertEquals(0,cache.retainedBytes());
                assertThrows(IllegalArgumentException.class,()->cache.load(region(0),new RegionReplayPage.CellRange(0,65),new Terrain(42),1,()->false));}
            cache.close();assertThrows(IOException.class,()->load(cache,0));
        }
    }
}
