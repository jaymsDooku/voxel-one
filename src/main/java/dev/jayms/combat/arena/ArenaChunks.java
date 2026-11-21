package dev.jayms.combat.arena;

import dev.jayms.Chunk;
import dev.jayms.ChunkPos;
import dev.jayms.World;
import dev.jayms.net.Blocks;

final class ArenaChunks {
    private ArenaChunks() {}

    static void generate(World world, PracticeTerrain terrain, int minX, int maxX, int minY, int maxY,
                         int minZ, int maxZ) {
        int width = Chunk.WIDTH, height = Chunk.HEIGHT, length = Chunk.LENGTH;
        for (int cz = Math.floorDiv(minZ, length); cz <= Math.floorDiv(maxZ - 1, length); cz++) {
            for (int cx = Math.floorDiv(minX, width); cx <= Math.floorDiv(maxX - 1, width); cx++) {
                for (int cy = Math.floorDiv(minY, height); cy <= Math.floorDiv(maxY - 1, height); cy++) {
                    var chunk = new Chunk();
                    for (int z = 0; z < length; z++) {
                        for (int x = 0; x < width; x++) {
                            for (int y = 0; y < height; y++) {
                                int block = terrain.block(cx * width + x, cy * height + y, cz * length + z);
                                if (block != Blocks.AIR) chunk.setBlock(x, y, z, block);
                            }
                        }
                    }
                    world.addChunk(new ChunkPos(cx, cy, cz), chunk);
                }
            }
        }
    }
}
