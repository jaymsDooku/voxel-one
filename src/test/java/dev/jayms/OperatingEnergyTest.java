package dev.jayms;

import dev.jayms.net.city.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class OperatingEnergyTest {
    public static CitySimulation productionScenario(long extraPower, boolean succeeds) {
        var ground=new CityTest.Ground();
        var city=new CitySimulation(GameConfig.cityGame(),ground,ground.terrain,null);
        var e=city.economy; IndustrialProgressionTest.unlock(e.resources,7);
        var firm=e.companies().stream().filter(c->c.kind==47).findFirst().orElseThrow();
        firm.cash=0; // No supplier stock or private money can replenish the exact fixture.
        e.properties.add(new CityEconomy.Property(400,0,firm.id,firm.id,100,0));
        var recipe=e.resources.catalog.recipes().stream().filter(r->r.id().equals("industrial-electrical-equipment")).findFirst().orElseThrow();
        for(var input:recipe.inputs().entrySet()) e.resources.add(0,firm.id,input.getKey(),input.getValue()*CityMaterials.UNIT);
        e.resources.add(0,firm.id,IndustrialProgression.ELECTRICAL,CityMaterials.UNIT);
        if(extraPower>0)e.resources.add(0,firm.id,IndustrialProgression.POWER,extraPower);
        var before=e.resources.state().stocks();
        new CityHarvesting(ground,ground.terrain,(x,z)->false).work(e,firm,.25);
        assertEquals(succeeds?1:0,e.resources.batch(firm.id,recipe.id()).completed());
        if(succeeds) {
            assertEquals(0,e.resources.available(0,firm.id,IndustrialProgression.POWER));
            assertEquals((1+recipe.count())*CityMaterials.UNIT,e.resources.available(0,firm.id,IndustrialProgression.ELECTRICAL));
            for(int input:recipe.inputs().keySet())assertEquals(0,e.resources.available(0,firm.id,input));
        } else assertEquals(before,e.resources.state().stocks(),"No batch consumes recipe inputs or grants output without full energy");
        return city;
    }
    @Test void recipePowerAloneCannotPayBoostedOperatingEnergy() { productionScenario(0,false); }
    @Test void oneFixedPointUnitShortLeavesAllInputsUntouched() { productionScenario(CityMaterials.UNIT/16-1,false); }
    @Test void exactCombinedRecipeAndOperatingPowerCompletesBoostedBatch() { productionScenario(CityMaterials.UNIT/16,true); }
    @Test void ordinaryCraftStillConsumesOnlyRecipePower() {
        var stock=IndustrialProgressionTest.resources();IndustrialProgressionTest.unlock(stock,7);
        var recipe=stock.catalog.recipes().stream().filter(r->r.id().equals("industrial-electrical-equipment")).findFirst().orElseThrow();
        for(var input:recipe.inputs().entrySet())stock.add(0,1,input.getKey(),input.getValue()*CityMaterials.UNIT);
        assertEquals(recipe.count(),stock.craft(1,recipe));
        assertEquals(0,stock.available(0,1,IndustrialProgression.POWER));
    }
}
