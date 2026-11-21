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

public class RegionReplayLightingPumpTest {
 @TempDir Path folder;
 ReadbackMetadataTest fixture(String name)throws Exception{var f=new ReadbackMetadataTest();f.folder=Files.createDirectories(folder.resolve(name));return f;}
 static int naturalLeaves(Terrain terrain,ChunkPos p){int n=0;for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)if(terrain.block(p.chunkX()*16+x,p.chunkY()*16+y,p.chunkZ()*16+z)!=Blocks.AIR)n++;return n;}
 static void ready(RegionReplayWorker.Binding.LightingPump p,int count)throws Exception {RegionReplayWorkerTest.await(()->{try{p.tick();assertTrue(p.status().pending()<=1);return p.status().ready()==count;}catch(IOException e){throw new UncheckedIOException(e);}});}
 @Test public void boundedRootsEvictionAndCloseDoNotPromoteWorld()throws Exception {
  var f=fixture("cache");var path=f.fixture();var worker=f.worker(path);var a=new ChunkPos(0,-2,0);var b=new ChunkPos(1,-2,0);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion)) {
    int na=naturalLeaves(world.terrain(),a),nb=naturalLeaves(world.terrain(),b);var binding=worker.bindTarget(local,world);assertThrows(IllegalArgumentException.class,()->binding.openLightingPump(world,289,8192,8192));assertThrows(IllegalArgumentException.class,()->binding.openLightingPump(world,2,8192,4_194_305));var p=binding.openLightingPump(world,2,8192,16384);assertThrows(IOException.class,()->binding.openLightingPump(world,2,8192,16384));
    p.desired(List.of(a,b));assertTrue(p.roots().isEmpty());ready(p,2);var snapshot=p.roots();assertEquals(2,snapshot.size());assertThrows(UnsupportedOperationException.class,snapshot::clear);for(var root:snapshot.values()){assertEquals(256,root.size());assertTrue(root.isFrozen());}assertEquals(na+nb,p.status().cachedLeaves());
    assertThrows(IllegalArgumentException.class,()->p.desired(List.of(a,b,new ChunkPos(2,10,0))));assertEquals(2,p.status().ready());p.desired(List.of(b));assertEquals(nb,p.status().cachedLeaves());assertFalse(p.roots().containsKey(a));assertEquals(2,snapshot.size());p.desired(List.of());assertEquals(0,p.status().cachedLeaves());
    p.desired(List.of(a));p.tick();p.desired(List.of());assertEquals(0,p.status().pending());assertEquals(0,p.status().ready());p.close();p.close();assertTrue(p.status().closed());assertEquals(0,p.status().cachedLeaves());assertThrows(IOException.class,p::roots);assertTrue(world.getLoadedChunks().isEmpty());assertEquals(0,world.editsVersion());assertFalse(local.replayComplete());assertThrows(IOException.class,local::save);
   }
  }finally{RegionReplayWorkerTest.closed(worker);f.noSpools();}
 }
 @Test public void aggregateBudgetFailsBeforeRetentionAndEvictionAllowsRetry()throws Exception {
  var f=fixture("budget");var worker=f.worker(f.fixture());var a=new ChunkPos(0,-2,0);var b=new ChunkPos(1,-2,0);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion)) {
    int na=naturalLeaves(world.terrain(),a),nb=naturalLeaves(world.terrain(),b);assertTrue(na>0&&nb>0);var p=worker.bindTarget(local,world).openLightingPump(world,2,8192,Math.max(na,nb));p.desired(List.of(a,b));
    RegionReplayWorkerTest.await(()->{try{p.tick();return p.status().failed()==1;}catch(IOException e){throw new UncheckedIOException(e);}});
    assertEquals(1,p.status().ready());assertEquals(na,p.status().cachedLeaves());assertFalse(p.roots().containsKey(b));var failure=p.failure(b);assertNotNull(failure);p.tick();assertSame(failure,p.failure(b));assertEquals(0,p.status().pending());
    p.desired(List.of(b));assertEquals(0,p.status().cachedLeaves());assertNotNull(p.failure(b));p.retry(b);ready(p,1);assertNull(p.failure(b));assertTrue(p.roots().containsKey(b));assertEquals(nb,p.status().cachedLeaves());assertThrows(IllegalArgumentException.class,()->p.retry(a));
    var error=new AtomicReference<Throwable>();var t=new Thread(()->{try{p.close();}catch(Throwable e){error.set(e);}});t.start();t.join(5000);assertFalse(t.isAlive());assertInstanceOf(IOException.class,error.get());assertEquals(1,p.roots().size());local.models=local.models.copy();assertThrows(IOException.class,p::roots);RegionReplayWorkerTest.closed(worker);assertTrue(p.status().closed());assertEquals(0,p.status().cachedLeaves());
   }
  }finally{RegionReplayWorkerTest.closed(worker);f.noSpools();}
 }
 @Test public void queueBackpressureAndCancelledDemandLeavePageRetryable()throws Exception {
  var f=fixture("queue");var path=f.fixture();var spool=f.spool();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var once=new AtomicBoolean(true);
  var worker=new RegionReplayWorker(spool,(cancel,progress)->{var s=LocalGame.openRegionalReadback(path,spool,ReadbackMetadataTest.REV,cancel,progress);return new RegionReplayWorker.Source(s.spool(),s.terrain(),e->{if(once.getAndSet(false)){entered.countDown();try{RegionReplayWorkerTest.release(release);}catch(IOException x){throw new UncheckedIOException(x);}}return s.allowed().test(e);},s.revision(),s.fingerprint(),s.metadata());},1,1,56,2);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion)) {
    var binding=worker.bindTarget(local,world);var p=binding.openLightingPump(world,1,8192,8192);var region=RegionReplayBindingTest.REGION;var range=new RegionReplayPage.CellRange(0,2);var first=binding.submit(region,range,2,world);assertTrue(entered.await(10,TimeUnit.SECONDS));var second=binding.submit(region,range,2,world);p.desired(List.of(region));p.tick();assertTrue(p.status().backpressure());assertEquals(0,p.status().pending());assertEquals(1,release.getCount());second.cancel();p.tick();assertEquals(1,p.status().pending());p.desired(List.of());assertEquals(0,p.status().pending());var retry=binding.submit(region,range,2,world);release.countDown();RegionReplayWorkerTest.done(first);RegionReplayWorkerTest.done(retry);assertNotNull(binding.result(retry,world));p.desired(List.of(region));ready(p,1);assertFalse(p.status().backpressure());
   }
  }finally{release.countDown();RegionReplayWorkerTest.closed(worker);f.noSpools();}
 }
}
