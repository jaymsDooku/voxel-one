package dev.jayms;

public final class ChunkGenerator {

    public static final int AIR = 0;
    public static final int GRASS = 1;
    public static final int DIRT = 2;
    public static final int STONE = 3;

    private ChunkGenerator() {
    }

    public static Chunk createExampleChunk() {
        return generate(new ChunkPos(0, 0, 0));
    }

    private static int calculateHeight(int worldX, int worldZ) {
        double height =
                8.0
                        + Math.sin(worldX * 0.08) * 6.0
                        + Math.cos(worldZ * 0.06) * 4.0;

        return (int) Math.floor(height);
    }

    public static Chunk createDebugChunk() {
        Chunk chunk = new Chunk();

        chunk.setBlock(2, 2, 2, GRASS);

        chunk.setBlock(5, 2, 5, STONE);
        chunk.setBlock(6, 2, 5, STONE);
        chunk.setBlock(7, 2, 5, STONE);

        for (int y = 0; y < 6; y++) {
            chunk.setBlock(10, y, 10, DIRT);
        }

        return chunk;
    }

    public static Chunk generate(ChunkPos position) {
        Chunk chunk = new Chunk();

        int originX = position.chunkX() * Chunk.WIDTH;
        int originY = position.chunkY() * Chunk.HEIGHT;
        int originZ = position.chunkZ() * Chunk.LENGTH;

        for (int x = 0; x < Chunk.WIDTH; x++) {
            for (int z = 0; z < Chunk.LENGTH; z++) {
                int worldX = originX + x;
                int worldZ = originZ + z;

                int surfaceY = calculateHeight(worldX, worldZ);

                for (int y = 0; y < Chunk.HEIGHT; y++) {
                    int worldY = originY + y;

                    if (worldY > surfaceY) {
                        continue; // New chunks already contain air.
                    }

                    int blockType;

                    if (worldY == surfaceY) {
                        blockType = GRASS;
                    } else if (worldY >= surfaceY - 2) {
                        blockType = DIRT;
                    } else {
                        blockType = STONE;
                    }

                    chunk.setBlock(x, y, z, blockType);
                }
            }
        }

        return chunk;
    }
}
