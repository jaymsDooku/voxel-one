package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldCellPatchTest {
    static final int X=-17,Y=128,Z=-1;
    static SparseVoxelOctree cell(int block){var t=new SparseVoxelOctree(16);t.fill(0,0,0,16,16,16,WorldVoxels.encode(block));return t;}
    static WorldVoxels world(){return new WorldVoxels(new Terrain(42));}
    static ChunkPos chunk(){return ChunkPos.fromBlock(X,Y,Z);}
    @Test void stagesWithoutLiveMutationAndMergesLatestUnrelatedChunks() {
        var world=world();world.apply(new Protocol.Edit(X-1,Y,Z,Blocks.BRICKS));var old=world.snapshot();var stamp=world.chunkStamp(chunk());
        var patch=world.captureCellPatch(List.of(chunk()));patch.replaceCell(X,Y,Z,cell(Blocks.STONE));
        assertEquals(0,world.type(X,Y,Z));assertEquals(stamp,world.chunkStamp(chunk()));
        world.apply(new Protocol.Edit(100,Y,100,Blocks.BRICKS));var candidate=patch.merge(world);assertNotNull(candidate);
        assertEquals(Blocks.STONE,candidate.type(X,Y,Z));assertEquals(Blocks.BRICKS,candidate.type(X-1,Y,Z));assertEquals(Blocks.BRICKS,candidate.type(100,Y,100));
        assertEquals(0,old.type(X,Y,Z));assertEquals(0,old.type(100,Y,100));assertEquals(0,world.type(X,Y,Z));
    }
    @Test void changedCapturedChunkRejectsRatherThanOverwritingItsNeighbour() {
        var world=world();var patch=world.captureCellPatch(List.of(chunk()));patch.replaceCell(X,Y,Z,cell(Blocks.STONE));
        world.apply(new Protocol.Edit(X-1,Y,Z,Blocks.BRICKS));assertNull(patch.merge(world));assertEquals(0,world.type(X,Y,Z));
        var retry=world.captureCellPatch(List.of(chunk()));retry.replaceCell(X,Y,Z,cell(Blocks.STONE));var candidate=retry.merge(world);
        assertNotNull(candidate);assertEquals(Blocks.BRICKS,candidate.type(X-1,Y,Z));assertEquals(Blocks.STONE,candidate.type(X,Y,Z));
    }
    @Test void patchSourceCandidateAndOlderSnapshotsRemainIndependentAfterReuse() {
        var world=world();world.apply(new Protocol.Edit(X-1,Y,Z,Blocks.STONE));var old=world.snapshot();
        var patch=world.captureCellPatch(List.of(chunk()));var input=cell(Blocks.STONE);patch.replaceCell(X,Y,Z,input);input.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.BRICKS));
        var first=patch.merge(world);assertNotNull(first);patch.replaceCell(X,Y,Z,cell(Blocks.BRICKS));var second=patch.merge(world);assertNotNull(second);
        assertEquals(Blocks.STONE,first.type(X,Y,Z));assertEquals(Blocks.BRICKS,second.type(X,Y,Z));
        first.apply(new Protocol.Edit(X-1,Y,Z,Blocks.BRICKS));assertEquals(Blocks.STONE,second.type(X-1,Y,Z));assertEquals(Blocks.STONE,old.type(X-1,Y,Z));
        world.apply(new Protocol.Edit(X-2,Y,Z,Blocks.BRICKS));assertEquals(0,first.type(X-2,Y,Z));assertEquals(0,second.type(X-2,Y,Z));assertNull(patch.merge(world));
    }
    @Test void terrainAbaAndDifferentWorldCannotReuseCapturedProof() {
        var terrain=new Terrain(42);var world=new WorldVoxels(terrain);var patch=world.captureCellPatch(List.of(chunk()));patch.replaceCell(X,Y,Z,cell(Blocks.STONE));
        assertNull(patch.merge(world()));terrain.stressGrid(new dev.jayms.net.city.StressGrid(32));terrain.stressGrid(null);assertNull(patch.merge(world));assertEquals(0,world.type(X,Y,Z));
    }
    @Test void clearedBucketInvalidatesPatchAndInvalidWritesAreRejected() {
        var world=world();world.apply(new Protocol.Edit(X-1,Y,Z,Blocks.STONE));var patch=world.captureCellPatch(List.of(chunk()));
        assertThrows(IllegalArgumentException.class,()->patch.replaceCell(X,Y,Z,new SparseVoxelOctree(8)));
        assertThrows(IllegalArgumentException.class,()->patch.replaceCell(100,Y,100,cell(Blocks.STONE)));
        patch.replaceCell(X,Y,Z,cell(Blocks.BRICKS));world.clear();assertNull(patch.merge(world));assertEquals(0,world.type(X,Y,Z));
    }
    @Test void fractionalGeometryAndAirReplacementRetainExactTrees() {
        var world=world();world.apply(new Protocol.Edit(X,Y,Z,Blocks.STONE));var patch=world.captureCellPatch(List.of(chunk()));
        var fractional=new SparseVoxelOctree(16);fractional.fill(1,2,3,7,8,9,WorldVoxels.encode(Blocks.BRICKS));
        patch.replaceCell(X,Y,Z,new SparseVoxelOctree(16));patch.replaceCell(X-1,Y,Z,fractional);var candidate=patch.merge(world);assertNotNull(candidate);
        assertEquals(0,candidate.cell(X,Y,Z).occupied());assertEquals(fractional.leaves(),candidate.cell(X-1,Y,Z).leaves());assertEquals(Blocks.STONE,world.type(X,Y,Z));
    }
}
