package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import dev.jayms.ui.StructureMenu;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jayms.net.structure.StructurePackage.*;
import static org.junit.jupiter.api.Assertions.*;

public class StructureSparseSnapshotTest {
    @TempDir Path folder;
    static SparseVoxelOctree block(int type){var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(type));return tree.freeze();}
    public static StructurePackage sparseAsset()throws Exception {
        var model=new SparseVoxelOctree(8);model.set(1,2,3,0xff267ac9);var detail=new SparseVoxelOctree(16);detail.fill(8,0,0,16,8,8,WorldVoxels.encode(new Protocol.Edit(0,0,0,Blocks.LED).withColor(0x26cfa8)));detail.freeze();
        return new StructurePackage("Sparse footprint",new Bounds(1024,256,1024,0,0,0),List.of(new ModelDefinition("Sparse model",model)),List.of(new Cell(new Position(0,0,0),block(Blocks.STONE),-1),new Cell(new Position(1023,0,0),null,0),new Cell(new Position(0,255,1023),detail,-1),new Cell(new Position(1023,255,1023),block(Blocks.WATER),-1)));
    }
    static int pump(StructureOwnerQueue queue,Future<?> future,java.util.function.IntConsumer after)throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);int calls=0;
        while(!future.isDone()&&System.nanoTime()<deadline){if(queue.pump())after.accept(++calls);else Thread.sleep(1);}assertTrue(future.isDone());return calls;
    }
    @Test void maximumBoundsWithFourCellsCaptureFourCompleteChunksInsteadOfTheBox()throws Exception {
        var asset=sparseAsset();var anchor=new Position(-15,161,-15);var outsideCell=new Protocol.Edit(-16,170,-10,Blocks.BRICKS);
        try(var world=new World(42);var queue=new StructureOwnerQueue()) {
            world.apply(outsideCell);var executor=Executors.newSingleThreadExecutor();
            try {
                var job=executor.submit(()->StructureRegionSnapshot.captureOccupied(world,asset,anchor,0,queue,()->false,p->{}));assertEquals(3,pump(queue,job,n->{}));var snapshot=job.get();
                var chunks=new HashSet<ChunkPos>();for(var p:asset.occupiedPositions()){var target=new Position(anchor.x()+p.x(),anchor.y()+p.y(),anchor.z()+p.z());chunks.add(ChunkPos.fromBlock(target.x(),target.y(),target.z()));assertEquals(0,snapshot.cell(target.x(),target.y(),target.z()).occupied());}
                assertEquals(4,chunks.size());for(var c:chunks)assertEquals(world.structureChunkStamp(c),snapshot.chunkStamp(c));
                assertEquals(WorldVoxels.encode(Blocks.BRICKS),snapshot.cell(-16,170,-10).uniform(0,0,0,16));assertThrows(IllegalArgumentException.class,()->snapshot.cell(0,161,0));assertNull(snapshot.chunkStamp(new ChunkPos(0,10,0)));assertFalse(StructurePreview.blocked(asset,anchor,0,snapshot,()->false,p->{}));
                world.apply(new Protocol.Edit(-16,170,-10,Blocks.GLASS));assertEquals(WorldVoxels.encode(Blocks.BRICKS),snapshot.cell(-16,170,-10).uniform(0,0,0,16));
                world.terrain().stressGrid(new dev.jayms.net.city.StressGrid(32));assertThrows(ConcurrentModificationException.class,()->snapshot.emptyCell(-15,161,-15));
            }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS));}
        }
    }
    @Test void everyRotationUsesExactDestinationChunksAndMatchesFullSnapshotConflicts()throws Exception {
        var asset=sparseAsset();try(var world=new World(42);var queue=new StructureOwnerQueue()) {
            var executor=Executors.newSingleThreadExecutor();try {
                for(int rotation=0;rotation<4;rotation++) {
                    final int r=rotation;var anchor=new Position(-15,161,-15);var box=StructureEngine.footprint(asset,anchor,r);
                    var p=StructureEngine.rotate(new Position(1023,0,0),1024,1024,r);int x=box.minX()+p.x(),y=box.minY()+p.y(),z=box.minZ()+p.z();world.apply(new Protocol.Edit(x,y,z,Blocks.STONE));
                    var job=executor.submit(()->StructureRegionSnapshot.captureOccupied(world,asset,anchor,r,queue,()->false,n->{}));pump(queue,job,n->{});var snapshot=job.get();var reference=world.structureSnapshot();assertEquals(StructurePreview.blocked(asset,anchor,r,reference,()->false,n->{}),StructurePreview.blocked(asset,anchor,r,snapshot,()->false,n->{}));assertTrue(StructurePreview.blocked(asset,anchor,r,snapshot,()->false,n->{}));
                    for(var q:asset.occupiedPositions()){var relative=StructureEngine.rotate(q,1024,1024,r);int a=box.minX()+relative.x(),b=box.minY()+relative.y(),c=box.minZ()+relative.z();assertTrue(reference.cell(a,b,c).sameValues(snapshot.cell(a,b,c)));}
                    world.apply(new Protocol.Edit(x,y,z,Blocks.AIR));
                }
            }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS));}
        }
    }
    @Test void duplicateKeysCoalesceAndBatchesStillStopAt128Chunks()throws Exception {
        var points=new ArrayList<Position>();for(int i=0;i<129;i++)points.add(new Position((i%64)*16,160,(i/64)*16));points.add(points.get(0));points.add(points.get(128));var progress=new CopyOnWriteArrayList<Integer>();
        try(var world=new World(42);var queue=new StructureOwnerQueue()) {
            var executor=Executors.newSingleThreadExecutor();try {
                var job=executor.submit(()->StructureRegionSnapshot.captureCells(world,points,queue,()->false,progress::add));assertEquals(4,pump(queue,job,n->{}));var source=job.get();assertEquals(List.of(0,99,100),progress);assertTrue(source.emptyCell(0,160,0));assertTrue(source.emptyCell(0,160,32));assertThrows(IllegalArgumentException.class,()->source.emptyCell(16,160,32));
            }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS));}
        }
    }
    @Test void invalidInputsAndCancellationBeforeOwnerRequestsNeverReadTheWorld()throws Exception {
        var begins=new AtomicInteger();try(var world=new World(42){@Override public StructureSnapshotPlan beginStructureSnapshot(){begins.incrementAndGet();return super.beginStructureSnapshot();}};var queue=new StructureOwnerQueue()) {
            assertThrows(IllegalStateException.class,()->StructureRegionSnapshot.captureCells(world,List.of(new Position(0,160,0)),queue,()->false,p->{}));
            var executor=Executors.newSingleThreadExecutor();try {
                var asset=sparseAsset();var job=executor.submit(()->{assertThrows(CancellationException.class,()->StructureRegionSnapshot.captureOccupied(world,asset,new Position(0,160,0),0,queue,()->true,p->{}));assertThrows(IOException.class,()->StructureRegionSnapshot.captureOccupied(world,asset,new Position(0,900,0),0,queue,()->false,p->{}));assertThrows(IllegalArgumentException.class,()->StructureRegionSnapshot.captureOccupied(world,asset,new Position(0,160,0),4,queue,()->false,p->{}));assertThrows(IllegalArgumentException.class,()->StructureRegionSnapshot.captureCells(world,List.of(),queue,()->false,p->{}));assertThrows(IOException.class,()->StructureRegionSnapshot.captureCells(world,List.of(new Position(Integer.MAX_VALUE,160,0)),queue,()->false,p->{}));return null;});job.get(10,TimeUnit.SECONDS);assertEquals(0,begins.get());assertFalse(queue.pump());
            }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS));}
        }
    }
    @Test void concurrentWorldAndModelChangesRejectBeforePublicationAndCanRetry()throws Exception {
        var points=new ArrayList<Position>();for(int i=0;i<129;i++)points.add(new Position((i%64)*16,160,(i/64)*16));
        try(var world=new World(42);var queue=new StructureOwnerQueue()) {
            var executor=Executors.newSingleThreadExecutor();try {
                var stale=executor.submit(()->StructureRegionSnapshot.captureCells(world,points,queue,()->false,p->{}));pump(queue,stale,n->{if(n==2)world.apply(new Protocol.Edit(900,160,900,Blocks.GLASS));});assertInstanceOf(ConcurrentModificationException.class,assertThrows(ExecutionException.class,stale::get).getCause());
                var changed=executor.submit(()->StructureRegionSnapshot.captureCells(world,points,queue,()->false,p->{}));pump(queue,changed,n->{if(n==2)try{var tree=new SparseVoxelOctree(8);tree.set(0,0,0,0xffabcdef);world.models().stageStructureModel(new ModelDefinition("Changed",tree));}catch(Exception failure){throw new RuntimeException(failure);}});assertInstanceOf(ConcurrentModificationException.class,assertThrows(ExecutionException.class,changed::get).getCause());
                var stop=new AtomicBoolean();var cancelled=executor.submit(()->StructureRegionSnapshot.captureCells(world,points,queue,stop::get,p->{}));pump(queue,cancelled,n->stop.set(true));assertInstanceOf(CancellationException.class,assertThrows(ExecutionException.class,cancelled::get).getCause());
                var retry=executor.submit(()->StructureRegionSnapshot.captureCells(world,points,queue,()->false,p->{}));pump(queue,retry,n->{});assertNotNull(retry.get().models());
            }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS));}
        }
    }
    @Test void realMenuPreviewPlacementSaveAndUndoPreserveEmptySpaceAndLaterEdits()throws Exception {
        var file=folder.resolve("synthetic-world.dat");var local=new LocalGame(file,42);var begins=new AtomicInteger();var whole=new AtomicInteger();
        try(var world=new World(42,local.models){@Override public StructureSnapshotPlan beginStructureSnapshot(){begins.incrementAndGet();return super.beginStructureSnapshot();}@Override public Source structureSnapshot(){whole.incrementAndGet();return super.structureSnapshot();}};var menu=new StructureMenu(folder.resolve("library"))) {
            var glass=new Protocol.Edit(1,160,0,Blocks.GLASS);WorldVoxels.remember(local.edits,glass);world.apply(glass);local.save();menu.selected=sparseAsset();menu.target=new Position(0,160,0);
            menu.execute("preview",world,local,null);waitMenu(menu);assertTrue(menu.message.startsWith("Footprint clear"));assertEquals(1,begins.get());
            menu.execute("place",world,local,null);waitMenu(menu);assertTrue(menu.message.startsWith("Placed and saved"),menu.message);assertEquals(Blocks.STONE,world.sample(0,160,0));assertTrue(Blocks.structureModel(world.sample(1023,160,0)));assertEquals(Blocks.WATER,world.sample(1023,415,1023));assertEquals(Blocks.GLASS,world.sample(1,160,0));assertEquals(2,begins.get());
            var later=new Protocol.Edit(2,160,0,Blocks.BRICKS);WorldVoxels.remember(local.edits,later);world.apply(later);menu.execute("undo",world,local,null);waitMenu(menu);assertEquals("Structure placement undone",menu.message);assertEquals(3,begins.get());assertEquals(0,whole.get());assertEquals(Blocks.AIR,world.sample(0,160,0));assertEquals(Blocks.GLASS,world.sample(1,160,0));assertEquals(Blocks.BRICKS,world.sample(2,160,0));
            var saved=new LocalGame(file,0);var replay=new WorldVoxels(new Terrain(saved.seed,saved.generatorVersion));saved.edits.values().forEach(replay::apply);assertEquals(Blocks.GLASS,replay.type(1,160,0));assertEquals(Blocks.BRICKS,replay.type(2,160,0));assertEquals(Blocks.AIR,replay.type(0,160,0));
        }
    }
    static void waitMenu(StructureMenu menu)throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);while(menu.busy&&System.nanoTime()<end){menu.pump();Thread.sleep(1);}assertFalse(menu.busy,"Menu must finish on owner pumps");}
}
