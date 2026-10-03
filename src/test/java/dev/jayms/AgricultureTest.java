package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.util.*;

class AgricultureTest {
    @TempDir Path directory;

    private CitySimulation city(CityTest.Ground g) {
        return new CitySimulation(new GameConfig(true, true, 1200, 6), g, g.terrain, null);
    }

    private CityEconomy.Company firm(CitySimulation s, int kind) {
        return s.economy.companies().stream().filter(f -> f.kind == kind).findFirst().orElseThrow();
    }

    private CityFrame.Building farm(CitySimulation s, CityTest.Ground g, int kind) {
        var p = s.economy.buyPlot(100, 3, 100, 30, 100, kind);
        assertNotNull(p);
        var requirements = s.economy.resources.plan(p, kind);
        for (var a : requirements.materials())
            s.economy.resources.add(0, p.developer(), a.material(), a.units());
        assertTrue(s.economy.resources.reserve(p));
        g.apply(StructureBlueprint.generate(3, kind, p.x(), p.y(), p.z()));
        assertTrue(s.economy.resources.consume(p));
        var b = new CityFrame.Building(400, 100, 3, p.x(), p.y(), p.z(), 2, 0);
        s.economy.completed(p, b.id());
        s.agriculture.completed(b, s.economy);
        return b;
    }

    @Test
    void farmersBuyAndBuildTheirOwnPlotsWhileDevelopersStayOut() {
        var g = new CityTest.Ground();
        var s = city(g);
        var e = s.economy;
        var developers = e.state().firms().stream().filter(f -> f.kind() == 0).toList();
        var farmer = firm(s, 7);
        double cash = farmer.cash, budget = e.budget;
        var p = e.buyPlot(9, 3, 100, 30, 100, 7);
        assertNotNull(p);
        assertEquals(farmer.id, p.developer());
        assertEquals(cash - p.landPrice(), farmer.cash, 1e-8);
        assertEquals(budget + p.landPrice(), e.budget, 1e-8);
        assertEquals(developers, e.state().firms().stream().filter(f -> f.kind() == 0).toList());
        e.resources.plan(p, 7);
        assertFalse(e.supply(p));
        e.work(p.id(), 100);
        assertEquals(0, e.project(p.id()).work());
        assertNull(e.buyPlot(9, 3, 120, 30, 100, 0));
        assertEquals(3, s.frame().agriculture().families().size());
        assertEquals(6, s.ecs.query(Agriculture.Farmer.class).size());
    }

    @Test
    void fieldsNeedBuiltSoilAndLabourAndRegrowRealNamedCrops() {
        var g = new CityTest.Ground();
        var s = city(g);
        var b = farm(s, g, 7);
        var owner = firm(s, 7);
        assertEquals(owner.id, s.economy.property(b.id()).owner());
        assertEquals(owner.id, s.economy.property(b.id()).operator());
        assertEquals(0, s.economy.property(b.id()).rent());
        assertTrue(s.economy.contracts.isEmpty());
        s.agriculture.tick(48);
        assertEquals(0, s.economy.resources.available(0, owner.id, CityMaterials.WHEAT));
        s.agriculture.work(b, s.economy, 2, g);
        var first = s.agriculture.state().fields().get(0);
        assertTrue(first.growth() > 0 && first.growth() < .5, "Dry land grows more slowly");
        s.agriculture.work(b, s.economy, 6, g);
        assertEquals(
                32 * CityMaterials.UNIT,
                s.economy.resources.available(0, owner.id, CityMaterials.WHEAT));
        assertEquals(
                32 * CityMaterials.UNIT,
                s.economy.resources.available(0, owner.id, CityMaterials.CARROT));
        var f = s.agriculture.state().fields().get(0);
        g.apply(List.of(new Protocol.Edit(f.x(), f.y() - 1, f.z(), Blocks.AIR)));
        s.agriculture.work(b, s.economy, 6, g);
        assertEquals(
                32 * CityMaterials.UNIT,
                s.economy.resources.available(0, owner.id, CityMaterials.WHEAT));
        assertEquals(
                64 * CityMaterials.UNIT,
                s.economy.resources.available(0, owner.id, CityMaterials.CARROT));
        assertEquals(0, s.economy.resources.available(0, owner.id, CityMaterials.FOOD));
    }

