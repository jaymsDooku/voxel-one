package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.net.city.Polygon.Cell;
import dev.jayms.net.city.parcel.*;
import dev.jayms.net.city.parcel.ParcelGenerator.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class ParcelPortfolioTest {
    @TempDir Path temp;
    private Request request(Polygon polygon,int target) {
        var cells=polygon.cells();var front=new LinkedHashSet<Cell>();
        int z=cells.stream().mapToInt(Cell::z).min().orElse(0);
        cells.stream().filter(c->c.z()==z).forEach(front::add);
        return new Request(cells,front,target,7,c->1,List.of());
    }
    @Test void allAlgorithmsCoverZonesWithoutOverlapAndAreDeterministic() {
        var polygons=List.of(CityTest.box(10,40,32,28),new Polygon(List.of(new Polygon.Point(0,40),new Polygon.Point(40,40),new Polygon.Point(10,75))));
        for(var polygon:polygons)for(var algorithm:ParcelPortfolio.Algorithm.values()) {
            var r=request(polygon,144);var result=algorithm.generate(r);
            assertFalse(result.isEmpty(),algorithm.label);ZoneParceling.validate(polygon,result);
            assertEquals(result,algorithm.generate(r),algorithm.label);
        }
    }
    @Test void emptyAndDisconnectedMasksAndNegativeCoordinates() {
        for(var algorithm:ParcelPortfolio.Algorithm.values()) {
            assertEquals(List.of(),algorithm.generate(new Request(Set.of(),Set.of(),16,0,c->1,List.of())));
            var cells=Set.of(new Cell(-10,-10),new Cell(-9,-10),new Cell(4,4),new Cell(5,4));
            var result=algorithm.generate(new Request(cells,Set.of(),16,0,c->1,List.of()));
            assertEquals(cells,new HashSet<>(result.stream().flatMap(p->p.cells().stream()).toList()));
            assertTrue(result.size()>=2);
        }
    }
    @Test void repairClipsOverlapFillsGapsAndSeparatesComponents() {
        var p=CityTest.box(0,40,12,12);var r=request(p,144);
        var bad=List.of(Set.of(new Cell(-20,-20),new Cell(0,40),new Cell(10,50)),Set.of(new Cell(0,40)));
        var repaired=ParcelPortfolio.repair(r,bad);ZoneParceling.validate(p,repaired);
        assertEquals(144,repaired.stream().mapToInt(a->a.cells().size()).sum());
    }
    @Test void mergeSplitAndIncrementalUsePriorParcels() {
        var p=CityTest.box(0,40,24,24);var r=request(p,144);var grid=ParcelPortfolio.Algorithm.GRID.generate(r);
        var prior=new Request(r.cells(),r.frontage(),144,7,c->1,grid);
        var merged=ParcelPortfolio.Algorithm.MERGE.generate(prior);var split=ParcelPortfolio.Algorithm.SPLIT.generate(prior);
        assertTrue(merged.size()<grid.size());assertTrue(split.size()>grid.size());
        assertEquals(grid,ParcelPortfolio.Algorithm.HISTORICAL.generate(prior));
        ZoneParceling.validate(p,merged);ZoneParceling.validate(p,split);
    }
    @Test void roadFrontageSplitKeepsBothChildrenOnRoad() {
        var r=request(CityTest.box(0,40,48,16),144);
        var result=ParcelPortfolio.Algorithm.ROAD_FRONTAGE.generate(r);
        assertTrue(result.size()>1);assertTrue(result.stream().allMatch(p->p.cells().stream().anyMatch(r.frontage()::contains)));
    }
    @Test void terrainCostsChangeOwnershipAndRejectInvalidCost() {
        var r=request(CityTest.box(0,40,24,24),144);
        var costly=new Request(r.cells(),r.frontage(),144,7,c->c.x()==10?100:1,List.of());
        assertNotEquals(ParcelPortfolio.Algorithm.TERRAIN_GROWTH.generate(r),ParcelPortfolio.Algorithm.TERRAIN_GROWTH.generate(costly));
        assertThrows(IllegalArgumentException.class,()->ParcelPortfolio.Algorithm.TERRAIN_GROWTH.generate(new Request(r.cells(),r.frontage(),144,7,c->Double.NaN,List.of())));
    }
    @Test void zoneSelectionRepairAndSaveRoundTrip() throws Exception {
        var ground=new CityTest.Ground();var sim=new CityTest().simulation(ground);
        var pose=new Protocol.Pose(1,8,40,24,0,0);
        assertTrue(sim.command(new CityCommand(CityCommand.ROAD,0,List.of(new Polygon.Point(80,80),new Polygon.Point(125,80))),1,pose).contains("built"));
        var polygon=CityTest.box(82,82,36,24);
        var command=new CityCommand(CityCommand.ZONE,4*(ParcelPortfolio.Algorithm.GRID.ordinal()+1),polygon.vertices());
        String response=sim.command(command,1,pose);assertTrue(response.contains("created"),response);
        var zone=sim.frame().zones().get(sim.frame().zones().size()-1);
        assertFalse(zone.parcels().isEmpty());assertFalse(ZoneParceling.sites(zone).isEmpty());
        var saved=temp.resolve("synthetic.city");sim.save(saved);assertEquals(sim.frame(),CitySimulation.load(saved));
        var bytes=new ByteArrayOutputStream();command.write(new DataOutputStream(bytes));assertEquals(command,CityCommand.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        String merged=sim.command(new CityCommand(CityCommand.PARCEL,zone.id(),List.of(new Polygon.Point(12,0))),1,pose);assertTrue(merged.contains("parcels"),merged);
        assertThrows(IOException.class,()->sim.frame().write(new DataOutputStream(new ByteArrayOutputStream()),15));
    }
    @Test void purchasedParcelDoesNotOfferAnotherBuildingSite() throws Exception {
        var ground=new CityTest.Ground();var sim=new CityTest().simulation(ground);
        var polygon=CityTest.box(80,82,24,24);
        sim.command(new CityCommand(CityCommand.ROAD,0,List.of(new Polygon.Point(78,80),new Polygon.Point(108,80))),1,null);
        String result=sim.command(new CityCommand(CityCommand.ZONE,12,polygon.vertices()),1,null);
        assertTrue(result.contains("created"),result);
        var zone=sim.frame().zones().get(sim.frame().zones().size()-1);
        var c=ZoneParceling.sites(zone).get(0);var parcel=zone.parcels().stream().filter(p->p.cells().contains(c)).findFirst().orElseThrow();
        assertNotNull(sim.economy.buyPlot(zone.id(),zone.type(),c.x(),ground.terrain.column(8,24).height()+1,c.z()));
        var method=CitySimulation.class.getDeclaredMethod("availableParcelSites",CityFrame.Zone.class);method.setAccessible(true);
        @SuppressWarnings("unchecked") var available=(List<Cell>)method.invoke(sim,zone);
        assertTrue(available.stream().noneMatch(parcel.cells()::contains));
        String refused=sim.command(new CityCommand(CityCommand.PARCEL,zone.id(),List.of(new Polygon.Point(1,0))),1,null);
        assertTrue(refused.contains("without buildings or purchased plots"),refused);assertEquals(zone,sim.frame().zones().get(sim.frame().zones().size()-1));
    }
    @Test void previewKeySelectionSetsZoneCommand() {
        var tools=new dev.jayms.ui.CityTools();tools.tool=0;
        var commands=new ArrayList<CityCommand>();assertTrue(tools.key(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_BRACKET,commands::add));assertEquals(3,tools.parcelAlgorithm);
        assertTrue(tools.key(org.lwjgl.glfw.GLFW.GLFW_KEY_V,commands::add));assertTrue(tools.parcelPreview);
        assertTrue(tools.key(org.lwjgl.glfw.GLFW.GLFW_KEY_P,commands::add));assertTrue(tools.parcelApply);
        assertTrue(tools.key(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,commands::add));assertFalse(tools.parcelApply);
    }
}
