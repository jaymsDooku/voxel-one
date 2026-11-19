package dev.jayms.net;

import dev.jayms.World;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class WorkerSaveStagingTest {
    @TempDir Path folder;
    ReadbackMetadataTest fixture(String name)throws IOException{var f=new ReadbackMetadataTest();f.folder=Files.createDirectories(folder.resolve(name));return f;}
    long prefixes(Path folder)throws IOException{try(var paths=Files.list(folder)){return paths.filter(p->p.getFileName().toString().startsWith(".structure-offline-")).count();}}
    ReadbackSaveSnapshot snapshot(LocalGame local,World world)throws Exception {
        var edit=new Protocol.Edit(0,160,0,0);world.apply(edit);return ReadbackSaveSnapshot.capture(local,world,RegionReplayOverlay.capture(List.of(edit),()->false),()->false);
    }
    @Test public void stagesOnSourceWorkerAndTransfersOnceWithoutPublishing()throws Exception {
        var f=fixture("stage");var path=f.fixture();var before=Files.readAllBytes(path);var worker=f.worker(path);
        try {
            ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
            try(var world=new World(local.seed,local.models,local.generatorVersion)) {
                var snapshot=snapshot(local,world);var request=worker.submitSave(snapshot,ReadbackMetadataTest.REV);RegionReplayWorkerTest.done(request);assertEquals(RegionReplayWorker.Phase.READY,request.outcome().phase());assertFalse(request.cancel());assertThrows(IOException.class,()->request.result(ReadbackMetadataTest.REV));assertArrayEquals(before,Files.readAllBytes(path));assertEquals(1,prefixes(f.folder));
                assertThrows(IOException.class,()->worker.submitSave(snapshot,ReadbackMetadataTest.REV));assertThrows(IOException.class,()->request.takePrepared(SavedPagingFactoryTest.NEXT));
                var executor=Executors.newSingleThreadExecutor();try{executor.submit(()->{assertThrows(IOException.class,()->request.takePrepared(ReadbackMetadataTest.REV));return null;}).get(10,TimeUnit.SECONDS);}finally{executor.shutdownNow();}
                try(var prepared=request.takePrepared(ReadbackMetadataTest.REV)) {
                    assertThrows(IOException.class,()->request.takePrepared(ReadbackMetadataTest.REV));var publication=prepared.authorize(()->false);
                    var writer=Executors.newSingleThreadExecutor();try{writer.submit(()->{publication.publish();publication.close();return null;}).get(10,TimeUnit.SECONDS);}finally{writer.shutdownNow();}
                }
                assertEquals(0,prefixes(f.folder));assertTrue(new LocalGame(path,0).edits.values().contains(new Protocol.Edit(0,160,0,0)));assertEquals(1,world.editsVersion());assertFalse(local.replayComplete());assertThrows(IOException.class,local::save);
                var page=worker.submit(PendingReplayOverlayTest.REGION,PendingReplayOverlayTest.RANGE,8,ReadbackMetadataTest.REV);RegionReplayWorkerTest.done(page);assertNotNull(page.result(ReadbackMetadataTest.REV)); // Immutable old source still serves pages; no implicit installation.
            }
        }finally{RegionReplayWorkerTest.closed(worker);}
        assertEquals(0,prefixes(f.folder));f.noSpools();
    }
    @Test public void unclaimedOutputClosesOnProducerAndStaleOwnerCannotAuthorize()throws Exception {
        var f=fixture("cleanup");var path=f.fixture();var before=Files.readAllBytes(path);var worker=f.worker(path);
        try {
            ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
            try(var world=new World(local.seed,local.models,local.generatorVersion)) {
                var snapshot=snapshot(local,world);var first=worker.submitSave(snapshot,ReadbackMetadataTest.REV);RegionReplayWorkerTest.done(first);assertEquals(1,prefixes(f.folder));
                try(var staged=first.takePrepared(ReadbackMetadataTest.REV)){local.health--;assertThrows(IOException.class,()->staged.authorize(()->false));}assertEquals(0,prefixes(f.folder));
                var next=ReadbackSaveSnapshot.capture(local,world,RegionReplayOverlay.capture(List.of(new Protocol.Edit(0,160,0,0)),()->false),()->false);
                var abandoned=worker.submitSave(next,ReadbackMetadataTest.REV);RegionReplayWorkerTest.done(abandoned);assertEquals(1,prefixes(f.folder));RegionReplayWorkerTest.closed(worker);assertEquals(0,prefixes(f.folder));assertThrows(IOException.class,()->abandoned.takePrepared(ReadbackMetadataTest.REV));assertArrayEquals(before,Files.readAllBytes(path));
            }
        }finally{RegionReplayWorkerTest.closed(worker);}f.noSpools();
    }
    @Test public void queuedCancellationSharesPageBackpressureAndAllowsRetry()throws Exception {
        var f=fixture("queue");var path=f.fixture();var before=Files.readAllBytes(path);var spool=f.spool();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var block=new AtomicBoolean();
        var worker=new RegionReplayWorker(spool,(cancel,progress)->{
            var source=LocalGame.openRegionalReadback(path,spool,ReadbackMetadataTest.REV,cancel,progress);
            return new RegionReplayWorker.Source(source.spool(),source.terrain(),edit->{if(block.getAndSet(false)){entered.countDown();try{RegionReplayWorkerTest.release(release);}catch(IOException e){throw new UncheckedIOException(e);}}return source.allowed().test(edit);},source.revision(),source.fingerprint(),source.metadata());
        },1,1,56,2);
        try {
            ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
            try(var world=new World(local.seed,local.models,local.generatorVersion)) {
                var snapshot=snapshot(local,world);block.set(true);var page=worker.submit(PendingReplayOverlayTest.REGION,PendingReplayOverlayTest.RANGE,8,ReadbackMetadataTest.REV);assertTrue(entered.await(10,TimeUnit.SECONDS));
                var cancelled=worker.submitSave(snapshot,ReadbackMetadataTest.REV);assertThrows(IOException.class,()->worker.submitSave(snapshot,ReadbackMetadataTest.REV));assertTrue(cancelled.cancel());var retry=worker.submitSave(snapshot,ReadbackMetadataTest.REV);release.countDown();RegionReplayWorkerTest.done(page);RegionReplayWorkerTest.done(retry);assertThrows(CancellationException.class,()->cancelled.takePrepared(ReadbackMetadataTest.REV));
                try(var staged=retry.takePrepared(ReadbackMetadataTest.REV)){assertNotNull(staged);}assertEquals(0,prefixes(f.folder));assertArrayEquals(before,Files.readAllBytes(path));
            }
        }finally{release.countDown();RegionReplayWorkerTest.closed(worker);}f.noSpools();
    }
}
