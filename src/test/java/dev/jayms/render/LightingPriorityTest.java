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
    @Test void coveredCameraDoesNotTakePriorityFromSky(){
        for(int turn=-16;turn<=16;turn++)assertFalse(RenderPipeline.prioritizeNearGi(false,turn));
    }
}
