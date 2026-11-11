package dev.jayms.net;
import dev.jayms.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class RegionReplaySceneTest {
 @TempDir Path folder;
 @Test void cpuCoverageDoesNotGrantMeshReadinessAndEvictionIsImmediate()throws Exception {
  var fixture=new RegionReplayReadViewTest();fixture.folder=folder;
  try(var c=fixture.context("scene",0);var scene=c.binding.openScene(c.world,1,8192)) {
   var region=RegionReplayReadViewTest.SOURCE;
   assertFalse(c.world.replayDataReady(0,160,0));assertFalse(c.world.isLoaded(0,160,0));
   assertThrows(IOException.class,()->scene.beginUpload(region));
   assertThrows(IllegalArgumentException.class,()->scene.desired(List.of(region,region)));
   scene.desired(List.of(region));long end=System.nanoTime()+10_000_000_000L;
   while(scene.status().data().ready()!=1&&System.nanoTime()<end){scene.tick();Thread.sleep(5);}
   assertEquals(1,scene.status().data().ready());assertTrue(c.world.replayDataReady(0,160,0));
   assertEquals(Blocks.STONE,c.world.sample(0,160,0));assertFalse(c.world.isLoaded(0,160,0));
   assertTrue(c.world.getLoadedChunks().isEmpty());assertFalse(c.world.nearbyReady(0,160,1));
   assertEquals(0,scene.status().gpuEntries());assertThrows(IOException.class,c.local::save);
   var error=new AtomicReference<Throwable>();var thread=new Thread(()->{try{scene.close();}catch(Throwable failure){error.set(failure);}});
   thread.start();thread.join(5000);assertFalse(thread.isAlive());assertInstanceOf(IOException.class,error.get());
   assertTrue(c.world.replayDataReady(0,160,0));scene.desired(List.of());
   assertFalse(c.world.replayDataReady(0,160,0));assertFalse(c.world.isLoaded(0,160,0));
   assertThrows(World.UnloadedReplayCellException.class,()->c.world.sample(0,160,0));
   assertArrayEquals(c.original,java.nio.file.Files.readAllBytes(c.path));
  }
 }
}
