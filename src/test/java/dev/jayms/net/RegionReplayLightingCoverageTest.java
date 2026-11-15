package dev.jayms.net;
import dev.jayms.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class RegionReplayLightingCoverageTest {
 @TempDir Path folder;
 ReadbackMetadataTest fixture(String name)throws Exception{var f=new ReadbackMetadataTest();f.folder=Files.createDirectories(folder.resolve(name));return f;}
 @Test public void neighbourhoodMatchesCaptureOrderAndRejectsInvalidFocus()throws Exception {
  var regions=RegionReplayWorker.Binding.lightingNeighbourhood(-16,32);assertEquals(288,regions.size());assertEquals(288,new HashSet<>(regions).size());
  for(int i=0;i<288;i++)assertEquals(new ChunkPos(Math.floorDiv(-16-48,16)+i%6,-2+i/6%8,Math.floorDiv(32-48,16)+i/48),regions.get(i));
  assertThrows(UnsupportedOperationException.class,regions::clear);assertThrows(IllegalArgumentException.class,()->RegionReplayWorker.Binding.lightingNeighbourhood(1,0));assertThrows(IllegalArgumentException.class,()->RegionReplayWorker.Binding.lightingNeighbourhood(0,-1));assertThrows(IOException.class,()->RegionReplayWorker.Binding.lightingNeighbourhood(Integer.MIN_VALUE,0));assertThrows(IOException.class,()->RegionReplayWorker.Binding.lightingNeighbourhood(Integer.MAX_VALUE-15,0));
 }
 @Test public void completeReceiptTracksGenerationUnknownAndCachedSubsets()throws Exception {
  var f=fixture("coverage");var worker=f.worker(f.fixture());var a=new ChunkPos(0,10,0);var b=new ChunkPos(1,10,0);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion)) {
    var p=worker.bindTarget(local,world).openLightingPump(world,2,8192,16384);assertNull(p.coverage());assertFalse(p.current(null));p.desired(List.of(a,b));assertNull(p.coverage());p.tick();assertNull(p.coverage());RegionReplayLightingPumpTest.ready(p,2);
    var first=p.coverage();assertNotNull(first);assertEquals(List.of(a,b),List.copyOf(first.roots().keySet()));assertTrue(p.current(first));assertThrows(UnsupportedOperationException.class,()->first.roots().clear());assertFalse(p.current(new RegionReplayWorker.Binding.LightingCoverage(first.generation(),first.roots())));
    p.desired(List.of(b,a));assertSame(first,p.coverage());assertThrows(IllegalArgumentException.class,()->p.desired(List.of(a,b,new ChunkPos(2,10,0))));assertSame(first,p.coverage());
    p.desired(List.of(b));assertFalse(p.current(first));var subset=p.coverage();assertNotNull(subset);assertTrue(subset.generation()>first.generation());assertEquals(Set.of(b),subset.roots().keySet());assertEquals(2,first.roots().size());
    p.desired(List.of(a,b));assertNull(p.coverage());assertFalse(p.current(subset));RegionReplayLightingPumpTest.ready(p,2);var second=p.coverage();assertNotSame(first,second);assertTrue(second.generation()>subset.generation());assertFalse(p.current(first));p.desired(List.of());assertNull(p.coverage());assertFalse(p.current(second));assertTrue(world.getLoadedChunks().isEmpty());assertEquals(0,world.editsVersion());assertFalse(local.replayComplete());assertThrows(IOException.class,local::save);
   }
  }finally{RegionReplayWorkerTest.closed(worker);f.noSpools();}
 }
 @Test public void failedCoverageOwnerSourceAndCloseNeverKeepReceiptCurrent()throws Exception {
  var f=fixture("fence");var worker=f.worker(f.fixture());var a=new ChunkPos(0,10,0);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion)) {
    var p=worker.bindTarget(local,world).openLightingPump(world,1,8192,8192);p.desired(List.of(a));RegionReplayLightingPumpTest.ready(p,1);var receipt=p.coverage();var error=new AtomicReference<Throwable>();var t=new Thread(()->{try{p.current(receipt);}catch(Throwable e){error.set(e);}});t.start();t.join(5000);assertFalse(t.isAlive());assertInstanceOf(IOException.class,error.get());assertTrue(p.current(receipt));local.models=local.models.copy();assertThrows(IOException.class,()->p.current(receipt));RegionReplayWorkerTest.closed(worker);assertThrows(IOException.class,()->p.current(receipt));
   }
  }finally{RegionReplayWorkerTest.closed(worker);f.noSpools();}
  var g=fixture("budget");var limited=g.worker(g.fixture());
  try {
   ReadbackMetadataTest.ready(limited);var local=limited.metadataResult(ReadbackMetadataTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion)) {
    var p=limited.bindTarget(local,world).openLightingPump(world,1,0,0);p.desired(List.of(a));RegionReplayWorkerTest.await(()->{try{p.tick();return p.status().failed()==1;}catch(IOException e){throw new UncheckedIOException(e);}});assertNull(p.coverage());p.retry(a);p.tick();assertNull(p.coverage());p.close();assertThrows(IOException.class,p::coverage);
   }
  }finally{RegionReplayWorkerTest.closed(limited);g.noSpools();}
 }
}
