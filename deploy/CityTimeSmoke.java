import dev.jayms.net.*;
import dev.jayms.net.city.*;

import java.nio.file.*;
import java.util.*;

/** Reproduce three full city days on actual terrain; save phase fixtures for client checks. */
public final class CityTimeSmoke {
    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public static void main(String[] args) throws Exception {
        Path report = Path.of(args[0]), fixtures = Path.of(args[1]);
        Files.createDirectories(fixtures);
        var local =
                new LocalGame(
                        Files.createTempDirectory("voxel-city-days-").resolve("world.dat"),
                        Terrain.DEFAULT_SEED);
        var terrain = new Terrain(local.seed);
        var voxels = new WorldVoxels(terrain);
        var ground =
                new CitySimulation.Ground() {
                    public int type(int x, int y, int z) {
                        return voxels.type(x, y, z);
                    }

                    public boolean occupied(int x, int y, int z, int width, int depth) {
                        return false;
                    }

                    public void apply(List<Protocol.Edit> edits) {
                        for (var e : edits) {
                            voxels.apply(e);
                            WorldVoxels.remember(local.edits, e);
                        }
                    }
                };
        local.startGame(new GameConfig(true, true, 1200, 6), ground);
        List<String> samples = new ArrayList<>(), cycles = new ArrayList<>();
        Map<Integer, CityFrame.Citizen> previous = new HashMap<>();
        for (int cycle = 1; cycle <= 3; cycle++) {
            Set<Integer> worked = new HashSet<>(),
                    ate = new HashSet<>(),
                    slept = new HashSet<>(),
                    walked = new HashSet<>();
            int meals = 0;
            double wagesBefore =
                    local.city.frame().economy().firms().stream()
                            .mapToDouble(CityEconomy.Firm::wages)
                            .sum();
            for (int second = 1; second <= 1200; second++) {
                local.city.advance(1);
                var f = local.city.frame();
                for (var c : f.citizens()) {
                    if (c.activity().startsWith("Working")
                            || c.activity().equals("Building for developer")) worked.add(c.id());
                    if (c.activity().equals("Sleeping at home")) slept.add(c.id());
                    if (c.activity().equals("Eating at shop")) ate.add(c.id());
                    var old = previous.put(c.id(), c);
                    if (old != null) {
                        if (c.hunger() > old.hunger() + 10) meals++;
                        if (c.phase() > old.phase()) walked.add(c.id());
                    }
                }
                if (second % 50 == 0) {
                    var counts = new TreeMap<String, Integer>();
                    for (var c : f.citizens()) counts.merge(c.activity(), 1, Integer::sum);
                    var entries = new ArrayList<String>();
                    counts.forEach((key, value) -> entries.add(quote(key) + ":" + value));
                    var m = CityMetrics.from(f);
                    samples.add(
                            String.format(
                                    Locale.ROOT,
                                    "{\"clock\":%s,\"elapsed\":%.3f,\"activities\":{%s},\"housed\":%d,\"employed\":%d,\"hungry\":%d,\"averageHunger\":%.2f,\"averageSavings\":%.2f}",
                                    quote(f.config().time(f.elapsed()).label()),
                                    f.elapsed(),
                                    String.join(",", entries),
                                    m.housed(),
                                    m.employed(),
                                    m.hungry(),
                                    m.averageHunger(),
                                    m.averageSavings()));
                }
                int total = (cycle - 1) * 1200 + second;
                String phase =
                        switch (total) {
                            case 300 -> "workday";
                            case 700 -> "evening";
                            case 950 -> "night";
                            case 1250 -> "morning";
                            default -> null;
                        };
                if (phase != null) {
                    Path file = fixtures.resolve(phase + ".dat");
                    if (Files.exists(file))
                        throw new IllegalArgumentException("Fixture already exists: " + phase);
                    var saved = new LocalGame(file, local.seed);
                    saved.edits.putAll(local.edits);
                    saved.city = local.city;
                    saved.save();
                }
            }
            double wages =
                    local.city.frame().economy().firms().stream()
                                    .mapToDouble(CityEconomy.Firm::wages)
                                    .sum()
                            - wagesBefore;
            if (worked.size() != 12
                    || ate.size() != 12
                    || slept.size() != 12
                    || walked.size() != 12
                    || wages <= 0)
                throw new AssertionError(
                        "Incomplete daily life in cycle "
                                + cycle
                                + ": work="
                                + worked.size()
                                + ", meals="
                                + ate.size()
                                + ", sleep="
                                + slept.size());
            cycles.add(
                    String.format(
                            Locale.ROOT,
                            "{\"cycle\":%d,\"workers\":%d,\"ate\":%d,\"slept\":%d,\"walked\":%d,\"mealsPurchased\":%d,\"wagesPaid\":%.2f}",
                            cycle,
                            worked.size(),
                            ate.size(),
                            slept.size(),
                            walked.size(),
                            meals,
                            wages));
        }
        if (local.city.frame().citizens().stream().anyMatch(c -> c.hunger() <= 35))
            throw new AssertionError("Unfed population");
        Files.writeString(
                report,
                "{\"scope\":\"Three complete default-length days, actual procedural terrain and"
                    + " collision; no wall-clock"
                    + " wait\",\"daySeconds\":1200,\"fixedStepSeconds\":0.1,\"simulatedSeconds\":3600,\"status\":\"passed\",\"cycles\":["
                        + String.join(",", cycles)
                        + "],\"hourlySamples\":["
                        + String.join(",", samples)
                        + "]}\n");
        System.out.println(
                "Passed three full days: all 12 citizens worked, ate, walked and slept in every"
                    + " cycle; 72 hourly samples recorded.");
    }
}
