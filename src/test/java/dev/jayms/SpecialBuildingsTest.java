package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.BuildingInfo;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;

class SpecialBuildingsTest {
    static class Ground implements CitySimulation.Ground {
        int grade; boolean occupied; int batches;
        Ground(int grade) { this.grade=grade; }
        public int type(int x,int y,int z) { return y<=grade ? Blocks.DIRT : 0; }
        public boolean occupied(int x,int y,int z,int w,int d) { return occupied; }
        public void apply(List<Protocol.Edit> edits) { batches++; assertTrue(edits.stream().allMatch(Protocol.Edit::valid)); }
    }
    CityCommand permit(int type,int x,int z) {
        return new CityCommand(CityCommand.SPECIAL,type,List.of(new Polygon.Point(x,z)),0,0);
    }
    @Test void placementPersistenceAndFutureToolsRespectEntireSite() throws Exception {
        var terrain=new Terrain(Terrain.DEFAULT_SEED);
        var ground=new Ground(terrain.column(8,24).height());
        var config=GameConfig.cityGame();
        var roads=new ArrayList<CityFrame.Road>();
        for(int x=50;x<56;x++) roads.add(new CityFrame.Road(x,50,ground.grade));
        var saved=new CityFrame(config,0,roads,List.of(),List.of(),List.of(),List.of());
        var city=new CitySimulation(config,ground,terrain,saved);
        assertEquals("Permitted City hall",city.command(permit(6,50,52),1,null));
        var building=city.frame().buildings().get(0);
        assertEquals(3,SpecialBuildings.level(building.type()));
        assertEquals(0,building.zone());
        assertTrue(BusinessMetrics.from(city.frame()).locations().stream().noneMatch(l -> l.building().id() == building.id()));
        var info=new BuildingInfo(); info.show(building.id(),0);
        assertTrue(info.lines(city.frame()).contains("Owner: City government"));
        int batches=ground.batches;
        assertTrue(city.command(permit(7,50,52),1,null).contains("overlaps"));
        assertTrue(city.command(permit(7,50,50),1,null).contains("roads"));
        assertTrue(city.command(new CityCommand(CityCommand.ROAD,0,List.of(new Polygon.Point(50,53),new Polygon.Point(55,53))),1,null).contains("special building"));
        assertTrue(city.command(new CityCommand(CityCommand.ZONE,0,List.of(new Polygon.Point(48,51),new Polygon.Point(60,51),new Polygon.Point(60,64),new Polygon.Point(48,64))),1,null).contains("special buildings"));
        assertEquals(batches,ground.batches);
        var bytes=new ByteArrayOutputStream(); city.frame().write(new DataOutputStream(bytes));
        var loaded=CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(city.frame().buildings(),loaded.buildings());
        var restored=new CitySimulation(config,ground,terrain,loaded);
        restored.advance(.1);
        assertNull(restored.economy.property(building.id()));
    }
    @Test void privateOwnersAndRejectedPermitsAreValidatedBeforeMutation() throws Exception {
        var terrain=new Terrain(Terrain.DEFAULT_SEED);
        var ground=new Ground(terrain.column(8,24).height());
        var config=GameConfig.cityGame();
        var seed=new CitySimulation(config,ground,terrain,null).frame();
        var roads=new ArrayList<CityFrame.Road>();
        for(int x=50;x<86;x++) roads.add(new CityFrame.Road(x,50,ground.grade));
        var saved=new CityFrame(config,0,roads,List.of(),List.of(),seed.citizens(),seed.horses(),seed.economy());
        var city=new CitySimulation(config,ground,terrain,saved);
        int citizen=seed.citizens().get(0).id(), company=seed.economy().firms().get(0).id();
        for(int kind=1;kind<=2;kind++) {
            int id=kind==1 ? citizen : company;
            assertTrue(city.command(new CityCommand(CityCommand.SPECIAL,SpecialBuildings.type(kind,2),
                    List.of(new Polygon.Point(50+(kind-1)*12,52)),kind,id),1,null).startsWith("Permitted"));
            var b=city.frame().buildings().get(kind-1);
            assertEquals(-kind,b.zone()); assertEquals(id,b.stock());
        }
        var before=city.frame().buildings(); int batches=ground.batches;
        assertEquals("Select an existing owner",city.command(new CityCommand(CityCommand.SPECIAL,8,
                List.of(new Polygon.Point(74,52)),2,Integer.MAX_VALUE),1,null));
        ground.occupied=true;
        assertTrue(city.command(permit(8,74,52),1,null).contains("player"));
        ground.occupied=false;
        assertTrue(city.command(permit(8,74,70),1,null).contains("road"));
        assertTrue(city.command(permit(8,263,52),1,null).contains("limits"));
        assertEquals(before,city.frame().buildings()); assertEquals(batches,ground.batches);
        city.advance(5);
        var bytes=new ByteArrayOutputStream(); city.frame().write(new DataOutputStream(bytes));
        var loaded=CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(before,loaded.buildings());
    }
    @Test void overheadObstructionsRejectAtomicallyAcrossBuildingAndEntranceVolume() {
        var terrain = new Terrain(Terrain.DEFAULT_SEED);
        int grade = terrain.column(8, 24).height();
        var config = GameConfig.cityGame();
        // Include the reported grade+10 case, the first unchecked layer, and world ceiling.
        for (int height : new int[] {grade + 8, grade + 10, Terrain.MAX_Y}) {
            for (var cell : List.of(new Polygon.Cell(52, 55),
                    new Polygon.Cell(50, 51), new Polygon.Cell(55, 59))) {
                var blocks = new HashMap<List<Integer>, Integer>();
                var obstruction = List.of(cell.x(), height, cell.z());
                blocks.put(obstruction, Blocks.STONE);
                var ground = new Ground(grade) {
                    @Override public int type(int x, int y, int z) {
                        return blocks.getOrDefault(List.of(x, y, z), super.type(x, y, z));
                    }
                    @Override public void apply(List<Protocol.Edit> edits) {
                        super.apply(edits);
                        for (var edit : edits)
                            blocks.put(List.of(edit.x(), edit.y(), edit.z()), edit.type());
                    }
                };
                var roads = new ArrayList<CityFrame.Road>();
                for (int x = 50; x < 56; x++) roads.add(new CityFrame.Road(x, 50, grade));
                var saved = new CityFrame(config, 0, roads, List.of(), List.of(), List.of(), List.of());
                var city = new CitySimulation(config, ground, terrain, saved);
                var beforeCity = city.frame();
                var beforeBlocks = Map.copyOf(blocks);
                assertEquals("Clear the building site first", city.command(permit(6, 50, 52), 1, null));
                assertEquals(0, ground.batches, "Rejected permit must not apply any world edits");
                assertEquals(beforeBlocks, blocks, "All existing blocks must survive rejection");
                assertEquals(Blocks.STONE, ground.type(cell.x(), height, cell.z()));
                assertEquals(beforeCity, city.frame(), "Rejected permit must not mutate city state");
            }
        }
    }

