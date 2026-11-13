package dev.jayms.net;
import dev.jayms.*;
import dev.jayms.render.WorldSkyVisibility;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class RegionReplaySkyMergeWorkerTest {
 @TempDir Path folder;
 ReadbackMetadataTest fixture(String name)throws Exception{var f=new ReadbackMetadataTest();f.folder=Files.createDirectories(folder.resolve(name));return f;}
 RegionReplayWorker.Request tile(RegionReplayWorker.Binding b,World w,int x)throws Exception{var r=b.submitSky(x,0,RegionReplaySkyWorkerTest.LIMITS,w);RegionReplayWorkerTest.done(r);return r;}
 RegionReplayWorker.Request merged(RegionReplayWorker.Binding b,World w,List<RegionReplayWorker.Request> tiles,int cap)throws Exception{var r=b.submitSkyMerge(tiles,cap,w);RegionReplayWorkerTest.done(r);return r;}
 @Test void mergeRunsOnWorkerAndRetainsExactSourceAndUnknownGaps()throws Exception {
  var f=fixture("source");var path=f.fixture();byte[] before=Files.readAllBytes(path);var worker=f.worker(path);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion);var foreign=new World(local.seed,local.models,local.generatorVersion)) {
    var b=worker.bindTarget(local,world);var a=tile(b,world,0);var c=tile(b,world,2);var r=merged(b,world,List.of(c,a),8000000);var result=b.mergeResult(r,world);assertEquals(RegionReplayWorker.Phase.READY,r.outcome().phase());
    assertEquals(List.of(new ChunkPos(2,0,0),new ChunkPos(0,0,0)),result.columns());assertThrows(UnsupportedOperationException.class,()->result.columns().clear());assertEquals(512,result.data().columns);assertEquals(b.fingerprint(),result.fingerprint());assertEquals(64,result.payloadDigest().length());
    assertArrayEquals(WorldSkyVisibility.mergeBounded(List.of(b.skyResult(c,world).data(),b.skyResult(a,world).data()),8000000,()->false).data,result.data().data);
    assertEquals(0,result.data().sampleKnown(.5f,150,.5f,-1));assertEquals(-1,result.data().sampleKnown(20.5f,150,.5f,-1));assertEquals(1,result.data().sampleKnown(32.5f,150,.5f,-1));
    assertThrows(IOException.class,()->b.result(r,world));assertThrows(IOException.class,()->b.chunkResult(r,world));assertThrows(IOException.class,()->b.skyResult(r,world));assertThrows(IOException.class,()->b.mergeResult(a,world));assertThrows(IOException.class,()->b.mergeResult(r,foreign));
    assertEquals(0,world.editsVersion());assertTrue(world.getLoadedChunks().isEmpty());assertFalse(local.replayComplete());assertThrows(IOException.class,local::save);assertArrayEquals(before,Files.readAllBytes(path));
    RegionReplayWorkerTest.closed(worker);assertThrows(IOException.class,()->b.mergeResult(r,world));assertEquals(512,result.data().columns);
   }
  }finally{RegionReplayWorkerTest.closed(worker);f.noSpools();}
 }
 @Test void budgetTamperOverlapAndEmptyRequestsFailWithoutPoisoningWorker()throws Exception {
  var f=fixture("bounds");var path=f.fixture();var worker=f.worker(path);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion)) {
    var b=worker.bindTarget(local,world);var a=tile(b,world,0);
    assertThrows(IllegalArgumentException.class,()->b.submitSkyMerge(List.of(a,a),8000000,world));assertThrows(IllegalArgumentException.class,()->b.submitSkyMerge(Collections.nCopies(37,a),8000000,world));
    var failed=merged(b,world,List.of(a),0);assertEquals(RegionReplayWorker.Phase.FAILED,failed.outcome().phase());assertThrows(IOException.class,()->b.mergeResult(failed,world));assertNotNull(b.mergeResult(merged(b,world,List.of(a),8000000),world));
    var data=b.skyResult(a,world).data();int[] before=data.data.clone();int offset=data.data[2];data.data[offset+6]=Float.floatToRawIntBits(.5f); // Still valid sky syntax, different source geometry.
    var tampered=merged(b,world,List.of(a),8000000);assertEquals(RegionReplayWorker.Phase.FAILED,tampered.outcome().phase());assertThrows(IOException.class,()->b.mergeResult(tampered,world));System.arraycopy(before,0,data.data,0,before.length);assertNotNull(b.mergeResult(merged(b,world,List.of(a),8000000),world));
    assertEquals(0,b.mergeResult(merged(b,world,List.of(),1),world).data().columns);
   }
  }finally{RegionReplayWorkerTest.closed(worker);f.noSpools();}
 }
 @Test void queuedMergeCancellationSharesPageBackpressureAndRetry()throws Exception {
  var f=fixture("queue");var path=f.fixture();var spool=f.spool();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var block=new AtomicBoolean();
  var worker=new RegionReplayWorker(spool,(cancel,progress)->{var s=LocalGame.openRegionalReadback(path,spool,ReadbackMetadataTest.REV,cancel,progress);return new RegionReplayWorker.Source(s.spool(),s.terrain(),e->{if(block.getAndSet(false)){entered.countDown();try{RegionReplayWorkerTest.release(release);}catch(IOException x){throw new UncheckedIOException(x);}}return s.allowed().test(e);},s.revision(),s.fingerprint(),s.metadata());},1,1,56,2);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion)) {
    var b=worker.bindTarget(local,world);var a=tile(b,world,0);block.set(true);var page=b.submit(new ChunkPos(0,10,0),new RegionReplayPage.CellRange(0,2),2,world);assertTrue(entered.await(10,TimeUnit.SECONDS));
    var cancelled=b.submitSkyMerge(List.of(a),8000000,world);assertThrows(IOException.class,()->b.submitSkyMerge(List.of(a),8000000,world));assertTrue(cancelled.cancel());var retry=b.submitSkyMerge(List.of(a),8000000,world);release.countDown();RegionReplayWorkerTest.done(page);RegionReplayWorkerTest.done(retry);assertThrows(CancellationException.class,()->b.mergeResult(cancelled,world));assertNotNull(b.mergeResult(retry,world));
   }
  }finally{release.countDown();RegionReplayWorkerTest.closed(worker);f.noSpools();}
 }
 @Test void foreignBindingsOwnerAndChangedTargetRejectMerge()throws Exception {
  var f=fixture("fence");var path=f.fixture();var a=f.worker(path);var b=f.worker(path);
  try {
   ReadbackMetadataTest.ready(a);ReadbackMetadataTest.ready(b);var al=a.metadataResult(ReadbackMetadataTest.REV);var bl=b.metadataResult(ReadbackMetadataTest.REV);
   try(var aw=new World(al.seed,al.models,al.generatorVersion);var bw=new World(bl.seed,bl.models,bl.generatorVersion)) {
    var ab=a.bindTarget(al,aw);var bb=b.bindTarget(bl,bw);var ar=tile(ab,aw,0);var br=tile(bb,bw,0);assertThrows(IOException.class,()->ab.submitSkyMerge(List.of(br),8000000,aw));var r=merged(ab,aw,List.of(ar),8000000);
    var error=new AtomicReference<Throwable>();var t=new Thread(()->{try{ab.mergeResult(r,aw);}catch(Throwable e){error.set(e);}});t.start();t.join(5000);assertFalse(t.isAlive());assertInstanceOf(IOException.class,error.get());
    aw.apply(new Protocol.Edit(8,160,0,Blocks.GLASS));assertThrows(IOException.class,()->ab.mergeResult(r,aw));assertThrows(IOException.class,()->ab.submitSkyMerge(List.of(ar),8000000,aw));assertNotNull(bb.skyResult(br,bw));
   }
  }finally{RegionReplayWorkerTest.closed(a);RegionReplayWorkerTest.closed(b);f.noSpools();}
 }
}
