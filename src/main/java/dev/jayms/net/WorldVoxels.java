package dev.jayms.net;

import dev.jayms.net.model.SparseVoxelOctree;

import java.util.*;

/**
 * Sparse overrides on an implicit seeded world; each block subdivides to sixteen cells per axis.
 */
public final class WorldVoxels {
    /** Ordered edit history with a cell index. Collection views are read-only. */
    public static final class History extends AbstractMap<String, Protocol.Edit> {
        private final Map<String, Protocol.Edit> ordered = new LinkedHashMap<>();
        private final Map<String, Set<String>> cells = new HashMap<>();
        @Override public Set<Entry<String, Protocol.Edit>> entrySet() {
            return Collections.unmodifiableMap(ordered).entrySet();
        }
        @Override public Protocol.Edit get(Object key) { return ordered.get(key); }
        @Override public int size() { return ordered.size(); }
        @Override public boolean containsKey(Object key) { return ordered.containsKey(key); }
        @Override public Protocol.Edit put(String key, Protocol.Edit edit) {
            if (!key.equals(edit.key())) throw new IllegalArgumentException("Edit key mismatch");
            cells.computeIfAbsent(edit.cellKey(), k -> new LinkedHashSet<>()).add(key);
            return ordered.put(key,edit);
        }
        @Override public Protocol.Edit remove(Object key) {
            var old = ordered.remove(key);
            if (old != null) {
                var keys = cells.get(old.cellKey()); keys.remove(key);
                if (keys.isEmpty()) cells.remove(old.cellKey());
            }
            return old;
        }
        @Override public void clear() { ordered.clear(); cells.clear(); }
        private void remember(Protocol.Edit edit) {
            var keys = cells.get(edit.cellKey());
            if (keys != null) for (var key : List.copyOf(keys)) {
                var old = ordered.get(key);
                if (covered(old,edit)) remove(key);
            }
            put(edit.key(),edit);
        }
    }
    public static final int RESOLUTION = 16;
    private static final int[] COLORS = new int[190];

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
        var existing = cells.get(x + "," + y + "," + z);
        return existing == null ? terrain.block(x, y, z) : decode(existing.uniform(0, 0, 0, 16));
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
                : !Blocks.subdividable(material) && edit.depth() > 0
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
        if (history instanceof History indexed) { indexed.remember(edit); return; }
        history.entrySet()
                .removeIf(
                        e -> {
                            var old = e.getValue();
                            return old.cellKey().equals(edit.cellKey()) && covered(old,edit);
                        });
        history.put(edit.key(), edit);
    }
    private static boolean covered(Protocol.Edit old, Protocol.Edit edit) {
        return old.minX() >= edit.minX() && old.minY() >= edit.minY() && old.minZ() >= edit.minZ()
                && old.minX()+old.size() <= edit.minX()+edit.size()
                && old.minY()+old.size() <= edit.minY()+edit.size()
                && old.minZ()+old.size() <= edit.minZ()+edit.size();
    }
}
