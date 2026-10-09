package dev.jayms;

import dev.jayms.net.city.GameConfig;
import dev.jayms.net.city.CityFrame;
import dev.jayms.net.*;
import dev.jayms.net.mobile.MobileSnapshot;
import java.util.List;
import dev.jayms.render.Daylight;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DaylightTest {
    @Test void fixedSandboxUsesConfiguredHourWithoutAdvancing() {
        var noon=new GameConfig(false,false,1200,12);
        var night=new GameConfig(false,false,1200,0);
        assertTrue(Daylight.at(noon,0).sun().y>.9);
        assertTrue(Daylight.at(night,0).sun().y<-.9);
        assertEquals(Daylight.at(night,0),Daylight.at(night,1e8));
        assertEquals(Daylight.at(new GameConfig(true,false,1200,18),0),Daylight.at(new GameConfig(false,false,1200,18),0));
    }
    @Test void mobileSnapshotUsesSameFixedSolarClock() {
        var terrain=new Terrain(42);
        var world=new WorldVoxels(terrain);
        var pose=new Protocol.Pose(1,0,26,0,0,0,0,0,false,0,0,false);
        for(double hour:new double[]{0,6,12,18}) {
            var config=new GameConfig(false,false,1200,hour);
            var city=new CityFrame(config,700,List.of(),List.of(),List.of(),List.of(),List.of());
            var snapshot=MobileSnapshot.capture(world,terrain,pose,new Inventory(),20,city,"",8);
            var sun=(List<?>)snapshot.get("sun");var desktop=Daylight.at(config,700).sun();
            assertEquals(desktop.x,((Number)sun.get(0)).doubleValue(),1e-6);
            assertEquals(desktop.y,((Number)sun.get(1)).doubleValue(),1e-6);
            assertEquals(desktop.z,((Number)sun.get(2)).doubleValue(),1e-6);
        }
    }
    @Test void cycleAndFixedClockShareSolarDirection() {
        assertEquals(Daylight.at(new GameConfig(false,true,1200,6),300),Daylight.at(new GameConfig(false,false,1200,12),0));
        assertTrue(Daylight.at(new GameConfig(false,false,1200,6),0).sun().x>.9);
        assertTrue(Daylight.at(new GameConfig(false,false,1200,18),0).sun().x<-.9);
    }
}
