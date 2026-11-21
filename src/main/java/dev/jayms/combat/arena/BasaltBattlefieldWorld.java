package dev.jayms.combat.arena;

import dev.jayms.World;

/** Loads the complete authored battlefield as resident chunks. */
public final class BasaltBattlefieldWorld {
    private BasaltBattlefieldWorld() {}

    public static void generate(World world) {
        ArenaChunks.generate(world, PracticeTerrain.BATTLEFIELD,
                -BasaltBattlefield.HALF_WIDTH, BasaltBattlefield.HALF_WIDTH,
                BasaltBattlefield.FLOOR_Y - 1, BasaltBattlefield.CEILING_Y,
                -BasaltBattlefield.HALF_LENGTH, (int) BasaltBattlefield.spectator().z() + 5);
    }
}
