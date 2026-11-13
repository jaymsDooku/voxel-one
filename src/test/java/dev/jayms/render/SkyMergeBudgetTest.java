package dev.jayms.render;
import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SkyMergeBudgetTest {
 static WorldSkyVisibility tile(int cx,int type){var root=new SparseVoxelOctree(256);root.fill(0,0,0,256,256,256,WorldVoxels.encode(type));return WorldSkyVisibility.build(new WorldSkyVisibility.Input(List.of(new WorldSkyVisibility.Geometry(new ChunkPos(cx,10,-1),root.freeze())),List.of(),new ModelLibrary()));}
 @Test public void disjointMergeMatchesResidentRebuildsDirectoryAndDetaches() {
  var a=tile(-2,Blocks.STONE);var b=tile(0,Blocks.GLASS);var expected=WorldSkyVisibility.merge(List.of(b,a));var actual=WorldSkyVisibility.mergeBounded(List.of(b,a),WorldSkyVisibility.MAX_INTS,()->false);
  assertArrayEquals(expected.data,actual.data);assertEquals(512,actual.columns);assertEquals(0,actual.sampleKnown(-31.5f,150,-15.5f,-1));assertEquals(.7f,actual.sampleKnown(.5f,150,-15.5f,-1));assertEquals(-1,actual.sampleKnown(-.5f,150,-15.5f,-1));
  Arrays.fill(a.data,0);Arrays.fill(b.data,0);assertArrayEquals(expected.data,actual.data);assertEquals(0,actual.sampleKnown(-31.5f,150,-15.5f,-1));
 }
 @Test public void budgetsOverlapCancellationAndRetry() {
  var a=tile(-2,Blocks.STONE);var b=tile(0,Blocks.GLASS);var parts=List.of(a,b);int total=WorldSkyVisibility.merge(parts).data.length;
  assertThrows(IllegalStateException.class,()->WorldSkyVisibility.mergeBounded(parts,total-1,()->false));assertEquals(total,WorldSkyVisibility.mergeBounded(parts,total,()->false).data.length);
  assertThrows(IllegalArgumentException.class,()->WorldSkyVisibility.mergeBounded(List.of(a,a),WorldSkyVisibility.MAX_INTS,()->false));assertThrows(IllegalArgumentException.class,()->WorldSkyVisibility.mergeBounded(Collections.nCopies(37,a),WorldSkyVisibility.MAX_INTS,()->false));
  assertThrows(CancellationException.class,()->WorldSkyVisibility.mergeBounded(parts,total,()->true));var calls=new AtomicInteger();assertThrows(CancellationException.class,()->WorldSkyVisibility.mergeBounded(parts,total,()->calls.incrementAndGet()>300));assertArrayEquals(WorldSkyVisibility.merge(parts).data,WorldSkyVisibility.mergeBounded(parts,total,()->false).data);
  assertThrows(IllegalArgumentException.class,()->WorldSkyVisibility.mergeBounded(parts,-1,()->false));assertThrows(IllegalArgumentException.class,()->WorldSkyVisibility.mergeBounded(parts,WorldSkyVisibility.MAX_INTS+1,()->false));
 }
 @Test public void malformedHeadersSegmentsAndIgnoredDirectory() {
  for(int change=0;change<7;change++) {
   var a=tile(-2,Blocks.STONE);int offset=a.data[2];
   switch(change){case 0->a.data[3]=129;case 1->a.data[2]++;case 2->{a.data[4]=a.data[0];a.data[5]=a.data[1];}case 3->a.data[offset]=Float.floatToRawIntBits(Float.NaN);case 4->a.data[offset+6]=Float.floatToRawIntBits(2);case 5->a.data[offset+4]=a.data[offset+1];case 6->a.data[3]=-2;}
   assertThrows(IllegalArgumentException.class,()->WorldSkyVisibility.mergeBounded(List.of(a),WorldSkyVisibility.MAX_INTS,()->false));
  }
  var a=tile(-2,Blocks.STONE);var expected=WorldSkyVisibility.merge(List.of(a));assertTrue(a.indexSpan>0);Arrays.fill(a.data,a.indexOffset,a.data.length,-123);assertArrayEquals(expected.data,WorldSkyVisibility.mergeBounded(List.of(a),WorldSkyVisibility.MAX_INTS,()->false).data);
 }
 @Test public void emptyAndConservativeOverflowRemainCorrect() {
  assertEquals(0,WorldSkyVisibility.mergeBounded(List.of(),1,()->false).columns);assertThrows(IllegalStateException.class,()->WorldSkyVisibility.mergeBounded(List.of(),0,()->false));
  var edits=new ArrayList<WorldSkyVisibility.EditedCell>();for(int i=0;i<129;i++){var cell=new SparseVoxelOctree(16);cell.fill(0,0,0,8,16,8,WorldVoxels.encode(Blocks.STONE));edits.add(new WorldSkyVisibility.EditedCell(-1,160+i,-1,cell.freeze()));}
  var a=WorldSkyVisibility.build(new WorldSkyVisibility.Input(List.of(),edits,new ModelLibrary()));assertEquals(-1,a.data[3]);var merged=WorldSkyVisibility.mergeBounded(List.of(a),100,()->false);assertEquals(0,merged.sample(-.75f,150,-.75f));assertEquals(1,merged.sample(-.75f,300,-.75f));assertArrayEquals(WorldSkyVisibility.merge(List.of(a)).data,merged.data);
 }
}
