package dev.jayms.render;
import dev.jayms.*;
import dev.jayms.net.model.SparseVoxelOctree;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplaySkyReadinessTest {
 static World replay(){var world=new World();world.attachReplayReadView(new World.ReplayReadView(){public boolean ready(int x,int y,int z){return false;}public SparseVoxelOctree cell(int x,int y,int z){throw new AssertionError("Unknown sky must not sample cells");}public void close(){}});return world;}
 @Test void replayNeverLooksLikeResidentClearSkyAndPendingKeysAreScoped()throws Exception {
  try(var world=replay();var other=replay();var resident=new World();var sky=new SkyVisibilityScheduler()) {
   assertTrue(world.hasReplayReadView());assertFalse(resident.hasReplayReadView());assertTrue(SkyVisibilityScheduler.ceilingReady(null,resident,0,0));assertEquals(List.of(),SkyVisibilityScheduler.ceilingKey(null,resident,0,0));
   assertFalse(SkyVisibilityScheduler.ceilingReady(null,world,0,0));var key=SkyVisibilityScheduler.ceilingKey(null,world,0,0);assertNotEquals(List.of(),key);assertEquals(key,SkyVisibilityScheduler.ceilingKey(null,world,15,15));assertNotEquals(key,SkyVisibilityScheduler.ceilingKey(null,world,16,0));assertNotEquals(key,SkyVisibilityScheduler.ceilingKey(null,other,0,0));
   for(int i=0;i<4;i++){assertNull(sky.update(world,0,0,1_000_000,true));assertEquals(0,sky.pending());assertEquals(0,sky.cachedColumns());assertEquals(0,sky.cachedBytes());}assertEquals(0,sky.published);
   world.close();assertFalse(SkyVisibilityScheduler.ceilingReady(null,world,0,0));
  }
 }
 @Test void residentCacheCannotCertifyReplayAndSchedulerCanReturnToResidentWork()throws Exception {
  try(var resident=new World();var unknown=replay();var foreign=new World();var sky=new SkyVisibilityScheduler()) {
   var chunk=new Chunk();chunk.setBlock(0,0,0,dev.jayms.net.Blocks.STONE);resident.addChunk(new ChunkPos(0,0,0),chunk);
   var prior=SkyVisibilitySchedulerTest.await(sky,resident);assertTrue(prior.valid(resident));assertTrue(sky.cachedColumns()>0);assertTrue(prior.localReady(resident,0,0));
   assertFalse(prior.localReady(unknown,0,0));assertFalse(prior.localReady(foreign,0,0));assertFalse(prior.valid(unknown));assertFalse(SkyVisibilityScheduler.ceilingReady(prior,unknown,0,0));
   assertNull(sky.update(unknown,0,0,0));assertEquals(0,sky.cachedColumns());assertEquals(0,sky.cachedBytes());assertEquals(0,sky.pending());
   var restored=SkyVisibilitySchedulerTest.await(sky,resident);assertNotSame(prior,restored);assertTrue(restored.valid(resident));assertTrue(restored.localReady(resident,0,0));
  }
 }
}