    @Test
    void cattleNeedPurchasedFeedAndMilkAndBeefComeFromFiniteAnimals() {
        var g = new CityTest.Ground();
        var s = city(g);
        var b = farm(s, g, 10);
        var cattle = firm(s, 10);
        var crop = firm(s, 7);
        s.agriculture.work(b, s.economy, 20, g);
        assertEquals(4, s.agriculture.state().cows().size());
        assertEquals(0, s.economy.resources.available(0, cattle.id, CityMaterials.MILK));
        assertEquals(0, s.economy.resources.available(0, cattle.id, CityMaterials.BEEF));
        s.economy.resources.add(0, crop.id, CityMaterials.WHEAT, 100 * CityMaterials.UNIT);
        double cash = cattle.cash, seller = crop.cash;
        s.agriculture.work(b, s.economy, 1, g);
        double paid = cash - cattle.cash;
        assertTrue(paid > 0, "Cattle farmer pays for feed at market prices");
        assertEquals(seller + paid, crop.cash, .00001);
        assertEquals(
                96 * CityMaterials.UNIT,
                s.economy.resources.available(0, crop.id, CityMaterials.WHEAT));
        assertEquals(
                4 * CityMaterials.UNIT,
                s.economy.resources.available(0, cattle.id, CityMaterials.MILK));
        assertEquals(
                8 * CityMaterials.UNIT,
                s.economy.resources.available(0, cattle.id, CityMaterials.BEEF));
        assertEquals(3, s.agriculture.state().cows().size());
        assertEquals(3, s.ecs.query(Agriculture.Livestock.class).size());
        s.agriculture.work(b, s.economy, 11, g);
        assertEquals(1, s.agriculture.state().farms().get(0).births());
        assertTrue(s.agriculture.state().cows().stream().anyMatch(c -> c.age() == 0));
        s.agriculture.tick(24);
        s.agriculture.work(b, s.economy, 1, g);
        assertEquals(2, s.agriculture.state().farms().get(0).slaughtered());
        assertEquals(3, s.agriculture.state().cows().size());
        var cow = s.agriculture.state().cows().get(0);
        var frame =
                new CityFrame(
                        s.config(),
                        0,
                        List.of(),
                        List.of(),
                        List.of(b),
                        List.of(),
                        List.of(),
                        s.economy.state(),
                        CityAddresses.empty(),
                        s.agriculture.state());
        assertTrue(CityOccupancy.overlaps(frame, cow.x(), cow.y(), cow.z(), .1f, 1, .1f));
    }

    @Test
    void foodFactoriesPurchaseAndConsumeEveryIngredientWithoutPublicSubsidies() {
        var g = new CityTest.Ground();
        var s = city(g);
        var e = s.economy;
        var work = new CityHarvesting(g, g.terrain, (x, z) -> false);
        var crops = firm(s, 7);
        var cane = firm(s, 9);
        var cattle = firm(s, 10);
        e.resources.add(0, crops.id, 1101, 100 * CityMaterials.UNIT);
        e.resources.add(0, crops.id, 1102, 100 * CityMaterials.UNIT);
        e.resources.add(0, cane.id, 1103, 100 * CityMaterials.UNIT);
        e.resources.add(0, cattle.id, 1105, 100 * CityMaterials.UNIT);
        // Retail inventory is reserved for citizen meals; factories buy directly from producers.
        var shop = firm(s, 1);
        e.resources.add(0, shop.id, CityMaterials.CARROT, 500 * CityMaterials.UNIT);
        e.resources.add(0, shop.id, CityMaterials.MILK, 500 * CityMaterials.UNIT);
        double shopCash = shop.cash;
        double money = e.companies().stream().mapToDouble(f -> f.cash).sum(), budget = e.budget;
        for (int kind = 11; kind <= 14; kind++) {
            var factory = firm(s, kind);
            work.work(e, factory, 1);
            assertEquals(0, e.resources.production(factory.id).processed());
            e.properties.add(
                    new CityEconomy.Property(400 + kind, 0, factory.id, factory.id, 400, 6));
            work.work(e, factory, 1);
            assertTrue(e.resources.production(factory.id).processed() > 0);
        }
        assertEquals(money, e.companies().stream().mapToDouble(f -> f.cash).sum(), .000001);
        assertEquals(budget, e.budget);
        var cake = firm(s, 14);
        var recipe = e.resources.catalog.recipes(14).get(0);
        long made = e.resources.batch(cake.id, recipe.id()).completed();
        assertEquals(8, made);
        assertEquals(32 * CityMaterials.UNIT, e.resources.available(0, cake.id, 1109));
        assertEquals(0, e.resources.available(0, cake.id, 1105));
        assertEquals(16 * CityMaterials.UNIT, e.resources.available(0, firm(s, 11).id, 1106));
        assertEquals(16 * CityMaterials.UNIT, e.resources.available(0, firm(s, 13).id, 1108));
        assertEquals(
                92 * CityMaterials.UNIT, e.resources.available(0, crops.id, CityMaterials.CARROT));
        assertEquals(
                92 * CityMaterials.UNIT, e.resources.available(0, cattle.id, CityMaterials.MILK));
        assertEquals(
                500 * CityMaterials.UNIT, e.resources.available(0, shop.id, CityMaterials.CARROT));
        assertEquals(
                500 * CityMaterials.UNIT, e.resources.available(0, shop.id, CityMaterials.MILK));
        assertEquals(shopCash, shop.cash);
    }

