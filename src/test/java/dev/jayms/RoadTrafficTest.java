package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.city.*;
import dev.jayms.net.city.Polygon.*;
import dev.jayms.net.city.RoadTraffic.Waypoint;

import org.junit.jupiter.api.Test;

import java.util.*;

class RoadTrafficTest {
    RoadTraffic straight() {
        return new RoadTraffic(
                List.of(
                        new CityAddresses.Street(
                                1, "Test", List.of(new Point(0, 24), new Point(5, 24)))));
    }

    @Test
    void opposingLanesAndPavements() {
        var road = straight();
        var forward = List.of(new Cell(0, 24), new Cell(1, 24), new Cell(2, 24));
        var reverse = new ArrayList<>(forward);
        Collections.reverse(reverse);
        assertEquals(25f, road.lanes(forward, true).get(1).z(), .001);
        assertEquals(24f, road.lanes(reverse, true).get(1).z(), .001);
        assertEquals(25.75f, road.lanes(forward, false).get(1).z(), .001);
        assertEquals(23.25f, road.lanes(reverse, false).get(1).z(), .001);
        assertFalse(RoadTraffic.blocks(0, 25f, 1, 25f, .5f, 24f, .65f));
    }

    @Test
    void bendsAndIntersectionConnectivity() {
        var road =
                new RoadTraffic(
                        List.of(
                                new CityAddresses.Street(
                                        1,
                                        "Bend",
                                        List.of(
                                                new Point(0, 24),
                                                new Point(2, 24),
                                                new Point(2, 27))),
                                new CityAddresses.Street(
                                        2, "Cross", List.of(new Point(0, 26), new Point(4, 26)))));
        assertTrue(road.connected(new Cell(2, 26), new Cell(3, 26)));
        assertTrue(road.connected(new Cell(2, 26), new Cell(2, 27)));
        assertFalse(road.connected(new Cell(1, 24), new Cell(1, 25)));
        assertEquals(
                List.of(
                        new Waypoint(.5f, 25.75f), new Waypoint(1.25f, 25.75f),
                        new Waypoint(1.25f, 26.5f), new Waypoint(1.25f, 27.5f)),
                road.lanes(
                        List.of(
                                new Cell(0, 24),
                                new Cell(1, 24),
                                new Cell(2, 24),
                                new Cell(2, 25),
                                new Cell(2, 26),
                                new Cell(2, 27)),
                        false));
        assertEquals(
                List.of(new Waypoint(1.25f, 25.75f)),
                road.lanes(List.of(new Cell(1, 24), new Cell(2, 24), new Cell(2, 25)), false));
    }

    @Test
    void lanePositionsRemainValidAtConstructionBoundary() {
        var road =
                new RoadTraffic(
                        List.of(
                                new CityAddresses.Street(
                                        1,
                                        "Edge",
                                        List.of(new Point(263, 24), new Point(263, 30)))));
        assertEquals(
                264.75f,
                road.lanes(List.of(new Cell(263, 25), new Cell(263, 24)), false).get(0).x());
    }

    @Test
    void buildingOnStoneTerrainStillCreatesStonePavements() {
        var ground = new CityTest.Ground();
        var city = new CityTest().simulation(ground);
        int y = city.frame().roads().get(0).y();
        var terrain = new ArrayList<dev.jayms.net.Protocol.Edit>();
        for (int x = 99; x <= 104; x++)
            for (int z = 99; z <= 101; z++)
                terrain.add(new dev.jayms.net.Protocol.Edit(x, y, z, dev.jayms.net.Blocks.STONE));
        ground.apply(terrain);
        String result =
                city.command(
                        new CityCommand(
                                CityCommand.ROAD,
                                0,
                                List.of(new Point(100, 100), new Point(103, 100))),
                        1,
                        new dev.jayms.net.Protocol.Pose(1, 8, 40, 24, 0, 0));
        assertTrue(result.startsWith("Dirt road built"), result);
        assertEquals(dev.jayms.net.Blocks.DIRT, ground.type(101, y, 100));
        assertPavement(ground, 101, y, 101);
        assertTrue(city.route(0, 24, 101, 100).isEmpty());
    }

    @Test
    void sweptSpacingStopsOvertakingAndAllowsRelease() {
        assertTrue(RoadTraffic.blocks(0, 0, 3, 0, 1, 0, .55f));
        assertTrue(RoadTraffic.blocks(0, 0, .22f, 0, .65f, 0, .55f));
        assertFalse(RoadTraffic.blocks(0, 0, .22f, 0, 2, 0, .55f));
        assertFalse(RoadTraffic.blocks(0, 0, .22f, 0, -.2f, 0, .55f));
    }

    @Test
    void actualTravellerQueuesAndResumesWithoutLosingRoute() throws Exception {
        queuesAndResumes(false);
    }

    @Test
    void mountedTrafficQueuesAndResumesWithoutLosingRoute() throws Exception {
        queuesAndResumes(true);
    }

