package dev.jayms.net;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class ReadbackWorkerPublicationTest {
    @TempDir Path folder;
    ReadbackSaveSnapshotTest contexts()throws IOException {
        var tests=new ReadbackSaveSnapshotTest();tests.folder=Files.createDirectories(folder.resolve("contexts"));return tests;
    }
    @Test public void workerPublishesAuthorizedSnapshotAndRetainsLaterOwnerEdits()throws Exception {
        try(var c=contexts().new Context("snapshot");var prepared=c.stage(c.snapshot())) {
            int savedHealth=c.local.health;var publication=prepared.authorize(()->false);
            prepared.close(); // Transfer means the owner no longer deletes the sealed file.
            assertThrows(IOException.class,publication::publish);
            assertArrayEquals(c.original,Files.readAllBytes(c.path));assertFalse(publication.snapshotCurrent());
            c.local.health--;int liveHealth=c.local.health;
            c.worker.submit(()->{publication.publish();publication.close();return null;}).get(10,TimeUnit.SECONDS);
            assertEquals(ReadbackSaveSnapshot.Phase.PUBLISHED,publication.phase());assertFalse(publication.cancel());assertFalse(publication.snapshotCurrent());
            assertEquals(savedHealth,new LocalGame(c.path,0).health);assertEquals(liveHealth,c.local.health);
            assertFalse(c.local.replayComplete());assertThrows(IOException.class,c.local::save);
            assertThrows(IOException.class,()->prepared.authorize(()->false));assertThrows(IOException.class,()->prepared.commit(()->false));
            c.worker.submit(()->{assertThrows(IOException.class,publication::publish);return null;}).get(10,TimeUnit.SECONDS);c.noPrefixes();
        }
    }
    @Test public void cancellationBeforeWorkerCleansOnlyTransferredFile()throws Exception {
        try(var c=contexts().new Context("cancel");var prepared=c.stage(c.snapshot())) {
            var unrelated=Files.write(c.fixture.folder.resolve("retained.tmp"),new byte[]{4,5,6});
            var publication=prepared.authorize(()->false);assertTrue(publication.cancel());assertTrue(publication.cancel());
            assertThrows(IOException.class,publication::close);
            c.worker.submit(()->{assertThrows(CancellationException.class,publication::publish);publication.close();return null;}).get(10,TimeUnit.SECONDS);
            assertEquals(ReadbackSaveSnapshot.Phase.CANCELLED,publication.phase());assertArrayEquals(c.original,Files.readAllBytes(c.path));assertArrayEquals(new byte[]{4,5,6},Files.readAllBytes(unrelated));c.noPrefixes();
        }
    }
    @Test public void staleAuthorizationAndForeignThreadsCannotConsumeStage()throws Exception {
        var tests=contexts();
        try(var c=tests.new Context("owner");var prepared=c.stage(c.snapshot())) {
            c.worker.submit(()->{assertThrows(IOException.class,()->prepared.authorize(()->false));assertThrows(IOException.class,prepared::close);return null;}).get(10,TimeUnit.SECONDS);
            assertThrows(CancellationException.class,()->prepared.authorize(()->true));
            var publication=prepared.authorize(()->false);
            c.worker.submit(()->{assertThrows(IOException.class,publication::cancel);assertThrows(IOException.class,publication::snapshotCurrent);publication.publish();return null;}).get(10,TimeUnit.SECONDS);
            assertTrue(publication.snapshotCurrent());
            var foreign=Executors.newSingleThreadExecutor();try{foreign.submit(()->{assertThrows(IOException.class,publication::close);return null;}).get(10,TimeUnit.SECONDS);}finally{foreign.shutdownNow();assertTrue(foreign.awaitTermination(10,TimeUnit.SECONDS));}
            c.worker.submit(()->{publication.close();return null;}).get(10,TimeUnit.SECONDS);c.noPrefixes();
        }
        try(var c=tests.new Context("stale");var prepared=c.stage(c.snapshot())) {
            c.local.health--;assertThrows(IOException.class,()->prepared.authorize(()->false));assertArrayEquals(c.original,Files.readAllBytes(c.path));
        }
    }
    @Test public void changedDestinationFailsWorkerWithoutOverwritingIt()throws Exception {
        try(var c=contexts().new Context("external");var prepared=c.stage(c.snapshot())) {
            var publication=prepared.authorize(()->false);var changed=c.original.clone();changed[changed.length-1]^=1;Files.write(c.path,changed);
            c.worker.submit(()->{assertThrows(IOException.class,publication::publish);publication.close();return null;}).get(10,TimeUnit.SECONDS);
            assertEquals(ReadbackSaveSnapshot.Phase.FAILED,publication.phase());assertFalse(publication.cancel());assertFalse(publication.snapshotCurrent());assertArrayEquals(changed,Files.readAllBytes(c.path));c.noPrefixes();
        }
    }
    @Test public void commitGateRejectsBeforeEpochAdvanceAndCannotCancelAfterClaim()throws Exception {
        var target=Files.writeString(folder.resolve("world.dat"),"original");var source=Files.writeString(folder.resolve("staged.tmp"),"snapshot");
        var generation=WorldSavePublication.capture(target,1024,()->false);
        assertThrows(CancellationException.class,()->WorldSavePublication.replaceIfUnchanged(source,target,generation,()->false,()->false));
        assertEquals("original",Files.readString(target));assertEquals("snapshot",Files.readString(source));
        // Reuse the same generation: a rejected gate must not advance the durable epoch.
        var state=new AtomicReference<>(ReadbackSaveSnapshot.Phase.WORKING);var claimed=new CountDownLatch(1);var proceed=new CountDownLatch(1);var worker=Executors.newSingleThreadExecutor();
        try {
            var future=worker.submit(()->{WorldSavePublication.replaceIfUnchanged(source,target,generation,()->state.get()==ReadbackSaveSnapshot.Phase.CANCELLED,()->{
                assertTrue(state.compareAndSet(ReadbackSaveSnapshot.Phase.WORKING,ReadbackSaveSnapshot.Phase.COMMITTING));claimed.countDown();
                try{assertTrue(proceed.await(10,TimeUnit.SECONDS));}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError(interrupted);}return true;
            });return null;});
            assertTrue(claimed.await(10,TimeUnit.SECONDS));assertFalse(state.compareAndSet(ReadbackSaveSnapshot.Phase.WORKING,ReadbackSaveSnapshot.Phase.CANCELLED));assertEquals("original",Files.readString(target));proceed.countDown();future.get(10,TimeUnit.SECONDS);assertEquals("snapshot",Files.readString(target));assertFalse(Files.exists(source));
        }finally{proceed.countDown();worker.shutdownNow();assertTrue(worker.awaitTermination(10,TimeUnit.SECONDS));}
    }
}
