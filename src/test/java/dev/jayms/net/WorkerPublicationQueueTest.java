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

public class WorkerPublicationQueueTest {
    @TempDir Path folder;
    WorkerSaveStagingTest fixtures()throws IOException{var t=new WorkerSaveStagingTest();t.folder=Files.createDirectories(folder.resolve("contexts"));return t;}
    ReadbackSaveSnapshot.Publication staged(RegionReplayWorker worker,ReadbackSaveSnapshot snapshot)throws Exception {
        var request=worker.submitSave(snapshot,ReadbackMetadataTest.REV);RegionReplayWorkerTest.done(request);
        try(var prepared=request.takePrepared(ReadbackMetadataTest.REV)){return prepared.authorize(()->false);}
    }
    @Test public void publishesOnSourceWorkerAndRetainsDurableReceiptAfterClose()throws Exception {
        var t=fixtures();var f=t.fixture("publish");var path=f.fixture();var worker=f.worker(path);var other=f.worker(path);
        try {
            ReadbackMetadataTest.ready(worker);ReadbackMetadataTest.ready(other);var local=worker.metadataResult(ReadbackMetadataTest.REV);other.metadataResult(ReadbackMetadataTest.REV);
            try(var world=new World(local.seed,local.models,local.generatorVersion)) {
                var publication=staged(worker,t.snapshot(local,world));local.health--;
                assertThrows(IOException.class,()->other.submitPublication(publication,ReadbackMetadataTest.REV));assertThrows(IOException.class,()->worker.submitPublication(publication,SavedPagingFactoryTest.NEXT));
                var request=worker.submitPublication(publication,ReadbackMetadataTest.REV);RegionReplayWorkerTest.done(request);assertEquals(ReadbackSaveSnapshot.Phase.PUBLISHED,request.publicationResult(ReadbackMetadataTest.REV).phase());assertFalse(request.cancel());assertFalse(publication.cancel());assertThrows(IOException.class,()->request.result(ReadbackMetadataTest.REV));assertThrows(IOException.class,()->worker.submitPublication(publication,ReadbackMetadataTest.REV));
                var saved=new LocalGame(path,0);assertTrue(saved.edits.values().contains(new Protocol.Edit(0,160,0,0)));assertEquals(local.health+1,saved.health);assertEquals(1,world.editsVersion());assertFalse(local.replayComplete());assertThrows(IOException.class,local::save);
                RegionReplayWorkerTest.closed(worker);assertEquals(ReadbackSaveSnapshot.Phase.PUBLISHED,request.publicationResult(ReadbackMetadataTest.REV).phase());assertEquals(0,t.prefixes(f.folder));
            }
        }finally{RegionReplayWorkerTest.closed(worker);RegionReplayWorkerTest.closed(other);}f.noSpools();
    }
    void queuedCase(boolean close)throws Exception {
        var t=fixtures();var f=t.fixture(close?"close":"cancel");var path=f.fixture();var before=Files.readAllBytes(path);var spool=f.spool();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var block=new AtomicBoolean();
        var worker=new RegionReplayWorker(spool,(cancel,progress)->{
            var source=LocalGame.openRegionalReadback(path,spool,ReadbackMetadataTest.REV,cancel,progress);
            return new RegionReplayWorker.Source(source.spool(),source.terrain(),edit->{if(block.getAndSet(false)){entered.countDown();try{RegionReplayWorkerTest.release(release);}catch(IOException e){throw new UncheckedIOException(e);}}return source.allowed().test(edit);},source.revision(),source.fingerprint(),source.metadata());
        },1,1,56,2);
        try {
            ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
            try(var world=new World(local.seed,local.models,local.generatorVersion)) {
                var snapshot=t.snapshot(local,world);var publication=staged(worker,snapshot);assertEquals(1,t.prefixes(f.folder));block.set(true);
                var page=worker.submit(PendingReplayOverlayTest.REGION,PendingReplayOverlayTest.RANGE,8,ReadbackMetadataTest.REV);assertTrue(entered.await(10,TimeUnit.SECONDS));var request=worker.submitPublication(publication,ReadbackMetadataTest.REV);
                assertThrows(IOException.class,()->worker.submit(PendingReplayOverlayTest.REGION,PendingReplayOverlayTest.RANGE,8,ReadbackMetadataTest.REV));
                if(close)worker.close();else {
                    var executor=Executors.newSingleThreadExecutor();try{assertFalse(executor.submit(request::cancel).get(10,TimeUnit.SECONDS));}finally{executor.shutdownNow();}
                    assertTrue(request.cancel());assertEquals(ReadbackSaveSnapshot.Phase.CANCELLED,publication.phase());
                }
                release.countDown();if(close)RegionReplayWorkerTest.closed(worker);else {
                    RegionReplayWorkerTest.done(page);RegionReplayWorkerTest.await(()->{try{return t.prefixes(f.folder)==0;}catch(IOException e){throw new UncheckedIOException(e);}});
                }
                assertEquals(0,t.prefixes(f.folder));assertArrayEquals(before,Files.readAllBytes(path));assertThrows(CancellationException.class,()->request.publicationResult(ReadbackMetadataTest.REV));assertEquals(1,world.editsVersion());
                if(!close){var retry=worker.submitPublication(staged(worker,snapshot),ReadbackMetadataTest.REV);RegionReplayWorkerTest.done(retry);assertEquals(ReadbackSaveSnapshot.Phase.PUBLISHED,retry.publicationResult(ReadbackMetadataTest.REV).phase());}
            }
        }finally{release.countDown();RegionReplayWorkerTest.closed(worker);}f.noSpools();
    }
    @Test public void queuedCancellationCleansOnWorkerAndAllowsFreshRetry()throws Exception{queuedCase(false);}
    @Test public void closeBeforePublicationStartsCleansOnlyOwnedPrefix()throws Exception{queuedCase(true);}
}
