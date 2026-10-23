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
        private final Map<String, Protocol.Edit> ordered;
        private final Map<String, CellKeys> cells;
        private Object owner=new Object();
        public History(){ordered=new LinkedHashMap<>();cells=new HashMap<>();}
        private History(History source) {
            ordered=new LinkedHashMap<>(source.ordered);cells=new HashMap<>(source.cells);
            // Neither copy may mutate the index entries shared at this boundary.
            source.owner=new Object();
        }
        /** Owner-thread transaction copy. Retains order; shares index entries until edited. */
        public static History copyOf(Map<String,Protocol.Edit> source) {
            if(source instanceof History history)return new History(history);
            var copy=new History();copy.putAll(source);return copy;
        }
        private CellKeys writable(String cell) {
            var keys=cells.get(cell);
            if(keys==null||keys.owner!=owner){keys=new CellKeys(owner,keys);cells.put(cell,keys);}
            return keys;
        }
        /** Most cells have one edit; allocate a set only for fractional edit histories. */
        private static final class CellKeys {
            final Object owner;
            CellKeys(Object owner,CellKeys source) {
                this.owner=owner;
                if(source!=null){single=source.single;if(source.many!=null)many=new LinkedHashSet<>(source.many);}
            }
            private String single;
            private LinkedHashSet<String> many;
            void add(String key) {
                if(many!=null){many.add(key);return;}
                if(single==null||single.equals(key)){single=key;return;}
                many=new LinkedHashSet<>();many.add(single);many.add(key);single=null;
            }
            void remove(Object key) {
                if(many==null){if(Objects.equals(key,single))single=null;return;}
                many.remove(key);
                if(many.size()==1){single=many.iterator().next();many=null;}
            }
            boolean isEmpty(){return many==null?single==null:many.isEmpty();}
            List<String> snapshot(){return many==null?(single==null?List.of():List.of(single)):List.copyOf(many);}
        }
        @Override public Set<Entry<String, Protocol.Edit>> entrySet() {
            return Collections.unmodifiableMap(ordered).entrySet();
        }
        @Override public Set<String> keySet() { return Collections.unmodifiableSet(ordered.keySet()); }
        @Override public Collection<Protocol.Edit> values() { return Collections.unmodifiableCollection(ordered.values()); }
        @Override public Protocol.Edit get(Object key) { return ordered.get(key); }
        @Override public int size() { return ordered.size(); }
        @Override public boolean containsKey(Object key) { return ordered.containsKey(key); }
        @Override public Protocol.Edit put(String key, Protocol.Edit edit) {
            if (!key.equals(edit.key())) throw new IllegalArgumentException("Edit key mismatch");
            writable(edit.cellKey()).add(key);
            return ordered.put(key,edit);
        }
        @Override public Protocol.Edit remove(Object key) {
            var old = ordered.remove(key);
            if (old != null) {
                var keys = writable(old.cellKey()); keys.remove(key);
                if (keys.isEmpty()) cells.remove(old.cellKey());
            }
            return old;
        }
        @Override public void clear() { ordered.clear(); cells.clear(); }
        private void remember(Protocol.Edit edit) {
            var keys = cells.get(edit.cellKey());
            if (keys != null) for (var key : keys.snapshot()) {
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
        private long revision;
        private int singleKey=-1;
        private SparseVoxelOctree singleTree;
        private Map<Integer,SparseVoxelOctree> cells;
        CellBucket(Object owner,CellBucket source) {
            this.owner=owner;
            if(source!=null) {
                revision=source.revision;singleKey=source.singleKey;singleTree=source.singleTree;
                if(source.cells!=null)cells=new HashMap<>(source.cells);
            }
        }
        SparseVoxelOctree get(int key){return cells==null?(key==singleKey?singleTree:null):cells.get(key);}
        void put(int key,SparseVoxelOctree tree) {
            revision++;
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

    /** Capture only affected buckets under the world authority lock. Stage on one worker;
     * merge under that same lock. No live cell changes occur during capture/staging. */
    public CellPatch captureCellPatch(Collection<ChunkPos> positions) {
        var staged=new WorldVoxels(terrain);var stamps=new HashMap<ChunkPos,Object>();
        for(var position:positions) {
            stamps.put(position,chunkStamp(position));var bucket=chunks.get(position);
            if(bucket!=null)staged.chunks.put(position,bucket);
        }
        owner=new Object();
        return new CellPatch(staged,stamps);
    }
    public static final class CellPatch {
        private final WorldVoxels staged;private final Map<ChunkPos,Object> stamps;
        private CellPatch(WorldVoxels staged,Map<ChunkPos,Object> stamps){this.staged=staged;this.stamps=stamps;}
        public void replaceCell(int x,int y,int z,SparseVoxelOctree tree) {
            if(!stamps.containsKey(ChunkPos.fromBlock(x,y,z)))throw new IllegalArgumentException("Cell outside captured chunks");
            staged.replaceCell(x,y,z,tree);
        }
        /** Null means a captured chunk/terrain changed: caller must restage from current.
         * Other current chunks are retained. The returned candidate is not published. */
        public WorldVoxels merge(WorldVoxels current) {
            for(var entry:stamps.entrySet()) {
                if(entry.getValue()==null||!entry.getValue().equals(current.chunkStamp(entry.getKey())))return null;
            }
            var result=current.snapshot();result.chunks.putAll(staged.chunks);
            // Protect the candidate if this staging object is edited/reused afterward.
            staged.owner=new Object();
            return result;
        }
    }

    private record ChunkStamp(Terrain terrain,long terrainRevision,CellBucket bucket,long revision) {}
    public long terrainRevision(){return terrain.geometryRevision();}
    /** Trusted in-process stamp for one chunk. Compare only under the authority's lock. */
    public Object chunkStamp(ChunkPos position) {
        long generation=terrain.geometryRevision();if((generation&1)!=0)return null;
        var bucket=chunks.get(position);
        return new ChunkStamp(terrain,generation,bucket,bucket==null?0:bucket.revision);
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
