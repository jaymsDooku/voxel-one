package dev.jayms;
import dev.jayms.net.city.*;
import dev.jayms.render.MaterialTextures;
import dev.jayms.net.Blocks;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

class RoadMarkingsTest {
    @Test void diagonalPaintIsStraightContinuousAndOnRoad() {
        var city=new CityTest().simulation(new CityTest.Ground());
        assertTrue(city.command(new CityCommand(CityCommand.ROAD,1,List.of(new Polygon.Point(90,90),new Polygon.Point(110,110))),1,null).contains("built"));
        var data=RoadMarkings.mesh(city.frame());float[] v=data.vertices();int[] ix=data.indices();
        double area=0;
        for(int i=0;i<ix.length;i+=3) {
            int a=ix[i]*9,b=ix[i+1]*9,c=ix[i+2]*9;
            if(v[a]<85||v[b]<85||v[c]<85)continue;
            area+=Math.abs((v[b]-v[a])*(v[c+2]-v[a+2])-(v[c]-v[a])*(v[b+2]-v[a+2]))/2;
            float x=(v[a]+v[b]+v[c])/3,z=(v[a+2]+v[b+2]+v[c+2])/3;
            assertTrue(city.frame().roads().stream().anyMatch(r->x>=r.x()&&x<=r.x()+1&&z>=r.z()&&z<=r.z()+1));
        }
        assertEquals(Math.hypot(20,20)*.125,area,.001,"No missing or extra stripe area across block boundaries");
        for(int i=0;i<v.length;i+=9)if(v[i]>85)assertTrue(Math.abs(v[i]-v[i+2])<.09,"All paint stays on the straight diagonal");
        assertEquals(MaterialTextures.layer(Blocks.ASPHALT),MaterialTextures.layer(Blocks.ROAD_LINE_X));
        assertEquals(MaterialTextures.layer(Blocks.ASPHALT),MaterialTextures.layer(Blocks.ROAD_LINE_Z));
    }
    @Test void dirtRoadsHaveNoPaint() {
        var city=new CityTest().simulation(new CityTest.Ground());
        int before=RoadMarkings.mesh(city.frame()).indices().length;
        city.command(new CityCommand(CityCommand.ROAD,0,List.of(new Polygon.Point(90,90),new Polygon.Point(110,97))),1,null);
        assertEquals(before,RoadMarkings.mesh(city.frame()).indices().length);
    }
    @Test void restartAndDeletionRefreshPaint() throws Exception {
        var ground=new CityTest.Ground();var city=new CityTest().simulation(ground);
        city.command(new CityCommand(CityCommand.ROAD,3,List.of(new Polygon.Point(90,90),new Polygon.Point(110,97))),1,null);
        var original=RoadMarkings.mesh(city.frame());
        var save=java.nio.file.Files.createTempFile("painted-road", ".dat");
        try {
            city.save(save);
            var restored=new CitySimulation(city.frame().config(),ground,ground.terrain,CitySimulation.load(save));
            assertArrayEquals(original.vertices(),RoadMarkings.mesh(restored.frame()).vertices());
            assertArrayEquals(original.indices(),RoadMarkings.mesh(restored.frame()).indices());
            int id=restored.frame().addresses().nearest(100,94).id();
            assertEquals("Road section deleted",restored.command(new CityCommand(CityCommand.DELETE_ROAD,id,List.of()),1,null));
            assertTrue(RoadMarkings.mesh(restored.frame()).vertices().length<original.vertices().length);
            var remaining=RoadMarkings.mesh(restored.frame()).vertices();
            for(int i=0;i<remaining.length;i+=9)
                assertTrue(remaining[i]<85,"No stale deleted paint");
        } finally { java.nio.file.Files.deleteIfExists(save); }
    }
    @Test void allLaneCountsKeepPaintAreaAtShallowSteepAndReverseHeadings() {
        for(int type=1;type<=3;type++)for(var end:List.of(new Polygon.Point(110,97),new Polygon.Point(97,110),new Polygon.Point(90,90))) {
            var city=new CityTest().simulation(new CityTest.Ground());
            var start=end.x()==90?new Polygon.Point(110,110):new Polygon.Point(90,90);
            assertTrue(city.command(new CityCommand(CityCommand.ROAD,type,List.of(start,end)),1,null).contains("built"));
            var data=RoadMarkings.mesh(city.frame());var v=data.vertices();var ix=data.indices();double area=0;
            for(int j=0;j<ix.length;j+=3){int a=ix[j]*9,b=ix[j+1]*9,c=ix[j+2]*9;
                if(v[a]<85||v[b]<85||v[c]<85)continue;
                area+=Math.abs((v[b]-v[a])*(v[c+2]-v[a+2])-(v[c]-v[a])*(v[b+2]-v[a+2]))/2;
            }
            double length=Math.hypot(end.x()-start.x(),end.z()-start.z());
            assertTrue(area<=type*length*.125+.01,"Paint is clipped at road end caps");
            float dx=end.x()-start.x(),dz=end.z()-start.z();
            float spacing=(float)(Math.max(Math.abs(dx),Math.abs(dz))/length);
            int radius=RoadTypes.width(type)/2;
            for(int lane=-radius+1;lane<radius;lane+=2)for(int sample=5;sample<=95;sample++) {
                float t=sample/100f;
                float x=start.x()+.5f+dx*t-(float)(dz/length)*lane*spacing;
                float z=start.z()+.5f+dz*t+(float)(dx/length)*lane*spacing;
                boolean covered=false;
                for(int j=0;j<ix.length;j+=3) {
                    int a=ix[j]*9,b=ix[j+1]*9,c=ix[j+2]*9;
                    float one=(v[b]-v[a])*(z-v[a+2])-(v[b+2]-v[a+2])*(x-v[a]);
                    float two=(v[c]-v[b])*(z-v[b+2])-(v[c+2]-v[b+2])*(x-v[b]);
                    float three=(v[a]-v[c])*(z-v[c+2])-(v[a+2]-v[c+2])*(x-v[c]);
                    float cross=(v[b]-v[a])*(v[c+2]-v[a+2])-(v[b+2]-v[a+2])*(v[c]-v[a]);
                    if(Math.abs(cross)>1e-8f && (one<=.0001f&&two<=.0001f&&three<=.0001f || one>=-.0001f&&two>=-.0001f&&three>=-.0001f)) {covered=true;break;}
                }
                assertTrue(covered,"No interior stripe gap: type "+type+" lane "+lane+" sample "+sample);
            }
        }
    }
}
