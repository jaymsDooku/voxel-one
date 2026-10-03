package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.*;

import org.joml.Vector3f;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.util.*;

class CityMaterialsTest {
    @TempDir Path directory;

    private long amount(List<CityMaterials.Amount> recipe, int material) {
        return recipe.stream()
                .filter(a -> a.material() == material)
                .mapToLong(CityMaterials.Amount::units)
                .sum();
    }

    private CitySimulation city(CityTest.Ground ground) {
        return new CityTest().simulation(ground);
    }

    @Test
    void recipesMatchTheActualBlueprintIncludingFractionalBeams() {
        var house = CityMaterials.requirements(0);
        assertEquals(96 * CityMaterials.UNIT, amount(house, Blocks.PLANKS));
        assertEquals(84 * CityMaterials.UNIT, amount(house, Blocks.BRICKS));
        assertEquals(6 * 512, amount(house, Blocks.WOOD));
        assertEquals(4 * CityMaterials.UNIT, amount(house, Blocks.GLASS));
        assertEquals(CityMaterials.UNIT, amount(house, Blocks.LED));
        assertEquals(41 * CityMaterials.UNIT, amount(CityMaterials.requirements(2), Blocks.STONE));
        assertEquals(
                138 * CityMaterials.UNIT, amount(CityMaterials.requirements(1), Blocks.BRICKS));
    }

    @Test
    void OtherOwnersStocksDoNotAuthorizeConstructionAndTradingTransfersCashAndVolume() {
        var s = city(new CityTest.Ground());
        var e = s.economy;
        var p = e.buyPlot(1, 0, 12, 30, 28);
        var recipe = e.resources.plan(p, 0);
        var seller =
                e.companies().stream()
                        .filter(c -> c.kind == CityMaterials.LOGGING)
                        .findFirst()
                        .orElseThrow();
        for (var a : recipe.materials()) e.resources.add(0, seller.id, a.material(), a.units());
        e.work(p.id(), 20);
        assertEquals(0, e.project(p.id()).work());
        assertFalse(e.resources.reserve(p));
        int citizen = s.frame().citizens().get(0).id();
        e.resources.add(1, citizen, Blocks.PLANKS, 1000 * CityMaterials.UNIT);
        assertFalse(
                e.resources.reserve(p), "Citizen inventories are not a public construction pool");
        double cash = seller.cash + e.company(p.developer()).cash;
        for (var a : recipe.materials())
            assertTrue(e.trade(0, seller.id, 0, p.developer(), a.material(), a.units()));
        assertEquals(cash, seller.cash + e.company(p.developer()).cash, 1e-8);
        assertTrue(e.resources.reserve(p));
        assertTrue(e.resources.project(p.id()).reserved());
        for (var a : recipe.materials())
            assertEquals(0, e.resources.available(0, p.developer(), a.material()));
        assertEquals(1000 * CityMaterials.UNIT, e.resources.available(1, citizen, Blocks.PLANKS));
        e.work(p.id(), 1);
        assertEquals(1, e.project(p.id()).work());
        assertTrue(e.resources.consume(p));
        assertFalse(e.resources.consume(p));
    }

    @Test
    void DeveloperOwnedMaterialsReduceTheCashRequiredToAcquireAPlot() {
        var s = city(new CityTest.Ground());
        var e = s.economy;
        var developer = e.companies().stream().filter(c -> c.kind == 0).findFirst().orElseThrow();
        for (var c : e.companies()) if (c.kind == 0) c.cash = 0;
        developer.cash = 40;
        for (var a : CityMaterials.requirements(0))
            e.resources.add(0, developer.id, a.material(), a.units());
        var plot = e.buyPlot(1, 0, 12, 30, 28);
        assertNotNull(plot);
        assertEquals(developer.id, plot.developer());
        assertEquals(16, developer.cash);
        assertTrue(e.resources.reserve(plot));
    }

