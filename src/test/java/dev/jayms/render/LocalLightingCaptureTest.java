package dev.jayms.render;

import dev.jayms.*;
import dev.jayms.net.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalLightingCaptureTest {
    static final class IndexedWorld extends World {
        @Override public Map<String,Protocol.Edit> editsSnapshot(){throw new AssertionError("Full history copy forbidden");}
    }
    @Test void distantHistoryDoesNotEnterLocalCaptureOrInvalidateIt()throws Exception {
        try(var world=new IndexedWorld()) {
            for(int i=0;i<100_000;i++)world.apply(new Protocol.Edit(100_000+i%1000,1000,100_000+i/1000,Blocks.STONE));
            try(var sky=new SkyVisibilityScheduler()) {
                for(int i=0;i<64;i++)sky.update(world,0,0,250_000);
                assertEquals(0,sky.pending());assertEquals(0,sky.cachedBytes());
            }
            var capture=new WorldLighting.Capture(world,0,0);
            assertTrue(capture.pump(Long.MAX_VALUE));
            assertEquals(288,capture.index);assertTrue(capture.capturedCells==0);
            capture.releasePayload();assertNull(capture.chunks);assertNull(capture.overrides);assertTrue(capture.valid(world,0,0));
            world.apply(new Protocol.Edit(200_000,1000,200_000,Blocks.STONE));
            assertTrue(capture.valid(world,0,0));
            world.apply(new Protocol.Edit(0,0,0,Blocks.LED).withColor(0x123456));
            assertFalse(capture.valid(world,0,0));
        }
    }
    @Test void denseLocalCaptureSlicesAndKeepsFrozenCurrentFractionalCells()throws Exception {
        try(var world=new IndexedWorld()) {
            for(int i=0;i<1536;i++)world.apply(new Protocol.Edit(i%16,i/256,i/16%16,Blocks.STONE));
            var capture=new WorldLighting.Capture(world,0,0);
            assertFalse(capture.pump(0));assertEquals(0,capture.index);
            assertFalse(capture.pump(Long.MAX_VALUE));assertTrue(capture.capturedCells<=1024);
            while(!capture.pump(Long.MAX_VALUE)){}
            world.apply(new Protocol.Edit(0,0,0,Blocks.LED,1,1,0,0).withColor(0x123456));
            assertFalse(capture.valid(world,0,0));
            var current=new WorldLighting.Capture(world,0,0);while(!current.pump(Long.MAX_VALUE)){}
            var cell=current.overrideAt(0,0,0);
            assertEquals(0xfe123456,cell.get(8,0,0));
            world.apply(new Protocol.Edit(0,0,0,0));
            assertEquals(0xfe123456,cell.get(8,0,0));
            assertFalse(current.valid(world,0,0));
        }
    }
    @Test void chunkReplacementAndWorldSwitchFenceCapture()throws Exception {
        try(var world=new World();var other=new World(999)) {
            var p=new ChunkPos(0,0,0);world.addChunk(p,new Chunk());
            var capture=new WorldLighting.Capture(world,0,0);while(!capture.pump(Long.MAX_VALUE)){}
            assertFalse(capture.valid(other,0,0));assertFalse(capture.valid(world,16,0));
            world.unloadChunk(p);assertFalse(capture.valid(world,0,0));
        }
    }
    @Test void negativeRegionEdgesAndModelChangesAreFenced()throws Exception {
        try(var world=new IndexedWorld()) {
            for(int x:new int[]{-65,-64,31,32})world.apply(new Protocol.Edit(x,0,0,Blocks.LED));
            var capture=new WorldLighting.Capture(world,-16,0);while(!capture.pump(Long.MAX_VALUE)){}
            assertEquals(2,capture.capturedCells);
            assertNotNull(capture.overrideAt(-64,0,0));
            assertNotNull(capture.overrideAt(31,0,0));
            var definition=dev.jayms.net.model.ModelGenerators.flowerPot();definition.voxels().set(0,0,0,0xffabcdef);
            world.models().register(definition,"synthetic");assertFalse(capture.valid(world,-16,0));
        }
    }

    @Test void workerRejectsStaleUnloadedFractionalRgbAndPublishesCurrentSnapshot()throws Exception {
        try(var world=new IndexedWorld();var lighting=new WorldLighting()) {
            for(int x=-3;x<3;x++)for(int y=-2;y<6;y++)for(int z=-3;z<3;z++)
                if(x!=0||y!=5||z!=0)world.addChunk(new ChunkPos(x,y,z),new Chunk());
            for(int x=0;x<16;x++)for(int y=80;y<96;y++)for(int z=0;z<16;z++)world.apply(new Protocol.Edit(x,y,z,0));
            world.apply(new Protocol.Edit(0,80,0,Blocks.LED,1,1,0,0).withColor(0x1234ff));
            var pending=WorldLighting.class.getDeclaredField("pending");pending.setAccessible(true);
            long deadline=System.nanoTime()+60_000_000_000L;
            while(pending.get(lighting)==null&&System.nanoTime()<deadline){assertNull(lighting.update(world,0,0,0,true,null,null,System.nanoTime()+2_000_000));Thread.sleep(1);}
            assertNotNull(pending.get(lighting));
            world.apply(new Protocol.Edit(0,80,0,Blocks.LED,1,1,0,0).withColor(0xff1234));
            LightVolume volume=null;
            while(volume==null&&System.nanoTime()<deadline){volume=lighting.update(world,0,0,0,true,null,null,System.nanoTime()+2_000_000);Thread.sleep(1);}
            assertNotNull(volume,"Current local snapshot eventually publishes");
            var rgb=volume.sample(.75f,80.25f,.25f);assertTrue(rgb[0]>rgb[2],"Stale blue result was rejected; current red fraction survived unloaded capture");
            assertTrue(lighting.acceptedFence().valid(world,0,0));assertNull(lighting.acceptedFence().chunks);assertNull(lighting.acceptedFence().overrides);
        }
    }

}
