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

public class RegionReplaySkyPumpTest {
 @TempDir Path folder;
 ReadbackMetadataTest fixture(String name)throws Exception{var f=new ReadbackMetadataTest();f.folder=Files.createDirectories(folder.resolve(name));return f;}
 static void ready(RegionReplayWorker.Binding.SkyPump p,int count)throws Exception{RegionReplayWorkerTest.await(()->{try{p.tick();assertTrue(p.status().pending()<=1);return p.status().ready()==count;}catch(IOException e){throw new UncheckedIOException(e);}});}
 @Test void boundedCacheEvictionInvalidDesiredAndClose()throws Exception {
  var f=fixture("cache");var path=f.fixture();var worker=f.worker(path);var a=new ChunkPos(0,0,0);var b=new ChunkPos(1,0,0);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion)) {
    var binding=worker.bindTarget(local,world);var p=binding.openSkyPump(world,2,8000000,RegionReplaySkyWorkerTest.LIMITS);assertThrows(IOException.class,()->binding.openSkyPump(world,2,8000000,RegionReplaySkyWorkerTest.LIMITS));
    p.desired(List.of(a,b));assertNull(p.result(a));ready(p,2);int na=p.result(a).data().data.length,nb=p.result(b).data().data.length;assertEquals(na+nb,p.status().cachedInts());
    assertThrows(IllegalArgumentException.class,()->p.desired(List.of(a,b,new ChunkPos(2,0,0))));assertThrows(IllegalArgumentException.class,()->p.desired(List.of(new ChunkPos(0,1,0))));assertEquals(2,p.status().ready());
    p.desired(List.of(b));assertNull(p.result(a));assertNotNull(p.result(b));assertEquals(nb,p.status().cachedInts());p.desired(List.of());assertEquals(0,p.status().cachedInts());
    p.desired(List.of(a));p.tick();p.close();assertTrue(p.status().closed());assertEquals(0,p.status().pending());assertEquals(0,p.status().cachedInts());assertThrows(IOException.class,()->p.result(a));assertEquals(0,world.editsVersion());assertTrue(world.getLoadedChunks().isEmpty());assertFalse(local.replayComplete());assertThrows(IOException.class,local::save);
   }
  }finally{RegionReplayWorkerTest.closed(worker);f.noSpools();}
 }
 @Test void cacheBudgetFailurePersistsUntilExplicitRetryAndEviction()throws Exception {
  var f=fixture("budget");var path=f.fixture();var worker=f.worker(path);var a=new ChunkPos(0,0,0);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion)) {
    var p=worker.bindTarget(local,world).openSkyPump(world,1,0,RegionReplaySkyWorkerTest.LIMITS);p.desired(List.of(a));
    RegionReplayWorkerTest.await(()->{try{p.tick();return p.status().failed()==1;}catch(IOException e){throw new UncheckedIOException(e);}});
    var failure=p.failure(a);assertNotNull(failure);p.tick();assertSame(failure,p.failure(a));assertEquals(0,p.status().pending());assertEquals(0,p.status().cachedInts());assertNull(p.result(a));
    p.retry(a);p.tick();assertEquals(1,p.status().pending());p.desired(List.of());assertEquals(0,p.status().pending());assertEquals(0,p.status().failed());p.close();
   }
  }finally{RegionReplayWorkerTest.closed(worker);f.noSpools();}
 }
 @Test void sharedQueueBackpressureDoesNotBlockAndRetriesAfterPageCompletion()throws Exception {
  var f=fixture("queue");var path=f.fixture();var spool=f.spool();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var once=new AtomicBoolean(true);
  var worker=new RegionReplayWorker(spool,(cancel,progress)->{var s=LocalGame.openRegionalReadback(path,spool,ReadbackMetadataTest.REV,cancel,progress);return new RegionReplayWorker.Source(s.spool(),s.terrain(),e->{if(once.getAndSet(false)){entered.countDown();try{RegionReplayWorkerTest.release(release);}catch(IOException x){throw new UncheckedIOException(x);}}return s.allowed().test(e);},s.revision(),s.fingerprint(),s.metadata());},1,1,56,2);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion)) {
    var b=worker.bindTarget(local,world);var p=b.openSkyPump(world,1,8000000,RegionReplaySkyWorkerTest.LIMITS);var region=new ChunkPos(0,10,0);var range=new RegionReplayPage.CellRange(0,2);
    var first=b.submit(region,range,2,world);assertTrue(entered.await(10,TimeUnit.SECONDS));var second=b.submit(region,range,2,world);p.desired(List.of(new ChunkPos(0,0,0)));p.tick();assertTrue(p.status().backpressure());assertEquals(0,p.status().pending());assertEquals(1,release.getCount());
    release.countDown();RegionReplayWorkerTest.done(first);RegionReplayWorkerTest.done(second);assertEquals(2,b.result(second,world).history().size());ready(p,1);assertFalse(p.status().backpressure());
   }
  }finally{release.countDown();RegionReplayWorkerTest.closed(worker);f.noSpools();}
 }
 @Test void wrongOwnerAndChangedModelsDiscardCacheAndWorkerCloseClosesPump()throws Exception {
  var f=fixture("fence");var path=f.fixture();var worker=f.worker(path);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion)) {
    var p=worker.bindTarget(local,world).openSkyPump(world,1,8000000,RegionReplaySkyWorkerTest.LIMITS);var a=new ChunkPos(0,0,0);p.desired(List.of(a));ready(p,1);
    var error=new AtomicReference<Throwable>();var t=new Thread(()->{try{p.close();}catch(Throwable e){error.set(e);}});t.start();t.join(5000);assertFalse(t.isAlive());assertInstanceOf(IOException.class,error.get());assertNotNull(p.result(a));
    local.models=local.models.copy();assertThrows(IOException.class,()->p.result(a));assertThrows(IOException.class,p::tick);RegionReplayWorkerTest.closed(worker);assertTrue(p.status().closed());assertEquals(0,p.status().cachedInts());
   }
  }finally{RegionReplayWorkerTest.closed(worker);f.noSpools();}
 }
}
