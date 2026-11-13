package dev.jayms.render;
import dev.jayms.*;
import dev.jayms.net.model.SparseVoxelOctree;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplayLightingCaptureTest {
 static class View implements World.ReplayReadView {
  Map<ChunkPos,SparseVoxelOctree> chunks=new LinkedHashMap<>();
  public boolean ready(int x,int y,int z){return chunks.containsKey(ChunkPos.fromBlock(x,y,z));}
  public Map<ChunkPos,SparseVoxelOctree> lightingChunks(){return chunks;}
  public SparseVoxelOctree cell(int x,int y,int z){throw new AssertionError("Lighting must use complete frozen roots");}
  public void close(){}
 }
 static class ReplayWorld extends World {
  @Override public Collection<World.EditedCell> editedCellsInChunk(ChunkPos p){throw new AssertionError("Replay must not use resident history fallback");}
 }
 static SparseVoxelOctree root(){var tree=new SparseVoxelOctree(256);tree.fill(0,0,0,16,16,16,0xff123456);return tree.freeze();}
 @Test void missingCoverageStaysPendingAndNeverSubmitsProceduralFallback()throws Exception {
  var view=new View();try(var world=new ReplayWorld();var lighting=new WorldLighting()) {
   world.attachReplayReadView(view);var capture=new WorldLighting.Capture(world,0,0);assertFalse(capture.pump(Long.MAX_VALUE));assertEquals(0,capture.index);
   for(int i=0;i<4;i++)assertNull(lighting.update(world,0,0,1,false,null,null,Long.MAX_VALUE));assertEquals(0,lighting.submitted);
   view.chunks.put(new ChunkPos(-3,-2,-3),root());assertFalse(capture.valid(world,0,0));var partial=new WorldLighting.Capture(world,0,0);assertFalse(partial.pump(Long.MAX_VALUE));assertEquals(1,partial.index);assertNotNull(partial.chunks[0]);assertNull(partial.chunks[1]);
  }
 }
 @Test void completeCoverageCopiesMapRetainsFrozenRootsAndFencesArrivalEvictionAndReplacement()throws Exception {
  var view=new View();var root=root();for(int z=-3;z<3;z++)for(int y=-2;y<6;y++)for(int x=-3;x<3;x++)view.chunks.put(new ChunkPos(x,y,z),root);
  try(var world=new ReplayWorld()) {
   world.attachReplayReadView(view);var snapshot=world.replayLightingChunks();assertEquals(288,snapshot.size());assertThrows(UnsupportedOperationException.class,snapshot::clear);
   var capture=new WorldLighting.Capture(world,0,0);assertTrue(capture.pump(Long.MAX_VALUE));assertEquals(288,capture.index);for(var tree:capture.chunks)assertSame(root,tree);assertTrue(capture.valid(world,0,0));
   try(var lighting=new WorldLighting()){assertNull(lighting.update(world,0,0,1,false,null,null,Long.MAX_VALUE));assertEquals(0,lighting.submitted,"Known geometry alone must not admit unknown sky lighting");}
   assertTrue(world.getLoadedChunks().isEmpty());assertEquals(0,world.editsVersion());
   assertThrows(IllegalStateException.class,()->root.set(0,0,0,0));view.chunks.put(new ChunkPos(-3,-2,-3),root());assertFalse(capture.valid(world,0,0));assertSame(root,snapshot.get(new ChunkPos(-3,-2,-3)));
   var current=new WorldLighting.Capture(world,0,0);view.chunks.remove(new ChunkPos(-3,-2,-3));assertFalse(current.valid(world,0,0));assertEquals(288,snapshot.size());
  }
 }
 @Test void invalidSnapshotsRejectAndResidentModeRetainsNullContract()throws Exception {
  try(var resident=new World()){assertNull(resident.replayLightingChunks());}
  var view=new View();try(var world=new World()) {
   world.attachReplayReadView(view);var p=new ChunkPos(0,0,0);view.chunks.put(p,new SparseVoxelOctree(256));assertThrows(IllegalStateException.class,world::replayLightingChunks);
   view.chunks.put(p,new SparseVoxelOctree(16).freeze());assertThrows(IllegalStateException.class,world::replayLightingChunks);
   view.chunks.clear();var root=root();for(int i=0;i<289;i++)view.chunks.put(new ChunkPos(i,0,0),root);assertThrows(IllegalStateException.class,world::replayLightingChunks);
   var many=view.chunks;view.chunks=new AbstractMap<>() {
    @Override public int size(){return 1;}
    @Override public Set<Map.Entry<ChunkPos,SparseVoxelOctree>> entrySet(){return many.entrySet();}
   };assertThrows(IllegalStateException.class,world::replayLightingChunks);
   view.chunks=new LinkedHashMap<>();assertTrue(world.replayLightingChunks().isEmpty());
  }
 }
}
