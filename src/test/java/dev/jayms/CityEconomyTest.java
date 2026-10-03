package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.*;
import dev.jayms.net.city.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.util.*;

class CityEconomyTest {
    @TempDir Path temp;

    CitySimulation sim(CityTest.Ground g) {
        return new CitySimulation(
                new GameConfig(true, false, 1200, 10),
                g,
                g.terrain,
                null,
                ProductionCatalog.toolEra());
    }

    CityCommand road() {
        return new CityCommand(
                CityCommand.ROAD, 0, List.of(new Polygon.Point(44, 24), new Polygon.Point(60, 24)));
    }

    final Protocol.Pose pose = new Protocol.Pose(1, 8, 40, 24, 0, 0);

    double total(CityFrame f) {
        return f.economy().budget()
                + f.economy().roadSpending()
                + f.economy().firms().stream().mapToDouble(c -> c.cash()).sum()
                + f.citizens().stream().mapToDouble(CityFrame.Citizen::money).sum();
    }

    @Test
    void publicRoadsAreBudgetedAtomicAndChargeOnlyNewCells() {
        var g = new CityTest.Ground();
        var s = sim(g);
        var initial = s.frame();
        assertEquals(10000, initial.economy().budget());
        assertTrue(s.command(road(), 1, pose).contains("Mayor paid"));
        var next = s.frame();
        int cells = next.roads().size() - initial.roads().size();
        assertEquals(cells * 4, next.economy().roadSpending());
        assertEquals(10000 - cells * 4, next.economy().budget());
        s.command(road(), 1, pose);
        assertEquals(next.economy(), s.frame().economy());
        s.economy.budget = 0;
        int count = g.edits.size();
        int roads = s.frame().roads().size();
        var further =
                new CityCommand(
                        CityCommand.ROAD,
                        0,
                        List.of(new Polygon.Point(60, 24), new Polygon.Point(70, 24)));
        assertTrue(s.command(further, 1, pose).contains("budget too low"));
        assertEquals(roads, s.frame().roads().size());
        assertEquals(count, g.edits.size());
    }

    @Test
    void zoningIsFreeAndUnfundedDevelopersDoNotBuild() {
        var g = new CityTest.Ground();
        var s = sim(g);
        s.command(road(), 1, pose);
        double budget = s.economy.budget;
        assertTrue(
                s.command(
                                new CityCommand(
                                        CityCommand.ZONE,
                                        0,
                                        CityTest.box(47, 26, 12, 10).vertices()),
                                1,
                                pose)
                        .contains("created"));
        assertEquals(budget, s.economy.budget);
        for (var c : s.economy.companies()) if (c.kind == CityEconomy.DEVELOPER) c.cash = 0;
        for (int i = 0; i < 160; i++) s.advance(1);
        assertTrue(s.frame().buildings().isEmpty());
        assertTrue(s.frame().economy().plots().isEmpty());
        assertEquals(budget, s.economy.budget);
    }

    @Test
    void developersBuyHireConstructSellAndRentWithConservedMoney() {
        var g = new CityTest.Ground();
        var s = sim(g);
        double total = total(s.frame());
        boolean construction = false;
        for (int i = 0; i < 160; i++) {
            s.advance(1);
            construction |=
                    s.frame().citizens().stream()
                            .anyMatch(
                                    c ->
                                            c.job() < 0
                                                    && c.activity()
                                                            .equals("Building for developer"));
        }
        var f = s.frame();
        assertTrue(construction);
        assertEquals(5, f.buildings().size());
        assertEquals(5, f.economy().plots().size());
        assertEquals(0, f.economy().roadSpending());
        assertEquals(
                f.economy().plots().stream().mapToDouble(CityEconomy.Plot::landPrice).sum(),
                f.economy().landRevenue(),
                1e-8);
        assertEquals(10000 + f.economy().landRevenue(), f.economy().budget(), 1e-8);
        assertTrue(
                f.economy().firms().stream()
                        .filter(c -> c.kind() == CityEconomy.DEVELOPER)
                        .allMatch(c -> c.land() > 0 && c.materials() > 0 && c.wages() > 0));
        assertEquals(5, f.economy().properties().size());
        assertTrue(
                f.economy().contracts().stream()
                        .anyMatch(c -> c.sale() && c.partyKind() == CityEconomy.CITIZEN));
        assertTrue(
                f.economy().contracts().stream()
                        .anyMatch(c -> !c.sale() && c.partyKind() == CityEconomy.CITIZEN));
        assertTrue(
                f.economy().contracts().stream()
                        .anyMatch(c -> c.sale() && c.partyKind() == CityEconomy.COMPANY));
        assertTrue(
                f.economy().contracts().stream()
                        .anyMatch(c -> !c.sale() && c.partyKind() == CityEconomy.COMPANY));
        assertTrue(f.citizens().stream().allMatch(c -> c.home() > 0 && c.job() > 0));
        assertEquals(total, total(f), .04);
        int properties = f.economy().properties().size();
        for (int i = 0; i < 300; i++) s.advance(1);
        assertEquals(
                properties,
                s.frame()
                        .economy()
                        .properties()
                        .size()); // avoid speculative construction with no demand
    }

