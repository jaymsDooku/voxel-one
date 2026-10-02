package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.BuildingInfo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.util.*;

class ManufacturingTest {
    @TempDir Path directory;

    private CityEconomy.Company firm(CitySimulation s, int kind) {
        return s.economy.companies().stream().filter(c -> c.kind == kind).findFirst().orElseThrow();
    }

    private CitySimulation city(CityTest.Ground ground) {
        return new CityTest().simulation(ground);
    }

    private CityHarvesting work(CityTest.Ground ground) {
        return new CityHarvesting(ground, ground.terrain, (x, z) -> false);
    }

    private void premises(CitySimulation s, int company) {
        s.economy.properties.add(new CityEconomy.Property(400, 0, company, company, 400, 6));
    }

    @Test
    void configurationMatchesDefaultAndRejectsBadInputsAndBonus() throws Exception {
        assertEquals(
                ProductionCatalog.cityGame(),
                ProductionCatalog.load(Path.of("config/city-production.properties")));
        var c = ProductionCatalog.cityGame();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ProductionCatalog(
                                c.products(),
                                List.of(
                                        new ProductionCatalog.Recipe(
                                                "free", 8, 1002, 1, Map.of(), 2, 16, true)),
                                c.equipment()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ProductionCatalog(
                                c.products(),
                                c.recipes(),
                                List.of(new ProductionCatalog.Equipment(1002, 2, Double.NaN))));
        Path bad = directory.resolve("bad.properties");
        Files.writeString(bad, "products=1001\nrecipes=oops\n");
        assertThrows(IOException.class, () -> ProductionCatalog.load(bad));
    }

    @Test
    void factoryRequiresPremisesAndPurchasesInputsAndBothToolsWithoutPublicMoney() {
        var ground = new CityTest.Ground();
        var s = city(ground);
        var e = s.economy;
        var worker = work(ground);
        var factory = firm(s, CityMaterials.TOOLS);
        var mine = firm(s, 2);
        var logging = firm(s, 3);
        e.resources.add(0, mine.id, Blocks.STONE, 20 * CityMaterials.UNIT);
        e.resources.add(0, logging.id, Blocks.WOOD, 20 * CityMaterials.UNIT);
        var before = e.state();
        worker.work(e, factory, 1);
        assertEquals(before.resources().stocks(), e.state().resources().stocks());
        assertEquals("Needs industrial factory", e.resources.production(factory.id).status());
        premises(s, factory.id);
        double budget = e.budget, total = e.companies().stream().mapToDouble(c -> c.cash).sum();
        worker.work(e, factory, .5);
        assertEquals(0, e.resources.available(0, factory.id, CityMaterials.PICKAXE));
        assertEquals(.5, e.resources.batch(factory.id, "stone-pickaxe").progress(), .00001);
        worker.work(e, factory, .5);
        assertEquals(
                CityMaterials.UNIT, e.resources.available(0, factory.id, CityMaterials.PICKAXE));
        assertEquals(CityMaterials.UNIT, e.resources.available(0, factory.id, CityMaterials.AXE));
        assertEquals(14 * CityMaterials.UNIT, e.resources.available(0, mine.id, Blocks.STONE));
        assertEquals(16 * CityMaterials.UNIT, e.resources.available(0, logging.id, Blocks.WOOD));
        assertEquals(1500 - 3.8, factory.cash, .00001);
        assertEquals(total, e.companies().stream().mapToDouble(c -> c.cash).sum(), .00001);
        assertEquals(budget, e.budget);
        double cash = mine.cash;
        assertTrue(e.purchase(mine.id, CityMaterials.PICKAXE, CityMaterials.UNIT));
        assertEquals(cash - 4, mine.cash, .00001);
        assertEquals(2, e.resources.productivity(mine.id, 2));
        assertTrue(e.purchase(logging.id, CityMaterials.AXE, CityMaterials.UNIT));
        assertEquals(2, e.resources.productivity(logging.id, 3));
        assertEquals(0, e.resources.available(0, factory.id, CityMaterials.PICKAXE));
        assertEquals(0, e.resources.available(0, factory.id, CityMaterials.AXE));
    }

