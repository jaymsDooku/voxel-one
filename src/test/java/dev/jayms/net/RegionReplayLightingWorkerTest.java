package dev.jayms.net;

import dev.jayms.*;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class RegionReplayLightingWorkerTest {
 @TempDir Path folder;
 ReadbackMetadataTest fixture(String name)throws Exception{var f=new ReadbackMetadataTest();f.folder=Files.createDirectories(folder.resolve(name));return f;}
 @Test public void completeCpuRootMatchesReplayWithoutRenderOrReadinessPromotion()throws Exception {
  var f=fixture("geometry");var path=f.fixture();var before=Files.readAllBytes(path);var expected=new LocalGame(path,0);var geometry=new WorldVoxels(new Terrain(expected.seed));expected.edits.values().forEach(geometry::apply);var worker=f.worker(path);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion);var other=new World(local.seed,local.models,local.generatorVersion)) {
    var b=worker.bindTarget(local,world);var r=b.submitLightingChunk(RegionReplayBindingTest.REGION,8192,world);RegionReplayWorkerTest.done(r);assertEquals(RegionReplayWorker.Phase.READY,r.outcome().phase());var result=b.lightingChunkResult(r,world);
    assertEquals(256,result.geometry().size());assertTrue(result.geometry().isFrozen());assertEquals(4096,r.completedCells());assertTrue(result.stagedCellLeaves()<=8192);assertNull(result.mesh());assertNull(result.modelDraws());assertNull(result.detached());
    for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)assertTrue(geometry.cell(x,160+y,z).sameValues(result.geometry().region(x*16,y*16,z*16,16)));
    assertThrows(IOException.class,()->b.chunkResult(r,world));assertThrows(IOException.class,()->b.result(r,world));assertThrows(IOException.class,()->r.chunkResult(ReadbackMetadataTest.REV,world));assertThrows(IOException.class,()->b.lightingChunkResult(r,other));
    var rendered=b.submitChunk(RegionReplayBindingTest.REGION,8192,world);RegionReplayWorkerTest.done(rendered);assertThrows(IOException.class,()->b.lightingChunkResult(rendered,world));assertNotNull(b.chunkResult(rendered,world).mesh());
    assertTrue(world.getLoadedChunks().isEmpty());assertEquals(0,world.editsVersion());assertFalse(world.isLoaded(0,160,0));assertFalse(local.replayComplete());assertThrows(IOException.class,local::save);assertArrayEquals(before,Files.readAllBytes(path));
    RegionReplayWorkerTest.closed(worker);assertTrue(result.geometry().isFrozen());assertThrows(IOException.class,()->b.lightingChunkResult(r,world));
   }
  }finally{RegionReplayWorkerTest.closed(worker);f.noSpools();}
 }
 @Test public void budgetsOwnerAndSourceChangesRejectAndRetry()throws Exception {
  var f=fixture("fences");var worker=f.worker(f.fixture());
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion)) {
    var b=worker.bindTarget(local,world);assertThrows(IllegalArgumentException.class,()->b.submitLightingChunk(RegionReplayBindingTest.REGION,-1,world));assertThrows(IllegalArgumentException.class,()->b.submitLightingChunk(RegionReplayBindingTest.REGION,RegionReplayChunk.MAX_CELL_LEAVES+1,world));
    var failed=b.submitLightingChunk(RegionReplayBindingTest.REGION,0,world);RegionReplayWorkerTest.done(failed);assertEquals(RegionReplayWorker.Phase.FAILED,failed.outcome().phase());assertThrows(IOException.class,()->b.lightingChunkResult(failed,world));
    var r=b.submitLightingChunk(RegionReplayBindingTest.REGION,8192,world);RegionReplayWorkerTest.done(r);assertNotNull(b.lightingChunkResult(r,world));var error=new AtomicReference<Throwable>();var t=new Thread(()->{try{b.lightingChunkResult(r,world);}catch(Throwable e){error.set(e);}});t.start();t.join(5000);assertFalse(t.isAlive());assertInstanceOf(IOException.class,error.get());
    local.models=local.models.copy();assertThrows(IOException.class,()->b.lightingChunkResult(r,world));
   }
  }finally{RegionReplayWorkerTest.closed(worker);f.noSpools();}
 }
 @Test public void sharedQueueCancellationLeavesPageAndLightingRetryable()throws Exception {
  var f=fixture("queue");var path=f.fixture();var spool=f.spool();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var once=new AtomicBoolean(true);
  var worker=new RegionReplayWorker(spool,(cancel,progress)->{var s=LocalGame.openRegionalReadback(path,spool,ReadbackMetadataTest.REV,cancel,progress);return new RegionReplayWorker.Source(s.spool(),s.terrain(),e->{if(once.getAndSet(false)){entered.countDown();try{RegionReplayWorkerTest.release(release);}catch(IOException x){throw new UncheckedIOException(x);}}return s.allowed().test(e);},s.revision(),s.fingerprint(),s.metadata());},1,1,56,2);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion)) {
    var b=worker.bindTarget(local,world);var region=RegionReplayBindingTest.REGION;var first=b.submit(region,new RegionReplayPage.CellRange(0,2),2,world);assertTrue(entered.await(10,TimeUnit.SECONDS));var queued=b.submitLightingChunk(region,8192,world);assertThrows(IOException.class,()->b.submitLightingChunk(region,8192,world));assertTrue(queued.cancel());assertThrows(CancellationException.class,()->b.lightingChunkResult(queued,world));var retry=b.submitLightingChunk(region,8192,world);assertEquals(1,release.getCount());release.countDown();RegionReplayWorkerTest.done(first);RegionReplayWorkerTest.done(retry);assertNotNull(b.result(first,world));assertNotNull(b.lightingChunkResult(retry,world));
   }
  }finally{release.countDown();RegionReplayWorkerTest.closed(worker);f.noSpools();}
 }
}
