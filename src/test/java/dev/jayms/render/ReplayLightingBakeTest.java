package dev.jayms.render;
import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ReplayLightingBakeTest {
 static Future<?> pending(WorldLighting lighting)throws Exception{var field=WorldLighting.class.getDeclaredField("pending");field.setAccessible(true);return (Future<?>)field.get(lighting);}
 static void baked(WorldLighting lighting)throws Exception{var task=pending(lighting);assertNotNull(task);task.get(30,TimeUnit.SECONDS);}
 static WorldSkyVisibility roof(){var tiles=new ArrayList<WorldSkyVisibility>();var solid=new SparseVoxelOctree(256);solid.fill(0,0,0,256,256,256,WorldVoxels.encode(Blocks.STONE));solid.freeze();for(int x=-3;x<3;x++)for(int z=-3;z<3;z++)tiles.add(WorldSkyVisibility.build(new WorldSkyVisibility.Input(List.of(new WorldSkyVisibility.Geometry(new ChunkPos(x,10,z),solid)),List.of(),new ModelLibrary())));return WorldSkyVisibility.mergeBounded(tiles,8_000_000,()->false);}
 @Test public void explicitBakeUsesCompleteRootsAndHighSkyWithoutTerrainFallback()throws Exception {
  try(var w=new World();var lighting=new WorldLighting()) {
   var f=new ReplayJointLightingCoverageTest.Fixture();f.attach(w);assertNull(lighting.updateReplay(w,0,0,1,false,Long.MAX_VALUE));baked(lighting);LightVolume volume=lighting.updateReplay(w,0,0,1,false,Long.MAX_VALUE,false);assertNotNull(volume);assertEquals(1,lighting.completed);assertTrue(volume.sampleLighting(.5f,-30.5f,.5f)[3]>.99f,"Frozen empty source stays lit below natural terrain");assertTrue(lighting.acceptedFence().valid(w,0,0));assertNull(lighting.acceptedFence().chunks);volume=null;
   f.sky.set(roof());f.skyKey.set(new Object());assertFalse(lighting.acceptedFence().valid(w,0,0));assertNull(lighting.updateReplay(w,0,0,1,false,Long.MAX_VALUE));baked(lighting);volume=lighting.updateReplay(w,0,0,1,false,Long.MAX_VALUE,false);assertNotNull(volume);assertEquals(2,lighting.completed);assertEquals(0,volume.sampleLighting(.5f,90.5f,.5f)[3],.0001f,"Source roof above GI ceiling blocks sky");assertFalse(SkyVisibilityScheduler.ceilingReady(null,w,0,0));assertTrue(w.getLoadedChunks().isEmpty());assertEquals(0,w.editsVersion());
  }
 }
 @Test public void sameRootsNewGenerationRejectsCompletionAndCurrentRetryPublishes()throws Exception {
  try(var w=new World();var lighting=new WorldLighting()) {
   var f=new ReplayJointLightingCoverageTest.Fixture();f.attach(w);assertNull(lighting.updateReplay(w,0,0,1,false,Long.MAX_VALUE));assertEquals(1,lighting.submitted);f.geometryKey.set(new Object());baked(lighting);assertNull(lighting.updateReplay(w,0,0,1,false,Long.MAX_VALUE,false));assertEquals(1,lighting.rejected);assertEquals(0,lighting.completed);assertNull(pending(lighting));
   f.skyValid.set(false);assertNull(lighting.updateReplay(w,0,0,1,false,Long.MAX_VALUE));assertEquals(1,lighting.submitted);f.skyValid.set(true);assertNull(lighting.updateReplay(w,0,0,1,false,Long.MAX_VALUE));baked(lighting);assertNotNull(lighting.updateReplay(w,0,0,1,false,Long.MAX_VALUE,false));assertEquals(2,lighting.submitted);assertEquals(1,lighting.completed);assertTrue(lighting.acceptedFence().valid(w,0,0));
  }
 }
 @Test public void ordinaryUpdatesMissingCoverageAndResidentModeRemainClosed()throws Exception {
  try(var resident=new World();var lighting=new WorldLighting()){assertThrows(IllegalArgumentException.class,()->lighting.updateReplay(resident,0,0,1,false,Long.MAX_VALUE));}
  try(var w=new World();var lighting=new WorldLighting()) {
   var f=new ReplayJointLightingCoverageTest.Fixture();f.attach(w);assertNull(lighting.updateReplay(w,0,0,1,false,0));assertEquals(0,lighting.submitted);assertNull(lighting.update(w,0,0,1,false,null,null,Long.MAX_VALUE));assertEquals(0,lighting.submitted);f.sky.set(ReplayJointLightingCoverageTest.Fixture.sky(true));assertNull(lighting.updateReplay(w,0,0,1,false,Long.MAX_VALUE));assertEquals(0,lighting.submitted);
  }
 }
}
