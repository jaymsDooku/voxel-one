import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;

import java.nio.file.*;
import java.util.*;

/** Real-world, three-day evidence: owned materials, natural extraction and private finance. */
public final class MaterialsSmoke {
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
        long wood = embodied(f, Blocks.WOOD) + embodied(f, Blocks.PLANKS) / 4;
        long stone =
                embodied(f, Blocks.STONE) + embodied(f, Blocks.BRICKS) + embodied(f, Blocks.LED);
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
        if (f.buildings().size() != 5
                || f.economy().firms().size() != 10
                || meals.size() != 12
                || sleeping.size() != 12
                || !sawReserved
                || !sawWaiting
                || drift > .5) throw new AssertionError("Incomplete three-day city lifecycle");
        for (int i = 0; i < 200; i++) local.city.advance(1);
        local.save();
        var saved = CitySimulation.load(fixture.resolveSibling(fixture.getFileName() + ".city"));
        if (!saved.equals(local.city.frame())) throw new AssertionError("Save round trip failed");
        String result =
                String.format(
                        Locale.ROOT,
                        "{\"scope\":\"Fresh real voxel world; three full 1200-second city days;"
                            + " hourly private company"
                            + " accounts\",\"days\":3,\"hourlySamples\":72,\"buildingChecks\":%d,\"buildings\":5,\"companies\":10,\"citizensFedAndSleeping\":12,\"waitingAndReservedProjectsObserved\":true,\"materialConservation\":true,\"naturalWoodHarvested\":%d,\"naturalStoneHarvested\":%d,\"naturalSandHarvested\":%d,\"companyCashReconciles\":true,\"cashRoundingDifference\":%.6f,\"saveRoundTrip\":true,\"samples\":[%s]}\n",
                        checkedBuildings,
                        removed.get(Blocks.WOOD),
                        removed.get(Blocks.STONE),
                        removed.get(Blocks.SAND),
                        drift,
                        String.join(",", samples));
        Files.writeString(report, result);
        System.out.println(
                "Three city days passed: real harvested supplies conserved, company cash"
                    + " reconciled, 12 citizens fed and sleeping, 5 material-funded buildings, save"
                    + " round trip.");
    }
}