    @Test
    void ReservationsPreventDoubleUseAndInsufficientFundsLeaveBothOwnersUnchanged() {
        var s = city(new CityTest.Ground());
        var e = s.economy;
        var p = e.buyPlot(1, 0, 12, 30, 28);
        var other = new CityEconomy.Plot(2, 1, 0, 24, 30, 28, p.developer(), 24, 90, 0, 0);
        e.plots.add(other);
        for (var a : CityMaterials.requirements(0))
            e.resources.add(0, p.developer(), a.material(), a.units());
        assertTrue(e.resources.reserve(p));
        assertFalse(e.resources.reserve(other));
        var seller = e.companies().stream().filter(c -> c.kind == 2).findFirst().orElseThrow();
        e.resources.add(0, seller.id, Blocks.STONE, CityMaterials.UNIT);
        e.company(p.developer()).cash = 0;
        var before = e.state();
        assertFalse(e.trade(0, seller.id, 0, p.developer(), Blocks.STONE, CityMaterials.UNIT));
        assertEquals(before, e.state());
        assertFalse(e.trade(0, seller.id, 0, p.developer(), Blocks.STONE, -1));
        assertEquals(before, e.state());
    }

    @Test
    void RefiningUsesThePlayerRecipesAndCannotManufactureMissingInputs() {
        var m = new CityMaterials(CityMaterials.State.empty());
        assertEquals(0, m.craft(1, Blocks.PLANKS));
        m.add(0, 1, Blocks.WOOD, CityMaterials.UNIT);
        assertEquals(4, m.craft(1, Blocks.PLANKS));
        assertEquals(0, m.available(0, 1, Blocks.WOOD));
        assertEquals(4 * CityMaterials.UNIT, m.available(0, 1, Blocks.PLANKS));
        assertEquals(0, m.craft(1, Blocks.LED));
        m.add(0, 1, Blocks.GLASS, CityMaterials.UNIT);
        m.add(0, 1, Blocks.STONE, CityMaterials.UNIT);
        assertEquals(1, m.craft(1, Blocks.LED));
        assertEquals(0, m.available(0, 1, Blocks.GLASS));
        assertEquals(0, m.available(0, 1, Blocks.STONE));
    }

    @Test
    void IndividualsCanBuyAndKeepMaterialsWithoutSupplyingUnrelatedDevelopers() {
        var s = city(new CityTest.Ground());
        var e = s.economy;
        var f =
                e.companies().stream()
                        .filter(c -> c.kind == CityMaterials.LOGGING)
                        .findFirst()
                        .orElseThrow();
        int citizen = s.frame().citizens().get(0).id();
        float cash = s.ecs.get(citizen, CitySimulation.Needs.class).money;
        e.resources.add(0, f.id, Blocks.WOOD, CityMaterials.UNIT);
        double businessCash = f.cash;
        assertTrue(e.trade(0, f.id, 1, citizen, Blocks.WOOD, CityMaterials.UNIT));
        assertEquals(cash - .5, s.ecs.get(citizen, CitySimulation.Needs.class).money);
        assertEquals(businessCash + .5, f.cash);
        assertEquals(CityMaterials.UNIT, e.resources.available(1, citizen, Blocks.WOOD));
        assertEquals(0, e.resources.available(0, f.id, Blocks.WOOD));
    }

    @Test
    void MissingNaturalWoodStopsEveryBuildingAndNeverAdvancesConstructionWork() {
        var g =
                new CityTest.Ground() {
                    @Override
                    public int type(int x, int y, int z) {
                        int t = super.type(x, y, z);
                        return t == Blocks.WOOD ? 0 : t;
                    }
                };
        var s = city(g);
        for (int i = 0; i < 180; i++) s.advance(1);
        assertFalse(s.frame().economy().plots().isEmpty());
        assertTrue(s.frame().buildings().isEmpty());
        assertTrue(s.frame().economy().plots().stream().allMatch(p -> p.work() == 0));
        var mill =
                s.economy.companies().stream()
                        .filter(c -> c.kind == CityMaterials.LOGGING)
                        .findFirst()
                        .orElseThrow();
        assertEquals(0, s.economy.resources.production(mill.id).harvested());
        assertEquals("Natural deposit exhausted", s.economy.resources.production(mill.id).status());
    }

