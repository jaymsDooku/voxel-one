package dev.jayms.net;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class RegionReplayJournalTest {
    @TempDir Path folder;
    static Protocol.Edit edit(int x){return new Protocol.Edit(x,160,0,Blocks.STONE);}
    @Test public void prefixAcknowledgementPreservesOrderedLaterEditsAndRejectsStaleSnapshots()throws Exception {
        var journal=new RegionReplayJournal();journal.append(List.of(edit(0)),()->false);var first=journal.capture(()->false);
        journal.append(List.of(edit(1),edit(0)),()->false);var overlapping=journal.capture(()->false);
        assertThrows(CancellationException.class,()->first.acknowledge(()->true));assertEquals(3,journal.capture(()->false).overlay().rows().size());
        assertEquals(List.of(edit(1),edit(0)),first.acknowledge(()->false).rows());
        assertThrows(IOException.class,()->first.acknowledge(()->false));assertThrows(IOException.class,()->overlapping.acknowledge(()->false));
        assertEquals(List.of(edit(1),edit(0)),journal.capture(()->false).overlay().rows());
        var rest=journal.capture(()->false);assertTrue(rest.acknowledge(()->false).rows().isEmpty());
        var empty=journal.capture(()->false);assertTrue(empty.acknowledge(()->false).rows().isEmpty());assertThrows(IOException.class,()->empty.acknowledge(()->false));
    }
    @Test public void boundsCancellationReentryAndForeignOwnerNeverPartiallyAppend()throws Exception {
        var journal=new RegionReplayJournal();journal.append(List.of(edit(0)),()->false);
        assertThrows(IOException.class,()->journal.append(Collections.nCopies(RegionReplayOverlay.MAX_ROWS,edit(1)),()->false));
        assertThrows(CancellationException.class,()->journal.append(List.of(edit(1)),()->true));
        assertThrows(IllegalArgumentException.class,()->journal.append(Arrays.asList(edit(1),new Protocol.Edit(Integer.MAX_VALUE,160,0,Blocks.STONE)),()->false));
        journal.append(List.of(edit(1)),()->{assertThrows(IOException.class,()->journal.append(List.of(edit(2)),()->false));return false;});
        var worker=Executors.newSingleThreadExecutor();try{worker.submit(()->{assertThrows(IOException.class,()->journal.capture(()->false));assertThrows(IOException.class,()->journal.append(List.of(edit(2)),()->false));return null;}).get(10,TimeUnit.SECONDS);}finally{worker.shutdownNow();assertTrue(worker.awaitTermination(10,TimeUnit.SECONDS));}
        assertEquals(List.of(edit(0),edit(1)),journal.capture(()->false).overlay().rows());
        journal.append(Collections.nCopies(RegionReplayOverlay.MAX_ROWS-2,edit(2)),()->false);assertEquals(RegionReplayOverlay.MAX_ROWS,journal.capture(()->false).overlay().rows().size());assertThrows(IOException.class,()->journal.append(List.of(edit(3)),()->false));
    }
    @Test public void preparedAcknowledgementRetainsRowsUntilOwnerCommitAndCloseAllowsRetry()throws Exception {
        var journal=new RegionReplayJournal();journal.append(List.of(edit(0)),()->false);var saved=journal.capture(()->false);
        journal.append(List.of(edit(1),edit(0)),()->false);var pending=RegionReplayOverlay.capture(List.of(edit(1),edit(0)),()->false);
        assertThrows(IOException.class,()->saved.prepareAcknowledgement(RegionReplayOverlay.empty(),()->false));
        var abandoned=saved.prepareAcknowledgement(pending,()->false);
        assertThrows(IOException.class,()->journal.capture(()->false));assertThrows(IOException.class,()->journal.append(List.of(edit(2)),()->false));
        var worker=Executors.newSingleThreadExecutor();
        try{worker.submit(()->{assertThrows(IOException.class,abandoned::commit);assertThrows(IOException.class,abandoned::close);return null;}).get(10,TimeUnit.SECONDS);}
        finally{worker.shutdownNow();assertTrue(worker.awaitTermination(10,TimeUnit.SECONDS));}
        abandoned.close();assertThrows(IOException.class,abandoned::commit);
        assertEquals(List.of(edit(0),edit(1),edit(0)),journal.capture(()->false).overlay().rows());
        try(var retry=saved.prepareAcknowledgement(pending,()->false)) {
            abandoned.close(); // A stale handle cannot unlock a later reservation.
            assertThrows(IOException.class,()->journal.append(List.of(edit(2)),()->false));
            assertEquals(pending.rows(),retry.commit().rows());assertThrows(IOException.class,retry::commit);
        }
        assertEquals(pending.rows(),journal.capture(()->false).overlay().rows());assertThrows(IOException.class,()->saved.acknowledge(()->false));
    }
    @Test public void cancellationAtEveryPreparationHookLeavesPrefixAndSuffixIntact()throws Exception {
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var probe=new RegionReplayJournal();probe.append(List.of(edit(0)),()->false);var snapshot=probe.capture(()->false);probe.append(List.of(edit(1)),()->false);
        try(var prepared=snapshot.prepareAcknowledgement(null,()->{calls.incrementAndGet();return false;})){}
        for(int stop=1;stop<=calls.get();stop++) {
            var journal=new RegionReplayJournal();journal.append(List.of(edit(0)),()->false);var saved=journal.capture(()->false);journal.append(List.of(edit(1)),()->false);
            int boundary=stop;var seen=new java.util.concurrent.atomic.AtomicInteger();
            assertThrows(CancellationException.class,()->saved.prepareAcknowledgement(null,()->seen.incrementAndGet()==boundary));
            assertEquals(List.of(edit(0),edit(1)),journal.capture(()->false).overlay().rows());
            try(var retry=saved.prepareAcknowledgement(null,()->false)){assertEquals(List.of(edit(1)),retry.commit().rows());}
        }
    }
    @Test public void preparationRechecksBoundGuardAndInvalidationAfterCallbacks()throws Exception {
        for(boolean invalidate:List.of(false,true))try(var world=new dev.jayms.World(42)) {
            var journal=new RegionReplayJournal();journal.append(List.of(edit(0)),()->false);var snapshot=journal.capture(()->false);
            var changed=new java.util.concurrent.atomic.AtomicBoolean();
            journal.bind(world,()->{if(changed.get()){if(invalidate)journal.invalidate();else throw new IOException("Changed bound state");}});
            assertThrows(IOException.class,()->snapshot.prepareAcknowledgement(null,()->{changed.set(true);return false;}));
            changed.set(false);
            if(invalidate)assertThrows(IOException.class,()->journal.capture(()->false));
            else assertEquals(List.of(edit(0)),journal.capture(()->false).overlay().rows());
        }
    }
    @Test public void publicationAcknowledgesOnlyItsJournalPrefixAfterExactReadback()throws Exception {
        var tests=new ReadbackSaveSnapshotTest();tests.folder=Files.createDirectories(folder.resolve("contexts"));
        try(var c=tests.new Context("journal")) {
            var journal=new RegionReplayJournal();var saved=new Protocol.Edit(0,160,0,0);journal.append(List.of(saved),()->false);var prefix=journal.capture(()->false);
            var snapshot=ReadbackSaveSnapshot.captureJournal(c.local,c.world,prefix,()->false);
            try(var prepared=c.stage(snapshot)) {
                var publication=prepared.authorize(()->false);journal.append(List.of(edit(3)),()->false);c.world.apply(edit(3));
                assertThrows(IOException.class,()->publication.acknowledgeOverlay(c.source,()->false));
                var directory=Files.createDirectories(c.fixture.folder.resolve("next"));
                var source=c.worker.submit(()->{publication.publish();return publication.reopenPublished(directory,ReadbackMetadataTest.REV,()->false,n->{});}).get(10,TimeUnit.SECONDS);
                try {
                    c.worker.submit(()->{assertThrows(IOException.class,()->publication.acknowledgeOverlay(source,()->false));return null;}).get(10,TimeUnit.SECONDS);
                    assertThrows(CancellationException.class,()->publication.acknowledgeOverlay(source,()->true));
                    assertThrows(IOException.class,()->publication.acknowledgeOverlay(c.source,()->false));
                    assertEquals(List.of(edit(3)),publication.acknowledgeOverlay(source,()->false).rows());assertEquals(List.of(edit(3)),journal.capture(()->false).overlay().rows());assertEquals(1,c.world.editsVersion());
                    assertThrows(IOException.class,()->publication.acknowledgeOverlay(source,()->false));assertThrows(IOException.class,()->ReadbackSaveSnapshot.captureJournal(c.local,c.world,prefix,()->false));
                    assertThrows(IOException.class,()->publication.adoptCurrentTail(source,()->false));
                    var savedWorld=new LocalGame(c.path,0);assertTrue(savedWorld.edits.values().contains(saved));assertFalse(savedWorld.edits.values().contains(edit(3)));assertFalse(c.local.replayComplete());
                }finally{c.worker.submit(()->{source.spool().close();publication.close();return null;}).get(10,TimeUnit.SECONDS);}
            }c.noPrefixes();
        }
    }
}
