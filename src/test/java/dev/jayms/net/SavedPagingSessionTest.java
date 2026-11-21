package dev.jayms.net;

import dev.jayms.World;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class SavedPagingSessionTest {
    @TempDir Path folder;
    ReadbackSaveSnapshotTest contexts()throws IOException {var t=new ReadbackSaveSnapshotTest();t.folder=Files.createDirectories(folder.resolve("contexts"));return t;}
    @Test public void preparedMetadataIsUnconsumedUntilOwnerCommitsExactlyOnce()throws Exception {
        try(var c=contexts().new Context("prepared")) {
            var path=Files.createDirectories(c.fixture.folder.resolve("second-spool"));
            var source=c.worker.submit(()->LocalGame.openRegionalReadback(c.path,path,SavedPagingFactoryTest.NEXT,()->false,n->{})).get(10,TimeUnit.SECONDS);
            try {
                var first=source.metadata().prepareCurrentTail(c.local,c.local.inventory.copy(),new LinkedHashMap<>(c.local.drops),c.local.health);
                var second=source.metadata().prepareCurrentTail(c.local,c.local.inventory.copy(),new LinkedHashMap<>(c.local.drops),c.local.health);
                assertNotSame(first.local(),second.local());assertFalse(first.local().replayComplete());assertThrows(IOException.class,first.local()::save);
                c.worker.submit(()->{assertThrows(IOException.class,first::commit);assertThrows(IOException.class,first::local);return null;}).get(10,TimeUnit.SECONDS);
                assertSame(first.local(),first.commit());assertThrows(IOException.class,first::commit);assertThrows(IOException.class,second::commit);assertThrows(IOException.class,()->source.metadata().transfer());
                assertArrayEquals(c.original,Files.readAllBytes(c.path));
            }finally{c.worker.submit(()->{source.spool().close();return null;}).get(10,TimeUnit.SECONDS);}
        }
    }
    @Test public void failedSessionPreparationRetriesAndReturnsCoherentReadOnlyWorld()throws Exception {
        try(var c=contexts().new Context("session")) {
            var editor=new RegionReplayEditor(c.world,new RegionReplayJournal());var saved=new Protocol.Edit(0,160,0,0);var later=PendingReplayOverlayTest.fraction();editor.apply(saved,()->false);
            var snapshot=ReadbackSaveSnapshot.captureJournal(c.local,c.world,editor.capture(()->false),()->false);var old=c.fixture.worker(c.path);ReadbackMetadataTest.ready(old);RegionReplayWorkerTest.closed(old);
            try(var prepared=c.stage(snapshot)) {
                var publication=prepared.authorize(()->false);editor.apply(later,()->false);c.worker.submit(()->{publication.publish();publication.close();return null;}).get(10,TimeUnit.SECONDS);
                var pending=publication.pendingPagingOverlay(()->false);var spool=Files.createDirectories(c.fixture.folder.resolve("paging"));
                var worker=new RegionReplayWorker(spool,publication.pagingFactory(old,spool,SavedPagingFactoryTest.NEXT,pending),1,1,56*1024,2000);
                try {
                    ReadbackMetadataTest.ready(worker);var disk=Files.readAllBytes(c.path);long edits=c.world.editsVersion();
                    c.local.health--;c.local.inventory.add(Blocks.STONE,1);c.local.drops.put(99,new ItemDrop(99,Blocks.STONE,1,1,2,3));
                    assertThrows(CancellationException.class,()->publication.adoptPagingScene(worker,SavedPagingFactoryTest.NEXT,pending,1,8192,()->true));
                    assertThrows(IllegalArgumentException.class,()->publication.adoptPagingScene(worker,SavedPagingFactoryTest.NEXT,pending,3,8192,()->false));
                    assertThrows(IllegalArgumentException.class,()->publication.adoptPagingScene(worker,SavedPagingFactoryTest.NEXT,pending,1,-1,()->false));
                    var copy=RegionReplayOverlay.capture(pending.rows(),()->false);
                    assertThrows(IOException.class,()->publication.adoptPagingScene(worker,SavedPagingFactoryTest.NEXT,copy,1,8192,()->false));
                    assertThrows(IOException.class,()->publication.adoptPagingScene(worker,ReadbackMetadataTest.REV,pending,1,8192,()->false));
                    c.worker.submit(()->{assertThrows(IOException.class,()->publication.adoptPagingScene(worker,SavedPagingFactoryTest.NEXT,pending,1,8192,()->false));return null;}).get(10,TimeUnit.SECONDS);
                    // Reject after the private World/view have actually been staged.
                    var guards=new java.util.concurrent.atomic.AtomicInteger();
                    RegionReplayWorker.OwnerGuard reentry=()->{
                        assertThrows(IOException.class,()->worker.metadataResult(SavedPagingFactoryTest.NEXT));
                        assertThrows(IOException.class,()->worker.metadataResultCurrentTail(SavedPagingFactoryTest.NEXT,worker.fingerprint(),c.path,pending,c.local,c.local.inventory.copy(),new LinkedHashMap<>(c.local.drops),c.local.health));
                        assertThrows(IOException.class,()->worker.metadataResultCurrentScene(SavedPagingFactoryTest.NEXT,worker.fingerprint(),c.path,pending,c.local,c.local.inventory.copy(),new LinkedHashMap<>(c.local.drops),c.local.health,1,8192,()->{}));
                    };
                    assertThrows(CancellationException.class,()->worker.metadataResultCurrentScene(SavedPagingFactoryTest.NEXT,worker.fingerprint(),c.path,pending,c.local,c.local.inventory.copy(),new LinkedHashMap<>(c.local.drops),c.local.health,1,8192,()->{guards.incrementAndGet();reentry.validate();throw new CancellationException();}));
                    assertThrows(IOException.class,()->worker.metadataResultCurrentScene(SavedPagingFactoryTest.NEXT,worker.fingerprint(),c.path,pending,c.local,c.local.inventory.copy(),new LinkedHashMap<>(c.local.drops),c.local.health,1,8192,()->{guards.incrementAndGet();reentry.validate();throw new IOException("Synthetic final guard rejection");}));assertEquals(2,guards.get());
                    assertEquals(List.of(saved,later),editor.capture(()->false).overlay().rows());assertArrayEquals(disk,Files.readAllBytes(c.path));
                    try(var session=publication.adoptPagingScene(worker,SavedPagingFactoryTest.NEXT,pending,1,8192,()->{
                        assertThrows(IOException.class,()->publication.adoptPagingScene(worker,SavedPagingFactoryTest.NEXT,pending,1,8192,()->false));return false;
                    })) {
                        assertNotSame(c.world,session.world());assertNotSame(c.local.models,session.world().models());assertSame(session.local().models,session.world().models());
                        assertEquals(c.local.models.revision(),session.local().models.revision());
                        assertEquals(new ArrayList<>(c.local.models.entries()),new ArrayList<>(session.local().models.entries()));
                        assertEquals(c.local.health,session.local().health);assertEquals(c.local.drops,session.local().drops);assertNotSame(c.local.drops,session.local().drops);assertNotSame(c.local.inventory,session.local().inventory);
                        for(int i=0;i<Inventory.SIZE;i++){assertEquals(c.local.inventory.type(i),session.local().inventory.type(i));assertEquals(c.local.inventory.count(i),session.local().inventory.count(i));}
                        assertTrue(session.world().hasReplayReadView());assertFalse(session.world().replayDataReady(0,160,0));assertFalse(session.world().isLoaded(0,160,0));
                        assertThrows(World.UnloadedReplayCellException.class,()->session.world().cell(0,160,0));assertThrows(IOException.class,session.local()::save);
                        assertThrows(IllegalStateException.class,()->session.world().apply(new Protocol.Edit(4,160,0,Blocks.STONE)));
                        assertThrows(IOException.class,()->worker.bindTarget(session.local(),session.world()));assertThrows(IOException.class,()->worker.metadataResult(SavedPagingFactoryTest.NEXT));
                        c.worker.submit(()->{assertThrows(IllegalStateException.class,session::close);return null;}).get(10,TimeUnit.SECONDS);assertEquals(0,session.scene().status().gpuEntries());
                        session.scene().desired(List.of(new dev.jayms.ChunkPos(0,10,0)));long deadline=System.nanoTime()+10_000_000_000L;
                        while(session.scene().status().data().ready()!=1&&System.nanoTime()<deadline){session.scene().tick();Thread.sleep(5);}
                        assertEquals(1,session.scene().status().data().ready());assertTrue(session.world().replayDataReady(0,160,0));assertFalse(session.world().isLoaded(0,160,0));
                        assertEquals(0,session.world().cell(0,160,0).get(0,0,0));assertEquals(WorldVoxels.encode(later),session.world().cell(3,160,0).get(8,0,8));
                        assertEquals(List.of(later),editor.capture(()->false).overlay().rows());assertEquals(edits,c.world.editsVersion());assertFalse(c.world.hasReplayReadView());assertArrayEquals(disk,Files.readAllBytes(c.path));
                        assertThrows(IOException.class,()->publication.adoptPagingScene(worker,SavedPagingFactoryTest.NEXT,pending,1,8192,()->false));assertThrows(IOException.class,()->publication.acknowledgePagingOverlay(worker,()->false));
                    }
                    // Retiring the new session never closes or rewrites the old World.
                    assertEquals(edits,c.world.editsVersion());assertEquals(WorldVoxels.encode(later),c.world.cell(3,160,0).get(8,0,8));
                }finally{RegionReplayWorkerTest.closed(worker);}
            }
        }
    }
    @Test public void staleWorldAndModelsCannotConsumeSessionMetadata()throws Exception {
        var contexts=contexts();
        for(int mode=0;mode<3;mode++)try(var c=contexts.new Context("stale-"+mode)) {
            var editor=new RegionReplayEditor(c.world,new RegionReplayJournal());editor.apply(new Protocol.Edit(0,160,0,0),()->false);
            var snapshot=ReadbackSaveSnapshot.captureJournal(c.local,c.world,editor.capture(()->false),()->false);var old=c.fixture.worker(c.path);ReadbackMetadataTest.ready(old);RegionReplayWorkerTest.closed(old);
            try(var prepared=c.stage(snapshot)) {
                var publication=prepared.authorize(()->false);editor.apply(PendingReplayOverlayTest.fraction(),()->false);c.worker.submit(()->{publication.publish();publication.close();return null;}).get(10,TimeUnit.SECONDS);
                var pending=publication.pendingPagingOverlay(()->false);var spool=Files.createDirectories(c.fixture.folder.resolve("paging"));var worker=new RegionReplayWorker(spool,publication.pagingFactory(old,spool,SavedPagingFactoryTest.NEXT,pending),1,1,56*1024,2000);
                try {
                    ReadbackMetadataTest.ready(worker);byte[] disk=Files.readAllBytes(c.path);
                    if(mode==0)editor.apply(new Protocol.Edit(4,160,0,Blocks.GLASS),()->false);else if(mode==1)c.world.apply(new Protocol.Edit(4,160,0,Blocks.GLASS));else c.local.models=c.local.models.copy();
                    assertThrows(IOException.class,()->publication.adoptPagingScene(worker,SavedPagingFactoryTest.NEXT,pending,1,8192,()->false));assertNotNull(worker.metadataResult(SavedPagingFactoryTest.NEXT));assertArrayEquals(disk,Files.readAllBytes(c.path));
                }finally{RegionReplayWorkerTest.closed(worker);}
            }
        }
    }
}
