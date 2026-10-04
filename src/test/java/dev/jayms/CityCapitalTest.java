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

class CityCapitalTest {
    @TempDir Path temp;
    final Protocol.Pose pose = new Protocol.Pose(1, 8, 40, 24, 0, 0);

    CitySimulation sim(CityTest.Ground ground) {
        return new CitySimulation(
                new GameConfig(true, false, 1200, 10),
                ground,
                ground.terrain,
                null,
                ProductionCatalog.toolEra());
    }

    CityCommand exchange() {
        return new CityCommand(CityCommand.EXCHANGE, 0, List.of(new Polygon.Point(30, 27)));
    }

    CityCommand capital(
            int action,
            int company,
            CityStockExchange.Owner owner,
            long shares,
            long price,
            long order) {
        return new CityCommand(
                new CityCommand.Capital(
                        action, company, owner.kind(), owner.id(), shares, price, order));
    }

    void staff(CitySimulation s) {
        var b = s.frame().buildings().stream().filter(v -> v.type() == SpecialBuildings.EXCHANGE).findFirst().orElseThrow();
        for (int id :
                s.ecs.query(
                        CitySimulation.Household.class,
                        CitySimulation.Position.class,
                        CitySimulation.Travel.class)) {
            var h = s.ecs.get(id, CitySimulation.Household.class);
            if (h.job == b.id()) {
                var p = s.ecs.get(id, CitySimulation.Position.class);
                p.x = b.x() + 2;
                p.z = b.z() + 2;
                p.y = b.y() + 1;
                var t = s.ecs.get(id, CitySimulation.Travel.class);
                t.activity =
                        s.economy.capital.graduates.contains(id)
                                ? "Exchange analyst (graduate)"
                                : "Exchange office support";
                t.route.clear();
                t.target = b.id();
                t.retryAt = 0;
            }
        }
        s.refreshExchange();
    }

    double total(CitySimulation s) {
        var f = s.frame();
        return f.economy().firms().stream().mapToDouble(CityEconomy.Firm::cash).sum()
                + f.economy().capital().investors().stream()
                                .mapToLong(CityCapital.Investor::cents)
                                .sum()
                        / 100.0
                + f.economy().capital().book().orders().stream()
                        .filter(CityStockExchange.Order::buy)
                        .mapToDouble(o -> o.shares() * o.price() / 100.0)
                        .sum();
    }

    static CityEconomy.State withoutCapital(CityEconomy.State s) {
        return new CityEconomy.State(
                s.budget(),
                s.roadSpending(),
                s.landRevenue(),
                s.rentClock(),
                s.firms(),
                s.plots(),
                s.properties(),
                s.contracts(),
                s.businesses(),
                s.resources());
    }

