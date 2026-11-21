package dev.jayms.net;

import dev.jayms.World;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class SavedPagingTailTest {
    @TempDir Path folder;
    ReadbackSaveSnapshotTest contexts()throws IOException {var t=new ReadbackSaveSnapshotTest();t.folder=Files.createDirectories(folder.resolve("contexts"));return t;}
    @Test public void combinedTransferAcknowledgesExactPrefixAndFailedAttemptsRemainRetryable()throws Exception {
        try(var c=contexts().new Context("combined")) {
            var editor=new RegionReplayEditor(c.world,new RegionReplayJournal());var saved=new Protocol.Edit(0,160,0,0);var later=PendingReplayOverlayTest.fraction();editor.apply(saved,()->false);
            var snapshot=ReadbackSaveSnapshot.captureJournal(c.local,c.world,editor.capture(()->false),()->false);
            var old=c.fixture.worker(c.path);ReadbackMetadataTest.ready(old);RegionReplayWorkerTest.closed(old);
            try(var prepared=c.stage(snapshot)) {
                var publication=prepared.authorize(()->false);editor.apply(later,()->false);
                c.worker.submit(()->{publication.publish();publication.close();return null;}).get(10,TimeUnit.SECONDS);
                var pending=publication.pendingPagingOverlay(()->false);var spool=Files.createDirectories(c.fixture.folder.resolve("paging"));
                var worker=new RegionReplayWorker(spool,publication.pagingFactory(old,spool,SavedPagingFactoryTest.NEXT,pending),1,1,56*1024,2000);
                try {
                    ReadbackMetadataTest.ready(worker);byte[] disk=Files.readAllBytes(c.path);long edits=c.world.editsVersion();
                    c.local.health--;c.local.inventory.add(Blocks.STONE,1);c.local.drops.put(99,new ItemDrop(99,Blocks.STONE,1,1,2,3));
                    assertThrows(CancellationException.class,()->publication.adoptAndAcknowledgePagingTail(worker,SavedPagingFactoryTest.NEXT,pending,()->true));
                    var hooks=new java.util.concurrent.atomic.AtomicInteger();
                    assertThrows(CancellationException.class,()->publication.adoptAndAcknowledgePagingTail(worker,SavedPagingFactoryTest.NEXT,pending,()->hooks.incrementAndGet()==5));
                    assertThrows(IOException.class,()->publication.adoptAndAcknowledgePagingTail(worker,ReadbackMetadataTest.REV,pending,()->false));
                    var copy=RegionReplayOverlay.capture(pending.rows(),()->false);
                    assertThrows(IOException.class,()->publication.adoptAndAcknowledgePagingTail(worker,SavedPagingFactoryTest.NEXT,copy,()->false));
                    c.worker.submit(()->{assertThrows(IOException.class,()->publication.adoptAndAcknowledgePagingTail(worker,SavedPagingFactoryTest.NEXT,pending,()->false));return null;}).get(10,TimeUnit.SECONDS);
                    assertEquals(List.of(saved,later),editor.capture(()->false).overlay().rows());assertArrayEquals(disk,Files.readAllBytes(c.path));
                    var reentries=new java.util.concurrent.atomic.AtomicInteger();
                    var next=publication.adoptAndAcknowledgePagingTail(worker,SavedPagingFactoryTest.NEXT,pending,()->{
                        reentries.incrementAndGet();
                        assertThrows(IOException.class,()->publication.adoptAndAcknowledgePagingTail(worker,SavedPagingFactoryTest.NEXT,pending,()->false));
                        assertThrows(IOException.class,()->publication.adoptPagingTail(worker,SavedPagingFactoryTest.NEXT,pending,()->false));
                        assertThrows(IOException.class,()->publication.acknowledgePagingOverlay(worker,()->false));return false;
                    });
                    assertTrue(reentries.get()>0);assertEquals(List.of(later),editor.capture(()->false).overlay().rows());
                    assertEquals(c.local.health,next.health);assertSame(c.local.models,next.models);assertEquals(c.local.drops,next.drops);assertNotSame(c.local.drops,next.drops);assertNotSame(c.local.inventory,next.inventory);
                    for(int i=0;i<Inventory.SIZE;i++){assertEquals(c.local.inventory.type(i),next.inventory.type(i));assertEquals(c.local.inventory.count(i),next.inventory.count(i));}
                    assertFalse(next.replayComplete());assertThrows(IOException.class,next::save);assertEquals(edits,c.world.editsVersion());assertFalse(c.world.hasReplayReadView());assertArrayEquals(disk,Files.readAllBytes(c.path));
                    assertThrows(IOException.class,()->publication.adoptAndAcknowledgePagingTail(worker,SavedPagingFactoryTest.NEXT,pending,()->false));
                    assertThrows(IOException.class,()->publication.adoptPagingTail(worker,SavedPagingFactoryTest.NEXT,pending,()->false));assertThrows(IOException.class,()->publication.acknowledgePagingOverlay(worker,()->false));assertThrows(IOException.class,()->worker.metadataResult(SavedPagingFactoryTest.NEXT));
                    try(var world=new World(next.seed,next.models,next.generatorVersion)) {
                        var binding=worker.bindTarget(next,world);var page=binding.submit(PendingReplayOverlayTest.REGION,PendingReplayOverlayTest.RANGE,8,world);RegionReplayWorkerTest.done(page);
                        assertEquals(0,binding.result(page,world).cells().get(new WorldVoxels.CellPosition(0,160,0)).tree().get(0,0,0));
                        assertEquals(WorldVoxels.encode(later),binding.result(page,world).cells().get(new WorldVoxels.CellPosition(3,160,0)).tree().get(8,0,8));assertFalse(world.hasReplayReadView());
                    }
                }finally{RegionReplayWorkerTest.closed(worker);}
            }
        }
    }
    @Test public void combinedTransferRejectsChangedGeometryAndModelsWithoutConsumingMetadata()throws Exception {
        var contexts=contexts();
        for(int mode=0;mode<3;mode++)try(var c=contexts.new Context("combined-reject-"+mode)) {
            var editor=new RegionReplayEditor(c.world,new RegionReplayJournal());editor.apply(new Protocol.Edit(0,160,0,0),()->false);
            var snapshot=ReadbackSaveSnapshot.captureJournal(c.local,c.world,editor.capture(()->false),()->false);
            var old=c.fixture.worker(c.path);ReadbackMetadataTest.ready(old);RegionReplayWorkerTest.closed(old);
            try(var prepared=c.stage(snapshot)) {
                var publication=prepared.authorize(()->false);editor.apply(PendingReplayOverlayTest.fraction(),()->false);
                c.worker.submit(()->{publication.publish();publication.close();return null;}).get(10,TimeUnit.SECONDS);
                var pending=publication.pendingPagingOverlay(()->false);var spool=Files.createDirectories(c.fixture.folder.resolve("paging"));
                var worker=new RegionReplayWorker(spool,publication.pagingFactory(old,spool,SavedPagingFactoryTest.NEXT,pending),1,1,56*1024,2000);
                try {
                    ReadbackMetadataTest.ready(worker);byte[] disk=Files.readAllBytes(c.path);
                    if(mode==0)editor.apply(new Protocol.Edit(4,160,0,Blocks.GLASS),()->false);
                    else if(mode==1)c.world.apply(new Protocol.Edit(4,160,0,Blocks.GLASS));else c.local.models=c.local.models.copy();
                    assertThrows(IOException.class,()->publication.adoptAndAcknowledgePagingTail(worker,SavedPagingFactoryTest.NEXT,pending,()->false));
                    assertNotNull(worker.metadataResult(SavedPagingFactoryTest.NEXT));assertArrayEquals(disk,Files.readAllBytes(c.path));
                    if(mode==0)assertEquals(3,editor.capture(()->false).overlay().rows().size());
                }finally{RegionReplayWorkerTest.closed(worker);}
            }
        }
    }
    @Test public void freshPagingRetainsLiveTailAndAcknowledgesOnlySavedPrefix()throws Exception {
        try(var c=contexts().new Context("live")) {
            var editor=new RegionReplayEditor(c.world,new RegionReplayJournal());var saved=new Protocol.Edit(0,160,0,0);var later=PendingReplayOverlayTest.fraction();editor.apply(saved,()->false);
            var snapshot=ReadbackSaveSnapshot.captureJournal(c.local,c.world,editor.capture(()->false),()->false);
            var old=c.fixture.worker(c.path);ReadbackMetadataTest.ready(old);RegionReplayWorkerTest.closed(old);
            try(var prepared=c.stage(snapshot)) {
                var publication=prepared.authorize(()->false);editor.apply(later,()->false);
                c.worker.submit(()->{publication.publish();publication.close();return null;}).get(10,TimeUnit.SECONDS);
                var pending=publication.pendingPagingOverlay(()->false);assertEquals(List.of(later),pending.rows());assertEquals(List.of(saved,later),editor.capture(()->false).overlay().rows());
                var spool=Files.createDirectories(c.fixture.folder.resolve("paging"));var worker=new RegionReplayWorker(spool,publication.pagingFactory(old,spool,SavedPagingFactoryTest.NEXT,pending),1,1,56*1024,2000);
                try {
                    ReadbackMetadataTest.ready(worker);c.local.health--;c.local.inventory.add(Blocks.STONE,1);c.local.drops.put(99,new ItemDrop(99,Blocks.STONE,1,1,2,3));
                    assertThrows(CancellationException.class,()->publication.adoptPagingTail(worker,SavedPagingFactoryTest.NEXT,pending,()->true));
                    c.worker.submit(()->{assertThrows(IOException.class,()->publication.adoptPagingTail(worker,SavedPagingFactoryTest.NEXT,pending,()->false));return null;}).get(10,TimeUnit.SECONDS);
                    assertThrows(IOException.class,()->publication.adoptPagingTail(worker,ReadbackMetadataTest.REV,pending,()->false));
                    var next=publication.adoptPagingTail(worker,SavedPagingFactoryTest.NEXT,pending,()->false);assertEquals(c.local.health,next.health);assertSame(c.local.models,next.models);assertEquals(c.local.drops,next.drops);assertNotSame(c.local.drops,next.drops);assertNotSame(c.local.inventory,next.inventory);
                    for(int i=0;i<Inventory.SIZE;i++){assertEquals(c.local.inventory.type(i),next.inventory.type(i));assertEquals(c.local.inventory.count(i),next.inventory.count(i));}
                    assertFalse(next.replayComplete());assertThrows(IOException.class,next::save);assertThrows(IOException.class,()->worker.metadataResult(SavedPagingFactoryTest.NEXT));assertThrows(IOException.class,()->publication.adoptPagingTail(worker,SavedPagingFactoryTest.NEXT,pending,()->false));
                    assertEquals(List.of(saved,later),editor.capture(()->false).overlay().rows());assertEquals(List.of(later),publication.acknowledgePagingOverlay(worker,()->false).rows());assertEquals(List.of(later),editor.capture(()->false).overlay().rows());assertEquals(2,c.world.editsVersion());assertThrows(IOException.class,()->publication.acknowledgePagingOverlay(worker,()->false));
                    try(var world=new World(next.seed,next.models,next.generatorVersion)) {
                        var binding=worker.bindTarget(next,world);var page=binding.submit(PendingReplayOverlayTest.REGION,PendingReplayOverlayTest.RANGE,8,world);RegionReplayWorkerTest.done(page);
                        assertEquals(0,binding.result(page,world).cells().get(new WorldVoxels.CellPosition(0,160,0)).tree().get(0,0,0));assertEquals(WorldVoxels.encode(later),binding.result(page,world).cells().get(new WorldVoxels.CellPosition(3,160,0)).tree().get(8,0,8));assertFalse(world.hasReplayReadView());
                    }
                    var disk=new LocalGame(c.path,0);assertTrue(disk.edits.values().contains(saved));assertFalse(disk.edits.values().contains(later));assertNotEquals(next.health,disk.health);
                    next.drops.clear();assertFalse(c.local.drops.isEmpty());
                }finally{RegionReplayWorkerTest.closed(worker);}
            }
        }
    }
    @Test public void staleGeometryModelsAndWrongPendingIdentityRejectWithoutTransfer()throws Exception {
        var contexts=contexts();
        for(int mode=0;mode<4;mode++)try(var c=contexts.new Context("reject-"+mode)) {
            var editor=new RegionReplayEditor(c.world,new RegionReplayJournal());editor.apply(new Protocol.Edit(0,160,0,0),()->false);
            var snapshot=ReadbackSaveSnapshot.captureJournal(c.local,c.world,editor.capture(()->false),()->false);var old=c.fixture.worker(c.path);ReadbackMetadataTest.ready(old);RegionReplayWorkerTest.closed(old);
            try(var prepared=c.stage(snapshot)) {
                var publication=prepared.authorize(()->false);editor.apply(PendingReplayOverlayTest.fraction(),()->false);c.worker.submit(()->{publication.publish();publication.close();return null;}).get(10,TimeUnit.SECONDS);
                var pending=publication.pendingPagingOverlay(()->false);var sourcePending=mode==3?RegionReplayOverlay.capture(pending.rows(),()->false):pending;
                var spool=Files.createDirectories(c.fixture.folder.resolve("paging"));var worker=new RegionReplayWorker(spool,publication.pagingFactory(old,spool,SavedPagingFactoryTest.NEXT,sourcePending),1,1,56*1024,2000);
                try {
                    ReadbackMetadataTest.ready(worker);byte[] before=Files.readAllBytes(c.path);
                    if(mode==0)editor.apply(new Protocol.Edit(4,160,0,Blocks.GLASS),()->false);else if(mode==1)c.world.apply(new Protocol.Edit(4,160,0,Blocks.GLASS));else if(mode==2)c.local.models=c.local.models.copy();
                    assertThrows(IOException.class,()->publication.adoptPagingTail(worker,SavedPagingFactoryTest.NEXT,pending,()->false));assertThrows(IOException.class,()->publication.acknowledgePagingOverlay(worker,()->false));assertNotNull(worker.metadataResult(SavedPagingFactoryTest.NEXT));assertArrayEquals(before,Files.readAllBytes(c.path));
                }finally{RegionReplayWorkerTest.closed(worker);}
            }
        }
    }
}
