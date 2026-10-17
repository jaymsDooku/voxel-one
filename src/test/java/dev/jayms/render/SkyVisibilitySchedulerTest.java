package dev.jayms.render;

import dev.jayms.*;
import dev.jayms.net.*;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SkyVisibilitySchedulerTest {
    static SkyVisibilityScheduler.Prepared await(SkyVisibilityScheduler s,World w)throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<end){var p=s.update(w,1_000_000);assertTrue(s.pending()<=3);if(p!=null&&p.valid(w))return p;Thread.sleep(1);}
        fail("No current sky atlas prepared");return null;
    }
    @Test void cachedTilesMatchFullSkyIncludingUnloadedFractionalRoofs()throws Exception {
        try(var world=new World(42);var sky=new SkyVisibilityScheduler()) {
            var left=new Chunk();left.setBlock(15,0,0,Blocks.STONE);world.addChunk(new ChunkPos(-1,4,0),left);
            var right=new Chunk();right.setBlock(0,0,0,Blocks.GLASS);world.addChunk(new ChunkPos(0,4,0),right);
            world.apply(new Protocol.Edit(-1,140,0,Blocks.STONE,1,0,0,0));
            var expected=WorldSkyVisibility.build(world);SkyVisibilityScheduler.Prepared result=null;
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(System.nanoTime()<end){var p=sky.update(world,1_000_000);if(p!=null)result=p;if(result!=null&&result.valid(world)&&java.util.Arrays.equals(result.data.data,expected.data))break;Thread.sleep(1);}
            assertNotNull(result);assertArrayEquals(expected.data,result.data.data);
            assertEquals(expected.sample(-.75f,100,.25f),result.data.sample(-.75f,100,.25f));
            world.apply(new Protocol.Edit(-1,140,0,0));assertFalse(result.valid(world));
            SkyVisibilityScheduler.Prepared latest=null;var exact=WorldSkyVisibility.build(world);
            end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(System.nanoTime()<end){var p=sky.update(world,1_000_000);if(p!=null)latest=p;if(latest!=null&&latest.valid(world)&&java.util.Arrays.equals(latest.data.data,exact.data))break;Thread.sleep(1);}
            assertNotNull(latest);assertArrayEquals(exact.data,latest.data.data);assertEquals(1,latest.data.sample(-.75f,100,.25f));
        }
    }
    @Test void staleCaptureAndPreviousWorldCannotPublish()throws Exception {
        CountDownLatch gate=new CountDownLatch(1);
        try(var world=new World(42);var other=new World(99);var sky=new SkyVisibilityScheduler()) {
            var p=new ChunkPos(0,4,0);var c=new Chunk();c.setBlock(0,0,0,Blocks.STONE);world.addChunk(p,c);
            world.prepareTerrainTask(()->{gate.await();return null;});sky.update(world,2_000_000);
            world.apply(new Protocol.Edit(0,64,0,0));gate.countDown();
            var ready=await(sky,world);assertTrue(sky.rejected>0);assertFalse(ready.valid(other));
            world.unloadChunk(p);assertFalse(ready.valid(world));assertTrue(sky.cachedBytes()<=4L*WorldSkyVisibility.MAX_INTS);
        }finally{gate.countDown();}
    }
}
