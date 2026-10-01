package dev.jayms;

/** Horizontal footprint of a rendered world region, in block coordinates. */
public record WorldBounds(float minX, float minZ, float maxX, float maxZ) {
    public static WorldBounds loaded(World world) {
        float minX = Float.POSITIVE_INFINITY, minZ = minX;
        float maxX = Float.NEGATIVE_INFINITY, maxZ = maxX;
        for (ChunkPos p : world.getLoadedChunks().keySet()) {
            minX = Math.min(minX, p.chunkX() * 16);
            maxX = Math.max(maxX, p.chunkX() * 16 + 16);
            minZ = Math.min(minZ, p.chunkZ() * 16);
            maxZ = Math.max(maxZ, p.chunkZ() * 16 + 16);
        }
        return Float.isFinite(minX)
                ? new WorldBounds(minX, minZ, maxX, maxZ)
                : new WorldBounds(0, 0, 16, 16);
    }
}
