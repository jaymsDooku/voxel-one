package dev.jayms.render;
import dev.jayms.*;
import dev.jayms.net.model.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ReplayJointLightingCoverageTest {
 static class Fixture {
  final AtomicReference<Object> geometryKey=new AtomicReference<>(new Object()),skyKey=new AtomicReference<>(new Object());
  final AtomicBoolean geometryValid=new AtomicBoolean(true),skyValid=new AtomicBoolean(true);
  final Map<ChunkPos,SparseVoxelOctree> roots=new LinkedHashMap<>();final AtomicReference<WorldSkyVisibility> sky=new AtomicReference<>();
  Fixture(){var empty=new SparseVoxelOctree(256).freeze();for(int z=-3;z<3;z++)for(int y=-2;y<6;y++)for(int x=-3;x<3;x++)roots.put(new ChunkPos(x,y,z),empty);sky.set(sky(false));}
  static WorldSkyVisibility sky(boolean gap){var tiles=new ArrayList<WorldSkyVisibility>();var empty=new SparseVoxelOctree(256).freeze();for(int x=-3;x<3;x++)for(int z=-3;z<3;z++){if(gap&&x==2&&z==2)continue;tiles.add(WorldSkyVisibility.build(new WorldSkyVisibility.Input(List.of(new WorldSkyVisibility.Geometry(new ChunkPos(x,10,z),empty)),List.of(),new ModelLibrary())));}return WorldSkyVisibility.mergeBounded(tiles,8_000_000,()->false);}
  void attach(World w){w.attachReplayReadView(new World.ReplayReadView(){public boolean ready(int x,int y,int z){return false;}public SparseVoxelOctree cell(int x,int y,int z){throw new AssertionError("No procedural cell fallback");}public World.ReplayGeometrySource geometrySource(){return new World.ReplayGeometrySource(){public World owner(){return w;}public Map<ChunkPos,SparseVoxelOctree> roots(){return roots;}public Object key(){return geometryKey.get();}public boolean current(){return geometryValid.get();}};}public World.ReplaySkySource skySource(){return new World.ReplaySkySource(){public World owner(){return w;}public WorldSkyVisibility data(){return sky.get();}public Object key(){return skyKey.get();}public boolean current(){return skyValid.get();}};}public void close(){}});}
 }
 @Test public void completeGeometryAndSkyYieldCurrentCpuReceiptWithoutBake()throws Exception {
  try(var w=new World();var other=new World();var lighting=new WorldLighting()) {
   var f=new Fixture();f.attach(w);var held=w.replayLightingSnapshot(0,0);assertNotNull(held);assertTrue(held.current(w));assertFalse(held.current(other));assertEquals(288,held.roots().size());assertSame(f.sky.get(),held.sky());assertSame(f.geometryKey.get(),held.geometryKey());assertSame(f.skyKey.get(),held.skyKey());assertEquals(0,held.x());assertEquals(0,held.z());assertThrows(UnsupportedOperationException.class,()->held.roots().clear());
   assertNull(lighting.update(w,0,0,1,false,null,null,Long.MAX_VALUE));assertEquals(0,lighting.submitted);assertFalse(SkyVisibilityScheduler.ceilingReady(null,w,0,0));assertTrue(w.getLoadedChunks().isEmpty());assertEquals(0,w.editsVersion());assertFalse(w.isLoaded(0,0,0));
   f.geometryKey.set(new Object());assertFalse(held.current(w));assertThrows(IllegalStateException.class,held::roots);assertThrows(IllegalStateException.class,held::sky);var current=w.replayLightingSnapshot(0,0);assertNotNull(current);f.skyKey.set(new Object());assertFalse(current.current(w));assertThrows(IllegalStateException.class,current::sky);
  }
 }
 @Test public void missingAndStaleCoverageNeverUseSamplingFallback()throws Exception {
  try(var w=new World()) {
   var f=new Fixture();f.attach(w);var removed=f.roots.remove(new ChunkPos(2,5,2));assertNull(w.replayLightingSnapshot(0,0));f.roots.put(new ChunkPos(2,5,2),removed);f.sky.set(Fixture.sky(true));assertEquals(1,f.sky.get().sampleKnown(47.5f,100,47.5f,1));assertNull(w.replayLightingSnapshot(0,0));f.sky.set(Fixture.sky(false));assertNotNull(w.replayLightingSnapshot(0,0));
   f.geometryValid.set(false);assertNull(w.replayLightingSnapshot(0,0));f.geometryValid.set(true);f.skyValid.set(false);assertNull(w.replayLightingSnapshot(0,0));f.skyValid.set(true);var held=w.replayLightingSnapshot(0,0);assertNotNull(held);assertNull(w.replayLightingSnapshot(16,0));w.close();assertFalse(held.current(w));assertThrows(IllegalStateException.class,held::roots);assertThrows(IllegalStateException.class,()->w.replayLightingSnapshot(0,0));
  }
 }
 @Test public void focusBoundsAndAlignmentRejectBeforeProviderAccess()throws Exception {
  try(var resident=new World()){assertNull(resident.replayLightingSnapshot(0,0));}
  try(var w=new World()) {
   w.attachReplayReadView(new World.ReplayReadView(){public boolean ready(int x,int y,int z){return false;}public SparseVoxelOctree cell(int x,int y,int z){throw new AssertionError();}public World.ReplayGeometrySource geometrySource(){throw new AssertionError("Invalid focus reached provider");}public void close(){}});
   assertThrows(IllegalArgumentException.class,()->w.replayLightingSnapshot(1,0));assertThrows(IllegalArgumentException.class,()->w.replayLightingSnapshot(0,-1));assertThrows(IllegalArgumentException.class,()->w.replayLightingSnapshot(Integer.MIN_VALUE,0));assertThrows(IllegalArgumentException.class,()->w.replayLightingSnapshot(0,Integer.MAX_VALUE-15));
  }
 }
}
