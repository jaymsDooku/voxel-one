import dev.jayms.Chunk;
import dev.jayms.MeshData;
import dev.jayms.MeshDataGenerator;
import dev.jayms.net.Blocks;
import dev.jayms.net.Protocol;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Headless production chunk-mesher probe. Not a structure or rendering benchmark. */
class PrerequisiteProbe {
    static long consumed;

    static Chunk fragmented(int count) {
        var chunk = new Chunk();
        for (int i = 0; i < count; i++) {
            int x = 2 * (i % 8), y = 2 * (i / 64), z = 2 * ((i / 8) % 8);
            for (int sub : new int[] {0, 15})
                chunk.apply(new Protocol.Edit(x, y, z, Blocks.piece(Blocks.LED, 4),
                        4, sub, sub, sub, (i * 37 + sub) & 0xffffff));
        }
        return chunk;
    }

    static void check(MeshData mesh, int cells) {
        int quads = mesh.indices().length / 6;
        if (quads != cells * 12 || mesh.vertices().length != quads * 36
                || mesh.surface().length != quads * 12)
            throw new AssertionError("Fragment face count or mesh layout changed");
        consumed += mesh.indices().length + mesh.vertices().length + mesh.surface().length;
    }

    static long heapPoolPeakSum() {
        return ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(p -> p.getType() == MemoryType.HEAP)
                .mapToLong(p -> p.getPeakUsage().getUsed()).sum();
    }

    static long processPeakRssKiB() {
        // Read only this numeric metric, never export a process/environment dump.
        try (var lines = Files.lines(Path.of("/proc/self/status"))) {
            return lines.filter(s -> s.startsWith("VmHWM:"))
                    .mapToLong(s -> Long.parseLong(s.trim().split("\\s+")[1])).findFirst().orElse(-1);
        } catch (Exception unavailable) {
            return -1;
        }
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        int runs = Integer.parseInt(args[0]);
        if (runs < 1 || runs > 100) throw new IllegalArgumentException("runs 1..100");
        for (int cells : new int[] {1, 8, 64, 512}) {
            Chunk chunk = fragmented(cells);
            for (int warmup = 0; warmup < 3; warmup++) check(MeshDataGenerator.generate(chunk), cells);
            for (int run = 1; run <= runs; run++) {
                System.gc(); // Before timing; fixture setup and explicit GC are excluded.
                ManagementFactory.getMemoryPoolMXBeans().forEach(p -> p.resetPeakUsage());
                long start = System.nanoTime();
                MeshData mesh = MeshDataGenerator.generate(chunk);
                double elapsed = (System.nanoTime() - start) / 1e6;
                check(mesh, cells);
                long bytes = 4L * (mesh.vertices().length + mesh.indices().length + mesh.surface().length);
                System.out.printf("{\"operation\":\"fragment_chunk_mesh\",\"cells\":%d,\"leaves\":%d,\"run\":%d,\"elapsed_ms\":%.6f,\"quads\":%d,\"mesh_array_bytes\":%d,\"heap_pool_peak_sum_bytes\":%d,\"process_peak_rss_kib\":%d}%n",
                        cells, cells * 2, run, elapsed, mesh.indices().length / 6, bytes,
                        heapPoolPeakSum(), processPeakRssKiB());
            }
        }
        // Edge/regression: a solid chunk still merges to six exterior faces.
        var solid = new Chunk();
        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
            solid.setBlock(x, y, z, Blocks.STONE);
        if (MeshDataGenerator.generate(solid).indices().length != 36)
            throw new AssertionError("Solid chunk greedy-mesh regression");
        if (MeshDataGenerator.generate(new Chunk()).indices().length != 0)
            throw new AssertionError("Empty chunk regression");
        // Instrumentation must retain the production cancellation path.
        Thread.currentThread().interrupt();
        try {
            MeshDataGenerator.generate(solid);
            throw new AssertionError("Interrupted mesh should cancel");
        } catch (java.util.concurrent.CancellationException expected) {
            Thread.interrupted();
        }
        if (consumed == 0) throw new AssertionError("Probe results unused");
    }
}
