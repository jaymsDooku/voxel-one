package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;
import dev.jayms.net.city.*;
import org.junit.jupiter.api.Test;

class ExchangeLabourMarketTest {
    CitySimulation fixture() {
        var helper = new CityCapitalTest();
        var city = helper.sim(new CityTest.Ground());
        assertTrue(city.command(helper.exchange(), 1, helper.pose).contains("built"));
        return city;
    }

    int exchange(CitySimulation city) {
        return city.frame().buildings().stream()
                .filter(b -> b.type() == SpecialBuildings.EXCHANGE).findFirst().orElseThrow().id();
    }

    @Test
    void quotesRespondToQualifiedSupplyAndExchangeDemand() {
        var city = fixture();
        for (int id : city.ecs.query(CitySimulation.Household.class))
            city.ecs.get(id, CitySimulation.Household.class).job = CityMaterials.YARD + 1;
        double scarceAnalyst = city.exchangeLabourRate(true);
        double scarceSupport = city.exchangeLabourRate(false);
        for (int id : city.ecs.query(CitySimulation.Household.class))
            city.ecs.get(id, CitySimulation.Household.class).job = 0;
        assertTrue(city.exchangeLabourRate(true) < scarceAnalyst);
        assertTrue(city.exchangeLabourRate(false) < scarceSupport);
        city.economy.capital.graduates.clear();
        assertEquals(scarceAnalyst, city.exchangeLabourRate(true), 1e-8);
        double before = city.exchangeLabourRate(false);
        assertTrue(city.command(new CityCommand(CityCommand.DEMOLISH, exchange(city), java.util.List.of()), 1, null).contains("demolished"));
        assertTrue(city.exchangeLabourRate(false) < before);
        assertTrue(Double.isFinite(city.exchangeLabourRate(true)));
    }

    @Test
    void actualPayrollUsesMarketQuotesAndOnlyTreasuryFunds() {
        var city = fixture();
        new CityCapitalTest().staff(city);
        for (var firm : city.economy.companies()) firm.cash = 0;
        double budget = city.economy.budget;
        double privateCash = city.economy.companies().stream().mapToDouble(c -> c.cash).sum();
        city.advance(.11);
        double payroll = 0;
        int paid = 0;
        for (int id : city.ecs.query(CitySimulation.Household.class)) {
            if (city.ecs.get(id, CitySimulation.Household.class).job != exchange(city)) continue;
            var activity = city.ecs.get(id, CitySimulation.Travel.class).activity;
            if (activity.startsWith("Exchange ")) {
                payroll += .1 * 24 / city.config().daySeconds()
                        * city.exchangeLabourRate(city.economy.capital.graduates.contains(id));
                paid++;
            }
        }
        assertTrue(paid >= 2);
        assertEquals(payroll, budget - city.economy.budget, 1e-6);
        assertEquals(privateCash, city.economy.companies().stream().mapToDouble(c -> c.cash).sum(), 1e-6);
        assertTrue(city.economy.capital.exchange.operational());
    }

    @Test
    void unfundedExchangeDoesNotHireAndWorkersCanTakeFundedPrivateJobs() throws Exception {
        var city = fixture();
        int office = exchange(city);
        city.economy.budget = 0;
        // Leave funded private vacancies available to each unpaid office worker.
        for (int id : city.ecs.query(CitySimulation.Household.class))
            if (city.ecs.get(id, CitySimulation.Household.class).job != office)
                city.ecs.get(id, CitySimulation.Household.class).job = CityMaterials.YARD + 1;
        var review = CitySimulation.class.getDeclaredMethod("chooseJobs", boolean.class);
        review.setAccessible(true);
        review.invoke(city, false);
        long remaining = city.frame().citizens().stream().filter(c -> c.job() == office).count();
        assertTrue(remaining < 4, "Unpaid workers take available funded vacancies");
        var moved = city.frame().citizens().stream().filter(c -> c.job() != office)
                .map(CityFrame.Citizen::id).toList();
        // Prevent unrelated private land purchases from refilling the empty treasury.
        for (var firm : city.economy.companies()) firm.cash = 0;
        for (int i = 0; i < 13; i++) city.advance(.11);
        assertTrue(city.frame().citizens().stream().filter(c -> c.job() == office).count() <= remaining);
        for (int id : moved) assertNotEquals(office, city.ecs.get(id, CitySimulation.Household.class).job);
        assertEquals(0, city.economy.budget);
        assertFalse(city.economy.capital.exchange.operational());
    }

    @Test
    void hiringKeepsSkillSlotsAndPaidWorkersCanChooseBetterOffers() throws Exception {
        var city = fixture();
        int office = exchange(city);
        var workers = city.frame().citizens().stream().filter(c -> c.job() == office).toList();
        assertEquals(4, workers.size());
        assertEquals(3, workers.stream().filter(c -> city.economy.capital.graduates.contains(c.id())).count());
        // Make a private firm's offer scarce and funded, then run the actual daily job review.
        for (int id : city.ecs.query(CitySimulation.Household.class))
            if (city.ecs.get(id, CitySimulation.Household.class).job != office)
                city.ecs.get(id, CitySimulation.Household.class).job = CityMaterials.YARD + 1;
        var review = CitySimulation.class.getDeclaredMethod("chooseJobs", boolean.class);
        review.setAccessible(true);
        review.invoke(city, true);
        assertTrue(workers.stream().anyMatch(c -> city.ecs.get(c.id(), CitySimulation.Household.class).job != office));
        var moved = workers.stream()
                .filter(c -> city.ecs.get(c.id(), CitySimulation.Household.class).job != office)
                .map(CityFrame.Citizen::id).toList();
        city.advance(.11);
        for (int id : moved) assertNotEquals(office, city.ecs.get(id, CitySimulation.Household.class).job);
        long analysts = city.frame().citizens().stream().filter(c -> c.job() == office)
                .filter(c -> city.economy.capital.graduates.contains(c.id())).count();
        long support = city.frame().citizens().stream().filter(c -> c.job() == office).count() - analysts;
        assertTrue(analysts <= 3);
        assertTrue(support <= 1);
    }

    @Test
    void unqualifiedApplicantsCannotFillAnalystVacancies() {
        var city = fixture();
        city.economy.capital.graduates.clear();
        for (int id : city.ecs.query(CitySimulation.Household.class))
            city.ecs.get(id, CitySimulation.Household.class).job = 0;
        city.advance(.11);
        assertEquals(1, city.frame().citizens().stream().filter(c -> c.job() == exchange(city)).count());
        assertFalse(city.economy.capital.exchange.operational());
    }

    @Test
    void graduatingSupportWorkerDoesNotOverfillOffice() {
        var city = fixture();
        int office = exchange(city);
        for (var citizen : city.frame().citizens())
            if (citizen.job() == office) city.economy.capital.graduates.add(citizen.id());
        for (var firm : city.economy.companies()) firm.cash = 0;
        city.advance(.11);
        assertEquals(4, city.frame().citizens().stream().filter(c -> c.job() == office).count());
    }
}
