package dev.jayms.render;

import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.model.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * One worker, coalesced edits, immutable octree snapshots; no GL or shared terrain cache on worker.
 */
public final class WorldLighting implements AutoCloseable {
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread t = new Thread(r, "voxel-lighting");
                        t.setDaemon(true);
                        return t;
                    });
    private Future<LightVolume> pending;
    private int centerX = Integer.MIN_VALUE, centerZ;
    private long revision = -1, submittedRevision;
    private int submittedX, submittedZ;

    public LightVolume update(World world, float px, float pz) {
        int cx = Math.floorDiv((int) Math.floor(px), 16) * 16,
                cz = Math.floorDiv((int) Math.floor(pz), 16) * 16;
        LightVolume result = null;
        if (pending != null && pending.isDone()) {
            try {
                var baked = pending.get();
                if (submittedRevision == world.editsVersion()
                        && submittedX == cx
                        && submittedZ == cz) {
                    result = baked;
                    revision = submittedRevision;
                    centerX = cx;
                    centerZ = cz;
                }
            } catch (Exception e) {
                throw new IllegalStateException("Voxel lighting failed", e);
            } finally {
                pending = null;
            }
        }
        if (pending == null
                && (cx != centerX || cz != centerZ || revision != world.editsVersion())) {
            submittedX = cx;
            submittedZ = cz;
            submittedRevision = world.editsVersion();
            Map<ChunkPos, SparseVoxelOctree> chunks = new HashMap<>();
            for (var e : world.getLoadedChunks().entrySet())
                if (Math.abs(e.getKey().chunkX() * 16 - cx) <= 64
                        && Math.abs(e.getKey().chunkZ() * 16 - cz) <= 64)
                    chunks.put(e.getKey(), e.getValue().snapshot());
            var edits = world.editsSnapshot();
            long seed = world.terrain().seed;
            pending =
                    worker.submit(
                            () -> {
                                WorldVoxels fallback = new WorldVoxels(new Terrain(seed));
                                for (var edit : edits.values()) fallback.apply(edit);
                                return LightVolume.bake(
                                        cx - 48,
                                        -32,
                                        cz - 48,
                                        96,
                                        128,
                                        96,
                                        (x, y, z) -> {
                                            var octree = chunks.get(ChunkPos.fromBlock(x, y, z));
                                            int value;
                                            if (octree != null)
                                                value =
                                                        octree.uniform(
                                                                Math.floorMod(x, 16) * 16,
                                                                Math.floorMod(y, 16) * 16,
                                                                Math.floorMod(z, 16) * 16,
                                                                16);
                                            else
                                                value = fallback.cell(x, y, z).uniform(0, 0, 0, 16);
                                            if (value != -1) return value;
                                            // Mixed cells retain tiny LEDs as light sources;
                                            // otherwise estimate partial coverage.
                                            var cell =
                                                    octree != null
                                                            ? octree.region(
                                                                    Math.floorMod(x, 16) * 16,
                                                                    Math.floorMod(y, 16) * 16,
                                                                    Math.floorMod(z, 16) * 16,
                                                                    16)
                                                            : fallback.cell(x, y, z);
                                            for (var leaf : cell.leaves())
                                                if (WorldVoxels.decode(leaf.color()) == Blocks.LED)
                                                    return leaf.color();
                                            return 0xff000000 | Blocks.PARTIAL;
                                        });
                            });
        }
        return result;
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }
}
