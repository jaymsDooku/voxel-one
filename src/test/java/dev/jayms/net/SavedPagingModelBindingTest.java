package dev.jayms.net;

import dev.jayms.net.model.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class SavedPagingModelBindingTest {
    @TempDir Path folder;
    ReadbackSaveSnapshotTest contexts()throws IOException {var t=new ReadbackSaveSnapshotTest();t.folder=Files.createDirectories(folder.resolve("contexts"));return t;}
    static int[] addModelsInOneRevision(ModelLibrary models)throws Exception {
        var prepared=models.copy();int[] ids=new int[2];
        for(int i=0;i<2;i++) {
            var tree=new SparseVoxelOctree(8);tree.fill(0,0,0,8,8,8,i==0?0xff00bfa5:0xffbc40dd);
            ids[i]=prepared.stageStructureModel(new ModelDefinition("Added component "+i,tree)).id();
        }
        long before=models.revision();models.replaceWith(prepared);assertEquals(before+1,models.revision());assertNotEquals(prepared.revision(),models.revision());return ids;
    }
    static ModelLibrary changed(ModelLibrary original,int mode)throws Exception {
        var result=new ModelLibrary();
        for(var entry:original.entries()) {
            if(entry.id()==Blocks.FLOWER_POT)continue;
            int target=mode==3?9:1024;
            if(entry.id()!=target){result.acceptWorldEntry(entry);continue;}
            if(mode==0)continue;
            var definition=entry.definition();String author=entry.author();int id=entry.id();
            if(mode==1){var tree=new SparseVoxelOctree(8);tree.fill(0,0,0,8,8,8,0xffed593c);definition=new ModelDefinition("Rebound",tree);}
            if(mode==2)definition=new ModelDefinition("Renamed source",definition.voxels());
            if(mode==3)author="other";
            if(mode==4)id++;
            result.acceptWorldEntry(new ModelLibrary.Entry(id,author,definition));
        }
        return result;
    }
    @Test public void sourceModelDeletionRebindingRenameAuthorAndIdMoveRejectBeforeStaging()throws Exception {
        var tests=contexts();
        for(int mode=0;mode<5;mode++)try(var c=tests.new Context("reject-"+mode)) {
            c.local.models.replaceWith(changed(c.local.models,mode));var snapshot=c.snapshot();
            c.worker.submit(()->{var error=assertThrows(IOException.class,()->snapshot.stage(c.source,()->false,n->{}));assertEquals("Replay source model bindings changed",error.getMessage());return null;}).get(10,TimeUnit.SECONDS);
            assertArrayEquals(c.original,Files.readAllBytes(c.path));assertEquals(0,c.world.editsVersion());c.noPrefixes();
        }
    }
    @Test public void preservedSourceBindingsAllowAdditionalDependenciesAndExactSaveReload()throws Exception {
        try(var c=contexts().new Context("additions")) {
            var old=c.local.models.copy();addModelsInOneRevision(c.local.models);assertTrue(c.local.models.preservesBindings(old));
            var before=c.local.models.copy();try(var staged=c.stage(c.snapshot())){staged.commit(()->false);}
            var reloaded=new LocalGame(c.path,0);assertTrue(reloaded.models.sameBindings(before));assertNotEquals(reloaded.models.revision(),before.revision());
            assertEquals(0,c.world.editsVersion());assertFalse(c.local.replayComplete());assertThrows(IOException.class,c.local::save);c.noPrefixes();
        }
    }
    @Test public void sceneRebasesRevisionAndAcceptsSourceSkyWithoutChangingRetiredBindings()throws Exception {
        try(var c=contexts().new Context("scene")) {
            int[] ids=addModelsInOneRevision(c.local.models);long retiredRevision=c.local.models.revision();var originalBindings=c.local.models.copy();
            var editor=new RegionReplayEditor(c.world,new RegionReplayJournal());editor.apply(new Protocol.Edit(8,160,0,ids[0]),()->false);
            var snapshot=ReadbackSaveSnapshot.captureJournal(c.local,c.world,editor.capture(()->false),()->false);
            var old=c.fixture.worker(c.path);ReadbackMetadataTest.ready(old);RegionReplayWorkerTest.closed(old);
            try(var prepared=c.stage(snapshot)) {
                var publication=prepared.authorize(()->false);editor.apply(PendingReplayOverlayTest.fraction(),()->false);c.worker.submit(()->{publication.publish();publication.close();return null;}).get(10,TimeUnit.SECONDS);
                var parsed=new LocalGame(c.path,0);assertTrue(parsed.models.sameBindings(originalBindings));assertNotEquals(retiredRevision,parsed.models.revision());
                var directory=Files.createDirectories(c.fixture.folder.resolve("replacement"));
                try(var replacement=new SavedPagingReplacement(publication,old,directory,SavedPagingFactoryTest.NEXT,1,8192,()->false)) {
                    SavedPagingReplacementTest.ready(replacement);var transfer=replacement.take(()->false);var worker=transfer.worker();
                    try(transfer) {
                        var session=transfer.session();assertNotSame(c.local.models,session.local().models);assertEquals(retiredRevision,c.local.models.revision());
                        assertEquals(parsed.models.revision(),session.local().models.revision());assertTrue(session.local().models.sameBindings(originalBindings));
                        var request=session.binding().submitSky(0,0,RegionReplaySkyWorkerTest.LIMITS,session.world());RegionReplayWorkerTest.done(request);
                        assertEquals(RegionReplayWorker.Phase.READY,request.outcome().phase());var tile=session.binding().skyResult(request,session.world());assertEquals(session.local().models.revision(),tile.modelRevision());
                        var other=new SparseVoxelOctree(8);other.set(0,0,0,0xff123456);int retiredOnly=c.local.models.stageStructureModel(new ModelDefinition("Retired only",other)).id();
                        assertFalse(session.local().models.has(retiredOnly));assertEquals(tile,session.binding().skyResult(request,session.world()));
                        var second=SavedPagingJournalTest.stage(worker,session,transfer.revision());SavedPagingJournalTest.publish(worker,second,transfer.revision());
                        var reloaded=new LocalGame(c.path,0);assertTrue(reloaded.models.sameBindings(originalBindings));assertFalse(reloaded.models.has(retiredOnly));
                        assertThrows(IOException.class,session.local()::save);assertFalse(c.world.hasReplayReadView());
                    }finally{RegionReplayWorkerTest.closed(worker);}
                }
            }
        }
    }
    @Test public void changedMetadataBindingsRejectWithoutConsumingPreparedSource()throws Exception {
        var tests=contexts();
        for(int mode=0;mode<6;mode++)try(var c=tests.new Context("metadata-"+mode)) {
            var directory=Files.createDirectories(c.fixture.folder.resolve("metadata"));var source=c.worker.submit(()->LocalGame.openRegionalReadback(c.path,directory,SavedPagingFactoryTest.NEXT,()->false,n->{})).get(10,TimeUnit.SECONDS);
            try {
                if(mode==5)addModelsInOneRevision(c.local.models);else c.local.models.replaceWith(changed(c.local.models,mode));
                if(mode!=5)assertThrows(IOException.class,()->source.metadata().prepareCurrentTail(c.local,c.local.inventory.copy(),new LinkedHashMap<>(c.local.drops),c.local.health));
                assertThrows(IOException.class,()->source.metadata().prepareCurrentScene(c.local,c.local.inventory.copy(),new LinkedHashMap<>(c.local.drops),c.local.health));
                assertNotNull(source.metadata().transfer());assertArrayEquals(c.original,Files.readAllBytes(c.path));
            }finally{c.worker.submit(()->{source.spool().close();return null;}).get(10,TimeUnit.SECONDS);}
        }
    }
}
