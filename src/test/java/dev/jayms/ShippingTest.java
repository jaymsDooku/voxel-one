package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import java.io.*;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShippingTest {
    @TempDir Path temp;
    static final class Ground implements CitySimulation.Ground {
        final Terrain terrain; final Map<List<Integer>,Integer> edits = new HashMap<>();
        Ground(Terrain terrain) { this.terrain=terrain; }
        public int type(int x,int y,int z) { return edits.getOrDefault(List.of(x,y,z),terrain.block(x,y,z)); }
        public boolean occupied(int x,int y,int z,int w,int d) { return false; }
        public void apply(List<Protocol.Edit> batch) { for(var e:batch) edits.put(List.of(e.x(),e.y(),e.z()),e.type()); }
    }
    static int[] site(Terrain t) {
        for(int x=-100;x<120;x+=6) for(int z=200;z<250;z++) {
            boolean ok=true;
            for(int dx=0;dx<6;dx++) for(int dz=-1;dz<=20;dz++) {
                if(dz<3) ok &= t.fields(x+dx,z+dz).waterLevel()<t.column(x+dx,z+dz).height();
                if(dz>=14 && dz<20) ok &= t.ocean(x+dx,z+dz) && t.column(x+dx,z+dz).height()<=12;
                for(int y=27;y<=95;y++) if(t.block(x+dx,y,z+dz)!=0) ok=false;
            }
            if(ok) return new int[]{x,z};
        }
        throw new AssertionError("No valid coastal port site");
    }
    static CityCommand permit(int x,int z) { return new CityCommand(CityCommand.SPECIAL,SpecialBuildings.PORT,List.of(new Polygon.Point(x,z)),0,0); }
    static CitySimulation city(Terrain t,Ground g,int x,int z) {
        var cfg=GameConfig.cityGame();
        return new CitySimulation(cfg,g,t,new CityFrame(cfg,0,List.of(new CityFrame.Road(x,z-2,26)),List.of(),List.of(),List.of(),List.of()));
    }
    @Test void openOceanDeterministicAndOldVersionsStayUnchanged() {
        Terrain a=new Terrain(42), b=new Terrain(42);
        for(int x:new int[]{-1000,-16,0,16,1000}) for(int z:new int[]{300,448,580,1024,9000}) {
            assertTrue(a.ocean(x,z)); assertEquals(14,a.surfaceHeight(x,z));
            assertEquals(Blocks.WATER,a.block(x,14,z)); assertEquals(0,a.block(x,15,z));
            assertEquals(a.column(x,z),b.column(x,z)); assertEquals(a.fields(x,z),b.fields(x,z));
        }
        Terrain old=new Terrain(42,2);
        assertFalse(old.ocean(0,300));
        assertEquals(new Geography(42).fields(0,300),old.fields(0,300));
        assertFalse(a.ocean(64,24)); assertFalse(new Terrain(42,1).ocean(0,300));
        assertEquals(old.column(8,24),a.column(8,24));
    }
    @Test void versionTwoOfflineWorldKeepsItsGeneratorAfterReload() throws Exception {
        Path save=temp.resolve("synthetic-v2.dat");
        new LocalGame(save,42).save();
        byte[] data=Files.readAllBytes(save);
        java.nio.ByteBuffer.wrap(data).putInt(12,2);
        Files.write(save,data);
        var game=new LocalGame(save,99);
        assertEquals(42,game.seed); assertEquals(2,game.generatorVersion);
        game.save();
        var restored=new LocalGame(save,99);
        assertEquals(2,restored.generatorVersion);
        assertEquals(new Geography(42).fields(0,300),new Terrain(restored.seed,restored.generatorVersion).fields(0,300));
    }
    @Test void coastalPermitAndSaveReloadAndDemolition() throws Exception {
        Terrain t=new Terrain(Terrain.DEFAULT_SEED); int[] p=site(t); Ground g=new Ground(t);
        var city=city(t,g,p[0],p[1]);
        assertEquals("Permitted Coastal port",city.command(permit(p[0],p[1]),1,null));
        var port=city.frame().buildings().get(0); assertEquals(SpecialBuildings.PORT,port.type());
        assertEquals(Blocks.WATER,g.type(p[0],14,p[1]+15));
        assertEquals(Blocks.STONE,g.type(p[0]+1,15,p[1]+14));
        int count=g.edits.size();
        assertTrue(city.command(new CityCommand(CityCommand.ROAD,0,List.of(
                new Polygon.Point(p[0]-4,p[1]+18),new Polygon.Point(p[0]+8,p[1]+18))),1,null).contains("special building"));
        assertEquals(count,g.edits.size()); assertNotEquals("Permitted Coastal port",city.command(permit(p[0],p[1]),1,null)); assertEquals(count,g.edits.size());
        var bytes=new ByteArrayOutputStream();city.frame().write(new DataOutputStream(bytes));
        var saved=CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(port,saved.buildings().get(0));
        var restored=new CitySimulation(GameConfig.cityGame(),g,t,saved);
        assertEquals(port,restored.frame().buildings().get(0));
        assertTrue(restored.command(new CityCommand(CityCommand.DEMOLISH,port.id(),List.of()),1,null).contains("demolished"));
        assertEquals(0,g.type(p[0]+1,15,p[1]+14));
        assertEquals(Blocks.WATER,g.type(p[0],14,p[1]+15));
        assertEquals(Blocks.WATER,g.type(p[0],14,p[1]+12));
        assertTrue(Shipping.port(p[0],27,p[1]).stream().allMatch(Protocol.Edit::valid));
    }
    @Test void inlandRiverAndBlockedBerthRejectAtomically() {
        Terrain t=new Terrain(Terrain.DEFAULT_SEED); Ground g=new Ground(t);
        var inland=city(t,g,10,40); assertNotEquals("Permitted Coastal port",inland.command(permit(10,40),1,null));
        assertTrue(inland.frame().buildings().isEmpty()); assertTrue(g.edits.isEmpty());
        int riverX=java.util.stream.IntStream.range(30,100)
                .filter(x -> t.fields(x,24).waterLevel()>t.column(x,24).height()).findFirst().orElseThrow();
        var river=city(t,g,riverX,24);
        assertTrue(river.command(permit(riverX,24),1,null).contains("dry coastal land"));
        assertTrue(g.edits.isEmpty());
        int[] p=site(t); var coastal=city(t,g,p[0],p[1]);
        g.edits.put(List.of(p[0]+2,16,p[1]+14),Blocks.STONE); int before=g.edits.size();
        assertTrue(coastal.command(permit(p[0],p[1]),1,null).contains("Clear the dock"));
        assertEquals(before,g.edits.size()); assertTrue(coastal.frame().buildings().isEmpty());
    }
    @Test void formatElevenRegionalSaveRetainsDistrictsWhenAddingShipping() throws Exception {
        Terrain t=new Terrain(Terrain.DEFAULT_SEED); Ground g=new Ground(t); int[] p=site(t);
        var original=city(t,g,p[0],p[1]);
        assertTrue(original.command(new CityCommand(CityCommand.SETTLE_DISTRICT,1_000_000,List.of()),1,null).contains("settled"));
        original.command(new CityCommand(CityCommand.FOCUS_DISTRICT,1,List.of()),1,null);
        var population=original.frame().population();
        assertEquals(1_000_000,population.population()); assertEquals(64,population.agents().size());
        var district=population.groups().get(0);
        assertTrue(district.y()>t.surfaceHeight((int)district.x(),(int)district.z()),
                "New nearby district residents must remain above ocean water");
        Path base=temp.resolve("base-format11.city");
        try(var out=new DataOutputStream(Files.newOutputStream(base))) {
            out.writeInt(0x4349543B); original.frame().write(out,11);
        }
        try(var in=new DataInputStream(Files.newInputStream(base))) { assertEquals(0x4349543B,in.readInt()); }
        var saved=CitySimulation.load(base); assertEquals(population,saved.population());
        var restored=new CitySimulation(saved.config(),g,t,saved);
        assertEquals("Permitted Coastal port",restored.command(permit(p[0],p[1]),1,null));
        assertEquals(population,restored.frame().population());
        Path combined=temp.resolve("regional-shipping-format11.city");restored.save(combined);
        var loaded=CitySimulation.load(combined);
        assertEquals(population,loaded.population()); assertEquals(SpecialBuildings.PORT,loaded.buildings().get(0).type());
        var bytes=new ByteArrayOutputStream();loaded.write(new DataOutputStream(bytes),11);
        assertEquals(loaded,CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),11));
        assertTrue(Protocol.VERSION>21,"Shipping must not reuse the pre-shipping wire version");
    }
    @Test void cargoComponentsAndCatalogRegression() {
        assertEquals(16,Shipping.container(0,0,0,Blocks.BRICKS).stream().filter(e->e.depth()==0).count());
        assertTrue(Shipping.carrier(0,15,0).stream().anyMatch(e->e.type()==Blocks.GLASS));
        assertEquals(24,SpecialBuildings.type(6,3)); assertEquals(1,SpecialBuildings.level(24));
        assertEquals(23,SpecialBuildings.AIRPORT); assertNotEquals(SpecialBuildings.AIRPORT,SpecialBuildings.PORT);
        assertEquals("Technical college level 3",SpecialBuildings.name(22));
        assertEquals(20,StructureBlueprint.depth(24)); assertEquals(7,StructureBlueprint.depth(4));
    }
}
