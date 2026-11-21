package dev.jayms.net;

import dev.jayms.*;
import dev.jayms.net.model.SparseVoxelOctree;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegionReplayMeshNeighboursTest {
    static RegionReplayChunk.Snapshot snapshot(ChunkPos pos,SparseVoxelOctree root,long revision) {
        return new RegionReplayChunk.Snapshot(pos,root,revision,1,1);
    }
    static SparseVoxelOctree solid(int type) {
        var root=new SparseVoxelOctree(256);root.fill(0,0,0,256,256,256,WorldVoxels.encode(type));return root;
    }
    @Test void sixFacesCullExactlyAndUnknownNeighboursKeepExteriorFaces()throws Exception {
        var pos=new ChunkPos(-7,10,-3);var source=snapshot(pos,solid(Blocks.STONE),2);
        var offsets=new int[][]{{-1,0,0},{1,0,0},{0,-1,0},{0,1,0},{0,0,-1},{0,0,1}};
        var neighbours=new ArrayList<RegionReplayChunk.Snapshot>();
        assertEquals(36,RegionReplayMesh.prepare(source,()->false).mesh().indices().length);
        for(int i=0;i<6;i++) {
            var d=offsets[i];neighbours.add(snapshot(new ChunkPos(pos.chunkX()+d[0],pos.chunkY()+d[1],pos.chunkZ()+d[2]),solid(Blocks.STONE),2));
            assertEquals((5-i)*6,RegionReplayMesh.prepare(source,neighbours,()->false).mesh().indices().length);
        }
        // A partial cell or a model is not an opaque whole-cell neighbour.
        var partial=new SparseVoxelOctree(256);partial.set(255,0,0,WorldVoxels.encode(Blocks.STONE));
        for(var root:List.of(partial,solid(1024),new SparseVoxelOctree(256))) {
            assertEquals(36,RegionReplayMesh.prepare(source,List.of(snapshot(new ChunkPos(-8,10,-3),root,2)),()->false).mesh().indices().length);
        }
        // Keep the existing water rule: opaque geometry behind water remains.
        assertEquals(36,RegionReplayMesh.prepare(source,List.of(snapshot(new ChunkPos(-8,10,-3),solid(Blocks.WATER),2)),()->false).mesh().indices().length);
        assertEquals(30,RegionReplayMesh.prepare(snapshot(pos,solid(Blocks.WATER),2),List.of(snapshot(new ChunkPos(-8,10,-3),solid(Blocks.STONE),2)),()->false).mesh().indices().length);
    }
    @Test void malformedNeighbourSetsRejectBeforeProducingMeshAndRetryWorks()throws Exception {
        var pos=new ChunkPos(0,0,0);var source=snapshot(pos,solid(Blocks.STONE),0);
        var neighbour=snapshot(new ChunkPos(1,0,0),solid(Blocks.STONE),0);
        assertThrows(IllegalArgumentException.class,()->RegionReplayMesh.prepare(source,List.of(neighbour,neighbour),()->false));
        for(var bad:List.of(pos,new ChunkPos(1,1,0),new ChunkPos(Integer.MIN_VALUE,0,0)))
            assertThrows(IllegalArgumentException.class,()->RegionReplayMesh.prepare(source,List.of(snapshot(bad,solid(Blocks.STONE),0)),()->false));
        assertThrows(IllegalArgumentException.class,()->RegionReplayMesh.prepare(source,Collections.nCopies(7,neighbour),()->false));
        assertThrows(IllegalArgumentException.class,()->RegionReplayMesh.prepare(source,List.of(snapshot(new ChunkPos(1,0,0),new SparseVoxelOctree(16),0)),()->false));
        assertThrows(IOException.class,()->RegionReplayMesh.prepare(source,List.of(snapshot(new ChunkPos(1,0,0),solid(Blocks.STONE),2)),()->false));
        assertEquals(30,RegionReplayMesh.prepare(source,List.of(neighbour),()->false).mesh().indices().length);
    }
    @Test void capturesNeighbourRootWithoutFreezingCallerAndCancelsSampling()throws Exception {
        var pos=new ChunkPos(0,0,0);var source=snapshot(pos,solid(Blocks.STONE),0);var mutable=solid(Blocks.STONE);
        var neighbour=snapshot(new ChunkPos(1,0,0),mutable,0);var calls=new AtomicInteger();
        assertThrows(CancellationException.class,()->RegionReplayMesh.prepare(source,List.of(neighbour),()->calls.incrementAndGet()>100));
        calls.set(0);
        var result=RegionReplayMesh.prepare(source,List.of(neighbour),()->{
            // Capturing roots occurs before the mesh sampler runs.
            if(calls.incrementAndGet()==100)mutable.fill(0,0,0,256,256,256,0);
            return false;
        });
        assertTrue(mutable.isEmpty());assertEquals(30,result.mesh().indices().length);
        assertEquals(36,RegionReplayMesh.prepare(source,List.of(neighbour),()->false).mesh().indices().length);
    }
}
