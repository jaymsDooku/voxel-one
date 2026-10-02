package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.city.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class CityMetricsTest {
    static CityFrame.Citizen citizen(
            int id,
            String name,
            int cohort,
            float hunger,
            float money,
            int home,
            int job,
            String activity) {
        return new CityFrame.Citizen(
                id, name, cohort, 8, 25, 24, 0, 0, hunger, money, home, job, 0, activity);
    }

    static CityFrame fixture() {
        var firms =
                List.of(
                        new CityEconomy.Firm(19, "Mining Co", 2, 500, 0, 0, 20, 50),
                        new CityEconomy.Firm(20, "Builders", 0, 100, 24, 90, 1, 0));
        var plots = List.of(new CityEconomy.Plot(1, 1, 0, 12, 24, 26, 20, 24, 90, 2, 0));
        var properties =
                List.of(
                        new CityEconomy.Property(1, 0, 20, 0, 50, .6),
                        new CityEconomy.Property(2, 0, 20, 19, 400, 6));
        var state = new CityEconomy.State(10000, 0, 24, 0, firms, plots, properties, List.of());
        return new CityFrame(
                GameConfig.cityGame(),
                20,
                List.of(),
                List.of(),
                List.of(
                        new CityFrame.Building(1, 1, 0, 12, 24, 26, 4, 0),
                        new CityFrame.Building(2, 2, 2, 0, 24, 14, 16, 0),
                        new CityFrame.Building(3, 3, 1, 14, 24, 14, 16, 27)),
                List.of(
                        citizen(1, "Alex", 0, 8, 2, 1, 2, "Working in mine"),
                        citizen(2, "Robin", 0, 34, 10, 0, 0, "Route obstructed"),
                        citizen(3, "Sam", 1, 35, 4, 1, -1, "Building for developer"),
                        citizen(4, "Sky", 2, 100, 100, 99, 99, "Needs home / work")),
                List.of(),
                state);
    }

    @Test
    void emptyPopulationHasFiniteMetricsAndAllGroups() {
        var m = CityMetrics.from(CityFrame.empty(GameConfig.cityGame()));
        assertEquals(0, m.population());
        assertEquals(0, m.averageHunger());
        assertEquals(0, m.averageSavings());
        assertEquals(0, m.medianSavings());
        assertEquals(0, m.employmentPercent());
        assertEquals(0, m.housingPercent());
        assertEquals(3, m.groups().size());
        assertTrue(m.groups().stream().allMatch(g -> g.population() == 0));
    }

    @Test
    void populationIndicatorsRespectThresholdsAndValidHomesEmployers() {
        var m = CityMetrics.from(fixture());
        assertEquals(4, m.population());
        assertEquals(2, m.housed());
        assertEquals(2, m.employed());
        assertEquals(2, m.hungry());
        assertEquals(1, m.criticalHunger());
        assertEquals(1, m.lowFunds());
        assertEquals(1, m.blockedRoutes());
        assertEquals(44.25, m.averageHunger());
        assertEquals(116, m.totalSavings());
        assertEquals(29, m.averageSavings());
        assertEquals(7, m.medianSavings());
        assertEquals(4, m.beds());
        assertEquals(2, m.vacantBeds());
        assertEquals(27, m.foodStock());
        assertEquals(1, m.projects());
        assertEquals(50, m.housingPercent());
        assertEquals(50, m.employmentPercent());
    }

    @Test
    void groupsAndOddMedianAreComputedFromTheirOwnPopulations() {
        var f = fixture();
        var m = CityMetrics.from(f);
        var g = m.groups().get(0);
        assertEquals(2, g.population());
        assertEquals(1, g.housed());
        assertEquals(1, g.employed());
        assertEquals(2, g.hungry());
        assertEquals(21, g.averageHunger());
        assertEquals(6, g.averageSavings());
        assertEquals(6, g.medianSavings());
        assertEquals(4, m.groups().get(1).medianSavings());
        assertEquals(100, m.groups().get(2).medianSavings());
        var cs = new ArrayList<>(f.citizens());
        cs.remove(3);
        var odd =
                new CityFrame(
                        f.config(),
                        f.elapsed(),
                        f.roads(),
                        f.zones(),
                        f.buildings(),
                        cs,
                        f.horses(),
                        f.economy());
        assertEquals(4, CityMetrics.from(odd).medianSavings());
        assertEquals(4, f.citizens().size());
    }

    @Test
    void finishedProjectsAndMissingFirmsAreNotCountedAsJobs() {
        var f = fixture();
        var e = f.economy();
        var state =
                new CityEconomy.State(
                        e.budget(),
                        e.roadSpending(),
                        e.landRevenue(),
                        e.rentClock(),
                        List.of(),
                        List.of(e.plots().get(0).complete(1)),
                        e.properties(),
                        e.contracts());
        var invalid =
                new CityFrame(
                        f.config(),
                        f.elapsed(),
                        f.roads(),
                        f.zones(),
                        f.buildings(),
                        f.citizens(),
                        f.horses(),
                        state);
        assertEquals(0, CityMetrics.from(invalid).employed());
        assertEquals(0, CityMetrics.from(invalid).projects());
    }

    @Test
    void metricsRefreshWhenTheCitySnapshotChanges() {
        var f = fixture();
        var cs = new ArrayList<>(f.citizens());
        cs.set(0, citizen(1, "Alex", 0, 90, 20, 1, 2, "Working in mine"));
        var changed =
                new CityFrame(
                        f.config(),
                        f.elapsed() + 1,
                        f.roads(),
                        f.zones(),
                        f.buildings(),
                        cs,
                        f.horses(),
                        f.economy());
        assertEquals(1, CityMetrics.from(changed).hungry());
        assertEquals(0, CityMetrics.from(changed).lowFunds());
        assertEquals(2, CityMetrics.from(f).hungry());
    }
}
