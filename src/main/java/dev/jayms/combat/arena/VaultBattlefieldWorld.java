package dev.jayms.combat.arena;

import dev.jayms.World;

/** Loads the authored vaults, siege field and spectator deck as resident chunks. */
public final class VaultBattlefieldWorld {
    private VaultBattlefieldWorld() {}

    public static void generate(World world) {
        ArenaChunks.generate(world, PracticeTerrain.VAULT_BATTLEFIELD,
                -VaultBattlefield.HALF_WIDTH, VaultBattlefield.HALF_WIDTH,
                VaultBattlefield.TRENCH_Y, VaultBattlefield.CEILING_Y,
                -VaultBattlefield.HALF_LENGTH, VaultBattlefield.HALF_LENGTH);
    }
}
