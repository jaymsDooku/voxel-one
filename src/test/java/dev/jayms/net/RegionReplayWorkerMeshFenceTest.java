package dev.jayms.net;

import dev.jayms.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegionReplayWorkerMeshFenceTest {
    @TempDir Path folder;
    RegionReplayWorkerNeighboursTest fixture() {var fixture=new RegionReplayWorkerNeighboursTest();fixture.folder=folder;return fixture;}
    static final ChunkPos CENTER=RegionReplayWorkerNeighboursTest.REGION;
    static final ChunkPos NEIGHBOR=new ChunkPos(CENTER.chunkX()+1,CENTER.chunkY(),CENTER.chunkZ());
    @Test void readyChunkRejectsAdjacentResidencyButDistantChangesAndPagesRemainValid()throws Exception {
        var f=fixture();var path=f.fixture();var worker=f.worker(path,e->true);
        try(var world=new World(42)) {
            var request=worker.submitChunkWithNeighboursFenced(CENTER,8192,63,RegionReplayWorkerTest.REV,world);RegionReplayWorkerTest.done(request);
            assertEquals(180,request.chunkResult(RegionReplayWorkerTest.REV,world).mesh().indices().length);
            var page=worker.submitFenced(CENTER,new RegionReplayPage.CellRange(0,64),12,RegionReplayWorkerTest.REV,world);RegionReplayWorkerTest.done(page);
            var baseline=world.beginReplayFence();var distant=new ChunkPos(8,10,8);world.addChunk(distant,ChunkGenerator.generate(distant,world.terrain()));baseline.validate(world);
            assertNotNull(request.chunkResult(RegionReplayWorkerTest.REV,world));
            world.addChunk(NEIGHBOR,ChunkGenerator.generate(NEIGHBOR,world.terrain()));baseline.validate(world);
            assertThrows(IOException.class,()->request.chunkResult(RegionReplayWorkerTest.REV,world));
            assertNotNull(page.result(RegionReplayWorkerTest.REV,world));
            var retry=worker.submitChunkFenced(CENTER,8192,RegionReplayWorkerTest.REV,world);RegionReplayWorkerTest.done(retry);assertNotNull(retry.chunkResult(RegionReplayWorkerTest.REV,world));
            world.getLoadedChunks().get(NEIGHBOR).markDirty();baseline.validate(world);
            assertThrows(IOException.class,()->retry.chunkResult(RegionReplayWorkerTest.REV,world));
        }finally{RegionReplayWorkerTest.closed(worker);f.noSpools();}
    }
    @Test void adjacentChangeWhileQueuedRejectsLateResultWithoutMutatingWorld()throws Exception {
        var f=fixture();var path=f.fixture();var spool=Files.createDirectories(folder.resolve("spool"));var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var worker=new RegionReplayWorker(spool,(cancel,progress)->{entered.countDown();RegionReplayWorkerTest.release(release);return LocalGame.openRegionalReadback(path,spool,RegionReplayWorkerTest.REV,cancel,progress);},1,1,336,12);
        try(var world=new World(42)) {
            assertTrue(entered.await(10,TimeUnit.SECONDS));var request=worker.submitChunkFenced(CENTER,8192,RegionReplayWorkerTest.REV,world);
            world.addChunk(NEIGHBOR,ChunkGenerator.generate(NEIGHBOR,world.terrain()));var membership=world.getLoadedChunks().size();
            release.countDown();RegionReplayWorkerTest.done(request);assertEquals(RegionReplayWorker.Phase.READY,request.outcome().phase());
            assertThrows(IOException.class,()->request.chunkResult(RegionReplayWorkerTest.REV,world));assertEquals(membership,world.getLoadedChunks().size());assertEquals(0,world.editsVersion());
        }finally{release.countDown();RegionReplayWorkerTest.closed(worker);f.noSpools();}
    }
}
