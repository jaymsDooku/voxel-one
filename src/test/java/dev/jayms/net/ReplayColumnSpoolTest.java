package dev.jayms.net;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class ReplayColumnSpoolTest {
 @TempDir Path folder;
 static List<Protocol.Edit> rows(){return List.of(new Protocol.Edit(-17,160,-1,Blocks.STONE),new Protocol.Edit(0,0,0,Blocks.GLASS),new Protocol.Edit(-17,320,-1,Blocks.GLASS),new Protocol.Edit(-18,160,-1,0),new Protocol.Edit(-17,-16,-1,Blocks.BRICKS),new Protocol.Edit(-1,160,-1,Blocks.LED),new Protocol.Edit(-17,320,-1,0));}
 static List<Protocol.Edit> selected(){var r=rows();return List.of(r.get(0),r.get(2),r.get(3),r.get(4),r.get(6));}
 RegionReplaySpool spool()throws Exception{return RegionReplaySpool.stage(folder,new DataInputStream(new ByteArrayInputStream(RegionReplayLoaderTest.encodeRows(rows()))),e->true,()->false);}
 void empty()throws Exception{try(var files=Files.list(folder)){assertEquals(0,files.count());}}
 @Test void verticalRecurringSectionsPreserveRowsAndImmutableDelivery()throws Exception {
  try(var spool=spool()) {
   var actual=new ArrayList<Protocol.Edit>();var heights=new ArrayList<Integer>();
   spool.visitColumn(-2,-1,5,e->true,()->false,(region,section)->{assertEquals(-2,region.chunkX());assertEquals(-1,region.chunkZ());heights.add(region.chunkY());assertThrows(UnsupportedOperationException.class,section::clear);actual.addAll(section);});
   assertEquals(selected(),actual);assertEquals(List.of(10,20,10,-1,20),heights);
   var geometry=new WorldVoxels(new Terrain(42));actual.forEach(geometry::apply);assertEquals(0,geometry.cell(-17,320,-1).occupied());assertEquals(WorldVoxels.encode(Blocks.STONE),geometry.cell(-17,160,-1).uniform(0,0,0,16));
   spool.visitColumn(999,999,0,e->{fail("Empty selection must not decode rows");return false;},()->false,(r,s)->fail("Empty selection must not deliver"));
  }empty();
 }
 @Test void rowBudgetRejectsBeforeAnySelectedDecodeOrDelivery()throws Exception {
  try(var spool=spool()) {
   var decoded=new AtomicInteger();var delivered=new AtomicInteger();
   assertThrows(IOException.class,()->spool.visitColumn(-2,-1,4,e->{decoded.incrementAndGet();return true;},()->false,(r,s)->delivered.incrementAndGet()));assertEquals(0,decoded.get());assertEquals(0,delivered.get());
   assertThrows(IOException.class,()->spool.visitColumn(-2,-1,0,e->true,()->false,(r,s)->fail()));
   assertThrows(IllegalArgumentException.class,()->spool.visitColumn(-2,-1,-1,e->true,()->false,(r,s)->fail()));assertThrows(IllegalArgumentException.class,()->spool.visitColumn(-2,-1,LocalGame.MAX_SAVED_EDITS+1,e->true,()->false,(r,s)->fail()));
   spool.visitColumn(-2,-1,5,e->true,()->false,(r,s)->delivered.addAndGet(s.size()));assertEquals(5,delivered.get());
  }empty();
 }
 @Test void cancellationSinkFailureAndForeignOwnerLeaveSpoolRetryable()throws Exception {
  try(var spool=spool()) {
   assertThrows(CancellationException.class,()->spool.visitColumn(-2,-1,5,e->true,()->true,(r,s)->fail()));var denied=new IOException("Synthetic column sink denied");
   assertSame(denied,assertThrows(IOException.class,()->spool.visitColumn(-2,-1,5,e->true,()->false,(r,s)->{throw denied;})));
   var pool=Executors.newSingleThreadExecutor();try{var failed=assertThrows(ExecutionException.class,()->pool.submit(()->{spool.visitColumn(-2,-1,5,e->true,()->false,(r,s)->{});return null;}).get(5,TimeUnit.SECONDS));assertInstanceOf(IOException.class,failed.getCause());}finally{pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
   var actual=new ArrayList<Protocol.Edit>();spool.visitColumn(-2,-1,5,e->true,()->false,(r,s)->actual.addAll(s));assertEquals(selected(),actual);
  }empty();
 }
 @Test void selectedPayloadCorruptionRejectsAndRestoredBytesCanRetry()throws Exception {
  byte[] original=RegionReplayLoaderTest.encodeRows(rows());try(var spool=spool()) {
   Path path;try(var files=Files.list(folder)){path=files.filter(p->p.getFileName().toString().startsWith(".replay-spool-")).findFirst().orElseThrow();}
   var changed=original.clone();changed[32]^=1;Files.write(path,changed);var delivered=new AtomicInteger();assertThrows(IOException.class,()->spool.visitColumn(-2,-1,5,e->true,()->false,(r,s)->delivered.incrementAndGet()));assertEquals(0,delivered.get());
   Files.write(path,original);var actual=new ArrayList<Protocol.Edit>();spool.visitColumn(-2,-1,5,e->true,()->false,(r,s)->actual.addAll(s));assertEquals(selected(),actual);
  }empty();
 }
}
