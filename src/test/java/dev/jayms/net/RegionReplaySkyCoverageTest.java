package dev.jayms.net;
import dev.jayms.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class RegionReplaySkyCoverageTest {
 @TempDir Path folder;
 ReadbackMetadataTest fixture(String name)throws Exception{var f=new ReadbackMetadataTest();f.folder=Files.createDirectories(folder.resolve(name));return f;}
 static void merged(RegionReplayWorker.Binding.SkyPump p)throws Exception {RegionReplayWorkerTest.await(()->{try{p.tick();assertTrue(p.status().pending()+p.status().mergePending()<=1);return p.coverage()!=null;}catch(IOException e){throw new UncheckedIOException(e);}});}
 @Test void automaticMergeTracksDesiredGenerationAndUnknownCoverage()throws Exception {
  var f=fixture("generation");var path=f.fixture();var worker=f.worker(path);var a=new ChunkPos(0,0,0);var b=new ChunkPos(2,0,0);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion)) {
    var p=worker.bindTarget(local,world).openSkyPump(world,2,8000000,RegionReplaySkyWorkerTest.LIMITS);p.requestMerge();p.tick();assertNull(p.coverage());assertEquals(0,p.status().mergePending());
    p.desired(List.of(a,b));long generation=p.status().generation();assertNull(p.coverage());merged(p);var first=p.coverage();assertEquals(generation,first.generation());assertEquals(List.of(a,b),first.merge().columns());assertTrue(p.current(first));assertFalse(p.current(new RegionReplayWorker.Binding.SkyCoverage(first.generation(),first.merge())));
    assertEquals(-1,first.merge().data().sampleKnown(20.5f,150,.5f,-1));p.desired(List.of(b,a));assertSame(first,p.coverage());assertEquals(generation,p.status().generation());
    assertThrows(IllegalArgumentException.class,()->p.desired(List.of(new ChunkPos(0,1,0))));assertSame(first,p.coverage());
    p.desired(List.of(b));assertEquals(generation+1,p.status().generation());assertFalse(p.current(first));assertNull(p.coverage());merged(p);assertEquals(List.of(b),p.coverage().merge().columns());assertTrue(p.status().mergedInts()<=8000000);
    p.desired(List.of());p.tick();assertNull(p.coverage());assertEquals(0,p.status().mergePending());assertEquals(0,p.status().mergedInts());assertEquals(0,world.editsVersion());assertFalse(local.replayComplete());assertThrows(IOException.class,local::save);
   }
  }finally{RegionReplayWorkerTest.closed(worker);f.noSpools();}
 }
 @Test void queuedOldGenerationIsCancelledAndNewMergeWaitsForSharedQueue()throws Exception {
  var f=fixture("queue");var path=f.fixture();var spool=f.spool();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var block=new AtomicBoolean();
  var worker=new RegionReplayWorker(spool,(cancel,progress)->{var s=LocalGame.openRegionalReadback(path,spool,ReadbackMetadataTest.REV,cancel,progress);return new RegionReplayWorker.Source(s.spool(),s.terrain(),e->{if(block.getAndSet(false)){entered.countDown();try{RegionReplayWorkerTest.release(release);}catch(IOException x){throw new UncheckedIOException(x);}}return s.allowed().test(e);},s.revision(),s.fingerprint(),s.metadata());},1,1,56,2);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion)) {
    var binding=worker.bindTarget(local,world);var p=binding.openSkyPump(world,2,8000000,RegionReplaySkyWorkerTest.LIMITS);var a=new ChunkPos(0,0,0);var b=new ChunkPos(2,0,0);p.desired(List.of(a,b));RegionReplaySkyPumpTest.ready(p,2);
    block.set(true);var page=binding.submit(new ChunkPos(0,10,0),new RegionReplayPage.CellRange(0,2),2,world);assertTrue(entered.await(10,TimeUnit.SECONDS));var queued=binding.submit(new ChunkPos(0,10,0),new RegionReplayPage.CellRange(0,2),2,world);
    p.requestMerge();assertTrue(p.status().backpressure());assertEquals(0,p.status().mergePending());assertNull(p.coverage());assertTrue(queued.cancel());p.tick();assertEquals(1,p.status().mergePending());long old=p.status().generation();
    p.desired(List.of(b));assertEquals(old+1,p.status().generation());assertNull(p.coverage());assertEquals(0,p.status().mergePending());p.tick();assertEquals(1,p.status().mergePending());assertEquals(1,release.getCount());release.countDown();RegionReplayWorkerTest.done(page);merged(p);assertEquals(old+1,p.coverage().generation());assertEquals(List.of(b),p.coverage().merge().columns());
   }
  }finally{release.countDown();RegionReplayWorkerTest.closed(worker);f.noSpools();}
 }
 @Test void mergeFailureRequiresRetryAndDesiredChangeCanRecover()throws Exception {
  var f=fixture("budget");var path=f.fixture();var worker=f.worker(path);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion)) {
    var b=worker.bindTarget(local,world);var a=b.submitSky(0,0,RegionReplaySkyWorkerTest.LIMITS,world);var c=b.submitSky(2,0,RegionReplaySkyWorkerTest.LIMITS,world);RegionReplayWorkerTest.done(a);RegionReplayWorkerTest.done(c);int cap=b.skyResult(a,world).data().data.length+b.skyResult(c,world).data().data.length;
    var p=b.openSkyPump(world,2,cap,RegionReplaySkyWorkerTest.LIMITS);p.desired(List.of(new ChunkPos(0,0,0),new ChunkPos(2,0,0)));p.requestMerge();
    RegionReplayWorkerTest.await(()->{try{p.tick();return p.mergeFailure()!=null;}catch(IOException e){throw new UncheckedIOException(e);}});var failure=p.mergeFailure();p.tick();assertSame(failure,p.mergeFailure());assertEquals(0,p.status().mergePending());assertNull(p.coverage());
    p.requestMerge();assertEquals(1,p.status().mergePending());p.desired(List.of(new ChunkPos(0,0,0)));merged(p);assertNull(p.mergeFailure());assertEquals(1,p.coverage().merge().columns().size());
   }
  }finally{RegionReplayWorkerTest.closed(worker);f.noSpools();}
 }
 @Test void ownerSourceAndCloseInvalidateHeldCoverage()throws Exception {
  var f=fixture("fence");var path=f.fixture();var worker=f.worker(path);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion)) {
    var p=worker.bindTarget(local,world).openSkyPump(world,1,8000000,RegionReplaySkyWorkerTest.LIMITS);p.desired(List.of(new ChunkPos(0,0,0)));p.requestMerge();merged(p);var held=p.coverage();
    var error=new AtomicReference<Throwable>();var t=new Thread(()->{try{p.current(held);}catch(Throwable e){error.set(e);}});t.start();t.join(5000);assertFalse(t.isAlive());assertInstanceOf(IOException.class,error.get());
    local.models=local.models.copy();assertThrows(IOException.class,()->p.current(held));RegionReplayWorkerTest.closed(worker);assertTrue(p.status().closed());assertEquals(0,p.status().mergePending());assertEquals(0,p.status().mergedInts());assertThrows(IOException.class,p::coverage);
   }
  }finally{RegionReplayWorkerTest.closed(worker);f.noSpools();}
 }
}
