package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RailwayShippingCompatibilityTest {
    @TempDir Path temp;

    @Test void formatTwelvePortSaveLoadsAndResavesWithoutBecomingStation() throws Exception {
        var config=GameConfig.cityGame();
        var original=new CityFrame(config,0,List.of(new CityFrame.Road(50,50,30,3)),List.of(),
                List.of(new CityFrame.Building(1,0,24,50,31,52,4,0)),List.of(),List.of());
        var file=temp.resolve("shipping12.city");
        try(var out=new DataOutputStream(Files.newOutputStream(file))) {
            out.writeInt(0x4349543C);original.write(out,12);
        }
        var loaded=CitySimulation.load(file);
        assertEquals(original,loaded);
        assertEquals(SpecialBuildings.PORT,loaded.buildings().get(0).type());
        assertTrue(loaded.railway().tracks().isEmpty());
        var ground=new RailwayTest.Ground(30);
        var city=new CitySimulation(config,ground,new Terrain(1L,3),loaded);
        var upgraded=temp.resolve("shipping13.city");city.save(upgraded);
        var restored=CitySimulation.load(upgraded);
        assertEquals(loaded.buildings(),restored.buildings());
        assertEquals(loaded.roads(),restored.roads());
        assertNotEquals(SpecialBuildings.PORT,SpecialBuildings.RAIL_STATION);
        assertEquals(25,SpecialBuildings.RAIL_STATION);assertEquals(26,SpecialBuildings.RAIL_DEPOT);
        assertEquals(28,Protocol.VERSION);
    }

    @Test void generatorThreeRemainsSupportedAndDeterministic() {
        var a=new Terrain(1L,3);var b=new Terrain(1L,3);
        assertEquals(3,a.version);
        for(int z:new int[]{24,300,580}) assertEquals(a.column(8,z),b.column(8,z));
        assertEquals(3,Terrain.CURRENT_VERSION);
    }
}
