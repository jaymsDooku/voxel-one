package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.Blocks;
import dev.jayms.net.city.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class MarketEconomyTest {
    private CitySimulation city() {
        return new CityTest().simulation(new CityTest.Ground());
    }

    private CityEconomy.Company firm(CityEconomy e, int kind) {
        return e.companies().stream().filter(c -> c.kind == kind).findFirst().orElseThrow();
    }

    @Test
    void loadsBaseGeneratedCityEightWithOwnershipOrdersAndExchange(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        var file = temp.resolve("base.city");
        try (var input = getClass().getResourceAsStream("/market/base-city8-ownership.city")) {
            assertNotNull(input);
            java.nio.file.Files.copy(input, file);
        }
        var saved = CitySimulation.load(file);
        assertEquals(21, dev.jayms.net.Protocol.VERSION);
        assertTrue(saved.buildings().stream().anyMatch(b -> b.type() == SpecialBuildings.EXCHANGE));
        var capital = saved.economy().capital();
        assertFalse(capital.book().listings().isEmpty());
        assertTrue(capital.book().listings().stream().anyMatch(l -> l.publicCompany()));
        assertFalse(capital.book().orders().isEmpty());
        var ground = new CityTest.Ground();
        var restored = new CitySimulation(saved.config(), ground, ground.terrain, saved);
        assertEquals(capital, restored.economy.capital.state());
        assertEquals(saved.buildings(), restored.frame().buildings());
        var roundTrip = temp.resolve("round-trip.city");
        restored.save(roundTrip);
        try (var input = new java.io.DataInputStream(java.nio.file.Files.newInputStream(roundTrip))) {
            assertEquals(0x4349543B, input.readInt());
        }
        assertEquals(capital, CitySimulation.load(roundTrip).economy().capital());
    }

    @Test
    void versionSevenSavePreservesConfiguredBusinesses(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        var catalog = ProductionCatalog.load(java.nio.file.Path.of("config/city-excavation.properties"));
        var ground = new CityTest.Ground();
        var config = GameConfig.cityGame();
        var simulation = new CitySimulation(config, ground, ground.terrain, null, catalog);
        var path = temp.resolve("configured.city");
        try (var output = new java.io.DataOutputStream(java.nio.file.Files.newOutputStream(path))) {
            output.writeInt(0x43495437);
            simulation.frame().write(output, 7);
        }
        try (var input = new java.io.DataInputStream(java.nio.file.Files.newInputStream(path))) {
            assertEquals(0x43495437, input.readInt());
        }
        var saved = CitySimulation.load(path);
        assertEquals(catalog, saved.economy().resources().catalog());
        var restored = new CitySimulation(config, ground, ground.terrain, saved);
        restored.advance(.1);
        assertEquals(catalog, restored.economy.resources.catalog);
        assertEquals(2, restored.economy.companies().stream().filter(c -> c.kind == 15).count());
    }

    @Test
    void configuredBusinessesCompeteOnStockAndPrice() throws Exception {
        var catalog = ProductionCatalog.load(java.nio.file.Path.of("config/city-excavation.properties"));
        var economy = new CityEconomy(new Ecs(), null, catalog);
        var sellers = economy.companies().stream().filter(c -> c.kind == 15).toList();
        var buyer = firm(economy, CityEconomy.DEVELOPER);
        int material = Blocks.SAND;
        economy.resources.add(0, sellers.get(0).id, material, CityMaterials.UNIT);
        double scarce = economy.marketPrice(material);
        economy.resources.add(0, sellers.get(1).id, material, 100 * CityMaterials.UNIT);
        assertTrue(economy.marketPrice(material) < scarce);
        assertTrue(economy.offer(0, sellers.get(1).id, material)
                < economy.offer(0, sellers.get(0).id, material));
        double cash = sellers.get(0).cash;
        assertTrue(economy.purchase(0, buyer.id, material, CityMaterials.UNIT));
        assertEquals(cash, sellers.get(0).cash);
        assertEquals(99 * CityMaterials.UNIT, economy.resources.available(0, sellers.get(1).id, material));
        assertEquals(CityMaterials.UNIT, economy.resources.available(0, buyer.id, material));
    }

    @Test
    void everyMaterialAndProductRespondsToStockAndDemand() {
        var s = city();
        var e = s.economy;
        var seller = firm(e, CityMaterials.LOGGING);
        var materials =
                new ArrayList<>(
                        List.of(
                                Blocks.WOOD,
                                Blocks.STONE,
                                Blocks.DIRT,
                                Blocks.PLANKS,
                                Blocks.BRICKS,
                                Blocks.GLASS,
                                Blocks.LED));
        e.resources.catalog.products().forEach(p -> materials.add(p.id()));
        for (int material : materials) {
            double scarce = e.marketPrice(material);
            e.resources.add(0, seller.id, material, 1000 * CityMaterials.UNIT);
            double abundant = e.marketPrice(material);
            assertTrue(abundant < scarce, "Abundance lowers " + material);
            assertTrue(Double.isFinite(abundant) && abundant > 0);
            e.resources.remove(0, seller.id, material, 1000 * CityMaterials.UNIT);
        }
        e.resources.add(0, seller.id, Blocks.BRICKS, 10 * CityMaterials.UNIT);
        double before = e.marketPrice(Blocks.BRICKS);
        assertNotNull(e.buyPlot(1, 0, 12, 30, 28));
        assertTrue(e.marketPrice(Blocks.BRICKS) > before, "Unfunded material needs increase price");
        for (int id : s.ecs.query(CitySimulation.Needs.class))
            s.ecs.get(id, CitySimulation.Needs.class).hunger = 100;
        double fed = e.marketPrice(CityMaterials.FOOD);
        for (int id : s.ecs.query(CitySimulation.Needs.class))
            s.ecs.get(id, CitySimulation.Needs.class).hunger = 0;
        assertTrue(e.marketPrice(CityMaterials.FOOD) > fed);
    }

    @Test
    void buyersChooseCheapestSupplierAndPartialAffordableFillsConserveAssets() {
        var e = city().economy;
        var expensive = firm(e, CityMaterials.LOGGING);
        var cheap = firm(e, CityEconomy.MINE);
        var buyer = firm(e, CityEconomy.DEVELOPER);
        e.resources.add(0, expensive.id, Blocks.WOOD, CityMaterials.UNIT);
        e.resources.add(0, cheap.id, Blocks.WOOD, 20 * CityMaterials.UNIT);
        assertTrue(e.offer(0, cheap.id, Blocks.WOOD) < e.offer(0, expensive.id, Blocks.WOOD));
        double cash = e.companies().stream().mapToDouble(c -> c.cash).sum();
        assertTrue(e.purchase(buyer.id, Blocks.WOOD, CityMaterials.UNIT));
        assertEquals(CityMaterials.UNIT, e.resources.available(0, expensive.id, Blocks.WOOD));
        assertEquals(19 * CityMaterials.UNIT, e.resources.available(0, cheap.id, Blocks.WOOD));
        assertEquals(cash, e.companies().stream().mapToDouble(c -> c.cash).sum(), 1e-8);
        buyer.cash = e.offer(0, cheap.id, Blocks.WOOD) * .5;
        long before = e.resources.available(0, buyer.id, Blocks.WOOD);
        assertFalse(e.purchase(buyer.id, Blocks.WOOD, 5 * CityMaterials.UNIT));
        assertTrue(e.resources.available(0, buyer.id, Blocks.WOOD) > before);
        assertTrue(buyer.cash >= 0);
        assertEquals(
                21 * CityMaterials.UNIT,
                e.resources.state().stocks().stream()
                        .filter(stock -> stock.material() == Blocks.WOOD)
                        .mapToLong(CityMaterials.Stock::units)
                        .sum());
        var state = e.state();
        assertFalse(e.trade(0, cheap.id, 0, buyer.id, Blocks.WOOD, -1));
        assertFalse(e.wage(buyer.id, Double.NaN, null));
        assertEquals(state, e.state());
    }

    @Test
    void householdsChooseCheapestMealMeetingNutritionAndPayTheActualSeller() {
        var g = new CityTest.Ground();
        var catalog =
                new ProductionCatalog(
                        List.of(
                                new ProductionCatalog.Product(
                                        CityMaterials.FOOD, "Small meal", .2, 10),
                                new ProductionCatalog.Product(1020, "Large meal", 5, 40)),
                        List.of(),
                        List.of());
        var s =
                new CitySimulation(
                        new GameConfig(true, false, 1200, 10), g, g.terrain, null, catalog);
        var e = s.economy;
        var shop = firm(e, CityEconomy.SHOP);
        e.properties.add(new CityEconomy.Property(999, 0, shop.id, shop.id, 200, 4));
        e.resources.add(0, shop.id, CityMaterials.FOOD, 20 * CityMaterials.UNIT);
        e.resources.add(0, shop.id, 1020, 20 * CityMaterials.UNIT);
        int citizen = s.frame().citizens().get(0).id();
        var needs = s.ecs.get(citizen, CitySimulation.Needs.class);
        needs.hunger = 0;
        var offer = e.cheapestFood(shop.id, needs.money, 35);
        assertEquals(CityMaterials.FOOD, offer.product());
        assertEquals(4, offer.portions());
        assertTrue(offer.nutrition() >= 35);
        assertEquals(1020, e.cheapestFood(shop.id, needs.money, 35, 1).product());
        assertNull(e.cheapestFood(shop.id, needs.money, 35, 0));
        double total = needs.money + shop.cash;
        assertEquals(offer, e.buyMeal(citizen, 999, 35));
        assertEquals(40, needs.hunger);
        assertEquals(
                16 * CityMaterials.UNIT, e.resources.available(0, shop.id, CityMaterials.FOOD));
        assertEquals(total, needs.money + shop.cash, 1e-5);
        needs.money = 0;
        var state = e.state();
        assertNull(e.buyMeal(citizen, 999, 35));
        assertEquals(state, e.state());
    }

    @Test
    void multiPortionMealsReconcileBusinessSalesBuildingStockAndInventory() {
        var g = new CityTest.Ground();
        var catalog =
                new ProductionCatalog(
                        List.of(
                                new ProductionCatalog.Product(
                                        CityMaterials.FOOD, "Small meal", .2, 10)),
                        List.of(),
                        List.of());
        var s =
                new CitySimulation(
                        new GameConfig(true, false, 1200, 10), g, g.terrain, null, catalog);
        var shop = firm(s.economy, CityEconomy.SHOP);
        int building = 600;
        for (var company : s.economy.companies()) company.cash = company.id == shop.id ? 10000 : 0;
        var citizens = s.frame().citizens();
        for (int i = 0; i < citizens.size(); i++) {
            int id = citizens.get(i).id();
            s.ecs.get(id, CitySimulation.Household.class).job = i < 2 ? building : 0;
            s.ecs.get(id, CitySimulation.Needs.class).hunger = i == 2 ? 20 : 100;
            var position = s.ecs.get(id, CitySimulation.Position.class);
            position.x = 14;
            position.z = 30;
        }
        s.economy.properties.add(new CityEconomy.Property(building, 0, shop.id, shop.id, 200, 4));
        s.economy.resources.add(0, shop.id, CityMaterials.FOOD, 20 * CityMaterials.UNIT);
        s.economy.businesses.open(building, shop.id);
        s.economy.businesses.delivery(building, 20, 0);
        var f = s.frame();
        var fixture =
                new CityFrame(
                        f.config(),
                        f.elapsed(),
                        f.roads(),
                        f.zones(),
                        List.of(new CityFrame.Building(building, 1, 1, 12, 24, 28, 2, 20)),
                        f.citizens(),
                        f.horses(),
                        f.economy());
        var run = new CitySimulation(f.config(), g, g.terrain, fixture);
        for (var citizen : citizens)
            run.ecs.get(citizen.id(), CitySimulation.Travel.class).target = building;
        run.advance(.11);
        var account =
                run.economy.businesses.records().stream()
                        .filter(r -> r.building() == building)
                        .findFirst()
                        .orElseThrow();
        long inventory = run.economy.resources.available(0, shop.id, CityMaterials.FOOD);
        int stock = run.frame().buildings().get(0).stock();
        assertEquals(4, account.total().sold());
        assertEquals(4, account.today().totals().sold());
        assertEquals(16 * CityMaterials.UNIT, inventory);
        assertEquals(16, stock);
        assertEquals(account.total().received() - account.total().sold(), stock);
        assertEquals(inventory / CityMaterials.UNIT, stock);
        assertTrue(run.ecs.get(citizens.get(2).id(), CitySimulation.Needs.class).hunger > 59);
    }

    private record MealStockFixture(CitySimulation run, int company, int eater) {}

    private MealStockFixture mealStockFixture(boolean sharedCompany) {
        var ground = new CityTest.Ground();
        var catalog = new ProductionCatalog(
                List.of(new ProductionCatalog.Product(CityMaterials.FOOD, "Small meal", .2, 10)),
                List.of(), List.of());
        var seed = new CitySimulation(new GameConfig(true, false, 1200, 10),
                ground, ground.terrain, null, catalog);
        var shop = firm(seed.economy, CityEconomy.SHOP);
        for (var company : seed.economy.companies()) company.cash = company.id == shop.id ? 10000 : 0;
        var citizens = seed.frame().citizens();
        int eaterIndex = sharedCompany ? 4 : 2;
        for (int i = 0; i < citizens.size(); i++) {
            int id = citizens.get(i).id();
            seed.life(id).education = CitizenLife.Education.SECONDARY;
            seed.ecs.get(id, CitySimulation.Household.class).job =
                    i < 2 ? 600 : sharedCompany && i < 4 ? 601 : 0;
            seed.ecs.get(id, CitySimulation.Needs.class).hunger = i == eaterIndex ? 20 : 100;
            var position = seed.ecs.get(id, CitySimulation.Position.class);
            position.x = sharedCompany && i >= 2 && i <= 4 ? 22 : 14;
            position.z = 30;
        }
        var shops = new ArrayList<CityFrame.Building>();
        shops.add(new CityFrame.Building(600, 1, 1, 12, 24, 28, 2, 1));
        if (sharedCompany) shops.add(new CityFrame.Building(601, 1, 1, 20, 24, 28, 2, 19));
        for (var building : shops) {
            seed.economy.properties.add(new CityEconomy.Property(
                    building.id(), 0, shop.id, shop.id, 200, 4));
            seed.economy.businesses.open(building.id(), shop.id);
            seed.economy.businesses.delivery(building.id(), building.stock(), 0);
        }
        seed.economy.resources.add(0, shop.id, CityMaterials.FOOD, 20 * CityMaterials.UNIT);
        var frame = seed.frame();
        var fixture = new CityFrame(frame.config(), frame.elapsed(), frame.roads(), frame.zones(),
                shops, frame.citizens(), frame.horses(), frame.economy());
        var run = new CitySimulation(frame.config(), ground, ground.terrain, fixture);
        int eater = citizens.get(eaterIndex).id();
        run.ecs.get(eater, CitySimulation.Travel.class).target = sharedCompany ? 601 : 600;
        return new MealStockFixture(run, shop.id, eater);
    }

    @Test
    void lowStockShopCannotSellMultiPortionMealBeforeRestocking() {
        var fixture = mealStockFixture(false);
        var run = fixture.run();
        var needs = run.ecs.get(fixture.eater(), CitySimulation.Needs.class);
        float money = needs.money;
        assertNull(run.economy.buyMeal(fixture.eater(), 600, 35, 1));
        assertEquals(money, needs.money);
        assertEquals(20, needs.hunger);
        run.advance(.11);
        var account = run.economy.businesses.records().stream()
                .filter(r -> r.building() == 600).findFirst().orElseThrow();
        assertEquals(0, account.total().sold());
        assertEquals(0, account.today().totals().sold());
        assertEquals(money, needs.money);
        assertTrue(needs.hunger <= 20);
        assertEquals(20 * CityMaterials.UNIT,
                run.economy.resources.available(0, fixture.company(), CityMaterials.FOOD));
        // The normal restock step allocates one additional portion after the blocked purchase.
        assertEquals(2, run.frame().buildings().get(0).stock());
    }

    @Test
    void sharedCompanyShopsUseLocalStockAndReconcileSales() {
        var fixture = mealStockFixture(true);
        var run = fixture.run();
        run.advance(.11);
        var records = run.economy.businesses.records();
        var low = records.stream().filter(r -> r.building() == 600).findFirst().orElseThrow();
        var stocked = records.stream().filter(r -> r.building() == 601).findFirst().orElseThrow();
        assertEquals(0, low.total().sold());
        assertEquals(4, stocked.total().sold());
        assertEquals(4, stocked.today().totals().sold());
        for (var building : run.frame().buildings()) {
            var account = records.stream().filter(r -> r.building() == building.id()).findFirst().orElseThrow();
            assertEquals(account.total().received() - account.total().sold(), building.stock());
        }
        assertEquals(16, run.frame().buildings().stream().mapToInt(CityFrame.Building::stock).sum());
        assertEquals(16 * CityMaterials.UNIT,
                run.economy.resources.available(0, fixture.company(), CityMaterials.FOOD));
        assertTrue(run.ecs.get(fixture.eater(), CitySimulation.Needs.class).hunger > 59);
    }

    @Test
    void unpaidMinimumCrewsCanSwitchAndAssignmentPreservesChoiceAcrossTicks() {
        for (int kind : new int[] {CityEconomy.MINE, CityEconomy.SHOP}) {
            var g = new CityTest.Ground();
            var s = new CityTest().simulation(g);
            var source = firm(s.economy, kind);
            var alternative = firm(s.economy, CityMaterials.LOGGING);
            for (var company : s.economy.companies())
                company.cash = company.id == source.id || company.id == alternative.id ? 10000 : 0;
            int minimum = kind == CityEconomy.SHOP ? 2 : 1;
            var citizens = s.frame().citizens();
            for (int i = 0; i < citizens.size(); i++) {
                s.life(citizens.get(i).id()).education = CitizenLife.Education.SECONDARY;
                s.ecs.get(citizens.get(i).id(), CitySimulation.Household.class).job =
                        i < minimum ? 600 : 0;
            }
            s.economy.properties.add(
                    new CityEconomy.Property(600, 0, source.id, source.id, 200, 4));
            s.economy.properties.add(
                    new CityEconomy.Property(601, 0, alternative.id, alternative.id, 200, 4));
            var f = s.frame();
            var fixture =
                    new CityFrame(
                            f.config(),
                            f.elapsed(),
                            f.roads(),
                            f.zones(),
                            List.of(
                                    new CityFrame.Building(
                                            600,
                                            1,
                                            kind == CityEconomy.SHOP ? 1 : 2,
                                            12,
                                            24,
                                            28,
                                            minimum,
                                            0),
                                    new CityFrame.Building(601, 1, 2, 24, 24, 28, 16, 0)),
                            f.citizens(),
                            f.horses(),
                            f.economy());
            var run = new CitySimulation(f.config(), g, g.terrain, fixture);
            run.advance(.11); // First daily review retains a funded minimum crew.
            for (int i = 0; i < minimum; i++)
                assertEquals(
                        600, run.ecs.get(citizens.get(i).id(), CitySimulation.Household.class).job);
            run.economy.company(source.id).cash = 0;
            for (int tick = 0; tick < 12; tick++) {
                run.advance(.11); // Same day: insolvency must prompt an immediate search.
                for (int i = 0; i < minimum; i++)
                    assertEquals(
                            601,
                            run.ecs.get(citizens.get(i).id(), CitySimulation.Household.class).job,
                            "Tick "
                                    + tick
                                    + ": automatic hiring must not return workers to an unpaid"
                                    + " job");
            }
            assertEquals(0, run.economy.company(source.id).cash);
        }
    }

    @Test
    void housingAndLabourRespondToDemandWhileLeasesKeepAgreedPrices() {
        var s = city();
        var e = s.economy;
        int company = firm(e, CityEconomy.DEVELOPER).id;
        var home = new CityFrame.Building(999, 1, 0, 12, 30, 28, 4, 0);
        e.properties.add(new CityEconomy.Property(999, 0, company, 0, 50, .6));
        e.priceProperties(List.of(home));
        double crowded = e.property(999).rent();
        e.priceProperties(List.of(home, new CityFrame.Building(998, 1, 0, 24, 30, 28, 40, 0)));
        assertTrue(e.property(999).rent() < crowded);
        int citizen =
                s.frame().citizens().stream()
                        .filter(c -> c.cohort() == 0)
                        .findFirst()
                        .orElseThrow()
                        .id();
        assertTrue(e.house(citizen, home));
        double agreed = e.contracts.get(0).amount();
        e.priceProperties(List.of(home));
        assertEquals(agreed, e.contracts.get(0).amount());
        double labour = e.labourRate(company, 1.8);
        for (int i = 0; i < 6; i++)
            e.plots.add(new CityEconomy.Plot(i + 100, 1, 0, i * 10, 30, 28, company, 10, 10, 0, 0));
        assertTrue(e.labourRate(company, 1.8) > labour);
    }

    @Test
    void simulationChoosesCheapestSuitableHousingRatherThanFirstBuilding() {
        var g = new CityTest.Ground();
        var s = new CityTest().simulation(g);
        var owner = firm(s.economy, CityEconomy.DEVELOPER);
        var first = new CityFrame.Building(600, 1, 0, 12, 30, 28, 4, 0);
        var cheap = new CityFrame.Building(601, 1, 0, 24, 30, 28, 40, 0);
        s.economy.properties.add(new CityEconomy.Property(600, 0, owner.id, 0, 50, .6));
        s.economy.properties.add(new CityEconomy.Property(601, 0, owner.id, 0, 50, .6));
        var f = s.frame();
        var fixture =
                new CityFrame(
                        f.config(),
                        f.elapsed(),
                        f.roads(),
                        f.zones(),
                        List.of(first, cheap),
                        f.citizens(),
                        f.horses(),
                        s.economy.state());
        var run = new CitySimulation(f.config(), g, g.terrain, fixture);
        run.advance(.11);
        assertTrue(run.economy.property(601).rent() < run.economy.property(600).rent());
        assertTrue(run.frame().citizens().stream().allMatch(c -> c.home() == 601));
    }

    @Test
    void quotesAndBalancesSurviveReloadWithoutASeparateMarketReset() {
        var g = new CityTest.Ground();
        var s = new CityTest().simulation(g);
        var e = s.economy;
        var seller = firm(e, CityMaterials.LOGGING);
        e.resources.add(0, seller.id, Blocks.WOOD, 15 * CityMaterials.UNIT);
        double price = e.offer(0, seller.id, Blocks.WOOD);
        var f = s.frame();
        var restored = new CitySimulation(f.config(), g, g.terrain, f);
        assertEquals(price, restored.economy.offer(0, seller.id, Blocks.WOOD));
        assertEquals(e.state(), restored.economy.state());
    }
}
