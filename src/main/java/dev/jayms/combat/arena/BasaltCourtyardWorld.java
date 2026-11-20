package dev.jayms.combat.arena;

import dev.jayms.World;
import dev.jayms.net.Blocks;

/** Installs the headless court layout as canonical edits on the render world's owner thread. */
public final class BasaltCourtyardWorld {
    public static void generate(World world) {
        reset(world);
    }

    /** Restores both the court and detached gallery while preserving edits outside their footprint. */
    public static void reset(World world) {
        BasaltCourtyard.restore(world::sample, world::apply);
    }

    public static boolean solid(World world, int x, int y, int z) {
        if (!BasaltCourtyard.contains(x, y, z)) return true;
        int material = world.sample(x, y, z);
        return material != Blocks.AIR && material != Blocks.WATER;
    }

    public static boolean canStand(World world, double x, double y, double z) {
        return BasaltCourtyard.canStand(x, y, z, (bx, by, bz) -> solid(world, bx, by, bz));
    }

    private BasaltCourtyardWorld() {}
}
