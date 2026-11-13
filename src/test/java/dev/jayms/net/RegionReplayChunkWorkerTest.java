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

class RegionReplayChunkWorkerTest {
    @TempDir Path folder;
    RegionReplayWorkerTest basic()throws Exception {
        var fixture=new RegionReplayWorkerTest();fixture.folder=Files.createDirectories(folder.resolve("basic"));return fixture;
    }
    ReadbackMetadataTest metadata(String name)throws Exception {
        var fixture=new ReadbackMetadataTest();fixture.folder=Files.createDirectories(folder.resolve(name));return fixture;
    }
    @Test void boundChunkIsCompleteDetachedAndRequiresActualSourceAndWorldFences()throws Exception {
        var fixture=metadata("bound");var path=fixture.fixture();byte[] before=Files.readAllBytes(path);
        var expected=new LocalGame(path,0);var geometry=new WorldVoxels(new Terrain(expected.seed));expected.edits.values().forEach(geometry::apply);
        var worker=fixture.worker(path);
        try {
            ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
            try(var world=new World(local.seed,local.models,local.generatorVersion);var other=new World(local.seed,local.models,local.generatorVersion)) {
                var binding=worker.bindTarget(local,world);
                var request=binding.submitChunk(RegionReplayBindingTest.REGION,8192,world);RegionReplayWorkerTest.done(request);
                assertEquals(RegionReplayWorker.Phase.READY,request.outcome().phase());
                assertThrows(IOException.class,()->request.result(ReadbackMetadataTest.REV));
                assertThrows(IOException.class,()->request.chunkResult(ReadbackMetadataTest.REV,world));
                assertThrows(IOException.class,()->binding.result(request,world));
                assertThrows(IOException.class,()->binding.chunkResult(request,other));
                var result=binding.chunkResult(request,world);
                for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
                    assertTrue(geometry.cell(x,160+y,z).sameValues(result.geometry().region(x*16,y*16,z*16,16)));
                assertTrue(world.getLoadedChunks().isEmpty());assertEquals(0,world.editsVersion());assertFalse(world.isLoaded(0,160,0));
                assertFalse(local.replayComplete());assertThrows(IOException.class,local::save);assertArrayEquals(before,Files.readAllBytes(path));
                world.apply(new Protocol.Edit(8,160,0,Blocks.GLASS));
                assertThrows(IOException.class,()->binding.chunkResult(request,world));
                RegionReplayWorkerTest.closed(worker);assertEquals(2,result.editedCells());
                assertThrows(IllegalStateException.class,()->result.geometry().fill(0,0,0,1,1,1,1));
            }
        }finally{RegionReplayWorkerTest.closed(worker);fixture.noSpools();}
    }
    @Test void pagesAndChunksShareOpeningQueueAndCancellationCapacity()throws Exception {
        var fixture=basic();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var open=fixture.factory(e->true);
        var worker=fixture.worker((cancel,progress)->{entered.countDown();RegionReplayWorkerTest.release(release);return open.open(cancel,progress);},1);
        try(var world=new World(42)) {
            assertTrue(entered.await(10,TimeUnit.SECONDS));
            var chunk=worker.submitChunkFenced(RegionReplayWorkerTest.REGION,8192,RegionReplayWorkerTest.REV,world);
            assertThrows(IOException.class,()->RegionReplayWorkerTest.request(worker));assertTrue(chunk.cancel());
            var page=RegionReplayWorkerTest.request(worker);
            assertThrows(IOException.class,()->worker.submitChunkFenced(RegionReplayWorkerTest.REGION,8192,RegionReplayWorkerTest.REV,world));
            worker.close();assertEquals(1,release.getCount());assertEquals(RegionReplayWorker.Phase.CANCELLED,page.outcome().phase());
            assertEquals(RegionReplayWorker.Phase.CANCELLED,chunk.outcome().phase());
        }finally{release.countDown();RegionReplayWorkerTest.closed(worker);fixture.noFiles();}
    }
    @Test void cancellationAndGeometryFailureLeaveWorkerUsableForPageAndChunkRetry()throws Exception {
        var fixture=basic();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var block=new AtomicBoolean(true);
        var worker=fixture.worker(fixture.factory(e->{if(block.getAndSet(false)){entered.countDown();try{RegionReplayWorkerTest.release(release);}catch(IOException error){throw new UncheckedIOException(error);}}return true;}),2);
        try(var world=new World(42)) {
            var first=worker.submitChunkFenced(RegionReplayWorkerTest.REGION,8192,RegionReplayWorkerTest.REV,world);
            assertTrue(entered.await(10,TimeUnit.SECONDS));assertTrue(first.cancel());
            var page=RegionReplayWorkerTest.request(worker);release.countDown();RegionReplayWorkerTest.done(page);
            assertEquals(1,page.result(RegionReplayWorkerTest.REV).history().size());
            assertThrows(IOException.class,()->page.chunkResult(RegionReplayWorkerTest.REV,world));
            assertThrows(CancellationException.class,()->first.chunkResult(RegionReplayWorkerTest.REV,world));
            var failed=worker.submitChunkFenced(RegionReplayWorkerTest.REGION,0,RegionReplayWorkerTest.REV,world);RegionReplayWorkerTest.done(failed);
            assertEquals(RegionReplayWorker.Phase.FAILED,failed.outcome().phase());assertThrows(IOException.class,()->failed.chunkResult(RegionReplayWorkerTest.REV,world));
            var good=worker.submitChunkFenced(RegionReplayWorkerTest.REGION,8192,RegionReplayWorkerTest.REV,world);RegionReplayWorkerTest.done(good);
            assertEquals(1,good.chunkResult(RegionReplayWorkerTest.REV,world).editedCells());
            assertThrows(IOException.class,()->good.chunkResult(new RegionReplayWorker.Revision(9,2,3),world));
            var executor=Executors.newSingleThreadExecutor();
            try{assertTrue(executor.submit(()->{try{good.chunkResult(RegionReplayWorkerTest.REV,world);return false;}catch(IOException expected){return true;}}).get());}
            finally{executor.shutdownNow();}
            world.terrain().stressGrid(null);assertThrows(IOException.class,()->good.chunkResult(RegionReplayWorkerTest.REV,world));
        }finally{release.countDown();RegionReplayWorkerTest.closed(worker);fixture.noFiles();}
    }
    @Test void identicalTagsCannotSubstituteForeignBindingAndModelMutationInvalidatesReadyChunk()throws Exception {
        var fixture=metadata("foreign");var path=fixture.fixture();var first=fixture.worker(path);var second=fixture.worker(path);
        try {
            ReadbackMetadataTest.ready(first);ReadbackMetadataTest.ready(second);
            var a=first.metadataResult(ReadbackMetadataTest.REV);var b=second.metadataResult(ReadbackMetadataTest.REV);
            try(var aw=new World(a.seed,a.models,a.generatorVersion);var bw=new World(b.seed,b.models,b.generatorVersion)) {
                var ab=first.bindTarget(a,aw);var bb=second.bindTarget(b,bw);
                var ar=ab.submitChunk(RegionReplayBindingTest.REGION,8192,aw);var br=bb.submitChunk(RegionReplayBindingTest.REGION,8192,bw);
                RegionReplayWorkerTest.done(ar);RegionReplayWorkerTest.done(br);
                assertThrows(IOException.class,()->ab.chunkResult(br,aw));assertThrows(IOException.class,()->bb.chunkResult(ar,bw));
                assertNotNull(ab.chunkResult(ar,aw));a.models=a.models.copy();
                assertThrows(IOException.class,()->ab.chunkResult(ar,aw));assertNotNull(bb.chunkResult(br,bw));
            }
        }finally{RegionReplayWorkerTest.closed(first);RegionReplayWorkerTest.closed(second);fixture.noSpools();}
    }
}
