import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;

import java.nio.file.*;
import java.util.*;

/**
 * Real-world, three-day evidence: manufacturing, owned tools, natural extraction and private
 * finance.
 */
public final class ManufacturingSmoke {
    static String q(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    static long embodied(CityFrame f, int material) {
        long total =
                f.economy().resources().stocks().stream()
                        .filter(s -> s.material() == material)
                        .mapToLong(CityMaterials.Stock::units)
                        .sum();
        for (var p : f.economy().resources().projects())
            if (p.reserved() || p.consumed())
                total +=
                        p.materials().stream()
                                .filter(a -> a.material() == material)
                                .mapToLong(CityMaterials.Amount::units)
                                .sum();
        return total;
    }

    static double cash(CityFrame f) {
        return f.economy().budget()
                + f.economy().roadSpending()
                + f.economy().firms().stream().mapToDouble(CityEconomy.Firm::cash).sum()
                + f.citizens().stream().mapToDouble(CityFrame.Citizen::money).sum();
    }

    public static void main(String[] args) throws Exception {
        Path report = Path.of(args[0]), fixture = Path.of(args[1]);
        if (Files.exists(fixture))
            throw new IllegalArgumentException("Use a fresh world for evidence");
        var local = new LocalGame(fixture, Terrain.DEFAULT_SEED);
        var terrain = new Terrain(local.seed);
        var world = new WorldVoxels(terrain);
        var removed = new HashMap<Integer, Long>();
        var ground =
                new CitySimulation.Ground() {
                    public int type(int x, int y, int z) {
                        return world.type(x, y, z);
                    }

                    public boolean occupied(int x, int y, int z, int w, int d) {
                        return false;
                    }

                    public void apply(List<Protocol.Edit> edits) {
                        if (edits.size() == 1 && edits.get(0).type() == 0) {
                            var e = edits.get(0);
                            removed.merge(type(e.x(), e.y(), e.z()), 1L, Long::sum);
                        }
                        for (var e : edits) {
                            world.apply(e);
                            WorldVoxels.remember(local.edits, e);
                        }
                    }
                };
        local.startGame(new GameConfig(true, true, 1200, 6), ground);
        double initial = cash(local.city.frame());
        var pose = new Protocol.Pose(1, 8, 40, 24, 0, 0);
        local.city.command(
                new CityCommand(
                        CityCommand.ROAD,
                        0,
                        List.of(new Polygon.Point(-10, 24), new Polygon.Point(-60, 24))),
                1,
                pose);
        local.city.command(
                new CityCommand(
                        CityCommand.ZONE,
                        2,
                        new Polygon(
                                        List.of(
                                                new Polygon.Point(-50, 26),
                                                new Polygon.Point(-10, 26),
                                                new Polygon.Point(-10, 50),
                                                new Polygon.Point(-50, 50)))
                                .vertices()),
                1,
                pose);
        var samples = new ArrayList<String>();
        var meals = new HashSet<Integer>();
        var sleeping = new HashSet<Integer>();
        boolean sawReserved = false, sawWaiting = false;
        int checkedBuildings = 0;
        for (int second = 1; second <= 3600; second++) {
            local.city.advance(1);
            var f = local.city.frame();
            sawReserved |=
                    f.economy().resources().projects().stream()
                            .anyMatch(CityMaterials.Project::reserved);
            sawWaiting |=
                    f.economy().plots().stream()
                            .anyMatch(
                                    p ->
                                            p.building() == 0
                                                    && p.work() == 0
                                                    && f.economy().resources().project(p.id())
                                                            != null
                                                    && !f.economy()
                                                            .resources()
                                                            .project(p.id())
                                                            .reserved());
            for (var b : f.buildings()) {
                var plot =
                        f.economy().plots().stream()
                                .filter(p -> p.building() == b.id())
                                .findFirst()
                                .orElseThrow();
                if (!f.economy().resources().project(plot.id()).consumed())
                    throw new AssertionError("Unfunded physical building");
                checkedBuildings++;
            }
            for (var c : f.citizens()) {
                if (c.activity().equals("Eating at shop")) meals.add(c.id());
                if (c.activity().equals("Sleeping at home")) sleeping.add(c.id());
            }
            if (second % 50 == 0) {
                var firms = new ArrayList<String>();
                for (var c : BusinessMetrics.from(f).companies())
                    firms.add(
                            String.format(
                                    Locale.ROOT,
                                    "{\"name\":%s,\"kind\":%s,\"cash\":%.3f,\"staff\":%d,\"sales\":%.3f,\"costs\":%.3f}",
                                    q(c.firm().name()),
                                    q(CityMaterials.sector(c.firm().kind())),
                                    c.firm().cash(),
                                    c.employees(),
                                    c.revenue(),
                                    c.expenses()));
                samples.add(
                        "{\"clock\":"
                                + q(f.config().time(f.elapsed()).label())
                                + ",\"buildings\":"
                                + f.buildings().size()
                                + ",\"companies\":["
                                + String.join(",", firms)
                                + "]}");
            }
        }
        var f = local.city.frame();
        long tools = embodied(f, CityMaterials.PICKAXE) + embodied(f, CityMaterials.AXE);
        long wood = embodied(f, Blocks.WOOD) + embodied(f, Blocks.PLANKS) / 4 + tools * 2;
        long stone =
                embodied(f, Blocks.STONE)
                        + embodied(f, Blocks.BRICKS)
                        + embodied(f, Blocks.LED)
                        + tools * 3;
        long sand = embodied(f, Blocks.SAND) + embodied(f, Blocks.GLASS) + embodied(f, Blocks.LED);
        if (wood != removed.get(Blocks.WOOD) * CityMaterials.UNIT
                || stone != removed.get(Blocks.STONE) * CityMaterials.UNIT
                || sand != removed.get(Blocks.SAND) * CityMaterials.UNIT)
            throw new AssertionError("Material conservation failed");
        for (var c : BusinessMetrics.from(f).companies()) {
            var firm = c.firm();
            double opening =
                    firm.kind() == 0
                            ? 1200
                            : firm.kind() == 1 ? 1500 : firm.kind() == 2 ? 2000 : 1500;
            double expected =
                    firm.kind() == 0
                            ? opening
                                    - firm.land()
                                    - firm.materials()
                                    - firm.wages()
                                    + firm.receipts()
                            : opening + c.profit() - (firm.kind() == 1 ? 240 : 0);
            if (Math.abs(expected - firm.cash()) > .001)
                throw new AssertionError("Company cash reconciliation: " + firm.name());
        }
        double drift = Math.abs(initial - cash(f));
        if (f.buildings().size() != 11
                || f.economy().firms().size() != 11
                || meals.size() != 12
                || sleeping.size() != 12
                || !sawReserved
                || !sawWaiting
                || drift > .5)
            throw new AssertionError(
                    "Incomplete three-day city lifecycle: buildings="
                            + f.buildings().size()
                            + ", firms="
                            + f.economy().firms().size()
                            + ", fed="
                            + meals.size()
                            + ", sleeping="
                            + sleeping.size()
                            + ", waiting="
                            + sawWaiting
                            + ", reserved="
                            + sawReserved
                            + ", cashDrift="
                            + drift
                            + ", citizens="
                            + f.citizens());
        for (int kind : new int[] {CityEconomy.MINE, CityMaterials.LOGGING}) {
            var firm =
                    local.city.economy.companies().stream()
                            .filter(c -> c.kind == kind)
                            .findFirst()
                            .orElseThrow();
            if (local.city.economy.resources.productivity(firm.id, kind) != 2)
                throw new AssertionError("Tool purchase or bonus missing");
        }
        var factory =
                f.economy().firms().stream()
                        .filter(c -> c.kind() == CityMaterials.TOOLS)
                        .findFirst()
                        .orElseThrow();
        if (f.economy().resources().production().stream()
                .noneMatch(p -> p.company() == factory.id() && p.processed() > 0))
            throw new AssertionError("No factory products");
        if (f.addresses().addresses().size() != f.buildings().size())
            throw new AssertionError("Unnamed buildings");
        for (int i = 0; i < 200; i++) local.city.advance(1);
        local.save();
        var saved = CitySimulation.load(fixture.resolveSibling(fixture.getFileName() + ".city"));
        if (!saved.equals(local.city.frame())) throw new AssertionError("Save round trip failed");
        String result =
                String.format(
                        Locale.ROOT,
                        "{\"scope\":\"Fresh real voxel world; three full 1200-second city days;"
                            + " hourly private company"
                            + " accounts\",\"days\":3,\"hourlySamples\":72,\"buildingChecks\":%d,\"buildings\":11,\"companies\":11,\"factoryToolsProduced\":%d,\"mineAndLoggingToolBonus\":2,\"namedRoadsAndBuildings\":true,\"citizensFedAndSleeping\":12,\"waitingAndReservedProjectsObserved\":true,\"materialConservation\":true,\"naturalWoodHarvested\":%d,\"naturalStoneHarvested\":%d,\"naturalSandHarvested\":%d,\"companyCashReconciles\":true,\"cashRoundingDifference\":%.6f,\"saveRoundTrip\":true,\"samples\":[%s]}\n",
                        checkedBuildings,
                        tools / CityMaterials.UNIT,
                        removed.get(Blocks.WOOD),
                        removed.get(Blocks.STONE),
                        removed.get(Blocks.SAND),
                        drift,
                        String.join(",", samples));
        Files.writeString(report, result);
        System.out.println(
                "Three city days passed: real harvested supplies conserved, company cash"
                    + " reconciled, 12 citizens fed and sleeping, 11 material-funded buildings and"
                    + " equipped mine/logging crews, save round trip.");
    }
}
