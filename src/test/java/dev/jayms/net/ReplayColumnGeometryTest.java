package dev.jayms.net;
import dev.jayms.net.model.SparseVoxelOctree;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class ReplayColumnGeometryTest {
 @TempDir Path folder;
 static List<Protocol.Edit> rows(){return List.of(
  new Protocol.Edit(-17,160,-1,Blocks.STONE),new Protocol.Edit(0,160,0,Blocks.GLASS),
  new Protocol.Edit(-17,320,-1,Blocks.GLASS),new Protocol.Edit(-17,160,-1,Blocks.piece(Blocks.LED,4),4,1,2,3,0x224466),
  new Protocol.Edit(-17,160,-1,0,1,0,0,0),new Protocol.Edit(-17,-16,-1,0),
  new Protocol.Edit(-17,160,-1,Blocks.piece(Blocks.LED,4),4,15,15,15,0x33aaff),new Protocol.Edit(-17,320,-1,0));}
 RegionReplaySpool spool()throws Exception{return RegionReplaySpool.stage(folder,new DataInputStream(new ByteArrayInputStream(RegionReplayLoaderTest.encodeRows(rows()))),e->true,()->false);}
 RegionReplayColumn.Snapshot load(RegionReplaySpool spool,Terrain terrain)throws Exception{return RegionReplayColumn.load(spool,-2,-1,terrain,7,3,RegionReplayColumn.MAX_LEAVES,e->true,()->false);}
 void empty()throws Exception{try(var files=Files.list(folder)){assertEquals(0,files.count());}}
 @Test void canonicalFractionalGeometryColoursAndExplicitAirMatchIndependentReplay()throws Exception {
  var terrain=new Terrain(42);RegionReplayColumn.Snapshot captured;
  try(var spool=spool()) {
   captured=load(spool,terrain);assertEquals(7,captured.inputRows());assertEquals(0,captured.terrainRevision());assertEquals(3,captured.cells().size());
   var expected=new WorldVoxels(new Terrain(42));rows().forEach(expected::apply);
   for(var entry:captured.cells().entrySet()) {var p=entry.getKey();var tree=entry.getValue().tree();assertTrue(tree.isFrozen());assertTrue(tree.sameValues(expected.cell(p.x(),p.y(),p.z())));assertThrows(IllegalStateException.class,()->tree.set(0,0,0,1));}
   var detailed=captured.cells().get(new WorldVoxels.CellPosition(-17,160,-1)).tree();assertEquals(0,detailed.get(1,2,3));assertEquals(WorldVoxels.encode(rows().get(6)),detailed.get(15,15,15));
   assertEquals(0,captured.cells().get(new WorldVoxels.CellPosition(-17,320,-1)).tree().occupied());assertEquals(0,captured.cells().get(new WorldVoxels.CellPosition(-17,-16,-1)).tree().occupied());assertThrows(UnsupportedOperationException.class,captured.cells()::clear);
  }empty();assertEquals(3,captured.cells().size());
 }
 @Test void rowCellLeafAndParameterBudgetsRejectWithoutLosingSource()throws Exception {
  try(var spool=spool()) {
   var decoded=new AtomicInteger();assertThrows(IOException.class,()->RegionReplayColumn.load(spool,-2,-1,new Terrain(42),6,3,RegionReplayColumn.MAX_LEAVES,e->{decoded.incrementAndGet();return true;},()->false));assertEquals(0,decoded.get());
   assertThrows(IOException.class,()->RegionReplayColumn.load(spool,-2,-1,new Terrain(42),7,2,RegionReplayColumn.MAX_LEAVES,e->true,()->false));assertThrows(IOException.class,()->RegionReplayColumn.load(spool,-2,-1,new Terrain(42),7,3,0,e->true,()->false));
   assertThrows(IllegalArgumentException.class,()->RegionReplayColumn.load(spool,-2,-1,new Terrain(42),RegionReplayColumn.MAX_ROWS+1,3,1,e->true,()->false));assertThrows(IllegalArgumentException.class,()->RegionReplayColumn.load(spool,-2,-1,new Terrain(42),7,RegionReplayColumn.MAX_CELLS+1,1,e->true,()->false));assertThrows(IllegalArgumentException.class,()->RegionReplayColumn.load(spool,-2,-1,new Terrain(42),7,3,RegionReplayColumn.MAX_LEAVES+1,e->true,()->false));assertEquals(3,load(spool,new Terrain(42)).cells().size());
  }empty();
 }
 @Test void cancellationAndTerrainChangeRejectAndRetryWithoutPublication()throws Exception {
  try(var spool=spool()) {
   var terrain=new Terrain(42);assertThrows(CancellationException.class,()->RegionReplayColumn.load(spool,-2,-1,terrain,7,3,100,e->true,()->true));
   var cancelled=new AtomicBoolean();assertThrows(CancellationException.class,()->RegionReplayColumn.load(spool,-2,-1,terrain,7,3,100,e->{cancelled.set(true);return true;},cancelled::get));
   var changed=new AtomicBoolean();assertThrows(IOException.class,()->RegionReplayColumn.load(spool,-2,-1,terrain,7,3,100,e->{if(!changed.getAndSet(true))terrain.stressGrid(null);return true;},()->false));assertEquals(2,load(spool,terrain).terrainRevision());
  }empty();
 }
 @Test void emptySelectionDoesNotPretendToContainUneditedTerrain()throws Exception {
  try(var spool=spool()) {var empty=RegionReplayColumn.load(spool,500,500,new Terrain(42),0,0,0,e->{fail("Unselected rows must not decode");return false;},()->false);assertEquals(0,empty.inputRows());assertTrue(empty.cells().isEmpty());}empty();
 }
}
