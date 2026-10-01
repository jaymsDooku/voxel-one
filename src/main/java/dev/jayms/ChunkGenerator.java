package dev.jayms;

import dev.jayms.net.Terrain;

public final class ChunkGenerator {
    public static final int AIR = 0, GRASS = 1, DIRT = 2, STONE = 3;

    public static Chunk generate(ChunkPos p) {
        return generate(p, new Terrain(Terrain.DEFAULT_SEED));
    }

    public static Chunk generate(ChunkPos p, Terrain terrain) {
        Chunk c = new Chunk();
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++)
                for (int y = 0; y < 16; y++) {
                    int type =
                            terrain.block(
                                    p.chunkX() * 16 + x, p.chunkY() * 16 + y, p.chunkZ() * 16 + z);
                    if (type != 0) c.setBlock(x, y, z, type);
                }
        return c;
    }

    public static Chunk createExampleChunk() {
        return generate(new ChunkPos(0, 0, 0));
    }

    public static Chunk createDebugChunk() {
        Chunk c = new Chunk();
        c.setBlock(2, 2, 2, GRASS);
        return c;
    }

    private ChunkGenerator() {}
}
