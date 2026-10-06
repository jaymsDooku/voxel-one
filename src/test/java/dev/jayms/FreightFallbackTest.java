package dev.jayms;

import dev.jayms.net.city.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class FreightFallbackTest {
    public static void cartFallbackScenario() {
        var stock=IndustrialProgressionTest.resources();
        IndustrialProgressionTest.unlock(stock,4);
        stock.add(0,1,IndustrialProgression.CART,CityMaterials.UNIT);
        assertTrue(IndustrialLogistics.deliver(stock,0,1,120,16*CityMaterials.UNIT));
        stock.add(0,1,IndustrialProgression.FREIGHT_TRAIN,CityMaterials.UNIT);
        stock.add(0,1,IndustrialProgression.STATION,CityMaterials.UNIT);
        stock.add(0,1,IndustrialProgression.COAL,CityMaterials.UNIT);
        var before=stock.state();
        assertTrue(IndustrialLogistics.deliver(stock,0,1,120,16*CityMaterials.UNIT));
        assertEquals(before,stock.state(),"Cart fallback spends no coal or fleet assets");
        assertFalse(IndustrialLogistics.deliver(stock,0,1,300,16*CityMaterials.UNIT));
        assertEquals(before,stock.state(),"No feasible mode changes nothing");
        stock.add(0,1,IndustrialProgression.COAL,8*CityMaterials.UNIT);
        long coal=stock.available(0,1,IndustrialProgression.COAL);
        assertTrue(IndustrialLogistics.deliver(stock,0,1,300,16*CityMaterials.UNIT));
        assertEquals(coal-19200,stock.available(0,1,IndustrialProgression.COAL));
    }
    public static CitySimulation foodFallbackScenario() {
        var ground=new CityTest.Ground();
        var city=new CitySimulation(GameConfig.cityGame(),ground,ground.terrain,null);
        var e=city.economy;
        var shop=e.companies().stream().filter(c->c.kind==CityEconomy.SHOP).findFirst().orElseThrow();
        var far=e.companies().stream().filter(c->c.kind==CityMaterials.FARM).findFirst().orElseThrow();
        var near=e.companies().stream().filter(c->c.kind==CityMaterials.SUGARCANE_FARM).findFirst().orElseThrow();
        var companies=List.of(shop,far,near);
        for(int i=0;i<3;i++) {
            var c=companies.get(i); c.cash=1000;
            e.properties.add(new CityEconomy.Property(5000+i,0,c.id,c.id,100,0));
        }
        e.logisticsSites(List.of(new CityFrame.Building(5000,0,1,0,27,0,16,0),
                new CityFrame.Building(5001,0,3,1000,27,0,16,0),
                new CityFrame.Building(5002,0,3,48,27,0,16,0)));
        e.resources.add(0,far.id,CityMaterials.CARROT,100*CityMaterials.UNIT);
        e.resources.add(0,near.id,CityMaterials.CARROT,10*CityMaterials.UNIT);
        assertTrue(e.offer(0,far.id,CityMaterials.CARROT)<e.offer(0,near.id,CityMaterials.CARROT));
        double total=e.companies().stream().mapToDouble(c->c.cash).sum();
        e.restockFood(shop.id,3);
        assertEquals(3*CityMaterials.UNIT,e.resources.available(0,shop.id,CityMaterials.CARROT));
        assertEquals(7*CityMaterials.UNIT,e.resources.available(0,near.id,CityMaterials.CARROT));
        assertEquals(100*CityMaterials.UNIT,e.resources.available(0,far.id,CityMaterials.CARROT));
        assertEquals(total,e.companies().stream().mapToDouble(c->c.cash).sum(),1e-8);
        assertTrue(shop.cash<1000); assertTrue(near.receipts>0); assertEquals(0,far.receipts);
        // Once feasible stock is exhausted, the remote farm cannot cause a partial failed trade.
        e.restockFood(shop.id,12);
        assertEquals(10*CityMaterials.UNIT,e.resources.available(0,shop.id,CityMaterials.CARROT));
        var stocks=e.resources.state(); double cash=shop.cash;
        e.restockFood(shop.id,12);
        assertEquals(stocks,e.resources.state()); assertEquals(cash,shop.cash);
        return city;
    }
    @Test void insufficientTrainEnergyFallsBackToCart() { cartFallbackScenario(); }
    @Test void unreachableCheapestFarmDoesNotBlockAffordableLocalFood() { foodFallbackScenario(); }
}
