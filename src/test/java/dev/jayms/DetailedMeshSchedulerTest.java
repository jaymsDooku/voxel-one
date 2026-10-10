package dev.jayms;

import dev.jayms.net.Blocks;
import dev.jayms.net.Protocol;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DetailedMeshSchedulerTest {
    private static World scene(){var w=new World();var c=new Chunk();c.setBlock(15,0,0,Blocks.BRICKS);w.addChunk(new ChunkPos(0,0,0),c);return w;}
    private static DetailedMeshScheduler.Result await(DetailedMeshScheduler scheduler,World w)throws Exception {
        long end=System.nanoTime()+5_000_000_000L;
        while(System.nanoTime()<end){var result=scheduler.ready(w);if(result!=null)return result;Thread.sleep(2);}
        fail("Mesh worker did not finish");return null;
    }
    @Test void snapshotPreservesSurfaceAndClosedLoadedBorder()throws Exception {
        try(var w=scene()){
            var n=new Chunk();n.setBlock(0,0,0,Blocks.STONE);w.addChunk(new ChunkPos(1,0,0),n);
            var c=w.getLoadedChunks().get(new ChunkPos(0,0,0));
            w.apply(new Protocol.Edit(15,0,1,Blocks.LED,1,1,0,0).withColor(0xff2040));
            var snapshot=new ChunkSnapshot(w,new ChunkPos(0,0,0),c);
            var expected=MeshDataGenerator.generate(c);var actual=MeshDataGenerator.generate(snapshot);
            assertArrayEquals(expected.vertices(),actual.vertices());assertArrayEquals(expected.indices(),actual.indices());assertArrayEquals(expected.surface(),actual.surface());
            n.setBlock(0,0,0,0);assertEquals(Blocks.STONE,snapshot.neighbor(16,0,0));
        }
    }
    @Test void editAndUnloadFenceResults()throws Exception {
        try(var w=scene();var s=new DetailedMeshScheduler()){
            var p=new ChunkPos(0,0,0);assertTrue(s.request(w,p));w.apply(new Protocol.Edit(15,0,0,0));
            assertNull(s.ready(w));assertEquals(1,s.rejected);
            assertTrue(s.request(w,p));var latest=await(s,w);assertNull(latest.data());
            w.apply(new Protocol.Edit(15,0,0,Blocks.BRICKS));assertTrue(s.request(w,p));w.unloadChunk(p);
            assertNull(s.ready(w));assertEquals(2,s.rejected);
        }
    }
    @Test void boundedJobsAndNeighborInvalidation()throws Exception {
        try(var w=scene();var s=new DetailedMeshScheduler()){
            var p=new ChunkPos(0,0,0);long rev=w.getLoadedChunks().get(p).meshRevision();
            assertTrue(s.request(w,p));var adjacent=new Chunk();w.addChunk(new ChunkPos(1,0,0),adjacent);
            assertTrue(w.getLoadedChunks().get(p).meshRevision()>rev);assertNull(s.ready(w));
            for(int i=0;i<10;i++){var pos=new ChunkPos(i,1,0);var c=new Chunk();c.setBlock(0,0,0,Blocks.STONE);w.addChunk(pos,c);s.request(w,pos);assertTrue(s.queued()<=DetailedMeshScheduler.MAX_JOBS);}
            assertEquals(DetailedMeshScheduler.MAX_JOBS,s.queued());
        }
    }
    @Test void independentlySwappedOpaqueBordersRemainClosed()throws Exception {
        try(var w=scene()){
            var p=new ChunkPos(0,0,0);var n=new Chunk();n.setBlock(0,0,0,Blocks.STONE);w.addChunk(new ChunkPos(1,0,0),n);
            var c=w.getLoadedChunks().get(p);var before=MeshDataGenerator.generate(new ChunkSnapshot(w,p,c,true));
            assertEquals(36,before.indices().length,"Hidden opaque boundary face closes the mesh");
            w.apply(new Protocol.Edit(16,0,0,0));var after=MeshDataGenerator.generate(new ChunkSnapshot(w,p,c,true));
            assertArrayEquals(before.vertices(),after.vertices(),"Neighbor removal cannot expose a stale border hole");
            w.apply(new Protocol.Edit(16,0,0,Blocks.WATER));
            assertEquals(new ChunkSnapshot(w,p,c).neighbor(16,0,0),new ChunkSnapshot(w,p,c,true).neighbor(16,0,0),"Water visibility is unchanged");
        }
    }
    @Test void incrementalColumnsTrackEditsUnloadAndDistance()throws Exception {
        try(var w=new World()){
            var p=new ChunkPos(0,4,0);var c=new Chunk();w.addChunk(p,c);
            var first=w.renderedColumns(8.2f,8.2f,112);assertTrue(first.contains(new ChunkPos(0,0,0)));
            assertSame(first,w.renderedColumns(8.3f,8.3f,112),"Unchanged visual mask is reused");
            w.apply(new Protocol.Edit(1,65,1,Blocks.BRICKS));assertFalse(w.renderedColumns().contains(new ChunkPos(0,0,0)),"Parent stays until a nonempty mesh exists");
            assertTrue(w.dirtyMeshes().containsKey(p));w.apply(new Protocol.Edit(1,65,1,0));assertTrue(w.renderedColumns().contains(new ChunkPos(0,0,0)));
            assertFalse(w.renderedColumns(1000,1000,16).contains(new ChunkPos(0,0,0)));w.unloadChunk(p);
            assertTrue(w.renderedColumns().isEmpty());assertTrue(w.dirtyMeshes().isEmpty());
        }
    }
}
