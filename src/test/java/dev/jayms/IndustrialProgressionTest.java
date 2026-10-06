package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.BusinessDashboard;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IndustrialProgressionTest {
    static CityMaterials resources() { return new CityMaterials(CityMaterials.State.empty()); }
    static void unlock(CityMaterials stock, int tier) {
        for (var era : IndustrialProgression.TIERS.subList(1, tier))
            for (String proof : era.proofs()) {
                var recipe = stock.catalog.recipes().stream().filter(r -> r.id().equals(proof)).findFirst().orElseThrow();
                stock.batch(recipe.companyKind(), proof, 0, 1);
            }
    }
    @Test void allEightErasNeedEveryPriorProofAndSurviveSerializationAndSale() throws Exception {
        var stock = resources();
        assertEquals(1, IndustrialProgression.tier(stock.state()));
        stock.batch(48, "industrial-electronics", 0, 20);
        assertEquals(1, IndustrialProgression.tier(stock.state()), "Future stock cannot skip tiers");
        for (int tier = 2; tier <= 8; tier++) {
            unlock(stock, tier);
            assertEquals(tier, IndustrialProgression.tier(stock.state()));
            assertEquals(tier, IndustrialProgression.tier(stock));
            assertTrue(stock.state().stocks().isEmpty(), "Unlocks outlive inventory sales");
        }
        var bytes = new ByteArrayOutputStream();
        CityMaterials.write(new DataOutputStream(bytes), stock.state());
        assertEquals(stock.state(), CityMaterials.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }
    @Test void lockedWorkAndMissingInputsCannotCreateProductsOrSpendPrivateMoney() {
        var ground = new CityTest.Ground();
        var city = new CitySimulation(GameConfig.cityGame(), ground, ground.terrain, null);
        var e = city.economy;
        var worker = new CityHarvesting(ground, ground.terrain, (x,z)->false);
        var smelter = e.companies().stream().filter(c -> c.kind == 34).findFirst().orElseThrow();
        e.properties.add(new CityEconomy.Property(400, 0, smelter.id, smelter.id, 100, 0));
        e.resources.add(0, smelter.id, IndustrialProgression.IRON_ORE, 2 * CityMaterials.UNIT);
        double cash = smelter.cash;
        var before = e.resources.state().stocks();
        worker.work(e, smelter, 1);
        assertEquals(before, e.resources.state().stocks());
        assertEquals(cash, smelter.cash);
        assertEquals("Locked: tier 2", e.resources.production(smelter.id).status());
        unlock(e.resources, 2);
        worker.work(e, smelter, 1);
        assertEquals(0, e.resources.available(0, smelter.id, IndustrialProgression.IRON));
        assertEquals(2 * CityMaterials.UNIT, e.resources.available(0, smelter.id, IndustrialProgression.IRON_ORE));
        assertNull(e.buyPlot(1,2,20,27,30,48));
    }
    @Test void everyNewRecipeCanRunOnlyAtItsEraAndConservesItsInputs() {
        for (var recipe : ProductionCatalog.cityGame().recipes()) {
            if (!recipe.id().startsWith("industrial-")) continue;
            var stock = resources();
            for (var input : recipe.inputs().entrySet()) stock.add(0, 1, input.getKey(), input.getValue() * CityMaterials.UNIT);
            assertEquals(0, stock.craft(1, recipe), recipe.id() + " locked");
            unlock(stock, IndustrialProgression.required(recipe.companyKind()));
            assertEquals(recipe.count(), stock.craft(1, recipe), recipe.id());
            for (var input : recipe.inputs().keySet()) assertEquals(0, stock.available(0, 1, input), recipe.id());
            assertEquals(recipe.count() * CityMaterials.UNIT, stock.available(0, 1, recipe.output()));
        }
    }
    @Test void fleetsExtendRangeAndBurnEnergyWithoutConsumingVehicles() {
        var stock = resources();
        assertTrue(IndustrialLogistics.deliver(stock,0,1,80,CityMaterials.UNIT), "Founding farms can buy building supplies");
        assertFalse(IndustrialLogistics.deliver(stock,0,1,100,CityMaterials.UNIT));
        unlock(stock, 4);
        assertFalse(IndustrialLogistics.deliver(stock, 0, 1, Double.NaN, CityMaterials.UNIT));
        assertFalse(IndustrialLogistics.deliver(stock, 0, 1, 1, -1));
        assertFalse(IndustrialLogistics.deliver(stock, 0, 1, 200, 16 * CityMaterials.UNIT));
        stock.add(0,1,IndustrialProgression.FREIGHT_TRAIN,CityMaterials.UNIT);
        stock.add(0,1,IndustrialProgression.STATION,CityMaterials.UNIT);
        assertFalse(IndustrialLogistics.deliver(stock,0,1,200,16*CityMaterials.UNIT));
        stock.add(0,1,IndustrialProgression.COAL,4*CityMaterials.UNIT);
        long before=stock.available(0,1,IndustrialProgression.COAL);
        assertTrue(IndustrialLogistics.deliver(stock,0,1,200,16*CityMaterials.UNIT));
        assertEquals(before-12800,stock.available(0,1,IndustrialProgression.COAL));
        assertEquals(CityMaterials.UNIT,stock.available(0,1,IndustrialProgression.FREIGHT_TRAIN));
        assertFalse(IndustrialLogistics.deliver(stock,0,1,600,CityMaterials.UNIT));
    }
    @Test void machineryAndElectricityImproveFactoriesAndAdvancedGoodsUnlockRealDenseBlueprint() {
        var stock=resources(); unlock(stock,8);
        assertFalse(IndustrialLogistics.denseHousing(stock.state()));
        stock.batch(48,"industrial-electronics",0,1); stock.batch(48,"industrial-alloys",0,1);
        assertTrue(IndustrialLogistics.denseHousing(stock.state()));
        stock.add(0,1,IndustrialProgression.MACHINERY,CityMaterials.UNIT);
        assertEquals(1.5,IndustrialLogistics.productivity(stock,1));
        stock.add(0,1,IndustrialProgression.ELECTRICAL,CityMaterials.UNIT);
        stock.add(0,1,IndustrialProgression.POWER,CityMaterials.UNIT);
        assertEquals(2,IndustrialLogistics.productivity(stock,1));
        assertTrue(StructureBlueprint.generate(0,50,0,0,0).stream().anyMatch(e->e.y()==9 && e.type()!=0));
        assertNotEquals(CityMaterials.requirements(0,0),CityMaterials.requirements(0,50));
    }
    @Test void privateFreightTransfersAreAtomicAndPaySellersOnlyAfterEnergyChecks() {
        var ground = new CityTest.Ground();
        var city = new CitySimulation(GameConfig.cityGame(), ground, ground.terrain, null);
        var e = city.economy;
        int buyer=e.companies().stream().filter(c->c.kind==0).findFirst().orElseThrow().id;
        int seller=e.companies().stream().filter(c->c.kind==2).findFirst().orElseThrow().id;
        e.properties.add(new CityEconomy.Property(1000,0,buyer,buyer,100,0));
        e.properties.add(new CityEconomy.Property(1001,0,seller,seller,100,0));
        e.logisticsSites(List.of(new CityFrame.Building(1000,0,2,0,27,0,16,0),new CityFrame.Building(1001,0,2,300,27,0,16,0)));
        e.resources.add(0,seller,Blocks.STONE,16*CityMaterials.UNIT);
        var before=e.resources.state().stocks(); double cash=e.company(buyer).cash;
        assertFalse(e.trade(0,seller,0,buyer,Blocks.STONE,16*CityMaterials.UNIT));
        assertEquals(before,e.resources.state().stocks()); assertEquals(cash,e.company(buyer).cash);
        unlock(e.resources,4);
        e.resources.add(0,buyer,IndustrialProgression.FREIGHT_TRAIN,CityMaterials.UNIT);
        e.resources.add(0,buyer,IndustrialProgression.STATION,CityMaterials.UNIT);
        e.resources.add(0,buyer,IndustrialProgression.COAL,8*CityMaterials.UNIT);
        double total=e.companies().stream().mapToDouble(c->c.cash).sum();
        assertTrue(e.trade(0,seller,0,buyer,Blocks.STONE,16*CityMaterials.UNIT));
        assertEquals(16*CityMaterials.UNIT,e.resources.available(0,buyer,Blocks.STONE));
        assertEquals(0,e.resources.available(0,seller,Blocks.STONE));
        assertEquals(total,e.companies().stream().mapToDouble(c->c.cash).sum(),1e-8);
        assertEquals(8*CityMaterials.UNIT-19200,e.resources.available(0,buyer,IndustrialProgression.COAL));
    }
    @Test void busAndMetroTripsPayFaresAndStopWhenEnergyRunsOut() {
        var ground=new CityTest.Ground();
        var city=new CitySimulation(GameConfig.cityGame(),ground,ground.terrain,null);
        var e=city.economy; unlock(e.resources,5);
        int provider=e.companies().stream().filter(c->c.kind==43).findFirst().orElseThrow().id;
        int passenger=city.frame().citizens().get(0).id();
        var needs=city.ecs.get(passenger,CitySimulation.Needs.class); needs.money=10;
        e.resources.add(0,provider,IndustrialProgression.BUS,CityMaterials.UNIT);
        e.resources.add(0,provider,IndustrialProgression.FUEL,CityMaterials.UNIT);
        double before=e.company(provider).cash;
        assertEquals(6,e.passengerSpeed(passenger,.5));
        assertEquals(9.9,needs.money,1e-5); assertEquals(before+.1,e.company(provider).cash,1e-8);
        assertEquals(CityMaterials.UNIT/2,e.resources.available(0,provider,IndustrialProgression.FUEL));
        e.resources.remove(0,provider,IndustrialProgression.FUEL,CityMaterials.UNIT/2);
        assertEquals(2.2,e.passengerSpeed(passenger,.5));
        unlock(e.resources,7);
        e.resources.add(0,provider,IndustrialProgression.METRO,CityMaterials.UNIT);
        assertEquals(2.2,e.passengerSpeed(passenger,.5));
        e.resources.add(0,provider,IndustrialProgression.POWER,CityMaterials.UNIT);
        assertEquals(10,e.passengerSpeed(passenger,.5));
        assertEquals(CityMaterials.UNIT/2,e.resources.available(0,provider,IndustrialProgression.POWER));
    }
    @Test void defaultSaveUpgradeRetainsPrivateCompanyIdsAndCustomCatalogDoesNotUpgrade() {
        var legacy=new CityEconomy(new Ecs(),null,ProductionCatalog.toolEra());
        var logger=legacy.companies().stream().filter(c->c.kind==3).findFirst().orElseThrow();
        legacy.resources.production(logger.id,0,8,8,"Working");
        legacy.resources.add(0,logger.id,Blocks.PLANKS,512*CityMaterials.UNIT);
        var saved=legacy.state();
        var previousDefault=new CityEconomy.State(saved.budget(),saved.roadSpending(),saved.landRevenue(),saved.rentClock(),
                saved.firms(),saved.plots(),saved.properties(),saved.contracts(),saved.businesses(),
                new CityMaterials.State(saved.resources().stocks(),saved.resources().production(),List.of(),ProductionCatalog.settlementGame()),saved.capital());
        var old=new CityEconomy(new Ecs(),previousDefault);
        assertTrue(IndustrialProgression.enabled(old.resources.catalog));
        assertEquals(35,old.companies().size());
        assertEquals(2,IndustrialProgression.tier(old.resources));
        assertEquals(2,old.resources.completed("planks"));
        assertEquals(512*CityMaterials.UNIT,old.resources.available(0,logger.id,Blocks.PLANKS));
        for(var firm:saved.firms()) {
            assertEquals(firm.cash(),old.company(firm.id()).cash);
            assertEquals(firm.name(),old.company(firm.id()).name);
        }
        assertFalse(IndustrialProgression.enabled(legacy.resources.catalog));
        assertEquals(11,legacy.companies().size());
    }
    @Test void olderCustomHarvestersInTheNewIdRangeKeepTheirProductionRules() {
        var base=ProductionCatalog.toolEra();
        var types=new ArrayList<>(base.businesses().types());
        types.add(new BusinessCatalog.Type(35,"Custom quarry",List.of(Blocks.STONE),1,2));
        var catalog=new ProductionCatalog(base.products(),base.recipes(),base.equipment(),
                new BusinessCatalog(types,List.of(new BusinessCatalog.Company("custom-quarry","Custom quarry",35,100))));
        var e=new CityEconomy(new Ecs(),null,catalog);
        var ground=new CityTest.Ground();
        var worker=new CityHarvesting(ground,ground.terrain,(x,z)->false);
        worker.work(e,e.companies().get(0),1);
        assertFalse(IndustrialProgression.enabled(e.resources.catalog));
        assertEquals(CityMaterials.UNIT,e.resources.available(0,e.companies().get(0).id,Blocks.STONE));
    }
    @Test void savedCustomCatalogsKeepTheirRulesAndNewProgressionTabAcceptsClick() {
        var legacy = new CityMaterials(new CityMaterials.State(List.of(),List.of(),List.of(),ProductionCatalog.toolEra()));
        assertFalse(IndustrialProgression.enabled(legacy.catalog));
        assertTrue(IndustrialProgression.unlocked(legacy,48));
        var ui = new BusinessDashboard(); ui.click(600,150,1280,720);
        assertEquals(2,ui.view);
    }
}
