package dev.jayms.net;

import dev.jayms.World;
import dev.jayms.ChunkPos;
import dev.jayms.net.model.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class SavedPagingJournalTest {
    @TempDir Path folder;
    static final RegionReplayWorker.Revision AFTER=new RegionReplayWorker.Revision(SavedPagingFactoryTest.NEXT.world()+1,SavedPagingFactoryTest.NEXT.models(),SavedPagingFactoryTest.NEXT.terrain());
    @FunctionalInterface interface Case {void run(ReadbackSaveSnapshotTest.Context source,RegionReplayEditor editor,RegionReplayJournal oldJournal,RegionReplayWorker worker,RegionReplayWorker.PagingSession session,RegionReplayOverlay pending)throws Exception;}
    void withSession(String name,Case action)throws Exception {
        var tests=new ReadbackSaveSnapshotTest();tests.folder=Files.createDirectories(folder.resolve("contexts"));
        try(var c=tests.new Context(name)) {
            var journal=new RegionReplayJournal();var editor=new RegionReplayEditor(c.world,journal);editor.apply(new Protocol.Edit(0,160,0,0),()->false);
            var snapshot=ReadbackSaveSnapshot.captureJournal(c.local,c.world,editor.capture(()->false),()->false);var old=c.fixture.worker(c.path);ReadbackMetadataTest.ready(old);RegionReplayWorkerTest.closed(old);
            try(var prepared=c.stage(snapshot)) {
                var publication=prepared.authorize(()->false);editor.apply(PendingReplayOverlayTest.fraction(),()->false);c.worker.submit(()->{publication.publish();publication.close();return null;}).get(10,TimeUnit.SECONDS);
                var pending=publication.pendingPagingOverlay(()->false);var spool=Files.createDirectories(c.fixture.folder.resolve("paging"));var worker=new RegionReplayWorker(spool,publication.pagingFactory(old,spool,SavedPagingFactoryTest.NEXT,pending),1,1,56*1024,2000);
                try {
                    ReadbackMetadataTest.ready(worker);
                    try(var session=publication.adoptPagingScene(worker,SavedPagingFactoryTest.NEXT,pending,1,8192,()->false)){action.run(c,editor,journal,worker,session,pending);}
                }finally{RegionReplayWorkerTest.closed(worker);}
            }
        }
    }
    static ReadbackSaveSnapshot.Publication stage(RegionReplayWorker worker,RegionReplayWorker.PagingSession session,RegionReplayWorker.Revision revision)throws Exception {
        var snapshot=ReadbackSaveSnapshot.captureJournal(session.local(),session.world(),session.journal().capture(()->false),()->false);
        var job=worker.submitSave(snapshot,revision);RegionReplayWorkerTest.done(job);
        try(var prepared=job.takePrepared(revision)){return prepared.authorize(()->false);}
    }
    static void publish(RegionReplayWorker worker,ReadbackSaveSnapshot.Publication publication,RegionReplayWorker.Revision revision)throws Exception {
        var job=worker.submitPublication(publication,revision);RegionReplayWorkerTest.done(job);assertEquals(ReadbackSaveSnapshot.Phase.PUBLISHED,job.publicationResult(revision).phase());
    }
    @Test public void sourceJournalIsIndependentReadOnlyAndOwnerBound()throws Exception {
        withSession("journal",(c,editor,oldJournal,worker,session,pending)->{
            assertNotSame(oldJournal,session.journal());assertEquals(pending.rows(),session.journal().capture(()->false).overlay().rows());
            assertThrows(IOException.class,()->RegionReplayJournal.readOnlySource(c.world,pending));
            assertThrows(IOException.class,()->session.journal().append(List.of(new Protocol.Edit(4,160,0,Blocks.GLASS)),()->false));
            assertThrows(IOException.class,()->session.journal().reserve(List.of(),()->false));
            assertThrows(CancellationException.class,()->session.journal().capture(()->true));
            c.worker.submit(()->{assertThrows(IOException.class,()->session.journal().capture(()->false));return null;}).get(10,TimeUnit.SECONDS);
            editor.apply(new Protocol.Edit(4,160,0,Blocks.GLASS),()->false);assertEquals(2,editor.capture(()->false).overlay().rows().size());assertEquals(pending.rows(),session.journal().capture(()->false).overlay().rows());
            var captured=session.journal().capture(()->false);assertThrows(IOException.class,()->ReadbackSaveSnapshot.captureJournal(session.local(),c.world,captured,()->false));
            session.close();assertThrows(IOException.class,()->session.journal().capture(()->false));
        });
    }
    @Test public void changedModelsInvalidateTheSourceJournalWithoutSaving()throws Exception {
        withSession("models",(c,editor,oldJournal,worker,session,pending)->{
            byte[] disk=Files.readAllBytes(c.path);var tree=new SparseVoxelOctree(8);tree.set(0,0,0,0xff010203);
            session.world().models().stageStructureModel(new ModelDefinition("Synthetic changed model",tree));
            assertThrows(IOException.class,()->session.journal().capture(()->false));assertArrayEquals(disk,Files.readAllBytes(c.path));
        });
    }
    @Test public void sourceJournalSavesPendingRowsOnceAndFreshSessionSupportsAnotherSave()throws Exception {
        withSession("repeat",(c,editor,oldJournal,worker,session,pending)->{
            byte[] before=Files.readAllBytes(c.path);long physicalRows=worker.status().sourceRows();
            assertThrows(CancellationException.class,()->ReadbackSaveSnapshot.captureJournal(session.local(),session.world(),session.journal().capture(()->false),()->true));
            var stale=ReadbackSaveSnapshot.captureJournal(session.local(),session.world(),session.journal().capture(()->false),()->false);session.local().health--;
            assertThrows(IOException.class,()->worker.submitSave(stale,SavedPagingFactoryTest.NEXT));
            var missing=ReadbackSaveSnapshot.capture(session.local(),session.world(),RegionReplayOverlay.empty(),()->false);var failed=worker.submitSave(missing,SavedPagingFactoryTest.NEXT);RegionReplayWorkerTest.done(failed);assertThrows(IOException.class,()->failed.takePrepared(SavedPagingFactoryTest.NEXT));
            assertArrayEquals(before,Files.readAllBytes(c.path));assertEquals(pending.rows(),session.journal().capture(()->false).overlay().rows());
            session.local().inventory.add(Blocks.STONE,1);session.local().drops.put(99,new ItemDrop(99,Blocks.STONE,1,1,2,3));
            var publication=stage(worker,session,SavedPagingFactoryTest.NEXT);publish(worker,publication,SavedPagingFactoryTest.NEXT);
            var disk=new LocalGame(c.path,0);assertEquals(session.local().health,disk.health);assertEquals(session.local().drops,disk.drops);assertEquals(1,disk.edits.values().stream().filter(PendingReplayOverlayTest.fraction()::equals).count());assertEquals(pending.rows(),session.journal().capture(()->false).overlay().rows());
            var nextPending=publication.pendingPagingOverlay(()->false);assertTrue(nextPending.rows().isEmpty());RegionReplayWorkerTest.closed(worker);
            var spool=Files.createDirectories(c.fixture.folder.resolve("next-paging"));var nextWorker=new RegionReplayWorker(spool,publication.pagingFactory(worker,spool,AFTER,nextPending),1,1,56*1024,2000);
            try {
                ReadbackMetadataTest.ready(nextWorker);assertEquals(physicalRows+pending.rows().size(),nextWorker.status().sourceRows());
                try(var next=publication.adoptPagingScene(nextWorker,AFTER,nextPending,1,8192,()->false)) {
                    assertTrue(session.journal().capture(()->false).overlay().rows().isEmpty());assertTrue(next.journal().capture(()->false).overlay().rows().isEmpty());
                    next.scene().desired(List.of(new ChunkPos(0,10,0)));long deadline=System.nanoTime()+10_000_000_000L;
                    while(next.scene().status().data().ready()!=1&&System.nanoTime()<deadline){next.scene().tick();Thread.sleep(5);}
                    assertEquals(1,next.scene().status().data().ready());assertEquals(WorldVoxels.encode(PendingReplayOverlayTest.fraction()),next.world().cell(3,160,0).get(8,0,8));assertFalse(next.world().isLoaded(0,160,0));
                    next.local().health--;var third=stage(nextWorker,next,AFTER);publish(nextWorker,third,AFTER);assertEquals(next.local().health,new LocalGame(c.path,0).health);assertThrows(IOException.class,next.local()::save);
                    var directory=Files.createDirectories(c.fixture.folder.resolve("verify"));
                    c.worker.submit(()->{var verified=LocalGame.openRegionalReadback(c.path,directory,AFTER,()->false,n->{});try{assertEquals(physicalRows+pending.rows().size(),verified.spool().size());}finally{verified.spool().close();}return null;}).get(10,TimeUnit.SECONDS);
                    assertEquals(pending.rows(),oldJournal.capture(()->false).overlay().rows());assertFalse(c.world.hasReplayReadView());
                }
            }finally{RegionReplayWorkerTest.closed(nextWorker);}
        });
    }
}
