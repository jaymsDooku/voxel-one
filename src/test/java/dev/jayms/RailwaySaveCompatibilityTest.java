package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent legacy road bytes reproduce the base-format paved save rejected by the old rail patch. */
class RailwaySaveCompatibilityTest {
    @TempDir Path directory;

    private Path legacySave(int version, List<CityFrame.Road> roads) throws Exception {
        var empty = CityFrame.empty(GameConfig.cityGame());
        var tail = new ByteArrayOutputStream();
        empty.write(new DataOutputStream(tail), version);
        var file = directory.resolve("legacy-city-" + version + ".city");
        try (var out = new DataOutputStream(Files.newOutputStream(file))) {
            out.writeInt(0x43495430 + version);
            empty.config().write(out);
            out.writeDouble(0);
            out.writeInt(roads.size());
            for (var road : roads) {
                out.writeInt(road.x()); out.writeInt(road.z()); out.writeInt(road.y());
                if (version >= 10) out.writeByte(road.type());
            }
            // 18 bytes config, 8 bytes elapsed, 4 bytes empty road count; retain only the old tail.
            out.write(tail.toByteArray(), 30, tail.size() - 30);
        }
        return file;
    }

    @Test void loadsAndResavesEveryBasePavedRoadTypeWithoutChangingItsTypeByte() throws Exception {
        var terrain = new Terrain(Terrain.DEFAULT_SEED);
        int grade = terrain.column(8,24).height();
        var roads = List.of(new CityFrame.Road(52,50,grade,0), new CityFrame.Road(62,50,grade,1),
                new CityFrame.Road(72,50,grade,2), new CityFrame.Road(82,50,grade,3));
        for (int version : new int[]{10,11,12}) {
            var saved = CitySimulation.load(legacySave(version, roads));
            assertEquals(roads, saved.roads());
            assertEquals(Railway.State.empty(), saved.railway());
            var city = new CitySimulation(saved.config(), new RailwayTest.Ground(grade), terrain, saved);
            var upgrade = directory.resolve("upgraded-" + version + ".city");
            city.save(upgrade);
            try (var in = new DataInputStream(Files.newInputStream(upgrade))) {
                assertEquals(0x4349543E, in.readInt());
            }
            var loaded = CitySimulation.load(upgrade);
            assertEquals(roads, loaded.roads());
            assertEquals(saved.population(), loaded.population());
            assertEquals(saved.aviation(), loaded.aviation());
            assertEquals(Railway.State.empty(), loaded.railway());
        }
    }

    @Test void olderDirtRoadSaveStillMigratesAndNewRailsCannotUseOldFormats() throws Exception {
        var road = new CityFrame.Road(52,50,26,0);
        assertEquals(List.of(road), CitySimulation.load(legacySave(9,List.of(road))).roads());
        var terrain = new Terrain(Terrain.DEFAULT_SEED);
        var city = RailwayTest.fixture(new RailwayTest.Ground(terrain.column(8,24).height()));
        RailwayTest.build(city);
        for (int version : new int[]{10,11,12,13})
            assertThrows(IOException.class, () -> city.frame().write(new DataOutputStream(new ByteArrayOutputStream()),version));
        assertEquals(25, Protocol.VERSION);
        assertEquals(23, SpecialBuildings.AIRPORT);
        assertEquals(25, SpecialBuildings.RAIL_STATION);
        assertEquals(26, SpecialBuildings.RAIL_DEPOT);
        assertEquals(14, CityCommand.RAIL);
    }

    @Test void oldRoadChoicesAndRailToolsShareTheMenuWithoutChangingRoadCommands() {
        var fixture = new CityToolsTest();
        for (int choice=0; choice<7; choice++) {
            var tools=new dev.jayms.ui.CityTools(); tools.tool=4; tools.roadMenu=true;
            var commands=new ArrayList<CityCommand>();
            tools.click(200,149+choice*28,1280,720,fixture.projection,fixture.view,fixture.frame,commands::add);
            if(choice>=5) {
                assertEquals(6,tools.tool);assertEquals(choice==5?8:9,tools.specialKind);
                fixture.click(tools,0,24,commands);
                assertEquals(CityCommand.SPECIAL,commands.get(0).kind());
                assertEquals(choice==5?SpecialBuildings.RAIL_STATION:SpecialBuildings.RAIL_DEPOT,commands.get(0).value());
            } else {
                fixture.click(tools,0,24,commands);fixture.click(tools,20,24,commands);
                assertEquals(choice==4?CityCommand.RAIL:CityCommand.ROAD,commands.get(0).kind());
                assertEquals(choice==4?0:choice,commands.get(0).value());
            }
        }
    }
}