    @Test
    void convergingTrafficReservesSpaceBeforeTheMerge() {
        var merge = new Waypoint(1, 24);
        var eastbound = List.of(merge, new Waypoint(2, 24));
        var northbound = List.of(merge, new Waypoint(2, 24));
        assertTrue(RoadTraffic.yields(2, 1, 25.75f, northbound, 1, 0, 24, eastbound));
        assertFalse(RoadTraffic.yields(1, 0, 24, eastbound, 2, 1, 25.75f, northbound));
        assertFalse(RoadTraffic.yields(2, -1, 24, eastbound, 1, 0, 24, eastbound));
    }

    @Test
    void mountingAndDismountingSwitchOnlyTheRemainingRoadLane() throws Exception {
        var city=new CityTest().simulation(new CityTest.Ground());
        var ids=city.ecs.query(CitySimulation.Position.class,CitySimulation.Household.class);
        for(int id:ids) {var p=city.ecs.get(id,CitySimulation.Position.class);p.x=100;p.z=100;}
        int id=ids.get(0);var p=city.ecs.get(id,CitySimulation.Position.class);p.x=.5f;p.z=25.75f;
        var t=city.ecs.get(id,CitySimulation.Travel.class);var h=city.ecs.get(id,CitySimulation.Household.class);
        for(int x=0;x<=10;x++)t.route.add(new Cell(x,24));
        var travel=CitySimulation.class.getDeclaredMethod("travel",int.class,
                CitySimulation.Position.class,CitySimulation.Household.class,CitySimulation.Travel.class,float.class);
        travel.setAccessible(true);
        travel.invoke(city,id,p,h,t,.1f);
        int horse=city.ecs.query(CitySimulation.Mount.class).get(0);
        h.horse=horse;city.ecs.get(horse,CitySimulation.Mount.class).rider=-id;
        travel.invoke(city,id,p,h,t,.1f);
        assertEquals(25,t.lanes.peek().z(),.001);
        assertEquals(1.5,t.lanes.peek().x(),.001,"Consumed road cells must not restart");
        assertTrue(p.z<25.75);
        float mountedZ=p.z;h.horse=0;city.ecs.get(horse,CitySimulation.Mount.class).rider=0;
        travel.invoke(city,id,p,h,t,.1f);
        assertEquals(25.75,t.lanes.peek().z(),.001);
        assertTrue(p.z>mountedZ);
    }

    @Test
    void shortCornerLaneSwitchKeepsTheTrimmedPavementEndpoint() throws Exception {
        var city=new CityTest().simulation(new CityTest.Ground());
        var ids=city.ecs.query(CitySimulation.Position.class,CitySimulation.Household.class);
        for(int id:ids) {var p=city.ecs.get(id,CitySimulation.Position.class);p.x=100;p.z=100;}
        int id=ids.get(0);var p=city.ecs.get(id,CitySimulation.Position.class);p.x=7.5f;p.z=25;
        var t=city.ecs.get(id,CitySimulation.Travel.class);var h=city.ecs.get(id,CitySimulation.Household.class);
        t.route.addAll(List.of(new Cell(7,24),new Cell(8,24),new Cell(8,25)));
        int horse=city.ecs.query(CitySimulation.Mount.class).get(0);
        h.horse=horse;city.ecs.get(horse,CitySimulation.Mount.class).rider=-id;
        var travel=CitySimulation.class.getDeclaredMethod("travel",int.class,
                CitySimulation.Position.class,CitySimulation.Household.class,CitySimulation.Travel.class,float.class);
        travel.setAccessible(true);
        travel.invoke(city,id,p,h,t,.1f);travel.invoke(city,id,p,h,t,.1f);
        h.horse=0;city.ecs.get(horse,CitySimulation.Mount.class).rider=0;
        travel.invoke(city,id,p,h,t,.1f);
        assertFalse(t.lanes.isEmpty());
        assertEquals(new Waypoint(7.25f,25.75f),t.lanes.peek());
    }

