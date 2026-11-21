package dev.jayms.combat.arena;

import dev.jayms.Chunk;
import dev.jayms.ChunkPos;
import dev.jayms.World;
import dev.jayms.net.Blocks;

/** Loads the complete authored battlefield as resident chunks. */
public final class BasaltBattlefieldWorld {
    private BasaltBattlefieldWorld() {}

    public static void generate(World world) {
        int width = Chunk.WIDTH, height = Chunk.HEIGHT, length = Chunk.LENGTH;
        int minX = Math.floorDiv(-BasaltBattlefield.HALF_WIDTH, width);
        int maxX = Math.floorDiv(BasaltBattlefield.HALF_WIDTH - 1, width);
        int minY = Math.floorDiv(BasaltBattlefield.FLOOR_Y - 1, height);
        int maxY = Math.floorDiv(BasaltBattlefield.CEILING_Y - 1, height);
        int minZ = Math.floorDiv(-BasaltBattlefield.HALF_LENGTH, length);
        int maxZ = Math.floorDiv((int) BasaltBattlefield.spectator().z() + 4, length);
        for (int cz = minZ; cz <= maxZ; cz++) {
            for (int cx = minX; cx <= maxX; cx++) {
                for (int cy = minY; cy <= maxY; cy++) {
                    var chunk = new Chunk();
                    for (int z = 0; z < length; z++)
                        for (int x = 0; x < width; x++)
                            for (int y = 0; y < height; y++) {
                                int block = BasaltBattlefield.block(cx * width + x, cy * height + y, cz * length + z);
                                if (block != Blocks.AIR) chunk.setBlock(x, y, z, block);
                            }
                    world.addChunk(new ChunkPos(cx, cy, cz), chunk);
                }
            }
        }
    }
}
