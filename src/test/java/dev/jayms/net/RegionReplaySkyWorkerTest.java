package dev.jayms.net;
import dev.jayms.*;
import dev.jayms.render.WorldSkyVisibility;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class RegionReplaySkyWorkerTest {
 @TempDir Path folder;
 static final WorldSkyVisibility.BuildLimits LIMITS=new WorldSkyVisibility.BuildLimits(256,131072,131072);
 ReadbackMetadataTest fixture(String name)throws Exception{var f=new ReadbackMetadataTest();f.folder=Files.createDirectories(folder.resolve(name));return f;}
 RegionReplayWorker.Request sky(RegionReplayWorker.Binding binding,World world)throws Exception{var r=binding.submitSky(0,0,LIMITS,world);RegionReplayWorkerTest.done(r);return r;}
 @Test void sourceBoundSkyPreservesGeometryWithoutWorldOrSavePromotion()throws Exception {
  var f=fixture("source");var path=f.fixture();byte[] before=Files.readAllBytes(path);var worker=f.worker(path);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion);var foreign=new World(local.seed,local.models,local.generatorVersion)) {
    var binding=worker.bindTarget(local,world);var r=sky(binding,world);assertEquals(RegionReplayWorker.Phase.READY,r.outcome().phase());assertEquals(32768,r.completedCells());assertTrue(r.checkedRows()>0);
    var tile=binding.skyResult(r,world);assertEquals(binding.fingerprint(),tile.fingerprint());assertEquals(new ChunkPos(0,0,0),tile.column());assertEquals(256,tile.data().columns);assertEquals(0,tile.data().sample(.5f,150,.5f));assertEquals(1,tile.data().sample(.5f,170,.5f));
    assertThrows(IOException.class,()->r.result(ReadbackMetadataTest.REV));assertThrows(IOException.class,()->binding.result(r,world));assertThrows(IOException.class,()->binding.chunkResult(r,world));assertThrows(IOException.class,()->binding.skyResult(r,foreign));
    assertEquals(0,world.editsVersion());assertTrue(world.getLoadedChunks().isEmpty());assertFalse(local.replayComplete());assertThrows(IOException.class,local::save);assertArrayEquals(before,Files.readAllBytes(path));
    RegionReplayWorkerTest.closed(worker);assertThrows(IOException.class,()->binding.skyResult(r,world));assertEquals(0,tile.data().sample(.5f,150,.5f));
   }
  }finally{RegionReplayWorkerTest.closed(worker);f.noSpools();}
 }
 @Test void skySharesQueueCancellationAndPageRetry()throws Exception {
  var f=fixture("queue");var path=f.fixture();var spool=f.spool();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var once=new AtomicBoolean(true);var owner=Thread.currentThread();
  var worker=new RegionReplayWorker(spool,(cancel,progress)->{
   var s=LocalGame.openRegionalReadback(path,spool,ReadbackMetadataTest.REV,cancel,progress);
   return new RegionReplayWorker.Source(s.spool(),s.terrain(),e->{assertNotSame(owner,Thread.currentThread());if(once.getAndSet(false)){entered.countDown();try{RegionReplayWorkerTest.release(release);}catch(IOException failure){throw new UncheckedIOException(failure);}}return s.allowed().test(e);},s.revision(),s.fingerprint(),s.metadata());
  },1,1,56,2);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion)) {
    var b=worker.bindTarget(local,world);var first=b.submitSky(0,0,LIMITS,world);assertTrue(entered.await(10,TimeUnit.SECONDS));
    var queued=b.submitSky(0,0,LIMITS,world);assertThrows(IOException.class,()->b.submitSky(0,0,LIMITS,world));assertTrue(queued.cancel());
    var page=b.submit(new ChunkPos(0,10,0),new RegionReplayPage.CellRange(0,2),2,world);assertTrue(first.cancel());release.countDown();RegionReplayWorkerTest.done(page);assertEquals(2,b.result(page,world).history().size());
    assertThrows(CancellationException.class,()->b.skyResult(first,world));assertThrows(CancellationException.class,()->b.skyResult(queued,world));assertThrows(IOException.class,()->b.skyResult(page,world));assertNotNull(b.skyResult(sky(b,world),world));
   }
  }finally{release.countDown();RegionReplayWorkerTest.closed(worker);f.noSpools();}
 }
 @Test void budgetFailureWrongOwnerAndTargetChangesReject()throws Exception {
  var f=fixture("fences");var path=f.fixture();var worker=f.worker(path);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion)) {
    var b=worker.bindTarget(local,world);var failed=b.submitSky(0,0,new WorldSkyVisibility.BuildLimits(256,0,0),world);RegionReplayWorkerTest.done(failed);assertEquals(RegionReplayWorker.Phase.FAILED,failed.outcome().phase());assertThrows(IOException.class,()->b.skyResult(failed,world));
    var r=sky(b,world);assertNotNull(b.skyResult(r,world));var error=new AtomicReference<Throwable>();var t=new Thread(()->{try{b.skyResult(r,world);}catch(Throwable e){error.set(e);}});t.start();t.join(5000);assertFalse(t.isAlive());assertInstanceOf(IOException.class,error.get());
    world.apply(new Protocol.Edit(8,160,0,Blocks.GLASS));assertThrows(IOException.class,()->b.skyResult(r,world));assertThrows(IOException.class,()->b.submitSky(0,0,LIMITS,world));
   }
  }finally{RegionReplayWorkerTest.closed(worker);f.noSpools();}
 }
 @Test void identicalFingerprintForeignBindingAndChangedModelsReject()throws Exception {
  var f=fixture("foreign");var path=f.fixture();var a=f.worker(path);var b=f.worker(path);
  try {
   ReadbackMetadataTest.ready(a);ReadbackMetadataTest.ready(b);var al=a.metadataResult(ReadbackMetadataTest.REV);var bl=b.metadataResult(ReadbackMetadataTest.REV);
   try(var aw=new World(al.seed,al.models,al.generatorVersion);var bw=new World(bl.seed,bl.models,bl.generatorVersion)) {
    var ab=a.bindTarget(al,aw);var bb=b.bindTarget(bl,bw);var ar=sky(ab,aw);var br=sky(bb,bw);assertEquals(ab.fingerprint(),bb.fingerprint());assertThrows(IOException.class,()->ab.skyResult(br,aw));assertThrows(IOException.class,()->bb.skyResult(ar,bw));assertNotNull(ab.skyResult(ar,aw));
    al.models=al.models.copy();assertThrows(IOException.class,()->ab.skyResult(ar,aw));assertNotNull(bb.skyResult(br,bw));
   }
  }finally{RegionReplayWorkerTest.closed(a);RegionReplayWorkerTest.closed(b);f.noSpools();}
 }
}