    @Test
    void pavementAndCarriagewayKeepIndependentSameDirectionQueues() throws Exception {
        var city=new CityTest().simulation(new CityTest.Ground());
        var ids=city.ecs.query(CitySimulation.Position.class,CitySimulation.Household.class);
        for(int id:ids) {var p=city.ecs.get(id,CitySimulation.Position.class);p.x=100;p.z=100;}
        int walker=ids.get(0),rider=ids.get(1),ahead=ids.get(2);
        var wp=city.ecs.get(walker,CitySimulation.Position.class);wp.x=.5f;wp.z=25.75f;
        var rp=city.ecs.get(rider,CitySimulation.Position.class);rp.x=1;rp.z=25;
        var wt=city.ecs.get(walker,CitySimulation.Travel.class);
        wt.lanes.addAll(List.of(new Waypoint(1.5f,25.75f),new Waypoint(2.5f,25.75f)));
        city.ecs.get(rider,CitySimulation.Travel.class).lanes.addAll(
                List.of(new Waypoint(2.5f,25),new Waypoint(3.5f,25)));
        int horse=city.ecs.query(CitySimulation.Mount.class).get(0);
        city.ecs.get(rider,CitySimulation.Household.class).horse=horse;
        city.ecs.get(horse,CitySimulation.Mount.class).rider=-rider;
        var travel=CitySimulation.class.getDeclaredMethod("travel",int.class,
                CitySimulation.Position.class,CitySimulation.Household.class,CitySimulation.Travel.class,float.class);
        travel.setAccessible(true);
        travel.invoke(city,walker,wp,city.ecs.get(walker,CitySimulation.Household.class),wt,.1f);
        assertTrue(wp.x>.5f,"The adjacent mounted stream must leave pavement travel clear");
        wp.x=.5f;
        var ap=city.ecs.get(ahead,CitySimulation.Position.class);ap.x=1;ap.z=25.75f;
        city.ecs.get(ahead,CitySimulation.Travel.class).lanes.addAll(
                List.of(new Waypoint(2.5f,25.75f),new Waypoint(3.5f,25.75f)));
        travel.invoke(city,walker,wp,city.ecs.get(walker,CitySimulation.Household.class),wt,.1f);
        assertEquals(.5f,wp.x);
        assertEquals("Waiting for traffic",wt.activity);
    }

    @Test
    void diagonalLaneEntryClearsAnIdleWorkerBesideThePavement() throws Exception {
        var city=new CityTest().simulation(new CityTest.Ground());
        var ids=city.ecs.query(CitySimulation.Position.class,CitySimulation.Household.class);
        for(int id:ids) {var p=city.ecs.get(id,CitySimulation.Position.class);p.x=100;p.z=100;}
        int walker=ids.get(0),worker=ids.get(1);
        var p=city.ecs.get(walker,CitySimulation.Position.class);p.x=20;p.z=25.1f;
        var idle=city.ecs.get(worker,CitySimulation.Position.class);idle.x=17.9f;idle.z=25.1f;
        var t=city.ecs.get(walker,CitySimulation.Travel.class);
        t.lanes.addAll(List.of(new Waypoint(19.5f,25.75f),new Waypoint(20.5f,25.75f)));
        var travel=CitySimulation.class.getDeclaredMethod("travel",int.class,
                CitySimulation.Position.class,CitySimulation.Household.class,CitySimulation.Travel.class,float.class);
        travel.setAccessible(true);
        travel.invoke(city,walker,p,city.ecs.get(walker,CitySimulation.Household.class),t,.1f);
        assertTrue(p.z>25.1f,"An idle worker beside the pavement must not block diagonal entry");
    }

    @Test
    void touchingEndCapsConnectOnlyAcrossBuiltRoadCells() {
        var streets=List.of(new CityAddresses.Street(1,"West",List.of(new Point(0,24),new Point(2,24))),
                new CityAddresses.Street(2,"East",List.of(new Point(4,24),new Point(8,24))));
        var road=new RoadTraffic(streets,Set.of(new Cell(3,24)));
        assertTrue(road.connected(new Cell(2,24),new Cell(3,24)));
        assertTrue(road.connected(new Cell(3,24),new Cell(4,24)));
        assertFalse(new RoadTraffic(streets,Set.of()).connected(new Cell(2,24),new Cell(3,24)));
    }

    @Test
    void tinyRemainingLookaheadKeepsTheEarlierMergeTravellerFree() {
        var lane=List.of(new Waypoint(-4.5f,25.75f),new Waypoint(-3.5f,25.75f));
        assertFalse(RoadTraffic.yields(10,-4.4867907f,25.677675f,lane,
                1,-4.46439f,23.750948f,lane),
                "The first arrival must clear a short corner before the later arrival");
        assertTrue(RoadTraffic.yields(1,-4.46439f,23.750948f,lane,
                10,-4.4867907f,25.677675f,lane));
    }

    @Test
    void multipleCrossingsKeepOneMergePriority() {
        var pavement=List.of(new Waypoint(-5.5f,25.75f),new Waypoint(-4.5f,25.75f));
        var carriageway=List.of(new Waypoint(-5.5f,25),new Waypoint(-4.5f,25));
        assertTrue(RoadTraffic.yields(11,-5.5f,23.819975f,pavement,
                12,-5.5f,25.399973f,carriageway));
        assertFalse(RoadTraffic.yields(12,-5.5f,25.399973f,carriageway,
                11,-5.5f,23.819975f,pavement),"Two conflicts must not reserve each other");
    }

