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

public class PreparedPagingSceneTest {
    @TempDir Path folder;
    final class Fixture implements AutoCloseable {
        final ReadbackSaveSnapshotTest.Context c;
        final RegionReplayEditor editor;
        final ReadbackSaveSnapshot.Publication publication;
        final RegionReplayWorker old;
        final Path directory;
        final List<RegionReplayWorker> workers=new ArrayList<>();
        final List<ReadbackSaveSnapshot.Publication.PreparedPagingScene> preparations=new ArrayList<>();
        Fixture(String name)throws Exception {
            var tests=new ReadbackSaveSnapshotTest();tests.folder=Files.createDirectories(folder.resolve("contexts"));c=tests.new Context(name);
            SavedPagingModelBindingTest.addModelsInOneRevision(c.local.models);
            editor=new RegionReplayEditor(c.world,new RegionReplayJournal());editor.apply(new Protocol.Edit(0,160,0,0),()->false);
            var snapshot=ReadbackSaveSnapshot.captureJournal(c.local,c.world,editor.capture(()->false),()->false);
            old=c.fixture.worker(c.path);ReadbackMetadataTest.ready(old);RegionReplayWorkerTest.closed(old);
            try(var prepared=c.stage(snapshot)) {
                publication=prepared.authorize(()->false);editor.apply(PendingReplayOverlayTest.fraction(),()->false);
                c.worker.submit(()->{publication.publish();publication.close();return null;}).get(10,TimeUnit.SECONDS);
            }
            directory=Files.createDirectories(c.fixture.folder.resolve("paging"));
        }
        RegionReplayWorker open(RegionReplayWorker.Revision revision,RegionReplayOverlay pending)throws Exception {
            var worker=new RegionReplayWorker(directory,publication.pagingFactory(old,directory,revision,pending),1,1,56*1024,2000);workers.add(worker);ReadbackMetadataTest.ready(worker);return worker;
        }
        ReadbackSaveSnapshot.Publication.PreparedPagingScene prepare(RegionReplayWorker worker,RegionReplayWorker.Revision revision,RegionReplayOverlay pending)throws Exception {
            var result=publication.preparePagingScene(worker,revision,pending,1,8192,()->false);preparations.add(result);return result;
        }
        @Override public void close()throws Exception {
            try {for(var prepared:preparations)prepared.close();for(var worker:workers)RegionReplayWorkerTest.closed(worker);try(var files=Files.list(directory)){assertEquals(0,files.count());}}
            finally{c.close();}
        }
    }
    static void warm(ReadbackSaveSnapshot.Publication.PreparedPagingScene prepared)throws Exception {
        var scene=prepared.scene();scene.desired(List.of(new ChunkPos(0,10,0)));long deadline=System.nanoTime()+10_000_000_000L;
        while(scene.status().data().ready()!=1&&System.nanoTime()<deadline){scene.tick();Thread.sleep(5);}
        assertEquals(1,scene.status().data().ready());
    }
    @Test public void warmedGeometryAndReceiptsSurviveLatestTailCommitAndRepeatSave()throws Exception {
        try(var f=new Fixture("commit")) {
            var pending=f.publication.pendingPagingOverlay(()->false);var worker=f.open(SavedPagingFactoryTest.NEXT,pending);var prepared=f.prepare(worker,SavedPagingFactoryTest.NEXT,pending);
            byte[] disk=Files.readAllBytes(f.c.path);long edits=f.c.world.editsVersion();assertEquals(2,f.editor.capture(()->false).overlay().rows().size());assertThrows(IOException.class,()->worker.metadataResult(SavedPagingFactoryTest.NEXT));
            warm(prepared);var world=prepared.world();var scene=prepared.scene();var binding=prepared.binding();var models=world.models();
            var sky=binding.submitSky(0,0,RegionReplaySkyWorkerTest.LIMITS,world);RegionReplayWorkerTest.done(sky);var tile=binding.skyResult(sky,world);
            f.c.local.health--;f.c.local.inventory.add(Blocks.STONE,1);f.c.local.drops.put(99,new ItemDrop(99,Blocks.STONE,1,1,2,3));
            try(var session=prepared.commit(()->false)) {
                assertSame(world,session.world());assertSame(scene,session.scene());assertSame(binding,session.binding());assertSame(models,session.local().models);
                assertEquals(1,scene.status().data().ready());assertSame(tile,binding.skyResult(sky,world));assertNotEquals(f.c.local.models.revision(),models.revision());
                assertEquals(f.c.local.health,session.local().health);assertEquals(f.c.local.drops,session.local().drops);assertNotSame(f.c.local.drops,session.local().drops);assertNotSame(f.c.local.inventory,session.local().inventory);
                for(int i=0;i<Inventory.SIZE;i++){assertEquals(f.c.local.inventory.type(i),session.local().inventory.type(i));assertEquals(f.c.local.inventory.count(i),session.local().inventory.count(i));}
                assertEquals(pending.rows(),f.editor.capture(()->false).overlay().rows());assertEquals(pending.rows(),session.journal().capture(()->false).overlay().rows());
                assertEquals(edits,f.c.world.editsVersion());assertFalse(f.c.world.hasReplayReadView());assertArrayEquals(disk,Files.readAllBytes(f.c.path));
                prepared.close();assertEquals(1,scene.status().data().ready());assertThrows(IOException.class,()->prepared.commit(()->false));assertThrows(IOException.class,session.local()::save);
                var second=SavedPagingJournalTest.stage(worker,session,SavedPagingFactoryTest.NEXT);SavedPagingJournalTest.publish(worker,second,SavedPagingFactoryTest.NEXT);
                var loaded=new LocalGame(f.c.path,0);assertEquals(session.local().health,loaded.health);assertEquals(session.local().drops,loaded.drops);assertTrue(loaded.models.sameBindings(models));
            }
        }
    }
    @Test public void laterGeometryRejectsThenFreshCandidateIncludesItWithoutLostPrefix()throws Exception {
        try(var f=new Fixture("geometry")) {
            byte[] disk=Files.readAllBytes(f.c.path);var pending=f.publication.pendingPagingOverlay(()->false);var worker=f.open(SavedPagingFactoryTest.NEXT,pending);var prepared=f.prepare(worker,SavedPagingFactoryTest.NEXT,pending);warm(prepared);
            var edit=new Protocol.Edit(6,160,2,Blocks.LED).withColor(0xed593c);f.editor.apply(edit,()->false);
            assertThrows(IOException.class,()->prepared.commit(()->false));assertEquals(3,f.editor.capture(()->false).overlay().rows().size());assertEquals(0,prepared.world().cell(6,160,2).get(0,0,0));assertArrayEquals(disk,Files.readAllBytes(f.c.path));
            prepared.close();RegionReplayWorkerTest.closed(worker);var nextRevision=new RegionReplayWorker.Revision(SavedPagingFactoryTest.NEXT.world()+1,SavedPagingFactoryTest.NEXT.models(),SavedPagingFactoryTest.NEXT.terrain());
            var nextPending=f.publication.pendingPagingOverlay(()->false);var nextWorker=f.open(nextRevision,nextPending);var next=f.prepare(nextWorker,nextRevision,nextPending);warm(next);
            try(var session=next.commit(()->false)){assertEquals(WorldVoxels.encode(edit),session.world().cell(6,160,2).get(0,0,0));assertEquals(nextPending.rows(),f.editor.capture(()->false).overlay().rows());assertArrayEquals(disk,Files.readAllBytes(f.c.path));}
        }
    }
    @Test public void abandonedPreparationOwnsCleanupAndNeedsFreshMetadata()throws Exception {
        try(var f=new Fixture("abandon")) {
            var pending=f.publication.pendingPagingOverlay(()->false);var worker=f.open(SavedPagingFactoryTest.NEXT,pending);var prepared=f.prepare(worker,SavedPagingFactoryTest.NEXT,pending);warm(prepared);var world=prepared.world();
            assertThrows(IOException.class,()->f.publication.adoptPagingScene(worker,SavedPagingFactoryTest.NEXT,pending,1,8192,()->false));
            assertThrows(IOException.class,()->f.publication.acknowledgePagingOverlay(worker,()->false));assertThrows(IOException.class,()->f.publication.pagingFactory(f.old,f.directory,SavedPagingFactoryTest.NEXT,pending));
            prepared.close();prepared.close();RegionReplayWorkerTest.closed(worker);assertTrue(world.isClosed());assertEquals(2,f.editor.capture(()->false).overlay().rows().size());
            assertThrows(IOException.class,()->prepared.world());assertThrows(IOException.class,()->prepared.commit(()->false));assertThrows(IOException.class,()->worker.metadataResult(SavedPagingFactoryTest.NEXT));
            var next=f.open(SavedPagingJournalTest.AFTER,pending);var retry=f.prepare(next,SavedPagingJournalTest.AFTER,pending);try(var session=retry.commit(()->false)){assertEquals(pending.rows(),session.journal().capture(()->false).overlay().rows());}
        }
    }
    @Test public void abandonedCleanupFailureGatesNewCandidateUntilExplicitWorkerRetry()throws Exception {
        try(var f=new Fixture("cleanup")) {
            var pending=f.publication.pendingPagingOverlay(()->false);var worker=f.open(SavedPagingFactoryTest.NEXT,pending);var prepared=f.prepare(worker,SavedPagingFactoryTest.NEXT,pending);warm(prepared);Path blocked;
            try(var files=Files.list(f.directory)){blocked=files.filter(p->p.getFileName().toString().startsWith(".replay-spool-")).findFirst().orElseThrow();}
            Files.delete(blocked);Files.createDirectory(blocked);Files.writeString(blocked.resolve("hold"),"synthetic");
            try {
                prepared.close();long deadline=System.nanoTime()+10_000_000_000L;
                while(worker.status().state()!=RegionReplayWorker.State.CLEANUP_FAILED&&System.nanoTime()<deadline)Thread.sleep(5);
                assertEquals(RegionReplayWorker.State.CLEANUP_FAILED,worker.status().state());
                assertThrows(IOException.class,()->f.publication.pagingFactory(f.old,f.directory,SavedPagingJournalTest.AFTER,pending));
                Files.delete(blocked.resolve("hold"));Files.delete(blocked);
                assertThrows(IOException.class,()->f.publication.pagingFactory(f.old,f.directory,SavedPagingJournalTest.AFTER,pending));
                assertEquals(RegionReplayWorker.State.CLEANUP_FAILED,worker.status().state());RegionReplayWorkerTest.closed(worker);
                var next=f.open(SavedPagingJournalTest.AFTER,pending);var retry=f.prepare(next,SavedPagingJournalTest.AFTER,pending);
                assertEquals(2,f.editor.capture(()->false).overlay().rows().size());retry.close();
            }finally{Files.deleteIfExists(blocked.resolve("hold"));Files.deleteIfExists(blocked);RegionReplayWorkerTest.closed(worker);}
        }
    }
    @Test public void everyFinalCancellationHookLeavesLiveTailAndJournalUntouched()throws Exception {
        int count;
        try(var f=new Fixture("hook-count")) {
            var pending=f.publication.pendingPagingOverlay(()->false);var worker=f.open(SavedPagingFactoryTest.NEXT,pending);var prepared=f.prepare(worker,SavedPagingFactoryTest.NEXT,pending);var calls=new java.util.concurrent.atomic.AtomicInteger();
            try(var session=prepared.commit(()->{calls.incrementAndGet();return false;})){count=calls.get();}
        }
        assertTrue(count>10&&count<=256);
        for(int stop=1;stop<=count;stop++)try(var f=new Fixture("cancel-"+stop)) {
            var pending=f.publication.pendingPagingOverlay(()->false);var worker=f.open(SavedPagingFactoryTest.NEXT,pending);var prepared=f.prepare(worker,SavedPagingFactoryTest.NEXT,pending);byte[] disk=Files.readAllBytes(f.c.path);var calls=new java.util.concurrent.atomic.AtomicInteger();int point=stop;
            assertThrows(CancellationException.class,()->prepared.commit(()->calls.incrementAndGet()==point));assertEquals(point,calls.get());assertEquals(2,f.editor.capture(()->false).overlay().rows().size());assertArrayEquals(disk,Files.readAllBytes(f.c.path));
        }
    }
    @Test public void ownerAndReentryGuardsKeepOnePreparationAndOneCommit()throws Exception {
        try(var f=new Fixture("owner")) {
            var pending=f.publication.pendingPagingOverlay(()->false);var worker=f.open(SavedPagingFactoryTest.NEXT,pending);var prepared=f.prepare(worker,SavedPagingFactoryTest.NEXT,pending);
            f.c.worker.submit(()->{assertThrows(IOException.class,prepared::world);assertThrows(IOException.class,prepared::close);assertThrows(IOException.class,()->prepared.commit(()->false));return null;}).get(10,TimeUnit.SECONDS);
            try(var session=prepared.commit(()->{
                assertThrows(IOException.class,prepared::close);assertThrows(IOException.class,()->prepared.commit(()->false));
                assertThrows(IOException.class,()->f.publication.preparePagingScene(worker,SavedPagingFactoryTest.NEXT,pending,1,8192,()->false));
                assertThrows(IOException.class,()->f.publication.adoptAndAcknowledgePagingTail(worker,SavedPagingFactoryTest.NEXT,pending,()->false));return false;
            })){assertEquals(pending.rows(),f.editor.capture(()->false).overlay().rows());}
        }
    }
    @Test public void onlyReservedCandidatesCanRefreshTailAndTheyCannotStageSaves()throws Exception {
        try(var f=new Fixture("ordinary")) {
            var pending=f.publication.pendingPagingOverlay(()->false);var worker=f.open(SavedPagingFactoryTest.NEXT,pending);
            try(var session=f.publication.adoptPagingScene(worker,SavedPagingFactoryTest.NEXT,pending,1,8192,()->false)) {
                assertThrows(IOException.class,()->session.binding().prepareLiveTail(session,SavedPagingFactoryTest.NEXT,session.local(),session.local().inventory.copy(),new LinkedHashMap<>(session.local().drops),session.local().health));
            }
        }
        try(var f=new Fixture("reserved")) {
            var pending=f.publication.pendingPagingOverlay(()->false);var worker=f.open(SavedPagingFactoryTest.NEXT,pending);
            try(var candidate=worker.metadataResultCurrentScene(SavedPagingFactoryTest.NEXT,worker.fingerprint(),f.c.path,pending,f.c.local,f.c.local.inventory.copy(),new LinkedHashMap<>(f.c.local.drops),f.c.local.health,1,8192,()->{},true)) {
                var snapshot=ReadbackSaveSnapshot.captureJournal(candidate.local(),candidate.world(),candidate.journal().capture(()->false),()->false);
                assertThrows(IOException.class,()->worker.submitSave(snapshot,SavedPagingFactoryTest.NEXT));
                var alias=new RegionReplayWorker.PagingSession(candidate.local(),candidate.world(),candidate.binding(),candidate.scene(),candidate.journal());
                assertThrows(IOException.class,()->candidate.binding().prepareLiveTail(alias,SavedPagingFactoryTest.NEXT,f.c.local,f.c.local.inventory.copy(),new LinkedHashMap<>(f.c.local.drops),f.c.local.health));
                assertEquals(2,f.editor.capture(()->false).overlay().rows().size());
            }
        }
    }
    @Test public void changedCandidateModelsClosedWorldOrWorkerAndOldModelsReject()throws Exception {
        for(int mode=0;mode<4;mode++)try(var f=new Fixture("stale-"+mode)) {
            var pending=f.publication.pendingPagingOverlay(()->false);var worker=f.open(SavedPagingFactoryTest.NEXT,pending);var prepared=f.prepare(worker,SavedPagingFactoryTest.NEXT,pending);byte[] disk=Files.readAllBytes(f.c.path);
            if(mode==0){var tree=new SparseVoxelOctree(8);tree.set(0,0,0,0xff123456);prepared.world().models().stageStructureModel(new ModelDefinition("Candidate changed",tree));}
            else if(mode==1)prepared.world().close();else if(mode==2)worker.close();else f.c.local.models=f.c.local.models.copy();
            assertThrows(IOException.class,()->prepared.commit(()->false));assertArrayEquals(disk,Files.readAllBytes(f.c.path));
            if(mode!=3)assertEquals(2,f.editor.capture(()->false).overlay().rows().size());
        }
    }
}
