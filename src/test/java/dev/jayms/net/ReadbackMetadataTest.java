package dev.jayms.net;

import dev.jayms.ChunkPos;
import dev.jayms.net.model.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class ReadbackMetadataTest {
    @TempDir Path folder;
    static final RegionReplayWorker.Revision REV=new RegionReplayWorker.Revision(1,2,3);
    Path spool()throws Exception{return Files.createDirectories(folder.resolve("spool"));}
    Path fixture()throws Exception {
        var path=folder.resolve("world.dat");var models=new ModelLibrary();var tree=new SparseVoxelOctree(8);tree.set(0,0,0,0xff123456);
        models.accept(new ModelLibrary.Entry(9,"fixture",new ModelDefinition("Custom",tree)));
        var detail=new SparseVoxelOctree(32);detail.set(31,30,29,0xffabcdef);int model=models.stageStructureModel(new ModelDefinition("Component",detail)).id();
        var rows=new WorldVoxels.History();WorldVoxels.remember(rows,new Protocol.Edit(0,160,0,Blocks.STONE));WorldVoxels.remember(rows,new Protocol.Edit(1,160,0,model));
        var inventory=new Inventory();inventory.add(Blocks.STONE,3);inventory.add(9,1);
        try(var staged=StagedOfflineSave.stageRegions(path,42,Terrain.CURRENT_VERSION,models,rows,()->false,n->{})) {
            staged.publish(inventory,17,List.of(new ItemDrop(3,Blocks.STONE,2,1,2,3),new ItemDrop(7,Blocks.GLASS,1,4,5,6)),2);
        }return path;
    }
    RegionReplayWorker worker(Path path)throws Exception {
        return new RegionReplayWorker(spool(),(cancel,progress)->LocalGame.openRegionalReadback(path,folder.resolve("spool"),REV,cancel,progress),2,1,56,2);
    }
    static void ready(RegionReplayWorker worker)throws Exception{RegionReplayWorkerTest.await(()->worker.status().state()==RegionReplayWorker.State.RUNNING);}
    void noSpools()throws Exception{try(var files=Files.list(spool())){assertEquals(0,files.count());}}
    /** Full writer/opener/worker fixture shared by startup integration tests. */
    public static LocalGame partialForTests(Path directory)throws Exception {
        Files.createDirectories(directory);Path path=directory.resolve("partial.dat"),spool=Files.createDirectories(directory.resolve("partial-spool"));
        try(var staged=StagedOfflineSave.stageRegions(path,42,Terrain.CURRENT_VERSION,new ModelLibrary(),new WorldVoxels.History(),()->false,n->{})) {
            staged.publish(new Inventory(),20,List.of(),0);
        }
        var worker=new RegionReplayWorker(spool,(cancel,progress)->LocalGame.openRegionalReadback(path,spool,REV,cancel,progress),1,1,0,0);
        try{ready(worker);return worker.metadataResult(REV);}finally{RegionReplayWorkerTest.closed(worker);}
    }
    @Test void fullStartupLoadCannotReturnMetadataOnlyState()throws Exception {
        var partial=partialForTests(folder);partial.edits=new WorldVoxels.History();
        try(var load=new OfflineGameLoad(folder.resolve("partial.dat"),42,(path,seed,cancel,progress)->partial)) {
            RegionReplayWorkerTest.await(load::done);assertThrows(IOException.class,load::result);
        }
        assertFalse(partial.replayComplete());
    }
    @Test void transfersCompleteMetadataOnceWithoutClaimingResidentReplay()throws Exception {
        var path=fixture();byte[] before=Files.readAllBytes(path);var expected=new LocalGame(path,0);var worker=worker(path);
        try {
            ready(worker);var metadata=worker.metadataResult(REV);assertFalse(metadata.replayComplete());assertTrue(metadata.edits.isEmpty());assertNull(metadata.city);
            assertEquals(worker.fingerprint(),metadata.readbackFingerprint());assertEquals(RegionalReadbackSourceTest.hash(before),metadata.readbackFingerprint());
            assertEquals(expected.seed,metadata.seed);assertEquals(expected.generatorVersion,metadata.generatorVersion);assertEquals(expected.health,metadata.health);
            for(int slot=0;slot<Inventory.SIZE;slot++){assertEquals(expected.inventory.type(slot),metadata.inventory.type(slot));assertEquals(expected.inventory.count(slot),metadata.inventory.count(slot));}
            assertEquals(new ArrayList<>(expected.drops.entrySet()),new ArrayList<>(metadata.drops.entrySet()));
            assertEquals(expected.models.entries().stream().map(e->e.id()+":"+e.definition().fingerprint()).toList(),metadata.models.entries().stream().map(e->e.id()+":"+e.definition().fingerprint()).toList());
            assertThrows(IOException.class,()->worker.metadataResult(REV));assertTrue(expected.replayComplete());assertEquals("",expected.readbackFingerprint());
            RegionReplayWorkerTest.closed(worker);assertEquals(17,metadata.health);assertThrows(IOException.class,metadata::save);assertArrayEquals(before,Files.readAllBytes(path));
        }finally{RegionReplayWorkerTest.closed(worker);noSpools();}
    }
    @Test void allPartialGameMutationAndPersistenceEntryPointsRejectBeforeCallbacksOrFiles()throws Exception {
        var path=fixture();byte[] before=Files.readAllBytes(path);var worker=worker(path);
        try {
            ready(worker);var metadata=worker.metadataResult(REV);int health=metadata.health;var inventory=metadata.inventory.copy();var drops=new LinkedHashMap<>(metadata.drops);
            assertThrows(IOException.class,metadata::save);
            assertThrows(IOException.class,()->metadata.startGame(null,null));assertThrows(IOException.class,()->metadata.startGame(null,null,null));
            assertThrows(IOException.class,()->metadata.createModel(null));assertThrows(IllegalStateException.class,()->metadata.edit(null,0,0));
            assertThrows(IllegalStateException.class,()->metadata.craft(0,null));assertThrows(IllegalStateException.class,()->metadata.craft(0,1,null));assertThrows(IllegalStateException.class,()->metadata.drop(0,null));
            assertThrows(IllegalStateException.class,()->metadata.tick(null,false,1,e->{fail("Partial tick callback");return 0;}));
            assertThrows(IOException.class,()->metadata.stageStructureHistory(null,null,()->{fail("Partial cancellation callback");return false;},n->fail("Partial progress callback")));
            assertThrows(IOException.class,()->metadata.stageStructureSave(null,null,()->{fail("Partial save callback");return false;},n->fail()));
            assertThrows(IllegalStateException.class,()->metadata.structureAuthority(null));assertThrows(IllegalStateException.class,()->metadata.structureAuthority(null,()->{fail();return null;}));
            assertThrows(IllegalStateException.class,()->metadata.structureAuthority(null,()->{fail();return null;},null));
            assertEquals(health,metadata.health);assertEquals(drops,metadata.drops);for(int i=0;i<Inventory.SIZE;i++){assertEquals(inventory.type(i),metadata.inventory.type(i));assertEquals(inventory.count(i),metadata.inventory.count(i));}
            assertArrayEquals(before,Files.readAllBytes(path));assertFalse(Files.exists(path.resolveSibling("world.dat.tmp")));assertFalse(Files.exists(path.resolveSibling("world.dat.city")));
        }finally{RegionReplayWorkerTest.closed(worker);noSpools();}
    }
    @Test void ownerStateCopiesCannotChangeTheWorkersPrivateModelPolicyOrReopenedMetadata()throws Exception {
        var path=fixture();byte[] before=Files.readAllBytes(path);var policy=new AtomicReference<Predicate<Protocol.Edit>>();
        var worker=new RegionReplayWorker(spool(),(cancel,progress)->{var source=LocalGame.openRegionalReadback(path,folder.resolve("spool"),REV,cancel,progress);policy.set(source.allowed());return source;},2,1,56,2);
        try {
            ready(worker);var metadata=worker.metadataResult(REV);var tree=new SparseVoxelOctree(8);tree.set(1,1,1,0xffabcdef);metadata.models.accept(new ModelLibrary.Entry(10,"owner",new ModelDefinition("Independent",tree)));
            metadata.inventory.add(Blocks.STONE,1);metadata.drops.clear();metadata.health=2;
            assertTrue(metadata.models.has(10));assertFalse(policy.get().test(new Protocol.Edit(0,160,0,10)));
            assertThrows(IllegalStateException.class,()->metadata.models.get(9).definition().voxels().set(0,0,0,0xffabcdef));
            var request=worker.submit(new ChunkPos(0,10,0),new RegionReplayPage.CellRange(0,64),2,REV);RegionReplayWorkerTest.done(request);assertEquals(2,request.result(REV).history().size());
            assertArrayEquals(before,Files.readAllBytes(path));assertThrows(IOException.class,metadata::save);
        }finally{RegionReplayWorkerTest.closed(worker);noSpools();}
        var reopened=worker(path);try{ready(reopened);var metadata=reopened.metadataResult(REV);assertFalse(metadata.models.has(10));assertEquals(3,metadata.inventory.countType(Blocks.STONE));assertEquals(17,metadata.health);assertEquals(2,metadata.drops.size());}
        finally{RegionReplayWorkerTest.closed(reopened);noSpools();}
    }
    @Test void revisionOwnerCloseAndMissingMetadataFencesDoNotConsumeAValidTransfer()throws Exception {
        var path=fixture();var worker=worker(path);
        try {
            ready(worker);assertThrows(IOException.class,()->worker.metadataResult(new RegionReplayWorker.Revision(9,2,3)));
            var executor=Executors.newSingleThreadExecutor();try{assertTrue(executor.submit(()->{try{worker.metadataResult(REV);return false;}catch(IOException expected){return true;}}).get());}finally{executor.shutdownNow();}
            assertFalse(worker.metadataResult(REV).replayComplete());
        }finally{RegionReplayWorkerTest.closed(worker);noSpools();}
        var unclaimed=worker(path);ready(unclaimed);RegionReplayWorkerTest.closed(unclaimed);assertThrows(IOException.class,()->unclaimed.metadataResult(REV));noSpools();
        byte[] empty=RegionReplayLoaderTest.encodeRows(List.of());
        var synthetic=new RegionReplayWorker(spool(),(cancel,progress)->new RegionReplayWorker.Source(
                RegionReplaySpool.stage(folder.resolve("spool"),new DataInputStream(new ByteArrayInputStream(empty)),e->true,cancel),new Terrain(42),e->true,REV),1,1,0,0);
        try{ready(synthetic);assertThrows(IOException.class,()->synthetic.metadataResult(REV));}finally{RegionReplayWorkerTest.closed(synthetic);noSpools();}
    }
    @Test void aPopulatedPublicHistoryCannotPromoteMetadataOnlyStateToWritable()throws Exception {
        var path=fixture();byte[] before=Files.readAllBytes(path);var worker=worker(path);
        try {
            ready(worker);var metadata=worker.metadataResult(REV);metadata.edits=new LocalGame(path,0).edits;
            assertFalse(metadata.edits.isEmpty());assertFalse(metadata.replayComplete());assertThrows(IOException.class,metadata::save);
            assertThrows(IllegalStateException.class,()->metadata.edit(new Protocol.Edit(0,160,0,0),Blocks.STONE,0));assertArrayEquals(before,Files.readAllBytes(path));
        }finally{RegionReplayWorkerTest.closed(worker);noSpools();}
        var complete=new LocalGame(path,0);assertTrue(complete.replayComplete());complete.save();assertEquals(2,new LocalGame(path,0).edits.size());
    }
    @Test void failedOrCancelledOpeningNeverExposesMetadata()throws Exception {
        var path=fixture();Files.write(path,new byte[]{1},StandardOpenOption.APPEND);var bad=worker(path);
        try{RegionReplayWorkerTest.await(()->bad.status().state()==RegionReplayWorker.State.FAILED);assertThrows(IOException.class,()->bad.metadataResult(REV));assertEquals("",bad.fingerprint());}
        finally{RegionReplayWorkerTest.closed(bad);noSpools();}
        path=fixture();final Path valid=path;var entered=new CountDownLatch(1);var proceed=new CountDownLatch(1);
        var cancelled=new RegionReplayWorker(spool(),(cancel,progress)->{entered.countDown();RegionReplayWorkerTest.release(proceed);return LocalGame.openRegionalReadback(valid,folder.resolve("spool"),REV,cancel,progress);},1,1,56,2);
        try {assertTrue(entered.await(10,TimeUnit.SECONDS));cancelled.close();assertThrows(IOException.class,()->cancelled.metadataResult(REV));}
        finally{proceed.countDown();RegionReplayWorkerTest.closed(cancelled);noSpools();}
    }
}