    @Test
    void missingInputsAndFullOutputDoNotConsumeInputsOrGenerateProducts() {
        var g = new CityTest.Ground();
        var s = city(g);
        var f = firm(s, 8);
        premises(s, f.id);
        var r = s.economy.resources;
        r.add(0, f.id, Blocks.WOOD, 4 * CityMaterials.UNIT);
        work(g).work(s.economy, f, 10);
        assertEquals(4 * CityMaterials.UNIT, r.available(0, f.id, Blocks.WOOD));
        assertEquals(0, r.available(0, f.id, CityMaterials.PICKAXE));
        assertTrue(r.state().batches().isEmpty());
        r.add(0, f.id, CityMaterials.PICKAXE, 16 * CityMaterials.UNIT);
        r.add(0, f.id, CityMaterials.AXE, 16 * CityMaterials.UNIT);
        r.add(0, f.id, Blocks.STONE, 6 * CityMaterials.UNIT);
        var stocks = r.state().stocks();
        work(g).work(s.economy, f, 10);
        assertEquals(stocks, r.state().stocks());
        assertEquals("Storage full", r.production(f.id).status());
    }

    @Test
    void ownedEquipmentIncreasesActualMiningAndLoggingExtraction() {
        for (int kind : new int[] {2, 3}) {
            var plainG = new CityTest.Ground();
            var plain = city(plainG);
            var boostedG = new CityTest.Ground();
            var boosted = city(boostedG);
            var a = firm(plain, kind);
            var b = firm(boosted, kind);
            var equipment = boosted.economy.resources.catalog.equipment(kind);
            boosted.economy.resources.add(0, b.id, equipment.product(), CityMaterials.UNIT);
            // Another owner's tool does not boost a company.
            plain.economy.resources.add(
                    1,
                    plain.frame().citizens().get(0).id(),
                    equipment.product(),
                    CityMaterials.UNIT);
            assertEquals(1, plain.economy.resources.productivity(a.id, kind));
            var plainWork = work(plainG);
            var boostedWork = work(boostedG);
            // Repeated paid ticks let the bounded deposit scanner continue through sparse terrain.
            // Selling the output keeps storage capacity from masking the extraction rate.
            for (int i = 0; i < 100; i++) {
                plainWork.work(plain.economy, a, .005);
                boostedWork.work(boosted.economy, b, .005);
                for (int material : new int[] {Blocks.STONE, Blocks.WOOD, Blocks.PLANKS}) {
                    plain.economy.resources.remove(
                            0,
                            a.id,
                            material,
                            plain.economy.resources.available(0, a.id, material));
                    boosted.economy.resources.remove(
                            0,
                            b.id,
                            material,
                            boosted.economy.resources.available(0, b.id, material));
                }
            }
            long original = plain.economy.resources.production(a.id).harvested();
            assertTrue(original > 0);
            long accelerated = boosted.economy.resources.production(b.id).harvested();
            assertTrue(
                    accelerated > original,
                    "Actual extraction must increase: " + original + " -> " + accelerated);
            assertTrue(accelerated <= original * 2.2);
            assertEquals(2, boosted.economy.resources.productivity(b.id, kind));
            assertEquals(
                    CityMaterials.UNIT,
                    boosted.economy.resources.available(0, b.id, equipment.product()));
        }
    }

