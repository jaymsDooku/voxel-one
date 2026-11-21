package dev.jayms.net;

import dev.jayms.World;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class SavedPagingRetirementTest {
    @TempDir Path folder;
    SavedPagingReplacementTest.Fixture fixture(String name)throws Exception {
        var tests=new SavedPagingReplacementTest();tests.folder=Files.createDirectories(folder.resolve(name));return tests.new Fixture(name);
    }
    @Test public void explicitRetirementClosesOnlyExactOldWorldAndKeepsNewSceneUsable()throws Exception {
        try(var f=fixture("exact");var replacement=f.open();var other=new World(42)) {
            var disk=Files.readAllBytes(f.c.path);SavedPagingReplacementTest.ready(replacement);replacement.prepare(()->false);SavedPagingWarmupTest.warm(replacement);
            try(var transfer=replacement.take(()->false)) {
                var next=transfer.session();assertFalse(f.c.world.isClosed());assertEquals(ReadbackSaveSnapshot.Publication.RetirementState.READY,transfer.previousStatus().state());
                assertThrows(IOException.class,()->transfer.retirePrevious(other,()->false));assertThrows(IOException.class,()->transfer.retirePrevious(next.world(),()->false));
                transfer.retirePrevious(f.c.world,()->false);assertTrue(f.c.world.isClosed());assertFalse(next.world().isClosed());assertEquals(1,next.scene().status().data().ready());
                assertEquals(ReadbackSaveSnapshot.Publication.RetirementState.CLOSED,transfer.previousStatus().state());assertTrue(transfer.previousStatus().closeStarted());
                transfer.retirePrevious(f.c.world,()->{fail("Completed retirement runs no cancellation hook");return false;});
                assertThrows(IOException.class,()->transfer.retirePrevious(other,()->false));assertArrayEquals(disk,Files.readAllBytes(f.c.path));assertThrows(IOException.class,next.local()::save);
                transfer.close();RegionReplayWorkerTest.closed(transfer.worker());
            }
        }
    }
    @Test public void laterOldOwnerEditsModelsOrSurvivalStateRejectWithoutClosingEitherWorld()throws Exception {
        for(int mode=0;mode<6;mode++)try(var f=fixture("stale-"+mode);var replacement=f.open()) {
            SavedPagingReplacementTest.ready(replacement);
            try(var transfer=replacement.take(()->false)) {
                switch(mode) {
                    case 0->f.c.local.health--;
                    case 1->f.c.local.inventory.add(Blocks.STONE,1);
                    case 2->f.c.local.drops.put(98,new ItemDrop(98,Blocks.STONE,1,1,2,3));
                    case 3->f.c.local.models=f.c.local.models.copy();
                    case 4->f.c.world.apply(new Protocol.Edit(6,160,2,Blocks.STONE));
                    case 5->f.c.world.terrain().stressGrid(null);
                }
                assertThrows(IOException.class,()->transfer.retirePrevious(f.c.world,()->false));assertFalse(f.c.world.isClosed());assertFalse(transfer.session().world().isClosed());
                assertEquals(ReadbackSaveSnapshot.Publication.RetirementState.READY,transfer.previousStatus().state());transfer.close();RegionReplayWorkerTest.closed(transfer.worker());
            }
        }
    }
    @Test public void closingTransferredSceneNeverImplicitlyRetiresOldWorld()throws Exception {
        try(var f=fixture("separate");var replacement=f.open()) {
            SavedPagingReplacementTest.ready(replacement);var transfer=replacement.take(()->false);transfer.close();RegionReplayWorkerTest.closed(transfer.worker());
            assertTrue(transfer.session().world().isClosed());assertFalse(f.c.world.isClosed());assertEquals(ReadbackSaveSnapshot.Publication.RetirementState.READY,transfer.previousStatus().state());
            transfer.retirePrevious(f.c.world,()->false);assertTrue(f.c.world.isClosed());
        }
    }
    @Test public void retirementOwnerReentryAndEveryCancellationHookKeepWorldOpen()throws Exception {
        for(int stop=1;stop<=2;stop++)try(var f=fixture("cancel-"+stop);var replacement=f.open()) {
            SavedPagingReplacementTest.ready(replacement);
            try(var transfer=replacement.take(()->false)) {
                f.c.worker.submit(()->{assertThrows(IOException.class,()->transfer.retirePrevious(f.c.world,()->false));assertThrows(IOException.class,transfer::previousStatus);return null;}).get(10,TimeUnit.SECONDS);
                int point=stop;var calls=new AtomicInteger();
                assertThrows(CancellationException.class,()->transfer.retirePrevious(f.c.world,()->{assertThrows(IOException.class,()->transfer.retirePrevious(f.c.world,()->false));return calls.incrementAndGet()==point;}));
                assertEquals(point,calls.get());assertFalse(f.c.world.isClosed());assertFalse(transfer.previousStatus().closeStarted());
                transfer.retirePrevious(f.c.world,()->false);transfer.close();RegionReplayWorkerTest.closed(transfer.worker());
            }
        }
    }
    @Test public void tailChangeAfterReceiptCaptureTransfersLatestButRejectsOldReceipt()throws Exception {
        try(var f=fixture("during-take");var replacement=f.open()) {
            SavedPagingReplacementTest.ready(replacement);replacement.prepare(()->false);var receipt=f.publication.prepareWorldRetirement(()->false);var calls=new AtomicInteger();
            try(var transfer=replacement.take(()->{if(calls.incrementAndGet()==1)f.c.local.health--;return false;})) {
                assertEquals(f.c.local.health,transfer.session().local().health);
                assertThrows(IOException.class,()->receipt.retire(f.c.world,()->false));assertFalse(f.c.world.isClosed());
                transfer.close();RegionReplayWorkerTest.closed(transfer.worker());
            }
        }
    }
    static final class FaultWorld extends World {
        final boolean after;int remaining=1,calls;
        FaultWorld(LocalGame local,boolean after){super(local.seed,local.models,local.generatorVersion);this.after=after;}
        @Override public void close()throws Exception {
            calls++;if(!after&&remaining-->0)throw new IOException("Synthetic pre-close failure");
            super.close();if(after&&remaining-->0)throw new IOException("Synthetic post-close failure");
        }
    }
    @Test public void preCloseFailureRevalidatesAndPostCloseFailureOnlyRetriesExplicitly()throws Exception {
        for(boolean after:new boolean[]{false,true}) {
            var tests=new ReadbackSaveSnapshotTest();tests.folder=Files.createDirectories(folder.resolve("fault-"+after));
            try(var c=tests.new Context("source");var world=new FaultWorld(c.local,after)) {
                var editor=new RegionReplayEditor(world,new RegionReplayJournal());editor.apply(new Protocol.Edit(0,160,0,0),()->false);
                var snapshot=ReadbackSaveSnapshot.captureJournal(c.local,world,editor.capture(()->false),()->false);
                var old=c.fixture.worker(c.path);ReadbackMetadataTest.ready(old);RegionReplayWorkerTest.closed(old);
                try(var staged=c.stage(snapshot)) {
                    var publication=staged.authorize(()->false);c.worker.submit(()->{publication.publish();publication.close();return null;}).get(10,TimeUnit.SECONDS);
                    var directory=Files.createDirectories(c.fixture.folder.resolve("retire"));
                    try(var replacement=new SavedPagingReplacement(publication,old,directory,SavedPagingFactoryTest.NEXT,1,8192,()->false)) {
                        SavedPagingReplacementTest.ready(replacement);
                        try(var transfer=replacement.take(()->false)) {
                            assertThrows(IOException.class,()->transfer.retirePrevious(world,()->false));assertEquals(1,world.calls);assertEquals(after,world.isClosed());
                            assertEquals(ReadbackSaveSnapshot.Publication.RetirementState.CLEANUP_FAILED,transfer.previousStatus().state());assertEquals(after,transfer.previousStatus().closeStarted());assertNotNull(transfer.previousStatus().failure());
                            for(int i=0;i<5;i++)assertEquals(SavedPagingReplacement.State.TRANSFERRED,replacement.poll(()->false));assertEquals(1,world.calls);
                            assertThrows(CancellationException.class,()->transfer.retirePrevious(world,()->true));assertEquals(1,world.calls);
                            if(!after){int health=c.local.health;c.local.health--;assertThrows(IOException.class,()->transfer.retirePrevious(world,()->false));assertEquals(1,world.calls);c.local.health=health;}
                            transfer.retirePrevious(world,()->false);assertEquals(2,world.calls);assertEquals(ReadbackSaveSnapshot.Publication.RetirementState.CLOSED,transfer.previousStatus().state());assertFalse(transfer.session().world().isClosed());
                            transfer.close();RegionReplayWorkerTest.closed(transfer.worker());
                        }
                    }
                }
            }
        }
    }
    @Test public void retirementCaptureRejectsPublicationReentryWithoutConsumingSource()throws Exception {
        try(var f=fixture("capture-reentry");var replacement=f.open()) {
            SavedPagingReplacementTest.ready(replacement);
            f.publication.prepareWorldRetirement(()->{assertThrows(IOException.class,()->f.publication.prepareWorldRetirement(()->false));return false;});
            assertFalse(f.c.world.isClosed());
            try(var transfer=replacement.take(()->false)) {
                transfer.retirePrevious(f.c.world,()->false);transfer.close();RegionReplayWorkerTest.closed(transfer.worker());
            }
        }
    }
    @Test public void retirementCannotStartBeforeSuccessfulSourceHandover()throws Exception {
        try(var f=fixture("before");var replacement=f.open()) {
            SavedPagingReplacementTest.ready(replacement);var receipt=f.publication.prepareWorldRetirement(()->false);
            assertThrows(IOException.class,()->receipt.retire(f.c.world,()->false));assertFalse(f.c.world.isClosed());
            replacement.close();SavedPagingReplacementTest.stopped(replacement);assertThrows(IOException.class,()->receipt.retire(f.c.world,()->false));
        }
    }
}
