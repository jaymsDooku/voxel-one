package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AviationTest {
    static class Ground implements CitySimulation.Ground {
        final int grade; int batches; boolean occupied;
        final Map<List<Integer>,Integer> blocks=new HashMap<>();
        Ground(int grade){this.grade=grade;}
        public int type(int x,int y,int z){return blocks.getOrDefault(List.of(x,y,z),y<=grade?Blocks.DIRT:0);}
        public boolean occupied(int x,int y,int z,int w,int d){return occupied;}
        public void apply(List<Protocol.Edit> edits){batches++;for(var e:edits){assertTrue(e.valid());blocks.put(List.of(e.x(),e.y(),e.z()),e.type());}}
    }
    record Fixture(CitySimulation city, Ground ground, Terrain terrain) {}
    static Fixture fixture() {
        var terrain=new Terrain(Terrain.DEFAULT_SEED);int grade=terrain.column(8,24).height();var ground=new Ground(grade);
        var config=GameConfig.cityGame();var seed=new CitySimulation(config,ground,terrain,null).frame();
        ground.blocks.clear();ground.batches=0;
        var roads=new ArrayList<CityFrame.Road>();for(int x=40;x<=170;x++)roads.add(new CityFrame.Road(x,50,grade));
        var citizens=new ArrayList<CityFrame.Citizen>();for(var c:seed.citizens())
            citizens.add(new CityFrame.Citizen(c.id(),c.name(),c.cohort(),52.5f,grade+1.01f,50.5f,0,0,90,500,0,0,0,"Ready"));
        return new Fixture(new CitySimulation(config,ground,terrain,new CityFrame(config,0,roads,List.of(),List.of(),citizens,List.of(),seed.economy())),ground,terrain);
    }
    static CityCommand permit(int x){return new CityCommand(CityCommand.SPECIAL,SpecialBuildings.AIRPORT,List.of(new Polygon.Point(x,52)),0,0);}
    static String command(CitySimulation c,CityCommand cmd){return c.command(cmd,1,null);}
    static CityFrame roundtrip(CityFrame f) throws Exception {
        var out=new ByteArrayOutputStream();f.write(new DataOutputStream(out));
        return CityFrame.read(new DataInputStream(new ByteArrayInputStream(out.toByteArray())));
    }
    @Test void permitsExpansionProtectionAndTreasuryAreAtomic() throws Exception {
        var f=fixture();var c=f.city();double money=c.economy.budget;
        assertEquals("Permitted Airport with 1 runway",command(c,permit(50)));
        var a=c.frame().buildings().get(0);assertEquals(money-2000,c.economy.budget);
        assertTrue(command(c,new CityCommand(CityCommand.FLIGHT,a.id(),List.of(),0,c.frame().citizens().get(0).id())).contains("second airport"));
        int batches=f.ground().batches;
        f.ground().occupied=true;
        assertTrue(command(c,new CityCommand(CityCommand.RUNWAY,a.id(),List.of())).contains("player"));
        assertEquals(batches,f.ground().batches);assertEquals(money-2000,c.economy.budget);
        f.ground().occupied=false;
        assertEquals("Airport expanded to 2 runways",command(c,new CityCommand(CityCommand.RUNWAY,a.id(),List.of())));
        assertEquals("Airport expanded to 3 runways",command(c,new CityCommand(CityCommand.RUNWAY,a.id(),List.of())));
        assertEquals("Airport already has 3 runways",command(c,new CityCommand(CityCommand.RUNWAY,a.id(),List.of())));
        assertEquals(money-4000,c.economy.budget);
        assertTrue(command(c,new CityCommand(CityCommand.ROAD,0,List.of(new Polygon.Point(60,80),new Polygon.Point(75,80)))).contains("special building"));
        assertEquals(c.frame().buildings(),roundtrip(c.frame()).buildings());
        c.economy.budget=0;int count=c.frame().buildings().size();
        assertTrue(command(c,permit(130)).contains("Treasury"));assertEquals(count,c.frame().buildings().size());
        assertEquals("Building demolished; zoned land can redevelop (no material refund)",command(c,new CityCommand(CityCommand.DEMOLISH,a.id(),List.of())));
        assertEquals(0,f.ground().type(60,f.ground().grade+1,80));
    }
    @Test void passengerWalksBoardsFliesLandsAndRestoresMidFlight() throws Exception {
        var f=fixture();var c=f.city();command(c,permit(50));command(c,permit(130));
        int person=c.frame().citizens().get(0).id();int dest=c.frame().buildings().get(1).id();
        assertTrue(command(c,new CityCommand(CityCommand.FLIGHT,dest,List.of(),0,person)).startsWith("Flight booked"));
        assertEquals("Citizen already has a flight",command(c,new CityCommand(CityCommand.FLIGHT,dest,List.of(),0,person)));
        assertEquals("Destination runways are busy",command(c,new CityCommand(CityCommand.FLIGHT,dest,List.of(),0,c.frame().citizens().get(1).id())));
        boolean boarding=false,flying=false,arrived=false,restored=false;
        for(int i=0;i<1500;i++) {
            c.advance(.1);var citizen=c.frame().citizens().stream().filter(p->p.id()==person).findFirst().orElseThrow();
            boarding|=citizen.activity().startsWith("Boarding");flying|=citizen.activity().startsWith("Flying");
            if(flying&&!restored){var snapshot=roundtrip(c.frame());assertEquals(c.frame().aviation(),snapshot.aviation());c=new CitySimulation(snapshot.config(),f.ground(),f.terrain(),snapshot);restored=true;}
            if(citizen.activity().startsWith("Arrived")){assertTrue(citizen.x()>130&&citizen.x()<136);arrived=true;}
            if(arrived&&c.frame().aviation().flights().isEmpty())break;
        }
        assertTrue(boarding);assertTrue(flying);assertTrue(restored);assertTrue(arrived);assertTrue(c.frame().aviation().flights().isEmpty());
    }
    @Test void expandedRunwaysReserveDistinctLanesAndDemolitionReleasesPassengers() throws Exception {
        var f=fixture();var c=f.city();command(c,permit(50));command(c,permit(130));
        int a=c.frame().buildings().get(0).id(),b=c.frame().buildings().get(1).id();
        command(c,new CityCommand(CityCommand.RUNWAY,a,List.of()));command(c,new CityCommand(CityCommand.RUNWAY,b,List.of()));
        for(var citizen:c.frame().citizens().subList(0,2)) assertTrue(command(c,new CityCommand(CityCommand.FLIGHT,b,List.of(),0,citizen.id())).startsWith("Flight booked"));
        var flights=c.frame().aviation().flights();assertEquals(2,flights.size());
        assertNotEquals(flights.get(0).originRunway(),flights.get(1).originRunway());
        assertNotEquals(flights.get(0).destinationRunway(),flights.get(1).destinationRunway());
        assertEquals(c.frame().aviation(),roundtrip(c.frame()).aviation());
        assertTrue(command(c,new CityCommand(CityCommand.DEMOLISH,a,List.of())).startsWith("Building demolished"));
        assertTrue(c.frame().aviation().flights().isEmpty());
        for(var flight:flights) {
            var person=c.frame().citizens().stream().filter(v->v.id()==flight.citizen()).findFirst().orElseThrow();
            assertTrue(person.x()>130 && person.x()<136);assertEquals(0,person.horse());
        }
        assertEquals(c.frame().buildings(),roundtrip(c.frame()).buildings());
    }

    @Test void takeoffAndLandingStayOverRunwaysAndCruiseClearsMountains() {
        for(int step=0;step<=1000;step++) {
            double t=step/1000.0;
            var p=FlightPath.sample(58,28,70,428,28,70,t);
            if(t<.2) { assertTrue(p.x()>=58 && p.x()<=68);assertEquals(70,p.z()); }
            else if(t>.8) { assertTrue(p.x()>=418 && p.x()<=428);assertEquals(70,p.z()); }
            else assertTrue(p.y()>Terrain.MAX_Y+10);
        }
    }

    @Test void blockedWalkCancelsAndInvalidReservationsAreRejected() throws Exception {
        var f=fixture();var c=f.city();command(c,permit(50));command(c,permit(130));
        int a=c.frame().buildings().get(0).id(),b=c.frame().buildings().get(1).id(),person=c.frame().citizens().get(0).id();
        assertTrue(command(c,new CityCommand(CityCommand.FLIGHT,b,List.of(),0,person)).startsWith("Flight booked"));
        f.ground().blocks.put(List.of(52,f.ground().grade+1,51),Blocks.STONE);
        for(int i=0;i<100;i++)c.advance(.1);
        assertTrue(c.frame().aviation().flights().isEmpty());
        var frame=c.frame();
        for(var flight:List.of(new Aviation.Flight(1,a,b,person,3,0,2,0),new Aviation.Flight(1,a,99999,person,0,0,2,0))) {
            var bad=new CityFrame(frame.config(),frame.elapsed(),frame.roads(),frame.zones(),frame.buildings(),frame.citizens(),frame.horses(),frame.economy(),frame.addresses(),frame.agriculture(),new Aviation.State(List.of(flight)));
            assertThrows(IOException.class,()->roundtrip(bad));
        }
        assertThrows(IOException.class,()->frame.write(new DataOutputStream(new ByteArrayOutputStream()),10));
    }

    @Test void diskSavePreservesBookedFlight(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var f=fixture();var c=f.city();command(c,permit(50));command(c,permit(130));
        int b=c.frame().buildings().get(1).id(),person=c.frame().citizens().get(0).id();
        command(c,new CityCommand(CityCommand.FLIGHT,b,List.of(),0,person));
        var file=directory.resolve("flight.city");c.save(file);var saved=CitySimulation.load(file);
        assertEquals(c.frame().aviation(),saved.aviation());
        var restored=new CitySimulation(saved.config(),f.ground(),f.terrain(),saved);
        boolean arrived=false;
        for(int i=0;i<1000&&!arrived;i++) {
            restored.advance(.1);
            arrived=restored.frame().citizens().stream().anyMatch(v->v.id()==person&&v.activity().startsWith("Arrived"));
        }
        assertTrue(arrived);
    }

    @Test void boardedAndArrivedJetsStayLevelOnTheRunway() {
        var f=fixture();var c=f.city();command(c,permit(50));command(c,permit(130));var frame=c.frame();
        int a=frame.buildings().get(0).id(),b=frame.buildings().get(1).id(),person=frame.citizens().get(0).id();
        for(int stage:new int[]{0,1,3}) {
            var snapshot=new CityFrame(frame.config(),frame.elapsed(),frame.roads(),frame.zones(),frame.buildings(),frame.citizens(),frame.horses(),frame.economy(),frame.addresses(),frame.agriculture(),
                    new Aviation.State(List.of(new Aviation.Flight(1,a,b,person,0,0,stage,0))));
            var jet=Aviation.planes(snapshot).stream().filter(p->p.id()==1).findFirst().orElseThrow();
            assertEquals(0,jet.pitch());assertEquals(0,jet.yaw());assertFalse(jet.airborne());
        }
    }

    @Test void compactSpecialMenuKeepsFlightChoiceAboveToolbar() {
        var tools=new dev.jayms.ui.CityTools();tools.tool=6;
        tools.click(80,427.5f,1280,640,new org.joml.Matrix4f(),new org.joml.Matrix4f(),CityFrame.empty(GameConfig.cityGame()),command -> fail("Menu should only select tool"));
        assertEquals(10,tools.tool);
    }

    @Test void oldSavesAndCommandsStayCompatibleAndCurvatureIsReal() throws Exception {
        var f=fixture();for(int version:new int[]{6,8,9,10}) {
            var bytes=new ByteArrayOutputStream();f.city().frame().write(new DataOutputStream(bytes),version);
            assertTrue(CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),version).aviation().flights().isEmpty());
        }
        var cmd=new CityCommand(CityCommand.FLIGHT,2,List.of(),0,7);var bytes=new ByteArrayOutputStream();cmd.write(new DataOutputStream(bytes));
        assertEquals(cmd,CityCommand.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        var start=FlightPath.sample(0,27,0,80,27,0,0);var end=FlightPath.sample(0,27,0,80,27,0,1);
        assertEquals(0,start.x());assertEquals(80,end.x());assertEquals(27,end.y());
        assertTrue(FlightPath.sample(0,27,0,80,27,0,.5).y()>=110);
        // Great-circle between equal northern latitudes bends poleward, unlike a map-space line.
        float r=(float)FlightPath.WORLD_RADIUS;
        var globe=FlightPath.sample(-r*.5f,0,r*.75f,r*.5f,0,r*.75f,.5);
        assertTrue(globe.z()>r*.75f+10000);
        assertTrue(Float.isFinite(FlightPath.sample(1,2,3,1,2,3,.5).yaw()));
        assertThrows(IllegalArgumentException.class,()->FlightPath.sample(Float.NaN,0,0,0,0,0,.5));
    }
}