    @Test
    void threeDaysFeedEveryoneAndPreserveFarmsCowsAndFoodAcrossSaveAndNetwork() throws Exception {
        // Existing worlds retain the original crop rates and economic trajectory.
        var g = new CityTest.Ground(Terrain.LEGACY_VERSION);
        var s = city(g);
        var ate = new HashSet<Integer>();
        var slept = new HashSet<Integer>();
        for (int second = 0; second < 3600; second++) {
            s.advance(1);
            for (var c : s.frame().citizens()) {
                if (c.activity().equals("Eating at shop")) ate.add(c.id());
                if (c.activity().equals("Sleeping at home")) slept.add(c.id());
            }
        }
        var f = s.frame();
        assertEquals(18, ate.size());
        assertEquals(18, slept.size());
        assertEquals(18, f.buildings().size());
        assertEquals(4, f.agriculture().farms().size());
        assertEquals(6, f.agriculture().fields().size());
        assertFalse(f.agriculture().cows().isEmpty());
        for (int kind = 11; kind <= 14; kind++) {
            int company = firm(s, kind).id;
            assertTrue(
                    f.economy().resources().production().stream()
                            .anyMatch(p -> p.company() == company && p.processed() > 0));
        }
        assertTrue(
                f.economy().resources().stocks().stream()
                        .noneMatch(a -> a.material() == CityMaterials.FOOD));
        assertEquals(18, CityMetrics.from(f).housed());
        for (var b : f.buildings())
            if (b.type() == 3) {
                var property = s.economy.property(b.id());
                assertEquals(property.owner(), property.operator());
                assertTrue(CityMaterials.farmer(s.economy.company(property.owner()).kind));
                var info = new BuildingInfo();
                info.building = b.id();
                assertTrue(info.lines(f).stream().anyMatch(row -> row.contains("Farmer-owned")));
            }
        var dashboard = new BusinessDashboard();
        dashboard.filter = 4;
        assertEquals(4, dashboard.rows(f).size());
        Path save = directory.resolve("city.dat");
        s.save(save);
        var loaded = CitySimulation.load(save);
        assertEquals(f, loaded);
        var bytes = new ByteArrayOutputStream();
        f.write(new DataOutputStream(bytes));
        assertEquals(
                f,
                CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        var restored = new CitySimulation(f.config(), g, g.terrain, loaded);
        assertEquals(f.agriculture(), restored.frame().agriculture());
        assertEquals(f.economy(), restored.frame().economy());
        restored.advance(1);
        assertTrue(restored.frame().agriculture().cows().stream().allMatch(c -> c.age() > 0));
    }

    @Test
    void farmExpansionPaysForAnotherPlotAndMaterialReservation() {
        var g = new CityTest.Ground();
        var s = city(g);
        for (int i = 0; i < 1150; i++) s.advance(1);
        var farmer = firm(s, 7);
        double land = farmer.land;
        long wheat = s.economy.resources.available(0, farmer.id, CityMaterials.WHEAT);
        s.economy.resources.remove(0, farmer.id, CityMaterials.WHEAT, wheat);
        for (int i = 0; i < 250; i++) s.advance(1);
        var plots =
                s.economy.plots.stream()
                        .filter(p -> p.type() == 3 && p.developer() == farmer.id)
                        .toList();
        assertEquals(2, plots.size());
        assertTrue(farmer.land > land);
        assertEquals(
                plots.stream().mapToDouble(CityEconomy.Plot::landPrice).sum(), farmer.land, 1e-8);
        assertTrue(plots.stream().allMatch(p -> p.building() > 0));
        assertEquals(
                4,
                s.agriculture.state().fields().stream()
                        .filter(f -> f.company() == farmer.id)
                        .count());
        for (var p : plots) assertTrue(s.economy.resources.project(p.id()).consumed());
        assertTrue(
                s.economy.properties.stream()
                        .filter(p -> p.owner() == farmer.id)
                        .allMatch(p -> p.operator() == farmer.id && p.rent() == 0));
    }

    @Test
    void snapshotRejectsForeignAndOutOfBoundsFarmEntities() throws Exception {
        var g = new CityTest.Ground();
        var s = city(g);
        for (int i = 0; i < 600; i++) s.advance(1);
        var f = s.frame();
        var field = f.agriculture().fields().get(0);
        var badFields = new ArrayList<>(f.agriculture().fields());
        badFields.set(
                0,
                new Agriculture.Field(
                        field.building(),
                        field.company(),
                        field.product(),
                        Integer.MAX_VALUE,
                        field.y(),
                        field.z(),
                        0,
                        0));
        var bad =
                new Agriculture.State(
                        true,
                        false,
                        f.agriculture().families(),
                        badFields,
                        f.agriculture().cows(),
                        f.agriculture().farms());
        var out = new ByteArrayOutputStream();
        Agriculture.write(new DataOutputStream(out), bad);
        assertThrows(
                IOException.class,
                () ->
                        Agriculture.read(
                                new DataInputStream(new ByteArrayInputStream(out.toByteArray())),
                                f.economy(),
                                f.buildings(),
                                f.citizens(),
                                f.horses()));
        var cows = new ArrayList<>(f.agriculture().cows());
        cows.add(cows.get(0));
        var duplicated =
                new Agriculture.State(
                        true,
                        false,
                        f.agriculture().families(),
                        f.agriculture().fields(),
                        cows,
                        f.agriculture().farms());
        var bytes = new ByteArrayOutputStream();
        Agriculture.write(new DataOutputStream(bytes), duplicated);
        assertThrows(
                IOException.class,
                () ->
                        Agriculture.read(
                                new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),
                                f.economy(),
                                f.buildings(),
                                f.citizens(),
                                f.horses()));
    }

