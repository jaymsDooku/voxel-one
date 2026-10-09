package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.render.WorldSkyVisibility;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldSkyVisibilityTest {
    @Test void visibleColumnsBeyondGiAndAtAltitudeHaveSkyButRoofsStayClosed()throws Exception{
        try(var world=new World()){
            for(int cx:new int[]{-5,0,4})for(int cy:new int[]{1,8}){
                var chunk=new Chunk();for(int x=0;x<16;x++)for(int z=0;z<16;z++)chunk.setBlock(x,8,z,Blocks.STONE);
                world.addChunk(new ChunkPos(cx,cy,0),chunk);
            }
            var sky=WorldSkyVisibility.build(world);
            for(float x:new float[]{-72.5f,8.5f,72.5f}){
                assertEquals(1,sky.sample(x,137.001f,8.5f));
                assertEquals(0,sky.sample(x,136.5f,8.5f));
                assertEquals(0,sky.sample(x,25.001f,8.5f),"High roof blocks lower receiver even outside GI");
            }
            assertEquals(0,sky.sample(999,150,999),"Unknown columns conservative");
        }
    }
    @Test void fineOpaqueFootprintsAndGlassRemainExactBeyondGi()throws Exception{
        try(var world=new World()){
            world.addChunk(new ChunkPos(4,8,0),new Chunk());
            // One 1/16 leaf over the test ray; adjacent micro-rays remain clear.
            world.apply(new Protocol.Edit(72,140,8,Blocks.STONE,4,0,8,0));
            world.apply(new Protocol.Edit(73,140,8,Blocks.GLASS));
            var sky=WorldSkyVisibility.build(world);
            assertEquals(0,sky.sample(72.01f,139,8.01f));assertEquals(1,sky.sample(72.2f,139,8.2f));
            assertEquals(.7f,sky.sample(73.5f,139,8.5f),.00001);
            assertEquals(1,sky.sample(72.01f,141,8.01f));
        }
    }
    @Test void modelCellsRepeatAndTinyRoofsBlockTheirExactFootprint()throws Exception{
        try(var world=new World()){
            var geometry=new dev.jayms.net.model.SparseVoxelOctree(32);geometry.fill(0,16,0,32,17,32,0xff8899aa);
            var model=world.models().register(new dev.jayms.net.model.ModelDefinition("Sky test roof",geometry),"synthetic");
            var chunk=new Chunk();for(int x=4;x<6;x++)for(int y=4;y<6;y++)for(int z=4;z<6;z++)chunk.setBlock(x,y,z,model.id());
            world.addChunk(new ChunkPos(4,8,0),chunk);var sky=WorldSkyVisibility.build(world);
            assertEquals(0,sky.sample(68.5f,131,4.5f));assertEquals(0,sky.sample(69.5f,131,5.5f));assertEquals(1,sky.sample(69.5f,134,5.5f));
        }
    }
    @Test void roofAboveLocalVolumeOccludesItsTopSkySeed()throws Exception{
        try(var world=new World()){
            var chunk=new Chunk();for(int x=0;x<16;x++)for(int z=0;z<16;z++)chunk.setBlock(x,10,z,Blocks.STONE);world.addChunk(new ChunkPos(4,8,0),chunk);
            var sky=WorldSkyVisibility.build(world);
            var volume=dev.jayms.render.LightVolume.bake(68,80,4,9,16,9,1,new dev.jayms.render.LightVolume.Sampler(){
                public int value(int x,int y,int z){return x==68||x==76||z==4||z==12||y==80?WorldVoxels.encode(Blocks.STONE):0;}
                public float skyVisibility(float x,float y,float z){return sky.sample(x,y,z);}
            },true);
            assertEquals(0,volume.sampleLighting(72.5f,85,8.5f)[3]);
        }
    }
    @Test void complexColumnOverflowCannotOpenRoofsOrDarkenSkyAboveThem()throws Exception{
        try(var world=new World()){
            world.addChunk(new ChunkPos(4,0,0),new Chunk());
            for(int i=0;i<130;i++)world.apply(new Protocol.Edit(72,1000+i*2,8,Blocks.STONE));
            var sky=WorldSkyVisibility.build(world);assertEquals(0,sky.sample(72.5f,990,8.5f));assertEquals(1,sky.sample(72.5f,1300,8.5f));
        }
    }
    @Test void coverageRevisionTracksWholeAndFineEditsButNotMeshInvalidation(){
        var chunk=new Chunk();long initial=chunk.geometryVersion();chunk.markDirty();assertEquals(initial,chunk.geometryVersion());
        chunk.setBlock(1,2,3,Blocks.STONE);assertEquals(initial+1,chunk.geometryVersion());
        chunk.apply(new Protocol.Edit(1,2,3,Blocks.GLASS,4,0,0,0));assertEquals(initial+2,chunk.geometryVersion());
    }
    @Test void unloadedEditedRoofsStillBlockLoadedReceivers()throws Exception{
        try(var world=new World()){
            world.addChunk(new ChunkPos(4,8,0),new Chunk());world.apply(new Protocol.Edit(72,180,8,Blocks.STONE));
            assertEquals(0,WorldSkyVisibility.build(world).sample(72.5f,140,8.5f));
        }
    }
    @Test void modelRoofGapsSurviveUnloadAndReload()throws Exception{
        try(var world=new World()){
            var geometry=new dev.jayms.net.model.SparseVoxelOctree(32);
            geometry.fill(0,16,0,16,17,16,0xff8899aa);
            int type=world.models().register(new dev.jayms.net.model.ModelDefinition("Quarter roof",geometry),"synthetic").id();
            var position=new ChunkPos(4,11,2);world.addChunk(position,new Chunk());
            world.apply(new Protocol.Edit(70,180,45,type));
            for(int state=0;state<3;state++){
                if(state==1){world.unloadChunk(position);}
                if(state==2)world.addChunk(position,new Chunk());
                assertEquals(state!=1,world.isLoaded(70,180,45));
                var sky=WorldSkyVisibility.build(world);
                assertEquals(1,sky.sample(70.8f,170,45.8f),"Model gap state="+state);
                assertEquals(0,sky.sample(70.2f,170,45.2f),"Opaque model footprint state="+state);
                assertEquals(1,sky.sample(70.2f,181,45.2f),"Above roof state="+state);
            }
        }
    }

}