    @Test
    void offsetOpposingEntriesReserveBodyClearance() {
        var west=List.of(new Waypoint(24.5f,23.25f),new Waypoint(23.5f,23.25f));
        var east=List.of(new Waypoint(24.5f,25.75f),new Waypoint(25.5f,25.75f));
        assertFalse(RoadTraffic.yields(6,24.5f,24.520006f,west,
                12,24.300838f,23.906181f,east));
        assertTrue(RoadTraffic.yields(12,24.300838f,23.906181f,east,
                6,24.5f,24.520006f,west),"Body widths must reserve an aisle even if centre lines miss");
    }

    @Test
    void clearanceChecksEveryPriorityTravellerBeforeWaiting() throws Exception {
        var city=new CityTest().simulation(new CityTest.Ground());
        var ids=city.ecs.query(CitySimulation.Position.class,CitySimulation.Household.class);
        for(int id:ids) {
            var p=city.ecs.get(id,CitySimulation.Position.class);p.x=100;p.z=100;
            city.ecs.get(id,CitySimulation.Household.class).horse=0;
            var t=city.ecs.get(id,CitySimulation.Travel.class);t.route.clear();t.clearRoadLanes();
        }
        for(int n=0;n<3;n++) {
            int id=ids.get(n);var p=city.ecs.get(id,CitySimulation.Position.class);
            p.x=new float[]{17.5f,16.5f,15.995405f}[n];
            p.z=n==2 ? 23.575386f : 23.25f;
            var t=city.ecs.get(id,CitySimulation.Travel.class);
            t.lanes.add(new Waypoint(n==2 ? 15.5f : p.x-1,n==2 ? 24 : 23.25f));
            t.lanes.add(new Waypoint(13.5f,n==2 ? 24 : 23.25f));
        }
        int joining=ids.get(2);var p=city.ecs.get(joining,CitySimulation.Position.class);
        float before=p.x;
        var travel=CitySimulation.class.getDeclaredMethod("travel",int.class,
                CitySimulation.Position.class,CitySimulation.Household.class,CitySimulation.Travel.class,float.class);
        travel.setAccessible(true);
        travel.invoke(city,joining,p,city.ecs.get(joining,CitySimulation.Household.class),
                city.ecs.get(joining,CitySimulation.Travel.class),.1f);
        assertTrue(p.x<before,"A distant reservation must not prevent clearing the blocked priority traveller");
        for(int n=0;n<2;n++) {
            var other=city.ecs.get(ids.get(n),CitySimulation.Position.class);
            assertTrue(Math.hypot(p.x-other.x,p.z-other.z)>=.5-.0001);
        }
    }

    @Test
    void queuedFollowerLeavesRoomForAJoiningTravellerToBackOut() throws Exception {
        for(boolean reverse:new boolean[]{false,true}) {
            var city=new CityTest().simulation(new CityTest.Ground());
            var ids=city.ecs.query(CitySimulation.Position.class,CitySimulation.Household.class);
            for(int id:ids) {
                var p=city.ecs.get(id,CitySimulation.Position.class);p.x=100;p.z=100;
                city.ecs.get(id,CitySimulation.Household.class).horse=0;
                var t=city.ecs.get(id,CitySimulation.Travel.class);t.route.clear();t.clearRoadLanes();
            }
            for(int n=0;n<3;n++) {
                int id=ids.get(n);var p=city.ecs.get(id,CitySimulation.Position.class);
                p.x=n==0 ? 10.39f : 9.5f;p.z=new float[]{23.25f,23.62f,24.5f}[n];
                var t=city.ecs.get(id,CitySimulation.Travel.class);
                t.lanes.add(new Waypoint(9.5f,23.25f));
                t.lanes.add(new Waypoint(3.5f+n,23.25f));
            }
            var travel=CitySimulation.class.getDeclaredMethod("travel",int.class,
                    CitySimulation.Position.class,CitySimulation.Household.class,CitySimulation.Travel.class,float.class);
            travel.setAccessible(true);
            boolean followerRetreated=false;
            for(int step=0;step<100;step++) for(int n=0;n<3;n++) {
                int id=ids.get(reverse ? 2-n : n);var p=city.ecs.get(id,CitySimulation.Position.class);
                var t=city.ecs.get(id,CitySimulation.Travel.class);if(t.lanes.isEmpty())continue;
                float z=p.z;
                travel.invoke(city,id,p,city.ecs.get(id,CitySimulation.Household.class),t,.1f);
                if(id==ids.get(2) && p.z>z)followerRetreated=true;
                for(int a=0;a<3;a++)for(int b=a+1;b<3;b++) {
                    var ap=city.ecs.get(ids.get(a),CitySimulation.Position.class);
                    var bp=city.ecs.get(ids.get(b),CitySimulation.Position.class);
                    assertTrue(Math.hypot(ap.x-bp.x,ap.z-bp.z)>=.8-.0001);
                }
            }
            assertTrue(followerRetreated);
            for(int n=0;n<3;n++)assertTrue(city.ecs.get(ids.get(n),CitySimulation.Travel.class).lanes.isEmpty(),
                    "Queued entry must drain in both update orders");
        }
    }

