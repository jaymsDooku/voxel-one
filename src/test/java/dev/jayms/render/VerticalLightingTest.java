package dev.jayms.render;

import dev.jayms.*;
import dev.jayms.net.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class VerticalLightingTest {
    static Future<?> pending(WorldLighting lighting)throws Exception {var f=WorldLighting.class.getDeclaredField("pending");f.setAccessible(true);return (Future<?>)f.get(lighting);}
    static void start(WorldLighting lighting,World world,WorldSkyVisibility sky)throws Exception {
        long end=System.nanoTime()+30_000_000_000L;
        while(pending(lighting)==null&&System.nanoTime()<end)lighting.update(world,0,0,1,true,sky,sky,Long.MAX_VALUE);
        assertNotNull(pending(lighting));pending(lighting).get(30,TimeUnit.SECONDS);
    }
    @Test public void boundedHeightSelectionHasHysteresisAndRejectsInvalidInputs() {
        try(var lighting=new WorldLighting()) {
            assertEquals(-32,lighting.originY());lighting.focusHeight(79);assertEquals(-32,lighting.originY());
            lighting.focusHeight(130);assertEquals(64,lighting.originY());lighting.focusHeight(175);assertEquals(64,lighting.originY());
            lighting.focusHeight(176);assertEquals(112,lighting.originY());lighting.focusHeight(-48);assertEquals(-112,lighting.originY());
            for(float invalid:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->lighting.focusHeight(invalid));
            assertEquals(-112,lighting.originY());
        }
    }
    @Test public void highAndNegativeCaptureRetainFractionalOverridesAndSourceFences()throws Exception {
        try(var world=new LocalLightingCaptureTest.IndexedWorld()) {
            var edit=new Protocol.Edit(0,130,0,Blocks.LED,1,1,0,0).withColor(0x123456);world.apply(edit);world.apply(new Protocol.Edit(0,-48,0,Blocks.LED).withColor(0xabcdef));
            var low=new WorldLighting.Capture(world,0,0);while(!low.pump(Long.MAX_VALUE)){}assertNull(low.overrideAt(0,130,0));
            var high=new WorldLighting.Capture(world,0,0,64,null);assertFalse(high.pump(0));while(!high.pump(Long.MAX_VALUE)){}
            assertEquals(288,high.index);assertEquals(288,high.chunks.length);var cell=high.overrideAt(0,130,0);assertNotNull(cell);assertEquals(WorldVoxels.encode(edit),cell.get(8,0,0));assertNull(high.overrideAt(0,63,0));assertNull(high.overrideAt(0,192,0));
            var below=new WorldLighting.Capture(world,0,0,-112,null);while(!below.pump(Long.MAX_VALUE)){}assertEquals(WorldVoxels.encode(new Protocol.Edit(0,-48,0,Blocks.LED).withColor(0xabcdef)),below.overrideAt(0,-48,0).get(0,0,0));
            world.apply(new Protocol.Edit(0,130,0,0));assertEquals(WorldVoxels.encode(edit),cell.get(8,0,0));assertFalse(high.valid(world,0,0));
            assertThrows(IllegalArgumentException.class,()->new WorldLighting.Capture(world,0,0,65,null));
        }
    }
    @Test public void obsoleteHeightCompletionRejectsAndHighLocalEmissionSurvives()throws Exception {
        try(var world=new World(42);var lighting=new WorldLighting()) {
            world.apply(new Protocol.Edit(0,130,0,Blocks.LED).withColor(0x123456));
            var sky=WorldSkyVisibility.build(world);lighting.focusHeight(130);start(lighting,world,sky);
            lighting.focusHeight(-48);assertNull(lighting.update(world,0,0,1,true,sky,sky,Long.MAX_VALUE,false));assertEquals(1,lighting.rejected);assertEquals(0,lighting.completed);assertNull(pending(lighting));
            lighting.focusHeight(130);start(lighting,world,sky);var result=lighting.update(world,0,0,1,true,sky,sky,Long.MAX_VALUE,false);
            assertNotNull(result);assertEquals(64,result.y);assertEquals(96*128*96*4,result.rgba.length);assertEquals(96*128*96,result.roots.length);
            var sample=result.sampleLighting(.5f,130.5f,.5f);assertTrue(sample[0]>0&&sample[1]>sample[0]&&sample[2]>sample[1]);
            assertTrue(WorldLighting.heightCovered(result,130));assertFalse(WorldLighting.heightCovered(result,-48));assertTrue(lighting.acceptedFence().valid(world,0,0));
        }
    }
    @Test public void residentHeightCannotPromotePrivateReplayCoverage()throws Exception {
        try(var world=new World();var lighting=new WorldLighting()) {
            var fixture=new ReplayJointLightingCoverageTest.Fixture();fixture.attach(world);lighting.focusHeight(130);
            var joint=world.replayLightingSnapshot(0,0);assertThrows(IllegalArgumentException.class,()->new WorldLighting.Capture(world,0,0,64,joint));
            assertNull(lighting.updateReplay(world,0,0,1,true,Long.MAX_VALUE));assertNotNull(pending(lighting));pending(lighting).get(30,TimeUnit.SECONDS);
            var result=lighting.updateReplay(world,0,0,1,true,Long.MAX_VALUE,false);assertNotNull(result);assertEquals(-32,result.y);assertTrue(world.getLoadedChunks().isEmpty());assertEquals(0,world.editsVersion());
        }
    }
}