    @Test
    void customPipelineAndPartialBatchesSurviveSaveAndNetwork() throws Exception {
        var defaults = ProductionCatalog.cityGame();
        var products = new ArrayList<>(defaults.products());
        products.add(new ProductionCatalog.Product(1004, "Steel fittings", 7));
        var recipes = new ArrayList<>(defaults.recipes());
        recipes.removeIf(r -> r.companyKind() == 8);
        recipes.add(
                new ProductionCatalog.Recipe(
                        "fittings", 8, 1004, 2, Map.of(Blocks.STONE, 2), 4, 20, true));
        var catalog = new ProductionCatalog(products, recipes, defaults.equipment());
        var g = new CityTest.Ground();
        var s =
                new CitySimulation(
                        new GameConfig(true, false, 1200, 10), g, g.terrain, null, catalog);
        var f = firm(s, 8);
        premises(s, f.id);
        s.economy.resources.add(0, f.id, Blocks.STONE, 4 * CityMaterials.UNIT);
        work(g).work(s.economy, f, .125);
        var bytes = new ByteArrayOutputStream();
        CityMaterials.write(new DataOutputStream(bytes), s.economy.resources.state());
        var saved =
                CityMaterials.read(
                        new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(s.economy.resources.state(), saved);
        var e =
                new CityEconomy(
                        new Ecs(),
                        new CityEconomy.State(
                                s.economy.budget,
                                0,
                                0,
                                0,
                                s.economy.state().firms(),
                                List.of(),
                                s.economy.properties,
                                List.of(),
                                List.of(),
                                saved));
        work(g).work(e, e.company(f.id), .125);
        assertEquals(2 * CityMaterials.UNIT, e.resources.available(0, f.id, 1004));
        assertEquals(2 * CityMaterials.UNIT, e.resources.available(0, f.id, Blocks.STONE));
        assertEquals(1, e.resources.batch(f.id, "fittings").completed());
        assertEquals("Steel fittings", e.resources.catalog.outputs(8));
    }

    @Test
    void expandedCityCompletesThreeDailyRoutinesWithoutTrappingMineWorkers() {
        var g = new CityTest.Ground();
        var s =
                new CitySimulation(
                        new GameConfig(true, true, 1200, 6),
                        g,
                        g.terrain,
                        null,
                        ProductionCatalog.toolEra());
        var pose = new Protocol.Pose(1, 8, 40, 24, 0, 0);
        s.command(
                new CityCommand(
                        CityCommand.ROAD,
                        0,
                        List.of(new Polygon.Point(-10, 24), new Polygon.Point(-60, 24))),
                1,
                pose);
        s.command(
                new CityCommand(CityCommand.ZONE, 2, CityTest.box(-50, 26, 40, 24).vertices()),
                1,
                pose);
        var meals = new HashSet<Integer>();
        var sleeping = new HashSet<Integer>();
        for (int second = 0; second < 3600; second++) {
            s.advance(1);
            for (var citizen : s.frame().citizens()) {
                if (citizen.activity().equals("Eating at shop")) meals.add(citizen.id());
                if (citizen.activity().equals("Sleeping at home")) sleeping.add(citizen.id());
            }
        }
        assertEquals(12, meals.size());
        assertEquals(12, sleeping.size());
        assertEquals(11, s.frame().buildings().size());
    }

    @Test
    void aStaffedFactoryBuildsAndTradesToolsDuringTheActualSimulation() throws Exception {
        var g = new CityTest.Ground();
        var s = city(g);
        var pose = new Protocol.Pose(1, 8, 40, 24, 0, 0);
        s.command(
                new CityCommand(
                        CityCommand.ROAD,
                        0,
                        List.of(new Polygon.Point(-10, 24), new Polygon.Point(-60, 24))),
                1,
                pose);
        s.command(
                new CityCommand(CityCommand.ZONE, 2, CityTest.box(-50, 26, 40, 24).vertices()),
                1,
                pose);
        for (int i = 0; i < 700; i++) s.advance(1);
        var f = firm(s, 8);
        assertTrue(s.economy.properties.stream().anyMatch(p -> p.operator() == f.id));
        assertTrue(s.economy.resources.production(f.id).processed() > 0);
        assertEquals(2, s.economy.resources.productivity(firm(s, 2).id, 2));
        assertEquals(2, s.economy.resources.productivity(firm(s, 3).id, 3));
        assertTrue(
                s.economy.businesses.records().stream()
                        .filter(a -> a.company() == f.id)
                        .anyMatch(a -> a.total().revenue() > 0 && a.total().supplies() > 0));
        Path save = directory.resolve("factory.city");
        s.save(save);
        var saved = CitySimulation.load(save);
        assertEquals(s.frame(), saved);
        var restored = new CitySimulation(saved.config(), g, g.terrain, saved);
        assertEquals(saved.economy(), restored.frame().economy());
        int factoryBuilding =
                s.economy.properties.stream()
                        .filter(p -> p.operator() == f.id)
                        .findFirst()
                        .orElseThrow()
                        .building();
        var inspector = new BuildingInfo();
        inspector.show(factoryBuilding, 0);
        assertTrue(
                inspector.lines(saved).stream()
                        .anyMatch(line -> line.contains("Stone pickaxe") && line.contains("->")));
    }
}
