package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldCellImageTest {
    static SparseVoxelOctree solid(int type){var t=new SparseVoxelOctree(16);t.fill(0,0,0,16,16,16,WorldVoxels.encode(type));return t;}
    static SparseVoxelOctree fractional(){var t=new SparseVoxelOctree(16);t.fill(1,2,3,4,5,6,0xfe123456);t.fill(12,11,10,13,12,11,WorldVoxels.encode(Blocks.GLASS));return t;}
    static byte[] bytes(SparseVoxelOctree tree)throws IOException{var b=new ByteArrayOutputStream();tree.write(new DataOutputStream(b));return b.toByteArray();}
    @Test void workerImageIsDetachedFrozenAndSharedUntilEachWorldEdits()throws Exception {
        var source=fractional();var expected=source.copy().freeze();var task=new FutureTask<>(()->WorldVoxels.prepareCellImage(source));
        var worker=new Thread(task,"synthetic-cell-image-worker");worker.start();var image=task.get(10,TimeUnit.SECONDS);worker.join();
        assertDoesNotThrow(()->source.fill(0,0,0,16,16,16,0));assertTrue(expected.sameValues(image.tree()));
        assertThrows(IllegalStateException.class,()->image.tree().fill(0,0,0,16,16,16,0));
        var first=new WorldVoxels(new Terrain(42));var second=new WorldVoxels(new Terrain(42));
        first.installCellImage(-17,180,-1,image);first.installCellImage(0,180,0,image);second.installCellImage(-17,180,-1,image);
        assertSame(image.tree(),first.cell(-17,180,-1));assertSame(image.tree(),first.cell(0,180,0));assertSame(image.tree(),second.cell(-17,180,-1));
        var snapshot=first.snapshot();first.apply(new Protocol.Edit(-17,180,-1,Blocks.BRICKS));
        assertSame(image.tree(),snapshot.cell(-17,180,-1));assertSame(image.tree(),second.cell(-17,180,-1));assertSame(image.tree(),first.cell(0,180,0));
        assertFalse(first.cell(-17,180,-1).sameValues(expected));assertTrue(expected.sameValues(image.tree()));
        var copy=image.tree().copy();copy.fill(0,0,0,16,16,16,0);assertTrue(expected.sameValues(image.tree()));
        first.clear();assertTrue(expected.sameValues(snapshot.cell(-17,180,-1)));assertTrue(expected.sameValues(second.cell(-17,180,-1)));
    }
    @Test void allCellKindsRetainExactBytesAndLegacyValues()throws Exception {
        var redundant=new ByteArrayOutputStream();var out=new DataOutputStream(redundant);out.writeByte(16);out.writeByte(2);for(int i=0;i<8;i++){out.writeByte(1);out.writeInt(WorldVoxels.encode(Blocks.STONE));}
        var trees=List.of(new SparseVoxelOctree(16),solid(Blocks.STONE),solid(Blocks.FLOWER_POT),fractional(),fractional().packedCellCopy(),SparseVoxelOctree.read(new DataInputStream(new ByteArrayInputStream(redundant.toByteArray()))));
        for(var tree:trees){var image=WorldVoxels.prepareCellImage(tree);var prepared=new WorldVoxels(new Terrain(42));var legacy=new WorldVoxels(new Terrain(42));
            prepared.installCellImage(-17,180,-1,image);legacy.replaceCell(-17,180,-1,tree);
            assertArrayEquals(bytes(tree),bytes(prepared.cell(-17,180,-1)));assertArrayEquals(bytes(legacy.cell(-17,180,-1)),bytes(prepared.cell(-17,180,-1)));
            assertEquals(legacy.type(-17,180,-1),prepared.type(-17,180,-1));assertTrue(tree.sameValues(image.tree()));}
    }
    @Test void legacyApiKeepsCallerIsolationAndInvalidImagesLeaveWorldUnchanged() {
        var world=new WorldVoxels(new Terrain(42));var source=solid(Blocks.STONE);world.replaceCell(0,180,0,source);
        assertNotSame(source,world.cell(0,180,0));source.fill(0,0,0,16,16,16,0);assertEquals(Blocks.STONE,world.type(0,180,0));
        var stamp=world.chunkStamp(new ChunkPos(0,11,0));
        assertThrows(NullPointerException.class,()->world.replaceCell(0,180,0,null));
        assertThrows(NullPointerException.class,()->world.installCellImage(0,180,0,null));
        assertThrows(NullPointerException.class,()->WorldVoxels.prepareCellImage(null));
        var small=new SparseVoxelOctree(8);assertThrows(IllegalArgumentException.class,()->WorldVoxels.prepareCellImage(small));assertDoesNotThrow(()->small.set(0,0,0,0xff123456));
        assertEquals(stamp,world.chunkStamp(new ChunkPos(0,11,0)));assertEquals(Blocks.STONE,world.type(0,180,0));
    }
    @Test void productionPreparedInstallSharesMetadataButPlacedWorldsStayIndependent()throws Exception {
        var source=fractional();var expected=source.copy().freeze();var replacement=World.prepareCellReplacement(-17,180,-1,source);
        source.fill(0,0,0,16,16,16,0);
        try(var first=new World(42);var second=new World(42)) {
            first.replaceCell(replacement);second.replaceCells(List.of(replacement));var p=ChunkPos.fromBlock(-17,180,-1);
            var a=first.editedCellsInChunk(p).iterator().next().voxels();var b=second.editedCellsInChunk(p).iterator().next().voxels();
            assertSame(a,first.cell(-17,180,-1));assertSame(a,b);assertSame(b,second.cell(-17,180,-1));
            assertThrows(IllegalStateException.class,()->a.fill(0,0,0,16,16,16,0));
            first.apply(new Protocol.Edit(-17,180,-1,Blocks.BRICKS));assertTrue(expected.sameValues(b));assertFalse(first.cell(-17,180,-1).sameValues(b));
            second.addChunk(p,new Chunk());assertTrue(expected.sameValues(second.cell(-17,180,-1)));second.unloadChunk(p);second.addChunk(p,new Chunk());assertTrue(expected.sameValues(second.cell(-17,180,-1)));
            assertArrayEquals(bytes(expected),bytes(b));
        }
    }
}