    @Test void demolitionAndSpecialPermitsHaveDistinctWireCommandsAndMatchingBlueprints() throws Exception {
        assertNotEquals(CityCommand.DEMOLISH, CityCommand.SPECIAL);
        var demolition = new CityCommand(CityCommand.DEMOLISH, 42, List.of());
        var special = permit(SpecialBuildings.type(3, 3), 50, 52);
        var bytes = new ByteArrayOutputStream();
        var out = new DataOutputStream(bytes);
        demolition.write(out);
        special.write(out);
        var in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(demolition, CityCommand.read(in));
        assertEquals(special, CityCommand.read(in));
        assertEquals(0, in.available());

        var terrain = new Terrain(Terrain.DEFAULT_SEED);
        int grade = terrain.column(8, 24).height();
        var blocks = new HashMap<List<Integer>, Integer>();
        var ground = new Ground(grade) {
            @Override public int type(int x, int y, int z) {
                return blocks.getOrDefault(List.of(x, y, z), super.type(x, y, z));
            }
            @Override public void apply(List<Protocol.Edit> edits) {
                super.apply(edits);
                for (var edit : edits)
                    blocks.put(List.of(edit.x(), edit.y(), edit.z()), edit.type());
            }
        };
        var roads = new ArrayList<CityFrame.Road>();
        for (int x = 50; x < 56; x++) roads.add(new CityFrame.Road(x, 50, grade));
        var city = new CitySimulation(GameConfig.cityGame(), ground, terrain,
                new CityFrame(GameConfig.cityGame(), 0, roads, List.of(), List.of(), List.of(), List.of()));
        assertTrue(city.command(special, 1, null).startsWith("Permitted"));
        var building = city.frame().buildings().get(0);
        var blueprint = StructureBlueprint.special(building.type(), building.x(), building.y(), building.z());
        assertTrue(city.command(new CityCommand(CityCommand.DEMOLISH, building.id(), List.of()), 1, null)
                .startsWith("Building demolished"));
        assertTrue(city.frame().buildings().isEmpty());
        for (var edit : blueprint)
            assertEquals(0, ground.type(edit.x(), edit.y(), edit.z()), "Demolition must clear civic lights and furniture");
    }

    @Test void catalogAndCommandRoundTrip() throws Exception {
        for(int kind=0;kind<5;kind++) for(int level=1;level<=3;level++) {
            int type=SpecialBuildings.type(kind,level);
            assertEquals(level,SpecialBuildings.level(type));
            assertFalse(SpecialBuildings.name(type).isBlank());
            var cmd=new CityCommand(CityCommand.SPECIAL,type,List.of(new Polygon.Point(60,52)),2,10000);
            var bytes=new ByteArrayOutputStream(); cmd.write(new DataOutputStream(bytes));
            assertEquals(cmd,CityCommand.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        }
    }
}