    @Test
    void privateForecourtCanPassAParkedMountWithoutReservingItsRoadHeadway() throws Exception {
        var ground=new CityTest.Ground();var city=new CityTest().simulation(ground);
        var ids=city.ecs.query(CitySimulation.Position.class,CitySimulation.Household.class);
        for(int id:ids) {var p=city.ecs.get(id,CitySimulation.Position.class);p.x=100;p.z=100;}
        for(int id:city.ecs.query(CitySimulation.Mount.class)) {
            var p=city.ecs.get(id,CitySimulation.Position.class);p.x=100;p.z=100;
            city.ecs.get(id,CitySimulation.Mount.class).rider=0;
        }
        int id=ids.getFirst(),horse=city.ecs.query(CitySimulation.Mount.class).getFirst();
        var p=city.ecs.get(id,CitySimulation.Position.class);p.x=14.5f;p.z=22.3f;
        int y=city.frame().roads().getFirst().y();p.y=y+1.01f;
        for(int x=13;x<=15;x++)for(int z=22;z<=26;z++)ground.apply(List.of(
                new dev.jayms.net.Protocol.Edit(x,y,z,dev.jayms.net.Blocks.DIRT),
                new dev.jayms.net.Protocol.Edit(x,y+1,z,dev.jayms.net.Blocks.AIR),
                new dev.jayms.net.Protocol.Edit(x,y+2,z,dev.jayms.net.Blocks.AIR)));
        var hp=city.ecs.get(horse,CitySimulation.Position.class);hp.x=14.5f;hp.z=23.7f;
        var t=city.ecs.get(id,CitySimulation.Travel.class);t.route.clear();t.clearRoadLanes();
        t.accessRoute=false;t.passingPoints=1;t.route.add(new Cell(14,24));t.route.add(new Cell(15,24));
        var travel=CitySimulation.class.getDeclaredMethod("travel",int.class,
                CitySimulation.Position.class,CitySimulation.Household.class,CitySimulation.Travel.class,float.class);
        travel.setAccessible(true);
        travel.invoke(city,id,p,city.ecs.get(id,CitySimulation.Household.class),t,.1f);
        assertTrue(p.z>22.3f,"A clear forecourt step must advance toward the road");
        assertTrue(Math.hypot(p.x-hp.x,p.z-hp.z)>=.65-.0001);
    }

    @Test
    void opposingAccessStreamsReserveTheirSharedAisle() {
        var south=List.of(new Waypoint(0,2));
        var north=List.of(new Waypoint(0,0));
        assertTrue(RoadTraffic.yields(2,0,1.5f,north,1,0,.5f,south));
        assertFalse(RoadTraffic.yields(1,0,.5f,south,2,0,1.5f,north));
        assertFalse(RoadTraffic.yields(2,1,1.5f,List.of(new Waypoint(1,0)),1,0,.5f,south));
    }

    @Test
    void reachedWaypointIsConsumedBeforeWaitingForAMerge() throws Exception {
        var city = new CityTest().simulation(new CityTest.Ground());
        var ids = city.ecs.query(CitySimulation.Position.class, CitySimulation.Household.class);
        for (int id : ids) {
            var position = city.ecs.get(id, CitySimulation.Position.class);
            position.x = 100;
            position.z = 100;
        }
        int ahead = ids.get(0), follower = ids.get(1);
        var ap = city.ecs.get(ahead, CitySimulation.Position.class);
        ap.x = 1.5f;
        ap.z = 24.75f;
        var p = city.ecs.get(follower, CitySimulation.Position.class);
        p.x = .5f;
        p.z = 25.75f;
        p.phase = 3;
        var merge = new Waypoint(1.5f, 25.75f);
        var at = city.ecs.get(ahead, CitySimulation.Travel.class);
        at.lanes.add(merge);
        at.lanes.add(new Waypoint(2.5f, 25.75f));
        var t = city.ecs.get(follower, CitySimulation.Travel.class);
        t.lanes.add(new Waypoint(p.x, p.z));
        t.lanes.add(merge);
        t.activity = "Walking";
        var travel =
                CitySimulation.class.getDeclaredMethod(
                        "travel",
                        int.class,
                        CitySimulation.Position.class,
                        CitySimulation.Household.class,
                        CitySimulation.Travel.class,
                        float.class);
        travel.setAccessible(true);
        travel.invoke(
                city, follower, p, city.ecs.get(follower, CitySimulation.Household.class), t, .1f);
        assertEquals(1, t.lanes.size());
        assertEquals(merge, t.lanes.peek());
        assertEquals("Walking", t.activity);
        assertEquals(.5f, p.x);
        assertEquals(3, p.phase);
    }

