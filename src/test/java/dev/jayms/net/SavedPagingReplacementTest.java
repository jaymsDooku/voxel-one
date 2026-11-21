package dev.jayms.net;

import dev.jayms.World;
import dev.jayms.ChunkPos;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class SavedPagingReplacementTest {
    @TempDir Path folder;
    final class Fixture implements AutoCloseable {
        final ReadbackSaveSnapshotTest.Context c;
        final RegionReplayEditor editor;
        final RegionReplayWorker old;
        final ReadbackSaveSnapshot.Publication publication;
        final Path directory;
        final List<SavedPagingReplacement> replacements=new ArrayList<>();
        final Protocol.Edit saved=new Protocol.Edit(0,160,0,0);
        Fixture(String name)throws Exception {
            var tests=new ReadbackSaveSnapshotTest();tests.folder=Files.createDirectories(folder.resolve("contexts"));c=tests.new Context(name);
            editor=new RegionReplayEditor(c.world,new RegionReplayJournal());editor.apply(saved,()->false);
            var snapshot=ReadbackSaveSnapshot.captureJournal(c.local,c.world,editor.capture(()->false),()->false);
            old=c.fixture.worker(c.path);ReadbackMetadataTest.ready(old);RegionReplayWorkerTest.closed(old);
            try(var prepared=c.stage(snapshot)) {
                publication=prepared.authorize(()->false);editor.apply(PendingReplayOverlayTest.fraction(),()->false);
                c.worker.submit(()->{publication.publish();publication.close();return null;}).get(10,TimeUnit.SECONDS);
            }
            directory=Files.createDirectories(c.fixture.folder.resolve("replacement"));
        }
        SavedPagingReplacement open()throws Exception {var replacement=new SavedPagingReplacement(publication,old,directory,SavedPagingFactoryTest.NEXT,1,8192,()->false);replacements.add(replacement);return replacement;}
        @Override public void close()throws Exception {
            try {
                for(var replacement:replacements)if(replacement.status().state()!=SavedPagingReplacement.State.TRANSFERRED){replacement.close();stopped(replacement);}
                try(var paths=Files.list(directory)){assertEquals(0,paths.count(),"Owned replay scratch cleaned");}
            }finally{c.close();}
        }
    }
    static void ready(SavedPagingReplacement replacement)throws Exception {
        long deadline=System.nanoTime()+10_000_000_000L;
        while(replacement.poll(()->false)!=SavedPagingReplacement.State.READY&&System.nanoTime()<deadline) {
            assertTrue(Set.of(SavedPagingReplacement.State.OPENING,SavedPagingReplacement.State.RETIRING).contains(replacement.status().state()),replacement.status().toString());Thread.sleep(5);
        }
        assertEquals(SavedPagingReplacement.State.READY,replacement.status().state());
    }
    static void stopped(SavedPagingReplacement replacement)throws Exception {
        long deadline=System.nanoTime()+10_000_000_000L;
        while(replacement.poll(()->false)==SavedPagingReplacement.State.RETIRING&&System.nanoTime()<deadline)Thread.sleep(5);
        assertNotEquals(SavedPagingReplacement.State.RETIRING,replacement.status().state());
    }
    @Test public void laterGeometryRestartsCandidateAndTransfersLatestTailOnlyOnce()throws Exception {
        try(var f=new Fixture("later");var replacement=f.open()) {
            var c=f.c;byte[] disk=Files.readAllBytes(c.path);ready(replacement);assertEquals(1,replacement.status().attempts());
            var later=new Protocol.Edit(6,160,2,Blocks.LED).withColor(0xed593c);f.editor.apply(later,()->false);
            assertEquals(SavedPagingReplacement.State.RETIRING,replacement.poll(()->false));
            assertEquals(List.of(f.saved,PendingReplayOverlayTest.fraction(),later),f.editor.capture(()->false).overlay().rows());
            ready(replacement);assertEquals(2,replacement.status().attempts());
            c.local.health--;c.local.inventory.add(Blocks.STONE,1);c.local.drops.put(99,new ItemDrop(99,Blocks.STONE,1,1,2,3));
            try(var transfer=replacement.take(()->false)) {
                assertEquals(SavedPagingReplacement.State.TRANSFERRED,replacement.status().state());replacement.close();
                var session=transfer.session();assertEquals(SavedPagingFactoryTest.NEXT.world()+1,transfer.revision().world());
                assertEquals(c.local.health,session.local().health);assertEquals(c.local.drops,session.local().drops);assertNotSame(c.local.models,session.local().models);
                for(int i=0;i<Inventory.SIZE;i++){assertEquals(c.local.inventory.type(i),session.local().inventory.type(i));assertEquals(c.local.inventory.count(i),session.local().inventory.count(i));}
                assertEquals(List.of(PendingReplayOverlayTest.fraction(),later),session.journal().capture(()->false).overlay().rows());
                assertEquals(session.journal().capture(()->false).overlay().rows(),f.editor.capture(()->false).overlay().rows());
                session.scene().desired(List.of(new ChunkPos(0,10,0)));long deadline=System.nanoTime()+10_000_000_000L;
                while(session.scene().status().data().ready()!=1&&System.nanoTime()<deadline){session.scene().tick();Thread.sleep(5);}
                assertEquals(1,session.scene().status().data().ready());assertEquals(WorldVoxels.encode(later),session.world().cell(6,160,2).get(0,0,0));
                assertEquals(WorldVoxels.encode(PendingReplayOverlayTest.fraction()),session.world().cell(3,160,0).get(8,0,8));
                assertFalse(session.world().isLoaded(0,160,0));assertThrows(IOException.class,session.local()::save);assertThrows(IllegalStateException.class,()->session.world().apply(later));
                assertFalse(c.world.hasReplayReadView());assertEquals(WorldVoxels.encode(later),c.world.cell(6,160,2).get(0,0,0));assertArrayEquals(disk,Files.readAllBytes(c.path));
                assertThrows(IOException.class,()->replacement.take(()->false));
                c.worker.submit(()->{assertThrows(IOException.class,transfer::close);return null;}).get(10,TimeUnit.SECONDS);
                transfer.close();RegionReplayWorkerTest.closed(transfer.worker());
            }
        }
    }
    @Test public void editsWhileOpeningAndRepeatedChangesStayBoundedWithoutAcknowledgement()throws Exception {
        try(var f=new Fixture("bounded");var replacement=f.open()) {
            byte[] disk=Files.readAllBytes(f.c.path);
            // The first candidate can still be OPENING here; the stamp is valid
            // independently of its I/O phase and pending rows never mutate it.
            for(int i=0;i<SavedPagingReplacement.MAX_ATTEMPTS;i++) {
                if(i>0)ready(replacement);
                f.editor.apply(new Protocol.Edit(7+i,160,2,Blocks.STONE),()->false);
                assertEquals(SavedPagingReplacement.State.RETIRING,replacement.poll(()->false));
                if(i<SavedPagingReplacement.MAX_ATTEMPTS-1)ready(replacement);
            }
            stopped(replacement);assertEquals(SavedPagingReplacement.State.RETRY_REQUIRED,replacement.status().state());assertEquals(3,replacement.status().attempts());
            assertNotNull(replacement.status().failure());assertEquals(5,f.editor.capture(()->false).overlay().rows().size());assertArrayEquals(disk,Files.readAllBytes(f.c.path));
            for(int i=0;i<5;i++)assertEquals(SavedPagingReplacement.State.RETRY_REQUIRED,replacement.poll(()->false));
            assertThrows(IOException.class,()->replacement.take(()->false));
        }
    }
    @Test public void cancellationAndOwnerReentryDoNotConsumeJournalOrMetadata()throws Exception {
        for(boolean duringTake:new boolean[]{false,true})try(var f=new Fixture("cancel-"+duringTake);var replacement=f.open()) {
            byte[] disk=Files.readAllBytes(f.c.path);ready(replacement);
            f.c.worker.submit(()->{assertThrows(IOException.class,()->replacement.poll(()->false));assertThrows(IOException.class,replacement::close);return null;}).get(10,TimeUnit.SECONDS);
            var calls=new java.util.concurrent.atomic.AtomicInteger();java.util.function.BooleanSupplier cancel=()->{
                calls.incrementAndGet();assertThrows(IOException.class,()->replacement.poll(()->false));assertThrows(IOException.class,replacement::close);return true;
            };
            if(duringTake)assertThrows(CancellationException.class,()->replacement.take(cancel));else assertThrows(CancellationException.class,()->replacement.poll(cancel));
            assertTrue(calls.get()>0);stopped(replacement);assertEquals(SavedPagingReplacement.State.CANCELLED,replacement.status().state());
            assertEquals(List.of(f.saved,PendingReplayOverlayTest.fraction()),f.editor.capture(()->false).overlay().rows());assertArrayEquals(disk,Files.readAllBytes(f.c.path));
            assertFalse(f.publication.pendingPagingOverlay(()->false).rows().isEmpty());
        }
    }
    @Test public void geometryOutsideJournalAndChangedModelsFailClosed()throws Exception {
        for(boolean models:new boolean[]{false,true})try(var f=new Fixture("stale-"+models);var replacement=f.open()) {
            ready(replacement);byte[] disk=Files.readAllBytes(f.c.path);
            if(models)f.c.local.models=f.c.local.models.copy();else f.c.world.apply(new Protocol.Edit(7,160,2,Blocks.STONE));
            assertThrows(IOException.class,()->replacement.poll(()->false));stopped(replacement);assertEquals(SavedPagingReplacement.State.FAILED,replacement.status().state());
            assertNotNull(replacement.status().failure());assertArrayEquals(disk,Files.readAllBytes(f.c.path));assertThrows(IOException.class,()->replacement.take(()->false));
        }
    }
    @Test public void changedPublishedFileFailsWorkerAndBudgetsRejectBeforeOpen()throws Exception {
        try(var f=new Fixture("source")) {
            assertThrows(IllegalArgumentException.class,()->new SavedPagingReplacement(f.publication,f.old,f.directory,SavedPagingFactoryTest.NEXT,3,8192,()->false));
            assertThrows(IllegalArgumentException.class,()->new SavedPagingReplacement(f.publication,f.old,f.directory,new RegionReplayWorker.Revision(Long.MAX_VALUE,0,0),1,8192,()->false));
            assertThrows(CancellationException.class,()->new SavedPagingReplacement(f.publication,f.old,f.directory,SavedPagingFactoryTest.NEXT,1,8192,()->true));
            byte[] disk=Files.readAllBytes(f.c.path);Files.write(f.c.path,new byte[]{0});
            try(var replacement=f.open()) {
                long deadline=System.nanoTime()+10_000_000_000L;
                while(replacement.poll(()->false)!=SavedPagingReplacement.State.FAILED&&System.nanoTime()<deadline)Thread.sleep(5);
                assertEquals(SavedPagingReplacement.State.FAILED,replacement.status().state());assertEquals(1,replacement.status().attempts());assertNotNull(replacement.status().failure());
                assertEquals(List.of(f.saved,PendingReplayOverlayTest.fraction()),f.editor.capture(()->false).overlay().rows());assertArrayEquals(new byte[]{0},Files.readAllBytes(f.c.path));
            }finally{Files.write(f.c.path,disk);}
        }
    }
    @Test public void cleanupFailureWaitsForExplicitCloseBeforeRetry()throws Exception {
        try(var f=new Fixture("cleanup");var replacement=f.open()) {
            ready(replacement);Path blocked;
            try(var files=Files.list(f.directory)){blocked=files.filter(p->p.getFileName().toString().startsWith(".replay-spool-")).findFirst().orElseThrow();}
            Files.delete(blocked);Files.createDirectory(blocked);Files.writeString(blocked.resolve("hold"),"synthetic");
            try {
                f.editor.apply(new Protocol.Edit(6,160,2,Blocks.STONE),()->false);replacement.poll(()->false);
                long deadline=System.nanoTime()+10_000_000_000L;
                while(replacement.status().worker().state()!=RegionReplayWorker.State.CLEANUP_FAILED&&System.nanoTime()<deadline){replacement.poll(()->false);Thread.sleep(5);}
                assertEquals(RegionReplayWorker.State.CLEANUP_FAILED,replacement.status().worker().state());
                assertThrows(CancellationException.class,()->replacement.poll(()->true));
                Files.delete(blocked.resolve("hold"));Files.delete(blocked);
                for(int i=0;i<5;i++){assertEquals(SavedPagingReplacement.State.RETIRING,replacement.poll(()->false));assertEquals(RegionReplayWorker.State.CLEANUP_FAILED,replacement.status().worker().state());}
                assertEquals(1,replacement.status().attempts());replacement.close();stopped(replacement);assertEquals(SavedPagingReplacement.State.CANCELLED,replacement.status().state());
            }finally{Files.deleteIfExists(blocked.resolve("hold"));Files.deleteIfExists(blocked);replacement.close();stopped(replacement);}
        }
    }
    @Test public void everyAdoptionCancellationHookLeavesPrefixAndDiskUntouched()throws Exception {
        int count;
        try(var f=new Fixture("count-hooks");var replacement=f.open()) {
            ready(replacement);var calls=new java.util.concurrent.atomic.AtomicInteger();
            try(var transfer=replacement.take(()->{calls.incrementAndGet();return false;})){count=calls.get();transfer.close();RegionReplayWorkerTest.closed(transfer.worker());}
        }
        assertTrue(count>10&&count<=256);System.out.println("Saved paging adoption cancellation hooks: "+count);
        for(int stop=1;stop<=count;stop++)try(var f=new Fixture("cancel-hook-"+stop);var replacement=f.open()) {
            ready(replacement);byte[] disk=Files.readAllBytes(f.c.path);int point=stop;var calls=new java.util.concurrent.atomic.AtomicInteger();
            assertThrows(CancellationException.class,()->replacement.take(()->calls.incrementAndGet()==point));stopped(replacement);
            assertEquals(SavedPagingReplacement.State.CANCELLED,replacement.status().state());assertEquals(point,calls.get());
            assertEquals(List.of(f.saved,PendingReplayOverlayTest.fraction()),f.editor.capture(()->false).overlay().rows());assertArrayEquals(disk,Files.readAllBytes(f.c.path));
        }
    }
    @Test public void journalStampTracksAppendAndRejectsWrongWorldAndEpoch()throws Exception {
        try(var f=new Fixture("stamp");var other=new World(42)) {
            var snapshot=f.editor.capture(()->false);var before=snapshot.tailStamp(f.c.world);assertEquals(2,before.rows());
            f.editor.apply(new Protocol.Edit(6,160,2,Blocks.STONE),()->false);var after=snapshot.tailStamp(f.c.world);assertEquals(before.epoch(),after.epoch());assertEquals(before.rows()+1,after.rows());
            assertThrows(IOException.class,()->snapshot.tailStamp(other));snapshot.acknowledge(()->false);assertThrows(IOException.class,()->snapshot.tailStamp(f.c.world));
        }
    }
}
