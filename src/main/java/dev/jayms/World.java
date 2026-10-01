package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.*;

import java.util.*;

public class World implements AutoCloseable {
    private final Map<ChunkPos, Chunk> loadedChunks = new HashMap<>();
    private final Map<ChunkPos, Map<String, Protocol.Edit>> edits = new HashMap<>();
    private final Terrain terrain;
    private final WorldVoxels voxels;
    private long editsVersion;
    private final ModelLibrary models;

    public World() {
        this(Terrain.DEFAULT_SEED);
    }

    public World(long seed) {
        this(seed, new ModelLibrary());
    }

    public World(long seed, ModelLibrary models) {
        this.models = models;
        terrain = new Terrain(seed);
        voxels = new WorldVoxels(terrain);
    }

    public long editsVersion() {
        return editsVersion;
    }

    public Map<String, Protocol.Edit> editsSnapshot() {
        Map<String, Protocol.Edit> snapshot = new LinkedHashMap<>();
        edits.values().forEach(snapshot::putAll);
        return Collections.unmodifiableMap(snapshot);
    }

    /** Full columns take over from distant terrain only once all nonempty chunks have a mesh. */
    public Set<ChunkPos> renderedColumns() {
        Set<ChunkPos> columns = new HashSet<>(), incomplete = new HashSet<>();
        for (var e : loadedChunks.entrySet()) {
            ChunkPos p = new ChunkPos(e.getKey().chunkX(), 0, e.getKey().chunkZ());
            columns.add(p);
            if (!e.getValue().isEmpty() && e.getValue().getMesh() == null) incomplete.add(p);
        }
        columns.removeAll(incomplete);
        return columns;
    }

    public ModelLibrary models() {
        return models;
    }

    public Terrain terrain() {
        return terrain;
    }

    public Map<ChunkPos, Chunk> getLoadedChunks() {
        return Collections.unmodifiableMap(loadedChunks);
    }

    public void addChunk(ChunkPos p, Chunk c) {
        if (loadedChunks.putIfAbsent(p, c) != null)
            throw new IllegalStateException("Already loaded");
        c.attach(this, p);
        for (var e : edits.getOrDefault(p, Map.of()).values()) c.apply(e);
        dirtyNeighbors(p);
    }

    private void dirtyNeighbors(ChunkPos p) {
        for (Face f : Face.values()) {
            Chunk c =
                    loadedChunks.get(
                            new ChunkPos(
                                    p.chunkX() + f.dx(), p.chunkY() + f.dy(), p.chunkZ() + f.dz()));
            if (c != null) c.markDirty();
        }
    }

    public boolean isLoaded(int x, int y, int z) {
        return loadedChunks.containsKey(ChunkPos.fromBlock(x, y, z));
    }

    public int getBlock(int x, int y, int z) {
        Chunk c = loadedChunks.get(ChunkPos.fromBlock(x, y, z));
        if (c == null) throw new IllegalStateException("Chunk not loaded");
        return c.getBlock(Math.floorMod(x, 16), Math.floorMod(y, 16), Math.floorMod(z, 16));
    }

    public int sample(int x, int y, int z) {
        ChunkPos p = ChunkPos.fromBlock(x, y, z);
        Chunk c = loadedChunks.get(p);
        if (c != null)
            return c.getBlock(Math.floorMod(x, 16), Math.floorMod(y, 16), Math.floorMod(z, 16));
        return voxels.type(x, y, z);
    }

    public int material(int x, int y, int z) {
        var p = new ChunkPos(Math.floorDiv(x, 256), Math.floorDiv(y, 256), Math.floorDiv(z, 256));
        var c = loadedChunks.get(p);
        if (c != null)
            return c.material(Math.floorMod(x, 256), Math.floorMod(y, 256), Math.floorMod(z, 256));
        return WorldVoxels.decode(
                voxels.cell(Math.floorDiv(x, 16), Math.floorDiv(y, 16), Math.floorDiv(z, 16))
                        .get(Math.floorMod(x, 16), Math.floorMod(y, 16), Math.floorMod(z, 16)));
    }

    public dev.jayms.net.model.SparseVoxelOctree cell(int x, int y, int z) {
        var c = loadedChunks.get(ChunkPos.fromBlock(x, y, z));
        return c == null
                ? voxels.cell(x, y, z)
                : c.cell(Math.floorMod(x, 16), Math.floorMod(y, 16), Math.floorMod(z, 16));
    }

    public int region(Protocol.Edit e) {
        int side = 16 >> e.depth();
        int type =
                WorldVoxels.decode(
                        cell(e.x(), e.y(), e.z())
                                .uniform(e.ix() * side, e.iy() * side, e.iz() * side, side));
        if (type == 0 || type == Blocks.PARTIAL) return type;
        return Blocks.isModel(type) && e.depth() > 0
                ? Blocks.PARTIAL
                : Blocks.piece(type, e.depth());
    }

    public void setBlock(int x, int y, int z, int type) {
        apply(new Protocol.Edit(x, y, z, type));
    }

    public void apply(Protocol.Edit e) {
        var p = ChunkPos.fromBlock(e.x(), e.y(), e.z());
        editsVersion++;
        voxels.apply(e);
        WorldVoxels.remember(edits.computeIfAbsent(p, k -> new LinkedHashMap<>()), e);
        var c = loadedChunks.get(p);
        if (c != null) c.apply(e);
        dirtyNeighbors(p);
    }

    /** Load nearest columns first; bounded generation per frame, unloading distant GPU buffers. */
    public void stream(float x, float z, int budget) throws Exception {
        int cx = Math.floorDiv((int) Math.floor(x), 16),
                cz = Math.floorDiv((int) Math.floor(z), 16);
        var it = loadedChunks.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (Math.abs(e.getKey().chunkX() - cx) > 6 || Math.abs(e.getKey().chunkZ() - cz) > 6) {
                e.getValue().close();
                it.remove();
                dirtyNeighbors(e.getKey());
            }
        }
        int generated = 0;
        for (int ring = 0; ring <= 4; ring++)
            for (int a = -ring; a <= ring; a++)
                for (int b = -ring; b <= ring; b++) {
                    if (Math.max(Math.abs(a), Math.abs(b)) != ring) continue;
                    if (loadedChunks.containsKey(new ChunkPos(cx + a, -2, cz + b))) continue;
                    for (int y = -2; y <= 7; y++) {
                        ChunkPos p = new ChunkPos(cx + a, y, cz + b);
                        addChunk(p, ChunkGenerator.generate(p, terrain));
                    }
                    if (++generated >= budget) return;
                }
    }

    @Override
    public void close() throws Exception {
        for (Chunk c : loadedChunks.values()) c.close();
    }
}