    @Test
    void wagesAndRecurringRentAreTransfersAndInsolvencyStopsWages() {
        var g = new CityTest.Ground();
        var s = sim(g);
        for (int i = 0; i < 160; i++) s.advance(1);
        var before = s.frame();
        double total = total(before);
        double budget = s.economy.budget;
        s.economy.rent(1);
        assertEquals(total, total(s.frame()), .001);
        assertEquals(budget, s.economy.budget);
        var worker = before.citizens().get(0);
        var needs = s.ecs.get(worker.id(), CitySimulation.Needs.class);
        var company = s.economy.companies().get(0);
        company.cash = 1;
        float money = needs.money;
        assertFalse(s.economy.wage(company.id, 2, needs));
        assertEquals(money, needs.money);
        assertEquals(1, company.cash);
        assertTrue(s.economy.wage(company.id, .5, needs));
        assertEquals(money + .5, needs.money, .0001);
        assertEquals(.5, company.cash);
    }

    @Test
    void midConstructionOwnershipContractsAndBalancesPersist() throws Exception {
        var g = new CityTest.Ground();
        var s = sim(g);
        s.advance(1);
        assertFalse(s.frame().economy().plots().isEmpty());
        assertTrue(s.frame().buildings().isEmpty());
        Path path = temp.resolve("city");
        s.save(path);
        var f = CitySimulation.load(path);
        assertEquals(s.frame(), f);
        var restored = new CitySimulation(f.config(), g, g.terrain, f);
        assertEquals(f.economy(), restored.frame().economy());
        for (int i = 0; i < 160; i++) restored.advance(1);
        assertEquals(5, restored.frame().buildings().size());
        restored.save(path);
        var saved = CitySimulation.load(path);
        var again = new CitySimulation(saved.config(), g, g.terrain, saved);
        assertEquals(saved.economy(), again.frame().economy());
        assertEquals(saved.buildings(), again.frame().buildings());
    }

    @Test
    void legacyCityIsAdoptedWithoutRebuildingOrPublicConstructionCost() throws Exception {
        var g = new CityTest.Ground();
        var s = sim(g);
        for (int i = 0; i < 160; i++) s.advance(1);
        var frame = s.frame();
        var all = new ByteArrayOutputStream();
        frame.write(new DataOutputStream(all));
        var economy = new ByteArrayOutputStream();
        frame.economy().write(new DataOutputStream(economy));
        Path path = temp.resolve("legacy");
        try (var out = new DataOutputStream(Files.newOutputStream(path))) {
            out.writeInt(0x43495431);
            out.write(all.toByteArray(), 0, all.size() - economy.size());
        }
        var legacy = CitySimulation.load(path);
        int edits = g.edits.size();
        var adopted = new CitySimulation(legacy.config(), g, g.terrain, legacy);
        assertEquals(frame.buildings(), adopted.frame().buildings());
        assertEquals(edits, g.edits.size());
        assertEquals(10000, adopted.economy.budget);
        assertEquals(0, adopted.economy.roadSpending);
        assertEquals(5, adopted.economy.properties.size());
        adopted.advance(1);
        assertTrue(
                adopted.frame().economy().contracts().stream()
                        .anyMatch(c -> c.partyKind() == CityEconomy.CITIZEN));
        adopted.save(path);
        assertEquals(adopted.frame(), CitySimulation.load(path));
    }

    @Test
    void malformedFinancialSnapshotIsRejected() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeDouble(Double.NaN);
        }
        assertThrows(
                IOException.class,
                () ->
                        CityEconomy.State.read(
                                new DataInputStream(
                                        new ByteArrayInputStream(bytes.toByteArray()))));
    }
}
