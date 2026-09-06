package dev.jayms;

public record ChunkPos(int chunkX, int chunkY, int chunkZ) {

    public static ChunkPos fromBlock(int x, int y, int z) {
        return new ChunkPos(
                Math.floorDiv(x, Chunk.WIDTH),
                Math.floorDiv(y, Chunk.HEIGHT),
                Math.floorDiv(z, Chunk.LENGTH)
        );
    }

}
