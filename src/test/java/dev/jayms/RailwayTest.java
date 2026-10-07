package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class RailwayTest {
    public static class Ground implements CitySimulation.Ground {
        public final int grade;
        public boolean occupied;
        public final Map<List<Integer>,Integer> blocks=new HashMap<>();
        public Ground(int grade){this.grade=grade;}
        public int type(int x,int y,int z){return blocks.getOrDefault(List.of(x,y,z),y<=grade?Blocks.DIRT:0);}
        public void apply(List<Protocol.Edit> edits){for(var e:edits){assertTrue(e.valid());blocks.put(List.of(e.x(),e.y(),e.z()),e.type());}}
        public boolean occupied(int x,int y,int z,int w,int d){return occupied;}
    }
    public static CityCommand rail(int x,int z,int bx,int bz){return new CityCommand(CityCommand.RAIL,0,List.of(new Polygon.Point(x,z),new Polygon.Point(bx,bz)));}
    public static CityCommand permit(int type,int x,int z){return new CityCommand(CityCommand.SPECIAL,type,List.of(new Polygon.Point(x,z)));}
    public static CitySimulation fixture(Ground ground) {
        var config=GameConfig.cityGame();var roads=new ArrayList<CityFrame.Road>();
        for(int x=48;x<132;x++)roads.add(new CityFrame.Road(x,50,ground.grade));
        return new CitySimulation(config,ground,new Terrain(Terrain.DEFAULT_SEED),new CityFrame(config,0,roads,List.of(),List.of(),List.of(),List.of()));
    }
    public static void build(CitySimulation city) {
        assertTrue(city.command(rail(48,60,132,60),1,null).startsWith("Rail built"));
        assertEquals("Permitted Rail station",city.command(permit(SpecialBuildings.RAIL_STATION,50,52),1,null));
        assertEquals("Permitted Rail station",city.command(permit(SpecialBuildings.RAIL_STATION,90,52),1,null));
        assertEquals("Permitted Rail depot",city.command(permit(SpecialBuildings.RAIL_DEPOT,120,52),1,null));
    }
    static Ground ground(){var t=new Terrain(Terrain.DEFAULT_SEED);return new Ground(t.column(8,24).height());}
    @Test void placementIsAtomicBudgetedAndProtectsRoadsZonesBuildingsPlayers() {
        var g=ground();var city=fixture(g);
        assertTrue(city.command(permit(SpecialBuildings.RAIL_STATION,50,52),1,null).contains("rear dock"));
        build(city);var before=city.frame();
        assertTrue(city.command(rail(48,60,132,60),1,null).contains("$0"));
        assertEquals(before.economy().roadSpending(),city.frame().economy().roadSpending());
        assertTrue(city.command(rail(48,50,60,50),1,null).contains("roads"));
        assertTrue(city.command(rail(50,53,56,53),1,null).contains("buildings"));
        assertTrue(city.command(new CityCommand(CityCommand.ROAD,0,List.of(new Polygon.Point(48,60),new Polygon.Point(60,60))),1,null).contains("rails"));
        g.occupied=true;assertTrue(city.command(rail(48,62,50,62),1,null).contains("player"));g.occupied=false;
        g.blocks.put(List.of(50,g.grade+1,62),Blocks.WOOD);
        assertTrue(city.command(rail(48,62,52,62),1,null).contains("Clear"));
        assertEquals(before.railway().tracks(),city.frame().railway().tracks());
        assertTrue(city.command(rail(-200,62,200,62),1,null).contains("too long"));
        assertTrue(city.command(new CityCommand(CityCommand.ROAD,0,List.of(new Polygon.Point(48,64),new Polygon.Point(52,64))),1,null).startsWith("Dirt road built"));
    }
    @Test void trainFollowsConnectedTrackBoardsAndReturnsToDepot(@TempDir Path dir) throws Exception {
        var city=fixture(ground());build(city);city.advance(.2);
        var state=city.frame().railway();assertEquals(1,state.trains().size());assertTrue(state.trains().get(0).dwell()>0);
        var railway=new Railway(state);var people=new HashSet<>(List.of(7));var arrivals=new ArrayList<String>();
        var riders=new Railway.Riders(){public boolean exists(int id){return people.contains(id);}public void move(int id,float x,float y,float z,String activity,boolean arrived){if(arrived)arrivals.add(activity);}};
        boolean boarded=false,moved=false,returned=false;
        for(int i=0;i<1600;i++) {
            railway.tick(.1f,city.frame().buildings(),riders);var train=railway.state().trains().get(0);
            assertTrue(railway.contains((int)Math.floor(train.x()),(int)Math.floor(train.z())));
            moved|=train.x()<120;
            if(!boarded&&train.stop()==1&&train.dwell()>0)boarded=railway.board(7,1,2);
            if(boarded&&!arrivals.isEmpty()&&train.stop()==3&&train.dwell()>0){returned=true;break;}
        }
        assertTrue(moved);assertTrue(boarded);assertEquals(List.of("Left train"),arrivals);assertTrue(returned);
        city.save(dir.resolve("city.dat"));var loaded=CitySimulation.load(dir.resolve("city.dat"));assertEquals(city.frame().railway(),loaded.railway());
        var bytes=new ByteArrayOutputStream();city.frame().write(new DataOutputStream(bytes));assertEquals(city.frame().railway(),CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))).railway());
        assertThrows(IOException.class, () -> city.frame().write(new DataOutputStream(new ByteArrayOutputStream()),9));
        // A legacy snapshot without rail building types remains readable.
        var legacy=fixture(ground()).frame();bytes.reset();legacy.write(new DataOutputStream(bytes),9);
        assertEquals(Railway.State.empty(),CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),9).railway());
    }
    @Test void trainsTurnAtCardinalCornersAndRejectInvalidBoarding() {
        int grade=ground().grade;
        var buildings=List.of(new CityFrame.Building(1,0,SpecialBuildings.RAIL_STATION,0,grade+1,0,8,0),
                new CityFrame.Building(2,0,SpecialBuildings.RAIL_STATION,20,grade+1,0,8,0),new CityFrame.Building(3,0,SpecialBuildings.RAIL_DEPOT,20,grade+1,20,8,0));
        var tracks=new ArrayList<Railway.Track>();for(int x=2;x<=22;x++)tracks.add(new Railway.Track(x,8,grade));
        for(int z=9;z<=28;z++)tracks.add(new Railway.Track(22,z,grade));
        var railway=new Railway(new Railway.State(tracks,List.of()));
        var riders=new Railway.Riders(){public boolean exists(int id){return true;}public void move(int id,float x,float y,float z,String activity,boolean arrived){}};
        boolean horizontal=false,vertical=false,returned=false;
        for(int i=0;i<800;i++) {
            railway.tick(.1f,buildings,riders);var train=railway.state().trains().get(0);
            assertTrue(railway.contains((int)Math.floor(train.x()),(int)Math.floor(train.z())));
            horizontal|=Math.abs(train.yaw())==90;vertical|=Math.abs(train.yaw())==180||train.yaw()==0&&train.dwell()==0;
            assertFalse(railway.board(7,1,999));assertFalse(railway.board(7,1,1));
            if(horizontal&&vertical&&train.stop()==3&&train.dwell()>0){returned=true;break;}
        }
        assertTrue(horizontal);assertTrue(vertical);assertTrue(returned);
    }
    @Test void disconnectedDepotKeepsTrainStoredAndRemovedDepotReleasesTrain() {
        var city=fixture(ground());build(city);city.advance(.2);var s=city.frame();
        var tracks=s.railway().tracks().stream().filter(t->t.x()>100).toList();
        var railway=new Railway(new Railway.State(tracks,s.railway().trains()));
        var riders=new Railway.Riders(){public boolean exists(int id){return true;}public void move(int id,float x,float y,float z,String activity,boolean arrived){}};
        for(int i=0;i<200;i++)railway.tick(.1f,s.buildings(),riders);
        var train=railway.state().trains().get(0);assertEquals(train.depot(),train.stop());assertEquals(10,train.dwell());
        railway.tick(.1f,s.buildings().stream().filter(b->b.type()!=SpecialBuildings.RAIL_DEPOT).toList(),riders);assertTrue(railway.state().trains().isEmpty());
    }
    @Test void citizenWalksBoardsAndCompletesRailJourney() {
        var g=ground();var built=fixture(g);build(built);var seed=built.frame();
        var buildings=new ArrayList<>(seed.buildings());buildings.add(new CityFrame.Building(4,1,0,90,g.grade+1,66,4,0));
        var citizen=new CityFrame.Citizen(7,"Rail test citizen",0,52.5f,g.grade+2,54.5f,0,0,90,20,4,0,0,"Going home");
        var frame=new CityFrame(seed.config(),seed.elapsed(),seed.roads(),seed.zones(),buildings,List.of(citizen),seed.horses(),seed.economy(),seed.addresses(),seed.agriculture(),seed.railway());
        var city=new CitySimulation(new GameConfig(true,false,1200,20),g,new Terrain(Terrain.DEFAULT_SEED),frame);
        boolean waiting=false,riding=false,arrived=false;
        for(int i=0;i<1800;i++){city.advance(.1);var c=city.frame().citizens().get(0);waiting|=c.activity().contains("rail station");riding|=c.activity().equals("Riding steam train");arrived|=riding&&c.x()>85&&!city.frame().railway().trains().get(0).passengers().stream().anyMatch(p->p.citizen()==7);if(arrived)break;}
        assertTrue(waiting,"Citizen waits at station: "+city.frame().citizens()+" trains="+city.frame().railway().trains());assertTrue(riding,"Citizen rides: "+city.frame().citizens());assertTrue(arrived,"Citizen leaves at destination station");
    }
}
