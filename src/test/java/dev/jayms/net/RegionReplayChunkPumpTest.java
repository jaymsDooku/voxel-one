package dev.jayms.net;

import dev.jayms.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegionReplayChunkPumpTest {
    // Unlike the read-view fixture, this fresh World has no attached read view:
    // implicit terrain is data-ready before and after pump work, but unloaded.
    // Detached completion must never install chunks or change save authority.
    @TempDir Path folder;
    static final ChunkPos SOURCE=RegionReplayReadViewTest.SOURCE;
    static final ChunkPos OTHER=new ChunkPos(-1,10,-1);
    RegionReplayReadViewTest fixture() {var test=new RegionReplayReadViewTest();test.folder=folder;return test;}
    static void ready(RegionReplayWorker.Binding.ChunkPump pump,int count)throws Exception {
        long end=System.nanoTime()+10_000_000_000L;
        do{pump.tick();if(pump.status().ready()==count)return;Thread.sleep(5);}while(System.nanoTime()<end);
        fail("Chunk pump did not complete desired geometry");
    }
    @Test void deduplicatedTicksRetainDetachedGeometryWithoutPromotion()throws Exception {
        var test=fixture();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var c=test.new Context("dedupe",0,entered,release)) {
            try(var pump=c.binding.openChunkPump(c.world,2,8192)) {
                assertTrue(c.world.replayDataReady(0,160,0));assertFalse(c.world.isLoaded(0,160,0));
                pump.desired(List.of(SOURCE,SOURCE));pump.tick();assertTrue(entered.await(10,TimeUnit.SECONDS));
                assertEquals(1,pump.status().desired());assertEquals(1,pump.status().pending());
                for(int i=0;i<5;i++)pump.tick();assertEquals(1,pump.status().pending());assertTrue(c.world.replayDataReady(0,160,0));
                release.countDown();ready(pump,1);assertEquals(0,pump.status().pending());assertEquals(Blocks.STONE,WorldVoxels.decode(pump.result(SOURCE).geometry().uniform(0,0,0,16)));assertFalse(c.world.isLoaded(0,160,0));
                pump.tick();assertEquals(0,pump.status().pending());assertEquals(4096,pump.status().stagedCells());assertEquals(0,c.world.editsVersion());assertTrue(c.world.getLoadedChunks().isEmpty());
                assertFalse(c.local.replayComplete());assertThrows(IOException.class,c.local::save);assertArrayEquals(c.original,Files.readAllBytes(c.path));
            }finally{release.countDown();}
        }
    }
    @Test void movingDemandCancelsObsoleteDecodeAndDropsItsGeometry()throws Exception {
        var test=fixture();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var c=test.new Context("move",0,entered,release)) {
            try(var pump=c.binding.openChunkPump(c.world,1,8192)) {
                pump.desired(List.of(SOURCE));pump.tick();assertTrue(entered.await(10,TimeUnit.SECONDS));
                pump.desired(List.of(OTHER));pump.tick();assertEquals(1,pump.status().pending());assertTrue(c.world.replayDataReady(0,160,0));
                release.countDown();ready(pump,1);assertNotNull(pump.result(OTHER));assertNull(pump.result(SOURCE));assertFalse(c.world.isLoaded(-16,160,-16));assertTrue(c.world.replayDataReady(0,160,0));
                assertTrue(c.world.getLoadedChunks().isEmpty());
                pump.desired(List.of());pump.tick();assertEquals(0,pump.status().desired());assertEquals(0,pump.status().pending());
            }finally{release.countDown();}
        }
    }
    @Test void fullSharedQueueReturnsBackpressureWithoutWaitingOrTreatingItAsFailure()throws Exception {
        var test=fixture();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var c=test.new Context("queue",0,entered,release)) {
            try(var pump=c.binding.openChunkPump(c.world,1,8192)) {
                var active=c.binding.submit(SOURCE,new RegionReplayPage.CellRange(0,64),4,c.world);assertTrue(entered.await(10,TimeUnit.SECONDS));
                var a=c.binding.submit(OTHER,new RegionReplayPage.CellRange(0,64),4,c.world);
                var b=c.binding.submit(OTHER,new RegionReplayPage.CellRange(64,128),4,c.world);
                pump.desired(List.of(SOURCE));pump.tick();assertTrue(pump.status().backpressure());assertEquals(0,pump.status().pending());assertEquals(0,pump.status().failed());
                release.countDown();RegionReplayWorkerTest.done(active);RegionReplayWorkerTest.done(a);RegionReplayWorkerTest.done(b);
                assertTrue(c.world.replayDataReady(0,160,0));ready(pump,1);assertFalse(pump.status().backpressure());assertEquals(Blocks.STONE,WorldVoxels.decode(pump.result(SOURCE).geometry().uniform(0,0,0,16)));assertFalse(c.world.isLoaded(0,160,0));
            }finally{release.countDown();}
        }
    }
    @Test void failedDecodeStaysFailedUntilExplicitRetryAndThenRecovers()throws Exception {
        var test=fixture();try(var c=test.context("failure",0);var pump=c.binding.openChunkPump(c.world,1,8192)) {
            Path source;try(var paths=Files.list(c.fixture.spool())){source=paths.filter(p->p.getFileName().toString().startsWith(".replay-spool-")).findFirst().orElseThrow();}
            var good=Files.readAllBytes(source);var bad=good.clone();bad[3]^=1;Files.write(source,bad);
            pump.desired(List.of(SOURCE));pump.tick();long end=System.nanoTime()+10_000_000_000L;
            while(pump.status().failed()==0&&System.nanoTime()<end){pump.tick();Thread.sleep(5);}
            assertEquals(1,pump.status().failed());assertNotNull(pump.failure(SOURCE));assertTrue(c.world.replayDataReady(0,160,0));
            Files.write(source,good);pump.desired(List.of(SOURCE));for(int i=0;i<5;i++)pump.tick();assertEquals(0,pump.status().pending());assertEquals(1,pump.status().failed());
            pump.retry(SOURCE);ready(pump,1);assertEquals(0,pump.status().failed());assertNull(pump.failure(SOURCE));
        }
    }
    @Test void inputOwnerAndModelFencesRejectBeforePublication()throws Exception {
        var test=fixture();try(var c=test.context("fences",0);var pump=c.binding.openChunkPump(c.world,2,8192)) {
            assertThrows(IOException.class,()->c.binding.openReadView(c.world,1));
            assertThrows(IOException.class,()->c.binding.openChunkPump(c.world,1,8192));
            pump.desired(List.of(SOURCE));pump.tick();
            assertThrows(IllegalArgumentException.class,()->pump.desired(List.of(SOURCE,OTHER,SOURCE)));assertEquals(1,pump.status().pending());
            assertThrows(NullPointerException.class,()->pump.desired(Arrays.asList(SOURCE,null)));assertEquals(1,pump.status().pending());
            assertThrows(IllegalArgumentException.class,()->pump.retry(OTHER));assertThrows(IOException.class,()->c.binding.openChunkPump(c.world,2,8192));
            var error=new AtomicReference<Throwable>();var thread=new Thread(()->{try{pump.tick();}catch(Throwable failure){error.set(failure);}});thread.start();thread.join(5000);assertFalse(thread.isAlive());assertInstanceOf(IOException.class,error.get());
            c.local.models=c.local.models.copy();assertThrows(IOException.class,pump::tick);assertFalse(c.world.isLoaded(0,160,0));
        }
    }
    @Test void closeCancelsOwnedWorkWithoutClosingOrWaitingForWorker()throws Exception {
        var test=fixture();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var c=test.new Context("close",0,entered,release)) {
            var pump=c.binding.openChunkPump(c.world,1,8192);
            try {
                pump.desired(List.of(SOURCE));pump.tick();assertTrue(entered.await(10,TimeUnit.SECONDS));pump.close();pump.close();
                assertTrue(pump.status().closed());assertEquals(0,pump.status().pending());assertEquals(RegionReplayWorker.State.RUNNING,c.worker.status().state());
                assertThrows(IOException.class,pump::tick);assertThrows(IOException.class,()->pump.desired(List.of(SOURCE)));assertFalse(c.world.isLoaded(0,160,0));
            }finally{release.countDown();pump.close();}
        }
    }
}
