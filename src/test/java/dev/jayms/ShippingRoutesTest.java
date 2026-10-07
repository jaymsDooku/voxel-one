package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShippingRoutesTest {
    static CityFrame.Building port(int id,int x,int z) {
        return new CityFrame.Building(id,0,SpecialBuildings.PORT,x,27,z,1,0);
    }
    static CityFrame frame(double time,CityFrame.Building... ports) {
        return new CityFrame(GameConfig.cityGame(),time,List.of(),List.of(),List.of(ports),List.of(),List.of());
    }
    @Test void waterNetworkMovementReturnDwellAndDemolition() {
        var t=new World(Terrain.DEFAULT_SEED);var routes=new ShippingRoutes();
        var a=port(1,0,240);var b=port(2,120,240);
        assertTrue(routes.ships(frame(0,a),t).isEmpty());
        var route=routes.routes(frame(0,a,b),t).get(0);
        for(var p:route.points())assertTrue(t.terrain().ocean((int)p.x(),(int)p.z()));
        var start=ShippingRoutes.sample(route,0);assertFalse(start.sailing());
        var moving=ShippingRoutes.sample(route,9);assertTrue(moving.sailing());assertNotEquals(start.z(),moving.z());
        double arrival=ShippingRoutes.DWELL+route.length()/ShippingRoutes.SPEED;
        var end=ShippingRoutes.sample(route,arrival);assertEquals(123,end.x());assertEquals(274,end.z());assertFalse(end.sailing());
        var returning=ShippingRoutes.sample(route,arrival+9);assertTrue(returning.sailing());assertTrue(returning.z()>end.z());
        assertEquals(start,ShippingRoutes.sample(route,2*arrival));
        assertTrue(routes.routes(frame(0,a),t).isEmpty(),"Demolishing destination removes service");
    }
    @Test void landOldTerrainFarPortsAndImmutableCache() {
        var routes=new ShippingRoutes();var t=new World(Terrain.DEFAULT_SEED);
        assertTrue(routes.routes(frame(0,port(1,0,20),port(2,120,20)),t).isEmpty());
        assertTrue(routes.routes(frame(0,port(1,0,240),port(2,120,240)),new World(42,new dev.jayms.net.model.ModelLibrary(),2)).isEmpty());
        assertTrue(routes.routes(frame(0,port(1,0,240),port(2,3000,240)),t).isEmpty());
        var f=frame(0,port(1,0,240),port(2,120,240));var r=routes.routes(f,t);
        assertSame(r,routes.routes(f,t));assertThrows(UnsupportedOperationException.class,()->r.clear());
    }
    @Test void wireAndSaveRoundTripKeepsVoyage() throws Exception {
        var t=new World(42);var f=frame(25,port(1,0,240),port(2,120,240));
        var bytes=new ByteArrayOutputStream();f.write(new DataOutputStream(bytes));
        var read=CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(new ShippingRoutes().ships(f,t),new ShippingRoutes().ships(read,t));
    }
    @Test void editedWaterHullAndMastObstaclesInvalidateFreshAndCachedRoutes() {
        var w=new World(Terrain.DEFAULT_SEED);var service=new ShippingRoutes();
        var f=frame(25,port(1,0,240),port(2,120,240));
        for(int y:new int[]{14,15,23}) {
            var before=service.routes(f,w);assertEquals(1,before.size());
            assertSame(before,service.routes(f,w));
            w.apply(new Protocol.Edit(63,y,300,Blocks.STONE));
            assertTrue(service.routes(f,w).isEmpty(),"Cached route rejects edit at height "+y);
            assertTrue(new ShippingRoutes().routes(f,w).isEmpty(),"Fresh route rejects edit");
            assertTrue(service.ships(f,w).isEmpty());
            w.apply(new Protocol.Edit(63,y,300,y==14?Blocks.WATER:0));
            assertEquals(1,service.routes(f,w).size(),"Removing obstacle restores service");
        }
        w.apply(Protocol.Edit.at(63.25,16.25,300.25,Blocks.piece(Blocks.STONE,2),2));
        assertTrue(service.routes(f,w).isEmpty(),"Partial blocks obstruct the corridor");
        w.apply(new Protocol.Edit(63,16,300,0));
        assertEquals(1,service.routes(f,w).size());
    }
    @Test void differentWorldsAndReplayedEditsCannotReuseAnOpenRoute() {
        var a=new World(42);var b=new World(42);var service=new ShippingRoutes();
        var f=frame(25,port(1,0,240),port(2,120,240));
        a.apply(new Protocol.Edit(900,15,900,Blocks.STONE));
        var obstacle=new Protocol.Edit(63,15,300,Blocks.STONE);b.apply(obstacle);
        assertEquals(a.editsVersion(),b.editsVersion());
        assertEquals(1,service.routes(f,a).size());assertTrue(service.routes(f,b).isEmpty());
        var reload=new World(42);b.editsSnapshot().values().forEach(reload::apply);
        assertTrue(new ShippingRoutes().routes(f,reload).isEmpty());
        reload.apply(new Protocol.Edit(63,15,300,0));
        assertEquals(1,service.routes(f,reload).size());
    }
    @Test void implicitWorldColumnsMatchGeneratorWithoutOctreeOverrides() {
        var t=new Terrain(42);var v=new WorldVoxels(t);
        for(int x:new int[]{-100,0,123})for(int z:new int[]{24,240,300})for(int y=-1;y<30;y++)
            assertEquals(t.block(x,y,z),v.type(x,y,z));
        v.apply(new Protocol.Edit(0,15,300,Blocks.STONE));assertEquals(Blocks.STONE,v.type(0,15,300));
    }
}
