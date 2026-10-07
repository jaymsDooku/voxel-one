package dev.jayms;

import dev.jayms.net.city.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StressGridRoadMarkingsTest {
    @Test void implicitGridPaintStaysBoundedToDetailedRoadColumns() {
        var grid=StressGrid.standard();
        assertEquals(0,RoadMarkings.stressMesh(grid,Set.of()).indices().length);
        int cx=Math.floorDiv(StressGrid.MIN_X+1,16),cz=Math.floorDiv(StressGrid.MIN_Z+1,16);
        var data=RoadMarkings.stressMesh(grid,Set.of(new ChunkPos(cx,0,cz)));
        assertTrue(data.indices().length>0,"Implicit grid dividers survive asphalt texture mapping");
        for(int i=0;i<data.vertices().length;i+=9) {
            float x=data.vertices()[i],y=data.vertices()[i+1],z=data.vertices()[i+2];
            assertTrue(x>=cx*16&&x<=cx*16+16&&z>=cz*16&&z<=cz*16+16);
            assertTrue(x>=StressGrid.MIN_X&&z>=StressGrid.MIN_Z);
            assertEquals(grid.grade()+1.003f,y);
        }
        for(int i=0;i<data.indices().length;i+=3) {
            var v=data.vertices();var ix=data.indices();
            float x=(v[ix[i]*9]+v[ix[i+1]*9]+v[ix[i+2]*9])/3;
            float z=(v[ix[i]*9+2]+v[ix[i+1]*9+2]+v[ix[i+2]*9+2])/3;
            assertTrue(grid.road((int)Math.floor(x),(int)Math.floor(z)),"Every grid stripe triangle is on a road");
        }
        assertEquals(0,RoadMarkings.stressMesh(grid,Set.of(new ChunkPos(10000,0,10000))).indices().length);
    }
}
