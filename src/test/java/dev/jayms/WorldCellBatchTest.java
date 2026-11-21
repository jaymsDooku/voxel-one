package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldCellBatchTest {
    static World.CellReplacement cell(int x,int block){
        var tree=new SparseVoxelOctree(16);tree.fill(1,2,3,4,5,6,WorldVoxels.encode(block));
        return World.prepareCellReplacement(x,180,0,tree);
    }
    static Chunk neighbor(World world){var c=new Chunk();world.addChunk(new ChunkPos(-1,11,0),c);return c;}
    @Test void coalescesNotificationsAndPreservesGeometryHistoryAndRevisions()throws Exception {
        var cells=new ArrayList<World.CellReplacement>();for(int x=0;x<8;x++)cells.add(cell(x,x%2==0?Blocks.GLASS:Blocks.STONE));
        cells.add(World.prepareCellReplacement(2,180,0,new SparseVoxelOctree(16)));cells.add(cell(16,Blocks.BRICKS));
        try(var actual=new World(42);var direct=new World(42)) {
            actual.addChunk(new ChunkPos(0,11,0),new Chunk());direct.addChunk(new ChunkPos(0,11,0),new Chunk());
            var a=neighbor(actual);var d=neighbor(direct);long av=a.meshRevision(),dv=d.meshRevision();
            actual.replaceCells(cells);for(var c:cells)direct.replaceCell(c);
            assertEquals(1,a.meshRevision()-av);assertEquals(9,d.meshRevision()-dv);assertTrue(a.dirty());
            assertEquals(direct.editsVersion(),actual.editsVersion());assertEquals(direct.editsSnapshot(),actual.editsSnapshot());
            for(int x=0;x<=16;x++)assertTrue(direct.cell(x,180,0).sameValues(actual.cell(x,180,0)));
            for(int x:new int[]{0,1})assertEquals(new ArrayList<>(direct.editsInChunk(new ChunkPos(x,11,0))),new ArrayList<>(actual.editsInChunk(new ChunkPos(x,11,0))));
            actual.unloadChunk(new ChunkPos(0,11,0));actual.addChunk(new ChunkPos(0,11,0),new Chunk());
            assertTrue(direct.cell(0,180,0).sameValues(actual.cell(0,180,0)));
        }
    }
    @Test void nestedBatchesKeepVirtualDispatchAndFlushAtOuterBoundary()throws Exception {
        var first=new AtomicBoolean(true);var calls=new AtomicInteger();
        try(var world=new World(42){@Override public void replaceCell(CellReplacement c){calls.incrementAndGet();super.replaceCell(c);if(first.getAndSet(false))replaceCells(List.of(WorldCellBatchTest.cell(1,Blocks.GLASS)));}}) {
            var neighbor=neighbor(world);long version=neighbor.meshRevision();world.replaceCells(List.of(cell(0,Blocks.STONE)));
            assertEquals(2,calls.get());assertEquals(version+1,neighbor.meshRevision());
            assertFalse(world.cell(0,180,0).isEmpty());assertFalse(world.cell(1,180,0).isEmpty());
            world.replaceCell(cell(2,Blocks.BRICKS));assertEquals(version+2,neighbor.meshRevision());
        }
    }
    @Test void failureFlushesChangedNeighborsAndRestoresNormalNotifications()throws Exception {
        var calls=new AtomicInteger();
        try(var world=new World(42){@Override public void replaceCell(CellReplacement c){super.replaceCell(c);if(calls.incrementAndGet()==2)throw new IllegalStateException("Synthetic post-install failure");}}) {
            var neighbor=neighbor(world);long version=neighbor.meshRevision();
            assertThrows(IllegalStateException.class,()->world.replaceCells(List.of(cell(0,Blocks.STONE),cell(1,Blocks.GLASS),cell(2,Blocks.BRICKS))));
            assertEquals(version+1,neighbor.meshRevision());assertFalse(world.cell(1,180,0).isEmpty());assertTrue(world.cell(2,180,0).isEmpty());
            world.replaceCell(cell(3,Blocks.STONE));assertEquals(version+2,neighbor.meshRevision());
        }
    }
    @Test void rejectsNullBatchBeforeMutationAndEmptyBatchHasNoNotifications()throws Exception {
        try(var world=new World(42)) {
            var neighbor=neighbor(world);long version=neighbor.meshRevision();
            assertThrows(NullPointerException.class,()->world.replaceCells(Arrays.asList(cell(0,Blocks.STONE),null)));
            world.replaceCells(List.of());assertEquals(version,neighbor.meshRevision());assertEquals(0,world.editsVersion());assertTrue(world.cell(0,180,0).isEmpty());
        }
    }
}