    @Test
    void cityFiveUpgradeIsDeferredAndPreservesExistingAssets() throws Exception {
        var g = new CityTest.Ground();
        var old =
                new CitySimulation(
                        new GameConfig(true, false, 1200, 10),
                        g,
                        g.terrain,
                        null,
                        ProductionCatalog.toolEra());
        for (int i = 0; i < 320; i++) old.advance(1);
        var before = old.frame();
        Path file = directory.resolve("old.city");
        try (var out = new DataOutputStream(Files.newOutputStream(file))) {
            out.writeInt(0x43495435);
            before.write(out, 5);
        }
        var loaded = CitySimulation.load(file);
        var s = new CitySimulation(loaded.config(), g, g.terrain, loaded);
        assertEquals(before.elapsed(), s.frame().elapsed());
        assertEquals(before.economy(), s.frame().economy());
        assertTrue(s.agriculture.pending());
        s.save(file);
        assertTrue(CitySimulation.load(file).agriculture().pending());
        s.advance(1);
        assertEquals(18, s.frame().citizens().size());
        assertEquals(17, s.frame().economy().firms().size());
        assertEquals(before.buildings(), s.frame().buildings());
        var previousCitizens = before.citizens().stream().map(CityFrame.Citizen::id).toList();
        int oldFarm = firm(s, 7).id;
        assertTrue(
                s.frame().citizens().stream()
                        .filter(c -> previousCitizens.contains(c.id()))
                        .noneMatch(c -> CityMetrics.employer(s.frame(), c) == oldFarm));
        assertEquals(before.economy().budget(), s.frame().economy().budget());
        assertEquals(
                before.economy().properties().stream()
                        .map(p -> List.of(p.building(), p.ownerKind(), p.owner(), p.operator()))
                        .toList(),
                s.frame().economy().properties().stream()
                        .map(p -> List.of(p.building(), p.ownerKind(), p.owner(), p.operator()))
                        .toList());
        assertEquals(
                before.economy().contracts(),
                s.frame().economy().contracts(),
                "Migration preserves negotiated contracts while market offers may move");
        assertTrue(s.frame().economy().resources().catalog().agriculture());
        s.advance(1);
        assertEquals(18, s.frame().citizens().size());
        assertTrue(s.frame().agriculture().fields().isEmpty());
    }
}
