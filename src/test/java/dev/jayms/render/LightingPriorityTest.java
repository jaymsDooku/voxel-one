package dev.jayms.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LightingPriorityTest {
    @Test void missingCoverageGetsThreeTurnsButSkyCannotStarve(){
        for(int start:new int[]{0,17,Integer.MAX_VALUE-2}){
            int gi=0,sky=0,run=0;
            for(int i=1;i<=32;i++){
                boolean priority=RenderPipeline.prioritizeNearGi(true,start+i);
                if(priority){gi++;assertTrue(++run<=3);}else{sky++;run=0;}
            }
            assertEquals(24,gi);assertEquals(8,sky);
        }
    }
    @Test void movingCameraRequestsSkyBeforeItsCurrentRegionLosesCoverage()throws Exception {
        try(var world=new dev.jayms.World(42)){
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)world.addChunk(new dev.jayms.ChunkPos(x,4,z),new dev.jayms.Chunk());
            var sky=WorldSkyVisibility.build(world);
            assertFalse(RenderPipeline.needsSkyCoverage(sky,8,8,0,0));
            assertTrue(RenderPipeline.needsSkyCoverage(sky,8,8,.5f,0),"Complete current coverage must not hide a missing forward tile");
            assertFalse(RenderPipeline.needsSkyCoverage(sky,8,8,-.5f,0),"A covered reversal does not need speculative work");
            for(int z=-2;z<=2;z++)world.addChunk(new dev.jayms.ChunkPos(3,4,z),new dev.jayms.Chunk());
            assertFalse(RenderPipeline.needsSkyCoverage(WorldSkyVisibility.build(world),8,8,.5f,0));
            world.unloadChunk(new dev.jayms.ChunkPos(3,4,2));
            assertTrue(RenderPipeline.needsSkyCoverage(WorldSkyVisibility.build(world),8,8,.5f,0),"The forward corner is a real dependency");
        }
    }
    @Test void coverageChecksInteriorColumnsAndRebasesOnlyForLocalGain()throws Exception {
        try(var world=new dev.jayms.World(42)){
            for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)world.addChunk(new dev.jayms.ChunkPos(x,4,z),new dev.jayms.Chunk());
            var complete=WorldSkyVisibility.build(world);
            world.unloadChunk(new dev.jayms.ChunkPos(0,4,0));
            var hole=WorldSkyVisibility.build(world);
            assertTrue(RenderPipeline.needsSkyCoverage(hole,8,8,0,0),"Interior missing tile must not pass a corner-only test");
            assertTrue(RenderPipeline.shouldRetargetSky(hole,complete,8,8,1,0,false));
            assertFalse(RenderPipeline.shouldRetargetSky(complete,hole,8,8,1,0,false),"Never trade current coverage away");
            assertFalse(RenderPipeline.shouldRetargetSky(hole,complete,8,8,1,0,true),"One rebase per transfer bounds churn");
            world.addChunk(new dev.jayms.ChunkPos(0,4,0),new dev.jayms.Chunk());
            world.addChunk(new dev.jayms.ChunkPos(20,4,20),new dev.jayms.Chunk());
            assertFalse(RenderPipeline.shouldRetargetSky(complete,WorldSkyVisibility.build(world),8,8,1,0,false),"Unrelated arrivals cannot restart a transfer");
        }
    }
    @Test void missingCameraSkyCannotBePreemptedByGi(){
        for(int turn=-16;turn<=16;turn++)assertFalse(RenderPipeline.prioritizeNearGi(true,true,turn));
    }
    @Test void coveredCameraDoesNotTakePriorityFromSky(){
        for(int turn=-16;turn<=16;turn++)assertFalse(RenderPipeline.prioritizeNearGi(false,turn));
    }
}