    private long embodied(CityFrame f, int material) {
        long n =
                f.economy().resources().stocks().stream()
                        .filter(s -> s.material() == material)
                        .mapToLong(CityMaterials.Stock::units)
                        .sum();
        for (var p : f.economy().resources().projects())
            if (p.reserved() || p.consumed()) n += amount(p.materials(), material);
        return n;
    }

    @Test
    void StarterCityConservesEveryHarvestedMaterialThroughTradeRefiningAndConstruction() {
        var removed = new HashMap<Integer, Long>();
        var g =
                new CityTest.Ground() {
                    @Override
                    public void apply(List<Protocol.Edit> batch) {
                        if (batch.size() == 1 && batch.get(0).type() == 0) {
                            var e = batch.get(0);
                            int t = type(e.x(), e.y(), e.z());
                            removed.merge(t, 1L, Long::sum);
                        }
                        super.apply(batch);
                    }
                };
        var s = city(g);
        double cash = CityEconomyTestCash(s.frame());
        for (int i = 0; i < 320; i++) s.advance(1);
        var f = s.frame();
        assertEquals(5, f.buildings().size());
        assertEquals(11, f.economy().firms().size());
        assertTrue(
                f.economy().resources().projects().stream()
                        .allMatch(CityMaterials.Project::consumed));
        assertTrue(
                f.citizens().stream()
                        .allMatch(c -> c.home() > 0 && CityMetrics.employer(f, c) != 0));
        assertTrue(
                f.economy().resources().stocks().stream()
                        .anyMatch(stock -> stock.ownerKind() == 1));
        assertEquals(
                removed.get(Blocks.WOOD) * CityMaterials.UNIT,
                embodied(f, Blocks.WOOD) + embodied(f, Blocks.PLANKS) / 4);
        assertEquals(
                removed.get(Blocks.STONE) * CityMaterials.UNIT,
                embodied(f, Blocks.STONE) + embodied(f, Blocks.BRICKS) + embodied(f, Blocks.LED));
        assertEquals(
                removed.get(Blocks.SAND) * CityMaterials.UNIT,
                embodied(f, Blocks.SAND) + embodied(f, Blocks.GLASS) + embodied(f, Blocks.LED));
        assertEquals(cash, CityEconomyTestCash(f), .08);
        var farm =
                f.economy().firms().stream()
                        .filter(c -> c.kind() == CityMaterials.FARM)
                        .findFirst()
                        .orElseThrow();
        var shop =
                f.economy().firms().stream().filter(c -> c.kind() == 1).findFirst().orElseThrow();
        long harvest =
                f.economy().resources().production().stream()
                        .filter(p -> p.company() == farm.id())
                        .findFirst()
                        .orElseThrow()
                        .harvested();
        long meals =
                f.economy().businesses().stream()
                        .filter(a -> a.company() == shop.id())
                        .mapToLong(a -> a.total().sold())
                        .sum();
        assertTrue(meals > 0);
        assertEquals(
                harvest * CityMaterials.UNIT,
                embodied(f, CityMaterials.FOOD) + meals * CityMaterials.UNIT);
    }

    private double CityEconomyTestCash(CityFrame f) {
        return f.economy().budget()
                + f.economy().roadSpending()
                + f.economy().firms().stream().mapToDouble(CityEconomy.Firm::cash).sum()
                + f.citizens().stream().mapToDouble(CityFrame.Citizen::money).sum();
    }

