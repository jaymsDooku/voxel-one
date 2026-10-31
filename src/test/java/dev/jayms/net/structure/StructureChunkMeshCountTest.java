package dev.jayms.net.structure;

import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureChunkMeshCountTest {
    static SparseVoxelOctree checker() {
        var tree=new SparseVoxelOctree(16);
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
            if(((x+y+z)&1)==0)tree.set(x,y,z,WorldVoxels.encode(Blocks.STONE));
        return tree.freeze();
    }
    static Chunk fragmented(int cells) {
        var chunk=new Chunk();var tree=checker();
        for(int i=0;i<cells;i++)chunk.replaceCell((i%4)*2,0,(i/4)*2,tree);
        return chunk;
    }
    static void matchesRender(Chunk chunk) {
        var before=MeshDataGenerator.generate(chunk);int quads=MeshDataGenerator.count(chunk);
        assertEquals(before.vertices().length/36,quads);
        assertEquals(before.indices().length/6,quads);
        assertEquals(before.surface().length/12,quads);
        var after=MeshDataGenerator.generate(chunk);
        assertArrayEquals(before.vertices(),after.vertices());
        assertArrayEquals(before.indices(),after.indices());
        assertArrayEquals(before.surface(),after.surface());
    }
    @Test void emptySolidAndLoadedNeighbourUseSameGreedyVisibility() {
        var empty=new Chunk();assertEquals(0,MeshDataGenerator.count(empty));matchesRender(empty);
        var single=new Chunk();single.setBlock(15,1,1,Blocks.STONE);
        assertEquals(6,MeshDataGenerator.count(single));
        var world=new World();world.addChunk(new ChunkPos(0,0,0),single);
        var neighbour=new Chunk();neighbour.setBlock(0,1,1,Blocks.STONE);world.addChunk(new ChunkPos(1,0,0),neighbour);
        assertEquals(5,MeshDataGenerator.count(single));matchesRender(single);
        var full=new Chunk();for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)full.setBlock(x,y,z,Blocks.STONE);
        assertEquals(6,MeshDataGenerator.count(full));matchesRender(full);
    }
    @Test void fractionalColoursMaterialsModelsAndWaterMatchWithoutChangingRenderBytes() {
        var random=new Random(20261031);
        for(int trial=0;trial<8;trial++) {
            var chunk=new Chunk();
            int[] types={Blocks.STONE,Blocks.BRICKS,Blocks.GLASS,Blocks.WATER,1024};
            for(int i=0;i<80;i++)chunk.setBlock(random.nextInt(16),random.nextInt(16),random.nextInt(16),types[i%types.length]);
            for(int i=0;i<8;i++) {
                var tree=new SparseVoxelOctree(16);
                tree.fill(0,0,0,8,8,8,0xfe000000|random.nextInt(1<<24));
                tree.fill(8,8,8,16,16,16,WorldVoxels.encode(i%2==0?Blocks.GLASS:Blocks.WATER));
                chunk.replaceCell(i,2,3,tree.freeze());
            }
            matchesRender(chunk);
        }
    }
    @Test void fragmentedBoundaryRetainsExactVertexBudgetRejection() {
        var admitted=fragmented(13);assertEquals(13*12288,MeshDataGenerator.count(admitted));matchesRender(admitted);
        var rejected=fragmented(14);
        var counted=assertThrows(IllegalStateException.class,()->MeshDataGenerator.count(rejected));
        var rendered=assertThrows(IllegalStateException.class,()->MeshDataGenerator.generate(rejected));
        assertEquals("Chunk mesh exceeds bounded vertex budget",counted.getMessage());
        assertEquals(rendered.getMessage(),counted.getMessage());
    }
    @Test void countAndRenderObserveThreadInterruptionForEmptyAndFractionalChunks() {
        for(var chunk:List.of(new Chunk(),fragmented(1))) {
            try {
                Thread.currentThread().interrupt();
                assertThrows(CancellationException.class,()->MeshDataGenerator.count(chunk));
                assertThrows(CancellationException.class,()->MeshDataGenerator.generate(chunk));
            } finally {Thread.interrupted();}
            matchesRender(chunk);
        }
    }
    @Test void engineRejectsChunkBudgetBeforePublishingAndReleasesPlacementLease() throws Exception {
        var tree=checker();var cells=new ArrayList<StructurePackage.Cell>();
        for(int i=0;i<14;i++)cells.add(new StructurePackage.Cell(new StructurePackage.Position((i%4)*2,0,(i/4)*2),tree,-1));
        var asset=new StructurePackage("Chunk budget",new StructurePackage.Bounds(8,1,8,0,0,0),List.of(),cells);
        class Authority implements StructureEngine.Authority {
            final ModelLibrary models=new ModelLibrary();final SparseVoxelOctree empty=new SparseVoxelOctree(16).freeze();int commits;
            public Object lock(){return this;}
            public ModelLibrary models(){return models;}
            public SparseVoxelOctree cell(int x,int y,int z){return empty;}
            public boolean emptyCell(int x,int y,int z){return true;}
            public void commit(ModelLibrary staged,Map<StructurePackage.Position,SparseVoxelOctree> replacement){commits++;}
        }
        var authority=new Authority();var engine=new StructureEngine();
        int originalCount=authority.models.entries().size();long originalRevision=authority.models.revision();
        var failure=assertThrows(IOException.class,()->engine.prepare(asset,new StructurePackage.Position(0,128,0),0,authority,()->false,p->{}));
        assertEquals("Structure would exceed chunk geometry budget",failure.getMessage());
        assertEquals("Chunk mesh exceeds bounded vertex budget",failure.getCause().getMessage());
        assertEquals(0,authority.commits);assertFalse(engine.busy());assertEquals(originalCount,authority.models.entries().size());
        assertEquals(originalRevision,authority.models.revision());
    }
}
