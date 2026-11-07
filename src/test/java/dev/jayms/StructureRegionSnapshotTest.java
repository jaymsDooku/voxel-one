package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import dev.jayms.ui.StructureMenu;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jayms.net.structure.StructurePackage.*;
import static org.junit.jupiter.api.Assertions.*;

class StructureRegionSnapshotTest {
    @TempDir Path folder;
    static int pump(StructureOwnerQueue queue,Future<?> result,java.util.function.IntConsumer after)throws Exception {
        long deadline=System.nanoTime()+30_000_000_000L;int calls=0;
        while(!result.isDone()&&System.nanoTime()<deadline){if(queue.pump())after.accept(++calls);else Thread.sleep(1);}
        assertTrue(result.isDone(),"Worker must finish without blocking owner");return calls;
    }
    @Test void regionBatchesMatchWholeSnapshotAndRemainDetached()throws Exception {
        try(var world=new World(42);var queue=new StructureOwnerQueue()) {
            var executor=Executors.newSingleThreadExecutor();
            try {
                var tree=new SparseVoxelOctree(8);tree.set(1,2,3,0xff123456);int id=world.models().stageStructureModel(new ModelDefinition("Part",tree)).id();
                world.apply(new Protocol.Edit(-17,180,-17,Blocks.STONE));world.apply(new Protocol.Edit(15,180,-16,id));world.apply(new Protocol.Edit(-1,196,0,Blocks.piece(Blocks.LED,4),4,1,2,3,0x987654));
                var resident=new Chunk();resident.setBlock(1,4,2,Blocks.BRICKS);world.addChunk(new ChunkPos(-1,11,-1),resident);
                var origin=new Position(-17,180,-17);var bounds=new Bounds(40,33,33,1,2,3);var reference=StructurePackage.capture("Region",bounds,origin,world.structureSnapshot(),()->false,p->{}).encode();
                var future=executor.submit(()->StructureRegionSnapshot.capture(world,origin,40,33,33,queue,()->false,p->{}));assertEquals(3,pump(queue,future,n->{}));var source=future.get();
                assertArrayEquals(reference,StructurePackage.capture("Region",bounds,origin,source,()->false,p->{}).encode());
                var position=new ChunkPos(-1,11,-1);assertEquals(world.structureChunkStamp(position),source.chunkStamp(position));
                world.apply(new Protocol.Edit(-17,180,-17,Blocks.GLASS));resident.setBlock(1,4,2,Blocks.STONE);
                assertEquals(WorldVoxels.encode(Blocks.STONE),source.cell(-17,180,-17).uniform(0,0,0,16));assertEquals(WorldVoxels.encode(Blocks.BRICKS),source.cell(-15,180,-14).uniform(0,0,0,16));
                assertNotEquals(world.structureChunkStamp(position),source.chunkStamp(position));assertThrows(IllegalArgumentException.class,()->source.cell(2000,180,2000));assertNull(source.chunkStamp(new ChunkPos(100,8,100)));
                world.terrain().stressGrid(new dev.jayms.net.city.StressGrid(32));world.terrain().stressGrid(null);assertThrows(ConcurrentModificationException.class,()->source.emptyCell(-17,180,-17));
            }finally{executor.shutdownNow();}
        }
    }
    @Test void ownerPumpsAtMost128ChunksAndChangedStateRejectsThenRetries()throws Exception {
        try(var world=new World(42);var queue=new StructureOwnerQueue()) {
            var executor=Executors.newSingleThreadExecutor();
            try {
                var progress=new CopyOnWriteArrayList<Integer>();var origin=new Position(0,128,0);
                var future=executor.submit(()->StructureRegionSnapshot.capture(world,origin,1024,1,65,queue,()->false,progress::add));
                assertEquals(5,pump(queue,future,n->{}));assertTrue(future.get().emptyChunk(new ChunkPos(63,8,4)));assertEquals(List.of(0,40,80,100),progress);
                var stale=executor.submit(()->StructureRegionSnapshot.capture(world,origin,1024,1,65,queue,()->false,p->{}));
                pump(queue,stale,n->{if(n==2)world.apply(new Protocol.Edit(1000,128,64,Blocks.STONE));});assertInstanceOf(ConcurrentModificationException.class,assertThrows(ExecutionException.class,stale::get).getCause());
                var cancel=new AtomicBoolean();var cancelled=executor.submit(()->StructureRegionSnapshot.capture(world,origin,1024,1,65,queue,cancel::get,p->{}));pump(queue,cancelled,n->cancel.set(true));assertInstanceOf(CancellationException.class,assertThrows(ExecutionException.class,cancelled::get).getCause());
                var retry=executor.submit(()->StructureRegionSnapshot.capture(world,origin,1024,1,65,queue,()->false,p->{}));pump(queue,retry,n->{});assertEquals(WorldVoxels.encode(Blocks.STONE),retry.get().cell(1000,128,64).uniform(0,0,0,16));
            }finally{executor.shutdownNow();}
        }
    }
    @Test void planRejectsOversizedBatchesWrongThreadAndModelChanges()throws Exception {
        try(var world=new World(42)) {
            var resident=new Chunk();resident.setBlock(0,0,0,Blocks.BRICKS);world.addChunk(new ChunkPos(0,8,0),resident);var staleResident=world.beginStructureSnapshot();resident.setBlock(0,0,0,Blocks.STONE);assertThrows(ConcurrentModificationException.class,staleResident::validate);
            var plan=world.beginStructureSnapshot();assertThrows(IllegalArgumentException.class,()->plan.batch(Collections.nCopies(129,new ChunkPos(0,8,0))));
            var executor=Executors.newSingleThreadExecutor();try{assertInstanceOf(IllegalStateException.class,assertThrows(ExecutionException.class,()->executor.submit(()->plan.batch(List.of())).get()).getCause());}finally{executor.shutdownNow();}
            var tree=new SparseVoxelOctree(8);tree.set(0,0,0,0xff123456);world.models().stageStructureModel(new ModelDefinition("Changed",tree));assertThrows(ConcurrentModificationException.class,plan::validate);
        }
    }
    @Test void menuCapturesThroughBatchesAndBusyCommandsDoNotSnapshot()throws Exception {
        var begins=new AtomicInteger();var whole=new AtomicInteger();
        try(var world=new World(42){@Override public StructureSnapshotPlan beginStructureSnapshot(){begins.incrementAndGet();return super.beginStructureSnapshot();}@Override public Source structureSnapshot(){whole.incrementAndGet();return super.structureSnapshot();}};var menu=new StructureMenu(folder)) {
            world.apply(new Protocol.Edit(0,128,0,Blocks.STONE));menu.first=menu.second=menu.anchor=new Position(0,128,0);
            menu.execute("capture Batch",world,null,null);assertTrue(menu.busy);menu.execute("capture Ignored",world,null,null);assertTrue(menu.message.startsWith("Wait"));assertEquals(0,whole.get());
            long deadline=System.nanoTime()+30_000_000_000L;while(menu.busy&&System.nanoTime()<deadline){menu.pump();Thread.sleep(1);}assertFalse(menu.busy);assertEquals("Captured Batch",menu.message);assertEquals(1,begins.get());assertEquals(0,whole.get());assertTrue(Files.exists(folder.resolve("Batch.vstr")));assertFalse(Files.exists(folder.resolve("Ignored.vstr")));
        }
    }
}