    @Test
    void closelySpacedTravellersClearBendsInBothDirections() throws Exception {
        for (int turn : new int[] {-1, 1}) {
            for (boolean reverse : new boolean[] {false, true}) {
                var ground = new CityTest.Ground();
                var city = new CityTest().simulation(ground);
                var cells = new ArrayList<Cell>();
                for (int x = 2; x <= 8; x++) cells.add(new Cell(x, 24));
                for (int n = 1; n <= 8; n++) cells.add(new Cell(8, 24 + n * turn));
                if (reverse) Collections.reverse(cells);
                var road =
                        new RoadTraffic(
                                List.of(
                                        new CityAddresses.Street(
                                                1,
                                                "Bend",
                                                List.of(
                                                        new Point(2, 24),
                                                        new Point(8, 24),
                                                        new Point(8, 24 + 8 * turn)))));
                var ids =
                        city.ecs.query(
                                CitySimulation.Position.class, CitySimulation.Household.class);
                for (int id : ids) {
                    var p = city.ecs.get(id, CitySimulation.Position.class);
                    p.x = 100;
                    p.z = 100;
                    city.ecs.get(id, CitySimulation.Household.class).horse = 0;
                    city.ecs.get(id, CitySimulation.Travel.class).route.clear();
                }
                int cornerIndex = reverse ? 8 : 6;
                var cornerCell = cells.get(cornerIndex);
                int dx = cells.get(1).x() - cells.get(0).x();
                int dz = cells.get(1).z() - cells.get(0).z();
                for (int i = 0; i < 3; i++) {
                    var p = city.ecs.get(ids.get(i), CitySimulation.Position.class);
                    p.x = cornerCell.x() + .5f - (1.3f + i * .55f) * dx - dz * 1.25f;
                    p.z = cornerCell.z() + .5f - (1.3f + i * .55f) * dz + dx * 1.25f;
                    p.y = city.frame().roads().get(0).y() + 1.01f;
                    var t = city.ecs.get(ids.get(i), CitySimulation.Travel.class);
                    t.target = 0;
                    t.lanes.addAll(
                            road.lanes(cells.subList(cornerIndex - 1, cells.size() - i), false));
                    // Join beyond samples already behind the traveller.
                    while (!t.lanes.isEmpty()
                            && (t.lanes.peek().x() - p.x) * dx + (t.lanes.peek().z() - p.z) * dz
                                    <= 0) t.lanes.remove();
                }
                var travel =
                        CitySimulation.class.getDeclaredMethod(
                                "travel",
                                int.class,
                                CitySimulation.Position.class,
                                CitySimulation.Household.class,
                                CitySimulation.Travel.class,
                                float.class);
                travel.setAccessible(true);
                boolean waited = false;
                for (int step = 0; step < 1250; step++) {
                    // Followers update first, including while the leader rounds the corner.
                    for (int i = 2; i >= 0; i--) {
                        int id = ids.get(i);
                        var t = city.ecs.get(id, CitySimulation.Travel.class);
                        if (t.lanes.isEmpty()) continue;
                        travel.invoke(
                                city,
                                id,
                                city.ecs.get(id, CitySimulation.Position.class),
                                city.ecs.get(id, CitySimulation.Household.class),
                                t,
                                .02f);
                        waited |= "Waiting for traffic".equals(t.activity);
                        assertNotEquals("Route obstructed", t.activity);
                    }
                }
                assertTrue(waited, "The closely spaced queue must exercise traffic waiting");
                for (int i = 0; i < 3; i++) {
                    int id = ids.get(i);
                    assertTrue(
                            city.ecs.get(id, CitySimulation.Travel.class).route.isEmpty(),
                            "Queue stuck: turn="
                                    + turn
                                    + ", reverse="
                                    + reverse
                                    + ", traveller="
                                    + i);
                    var destination =
                            road.lanes(cells.subList(cornerIndex - 1, cells.size() - i), false);
                    var end = destination.get(destination.size() - 1);
                    var p = city.ecs.get(id, CitySimulation.Position.class);
                    assertEquals(end.x(), p.x, .001);
                    assertEquals(end.z(), p.z, .001);
                }
            }
        }
    }

