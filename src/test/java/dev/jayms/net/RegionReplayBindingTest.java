package dev.jayms.net;

import dev.jayms.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegionReplayBindingTest {
    @TempDir Path folder;
    static final RegionReplayWorker.Revision REV=ReadbackMetadataTest.REV;
    static final ChunkPos REGION=new ChunkPos(0,10,0);
    static final RegionReplayPage.CellRange RANGE=new RegionReplayPage.CellRange(0,2);
    ReadbackMetadataTest fixture(String name)throws Exception {
        var fixture=new ReadbackMetadataTest();fixture.folder=Files.createDirectories(folder.resolve(name));return fixture;
    }
    World target(LocalGame local){return new World(local.seed,local.models,local.generatorVersion);}
    RegionReplayWorker.Request request(RegionReplayWorker.Binding binding,World world)throws Exception {
        var request=binding.submit(REGION,RANGE,2,world);RegionReplayWorkerTest.done(request);return request;
    }
    @Test void actualSourceMetadataAndDependenciesBindWithoutMutationOrPromotion()throws Exception {
        var fixture=fixture("actual");var path=fixture.fixture();byte[] before=Files.readAllBytes(path);var expected=new LocalGame(path,0);var worker=fixture.worker(path);
        try {
            ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(REV);
            try(var world=target(local);var replacement=target(local)) {
                var binding=worker.bindTarget(local,world);assertEquals(RegionalReadbackSourceTest.hash(before),binding.fingerprint());
                assertThrows(IOException.class,()->worker.bindTarget(local,replacement));
                assertThrows(IOException.class,()->binding.submit(REGION,RANGE,2,replacement));
                var request=request(binding,world);assertThrows(IOException.class,()->request.result(REV));assertThrows(IOException.class,()->request.result(REV,world));
                assertThrows(IOException.class,()->binding.result(request,replacement));
                var page=binding.result(request,world);assertEquals(new ArrayList<>(expected.edits.values()),new ArrayList<>(page.history().values()));
                assertTrue(local.models.entries().stream().anyMatch(e->e.definition().voxels().size()==32));
                assertEquals(0,world.editsVersion());assertEquals(0,world.sample(0,160,0));assertTrue(world.getLoadedChunks().isEmpty());
                assertFalse(local.replayComplete());assertThrows(IOException.class,local::save);assertArrayEquals(before,Files.readAllBytes(path));
            }
        }finally{RegionReplayWorkerTest.closed(worker);fixture.noSpools();}
    }
    @Test void wrongSeedGeneratorLibraryTerrainOrNonFreshTargetRejectWithoutConsumingBinding()throws Exception {
        var fixture=fixture("mismatch");var path=fixture.fixture();var worker=fixture.worker(path);
        try {
            ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(REV);
            for(int change=0;change<7;change++) {
                try(var world=new World(change==0?43:42,change==2?local.models.copy():local.models,change==1?Terrain.LEGACY_VERSION:local.generatorVersion)) {
                    switch(change) {
                        case 3->world.terrain().stressGrid(new dev.jayms.net.city.StressGrid(1));
                        case 4->world.apply(new Protocol.Edit(8,160,0,Blocks.GLASS));
                        case 5->world.addChunk(REGION,new Chunk());
                        case 6->world.close();
                    }
                    assertThrows(IOException.class,()->worker.bindTarget(local,world));
                }
            }
            try(var world=target(local)){assertNotNull(worker.bindTarget(local,world));}
        }finally{RegionReplayWorkerTest.closed(worker);fixture.noSpools();}
    }
    @Test void foreignMetadataAndRequestsRejectEvenWithIdenticalSourceHashAndTags()throws Exception {
        var fixture=fixture("foreign");var path=fixture.fixture();var first=fixture.worker(path);var second=fixture.worker(path);
        try {
            ReadbackMetadataTest.ready(first);ReadbackMetadataTest.ready(second);var a=first.metadataResult(REV);var b=second.metadataResult(REV);assertEquals(a.readbackFingerprint(),b.readbackFingerprint());
            try(var aw=target(a);var bw=target(b)) {
                assertThrows(IOException.class,()->first.bindTarget(b,bw));
                var ab=first.bindTarget(a,aw);var bb=second.bindTarget(b,bw);var ar=request(ab,aw);var br=request(bb,bw);
                assertThrows(IOException.class,()->ab.result(br,aw));assertThrows(IOException.class,()->bb.result(ar,bw));
                assertNotNull(ab.result(ar,aw));assertNotNull(bb.result(br,bw));
            }
        }finally{RegionReplayWorkerTest.closed(first);RegionReplayWorkerTest.closed(second);fixture.noSpools();}
    }
    @Test void metadataModelMutationBeforeBindingAndAfterReadyResultsReject()throws Exception {
        for(int change=0;change<4;change++) {
            var fixture=fixture("models-"+change);var path=fixture.fixture();var worker=fixture.worker(path);
            try {
                ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(REV);
                try(var world=target(local)) {
                    RegionReplayWorker.Binding binding=change>=2?worker.bindTarget(local,world):null;
                    var request=binding==null?null:request(binding,world);
                    if((change&1)==0)local.models=local.models.copy();
                    else {var tree=new SparseVoxelOctree(8);tree.set(0,0,0,0xffabcdef);local.models.stageStructureModel(new ModelDefinition("Changed",tree));}
                    if(binding==null)assertThrows(IOException.class,()->worker.bindTarget(local,world));
                    else {assertThrows(IOException.class,()->binding.result(request,world));assertThrows(IOException.class,()->binding.submit(REGION,RANGE,2,world));}
                }
            }finally{RegionReplayWorkerTest.closed(worker);fixture.noSpools();}
        }
    }
    @Test void sourcePathDeletionDoesNotChangeValidatedSnapshotAndStillCannotSavePartialState()throws Exception {
        var fixture=fixture("snapshot");var path=fixture.fixture();var worker=fixture.worker(path);
        try {
            ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(REV);
            try(var world=target(local)) {
                var binding=worker.bindTarget(local,world);String identity=binding.fingerprint();Files.delete(path);
                var page=binding.result(request(binding,world),world);assertEquals(2,page.history().size());assertEquals(identity,binding.fingerprint());
                assertThrows(IOException.class,local::save);assertFalse(Files.exists(path));
                world.apply(new Protocol.Edit(8,160,0,Blocks.GLASS));assertThrows(IOException.class,()->binding.submit(REGION,RANGE,2,world));
            }
        }finally{RegionReplayWorkerTest.closed(worker);fixture.noSpools();}
    }
    @Test void sourceTerrainMustMatchWholeFileMetadata()throws Exception {
        var fixture=fixture("terrain");var path=fixture.fixture();
        var source=LocalGame.openRegionalReadback(path,fixture.spool(),REV,()->false,n->{});
        try(var owned=source.spool()) {
            for(var terrain:List.of(new Terrain(43),new Terrain(42,Terrain.LEGACY_VERSION),new Terrain(42))) {
                if(terrain.seed==42&&terrain.version==Terrain.CURRENT_VERSION)terrain.stressGrid(null);
                assertThrows(IllegalArgumentException.class,()->new RegionReplayWorker.Source(owned,terrain,source.allowed(),REV,source.fingerprint(),source.metadata()));
            }
            assertNotNull(new RegionReplayWorker.Source(owned,new Terrain(42),source.allowed(),REV,source.fingerprint(),source.metadata()));
        }fixture.noSpools();
    }
    @Test void ownerCloseAndCancellationCannotBypassBinding()throws Exception {
        var fixture=fixture("lifecycle");var path=fixture.fixture();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var spool=fixture.spool();var worker=new RegionReplayWorker(spool,(cancel,progress)->{
            var source=LocalGame.openRegionalReadback(path,spool,REV,cancel,progress);
            return new RegionReplayWorker.Source(source.spool(),source.terrain(),edit->{entered.countDown();try{RegionReplayWorkerTest.release(release);}catch(IOException error){throw new UncheckedIOException(error);}return source.allowed().test(edit);},REV,source.fingerprint(),source.metadata());
        },2,1,56,2);
        try {
            ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(REV);
            try(var world=target(local)) {
                var binding=worker.bindTarget(local,world);var request=binding.submit(REGION,RANGE,2,world);assertTrue(entered.await(10,TimeUnit.SECONDS));assertTrue(request.cancel());release.countDown();
                assertThrows(CancellationException.class,()->binding.result(request,world));
                var error=new AtomicReference<Throwable>();var thread=new Thread(()->{try{binding.submit(REGION,RANGE,2,world);}catch(Throwable failure){error.set(failure);}});thread.start();thread.join(5000);assertFalse(thread.isAlive());assertInstanceOf(IOException.class,error.get());
                var ready=request(binding,world);assertNotNull(binding.result(ready,world));RegionReplayWorkerTest.closed(worker);
                assertThrows(IOException.class,()->binding.result(ready,world));assertThrows(IOException.class,()->binding.submit(REGION,RANGE,2,world));assertEquals(0,world.editsVersion());
            }
        }finally{release.countDown();RegionReplayWorkerTest.closed(worker);fixture.noSpools();}
    }
    @Test void sourceTerrainChangeDuringDecodeCannotPublishBoundPage()throws Exception {
        var fixture=fixture("source-change");var path=fixture.fixture();var spool=fixture.spool();
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var terrain=new AtomicReference<Terrain>();
        var worker=new RegionReplayWorker(spool,(cancel,progress)->{
            var source=LocalGame.openRegionalReadback(path,spool,REV,cancel,progress);terrain.set(source.terrain());
            return new RegionReplayWorker.Source(source.spool(),source.terrain(),edit->{
                entered.countDown();try{RegionReplayWorkerTest.release(release);}catch(IOException error){throw new UncheckedIOException(error);}
                return source.allowed().test(edit);
            },REV,source.fingerprint(),source.metadata());
        },2,1,56,2);
        try {
            ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(REV);
            try(var world=target(local)) {
                var binding=worker.bindTarget(local,world);var request=binding.submit(REGION,RANGE,2,world);
                assertTrue(entered.await(10,TimeUnit.SECONDS));terrain.get().stressGrid(new dev.jayms.net.city.StressGrid(1));release.countDown();RegionReplayWorkerTest.done(request);
                assertEquals(RegionReplayWorker.Phase.FAILED,request.outcome().phase());assertThrows(IOException.class,()->binding.result(request,world));
                var retry=request(binding,world);assertEquals(RegionReplayWorker.Phase.FAILED,retry.outcome().phase());assertThrows(IOException.class,()->binding.result(retry,world));
                assertEquals(0,world.editsVersion());assertTrue(world.getLoadedChunks().isEmpty());
            }
        }finally{release.countDown();RegionReplayWorkerTest.closed(worker);fixture.noSpools();}
    }

}
