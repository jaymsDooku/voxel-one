package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;
import dev.jayms.net.city.*;
import org.junit.jupiter.api.Test;
import java.util.*;

class ShopRestockMarketTest {
    static final int A = 1020, B = 1021, SHOP = 600;

    CitySimulation fixture(double priceA, double priceB, int stockA, int stockB) {
        return fixture(priceA, priceB, stockA, stockB, 35, 35);
    }

    CitySimulation fixture(double priceA, double priceB, int stockA, int stockB, int nutritionA, int nutritionB) {
        var ground = new CityTest.Ground();
        var catalog = new ProductionCatalog(List.of(
                new ProductionCatalog.Product(CityMaterials.FOOD, "Food", 1, 35),
                new ProductionCatalog.Product(A, "Meal A", priceA, nutritionA),
                new ProductionCatalog.Product(B, "Meal B", priceB, nutritionB)), List.of(), List.of());
        var seed = new CitySimulation(new GameConfig(true, false, 1200, 10),
                ground, ground.terrain, null, catalog);
        var shop = shop(seed);
        var sellers = sellers(seed);
        seed.economy.resources.add(0, sellers.get(0).id, A, stockA * CityMaterials.UNIT);
        seed.economy.resources.add(0, sellers.get(1).id, B, stockB * CityMaterials.UNIT);
        seed.economy.properties.add(new CityEconomy.Property(SHOP, 0, shop.id, shop.id, 200, 4));
        seed.economy.businesses.open(SHOP, shop.id);
        for (int id : seed.ecs.query(CitySimulation.Needs.class))
            seed.ecs.get(id, CitySimulation.Needs.class).hunger = 100;
        var f = seed.frame();
        var buildings = List.of(new CityFrame.Building(SHOP, 1, 1, 12, 24, 28, 2, 0));
        var frame = new CityFrame(f.config(), f.elapsed(), f.roads(), List.of(), buildings,
                f.citizens(), f.horses(), f.economy(), CityAddresses.migrate(f.roads(), buildings),
                new Agriculture.State(true, false, List.of(), List.of(), List.of(), List.of()));
        return new CitySimulation(f.config(), ground, ground.terrain, frame);
    }

    CityEconomy.Company shop(CitySimulation city) {
        return city.economy.companies().stream().filter(c -> c.kind == CityEconomy.SHOP)
                .findFirst().orElseThrow();
    }

    List<CityEconomy.Company> sellers(CitySimulation city) {
        return city.economy.companies().stream().filter(c -> c.kind >= 2 && c.kind <= 6).toList();
    }

    void restock(CitySimulation city) throws Exception {
        var method = CitySimulation.class.getDeclaredMethod("restock");
        method.setAccessible(true);
        method.invoke(city);
    }

    long stock(CitySimulation city, int product) {
        return city.economy.resources.available(0, shop(city).id, product) / CityMaterials.UNIT;
    }

    @Test
    void reversedReferenceAndSupplierOfferOrderingBuysCheapestSuitableFood() throws Exception {
        var city = fixture(1, 1.02, 10, 30);
        var suppliers = sellers(city);
        city.economy.resources.add(0, suppliers.get(2).id, A, 10 * CityMaterials.UNIT);
        city.economy.resources.add(0, suppliers.get(3).id, A, 10 * CityMaterials.UNIT);
        assertTrue(city.economy.marketPrice(A) < city.economy.marketPrice(B));
        assertTrue(city.economy.offer(0, suppliers.get(1).id, B)
                < city.economy.offer(0, suppliers.get(0).id, A));
        var developer = city.economy.companies().stream()
                .filter(c -> c.kind == CityEconomy.DEVELOPER).findFirst().orElseThrow();
        city.economy.resources.add(0, developer.id, A, 1000 * CityMaterials.UNIT);
        double developerCash = developer.cash;
        assertTrue(city.economy.offer(0, developer.id, A)
                < city.economy.offer(0, suppliers.get(1).id, B));
        double cash = city.economy.companies().stream().mapToDouble(c -> c.cash).sum();
        restock(city);
        assertEquals(0, stock(city, A));
        assertEquals(16, stock(city, B));
        assertEquals(cash, city.economy.companies().stream().mapToDouble(c -> c.cash).sum(), 1e-8);
        assertEquals(1, city.frame().buildings().get(0).stock());
        assertEquals(developerCash, developer.cash);
    }

    @Test
    void supplierDepletionRechecksSubstitutesBeforeMoreExpensiveFills() throws Exception {
        var city = fixture(.6, 1.7, 3, 100);
        city.economy.resources.add(0, sellers(city).get(2).id, A, CityMaterials.UNIT);
        assertTrue(city.economy.offer(0, sellers(city).get(0).id, A)
                < city.economy.offer(0, sellers(city).get(1).id, B));
        restock(city);
        assertEquals(2, stock(city, A));
        assertEquals(14, stock(city, B));
    }

    @Test
    void unaffordableAndEmptyOffersDoNotSpendMoneyOrOverdraw() throws Exception {
        var city = fixture(1, 1.02, 0, 30);
        var buyer = shop(city);
        buyer.cash = .2;
        restock(city);
        assertEquals(.2, buyer.cash);
        assertEquals(0, stock(city, A) + stock(city, B));
        buyer.cash = .5;
        restock(city);
        assertEquals(2, stock(city, B));
        assertTrue(buyer.cash >= 0);
    }
    @Test
    void higherNutritionCanBeatLowerPortionPrice() throws Exception {
        var city = fixture(1, 2, 30, 30, 10, 35);
        var suppliers = sellers(city);
        assertTrue(city.economy.offer(0, suppliers.get(0).id, A)
                < city.economy.offer(0, suppliers.get(1).id, B));
        restock(city);
        assertEquals(0, stock(city, A));
        assertEquals(16, stock(city, B));
    }

}
