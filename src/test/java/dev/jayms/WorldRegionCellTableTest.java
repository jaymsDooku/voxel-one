package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldRegionCellTableTest {
    static SparseVoxelOctree stone(){var t=new SparseVoxelOctree(16);t.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));return t.freeze();}
    @Test void denseNegativeRegionLookupAndSnapshotForksKeepAllCellsIndependent() {
        var world=new WorldVoxels(new Terrain(42));var tree=stone();
        WorldVoxels beforeDense=null;
        for(int key=0;key<4096;key++) {
            if(key==1024)beforeDense=world.snapshot();
            world.replaceCell(-16+(key&15),160+(key>>>8),32+((key>>>4)&15),tree);
        }
        var full=world.snapshot();assertNotNull(beforeDense);
        for(int key=0;key<4096;key++) {
            int x=-16+(key&15),y=160+(key>>>8),z=32+((key>>>4)&15);
            assertEquals(Blocks.STONE,world.type(x,y,z));
            assertEquals(key<1024?4096:0,beforeDense.cell(x,y,z).occupied());
        }
        world.replaceCell(-16,160,32,new SparseVoxelOctree(16));
        assertTrue(world.cell(-16,160,32).isEmpty());assertEquals(4096,full.cell(-16,160,32).occupied());
        beforeDense.replaceCell(-16,160,32,new SparseVoxelOctree(16));
        assertEquals(4096,full.cell(-16,160,32).occupied());assertEquals(4096,world.cell(-1,175,47).occupied());
    }
    @Test void explicitAirRemainsAnOverrideAndDenseRegionOccupancyChangesDoNotLeak() {
        var world=new WorldVoxels(new Terrain(42));var empty=new SparseVoxelOctree(16).freeze();
        for(int y=160;y<176;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)world.replaceCell(x,y,z,empty);
        var snapshot=world.snapshot();var region=new ChunkPos(0,10,0);
        assertTrue(world.emptyChunk(region));world.replaceCell(15,175,15,stone());
        assertFalse(world.emptyChunk(region));assertTrue(snapshot.emptyChunk(region));
        assertTrue(snapshot.cell(15,175,15).isEmpty());
        // An air override below the terrain must not turn into an absent table slot.
        world.replaceCell(0,0,0,empty);assertTrue(world.cell(0,0,0).isEmpty());
        world.replaceCell(1,0,0,stone());assertTrue(world.cell(0,0,0).isEmpty());
    }
}
