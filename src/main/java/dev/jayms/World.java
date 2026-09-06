package dev.jayms;

import java.util.*;

public class World implements AutoCloseable {

    private Map<ChunkPos, Chunk> loadedChunks = new HashMap<>();

    public Map<ChunkPos, Chunk> getLoadedChunks() {
        return Collections.unmodifiableMap(loadedChunks);
    }

    public void addChunk(ChunkPos position, Chunk chunk) {
        if (loadedChunks.putIfAbsent(position, chunk) != null) {
            throw new IllegalStateException(
                    "Chunk already loaded: " + position
            );
        }
    }

    public boolean isLoaded(int x, int y, int z) {
        return loadedChunks.containsKey(ChunkPos.fromBlock(x, y, z));
    }

    private Chunk requireChunk(int x, int y, int z) {
        ChunkPos position = ChunkPos.fromBlock(x, y, z);
        Chunk chunk = loadedChunks.get(position);

        if (chunk == null) {
            throw new IllegalStateException(
                    "Chunk not loaded: " + position
            );
        }

        return chunk;
    }

    public int getBlock(int x, int y, int z) {
        Chunk chunk = requireChunk(x, y, z);

        return chunk.getBlock(
                Math.floorMod(x, Chunk.WIDTH),
                Math.floorMod(y, Chunk.HEIGHT),
                Math.floorMod(z, Chunk.LENGTH)
        );
    }

    public void setBlock(int x, int y, int z, int blockType) {
        Chunk chunk = requireChunk(x, y, z);

        chunk.setBlock(
                Math.floorMod(x, Chunk.WIDTH),
                Math.floorMod(y, Chunk.HEIGHT),
                Math.floorMod(z, Chunk.LENGTH),
                blockType
        );
    }

    public Map<ChunkPos, Mesh> generateMeshes() {
        Map<ChunkPos, Mesh> meshes = new HashMap<>();

        for (var entry : loadedChunks.entrySet()) {
            MeshData meshData =
                    MeshDataGenerator.generate(entry.getValue());

            meshes.put(entry.getKey(), new Mesh(meshData));
        }

        return meshes;
    }

    @Override
    public void close() throws Exception {
        for (var entry : loadedChunks.entrySet()) {
            entry.getValue().close();
        }
    }
}
