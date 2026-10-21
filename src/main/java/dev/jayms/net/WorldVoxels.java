package dev.jayms.net;

import dev.jayms.ChunkPos;
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
    private static final class CellBucket {
        final Object owner;
        private int singleKey=-1;
        private SparseVoxelOctree singleTree;
        private Map<Integer,SparseVoxelOctree> cells;
        CellBucket(Object owner,CellBucket source) {
            this.owner=owner;
            if(source!=null) {
                singleKey=source.singleKey;singleTree=source.singleTree;
                if(source.cells!=null)cells=new HashMap<>(source.cells);
            }
        }
        SparseVoxelOctree get(int key){return cells==null?(key==singleKey?singleTree:null):cells.get(key);}
        void put(int key,SparseVoxelOctree tree) {
            if(cells!=null){cells.put(key,tree);return;}
            if(singleKey<0||singleKey==key){singleKey=key;singleTree=tree;return;}
            cells=new HashMap<>(4);cells.put(singleKey,singleTree);cells.put(key,tree);singleKey=-1;singleTree=null;
        }
    }
    private final Map<ChunkPos,CellBucket> chunks=new HashMap<>();
    private Object owner=new Object();

    public WorldVoxels(Terrain terrain) {
        this.terrain = terrain;
    }

    /** Owner-thread snapshot: share frozen cells and clone a bucket on its next edit. */
    public WorldVoxels snapshot() {
        var copy=new WorldVoxels(terrain);
        copy.chunks.putAll(chunks);
        // Neither world may mutate a bucket owned before this sharing boundary.
        owner=new Object();
        return copy;
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
        return value == -1 ? Blocks.PARTIAL : value >>> 24 == 254 ? Blocks.LED : value & 0xffffff;
    }

    public void clear(){chunks.clear();}

    private SparseVoxelOctree editedCell(int x,int y,int z) {
        var bucket=chunks.get(ChunkPos.fromBlock(x,y,z));
        return bucket==null?null:bucket.get(localCell(x,y,z));
    }
    private static int localCell(int x,int y,int z){return (x&15)|((z&15)<<4)|((y&15)<<8);}

    /** Read-only cell. World changes go through apply, including on a mutable snapshot. */
    public SparseVoxelOctree cell(int x, int y, int z) {
        var existing = editedCell(x,y,z);
        if (existing != null) return existing;
        var tree = new SparseVoxelOctree(16);
        tree.fill(0, 0, 0, 16, 16, 16, encode(terrain.block(x, y, z)));
        return tree.freeze();
    }

    public int type(int x, int y, int z) {
        var existing = editedCell(x,y,z);
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
        replaceCell(edit.x(),edit.y(),edit.z(),tree);
    }

    /** Replace one validated world cell without replaying its leaves. Owner thread only. */
    public void replaceCell(int x,int y,int z,SparseVoxelOctree tree) {
        Objects.requireNonNull(tree,"tree");
        if(tree.size()!=RESOLUTION)throw new IllegalArgumentException("Invalid world cell resolution");
        // Copy the wrapper so later caller edits cannot change this world or freeze the caller.
        var stored=tree.copy().freeze();
        var position=ChunkPos.fromBlock(x,y,z);
        var bucket=chunks.get(position);
        if(bucket==null||bucket.owner!=owner) {
            bucket=new CellBucket(owner,bucket);
            chunks.put(position,bucket);
        }
        bucket.put(localCell(x,y,z),stored);
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
