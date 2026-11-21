package dev.jayms.net;

import dev.jayms.World;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class StagedCleanupQueueTest {
    @TempDir Path folder;
    WorkerSaveStagingTest fixtures()throws IOException{var t=new WorkerSaveStagingTest();t.folder=Files.createDirectories(folder.resolve("contexts"));return t;}
    ReadbackSaveSnapshot.Prepared taken(RegionReplayWorker worker,ReadbackSaveSnapshot snapshot)throws Exception {
        var request=worker.submitSave(snapshot,ReadbackMetadataTest.REV);RegionReplayWorkerTest.done(request);return request.takePrepared(ReadbackMetadataTest.REV);
    }
    @Test public void staleRejectedStageReturnsToExactProducerWithoutTargetMutation()throws Exception {
        var t=fixtures();var f=t.fixture("stale");var path=f.fixture();var before=Files.readAllBytes(path);var worker=f.worker(path);var foreign=f.worker(path);
        try {
            ReadbackMetadataTest.ready(worker);ReadbackMetadataTest.ready(foreign);var local=worker.metadataResult(ReadbackMetadataTest.REV);foreign.metadataResult(ReadbackMetadataTest.REV);
            try(var world=new World(local.seed,local.models,local.generatorVersion);var prepared=taken(worker,t.snapshot(local,world))) {
                local.health--;assertThrows(IOException.class,()->prepared.authorize(()->false));assertEquals(1,t.prefixes(f.folder));
                assertThrows(IOException.class,()->foreign.submitCleanup(prepared,ReadbackMetadataTest.REV));assertThrows(IOException.class,()->worker.submitCleanup(prepared,SavedPagingFactoryTest.NEXT));
                var executor=Executors.newSingleThreadExecutor();try{executor.submit(()->{assertThrows(IOException.class,()->worker.submitCleanup(prepared,ReadbackMetadataTest.REV));return null;}).get(10,TimeUnit.SECONDS);}finally{executor.shutdownNow();}
                var cleanup=worker.submitCleanup(prepared,ReadbackMetadataTest.REV);RegionReplayWorkerTest.done(cleanup);cleanup.cleanupResult(ReadbackMetadataTest.REV);assertFalse(cleanup.cancel());assertThrows(IOException.class,()->cleanup.result(ReadbackMetadataTest.REV));assertThrows(IOException.class,()->cleanup.publicationResult(ReadbackMetadataTest.REV));assertThrows(IOException.class,()->worker.submitCleanup(prepared,ReadbackMetadataTest.REV));assertThrows(IOException.class,()->prepared.authorize(()->false));
                assertEquals(0,t.prefixes(f.folder));assertArrayEquals(before,Files.readAllBytes(path));assertEquals(1,world.editsVersion());assertFalse(local.replayComplete());assertThrows(IOException.class,local::save);
            }
        }finally{RegionReplayWorkerTest.closed(worker);RegionReplayWorkerTest.closed(foreign);}f.noSpools();
    }
    void queuedCase(boolean close)throws Exception {
        var t=fixtures();var f=t.fixture(close?"retire":"queue");var path=f.fixture();var before=Files.readAllBytes(path);var spool=f.spool();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var block=new AtomicBoolean();
        var worker=new RegionReplayWorker(spool,(cancel,progress)->{
            var source=LocalGame.openRegionalReadback(path,spool,ReadbackMetadataTest.REV,cancel,progress);
            return new RegionReplayWorker.Source(source.spool(),source.terrain(),edit->{if(block.getAndSet(false)){entered.countDown();try{RegionReplayWorkerTest.release(release);}catch(IOException e){throw new UncheckedIOException(e);}}return source.allowed().test(edit);},source.revision(),source.fingerprint(),source.metadata());
        },1,1,56,2);
        try {
            ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
            try(var world=new World(local.seed,local.models,local.generatorVersion);var prepared=taken(worker,t.snapshot(local,world))) {
                block.set(true);var page=worker.submit(PendingReplayOverlayTest.REGION,PendingReplayOverlayTest.RANGE,8,ReadbackMetadataTest.REV);assertTrue(entered.await(10,TimeUnit.SECONDS));
                var queuedPage=worker.submit(PendingReplayOverlayTest.REGION,PendingReplayOverlayTest.RANGE,8,ReadbackMetadataTest.REV);assertThrows(IOException.class,()->worker.submitCleanup(prepared,ReadbackMetadataTest.REV));assertEquals(1,t.prefixes(f.folder));assertTrue(queuedPage.cancel());
                var cleanup=worker.submitCleanup(prepared,ReadbackMetadataTest.REV);assertFalse(cleanup.cancel());prepared.close();assertEquals(1,t.prefixes(f.folder)); // Owner close no longer owns deletion after transfer.
                assertThrows(IOException.class,()->prepared.authorize(()->false));
                if(close)worker.close();release.countDown();if(close){RegionReplayWorkerTest.closed(worker);assertThrows(CancellationException.class,()->cleanup.cleanupResult(ReadbackMetadataTest.REV));}
                else{RegionReplayWorkerTest.done(page);RegionReplayWorkerTest.done(cleanup);cleanup.cleanupResult(ReadbackMetadataTest.REV);}
                assertEquals(0,t.prefixes(f.folder));assertArrayEquals(before,Files.readAllBytes(path));
            }
        }finally{release.countDown();RegionReplayWorkerTest.closed(worker);}f.noSpools();
    }
    @Test public void queueRejectionRetainsOwnershipAndRetryTransfersDeletion()throws Exception{queuedCase(false);}
    @Test public void retirementCleansAcceptedQueuedDisposalOnProducer()throws Exception{queuedCase(true);}
}
