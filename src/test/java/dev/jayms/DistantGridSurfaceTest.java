package dev.jayms;

import dev.jayms.net.Blocks;
import dev.jayms.net.Protocol;
import dev.jayms.net.Terrain;
import dev.jayms.net.city.StressGrid;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DistantGridSurfaceTest {
    private final StressGrid grid=new StressGrid(24);
    private final Terrain terrain=new Terrain(Terrain.DEFAULT_SEED);

    @Test void allLodsUseWorldGridOnTheirFlatNaturalSurface(){
        terrain.stressGrid(grid);
        var mesher=new DistantTerrainMesher(terrain,List.of());
        for(int size=16;size<=DistantTerrainPlan.ROOT_SIZE;size*=2){
            var tile=new DistantTerrainPlan.Tile(-size,-size,size);
            var mesh=mesher.build(tile);
            // Cross a centerline, the pavement beside it, and the adjoining plot.
            for(int x=-15;x<0;x++) assertEquals(-1,topColor(mesh,tile,x,-7,grid.grade()+1));
        }
    }

    @Test void authoredSurfacesKeepTheirExactFootprintAcrossLods(){
        terrain.stressGrid(grid);
        var mesher=new DistantTerrainMesher(terrain,List.of(
                new Protocol.Edit(-3,24,-2,Blocks.STONE),new Protocol.Edit(-2,24,-2,Blocks.SNOW),
                new Protocol.Edit(-1,24,-2,Blocks.AIR)));
        for(int size:new int[]{16,64,256,1024}){
            var tile=new DistantTerrainPlan.Tile(-size,-size,size);
            var mesh=mesher.build(tile);
            assertEquals(Blocks.color(Blocks.STONE)[0],topColor(mesh,tile,-3,-2,25));
            assertEquals(Blocks.color(Blocks.SNOW)[0],topColor(mesh,tile,-2,-2,25));
            assertEquals(Blocks.color(Blocks.DIRT)[0],topColor(mesh,tile,-1,-2,24));
            assertEquals(-1,topColor(mesh,tile,-4,-2,25));
        }
    }

    @Test void coarseGridStopsAtTheActualFiniteEdge(){
        terrain.stressGrid(grid);
        int size=64,min=StressGrid.MIN_X,z=0;
        var tile=new DistantTerrainPlan.Tile(Math.floorDiv(min,size)*size,z,size);
        var mesh=new DistantTerrainMesher(terrain,List.of()).build(tile);
        assertEquals(-1,topColor(mesh,tile,min,z,25));
        int naturalTop=terrain.surfaceHeight(min-1,z)+1;
        assertTrue(topColor(mesh,tile,min-1,z,naturalTop)>=0);
        assertNotEquals(25,naturalTop);
        assertTrue(hasRiser(mesh,min-tile.x(),z+.5f,Math.min(naturalTop,25)+.5f));
    }

    @Test void neighboringExcavationsKeepTheirOwnDepthAndRisersInCoarseTiles(){
        terrain.stressGrid(grid);
        var edits=new java.util.ArrayList<Protocol.Edit>();
        for(int x=0;x<2;x++) for(int z=0;z<2;z++)
            edits.add(new Protocol.Edit(x,24,z,Blocks.AIR));
        edits.add(new Protocol.Edit(1,23,0,Blocks.AIR));
        var tile=new DistantTerrainPlan.Tile(0,0,64);
        var mesh=new DistantTerrainMesher(terrain,edits).build(tile);
        assertEquals(Blocks.color(Blocks.DIRT)[0],topColor(mesh,tile,0,0,24));
        assertEquals(Blocks.color(Blocks.DIRT)[0],topColor(mesh,tile,1,0,23));
        assertTrue(hasRiser(mesh,1,.5f,23.5f));
    }

    @Test void authoredAsphaltUsesTheActualRoadMipOnAndOffAnImplicitRoad(){
        terrain.stressGrid(grid);
        int rx=StressGrid.MIN_X+StressGrid.STRIDE*500,rz=StressGrid.MIN_Z+StressGrid.STRIDE*500+5;
        assertEquals(Blocks.ASPHALT,grid.surface(rx,rz));
        assertEquals(Blocks.GRASS,grid.surface(rx+4,rz));
        var mesh=new DistantTerrainMesher(terrain,List.of(new Protocol.Edit(rx,24,rz,Blocks.ASPHALT),
                new Protocol.Edit(rx+4,24,rz,Blocks.ASPHALT)));
        int size=64;
        var tile=new DistantTerrainPlan.Tile(Math.floorDiv(rx,size)*size,Math.floorDiv(rz,size)*size,size);
        var data=mesh.build(tile);
        float asphalt=Blocks.color(Blocks.ASPHALT)[0]*dev.jayms.render.MaterialTextures.meanBrightness(11);
        assertEquals(asphalt,topColor(data,tile,rx,rz,25));
        assertEquals(asphalt,topColor(data,tile,rx+4,rz,25));
        assertEquals(-1,topColor(data,tile,rx+5,rz,25));
    }

    private static boolean hasRiser(MeshData mesh,int x,float z,float y){
        float[] v=mesh.vertices();
        for(int i=0;i<v.length;i+=36){
            if(v[i]!=x || Math.abs(v[i+3])!=1)continue;
            if(y>Math.min(v[i+1],v[i+19]) && y<Math.max(v[i+1],v[i+19])
                    && z>Math.min(v[i+2],v[i+20]) && z<Math.max(v[i+2],v[i+20]))return true;
        }
        return false;
    }

    @Test void ordinaryTerrainDoesNotTagAnyGridMaterials(){
        float[] v=new DistantTerrainMesher(terrain,List.of())
                .build(new DistantTerrainPlan.Tile(0,0,64)).vertices();
        for(int i=6;i<v.length;i+=9) assertTrue(v[i]>=0);
    }

    private static float topColor(MeshData mesh,DistantTerrainPlan.Tile tile,int x,int z,int y){
        float px=x+.5f-tile.x(),pz=z+.5f-tile.z();
        float[] v=mesh.vertices();
        for(int i=0;i<v.length;i+=36){
            if(v[i+4]!=1 || v[i+1]!=y)continue;
            if(px>=Math.min(v[i],v[i+18]) && px<Math.max(v[i],v[i+18])
                    && pz>=Math.min(v[i+2],v[i+20]) && pz<Math.max(v[i+2],v[i+20]))return v[i+6];
        }
        return fail("Missing surface at "+x+", "+y+", "+z);
    }
}
