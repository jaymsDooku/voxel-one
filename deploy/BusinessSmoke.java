import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;

import java.nio.file.*;
import java.util.*;

/** A real voxel-world fixture and operating account evidence across three full city days. */
public final class BusinessSmoke {
    static String q(String v) {
        return "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public static void main(String[] args) throws Exception {
        Path report = Path.of(args[0]), fixture = Path.of(args[1]);
        var local = new LocalGame(fixture, Terrain.DEFAULT_SEED);
        var terrain = new Terrain(local.seed);
        var voxels = new WorldVoxels(terrain);
        var ground =
                new CitySimulation.Ground() {
                    public int type(int x, int y, int z) {
                        return voxels.type(x, y, z);
                    }

                    public boolean occupied(int x, int y, int z, int w, int d) {
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
        var hours = new ArrayList<String>();
        for (int second = 1; second <= 3600; second++) {
            local.city.advance(1);
            if (second % 50 == 0) {
                var f = local.city.frame();
                var entries = new ArrayList<String>();
                for (var l : BusinessMetrics.from(f).locations())
                    entries.add(
                            String.format(
                                    Locale.ROOT,
                                    "{\"building\":%d,\"name\":%s,\"status\":%s,\"staff\":%d,\"onSite\":%d,\"cash\":%.3f,\"revenue\":%.3f,\"expenses\":%.3f,\"profit\":%.3f,\"stock\":%d,\"produced\":%d,\"sold\":%d,\"received\":%d}",
                                    l.building().id(),
                                    q(l.name()),
                                    q(l.status()),
                                    l.employees().size(),
                                    l.onSite(),
                                    l.firm().cash(),
                                    l.total().revenue(),
                                    l.total().expenses(),
                                    l.total().profit(),
                                    l.building().stock(),
                                    l.total().produced(),
                                    l.total().sold(),
                                    l.total().received()));
                hours.add(
                        "{\"clock\":"
                                + q(f.config().time(f.elapsed()).label())
                                + ",\"businesses\":["
                                + String.join(",", entries)
                                + "]}");
            }
        }
        var f = local.city.frame();
        var m = BusinessMetrics.from(f);
        if (m.locations().size() != 2
                || m.locations().stream()
                        .anyMatch(
                                l ->
                                        l.account().history().size() != 3
                                                || l.total().sold() == 0
                                                || l.total().wages() == 0))
            throw new AssertionError("Business did not operate across three days");
        for (var l : m.locations()) {
            long stock =
                    l.building().type() == 1
                            ? 80 + l.total().received() - l.total().sold()
                            : l.total().produced() - l.total().sold();
            if (stock != l.building().stock())
                throw new AssertionError("Stock reconciliation failed");
            double opening = l.building().type() == 1 ? 1260 : 2000;
            if (Math.abs(opening + l.total().profit() - l.firm().cash()) > .001)
                throw new AssertionError("Cash reconciliation failed");
        }
        // Continue to the workday for a desktop fixture with accumulated daily history.
        for (int i = 0; i < 200; i++) local.city.advance(1);
        local.save();
        Files.writeString(
                report,
                "{\"scope\":\"Fresh real voxel world, three full configured 1200-second city days;"
                    + " hourly business"
                    + " accounts\",\"days\":3,\"hourlySamples\":72,\"cashAndStockReconcile\":true,\"samples\":["
                        + String.join(",", hours)
                        + "]}\n");
        System.out.println(
                "Three days passed; 72 hourly samples; company cash and inventory reconcile; saved"
                    + " playable fixture.");
    }
}
