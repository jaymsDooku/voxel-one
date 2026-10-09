package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import dev.jayms.render.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereLightingTest {
    private LightVolume room(boolean window,boolean led){
        return LightVolume.bake(0,0,0,9,9,9,1,(x,y,z)->{
            if(x>=1&&x<=7&&y>=1&&y<=7&&z>=1&&z<=7&&(x==1||x==7||y==1||y==7||z==1||z==7)){if(window&&x==1&&y==4&&z==4)return 0;return WorldVoxels.encode(Blocks.STONE);}
            return led&&x==4&&y==4&&z==4?0xfeff0000:0;
        },true);
    }
    @Test void separateSkyKeepsVisibilityButRemovesFixedOutdoorColour(){
        var outdoor=LightVolume.bake(0,0,0,5,5,5,1,(x,y,z)->0,true).sampleLighting(2.5f,2.5f,2.5f);
        assertEquals(0,outdoor[0]);assertEquals(0,outdoor[1]);assertEquals(0,outdoor[2]);assertEquals(1,outdoor[3]);
        var closed=room(false,false).sampleLighting(3.5f,4.5f,4.5f);assertEquals(0,closed[3]);assertEquals(0,closed[0]);
        var open=room(true,false).sampleLighting(3.5f,4.5f,4.5f);assertTrue(open[3]>0);assertEquals(0,open[0]);
    }
    @Test void ledSurvivesAndNormalSolidsDoNotEmitSky(){
        var volume=room(false,true);assertTrue(volume.sampleLighting(3.5f,4.5f,4.5f)[0]>.2);assertEquals(0,volume.sampleLighting(3.5f,4.5f,4.5f)[3]);
        int stone=1+4*9+4*81,led=4+4*9+4*81;
        assertEquals(0,volume.transport.radiance[stone*4]);assertTrue(volume.transport.radiance[led*4]>3);
    }
    @Test void separateSkyRespectsFineOpaqueAndGlassRoofs(){
        for(int resolution:new int[]{16,32})for(int material:new int[]{Blocks.STONE,Blocks.GLASS}){
            var roof=new SparseVoxelOctree(resolution);
            roof.fill(0,resolution/2,0,resolution,resolution/2+1,resolution,WorldVoxels.encode(material));
            var volume=LightVolume.bake(0,0,0,5,7,5,1,new LightVolume.Sampler(){
                public int value(int x,int y,int z){return x==0||x==4||z==0||z==4||y==0?WorldVoxels.encode(Blocks.STONE):y==4?-1:0;}
                public SparseVoxelOctree detail(int x,int y,int z){return roof;}
            },true);
            assertTrue(volume.sampleLighting(2.5f,4.8f,2.5f)[3]>.9f);
            var below=volume.sampleLighting(2.5f,4.25f,2.5f);
            assertEquals(0,below[0]);assertEquals(0,below[1]);assertEquals(0,below[2]);
            if(material==Blocks.STONE)assertTrue(below[3]<.04f,"Fine opaque roof blocks outdoor atmosphere");
            else assertTrue(below[3]>.3f,"Fine glass transmits sky visibility");
        }
    }
}
