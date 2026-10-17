package dev.jayms;

import dev.jayms.net.Terrain;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TerrainSchedulerTest {
    private static TerrainScheduler.Result await(TerrainScheduler scheduler)throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<deadline){var result=scheduler.poll();if(result!=null)return result;Thread.sleep(1);}
        fail("Terrain did not finish");return null;
    }
    @Test void duplicatesCapacityAndObsoleteRunningWorkKeepSlots()throws Exception {
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
        try(var scheduler=new TerrainScheduler(42,Terrain.CURRENT_VERSION,(p,t)->{
            started.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}
            return new Chunk();
        })) {
            var first=new ChunkPos(0,0,0);assertTrue(scheduler.request(first,null));assertTrue(started.await(2,TimeUnit.SECONDS));
            assertFalse(scheduler.request(first,null));
            for(int i=1;i<TerrainScheduler.CAPACITY;i++)assertTrue(scheduler.request(new ChunkPos(i,0,0),null));
            assertFalse(scheduler.request(new ChunkPos(99,0,0),null));assertEquals(8,scheduler.pending());
            scheduler.discardOutside(100,100,4);assertEquals(1,scheduler.pending(),"obsolete running generation retains its slot");
            assertFalse(scheduler.request(first,null));
            var next=new ChunkPos(100,0,100);assertTrue(scheduler.request(next,null));release.countDown();
            assertEquals(next,await(scheduler).position());assertEquals(0,scheduler.pending());assertEquals(1,scheduler.discarded);
        }finally{release.countDown();}
    }
    @Test void fifoFairnessAndExplicitUnloadFence()throws Exception {
        try(var scheduler=new TerrainScheduler(42,3,(p,t)->new Chunk())) {
            for(int i=0;i<8;i++)assertTrue(scheduler.request(new ChunkPos(i,0,0),null));
            scheduler.cancel(new ChunkPos(3,0,0));
            var actual=new ArrayList<Integer>();while(actual.size()<7)actual.add(await(scheduler).position().chunkX());
            assertEquals(List.of(0,1,2,4,5,6,7),actual);
        }
    }
    @Test void allVersionsMatchDetachedOutputAcrossFeatureBorders()throws Exception {
        for(int version=1;version<=Terrain.CURRENT_VERSION;version++) {
            try(var scheduler=new TerrainScheduler(748291,version)) {
                Terrain reference=new Terrain(748291,version);
                for(var p:List.of(new ChunkPos(-1,3,0),new ChunkPos(0,3,0),new ChunkPos(1,4,-1))) {
                    assertTrue(scheduler.request(p,null));Chunk actual=await(scheduler).chunk();Chunk expected=ChunkGenerator.generate(p,reference);
                    for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++)assertEquals(expected.getBlock(x,y,z),actual.getBlock(x,y,z));
                }
            }
        }
    }
    @Test void failuresReachOwnerAndDoNotWedgeNextJob()throws Exception {
        try(var scheduler=new TerrainScheduler(42,3,(p,t)->{if(p.chunkX()==0)throw new IllegalArgumentException("synthetic failure");return new Chunk();})) {
            scheduler.request(new ChunkPos(0,0,0),null);scheduler.request(new ChunkPos(1,0,0),null);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while(scheduler.pending()==2&&System.nanoTime()<deadline) {
                try{scheduler.poll();}catch(IllegalStateException expected){assertEquals("synthetic failure",expected.getCause().getMessage());break;}
                Thread.sleep(1);
            }
            assertEquals(1,await(scheduler).position().chunkX());
        }
    }
    @Test void shutdownInterruptsActiveWorkAndRejectsRequests()throws Exception {
        CountDownLatch active=new CountDownLatch(1);
        var scheduler=new TerrainScheduler(42,3,(p,t)->{active.countDown();try{Thread.sleep(30000);}catch(InterruptedException expected){Thread.currentThread().interrupt();}return new Chunk();});
        scheduler.request(new ChunkPos(0,0,0),null);assertTrue(active.await(2,TimeUnit.SECONDS));
        scheduler.close();assertEquals(0,scheduler.pending());assertFalse(scheduler.request(new ChunkPos(1,0,0),null));scheduler.close();
    }
    @Test void boundedAuxiliaryLaneAlternatesWithTerrain()throws Exception {
        CountDownLatch active=new CountDownLatch(1),gate=new CountDownLatch(1);
        var order=Collections.synchronizedList(new ArrayList<String>());
        try(var scheduler=new TerrainScheduler(42,3,(p,t)->{order.add("terrain"+p.chunkX());if(p.chunkX()==0){active.countDown();try{gate.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}}return new Chunk();})) {
            scheduler.request(new ChunkPos(0,0,0),null);assertTrue(active.await(2,TimeUnit.SECONDS));
            var a=scheduler.prepare(()->{order.add("sky1");return 1;});var b=scheduler.prepare(()->{order.add("sky2");return 2;});
            assertThrows(RejectedExecutionException.class,()->scheduler.prepare(()->3));scheduler.request(new ChunkPos(1,0,0),null);gate.countDown();
            assertEquals(1,a.get(2,TimeUnit.SECONDS));assertEquals(2,b.get(2,TimeUnit.SECONDS));
            assertEquals(List.of("terrain0","sky1","terrain1","sky2"),order);
        }finally{gate.countDown();}
    }
}
