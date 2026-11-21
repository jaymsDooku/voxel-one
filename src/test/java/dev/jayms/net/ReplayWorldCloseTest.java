package dev.jayms.net;
import dev.jayms.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ReplayWorldCloseTest {
 @TempDir Path folder;
 @Test void everyRealReplayViewRejectsForeignCloseBeforeWorldMutation()throws Exception {
  for(int mode=0;mode<3;mode++) {
   var fixture=new RegionReplayReadViewTest();fixture.folder=Files.createDirectory(folder.resolve("mode"+mode));
   try(var c=fixture.context("close",mode==0?1:0)) {
    if(mode==0)c.publish(RegionReplayReadViewTest.SOURCE,0);
    else if(mode==1){var pump=c.binding.openChunkReadPump(c.world,1,8192);pump.desired(List.of(RegionReplayReadViewTest.SOURCE));RegionReplayChunkPumpTest.ready(pump,1);}
    else{var scene=c.binding.openScene(c.world,1,8192);scene.desired(List.of(RegionReplayReadViewTest.SOURCE));long end=System.nanoTime()+10_000_000_000L;while(scene.status().data().ready()!=1&&System.nanoTime()<end){scene.tick();Thread.sleep(5);}assertEquals(1,scene.status().data().ready());}
    long sky=c.world.skyStamp(),revision=c.world.sceneRevision();var failure=new AtomicReference<Throwable>();
    var thread=new Thread(()->{try{c.world.close();}catch(Throwable problem){failure.set(problem);}});thread.start();thread.join(5000);assertFalse(thread.isAlive());assertInstanceOf(IllegalStateException.class,failure.get());
    assertFalse(c.world.isClosed());assertEquals(sky,c.world.skyStamp());assertEquals(revision,c.world.sceneRevision());assertTrue(c.world.replayDataReady(0,160,0));assertEquals(Blocks.STONE,c.world.sample(0,160,0));
    assertArrayEquals(c.original,Files.readAllBytes(c.path));c.world.close();assertTrue(c.world.isClosed());c.world.close();
   }
  }
 }
}
