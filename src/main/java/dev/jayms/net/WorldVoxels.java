package dev.jayms.net;

import dev.jayms.net.model.SparseVoxelOctree;

import java.util.*;

/**
 * Sparse overrides on an implicit seeded world; each block subdivides to sixteen cells per axis.
 */
public final class WorldVoxels {
    public static final int RESOLUTION = 16;
    private static final int[] COLORS = new int[185];

    static {
        for (int i = 1; i < COLORS.length; i++) {
            float[] c = Blocks.color(i);
            COLORS[i] =
                    0xff000000
                            | Math.round(c[0] * 255) << 16
                            | Math.round(c[1] * 255) << 8
                            | Math.round(c[2] * 255);
        }
    }

    public static int color(int type) {
        return type == 0 || Blocks.isModel(type) ? 0 : COLORS[type];
    }

    private final Terrain terrain;
    private final Map<String, SparseVoxelOctree> cells = new HashMap<>();

    public WorldVoxels(Terrain terrain) {
        this.terrain = terrain;
    }

    public static int encode(int type) {
        return type == 0 ? 0 : type == Blocks.LED ? 0xfeffffff : 0xff000000 | type;
    }

    public static int encode(Protocol.Edit edit) {
        return Blocks.material(edit.type()) == Blocks.LED
                ? 0xfe000000 | edit.color()
                : encode(Blocks.material(edit.type()));
    }

    public static int lightColor(int value) {
        return value >>> 24 == 254 ? value & 0xffffff : 0xffffff;
    }

    public static int surfaceColor(int value) {
        return decode(value) == Blocks.LED ? 0xff000000 | lightColor(value) : color(decode(value));
    }

    public static int decode(int value) {
        return value == -1 ? Blocks.PARTIAL : value >>> 24 == 254 ? Blocks.LED : value & 255;
    }

    public SparseVoxelOctree cell(int x, int y, int z) {
        var existing = cells.get(x + "," + y + "," + z);
        if (existing != null) return existing;
        var tree = new SparseVoxelOctree(16);
        tree.fill(0, 0, 0, 16, 16, 16, encode(terrain.block(x, y, z)));
        return tree;
    }

    public int type(int x, int y, int z) {
        return decode(cell(x, y, z).uniform(0, 0, 0, 16));
    }

    public int region(Protocol.Edit edit) {
        int side = 16 >> edit.depth();
        int material =
                decode(
                        cell(edit.x(), edit.y(), edit.z())
                                .uniform(
                                        edit.ix() * side,
                                        edit.iy() * side,
                                        edit.iz() * side,
                                        side));
        return material == Blocks.PARTIAL || material == 0
                ? material
                : Blocks.isModel(material) && edit.depth() > 0
                        ? Blocks.PARTIAL
                        : Blocks.piece(material, edit.depth());
    }

    public void apply(Protocol.Edit edit) {
        var tree = cell(edit.x(), edit.y(), edit.z()).copy();
        int side = 16 >> edit.depth(),
                x = edit.ix() * side,
                y = edit.iy() * side,
                z = edit.iz() * side;
        tree.fill(x, y, z, x + side, y + side, z + side, encode(edit));
        cells.put(edit.cellKey(), tree);
    }

    /** Preserve replay order and remove overrides fully covered by the new region. */
    public static void remember(Map<String, Protocol.Edit> history, Protocol.Edit edit) {
        history.entrySet()
                .removeIf(
                        e -> {
                            var old = e.getValue();
                            return old.cellKey().equals(edit.cellKey())
                                    && old.minX() >= edit.minX()
                                    && old.minY() >= edit.minY()
                                    && old.minZ() >= edit.minZ()
                                    && old.minX() + old.size() <= edit.minX() + edit.size()
                                    && old.minY() + old.size() <= edit.minY() + edit.size()
                                    && old.minZ() + old.size() <= edit.minZ() + edit.size();
                        });
        history.put(edit.key(), edit);
    }
}
