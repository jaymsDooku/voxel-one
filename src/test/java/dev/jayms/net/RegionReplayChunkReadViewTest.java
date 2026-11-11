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

class RegionReplayChunkReadViewTest {
    @TempDir Path folder;
    static final ChunkPos SOURCE=RegionReplayReadViewTest.SOURCE;
    RegionReplayReadViewTest fixture(){var test=new RegionReplayReadViewTest();test.folder=folder;return test;}
    @Test void completeChunksServeExactWorldQueriesWithoutLiveMeshOrSaveAuthority()throws Exception {
        try(var c=fixture().context("queries",0);var pump=c.binding.openChunkReadPump(c.world,2,8192)) {
            assertFalse(c.world.replayDataReady(0,160,0));assertThrows(World.UnloadedReplayCellException.class,()->c.world.sample(0,160,0));
            pump.desired(List.of(SOURCE,new ChunkPos(0,0,0)));RegionReplayChunkPumpTest.ready(pump,2);
            var complete=new LocalGame(c.path,0);var reference=new WorldVoxels(new Terrain(complete.seed,complete.generatorVersion));complete.edits.values().forEach(reference::apply);
            for(int y=160;y<176;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
                assertTrue(c.world.replayDataReady(x,y,z));assertEquals(reference.type(x,y,z),c.world.sample(x,y,z));
            }
            var detail=RegionReplayReadViewTest.DETAIL;
            assertEquals(WorldVoxels.encode(detail),c.world.value(2*16+1,160*16+2,3));
            assertEquals(reference.type(1,160,0),c.world.sample(1,160,0));assertTrue(Blocks.isModel(c.world.sample(1,160,0)));
            assertEquals(reference.type(0,0,0),c.world.getBlock(0,0,0));
            var retained=c.world.cell(0,160,0);assertThrows(IllegalStateException.class,()->retained.set(0,0,0,0));
            assertFalse(c.world.isLoaded(0,160,0));assertFalse(c.world.nearbyReady(0,160,1));assertTrue(c.world.getLoadedChunks().isEmpty());
            assertThrows(IllegalStateException.class,()->c.world.setBlock(0,160,0,0));assertThrows(IllegalStateException.class,c.world::editsSnapshot);
            assertFalse(c.local.replayComplete());assertThrows(IOException.class,c.local::save);assertArrayEquals(c.original,Files.readAllBytes(c.path));
        }
    }
    @Test void evictionAndNegativeCoordinatesNeverFallBackToImplicitTerrain()throws Exception {
        try(var c=fixture().context("eviction",0);var pump=c.binding.openChunkReadPump(c.world,1,8192)) {
            pump.desired(List.of(SOURCE));RegionReplayChunkPumpTest.ready(pump,1);var retained=c.world.cell(0,160,0);
            var other=new ChunkPos(-1,10,-1);pump.desired(List.of(other));
            assertFalse(c.world.replayDataReady(0,160,0));assertThrows(World.UnloadedReplayCellException.class,()->c.world.sample(0,160,0));
            RegionReplayChunkPumpTest.ready(pump,1);assertTrue(c.world.replayDataReady(-16,160,-16));assertTrue(c.world.replayDataReady(-1,175,-1));
            assertEquals(0,c.world.value(-256,2560,-256));assertFalse(c.world.replayDataReady(-17,160,-16));
            assertFalse(c.world.replayDataReady(Integer.MIN_VALUE,160,0));assertThrows(World.UnloadedReplayCellException.class,()->c.world.cell(Integer.MIN_VALUE,160,0));
            pump.desired(List.of());assertFalse(c.world.replayDataReady(-16,160,-16));assertEquals(WorldVoxels.encode(Blocks.STONE),retained.get(0,0,0));
        }
    }
    @Test void pendingCancelledAndClosedCoverageStaysUnavailable()throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var c=fixture().new Context("pending",0,entered,release)) {
            var pump=c.binding.openChunkReadPump(c.world,1,8192);
            try {
                pump.desired(List.of(SOURCE));pump.tick();assertTrue(entered.await(10,TimeUnit.SECONDS));
                assertFalse(c.world.replayDataReady(0,160,0));assertThrows(World.UnloadedReplayCellException.class,()->c.world.cell(0,160,0));
                pump.desired(List.of());assertFalse(c.world.replayDataReady(0,160,0));
                pump.close();pump.close();assertThrows(IllegalStateException.class,()->c.world.sample(0,160,0));
                assertTrue(pump.status().closed());assertEquals(0,pump.status().ready());assertEquals(RegionReplayWorker.State.RUNNING,c.worker.status().state());
            }finally{release.countDown();pump.close();}
        }
    }
    @Test void failedDecodeNeverGrantsCoverageAndRequiresExplicitRetry()throws Exception {
        try(var c=fixture().context("failure",0);var pump=c.binding.openChunkReadPump(c.world,1,8192)) {
            Path source;try(var paths=Files.list(c.fixture.spool())){source=paths.filter(p->p.getFileName().toString().startsWith(".replay-spool-")).findFirst().orElseThrow();}
            var good=Files.readAllBytes(source);var bad=good.clone();bad[3]^=1;Files.write(source,bad);
            pump.desired(List.of(SOURCE));pump.tick();long end=System.nanoTime()+10_000_000_000L;
            while(pump.status().failed()==0&&System.nanoTime()<end){pump.tick();Thread.sleep(5);}
            assertEquals(1,pump.status().failed());assertFalse(c.world.replayDataReady(0,160,0));assertThrows(World.UnloadedReplayCellException.class,()->c.world.getBlock(0,160,0));
            Files.write(source,good);pump.desired(List.of(SOURCE));pump.tick();assertEquals(1,pump.status().failed());assertFalse(c.world.replayDataReady(0,160,0));
            pump.retry(SOURCE);RegionReplayChunkPumpTest.ready(pump,1);assertEquals(Blocks.STONE,c.world.getBlock(0,160,0));
            assertFalse(c.local.replayComplete());assertArrayEquals(c.original,Files.readAllBytes(c.path));
        }
    }
    @Test void ownerModelAndViewIdentityFencesProtectAllReads()throws Exception {
        try(var c=fixture().context("fences",0)) {
            var before=c.world.beginReplayFence();
            try(var pump=c.binding.openChunkReadPump(c.world,1,8192)) {
                assertThrows(ConcurrentModificationException.class,()->before.validate(c.world));
                assertThrows(IOException.class,()->c.binding.openReadView(c.world,1));assertThrows(IOException.class,()->c.binding.openChunkReadPump(c.world,1,8192));
                pump.desired(List.of(SOURCE));RegionReplayChunkPumpTest.ready(pump,1);
                var error=new AtomicReference<Throwable>();var thread=new Thread(()->{try{c.world.sample(0,160,0);}catch(Throwable failure){error.set(failure);}});
                thread.start();thread.join(5000);assertFalse(thread.isAlive());assertInstanceOf(IllegalStateException.class,error.get());
                // An off-owner read must not invalidate owner coverage.
                assertEquals(Blocks.STONE,c.world.sample(0,160,0));
                c.local.models=c.local.models.copy();assertThrows(IllegalStateException.class,()->c.world.replayDataReady(0,160,0));assertThrows(IllegalStateException.class,()->c.world.cell(0,160,0));
            }
        }
    }
}
