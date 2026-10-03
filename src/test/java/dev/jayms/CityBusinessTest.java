package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.city.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.util.*;

class CityBusinessTest {
    @TempDir Path directory;

    private CitySimulation settled(CityTest.Ground ground) {
        var sim = new CityTest().simulation(ground);
        for (int i = 0; i < 160; i++) sim.advance(1);
        return sim;
    }

    @Test
    void staffedBusinessesProduceSellAndReconcileCashAndStock() {
        var g = new CityTest.Ground();
        var s = settled(g);
        var frame = s.frame();
        var metrics = BusinessMetrics.from(frame);
        assertEquals(8, metrics.locations().size());
        for (var l : metrics.locations()) {
            if (l.firm().kind() == CityMaterials.TOOLS) {
                assertEquals("Needs industrial factory", l.status());
                assertTrue(l.employees().isEmpty());
                continue;
            }
            assertNotNull(l.account());
            assertFalse(l.employees().isEmpty());
            assertTrue(l.total().wages() > 0);
        }
        for (var c : metrics.companies())
            if (c.firm().kind() != 0) {
                var f = c.firm();
                var accounts =
                        frame.economy().businesses().stream()
                                .filter(a -> a.company() == f.id())
                                .toList();
                assertEquals(
                        f.wages(),
                        accounts.stream().mapToDouble(a -> a.total().wages()).sum(),
                        .0001);
                assertEquals(f.receipts(), c.revenue(), .0001);
                double initial = f.kind() == 1 ? 1500 - 240 : f.kind() == 2 ? 2000 : 1500;
                assertEquals(initial + c.profit(), f.cash(), .0001);
            }
        var shop =
                metrics.locations().stream()
                        .filter(l -> l.building().type() == 1)
                        .findFirst()
                        .orElseThrow();
        assertEquals(shop.total().received() - shop.total().sold(), shop.building().stock());
        assertTrue(shop.total().sold() > 0);
        assertEquals(10120, frame.economy().budget());
        assertEquals(11, metrics.companies().size());
    }

    @Test
    void shopSalesRequireStaffActuallyPresentInTheWorkplace() {
        var g = new CityTest.Ground();
        var s = settled(g);
        var f = s.frame();
        var shop = f.buildings().stream().filter(b -> b.type() == 1).findFirst().orElseThrow();
        for (var c : f.citizens()) {
            var p = s.ecs.get(c.id(), CitySimulation.Position.class);
            var t = s.ecs.get(c.id(), CitySimulation.Travel.class);
            t.route.clear();
            t.target = -9999;
            t.retryAt = 0;
            t.mealUntil = 0;
            if (c.job() == shop.id()) {
                p.x = 1000;
                p.z = 1000;
            } else {
                p.x = shop.x() + 2.5f;
                p.z = shop.z() + 2.5f;
                s.ecs.get(c.id(), CitySimulation.Needs.class).hunger = 20;
            }
        }
        double sales =
                BusinessMetrics.from(s.frame()).locations().stream()
                        .filter(l -> l.building().type() == 1)
                        .findFirst()
                        .orElseThrow()
                        .total()
                        .revenue();
        s.advance(1);
        var location =
                BusinessMetrics.from(s.frame()).locations().stream()
                        .filter(l -> l.building().type() == 1)
                        .findFirst()
                        .orElseThrow();
        assertEquals(sales, location.total().revenue());
        assertEquals("Awaiting staff", location.status());
        assertTrue(location.attention());
    }

    @Test
    void commercialShiftsOperateInEveningWhileMinesClose() {
        var g = new CityTest.Ground();
        var s = settled(g);
        var f = s.frame();
        s = new CitySimulation(new GameConfig(true, false, 1200, 18), g, g.terrain, f);
        for (var c : f.citizens()) {
            var b =
                    BusinessMetrics.from(f).locations().stream()
                            .map(BusinessMetrics.Location::building)
                            .filter(v -> v.id() == c.job())
                            .findFirst()
                            .orElseThrow();
            var p = s.ecs.get(c.id(), CitySimulation.Position.class);
            p.x = b.x() + 2.5f;
            p.z = b.z() + 2.5f;
            var t = s.ecs.get(c.id(), CitySimulation.Travel.class);
            t.route.clear();
            t.target = c.job();
            t.mealUntil = 0;
            s.ecs.get(c.id(), CitySimulation.Needs.class).hunger = 100;
        }
        var before = BusinessMetrics.from(s.frame());
        s.advance(1);
        var after = BusinessMetrics.from(s.frame());
        var mineBefore =
                before.locations().stream()
                        .filter(l -> l.building().type() == 2)
                        .findFirst()
                        .orElseThrow();
        var mineAfter =
                after.locations().stream()
                        .filter(l -> l.building().type() == 2)
                        .findFirst()
                        .orElseThrow();
        assertEquals(mineBefore.total().wages(), mineAfter.total().wages());
        assertEquals("Closed", mineAfter.status());
        var shop =
                after.locations().stream()
                        .filter(l -> l.building().type() == 1)
                        .findFirst()
                        .orElseThrow();
        assertTrue(
                shop.total().wages()
                        > before.locations().stream()
                                .filter(l -> l.building().type() == 1)
                                .findFirst()
                                .orElseThrow()
                                .total()
                                .wages());
        assertEquals(1, shop.working());
    }