    @Test
    void allCompaniesHavePrivateFoundersIncludingEmptyAndLegacyWorlds() throws Exception {
        var s = sim(new CityTest.Ground());
        var state = s.economy.capital.state();
        assertEquals(s.economy.companies().size(), state.book().listings().size());
        for (var l : state.book().listings()) {
            assertFalse(l.publicCompany());
            assertEquals(1, l.founder().kind());
            assertEquals(1000, s.economy.capital.exchange.shares(l.company(), l.founder()));
            assertTrue(state.investors().stream().anyMatch(p -> p.id() == l.founder().id()));
        }
        var bytes = new ByteArrayOutputStream();
        s.frame().write(new DataOutputStream(bytes), 6);
        var legacy =
                CityFrame.read(
                        new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), 6);
        var g = new CityTest.Ground();
        var migrated = new CitySimulation(s.config(), g, g.terrain, legacy);
        assertEquals(state.book().listings(), migrated.economy.capital.state().book().listings());
        var empty = new CityEconomy(new Ecs(), null);
        assertTrue(
                empty.capital.state().book().listings().stream()
                        .allMatch(l -> l.founder().kind() == 1));
    }

    @Test
    void placementIsBudgetedAtomicAndOfficeHasGraduateMajority() {
        var g = new CityTest.Ground();
        var s = sim(g);
        double budget = s.economy.budget;
        g.occupied = true;
        int edits = g.edits.size();
        assertTrue(s.command(exchange(), 1, pose).contains("occupied"));
        assertEquals(budget, s.economy.budget);
        assertEquals(edits, g.edits.size());
        g.occupied = false;
        assertTrue(s.command(exchange(), 1, pose).contains("built"));
        assertEquals(budget - 600, s.economy.budget);
        var b = s.frame().buildings().get(0);
        assertEquals(SpecialBuildings.EXCHANGE, b.type());
        var assigned = s.frame().citizens().stream().filter(c -> c.job() == b.id()).toList();
        assertEquals(4, assigned.size());
        assertEquals(
                3,
                assigned.stream()
                        .filter(c -> s.economy.capital.graduates.contains(c.id()))
                        .count());
        var inspector = new BuildingInfo();
        inspector.building = b.id();
        assertTrue(
                inspector.lines(s.frame()).stream()
                        .anyMatch(v -> v.contains("Graduate staff 3 / 4")));
        assertFalse(s.economy.capital.exchange.operational());
        staff(s);
        assertTrue(s.economy.capital.exchange.operational());
        assertTrue(CapitalDashboard.available(s.frame()));
        assertEquals(-1, CityMetrics.employer(s.frame(), assigned.get(0)));
        double after = s.economy.budget;
        s.command(exchange(), 1, pose);
        assertEquals(after, s.economy.budget);
        s.economy.capital.graduates.clear();
        s.refreshExchange();
        assertFalse(s.economy.capital.exchange.operational());
    }

    @Test
    void authorizesOfferingsSettlesCompanyAndPersonOrdersAndCancelsAfterClosure() {
        var s = sim(new CityTest.Ground());
        var e = s.economy.capital.exchange;
        var firm = s.economy.companies().get(0);
        var owner = e.listing(firm.id).founder();
        var other = e.listing(s.economy.companies().get(1).id).founder();
        assertTrue(
                s.command(capital(0, firm.id, owner, 100, 500, 0), 1, pose)
                        .contains("operating exchange"));
        s.command(exchange(), 1, pose);
        staff(s);
        assertTrue(s.command(capital(0, firm.id, other, 100, 500, 0), 1, pose).contains("owner"));
        assertTrue(
                s.command(capital(0, firm.id, owner, 100, 500, 0), 1, pose)
                        .contains("Public offering"));
        double cash = firm.cash, total = total(s);
        assertEquals(
                "Share order accepted", s.command(capital(1, firm.id, other, 10, 600, 0), 1, pose));
        assertEquals(cash + 50, firm.cash, 1e-8);
        assertEquals(10, e.shares(firm.id, other));
        var corporate = new CityStockExchange.Owner(0, s.economy.companies().get(2).id);
        s.command(capital(1, firm.id, corporate, 90, 500, 0), 1, pose);
        long sale = e.submit(firm.id, other, false, 5, 700);
        s.command(capital(1, firm.id, corporate, 5, 800, 0), 1, pose);
        assertEquals(700, e.listing(firm.id).lastPrice());
        assertEquals(cash + 500, firm.cash, 1e-8);
        assertEquals(total, total(s), 1e-8);
        assertFalse(e.cancel(sale, other));
        long bid = e.submit(firm.id, other, true, 5, 100);
        s.economy.budget = 0;
        s.refreshExchange();
        assertTrue(
                s.command(capital(3, firm.id, corporate, 0, 0, bid), 1, pose)
                        .contains("not owned"));
        assertTrue(s.command(capital(3, firm.id, other, 0, 0, bid), 1, pose).contains("cancelled"));
        assertEquals(total, total(s), 1e-8);
    }

    @Test
    void saveAndNetworkRoundTripRetainEscrowAndShareholdings() throws Exception {
        var g = new CityTest.Ground();
        var s = sim(g);
        s.command(exchange(), 1, pose);
        staff(s);
        var e = s.economy.capital.exchange;
        int company = s.economy.companies().get(0).id;
        var owner = e.listing(company).founder();
        var buyer = e.listing(s.economy.companies().get(1).id).founder();
        s.command(capital(0, company, owner, 100, 500, 0), 1, pose);
        s.command(capital(1, company, buyer, 120, 500, 0), 1, pose);
        var expected = s.frame();
        var file = temp.resolve("stock.city");
        s.save(file);
        assertEquals(expected, CitySimulation.load(file));
        var bytes = new ByteArrayOutputStream();
        expected.write(new DataOutputStream(bytes));
        assertEquals(
                expected,
                CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        var restored = new CitySimulation(s.config(), g, g.terrain, CitySimulation.load(file));
        assertEquals(expected.economy().capital(), restored.economy.capital.state());
        assertEquals(total(s), total(restored), 1e-8);
        assertFalse(restored.economy.capital.exchange.operational());
        staff(restored);
        restored.command(capital(2, company, owner, 10, 450, 0), 1, pose);
        assertEquals(110, restored.economy.capital.exchange.shares(company, buyer));
        assertEquals(total(s), total(restored), 1e-8);
    }

    @Test
    void exactCommandPayloadRoundTripsAndMalformedCommandsAreRejected() throws Exception {
        var c = capital(1, 1, new CityStockExchange.Owner(1, 1), 1000000, 99999999, 0);
        var bytes = new ByteArrayOutputStream();
        c.write(new DataOutputStream(bytes));
        assertEquals(
                c,
                CityCommand.read(
                        new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        assertThrows(
                IllegalArgumentException.class,
                () -> capital(1, 1, new CityStockExchange.Owner(1, 1), -1, 100, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CityCommand(CityCommand.CAPITAL, 0, List.of()));
    }

    @Test
    void staffCommuteToBuiltOfficesThroughNormalSimulation() {
        var s = sim(new CityTest.Ground());
        s.command(exchange(), 1, pose);
        for (int i = 0; i < 120; i++) s.advance(1);
        assertTrue(
                s.economy.capital.exchange.operational(),
                "Exchange staff must reach offices without fixture teleportation");
        assertTrue(CapitalDashboard.available(s.frame()));
        assertEquals(
                3,
                s.frame().citizens().stream()
                        .filter(c -> c.job() == 1 && s.economy.capital.graduates.contains(c.id()))
                        .count());
    }

    @Test
    void corruptCapitalReferencesAndNegativeInvestorBalancesAreRejectedOnNetworkRead()
            throws Exception {
        var s = sim(new CityTest.Ground());
        var f = s.frame();
        var original = f.economy().capital();
        var badStates =
                List.of(
                        new CityCapital.State(original.investors(), List.of(9999), original.book()),
                        new CityCapital.State(
                                List.of(new CityCapital.Investor(1, "Bad", -1)),
                                original.graduates(),
                                original.book()),
                        new CityCapital.State(
                                original.investors(), List.of(9, 9), original.book()));
        for (var bad : badStates) {
            var e = f.economy();
            var state =
                    new CityEconomy.State(
                            e.budget(),
                            e.roadSpending(),
                            e.landRevenue(),
                            e.rentClock(),
                            e.firms(),
                            e.plots(),
                            e.properties(),
                            e.contracts(),
                            e.businesses(),
                            e.resources(),
                            bad);
            var malformed =
                    new CityFrame(
                            f.config(),
                            f.elapsed(),
                            f.roads(),
                            f.zones(),
                            f.buildings(),
                            f.citizens(),
                            f.horses(),
                            state,
                            f.addresses(),
                            f.agriculture());
            var bytes = new ByteArrayOutputStream();
            malformed.write(new DataOutputStream(bytes));
            assertThrows(
                    IOException.class,
                    () ->
                            CityFrame.read(
                                    new DataInputStream(
                                            new ByteArrayInputStream(bytes.toByteArray()))));
        }
    }

    @Test
    void unpaidOfficesCloseEvenWhenTreasuryHasPositiveDustBalance() {
        var s = sim(new CityTest.Ground());
        s.command(exchange(), 1, pose);
        staff(s);
        for (var firm : s.economy.companies())
            if (firm.kind == CityEconomy.DEVELOPER) firm.cash = 0;
        s.economy.budget = 0.00001;
        s.advance(.2);
        assertTrue(s.economy.budget > 0);
        assertFalse(s.economy.capital.exchange.operational());
        assertFalse(CapitalDashboard.available(s.frame()));
    }

    @Test
    void exchangeDemolitionClearsOfficeReferencesClosesTradingAndPreservesInvestments()
            throws Exception {
        var ground = new CityTest.Ground();
        var s = sim(ground);
        s.command(exchange(), 1, pose);
        staff(s);
        var office =
                s.frame().buildings().stream().filter(b -> b.type() == SpecialBuildings.EXCHANGE).findFirst().orElseThrow();
        var book = s.economy.capital.exchange;
        int company = s.economy.companies().get(0).id;
        var founder = book.listing(company).founder();
        var buyer = book.listing(s.economy.companies().get(1).id).founder();
        book.goPublic(company, founder, 100, 500);
        long bid = book.submit(company, buyer, true, 120, 500);
        var investors = s.economy.capital.state();
        double money = total(s), budget = s.economy.budget;
        var command = new CityCommand(CityCommand.DEMOLISH, office.id(), List.of());
        ground.occupied = true;
        var protectedFrame = s.frame();
        assertTrue(s.command(command, 1, pose).contains("players"));
        assertEquals(protectedFrame, s.frame());
        assertTrue(book.operational());
        ground.occupied = false;
        assertTrue(s.command(command, 1, pose).startsWith("Building demolished"));
        assertFalse(book.operational());
        assertFalse(CapitalDashboard.available(s.frame()));
        assertEquals(investors, s.economy.capital.state());
        assertEquals(money, total(s), 1e-8);
        assertEquals(budget, s.economy.budget);
        assertEquals(0, ground.type(office.x() + 1, office.y() + 1, office.z() + 3));
        assertEquals(0, ground.type(office.x() + 1, office.y() + 3, office.z()));
        assertTrue(s.frame().buildings().stream().noneMatch(b -> b.id() == office.id()));
        assertTrue(
                s.frame().addresses().addresses().stream()
                        .noneMatch(a -> a.building() == office.id()));
        assertTrue(
                s.frame().citizens().stream()
                        .noneMatch(c -> c.job() == office.id() || c.home() == office.id()));
        for (int id : s.ecs.query(CitySimulation.Household.class, CitySimulation.Travel.class))
            assertTrue(s.ecs.get(id, CitySimulation.Travel.class).route.isEmpty());
        assertTrue(
                s.command(capital(1, company, buyer, 1, 500, 0), 1, pose)
                        .contains("operating exchange"));
        assertTrue(s.command(capital(3, company, buyer, 0, 0, bid), 1, pose).contains("cancelled"));
        assertEquals(money, total(s), 1e-8);
        var bytes = new ByteArrayOutputStream();
        s.frame().write(new DataOutputStream(bytes));
        assertEquals(
                s.frame(),
                CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        assertTrue(s.command(exchange(), 1, pose).contains("built"));
        staff(s);
        assertTrue(book.operational());
        assertEquals(100, book.shares(company, buyer));
    }

    @Test
    void demolitionAndExchangeCommandsHaveDistinctWireIds() throws Exception {
        assertEquals(4, CityCommand.DEMOLISH);
        assertEquals(6, CityCommand.EXCHANGE);
        assertEquals(7, CityCommand.CAPITAL);
        var commands =
                List.of(
                        new CityCommand(CityCommand.DEMOLISH, 7, List.of()),
                        exchange(),
                        capital(1, 1, new CityStockExchange.Owner(1, 1), 10, 500, 0));
        for (var command : commands) {
            var bytes = new ByteArrayOutputStream();
            command.write(new DataOutputStream(bytes));
            assertEquals(command.kind(), bytes.toByteArray()[0]);
            assertEquals(
                    command,
                    CityCommand.read(
                            new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        }
    }

    @Test
    void officeWagesComeFromTreasuryAndGraduateQualificationsPersistIndependently() {
        var s = sim(new CityTest.Ground());
        s.command(exchange(), 1, pose);
        staff(s);
        var grads = new ArrayList<>(s.economy.capital.graduates);
        s.ecs.get(grads.get(0), CitySimulation.Household.class).cohort = 0;
        assertTrue(s.economy.capital.graduates.contains(grads.get(0)));
        for (var firm : s.economy.companies())
            if (firm.kind == CityEconomy.DEVELOPER) firm.cash = 0;
        double budget = s.economy.budget;
        float money = s.ecs.get(grads.get(0), CitySimulation.Needs.class).money;
        s.advance(.2);
        assertTrue(s.economy.budget < budget);
        assertTrue(s.ecs.get(grads.get(0), CitySimulation.Needs.class).money > money);
    }
}