    @Test
    void MaterialsAndMidConstructionReservationsSurviveSaveAndNetworkRoundTrips() throws Exception {
        var g = new CityTest.Ground();
        var s = city(g);
        for (int i = 0; i < 60; i++) s.advance(1);
        assertTrue(
                s.frame().economy().resources().projects().stream()
                        .anyMatch(CityMaterials.Project::reserved));
        Path save = directory.resolve("resources.city");
        s.save(save);
        var f = CitySimulation.load(save);
        assertEquals(s.frame(), f);
        var bytes = new ByteArrayOutputStream();
        f.write(new DataOutputStream(bytes));
        assertEquals(
                f,
                CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        var restored = new CitySimulation(f.config(), g, g.terrain, f);
        assertEquals(f.economy(), restored.frame().economy());
        for (int i = 0; i < 100; i++) restored.advance(1);
        assertEquals(5, restored.frame().buildings().size());
    }

    @Test
    void CityThreeMigrationPreservesHistoricalPropertiesAndBalancesWithoutInventingMaterials()
            throws Exception {
        var g = new CityTest.Ground();
        var s = city(g);
        for (int i = 0; i < 160; i++) s.advance(1);
        Path save = directory.resolve("old.city");
        try (var out = new DataOutputStream(Files.newOutputStream(save))) {
            out.writeInt(0x43495433);
            var f = s.frame();
            new CityFrame(
                            new GameConfig(true, false, 1200, 18),
                            f.elapsed(),
                            f.roads(),
                            f.zones(),
                            f.buildings(),
                            f.citizens(),
                            f.horses(),
                            f.economy())
                    .write(out, 3);
        }
        var old = CitySimulation.load(save);
        assertEquals(CityMaterials.State.empty(), old.economy().resources());
        var restored = new CitySimulation(old.config(), g, g.terrain, old);
        assertEquals(old.economy(), CityCapitalTest.withoutCapital(restored.frame().economy()));
        assertTrue(
                restored.economy.capital.state().book().listings().stream()
                        .allMatch(l -> !l.publicCompany()));
        assertEquals(old.buildings(), restored.frame().buildings());
        assertEquals(old.elapsed(), restored.frame().elapsed());
        var inspector = new BuildingInfo();
        inspector.show(old.buildings().get(0).id(), 0);
        assertTrue(
                inspector.lines(old).stream()
                        .anyMatch(
                                line ->
                                        line.contains(
                                                "historical construction materials were not"
                                                        + " recorded")));
        assertFalse(
                inspector.lines(old).stream()
                        .anyMatch(line -> line.contains("AVAILABLE / REQUIRED")));
        restored.advance(1);
        assertEquals(5, restored.frame().buildings().size());
        assertTrue(
                restored.frame().buildings().stream()
                        .filter(b -> b.type() == 2)
                        .allMatch(b -> b.stock() == 0),
                "Old abstract supplies do not become natural stone at a closed quarry");
        restored.save(save);
        assertEquals(restored.frame(), CitySimulation.load(save));
    }

    @Test
    void MalformedDuplicateNegativeOrRecipeChangingStocksAreRejected() throws Exception {
        for (var state :
                List.of(
                        new CityMaterials.State(
                                List.of(new CityMaterials.Stock(0, 1, Blocks.WOOD, -1)),
                                List.of(),
                                List.of()),
                        new CityMaterials.State(
                                List.of(
                                        new CityMaterials.Stock(0, 1, Blocks.WOOD, 4096),
                                        new CityMaterials.Stock(0, 1, Blocks.WOOD, 4096)),
                                List.of(),
                                List.of()),
                        new CityMaterials.State(
                                List.of(),
                                List.of(
                                        new CityMaterials.Production(
                                                1, Double.NaN, 0, 0, "Working")),
                                List.of()))) {
            var bytes = new ByteArrayOutputStream();
            CityMaterials.write(new DataOutputStream(bytes), state);
            assertThrows(
                    IOException.class,
                    () ->
                            CityMaterials.read(
                                    new DataInputStream(
                                            new ByteArrayInputStream(bytes.toByteArray()))));
        }
        var s = city(new CityTest.Ground());
        var p = s.economy.buyPlot(1, 0, 12, 30, 28);
        var base = s.economy.state();
        var forged =
                new CityEconomy.State(
                        base.budget(),
                        base.roadSpending(),
                        base.landRevenue(),
                        base.rentClock(),
                        base.firms(),
                        base.plots(),
                        base.properties(),
                        base.contracts(),
                        base.businesses(),
                        new CityMaterials.State(
                                List.of(),
                                List.of(),
                                List.of(
                                        new CityMaterials.Project(
                                                p.id(), 0, true, false, List.of()))));
        var bytes = new ByteArrayOutputStream();
        forged.write(new DataOutputStream(bytes));
        assertThrows(
                IOException.class,
                () ->
                        CityEconomy.State.read(
                                new DataInputStream(
                                        new ByteArrayInputStream(bytes.toByteArray()))));
    }

    @Test
    void ExpandedIndustrialZonesConstructDistinctMaterialFundedWorkshopsForAllResourceCompanies() {
        var g = new CityTest.Ground();
        var s = city(g);
        var pose = new Protocol.Pose(1, 8, 40, 24, 0, 0);
        assertTrue(
                s.command(
                                new CityCommand(
                                        CityCommand.ROAD,
                                        0,
                                        List.of(
                                                new Polygon.Point(-10, 24),
                                                new Polygon.Point(-60, 24))),
                                1,
                                pose)
                        .contains("Mayor paid"));
        assertTrue(
                s.command(
                                new CityCommand(
                                        CityCommand.ZONE,
                                        2,
                                        CityTest.box(-50, 26, 40, 24).vertices()),
                                1,
                                pose)
                        .contains("created"));
        for (int i = 0; i < 500; i++) s.advance(1);
        var f = s.frame();
        for (int kind = 2; kind <= CityMaterials.TOOLS; kind++) {
            final int k = kind;
            var company =
                    f.economy().firms().stream()
                            .filter(c -> c.kind() == k)
                            .findFirst()
                            .orElseThrow();
            var property =
                    f.economy().properties().stream()
                            .filter(p -> p.operator() == company.id())
                            .findFirst()
                            .orElseThrow();
            var b =
                    f.buildings().stream()
                            .filter(v -> v.id() == property.building())
                            .findFirst()
                            .orElseThrow();
            var p =
                    f.economy().plots().stream()
                            .filter(v -> v.building() == b.id())
                            .findFirst()
                            .orElseThrow();
            assertTrue(f.economy().resources().project(p.id()).consumed());
            assertEquals(k, f.economy().resources().project(p.id()).businessKind());
            assertEquals(
                    k == 2 ? Blocks.AIR : Blocks.PLANKS,
                    g.type(b.x() + 2, b.y(), b.z() + 3),
                    "Only the quarry has an open mine shaft");
        }
        assertEquals(11, f.buildings().size());
    }

    @Test
    void PickingFindsTheNearestBuildingAndInspectorShowsLiveOwnedRequirements() {
        var s = city(new CityTest.Ground());
        s.advance(1);
        var f = s.frame();
        var p = f.economy().plots().get(0);
        var tools = new CityTools();
        assertTrue(
                tools.selectRay(
                        new Vector3f(p.x() + 3, p.y() + 20, p.z() + 3),
                        new Vector3f(0, -1, 0),
                        30,
                        f));
        assertEquals(p.id(), tools.selectedPlot);
        var ui = new BuildingInfo();
        ui.show(0, p.id());
        assertTrue(ui.open);
        assertTrue(
                ui.lines(f).stream()
                        .anyMatch(line -> line.contains("Waiting for developer-owned")));
        assertTrue(ui.lines(f).stream().anyMatch(line -> line.startsWith("Planks: 0 / 96")));
        for (int i = 0; i < 160; i++) s.advance(1);
        f = s.frame();
        var b = f.buildings().get(0);
        assertTrue(
                tools.selectRay(
                        new Vector3f(b.x() + 3, b.y() + 20, b.z() + 3),
                        new Vector3f(0, -1, 0),
                        30,
                        f));
        assertEquals(b.id(), tools.selectedBuilding);
        assertEquals(0, tools.selectedPlot);
        ui.show(b.id(), 0);
        assertTrue(ui.lines(f).stream().anyMatch(line -> line.contains("used in this building")));
        assertFalse(tools.selectRay(new Vector3f(1000, 200, 1000), new Vector3f(0, -1, 0), 12, f));
    }
}
