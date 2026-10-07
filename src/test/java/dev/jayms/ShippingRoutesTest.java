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
        var t=new Terrain(Terrain.DEFAULT_SEED);var routes=new ShippingRoutes();
        var a=port(1,0,240);var b=port(2,120,240);
        assertTrue(routes.ships(frame(0,a),t).isEmpty());
        var route=routes.routes(frame(0,a,b),t).get(0);
        for(var p:route.points())assertTrue(t.ocean((int)p.x(),(int)p.z()));
        var start=ShippingRoutes.sample(route,0);assertFalse(start.sailing());
        var moving=ShippingRoutes.sample(route,9);assertTrue(moving.sailing());assertNotEquals(start.z(),moving.z());
        double arrival=ShippingRoutes.DWELL+route.length()/ShippingRoutes.SPEED;
        var end=ShippingRoutes.sample(route,arrival);assertEquals(123,end.x());assertEquals(274,end.z());assertFalse(end.sailing());
        var returning=ShippingRoutes.sample(route,arrival+9);assertTrue(returning.sailing());assertTrue(returning.z()>end.z());
        assertEquals(start,ShippingRoutes.sample(route,2*arrival));
        assertTrue(routes.routes(frame(0,a),t).isEmpty(),"Demolishing destination removes service");
    }
    @Test void landOldTerrainFarPortsAndImmutableCache() {
        var routes=new ShippingRoutes();var t=new Terrain(Terrain.DEFAULT_SEED);
        assertTrue(routes.routes(frame(0,port(1,0,20),port(2,120,20)),t).isEmpty());
        assertTrue(routes.routes(frame(0,port(1,0,240),port(2,120,240)),new Terrain(42,2)).isEmpty());
        assertTrue(routes.routes(frame(0,port(1,0,240),port(2,3000,240)),t).isEmpty());
        var f=frame(0,port(1,0,240),port(2,120,240));var r=routes.routes(f,t);
        assertSame(r,routes.routes(f,t));assertThrows(UnsupportedOperationException.class,()->r.clear());
    }
    @Test void wireAndSaveRoundTripKeepsVoyage() throws Exception {
        var t=new Terrain(42);var f=frame(25,port(1,0,240),port(2,120,240));
        var bytes=new ByteArrayOutputStream();f.write(new DataOutputStream(bytes));
        var read=CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(new ShippingRoutes().ships(f,t),new ShippingRoutes().ships(read,t));
    }
}
