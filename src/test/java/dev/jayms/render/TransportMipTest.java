package dev.jayms.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransportMipTest {
    @Test void preservesConstantRadianceThroughOddAndUnitAxes(){
        float[] input=new float[5*3*4];java.util.Arrays.fill(input,2.5f);
        var levels=TransportField.mipmaps(5,3,1,input);
        assertEquals(3,levels.length);assertEquals(4,levels[2].length);
        for(var level:levels)for(float value:level)assertEquals(2.5f,value,.00001f);
    }
    @Test void filtersAllEightRadianceSamplesAndKeepsBaseUnchanged(){
        float[] input=new float[2*2*2*4];for(int i=0;i<8;i++)for(int c=0;c<4;c++)input[i*4+c]=i;
        var levels=TransportField.mipmaps(2,2,2,input);
        assertSame(input,levels[0]);for(float value:levels[1])assertEquals(3.5f,value,.00001f);
    }
    @Test void oddAxesUseLinearDestinationCenters(){
        float[] input=new float[5*4];for(int i=0;i<5;i++)for(int c=0;c<4;c++)input[i*4+c]=i;
        var levels=TransportField.mipmaps(5,1,1,input);
        assertEquals(.75f,levels[1][0],.00001f);assertEquals(3.25f,levels[1][4],.00001f);assertEquals(2,levels[2][0],.00001f);
    }
}