    @Test
    void crossingStreamsClearWithSpacingInBothUpdateOrders() throws Exception {
        for (boolean mounted : new boolean[] {false, true}) {
            for (boolean reverseOrder : new boolean[] {false, true}) {
                var city = new CityTest().simulation(new CityTest.Ground());
                var ids =
                        city.ecs.query(
                                CitySimulation.Position.class, CitySimulation.Household.class);
                for (int id : ids) {
                    var p = city.ecs.get(id, CitySimulation.Position.class);
                    p.x = 100;
                    p.z = 100;
                    city.ecs.get(id, CitySimulation.Household.class).horse = 0;
                    city.ecs.get(id, CitySimulation.Travel.class).route.clear();
                }
                float scale = mounted ? 1.3f : 1;
                for (int i = 0; i < 2; i++) {
                    int id = ids.get(i);
                    var p = city.ecs.get(id, CitySimulation.Position.class);
                    p.x = 8.5f - (i == 0 ? .35f * scale : 0);
                    p.z = 24.5f - (i == 1 ? .4f * scale : 0);
                    p.y = city.frame().roads().get(0).y() + 1.01f;
                    var t = city.ecs.get(id, CitySimulation.Travel.class);
                    t.target = 0;
                    t.lanes.add(new Waypoint(i == 0 ? 10.5f : 8.5f, i == 0 ? 24.5f : 26.5f));
                    if (mounted) {
                        int horse = city.ecs.query(CitySimulation.Mount.class).get(i);
                        city.ecs.get(id, CitySimulation.Household.class).horse = horse;
                        city.ecs.get(horse, CitySimulation.Mount.class).rider = -id;
                    }
                }
                var travel =
                        CitySimulation.class.getDeclaredMethod(
                                "travel",
                                int.class,
                                CitySimulation.Position.class,
                                CitySimulation.Household.class,
                                CitySimulation.Travel.class,
                                float.class);
                travel.setAccessible(true);
                float gap = mounted ? .65f : .5f;
                boolean backedOut = false;
                for (int step = 0; step < 100; step++) {
                    for (int n = 0; n < 2; n++) {
                        int id = ids.get(reverseOrder ? 1 - n : n);
                        var t = city.ecs.get(id, CitySimulation.Travel.class);
                        if (t.lanes.isEmpty()) continue;
                        var p = city.ecs.get(id, CitySimulation.Position.class);
                        float before = p.z;
                        travel.invoke(
                                city,
                                id,
                                p,
                                city.ecs.get(id, CitySimulation.Household.class),
                                t,
                                .1f);
                        if (id == ids.get(1) && p.z < before) backedOut = true;
                        var a = city.ecs.get(ids.get(0), CitySimulation.Position.class);
                        var b = city.ecs.get(ids.get(1), CitySimulation.Position.class);
                        assertTrue(
                                Math.hypot(a.x - b.x, a.z - b.z) >= gap - .0001,
                                "Crossing traffic must retain spacing");
                        assertNotEquals("Route obstructed", t.activity);
                    }
                }
                assertTrue(backedOut, "The yielding stream must free the reserved crossing");
                for (int i = 0; i < 2; i++) {
                    int id = ids.get(i);
                    assertTrue(
                            city.ecs.get(id, CitySimulation.Travel.class).route.isEmpty(),
                            "Crossing stuck: mounted="
                                    + mounted
                                    + ", reverseOrder="
                                    + reverseOrder);
                    var p = city.ecs.get(id, CitySimulation.Position.class);
                    assertEquals(i == 0 ? 10.5f : 8.5f, p.x, .001);
                    assertEquals(i == 0 ? 24.5f : 26.5f, p.z, .001);
                }
            }
        }
    }

    @Test
    void pavementMergeCanClearBackOntoBuildingAccessPath() throws Exception {
        for (boolean reverseOrder : new boolean[] {false, true}) {
            var city = new CityTest().simulation(new CityTest.Ground());
            var ids = city.ecs.query(CitySimulation.Position.class, CitySimulation.Household.class);
            for (int id : ids) {
                var p = city.ecs.get(id, CitySimulation.Position.class);
                p.x = 100;
                p.z = 100;
                city.ecs.get(id, CitySimulation.Household.class).horse = 0;
                city.ecs.get(id, CitySimulation.Travel.class).route.clear();
            }
            for (int i = 0; i < 2; i++) {
                int id = ids.get(i);
                var p = city.ecs.get(id, CitySimulation.Position.class);
                p.x = i == 0 ? .16f : .474f;
                p.z = i == 0 ? 25.75f : 26.178f;
                p.y = city.frame().roads().get(0).y() + 1.01f;
                var t = city.ecs.get(id, CitySimulation.Travel.class);
                t.target = 0;
                if (i == 1) t.lanes.add(new Waypoint(.5f, 25.75f));
                t.lanes.add(new Waypoint(i == 0 ? 4.5f : 3.5f, 25.75f));
            }
            var travel =
                    CitySimulation.class.getDeclaredMethod(
                            "travel",
                            int.class,
                            CitySimulation.Position.class,
                            CitySimulation.Household.class,
                            CitySimulation.Travel.class,
                            float.class);
            travel.setAccessible(true);
            boolean retreated = false;
            for (int step = 0; step < 100; step++) {
                for (int n = 0; n < 2; n++) {
                    int id = ids.get(reverseOrder ? 1 - n : n);
                    var t = city.ecs.get(id, CitySimulation.Travel.class);
                    if (t.lanes.isEmpty()) continue;
                    var p = city.ecs.get(id, CitySimulation.Position.class);
                    float before = p.z;
                    travel.invoke(
                            city, id, p, city.ecs.get(id, CitySimulation.Household.class), t, .1f);
                    if (id == ids.get(1) && p.z > before) retreated = true;
                    var a = city.ecs.get(ids.get(0), CitySimulation.Position.class);
                    var b = city.ecs.get(ids.get(1), CitySimulation.Position.class);
                    assertTrue(
                            Math.hypot(a.x - b.x, a.z - b.z) >= .5f - .0001,
                            "Access merge must retain spacing: step=" + step + ", positions="
                                    + a.x + "," + a.z + " / " + b.x + "," + b.z);
                    assertNotEquals("Route obstructed", t.activity);
                }
            }
            assertTrue(retreated, "Joining traveller must clear back onto the access path");
            for (int i = 0; i < 2; i++) {
                assertTrue(
                        city.ecs.get(ids.get(i), CitySimulation.Travel.class).route.isEmpty(),
                        "Access merge must drain in both update orders");
            }
        }
    }

