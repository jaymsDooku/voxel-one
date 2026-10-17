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
    @Test void meshOnlyInvalidationDoesNotInvalidateLightingGeometry()throws Exception {
        try(var world=new World()){
            var c=new Chunk();world.addChunk(new ChunkPos(0,0,0),c);
            var capture=new WorldLighting.Capture(world,0,0);while(!capture.pump(Long.MAX_VALUE)){}
            long sky=world.skyStamp();c.markDirty();
            assertEquals(sky,world.skyStamp());assertTrue(capture.valid(world,0,0));
            c.setBlock(0,0,0,Blocks.STONE);assertFalse(capture.valid(world,0,0));
        }
    }
    @Test void deterministicTerrainResidencyDoesNotCancelEquivalentLightingButDirectEditsDo()throws Exception {
        try(var world=new World(42)){
            var p=new ChunkPos(0,4,0);var capture=new WorldLighting.Capture(world,0,0);
            while(!capture.pump(Long.MAX_VALUE)){}
            var generated=ChunkGenerator.generate(p,world.terrain());world.addChunk(p,generated);
            assertTrue(capture.valid(world,0,0),"Generated attachment equals detached terrain");
            world.unloadChunk(p);assertTrue(capture.valid(world,0,0),"Generated unload preserves canonical terrain");
            generated=ChunkGenerator.generate(p,world.terrain());world.addChunk(p,generated);
            generated.setBlock(0,0,0,Blocks.LED);assertFalse(capture.valid(world,0,0),"Direct resident mutation remains fenced");
            var changed=new WorldLighting.Capture(world,0,0);while(!changed.pump(Long.MAX_VALUE)){}
            world.unloadChunk(p);assertFalse(changed.valid(world,0,0),"Removing custom geometry remains fenced");
            var wrongSeed=new WorldLighting.Capture(world,0,0);while(!wrongSeed.pump(Long.MAX_VALUE)){}
            world.addChunk(p,ChunkGenerator.generate(p,new Terrain(999)));
            assertFalse(wrongSeed.valid(world,0,0),"A generated chunk from another seed is custom geometry");
            world.unloadChunk(p);var wrongPosition=new WorldLighting.Capture(world,0,0);while(!wrongPosition.pump(Long.MAX_VALUE)){}
            world.addChunk(p,ChunkGenerator.generate(new ChunkPos(1,4,0),world.terrain()));
            assertFalse(wrongPosition.valid(world,0,0),"Moving generated data to another position is custom geometry");
        }
    }
    @Test void ceilingMaskIgnoresStaleAtlasAfterCustomRoofUnloads()throws Exception {
        try(var world=new World(42)){
            var p=new ChunkPos(0,8,0);var roof=new Chunk();roof.setBlock(0,0,0,Blocks.STONE);world.addChunk(p,roof);
            var before=new WorldLighting.Capture(world,0,0);var oldSky=WorldSkyVisibility.build(world);
            assertTrue(world.requiresSkyCeiling(0,0));assertEquals(0,WorldLighting.skyVisibility(oldSky,before.ceilings,0,0,.5f,96,.5f));
            world.unloadChunk(p);assertFalse(before.valid(world,0,0));var after=new WorldLighting.Capture(world,0,0);
            assertFalse(world.requiresSkyCeiling(0,0));assertEquals(1,WorldLighting.skyVisibility(oldSky,after.ceilings,0,0,.5f,96,.5f),"Stale atlas roof cannot darken a column with no remaining ceiling");
        }
    }
    @Test void closedWorldCannotSubmitLightingWork()throws Exception {
        try(var world=new World();var lighting=new WorldLighting()){
            var capture=new WorldLighting.Capture(world,0,0);world.close();assertFalse(capture.valid(world,0,0));
            assertNull(lighting.update(world,0,0));assertEquals(0,lighting.submitted);
        }
    }
    @Test void predictedCompletionCanBeUsedAtANewRelevantFocus()throws Exception {
        try(var world=new World(42);var lighting=new WorldLighting()){
            for(int x=-3;x<=6;x++)for(int z=-3;z<3;z++)for(int y=-2;y<6;y++)world.addChunk(new ChunkPos(x,y,z),new Chunk());
            var pending=WorldLighting.class.getDeclaredField("pending");pending.setAccessible(true);long end=System.nanoTime()+30_000_000_000L;
            while(pending.get(lighting)==null&&System.nanoTime()<end)lighting.updatePredicted(world,32,0,0,0,1,true,null,System.nanoTime()+2_000_000,true);
            assertNotNull(pending.get(lighting));((java.util.concurrent.Future<?>)pending.get(lighting)).get(30,java.util.concurrent.TimeUnit.SECONDS);
            var completed=pending.get(lighting);long submitted=lighting.submitted;
            for(int i=0;i<8;i++)assertNull(lighting.updatePredicted(world,48,0,16,0,1,true,null,System.nanoTime()+2_000_000,true,false));
            assertSame(completed,pending.get(lighting),"One completed CPU payload waits while the GPU slot is occupied");assertEquals(submitted,lighting.submitted,"Backpressure cannot create extra workers or results");
            var result=lighting.updatePredicted(world,48,0,16,0,1,true,null,System.nanoTime()+2_000_000,true);
            assertNotNull(result,"A still-relevant completed field is not discarded at each chunk boundary");assertEquals(-16,result.x);
            var capture=WorldLighting.class.getDeclaredField("capture");capture.setAccessible(true);assertNull(capture.get(lighting),"Collection must return before starting a new capture for an occupied GPU slot");assertNull(pending.get(lighting));
            lighting.invalidateAccepted();lighting.updatePredicted(world,48,0,16,0,1,true,null,System.nanoTime()+2_000_000,true);
            while(pending.get(lighting)==null&&System.nanoTime()<end)lighting.updatePredicted(world,48,0,16,0,1,true,null,System.nanoTime()+2_000_000,true);
            assertNotNull(pending.get(lighting));((java.util.concurrent.Future<?>)pending.get(lighting)).get(30,java.util.concurrent.TimeUnit.SECONDS);
            assertNull(lighting.updatePredicted(world,2048,0,2048,0,1,true,null,System.nanoTime()+2_000_000,false),"Teleport discards an irrelevant completion");
        }
    }
    @Test void occupiedGpuSlotDoesNotFreezeAnObsoleteCapture()throws Exception {
        try(var world=new World(42);var lighting=new WorldLighting()){
            var capture=WorldLighting.class.getDeclaredField("capture");capture.setAccessible(true);
            var pending=WorldLighting.class.getDeclaredField("pending");pending.setAccessible(true);
            for(int x=128;x<192;x+=4){
                assertNull(lighting.updatePredicted(world,x,0,x,0,1,true,null,Long.MAX_VALUE,false,false));
                assertNull(capture.get(lighting));assertNull(pending.get(lighting));
            }
            // Once the slot is free, preparation uses the new forecast, not x128.
            lighting.updatePredicted(world,224,0,192,0,1,true,null,0,true,true);
            assertNull(capture.get(lighting),"An expired deadline must still do no work");
            lighting.updatePredicted(world,224,0,192,0,1,true,null,Long.MAX_VALUE,true,true);
            var current=(WorldLighting.Capture)capture.get(lighting);
            if(current!=null)assertEquals(224,current.x);
            else {var x=WorldLighting.class.getDeclaredField("submittedX");x.setAccessible(true);assertNotNull(pending.get(lighting));assertEquals(224,x.getInt(lighting));}
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
            ((java.util.concurrent.Future<?>)pending.get(lighting)).get(30,java.util.concurrent.TimeUnit.SECONDS);
            assertNull(lighting.update(world,0,0,0,true,null,null,System.nanoTime()+2_000_000,false));
            assertNull(pending.get(lighting),"Completion is collected even when new preparation is not allowed");assertEquals(1,lighting.submitted);assertEquals(1,lighting.rejected);
            LightVolume volume=null;
            while(volume==null&&System.nanoTime()<deadline){volume=lighting.update(world,0,0,0,true,null,null,System.nanoTime()+2_000_000);Thread.sleep(1);}
            assertNotNull(volume,"Current local snapshot eventually publishes");
            var rgb=volume.sample(.75f,80.25f,.25f);assertTrue(rgb[0]>rgb[2],"Stale blue result was rejected; current red fraction survived unloaded capture");
            assertTrue(lighting.acceptedFence().valid(world,0,0));assertNull(lighting.acceptedFence().chunks);assertNull(lighting.acceptedFence().overrides);
            long submitted=lighting.submitted;lighting.invalidateAccepted();
            long retry=System.nanoTime()+5_000_000_000L;
            while(pending.get(lighting)==null&&System.nanoTime()<retry){lighting.update(world,0,0,0,true,null,null,System.nanoTime()+2_000_000);Thread.sleep(1);}
            assertEquals(submitted+1,lighting.submitted,"Discarded payload is regenerated at the same focus");
        }
    }

    @Test void canonicalEditedGenerationKeepsLightingValidAcrossUnloadAndReplay()throws Exception {
        try(var world=new World(42)){
            var position=new ChunkPos(0,4,0);world.addChunk(position,ChunkGenerator.generate(position,world.terrain()));
            world.apply(new Protocol.Edit(0,65,0,Blocks.LED,1,1,0,0).withColor(0x123456));
            var capture=new WorldLighting.Capture(world,0,0);while(!capture.pump(Long.MAX_VALUE)){}
            world.unloadChunk(position);assertTrue(capture.valid(world,0,0),"Unloading generated terrain with unchanged canonical edits does not change lighting geometry");
            world.addChunk(position,ChunkGenerator.generate(position,world.terrain()));assertTrue(capture.valid(world,0,0),"Canonical replay equals the detached snapshot");
            assertEquals(0xfe123456,world.cell(0,65,0).get(8,0,0));world.apply(new Protocol.Edit(0,65,0,Blocks.AIR));assertFalse(capture.valid(world,0,0),"A newer authoritative edit still invalidates the result");
        }
    }

}
