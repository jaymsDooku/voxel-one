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
    private float ambient = -1, submittedAmbient;
    private boolean separateSky,submittedSeparateSky;

    public LightVolume update(World world, float px, float pz) {
        return update(world, px, pz, 1);
    }

    public LightVolume update(World world, float px, float pz, float skyStrength) {return update(world,px,pz,skyStrength,false);}
    public LightVolume update(World world,float px,float pz,float skyStrength,boolean dynamicSky) {
        int cx = Math.floorDiv((int) Math.floor(px), 16) * 16,
                cz = Math.floorDiv((int) Math.floor(pz), 16) * 16;
        LightVolume result = null;
        if (pending != null && pending.isDone()) {
            try {
                var baked = pending.get();
                if (submittedRevision == world.editsVersion()
                        && submittedX == cx
                        && submittedZ == cz
                        && submittedAmbient == skyStrength && submittedSeparateSky==dynamicSky) {
                    result = baked;
                    revision = submittedRevision;
                    ambient = submittedAmbient;
                    separateSky=submittedSeparateSky;
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
                && (cx != centerX
                        || cz != centerZ
                        || revision != world.editsVersion()
                        || ambient != skyStrength || separateSky!=dynamicSky)) {
            submittedAmbient = skyStrength;
            submittedSeparateSky=dynamicSky;
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
            int generatorVersion = world.terrain().version;
            var stressGrid = world.terrain().stressGrid();
            ModelLibrary models = world.models().copy();
            pending =
                    worker.submit(
                            () -> {
                                var terrain = new Terrain(seed, generatorVersion);
                                terrain.stressGrid(stressGrid);
                                WorldVoxels fallback = new WorldVoxels(terrain);
                                for (var edit : edits.values()) fallback.apply(edit);
                                return LightVolume.bake(
                                        cx - 48,
                                        -32,
                                        cz - 48,
                                        96,
                                        128,
                                        96,
                                        skyStrength,
                                        new LightVolume.Sampler() {
                                            private final Map<Integer, SparseVoxelOctree> geometry =
                                                    new HashMap<>();

                                            private SparseVoxelOctree cell(int x, int y, int z) {
                                                var octree =
                                                        chunks.get(ChunkPos.fromBlock(x, y, z));
                                                return octree == null
                                                        ? fallback.cell(x, y, z)
                                                        : octree.region(
                                                                Math.floorMod(x, 16) * 16,
                                                                Math.floorMod(y, 16) * 16,
                                                                Math.floorMod(z, 16) * 16,
                                                                16);
                                            }

                                            public int value(int x, int y, int z) {
                                                int value = cell(x, y, z).uniform(0, 0, 0, 16);
                                                return Blocks.isModel(WorldVoxels.decode(value))
                                                        ? -1
                                                        : value;
                                            }

                                            public SparseVoxelOctree detail(int x, int y, int z) {
                                                var tree = cell(x, y, z);
                                                int type =
                                                        WorldVoxels.decode(
                                                                tree.uniform(0, 0, 0, 16));
                                                if (!Blocks.isModel(type)) return tree;
                                                return geometry.computeIfAbsent(
                                                        type, id -> modelGeometry(models.get(id)));
                                            }
                                        },dynamicSky);
                            });
        }
        return result;
    }

    /**
     * Model colours are opaque ARGB, not encoded block IDs. Preserve their actual 8/16/32 geometry.
     */
    public static SparseVoxelOctree modelGeometry(ModelLibrary.Entry entry) {
        var model = entry == null ? null : entry.definition().voxels();
        var tree = new SparseVoxelOctree(model == null ? 16 : model.size());
        if (model == null) tree.fill(0, 0, 0, 16, 16, 16, WorldVoxels.encode(Blocks.STONE));
        else
            for (var leaf : model.leaves())
                tree.fill(
                        leaf.x(),
                        leaf.y(),
                        leaf.z(),
                        leaf.x() + leaf.side(),
                        leaf.y() + leaf.side(),
                        leaf.z() + leaf.side(),
                        WorldVoxels.encode(Blocks.STONE));
        return tree;
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }
}