    private void queuesAndResumes(boolean mounted) throws Exception {
        var ground = new CityTest.Ground();
        var city = new CityTest().simulation(ground);
        var ids = city.ecs.query(CitySimulation.Position.class, CitySimulation.Household.class);
        assertTrue(ids.size() >= 3);
        for (int id : ids) {
            var p = city.ecs.get(id, CitySimulation.Position.class);
            p.x = 100;
            p.z = 100;
            city.ecs.get(id, CitySimulation.Household.class).horse = 0;
        }
        var travelMethod =
                CitySimulation.class.getDeclaredMethod(
                        "travel",
                        int.class,
                        CitySimulation.Position.class,
                        CitySimulation.Household.class,
                        CitySimulation.Travel.class,
                        float.class);
        travelMethod.setAccessible(true);
        for (int i = 0; i < 3; i++) {
            var p = city.ecs.get(ids.get(i), CitySimulation.Position.class);
            p.x = 1.5f - i * (mounted ? 1f : .65f);
            p.z = mounted ? 25f : 25.75f;
            p.y = city.frame().roads().get(0).y() + 1.01f;
            var t = city.ecs.get(ids.get(i), CitySimulation.Travel.class);
            t.target = 0;
            t.lanes.clear();
            t.lanes.add(new Waypoint(4.5f, p.z));
            if (mounted) {
                int horse = city.ecs.query(CitySimulation.Mount.class).get(i);
                city.ecs.get(ids.get(i), CitySimulation.Household.class).horse = horse;
                city.ecs.get(horse, CitySimulation.Mount.class).rider = -ids.get(i);
            }
        }
        for (int i = 1; i < 3; i++) {
            int id = ids.get(i);
            var p = city.ecs.get(id, CitySimulation.Position.class);
            var t = city.ecs.get(id, CitySimulation.Travel.class);
            float before = p.x;
            travelMethod.invoke(
                    city, id, p, city.ecs.get(id, CitySimulation.Household.class), t, .1f);
            assertEquals(before, p.x);
            assertEquals("Waiting for traffic", t.activity);
            assertEquals(1, t.lanes.size());
        }
        float[] before = new float[3];
        for(int i=0;i<3;i++) before[i]=city.ecs.get(ids.get(i),CitySimulation.Position.class).x;
        // Release must first restore the configured gap for an initially crowded spawn.
        for(int step=0;step<20;step++) for(int i=0;i<3;i++) {
            int id=ids.get(i);
            travelMethod.invoke(city,id,city.ecs.get(id,CitySimulation.Position.class),
                    city.ecs.get(id,CitySimulation.Household.class),
                    city.ecs.get(id,CitySimulation.Travel.class),.1f);
        }
        for(int i=0;i<3;i++) assertTrue(city.ecs.get(ids.get(i),CitySimulation.Position.class).x>before[i]);

    }

    private void assertPavement(CityTest.Ground ground, int x, int y, int z) {
        assertEquals(
                dev.jayms.net.Blocks.piece(dev.jayms.net.Blocks.DIRT, 1),
                ground.voxels.region(dev.jayms.net.Protocol.Edit.at(x, y + .5, z, 0, 1)));
        assertEquals(
                dev.jayms.net.Blocks.piece(dev.jayms.net.Blocks.STONE, 1),
                ground.voxels.region(dev.jayms.net.Protocol.Edit.at(x, y + .5, z + .5, 0, 1)));
    }

    @Test
    void pavementMaterialAndSaveRestoreTopology() {
        var ground = new CityTest.Ground();
        var city = new CityTest().simulation(ground);
        int y = city.frame().roads().get(0).y();
        assertEquals(dev.jayms.net.Blocks.DIRT, ground.type(0, y, 24));
        assertPavement(ground, 0, y, 25);
        var route = city.route(0, 24, 20, 24);
        assertFalse(route.isEmpty());
        var restored = new CitySimulation(city.config(), ground, ground.terrain, city.frame());
        assertEquals(route, restored.route(0, 24, 20, 24));
        ground.apply(List.of(new dev.jayms.net.Protocol.Edit(0, y, 25, dev.jayms.net.Blocks.DIRT)));
        restored.advance(.1);
        assertPavement(ground, 0, y, 25);
    }
}
