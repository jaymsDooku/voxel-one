package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldPackedCellImageTest {
    @Test void installedImageStaysPackedAcrossSnapshotsAndIndependentEdits() throws Exception {
        var source=SparseVoxelPackedCellTest.fixture();var expected=SparseVoxelPackedCellTest.bytes(source);
        var image=WorldVoxels.prepareCellImage(source);
        assertTrue(SparseVoxelPackedCellTest.packedWithoutGraph(image.tree()));
        assertArrayEquals(expected,SparseVoxelPackedCellTest.bytes(image.tree()));
        var world=new WorldVoxels(new Terrain(42));world.installCellImage(-17,180,-1,image);
        var snapshot=world.snapshot();assertSame(image.tree(),snapshot.cell(-17,180,-1));
        source.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        assertArrayEquals(expected,SparseVoxelPackedCellTest.bytes(world.cell(-17,180,-1)));
        world.apply(new Protocol.Edit(-17,180,-1,Blocks.piece(Blocks.BRICKS,4),4,0,0,0));
        assertEquals(WorldVoxels.encode(Blocks.BRICKS),world.cell(-17,180,-1).get(0,0,0));
        assertArrayEquals(expected,SparseVoxelPackedCellTest.bytes(snapshot.cell(-17,180,-1)));
        assertTrue(SparseVoxelPackedCellTest.packedWithoutGraph(snapshot.cell(-17,180,-1)));
        assertThrows(IllegalStateException.class,()->image.tree().set(0,0,0,0));
    }
    @Test void preparedWorldReplacementKeepsCanonicalBackingPackedAndReplayEquivalent() throws Exception {
        var source=SparseVoxelPackedCellTest.fixture();var expected=source.copy();
        var replacement=World.prepareCellReplacement(-17,180,-1,source);
        try(var world=new World(42)) {world.replaceCell(replacement);
        var cell=world.cell(-17,180,-1);
        assertTrue(SparseVoxelPackedCellTest.packedWithoutGraph(cell));assertTrue(expected.sameValues(cell));
        var replay=new WorldVoxels(new Terrain(42));for(var edit:replacement.replayState().edits())replay.apply(edit);
        assertTrue(replay.cell(-17,180,-1).sameValues(cell));
        source.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        assertTrue(expected.sameValues(cell));assertTrue(SparseVoxelPackedCellTest.packedWithoutGraph(cell));
        assertSame(cell,world.editedCellsInChunk(ChunkPos.fromBlock(-17,180,-1)).iterator().next().voxels());
        }
    }
    @Test void airUniformValidationAndCancellationKeepPriorWorldIntact() throws Exception {
        var world=new WorldVoxels(new Terrain(42));var air=WorldVoxels.prepareCellImage(new SparseVoxelOctree(16));
        world.installCellImage(0,0,0,air);assertTrue(world.cell(0,0,0).isEmpty());
        var solid=new SparseVoxelOctree(16);solid.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        var image=WorldVoxels.prepareCellImage(solid);world.installCellImage(1,180,0,image);
        assertFalse(SparseVoxelPackedCellTest.packedWithoutGraph(image.tree()));assertEquals(4096,image.tree().occupied());
        assertThrows(IllegalArgumentException.class,()->WorldVoxels.prepareCellImage(new SparseVoxelOctree(32)));
        assertThrows(NullPointerException.class,()->WorldVoxels.prepareCellImage(null));
        Thread.currentThread().interrupt();try {
            assertThrows(java.util.concurrent.CancellationException.class,()->WorldVoxels.prepareCellImage(SparseVoxelPackedCellTest.fixture()));
        } finally {Thread.interrupted();}
        assertTrue(world.cell(0,0,0).isEmpty());assertEquals(Blocks.STONE,world.type(1,180,0));
    }
}
