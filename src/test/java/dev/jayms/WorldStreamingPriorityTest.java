package dev.jayms;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldStreamingPriorityTest {
    @Test void forwardSkyColumnsPrecedeUnusedRearCornersAfterCollisionArea()throws Exception {
        try(var world=new World(42)){
            var generated=new ConcurrentLinkedQueue<ChunkPos>();
            var scheduler=new TerrainScheduler(42,world.terrain().version,(position,terrain)->{generated.add(position);return new Chunk();});
            var field=World.class.getDeclaredField("terrainScheduler");field.setAccessible(true);field.set(world,scheduler);
            var history=World.class.getDeclaredField("streamHistory");history.setAccessible(true);history.setBoolean(world,true);
            var previous=World.class.getDeclaredField("lastStreamX");previous.setAccessible(true);previous.setFloat(world,8);
            var previousZ=World.class.getDeclaredField("lastStreamZ");previousZ.setAccessible(true);previousZ.setFloat(world,8);
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            // Preserve travel direction while draining detached work; minimal representable forward
            // steps stay in the same chunk and rectangle for this scheduling check.
            float x=128;
            while(generated.size()<140&&System.nanoTime()<end){x=Math.nextUp(x);world.streamAsync(x,110,8,2_000_000);Thread.sleep(1);}
            var positions=new java.util.ArrayList<>(generated);assertTrue(positions.size()>=140);
            for(var p:positions.subList(0,90))assertTrue(p.chunkX()>=7&&p.chunkX()<=9&&p.chunkZ()>=-1&&p.chunkZ()<=1,"Collision rectangle gets first service: "+p);
            for(var p:positions.subList(90,130))assertEquals(10,p.chunkX(),"Forward sky columns precede unused side/rear corners");
            assertEquals(positions.size(),new java.util.HashSet<>(positions).size(),"Prediction does not duplicate accepted jobs");
        }
    }
    @Test void playableRectanglePrecedesUnusedRingCornersAndOuterWorkStillRuns()throws Exception {
        try(var world=new World(42)){
            var generated=new ConcurrentLinkedQueue<ChunkPos>();
            var scheduler=new TerrainScheduler(42,world.terrain().version,(position,terrain)->{generated.add(position);return new Chunk();});
            var field=World.class.getDeclaredField("terrainScheduler");field.setAccessible(true);field.set(world,scheduler);
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(generated.size()<=160&&System.nanoTime()<end){world.streamAsync(8,110,8,2_000_000);Thread.sleep(1);}
            var positions=new java.util.ArrayList<>(generated);
            assertTrue(positions.size()>160,"Background terrain progresses after playable coverage");
            var critical=new java.util.HashSet<ChunkPos>();
            for(var position:positions.subList(0,160)){
                assertTrue(position.chunkX()>=-1&&position.chunkX()<=2&&position.chunkZ()>=-1&&position.chunkZ()<=2,"All sixteen playable columns precede unused corners: "+position);
                assertTrue(critical.add(position),"Requests remain coalesced");
            }
            var next=positions.get(160);
            assertTrue(next.chunkX() < -1||next.chunkX()>2||next.chunkZ() < -1||next.chunkZ()>2,"Outer terrain still receives work");
            assertTrue(world.terrainPending()<=TerrainScheduler.CAPACITY+1);
        }
    }
}
