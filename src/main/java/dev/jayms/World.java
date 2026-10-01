package dev.jayms;

import dev.jayms.net.*;

import java.util.*;

public class World implements AutoCloseable {
    private final Map<ChunkPos, Chunk> loadedChunks = new HashMap<>();
    private final Map<ChunkPos, Map<String, Protocol.Edit>> edits = new HashMap<>();
    private final Terrain terrain;

    public World() {
        this(Terrain.DEFAULT_SEED);
    }

    public World(long seed) {
        terrain = new Terrain(seed);
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
        for (var e : edits.getOrDefault(p, Map.of()).values())
            c.setBlock(
                    Math.floorMod(e.x(), 16),
                    Math.floorMod(e.y(), 16),
                    Math.floorMod(e.z(), 16),
                    e.type());
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
        var e = edits.getOrDefault(p, Map.of()).get(x + "," + y + "," + z);
        return e == null ? terrain.block(x, y, z) : e.type();
    }

    public void setBlock(int x, int y, int z, int type) {
        ChunkPos p = ChunkPos.fromBlock(x, y, z);
        var e = new Protocol.Edit(x, y, z, type);
        edits.computeIfAbsent(p, k -> new HashMap<>()).put(e.key(), e);
        Chunk c = loadedChunks.get(p);
        if (c != null)
            c.setBlock(Math.floorMod(x, 16), Math.floorMod(y, 16), Math.floorMod(z, 16), type);
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
