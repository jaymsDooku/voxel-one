package dev.jayms.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransportMipTest {
    static float fromHalf(int bits){
        int e=bits>>>10&31,m=bits&1023;float value=e==31?(m==0?Float.POSITIVE_INFINITY:Float.NaN):e==0?Math.scalb((float)m,-24):Math.scalb(1+m/1024f,e-15);
        return (bits&0x8000)==0?value:-value;
    }
    @Test void workerHalfPackingPreservesFiniteValuesAndRoundsTiesToEven(){
        for(int h=0;h<65536;h++){
            float value=fromHalf(h);int expected=Float.isNaN(value)?(h&0x8000)|0x7e00:h;
            assertEquals(expected,TransportField.half(value)&65535);
        }
        for(int h=0;h<0x7bff;h++){
            float midpoint=(fromHalf(h)+fromHalf(h+1))*.5f;
            int expected=(h&1)==0?h:h+1;
            assertEquals(expected,TransportField.half(midpoint)&65535);
            assertEquals(expected|0x8000,TransportField.half(-midpoint)&65535);
        }
        assertEquals(0x7c00,TransportField.half(65520f)&65535);
    }
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

    @Test void uniformRootProofSurvivesBakeScratchCleanup(){
        var plain=LightVolume.bake(0,0,0,3,3,3,1,(x,y,z)->0,true);
        assertTrue(plain.uniformRoots());
        assertTrue(java.util.Arrays.stream(plain.roots).allMatch(x->x==0));
        var tree=new dev.jayms.net.model.SparseVoxelOctree(16);
        tree.fill(0,0,0,8,8,8,dev.jayms.net.WorldVoxels.encode(dev.jayms.net.Blocks.LED));
        var fractional=LightVolume.bake(0,0,0,3,3,3,1,new LightVolume.Sampler(){
            public int value(int x,int y,int z){return x==1&&y==1&&z==1?-1:0;}
            public dev.jayms.net.model.SparseVoxelOctree detail(int x,int y,int z){return tree;}
        },true);
        assertTrue(java.util.Arrays.stream(fractional.roots).anyMatch(x->x!=0));
        assertFalse(fractional.uniformRoots(),"Released bake scratch cannot erase refined-root dependencies");
    }
}
