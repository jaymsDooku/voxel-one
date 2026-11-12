package dev.jayms.render;
import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SkyBuildBudgetTest {
 static WorldSkyVisibility.Input chunk(SparseVoxelOctree tree,ModelLibrary models){return new WorldSkyVisibility.Input(List.of(new WorldSkyVisibility.Geometry(new ChunkPos(-2,10,-1),tree.freeze())),List.of(),models);}
 static WorldSkyVisibility.BuildLimits limits(int columns,int leaves,int segments){return new WorldSkyVisibility.BuildLimits(columns,leaves,segments);}
 @Test public void mergedLeafFanoutCountsBeforeCompactionAndRetryMatchesResident() {
  var tree=new SparseVoxelOctree(256);tree.fill(0,0,0,256,256,256,WorldVoxels.encode(Blocks.STONE));var input=chunk(tree,new ModelLibrary());
  assertThrows(IllegalStateException.class,()->WorldSkyVisibility.buildBounded(input,limits(255,1,256),()->false));
  assertThrows(IllegalStateException.class,()->WorldSkyVisibility.buildBounded(input,limits(256,0,256),()->false));
  assertThrows(IllegalStateException.class,()->WorldSkyVisibility.buildBounded(input,limits(256,1,255),()->false));
  var result=WorldSkyVisibility.buildBounded(input,limits(256,1,256),()->false);assertArrayEquals(WorldSkyVisibility.build(input).data,result.data);assertEquals(256,result.columns);assertEquals(0,result.sample(-31.5f,150,-15.5f));
 }
 @Test public void modelExpansionCancellationAndRetry()throws Exception {
  var voxels=new SparseVoxelOctree(8);voxels.set(0,0,0,0xffff3344);voxels.set(7,7,7,0xff3344ff);var models=new ModelLibrary();int type=models.register(new ModelDefinition("Synthetic budget",voxels),"synthetic").id();
  var tree=new SparseVoxelOctree(256);tree.fill(0,0,0,256,256,256,WorldVoxels.encode(type));var input=chunk(tree,models);
  assertThrows(IllegalStateException.class,()->WorldSkyVisibility.buildBounded(input,limits(256,1,4),()->false));
  var calls=new AtomicInteger();assertThrows(CancellationException.class,()->WorldSkyVisibility.buildBounded(input,limits(256,1,8192),()->calls.incrementAndGet()>300));
  var result=WorldSkyVisibility.buildBounded(input,limits(256,1,8192),()->false);assertArrayEquals(WorldSkyVisibility.build(input).data,result.data);assertEquals(1,result.sample(-31.5f,180,-15.5f));
 }
 @Test public void emptyMutableOversizedInputsAndParameters() {
  var models=new ModelLibrary();var empty=chunk(new SparseVoxelOctree(256),models);
  assertThrows(IllegalStateException.class,()->WorldSkyVisibility.buildBounded(empty,limits(0,0,0),()->false));
  assertEquals(256,WorldSkyVisibility.buildBounded(empty,limits(256,0,0),()->false).columns);
  var mutable=new WorldSkyVisibility.Input(List.of(),List.of(new WorldSkyVisibility.EditedCell(0,100,0,new SparseVoxelOctree(16))),models);
  assertThrows(IllegalArgumentException.class,()->WorldSkyVisibility.buildBounded(mutable,limits(256,1,1),()->false));
  var many=new WorldSkyVisibility.Input(Collections.nCopies(4106,empty.chunks().get(0)),List.of(),models);
  assertThrows(IllegalArgumentException.class,()->WorldSkyVisibility.buildBounded(many,limits(256,1,1),()->false));
  assertThrows(IllegalArgumentException.class,()->limits(257,1,1));assertThrows(IllegalArgumentException.class,()->limits(1,131073,1));assertThrows(IllegalArgumentException.class,()->limits(1,1,131073));
  assertThrows(CancellationException.class,()->WorldSkyVisibility.buildBounded(empty,limits(256,0,0),()->true));
 }
}
