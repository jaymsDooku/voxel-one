package dev.jayms.net;

import dev.jayms.net.model.*;
import dev.jayms.render.WorldSkyVisibility;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class ReplayColumnSkyInputTest {
 @TempDir Path folder;
 static List<Protocol.Edit> rows(){return List.of(new Protocol.Edit(-17,-32,-1,0),
  new Protocol.Edit(-17,160,-1,Blocks.STONE),new Protocol.Edit(-17,160,-1,0,1,0,0,0),new Protocol.Edit(-17,160,-1,0,1,0,1,0),
  new Protocol.Edit(-18,320,-1,Blocks.GLASS),new Protocol.Edit(-18,320,-1,0),
  new Protocol.Edit(-18,-48,-1,Blocks.GLASS));}
 RegionReplaySpool spool()throws Exception{return RegionReplaySpool.stage(folder,new DataInputStream(new ByteArrayInputStream(RegionReplayLoaderTest.encodeRows(rows()))),e->true,()->false);}
 RegionReplayColumn.Snapshot snapshot(RegionReplaySpool spool,Terrain t)throws Exception{return RegionReplayColumn.load(spool,-2,-1,t,7,4,RegionReplayColumn.MAX_LEAVES,e->true,()->false);}
 @Test void completeNaturalRangeAndOverridesMatchWorldGeometryAndSky()throws Exception {
  var terrain=new Terrain(42);var models=new ModelLibrary();
  try(var spool=spool()) {
   var source=snapshot(spool,terrain);var progress=new AtomicInteger();var result=RegionReplayColumn.skyInput(source,terrain,models,RegionReplayColumn.MAX_LEAVES,()->false,progress::set);
   assertEquals(32768,progress.get());assertEquals(8,result.input().chunks().size());assertEquals(3,result.input().edits().size());
   var world=new WorldVoxels(new Terrain(42));rows().forEach(world::apply);
   for(var chunk:result.input().chunks()) {
    assertTrue(chunk.root().isFrozen());var expected=new SparseVoxelOctree(256);
    for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)expected.replaceRegion(x*16,y*16,z*16,world.cell(chunk.position().chunkX()*16+x,chunk.position().chunkY()*16+y,chunk.position().chunkZ()*16+z));
    assertTrue(expected.sameValues(chunk.root()));
   }
   for(var cell:result.input().edits())assertTrue(cell.root().sameValues(world.cell(cell.x(),cell.y(),cell.z())));
   assertEquals(0,result.input().chunks().get(0).root().get(15*16,0,15*16)); // Natural stone explicitly removed.
   var sky=WorldSkyVisibility.build(result.input());assertEquals(256,sky.columns);
   assertEquals(1,sky.sampleKnown(-16.75f,100,-.75f,-1)); // Real fractional hole.
   assertEquals(0,sky.sampleKnown(-16.25f,100,-.75f,-1)); // Remaining roof quadrant.
   assertEquals(1,sky.sampleKnown(-17.25f,100,-.25f,-1)); // Cleared high glass leaves no stale roof.
   assertEquals(-1,sky.sampleKnown(0,100,0,-1)); // Unselected column remains unknown.
   assertThrows(UnsupportedOperationException.class,()->result.input().chunks().clear());
   assertThrows(UnsupportedOperationException.class,()->result.input().edits().clear());
  }
 }
 @Test void budgetsCancellationAndTerrainChangesRejectAndRetry()throws Exception {
  var terrain=new Terrain(42);var models=new ModelLibrary();
  try(var spool=spool()) {
   var source=snapshot(spool,terrain);
   assertThrows(IllegalArgumentException.class,()->RegionReplayColumn.skyInput(source,terrain,models,RegionReplayColumn.MAX_LEAVES+1,()->false,n->{}));
   assertThrows(IOException.class,()->RegionReplayColumn.skyInput(source,terrain,models,0,()->false,n->{}));
   assertThrows(CancellationException.class,()->RegionReplayColumn.skyInput(source,terrain,models,RegionReplayColumn.MAX_LEAVES,()->true,n->{}));
   var progress=new AtomicInteger();assertThrows(CancellationException.class,()->RegionReplayColumn.skyInput(source,terrain,models,RegionReplayColumn.MAX_LEAVES,()->progress.get()>=128,progress::set));assertEquals(128,progress.get());
   assertThrows(IOException.class,()->RegionReplayColumn.skyInput(source,terrain,models,RegionReplayColumn.MAX_LEAVES,()->false,n->{if(n==128)terrain.stressGrid(null);}));
   assertThrows(IOException.class,()->RegionReplayColumn.skyInput(source,terrain,models,RegionReplayColumn.MAX_LEAVES,()->false,n->{}));
   assertEquals(2,RegionReplayColumn.skyInput(snapshot(spool,terrain),terrain,models,RegionReplayColumn.MAX_LEAVES,()->false,n->{}).terrainRevision());
  }
 }
 @Test void invalidColumnCellsRejectBeforeTerrainProgress()throws Exception {
  var terrain=new Terrain(42);var root=new SparseVoxelOctree(16);var cells=Map.of(new WorldVoxels.CellPosition(0,160,0),WorldVoxels.prepareCellImage(root));
  var invalid=new RegionReplayColumn.Snapshot(-2,-1,1,0,cells);var progress=new AtomicInteger();
  assertThrows(IOException.class,()->RegionReplayColumn.skyInput(invalid,terrain,new ModelLibrary(),RegionReplayColumn.MAX_LEAVES,()->false,n->progress.incrementAndGet()));assertEquals(0,progress.get());
 }
 @Test void modelSnapshotIsIndependentAndMidBuildChangeRejects()throws Exception {
  var terrain=new Terrain(42);var models=new ModelLibrary();
  try(var spool=spool()) {
   var source=snapshot(spool,terrain);var result=RegionReplayColumn.skyInput(source,terrain,models,RegionReplayColumn.MAX_LEAVES,()->false,n->{});
   var voxel=new SparseVoxelOctree(8);voxel.set(0,0,0,0xffff3344);var definition=new ModelDefinition("Synthetic column",voxel);
   models.register(definition,"synthetic");assertNotEquals(models.revision(),result.input().models().revision());assertEquals(result.modelRevision(),result.input().models().revision());
   var next=new SparseVoxelOctree(8);next.set(1,0,0,0xff3344ff);var changed=new ModelDefinition("Changed column",next);
   assertThrows(IOException.class,()->RegionReplayColumn.skyInput(source,terrain,models,RegionReplayColumn.MAX_LEAVES,()->false,n->{if(n==128)try{models.register(changed,"synthetic");}catch(IOException e){throw new UncheckedIOException(e);}}));
   assertEquals(models.revision(),RegionReplayColumn.skyInput(source,terrain,models,RegionReplayColumn.MAX_LEAVES,()->false,n->{}).modelRevision());
  }
 }

}
