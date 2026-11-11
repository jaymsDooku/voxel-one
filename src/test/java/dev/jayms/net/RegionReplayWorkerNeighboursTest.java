package dev.jayms.net;

import dev.jayms.*;
import dev.jayms.net.model.ModelLibrary;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegionReplayWorkerNeighboursTest {
    @TempDir Path folder;
    final AtomicReference<Terrain> sourceTerrain=new AtomicReference<>();
    static final ChunkPos REGION=new ChunkPos(-2,10,-3);
    static final RegionReplayWorker.Revision REV=RegionReplayWorkerTest.REV;
    Path fixture()throws Exception {
        var rows=new WorldVoxels.History();
        int[][] positions={{0,8,8},{-1,8,8},{15,8,8},{16,8,8},{8,0,8},{8,-1,8},{8,15,8},{8,16,8},{8,8,0},{8,8,-1},{8,8,15},{8,8,16}};
        for(var p:positions)WorldVoxels.remember(rows,new Protocol.Edit(-32+p[0],160+p[1],-48+p[2],Blocks.STONE));
        var path=folder.resolve("world.dat");
        try(var save=StagedOfflineSave.stageRegions(path,42,Terrain.CURRENT_VERSION,new ModelLibrary(),rows,()->false,n->{})){save.publish(new Inventory(),20,List.of(),0);}
        return path;
    }
    RegionReplayWorker worker(Path path,Predicate<Protocol.Edit> policy)throws Exception {
        var spool=Files.createDirectories(folder.resolve("spool"));
        return new RegionReplayWorker(spool,(cancel,progress)->{
            var source=LocalGame.openRegionalReadback(path,spool,REV,cancel,progress);
            sourceTerrain.set(source.terrain());
            return new RegionReplayWorker.Source(source.spool(),source.terrain(),e->source.allowed().test(e)&&policy.test(e),REV,source.fingerprint(),source.metadata());
        },1,1,12*28,12);
    }
    void noSpools()throws Exception{try(var files=Files.list(folder.resolve("spool"))){assertEquals(0,files.count());}}
    @Test void allSixMaskBitsUseSameSourceAndDefaultKeepsUnknownFaces()throws Exception {
        var path=fixture();var original=Files.readAllBytes(path);var worker=worker(path,e->true);
        try {
            ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(REV);
            try(var world=new World(local.seed,local.models,local.generatorVersion)) {
                var binding=worker.bindTarget(local,world);
                dev.jayms.net.model.SparseVoxelOctree baseline=null;
                for(int mask:new int[]{0,1,2,4,8,16,32,63}) {
                    var request=binding.submitChunkWithNeighbours(REGION,8192,mask,world);RegionReplayWorkerTest.done(request);
                    assertEquals(RegionReplayWorker.Phase.READY,request.outcome().phase());
                    var result=binding.chunkResult(request,world);
                    assertEquals(216-6*Integer.bitCount(mask),result.mesh().indices().length,"mask "+mask);
                    assertEquals(4096,request.completedCells());assertEquals(6,result.editedCells());
                    if(baseline==null)baseline=result.geometry();else assertTrue(baseline.sameValues(result.geometry()));
                    assertThrows(IOException.class,()->request.chunkResult(REV,world));
                }
                assertTrue(world.getLoadedChunks().isEmpty());assertEquals(0,world.editsVersion());assertFalse(world.isLoaded(-32,160,-48));
                assertFalse(local.replayComplete());assertThrows(IOException.class,local::save);assertArrayEquals(original,Files.readAllBytes(path));
            }
        }finally{RegionReplayWorkerTest.closed(worker);noSpools();}
    }
    @Test void invalidMasksAndWorldEdgesRejectBeforeQueueAndCancellationFreesSharedSlot()throws Exception {
        var path=fixture();var entered=new CountDownLatch(1);var proceed=new CountDownLatch(1);
        var spool=Files.createDirectories(folder.resolve("spool"));
        var worker=new RegionReplayWorker(spool,(cancel,progress)->{entered.countDown();RegionReplayWorkerTest.release(proceed);return LocalGame.openRegionalReadback(path,spool,REV,cancel,progress);},1,1,336,12);
        try(var world=new World(42)) {
            assertTrue(entered.await(10,TimeUnit.SECONDS));
            for(int mask:new int[]{-1,64,Integer.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->worker.submitChunkWithNeighboursFenced(REGION,8192,mask,REV,world));
            assertThrows(IOException.class,()->worker.submitChunkWithNeighboursFenced(new ChunkPos(62499,10,0),8192,2,REV,world));
            assertThrows(IOException.class,()->worker.submitChunkWithNeighboursFenced(new ChunkPos(Integer.MAX_VALUE,10,0),8192,1,REV,world));
            var request=worker.submitChunkWithNeighboursFenced(REGION,8192,63,REV,world);
            assertThrows(IOException.class,()->worker.submitChunkFenced(REGION,8192,REV,world));assertTrue(request.cancel());
            var next=worker.submitChunkFenced(REGION,8192,REV,world);proceed.countDown();RegionReplayWorkerTest.done(next);
            assertNotNull(next.chunkResult(REV,world));assertThrows(CancellationException.class,()->request.chunkResult(REV,world));
        }finally{proceed.countDown();RegionReplayWorkerTest.closed(worker);noSpools();}
    }
    @Test void cancellationDuringNeighbourReadNeverPublishesAndUnchangedRetryWorks()throws Exception {
        var path=fixture();var entered=new CountDownLatch(1);var proceed=new CountDownLatch(1);var block=new AtomicBoolean(true);
        var active=new AtomicReference<RegionReplayWorker.Request>();var assigned=new CountDownLatch(1);
        var worker=worker(path,e->{try{RegionReplayWorkerTest.release(assigned);}catch(IOException failure){throw new UncheckedIOException(failure);}var request=active.get();if(request!=null&&request.completedCells()==4096&&block.getAndSet(false)){entered.countDown();try{RegionReplayWorkerTest.release(proceed);}catch(IOException failure){throw new UncheckedIOException(failure);}}return true;});
        try(var world=new World(42)) {
            var request=worker.submitChunkWithNeighboursFenced(REGION,8192,63,REV,world);active.set(request);assigned.countDown();
            assertTrue(entered.await(10,TimeUnit.SECONDS));assertEquals(4096,request.completedCells());
            assertEquals(RegionReplayWorker.Phase.LOADING,request.outcome().phase());assertTrue(request.cancel());
            var retry=worker.submitChunkWithNeighboursFenced(REGION,8192,63,REV,world);active.set(null);proceed.countDown();RegionReplayWorkerTest.done(retry);
            assertThrows(CancellationException.class,()->request.chunkResult(REV,world));assertEquals(180,retry.chunkResult(REV,world).mesh().indices().length);
            world.apply(new Protocol.Edit(0,160,0,Blocks.STONE));assertThrows(IOException.class,()->retry.chunkResult(REV,world));
        }finally{assigned.countDown();proceed.countDown();RegionReplayWorkerTest.closed(worker);noSpools();}
    }
    @Test void failedNeighbourPolicyCannotPublishCenterOrPoisonWorker()throws Exception {
        var path=fixture();var active=new AtomicReference<RegionReplayWorker.Request>();var assigned=new CountDownLatch(1);var reject=new AtomicBoolean(true);
        var worker=worker(path,e->{try{RegionReplayWorkerTest.release(assigned);}catch(IOException failure){throw new UncheckedIOException(failure);}var request=active.get();return !(request!=null&&request.completedCells()==4096&&reject.get());});
        try(var world=new World(42)) {
            var request=worker.submitChunkWithNeighboursFenced(REGION,8192,63,REV,world);active.set(request);assigned.countDown();RegionReplayWorkerTest.done(request);
            assertEquals(RegionReplayWorker.Phase.FAILED,request.outcome().phase());assertEquals(4096,request.completedCells());assertThrows(IOException.class,()->request.chunkResult(REV,world));
            reject.set(false);active.set(null);var retry=worker.submitChunkWithNeighboursFenced(REGION,8192,63,REV,world);RegionReplayWorkerTest.done(retry);
            assertEquals(180,retry.chunkResult(REV,world).mesh().indices().length);assertTrue(world.getLoadedChunks().isEmpty());
        }finally{assigned.countDown();RegionReplayWorkerTest.closed(worker);noSpools();}
    }
    @Test void sourceTerrainChangeDuringNeighbourLoadRejectsCompleteCenter()throws Exception {
        var path=fixture();var original=Files.readAllBytes(path);var active=new AtomicReference<RegionReplayWorker.Request>();
        var assigned=new CountDownLatch(1);var change=new AtomicBoolean(true);
        var worker=worker(path,e->{
            try{RegionReplayWorkerTest.release(assigned);}catch(IOException failure){throw new UncheckedIOException(failure);}
            var request=active.get();if(request!=null&&request.completedCells()==4096&&change.getAndSet(false))sourceTerrain.get().stressGrid(null);
            return true;
        });
        try(var world=new World(42)) {
            var request=worker.submitChunkWithNeighboursFenced(REGION,8192,63,REV,world);active.set(request);assigned.countDown();RegionReplayWorkerTest.done(request);
            assertEquals(RegionReplayWorker.Phase.FAILED,request.outcome().phase());assertFalse(change.get());
            assertThrows(IOException.class,()->request.chunkResult(REV,world));assertArrayEquals(original,Files.readAllBytes(path));assertTrue(world.getLoadedChunks().isEmpty());
            var retry=worker.submitChunkWithNeighboursFenced(REGION,8192,63,REV,world);RegionReplayWorkerTest.done(retry);
            assertEquals(RegionReplayWorker.Phase.FAILED,retry.outcome().phase()); // Metadata generation remains stale.
        }finally{assigned.countDown();RegionReplayWorkerTest.closed(worker);noSpools();}
    }
}
