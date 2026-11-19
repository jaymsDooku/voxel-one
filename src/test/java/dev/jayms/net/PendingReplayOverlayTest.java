package dev.jayms.net;

import dev.jayms.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class PendingReplayOverlayTest {
    @TempDir Path folder;
    static final ChunkPos REGION=new ChunkPos(0,10,0);
    static final RegionReplayPage.CellRange RANGE=new RegionReplayPage.CellRange(0,64);
    static Protocol.Edit fraction(){return new Protocol.Edit(3,160,0,Blocks.piece(Blocks.LED,1),1,1,0,1).withColor(0x336699);}
    static RegionReplayOverlay pending(){return RegionReplayOverlay.capture(List.of(new Protocol.Edit(0,160,0,0),fraction()),()->false);}
    ReadbackSaveSnapshotTest contexts()throws IOException {var t=new ReadbackSaveSnapshotTest();t.folder=Files.createDirectories(folder.resolve("contexts"));return t;}
    @Test public void detachedCompositionPreservesColourFractionEmptySpaceAndBounds()throws Exception {
        try(var c=contexts().new Context("compose")) {
            c.worker.submit(()->{
                var base=RegionReplayPage.loadWindow(c.source.spool(),REGION,RANGE,c.source.terrain(),100,8,c.source.allowed(),()->false);int count=base.history().size();
                var combined=RegionReplayPage.withOverlay(base,pending(),c.source.terrain(),8,c.source.allowed(),()->false);
                assertEquals(4,combined.sourceRows());assertEquals(0,combined.cells().get(new WorldVoxels.CellPosition(0,160,0)).tree().get(0,0,0));
                var image=combined.cells().get(new WorldVoxels.CellPosition(3,160,0)).tree();assertEquals(WorldVoxels.encode(fraction()),image.get(8,0,8));assertEquals(0,image.get(0,0,0));assertNull(combined.cells().get(new WorldVoxels.CellPosition(4,160,0)));assertTrue(combined.cells().containsKey(new WorldVoxels.CellPosition(1,160,0)));
                assertThrows(IOException.class,()->RegionReplayPage.withOverlay(base,pending(),c.source.terrain(),3,c.source.allowed(),()->false));assertThrows(CancellationException.class,()->RegionReplayPage.withOverlay(base,pending(),c.source.terrain(),8,c.source.allowed(),()->true));assertEquals(count,base.history().size());assertNotEquals(0,base.cells().get(new WorldVoxels.CellPosition(0,160,0)).tree().get(0,0,0));
                var negative=new Protocol.Edit(-1,160,-1,Blocks.STONE);var negativeRange=new RegionReplayPage.CellRange(192,256);var region=new ChunkPos(-1,10,-1);
                var empty=RegionReplayPage.loadWindow(c.source.spool(),region,negativeRange,c.source.terrain(),100,8,c.source.allowed(),()->false);
                var page=RegionReplayPage.withOverlay(empty,RegionReplayOverlay.capture(List.of(negative),()->false),c.source.terrain(),8,c.source.allowed(),()->false);assertEquals(1,page.sourceRows());assertEquals(WorldVoxels.encode(Blocks.STONE),page.cells().get(new WorldVoxels.CellPosition(-1,160,-1)).tree().get(0,0,0));return null;
            }).get(10,TimeUnit.SECONDS);
        }
    }
    @Test public void workerPagesPendingGeometryAndRejectsMixedChunkAndSkyPaths()throws Exception {
        try(var c=contexts().new Context("worker");var prepared=c.stage(c.snapshot())) {
            var old=c.fixture.worker(c.path);ReadbackMetadataTest.ready(old);RegionReplayWorkerTest.closed(old);
            var publication=prepared.authorize(()->false);c.worker.submit(()->{publication.publish();publication.close();return null;}).get(10,TimeUnit.SECONDS);
            var spool=Files.createDirectories(c.fixture.folder.resolve("pending-pages"));var overlay=RegionReplayOverlay.capture(List.of(fraction()),()->false);
            var worker=new RegionReplayWorker(spool,publication.pagingFactory(old,spool,SavedPagingFactoryTest.NEXT,overlay),1,1,56*1024,2000);
            try {
                ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(SavedPagingFactoryTest.NEXT);assertEquals(worker.fingerprint(),local.readbackFingerprint());
                var request=worker.submit(REGION,RANGE,8,SavedPagingFactoryTest.NEXT);RegionReplayWorkerTest.await(()->request.outcome().phase()==RegionReplayWorker.Phase.READY);assertEquals(WorldVoxels.encode(fraction()),request.result(SavedPagingFactoryTest.NEXT).cells().get(new WorldVoxels.CellPosition(3,160,0)).tree().get(8,0,8));
                try(var world=new World(local.seed,local.models,local.generatorVersion)) {
                    var chunk=worker.submitChunkFenced(REGION,131072,SavedPagingFactoryTest.NEXT,world);RegionReplayWorkerTest.await(()->chunk.outcome().phase()==RegionReplayWorker.Phase.FAILED);assertThrows(IOException.class,()->chunk.chunkResult(SavedPagingFactoryTest.NEXT,world));
                    var binding=worker.bindTarget(local,world);var sky=binding.submitSky(0,0,new dev.jayms.render.WorldSkyVisibility.BuildLimits(256,131072,131072),world);RegionReplayWorkerTest.await(()->sky.outcome().phase()==RegionReplayWorker.Phase.FAILED);assertThrows(IOException.class,()->binding.skyResult(sky,world));assertFalse(world.hasReplayReadView());assertThrows(IOException.class,local::save);
                }
            }finally{RegionReplayWorkerTest.closed(worker);}
            try(var files=Files.list(spool)){assertEquals(0,files.count());}
            var bad=RegionReplayOverlay.capture(List.of(new Protocol.Edit(3,160,0,10)),()->false);assertFalse(c.local.models.has(10));
            var rejected=new RegionReplayWorker(spool,publication.pagingFactory(old,spool,SavedPagingFactoryTest.NEXT,bad),1,1,56*1024,2000);
            try{RegionReplayWorkerTest.await(()->rejected.status().state()==RegionReplayWorker.State.FAILED);assertEquals(RegionReplayWorker.State.FAILED,rejected.status().state());}finally{RegionReplayWorkerTest.closed(rejected);}
            try(var files=Files.list(spool)){assertEquals(0,files.count());}
        }
    }
    @Test public void savingPendingSourceRequiresPrefixAndWritesItOnce()throws Exception {
        try(var c=contexts().new Context("save")) {
            var pending=pending();var missing=ReadbackSaveSnapshot.capture(c.local,c.world,RegionReplayOverlay.empty(),()->false);var complete=ReadbackSaveSnapshot.capture(c.local,c.world,pending,()->false);
            var source=c.worker.submit(()->new RegionReplayWorker.Source(c.source.spool(),c.source.terrain(),c.source.allowed(),c.source.revision(),c.source.fingerprint(),c.source.metadata(),pending)).get(10,TimeUnit.SECONDS);
            c.worker.submit(()->{assertThrows(IOException.class,()->missing.stage(source,()->false,n->{}));return null;}).get(10,TimeUnit.SECONDS);c.noPrefixes();assertArrayEquals(c.original,Files.readAllBytes(c.path));
            var stage=c.worker.submit(()->complete.stage(source,()->false,n->{})).get(10,TimeUnit.SECONDS);try(stage){stage.commit(()->false);}
            c.worker.submit(()->{var loaded=LocalGame.openRegionalReadback(c.path,c.fixture.spool(),ReadbackMetadataTest.REV,()->false,n->{});try{assertEquals(4,loaded.spool().size());}finally{loaded.spool().close();}return null;}).get(10,TimeUnit.SECONDS);c.noPrefixes();
        }
    }
}