    @Test
    void bankruptEmployersStopPayAndRaiseBusinessAlertsWithoutPublicSubsidy() {
        var g = new CityTest.Ground();
        var s = settled(g);
        for (var f : s.economy.companies()) if (f.kind != 0) f.cash = 0;
        double budget = s.economy.budget;
        s.advance(5);
        var m = BusinessMetrics.from(s.frame());
        assertTrue(m.locations().stream().allMatch(BusinessMetrics.Location::attention));
        assertTrue(m.locations().stream().anyMatch(l -> l.today().missedWages() > 0));
        assertEquals(budget, s.economy.budget);
        assertTrue(
                m.companies().stream()
                        .filter(c -> c.firm().kind() != 0)
                        .allMatch(c -> c.firm().cash() == 0));
    }

    @Test
    void dailyAccountsCloseAtMidnightAndHistoryIsBoundedAndPersistent() throws Exception {
        var accounts = new CityBusinesses(List.of());
        accounts.open(1, 2);
        for (int day = 1; day <= 10; day++) {
            accounts.beginDay(day);
            accounts.sale(1, 2, 6);
            accounts.wage(1, 2, 1);
        }
        var r = accounts.records().get(0);
        assertEquals(60, r.total().revenue());
        assertEquals(40, r.total().profit());
        assertEquals(7, r.history().size());
        assertEquals(3, r.history().get(0).day());
        assertEquals(10, r.today().day());
        var bytes = new ByteArrayOutputStream();
        CityBusinesses.write(new DataOutputStream(bytes), accounts.records());
        var restored =
                new CityBusinesses(
                        CityBusinesses.read(
                                new DataInputStream(
                                        new ByteArrayInputStream(bytes.toByteArray()))));
        assertEquals(accounts.records(), restored.records());
        restored.beginDay(10);
        assertEquals(accounts.records(), restored.records());
        restored.beginDay(11);
        assertEquals(7, restored.records().get(0).history().size());
    }

    @Test
    void saveNetworkRoundTripAndLegacyCityTwoMigrationPreserveTheCity() throws Exception {
        var g = new CityTest.Ground();
        var s = settled(g);
        var f = s.frame();
        Path save = directory.resolve("city");
        s.save(save);
        assertEquals(f, CitySimulation.load(save));
        var bytes = new ByteArrayOutputStream();
        f.write(new DataOutputStream(bytes));
        assertEquals(
                f,
                CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        try (var out = new DataOutputStream(Files.newOutputStream(save))) {
            out.writeInt(0x43495432);
            f.write(out, 2);
        }
        var old = CitySimulation.load(save);
        assertTrue(old.economy().businesses().isEmpty());
        assertEquals(f.buildings(), old.buildings());
        assertEquals(f.economy().firms(), old.economy().firms());
        var migrated = new CitySimulation(old.config(), g, g.terrain, old);
        assertEquals(old.economy(), CityCapitalTest.withoutCapital(migrated.frame().economy()));
        assertTrue(
                migrated.economy.capital.state().book().listings().stream()
                        .allMatch(l -> !l.publicCompany()));
        migrated.advance(1);
        assertEquals(14, BusinessMetrics.from(migrated.frame()).locations().size());
        assertEquals(18, migrated.frame().citizens().size());
        migrated.save(save);
        assertEquals(migrated.frame(), CitySimulation.load(save));
    }

    @Test
    void productionFollowsPaidGameHoursAndRetainsFractionalWorkAfterReload() {
        var accounts = new CityBusinesses(List.of());
        accounts.open(1, 2);
        assertEquals(0, accounts.produce(1, .1, 100));
        accounts = new CityBusinesses(accounts.records());
        assertEquals(1, accounts.produce(1, .15, 100));
        assertEquals(4, accounts.produce(1, 1, 100));
        assertEquals(0, accounts.produce(1, 10, 0));
        assertEquals(5, accounts.records().get(0).total().produced());
    }

    @Test
    void malformedBusinessCountersAreRejected() throws Exception {
        var record =
                new CityBusinesses.Record(
                        1,
                        2,
                        1,
                        Double.NaN,
                        CityBusinesses.Totals.empty(),
                        new CityBusinesses.Day(1, CityBusinesses.Totals.empty()),
                        List.of());
        var bytes = new ByteArrayOutputStream();
        CityBusinesses.write(new DataOutputStream(bytes), List.of(record));
        assertThrows(
                IOException.class,
                () ->
                        CityBusinesses.read(
                                new DataInputStream(
                                        new ByteArrayInputStream(bytes.toByteArray()))));
    }

    @Test
    void emptyCityHasNoOperatingMetricsAndNoInventedProfit() {
        var m = BusinessMetrics.from(CityFrame.empty(GameConfig.cityGame()));
        assertTrue(m.locations().isEmpty());
        assertTrue(m.companies().isEmpty());
        assertEquals(0, m.revenue());
        assertEquals(0, m.expenses());
    }
}
