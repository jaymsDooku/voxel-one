package dev.jayms.net;

import dev.jayms.ChunkPos;
import dev.jayms.net.model.SparseVoxelOctree;

import java.util.*;

/**
 * Sparse overrides on an implicit seeded world; each block subdivides to sixteen cells per axis.
 */
public final class WorldVoxels {
    /** Immutable world-cell coordinate; contains no geometry or replay history. */
    public record CellPosition(int x,int y,int z) {}
    /** Ordered edit history with a cell index. Collection views are read-only. */
    public static final class History extends AbstractMap<String, Protocol.Edit> {
        private final PersistentOrderedMap<Protocol.Edit> ordered;
        private final PersistentOrderedMap<CellKeys> cells;
        private Object owner=new Object();
        private long revision;
        /** Owner-thread change stamp; pair with this history identity. */
        public long revision(){return revision;}
        // Persistent insertion indexes retain replay order without whole-map copying.
        // Iterators walk trees directly; they never scan a sparse hash table.
        public History(){ordered=new PersistentOrderedMap<>();cells=new PersistentOrderedMap<>();}
        /** Begin under the authority lock; every batch read needs that same lock.
         * Any edit-history mutation invalidates the cursor before iterator access. */
        public CopyCursor copyCursor(){return new CopyCursor();}
        public final class CopyCursor {
            private final long expectedRevision=revision;
            private final Iterator<Entry<String,Protocol.Edit>> iterator=ordered.entrySet().iterator();
            private int remaining=ordered.size();
            private CopyCursor(){}
            /** At most 1024 detached key/value pairs; copying belongs outside the lock. */
            public CopyBatch nextBatch() {
                if(revision!=expectedRevision)throw new ConcurrentModificationException("History changed during copy");
                int count=Math.min(1024,remaining);
                if(count==0)return CopyBatch.EMPTY;
                var keys=new String[count];var values=new Protocol.Edit[count];
                for(int i=0;i<count;i++){var entry=iterator.next();keys[i]=entry.getKey();values[i]=entry.getValue();}
                remaining-=count;return new CopyBatch(keys,values);
            }
        }
        /** Begin and read every batch under the same owner lock. No spatial/order contract.
         * Visits explicit air and every other indexed cell once, regardless of edit count. */
        public CellCopyCursor cellCopyCursor(){return new CellCopyCursor();}
        public final class CellCopyCursor {
            private final long expectedRevision=revision;
            private final Iterator<CellKeys> iterator=cells.values().iterator();
            private int remaining=cells.size();
            private CellCopyCursor(){}
            /** At most 1024 detached positions. Any source mutation invalidates even exhaustion. */
            public List<CellPosition> nextBatch() {
                if(revision!=expectedRevision)throw new ConcurrentModificationException("History changed during cell copy");
                int count=Math.min(1024,remaining);if(count==0)return List.of();
                var result=new ArrayList<CellPosition>(count);
                for(int i=0;i<count;i++) {
                    var keys=iterator.next();String first=keys.many==null?keys.single:keys.many.iterator().next();
                    var edit=ordered.get(first);
                    if(edit==null)throw new IllegalStateException("History cell index mismatch");
                    result.add(new CellPosition(edit.x(),edit.y(),edit.z()));
                }
                remaining-=count;return List.copyOf(result);
            }
        }
        /** Immutable cursor result. Captured keys already passed History.put validation. */
        public static final class CopyBatch extends AbstractList<Protocol.Edit> implements RandomAccess {
            private static final CopyBatch EMPTY=new CopyBatch(new String[0],new Protocol.Edit[0]);
            private final String[] keys;private final Protocol.Edit[] values;
            private CopyBatch(String[] keys,Protocol.Edit[] values){this.keys=keys;this.values=values;}
            @Override public int size(){return values.length;}
            @Override public Protocol.Edit get(int index){return values[index];}
            /** Rebuild the private target index while reusing immutable validated key strings. */
            public void copyTo(History target,Runnable checkpoint) {
                Objects.requireNonNull(target);Objects.requireNonNull(checkpoint);
                for(int i=0;i<values.length;i++){checkpoint.run();target.putTrusted(keys[i],values[i]);}
            }
        }
        private History(History source) {
            ordered=new PersistentOrderedMap<>(source.ordered);cells=new PersistentOrderedMap<>(source.cells);
            // Neither copy may mutate the index entries shared at this boundary.
            source.owner=new Object();
        }
        /** Owner-thread constant-root transaction copy. Retains order; shares index
         * entries until edited. This does not validate or publish a transaction. */
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
                if(single==null){single=key;return;}
                if(single.equals(key))return;
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
            // The persistent index already exposes an unmodifiable set of immutable
            // entries. An additional map wrapper allocates a wrapper per replay row.
            return ordered.entrySet();
        }
        @Override public Set<String> keySet() { return Collections.unmodifiableSet(ordered.keySet()); }
        @Override public Collection<Protocol.Edit> values() { return Collections.unmodifiableCollection(ordered.values()); }
        @Override public Protocol.Edit get(Object key) { return ordered.get(key); }
        @Override public int size() { return ordered.size(); }
        @Override public boolean containsKey(Object key) { return ordered.containsKey(key); }
        @Override public Protocol.Edit put(String key, Protocol.Edit edit) {
            if (!key.equals(edit.key())) throw new IllegalArgumentException("Edit key mismatch");
            return putTrusted(key,edit);
        }
        private Protocol.Edit putTrusted(String key,Protocol.Edit edit) {
            // A validated depth-zero key is also the cell key. Share its immutable
            // string with the ordered map rather than retaining a duplicate.
            return putInCell(key,edit,edit.depth()==0?key:edit.cellKey());
        }
        private Protocol.Edit putInCell(String key,Protocol.Edit edit,String cell) {
            writable(cell).add(key);
            revision++;
            return ordered.put(key,edit);
        }
        @Override public Protocol.Edit remove(Object key) {
            var old = ordered.remove(key);
            if (old != null) {
                revision++;
                var keys = writable(old.cellKey()); keys.remove(key);
                if (keys.isEmpty()) cells.remove(old.cellKey());
            }
            return old;
        }
        @Override public void clear() { if(!ordered.isEmpty())revision++; ordered.clear(); cells.clear(); }
        private void rememberCell(Protocol.CellState state) {
            if(state.tree().size()!=RESOLUTION)throw new IllegalArgumentException("Invalid world cell resolution");
            String cell=state.x()+","+state.y()+","+state.z();
            var old=cells.remove(cell);
            if(old!=null)for(var key:old.snapshot())ordered.remove(key);
            // A complete cell starts with air, followed by nonoverlapping octree leaves.
            // No coverage scan is needed between those leaves. Full-cell leaves share
            // the prefix key; replacing its value retains the same final replay order.
            // Both strings are derived here from this immutable cell and its emitted
            // edits; external map keys still pass public put validation.
            state.forEachEdit(edit->putInCell(edit.depth()==0?cell:edit.key(),edit,cell));
        }
        private void rememberCell(CellReplay replay) {
            var old=cells.remove(replay.cell);
            if(old!=null)for(var key:old.snapshot())ordered.remove(key);
            for(int i=0;i<replay.edits.length;i++) {
                ordered.put(replay.keys[i],replay.edits[i]);revision++;
            }
            // The replay's owner token can never equal a history owner. Later
            // edits clone this index through writable(), protecting every reuse.
            cells.put(replay.cell,replay.index);
        }
        private void remember(Protocol.Edit edit) {
            var keys = cells.get(edit.cellKey());
            if (keys != null) for (var key : keys.snapshot()) {
                var old = ordered.get(key);
                if (covered(old,edit)) remove(key);
            }
            put(edit.key(),edit);
        }
    }
    /** Immutable replay records/keys and cell index. No source geometry is retained. */
    public static final class CellReplay {
        private final String cell;
        private final String[] keys;
        private final Protocol.Edit[] edits;
        private final History.CellKeys index;
        private CellReplay(String cell,String[] keys,Protocol.Edit[] edits,History.CellKeys index) {
            this.cell=cell;this.keys=keys;this.edits=edits;this.index=index;
        }
        public String cellKey(){return cell;}
        /** Includes the clear record and a possible full-cell replacement of that same key. */
        public int editCount(){return edits.length;}
    }
    /** Prepare from stable geometry on a worker. Failure cannot change a history. */
    public static CellReplay prepareCellReplay(Protocol.CellState state) {
        Objects.requireNonNull(state,"state");
        if(state.tree().size()!=RESOLUTION)throw new IllegalArgumentException("Invalid world cell resolution");
        String cell=state.x()+","+state.y()+","+state.z();
        var keys=new ArrayList<String>();var edits=new ArrayList<Protocol.Edit>();
        var index=new History.CellKeys(new Object(),null);
        state.forEachEdit(edit->{
            String key=edit.depth()==0?cell:edit.key();keys.add(key);edits.add(edit);index.add(key);
        });
        return new CellReplay(cell,keys.toArray(String[]::new),edits.toArray(Protocol.Edit[]::new),index);
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
    // Immutable roots give bounded-path updates and stable iteration without map cloning.
    private ChunkDirectory<CellBucket> chunks=new ChunkDirectory<>(Integer.MAX_VALUE);
    private record ChunkLookup(ChunkDirectory<CellBucket> root,int x,int y,int z,CellBucket bucket) {}
    private volatile ChunkLookup lookup;
    // Admission hints only. Races can cause extra/missed cache writes, never a hit.
    // No root or bucket is retained here; the immutable entry validates both.
    private int previousLookupX,previousLookupY,previousLookupZ;
    private boolean previousLookup;
    private CellBucket chunk(ChunkPos position) {
        if(position==null)return null;
        return chunk(position.chunkX(),position.chunkY(),position.chunkZ());
    }
    private CellBucket chunk(int x,int y,int z) {
        var root=chunks;var cached=lookup;
        if(cached!=null&&cached.root==root&&cached.x==x&&cached.y==y&&cached.z==z)return cached.bucket;
        var bucket=root.get(x,y,z);
        // Admit a repeated chunk, not a fraction of random cold reads. Always use
        // this read's root/key/bucket, even if concurrent hints formed a false match.
        if(previousLookup&&previousLookupX==x&&previousLookupY==y&&previousLookupZ==z)
            lookup=new ChunkLookup(root,x,y,z,bucket);
        previousLookupX=x;previousLookupY=y;previousLookupZ=z;previousLookup=true;
        return bucket;
    }
    private void putChunk(ChunkPos position,CellBucket bucket){chunks=chunks.with(position,bucket);lookup=null;previousLookup=false;}
    private Object owner=new Object();
    private long geometryRevision;

    public WorldVoxels(Terrain terrain) {
        this.terrain = terrain;
    }

    /** Owner-thread snapshot: share frozen cells and clone a bucket on its next edit. */
    public WorldVoxels snapshot() {
        var copy=new WorldVoxels(terrain);
        copy.chunks=chunks;copy.geometryRevision=geometryRevision;
        // Neither world may mutate a bucket owned before this sharing boundary.
        owner=new Object();
        return copy;
    }

    /** Owner-only bounded sharing boundary for a selected structure region. */
    public WorldVoxels snapshot(Collection<ChunkPos> positions) {
        if(positions.size()>128)throw new IllegalArgumentException("Structure snapshot batch exceeds 128 chunks");
        var copy=new WorldVoxels(terrain);copy.geometryRevision=geometryRevision;
        for(var position:positions){var bucket=chunk(Objects.requireNonNull(position));if(bucket!=null)copy.putChunk(position,bucket);}
        owner=new Object();return copy;
    }

    /** Check caller admission budgets before capturing the chunk root on the owner. */
    public CellSnapshot cellSnapshot(History history,int maxCells,int maxChunks) {
        Objects.requireNonNull(history);
        if(maxCells<0||maxChunks<0)throw new IllegalArgumentException("Negative cell snapshot budget");
        if(history.cells.size()>maxCells||chunks.size()>maxChunks)throw new IllegalArgumentException("Cell snapshot budget exceeded");
        return cellSnapshot(history);
    }
    /** Owner-thread capture for indexed cell persistence. Every live read and final
     * unchanged check must use this same owner thread and its authority lock. */
    public CellSnapshot cellSnapshot(History history) {
        Objects.requireNonNull(history);long terrainRevision=terrain.geometryRevision();
        if((terrainRevision&1)!=0)throw new ConcurrentModificationException("Terrain changing during cell capture");
        var result=new CellSnapshot(this,history,snapshot(),terrainRevision);
        if(!result.unchanged(this,history))throw new ConcurrentModificationException("World changed during cell capture");
        return result;
    }
    /** Start on the owner. Each advance copies at most 128 chunk entries; no
     * detached handle escapes until all entries and revision fences pass. */
    public CellSnapshotBuilder beginCellSnapshot(History history,int maxCells,int maxChunks) {
        Objects.requireNonNull(history);
        if(maxCells<0||maxChunks<0)throw new IllegalArgumentException("Negative cell snapshot budget");
        if(history.cells.size()>maxCells||chunks.size()>maxChunks)throw new IllegalArgumentException("Cell snapshot budget exceeded");
        return new CellSnapshotBuilder(this,history);
    }
    public static final class CellSnapshotBuilder {
        private final Thread ownerThread=Thread.currentThread();
        private final WorldVoxels source,detached;
        private final History history;
        private final long worldRevision,historyRevision,terrainRevision;
        private final Iterator<Map.Entry<ChunkPos,CellBucket>> entries;
        private CellSnapshot result;
        private CellSnapshotBuilder(WorldVoxels source,History history) {
            this.source=source;this.history=history;
            worldRevision=source.geometryRevision;historyRevision=history.revision();terrainRevision=source.terrain.geometryRevision();
            if((terrainRevision&1)!=0)throw new ConcurrentModificationException("Terrain changing during cell capture");
            detached=new WorldVoxels(source.terrain);detached.geometryRevision=worldRevision;
            // Freeze ownership before sharing even the first bucket.
            source.owner=new Object();entries=source.chunks.iterator();
        }
        public CellSnapshot advance(WorldVoxels current,History currentHistory) {
            if(Thread.currentThread()!=ownerThread)throw new IllegalStateException("Cell snapshot builder requires owner thread");
            if(current!=source||currentHistory!=history||source.geometryRevision!=worldRevision
                    ||history.revision()!=historyRevision||source.terrain.geometryRevision()!=terrainRevision)
                throw new ConcurrentModificationException("World/history changed during cell capture");
            if(result!=null)return result;
            for(int i=0;i<128&&entries.hasNext();i++) {
                var entry=entries.next();detached.putChunk(entry.getKey(),entry.getValue());
            }
            if(entries.hasNext())return null;
            result=new CellSnapshot(source,history,detached,terrainRevision);
            return result;
        }
    }
    /** Detached geometry reads may run on a worker. A candidate must still pass an
     * owner-side unchanged check immediately before publication; this handle never publishes. */
    public static final class CellSnapshot {
        private final Thread owner=Thread.currentThread();
        private final WorldVoxels source,detached;
        private final History history;
        private final long worldRevision,historyRevision,terrainRevision;
        private final History.CellCopyCursor cursor;
        private CellSnapshot(WorldVoxels source,History history,WorldVoxels detached,long terrainRevision) {
            this.source=source;this.history=history;this.detached=detached;this.terrainRevision=terrainRevision;
            worldRevision=source.geometryRevision;historyRevision=history.revision();cursor=history.cellCopyCursor();
        }
        public long terrainSeed(){return detached.terrain.seed;}
        public int terrainVersion(){return detached.terrain.version;}
        private void owner(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Cell snapshot validation requires owner thread");}
        private void terrain(){if(detached.terrain.geometryRevision()!=terrainRevision)throw new ConcurrentModificationException("Terrain changed during cell capture");}
        public boolean unchanged(WorldVoxels current,History currentHistory) {
            owner();return current==source&&currentHistory==history&&source.geometryRevision==worldRevision
                    &&history.revision()==historyRevision&&source.terrain.geometryRevision()==terrainRevision;
        }
        public List<CellPosition> nextBatch(WorldVoxels current,History currentHistory) {
            if(!unchanged(current,currentHistory))throw new ConcurrentModificationException("World/history changed during cell capture");
            return cursor.nextBatch();
        }
        /** Enumerate stored overrides without consulting flat replay history. Create and
         * consume each cursor on one worker. Explicit air is retained; implicit terrain
         * is absent. Final owner unchanged validation is still required before publication. */
        public WorldCellCursor worldCells(int maxCells) {
            if(maxCells<0)throw new IllegalArgumentException("Negative world cell budget");
            terrain();return new WorldCellCursor(maxCells);
        }
        public final class WorldCellCursor {
            private final Thread reader=Thread.currentThread();
            private final int maxCells;
            private final Iterator<Map.Entry<ChunkPos,CellBucket>> chunks=detached.chunks.iterator();
            private Iterator<Map.Entry<Integer,SparseVoxelOctree>> cells=Collections.emptyIterator();
            private ChunkPos position;
            private int emitted;
            private boolean failed;
            private WorldCellCursor(int maxCells){this.maxCells=maxCells;}
            public List<Protocol.CellState> nextBatch() {
                if(Thread.currentThread()!=reader)throw new IllegalStateException("World cell cursor requires its reader thread");
                if(failed)throw new IllegalStateException("World cell cursor failed; create a new capture");
                try {
                    terrain();var result=new ArrayList<Protocol.CellState>();
                    while(result.size()<1024) {
                        if(!cells.hasNext()) {
                            if(!chunks.hasNext())break;
                            var entry=chunks.next();position=entry.getKey();var bucket=entry.getValue();
                            cells=bucket.cells!=null?bucket.cells.entrySet().iterator():bucket.singleKey<0?Collections.emptyIterator()
                                    :Collections.singletonMap(bucket.singleKey,bucket.singleTree).entrySet().iterator();
                            continue;
                        }
                        if(emitted>=maxCells)throw new IllegalArgumentException("World cell snapshot budget exceeded");
                        var cell=cells.next();int index=cell.getKey();
                        int x=position.chunkX()*16+(index&15),y=position.chunkY()*16+(index>>>8),z=position.chunkZ()*16+((index>>>4)&15);
                        if(!new Protocol.Edit(x,y,z,0).valid())throw new IllegalArgumentException("Invalid world cell position");
                        result.add(new Protocol.CellState(x,y,z,cell.getValue()));emitted++;
                    }
                    terrain();return List.copyOf(result);
                }catch(RuntimeException failure){failed=true;throw failure;}
            }
        }
        /** Caller supplies a position from nextBatch; the returned cell tree is frozen. */
        public Protocol.CellState cell(CellPosition position) {
            Objects.requireNonNull(position);
            if(!new Protocol.Edit(position.x(),position.y(),position.z(),0).valid())throw new IllegalArgumentException("Invalid world cell position");
            terrain();var tree=detached.cell(position.x(),position.y(),position.z()).copy().freeze();terrain();
            return new Protocol.CellState(position.x(),position.y(),position.z(),tree);
        }
    }

    /** Capture only affected buckets under the world authority lock. Stage on one worker;
     * merge under that same lock. No live cell changes occur during capture/staging. */
    public CellPatch captureCellPatch(Collection<ChunkPos> positions) {
        var staged=new WorldVoxels(terrain);var stamps=new HashMap<ChunkPos,Object>();
        for(var position:positions) {
            stamps.put(position,chunkStamp(position));var bucket=chunk(position);
            if(bucket!=null)staged.putChunk(position,bucket);
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
            var result=current.snapshot();for(var entry:staged.chunks)result.putChunk(entry.getKey(),entry.getValue());result.geometryRevision++;
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
        var bucket=chunk(position);
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

    public void clear(){if(chunks.size()!=0)geometryRevision++;chunks=chunks.cleared();lookup=null;previousLookup=false;}

    private SparseVoxelOctree editedCell(int x,int y,int z) {
        var bucket=chunk(Math.floorDiv(x,16),Math.floorDiv(y,16),Math.floorDiv(z,16));
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

    /** Conservative capture hint: implicit terrain is air only outside its full height range. */
    public boolean emptyChunk(ChunkPos position) {
        long minY=(long)position.chunkY()*16;
        if(minY<=Terrain.MAX_Y&&minY+15>=Terrain.MIN_Y)return false;
        var bucket=chunk(position);if(bucket==null)return true;
        if(bucket.cells==null)return bucket.singleKey<0||bucket.singleTree.occupied()==0;
        for(var tree:bucket.cells.values())if(tree.occupied()!=0)return false;
        return true;
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

    /** Detached immutable cell image. Geometry/material policy remains caller-owned. */
    public static final class CellImage {
        private final SparseVoxelOctree tree;
        private CellImage(SparseVoxelOctree tree){this.tree=tree;}
        public SparseVoxelOctree tree(){return tree;}
    }
    /** Worker preparation. Never freezes the caller or retains its mutable wrapper. */
    public static CellImage prepareCellImage(SparseVoxelOctree source) {
        Objects.requireNonNull(source,"source");
        if(source.size()!=RESOLUTION)throw new IllegalArgumentException("Invalid world cell resolution");
        return new CellImage(source.copy().freeze());
    }
    /** Owner installation shares only a factory-owned frozen image; later edits fork. */
    public void installCellImage(int x,int y,int z,CellImage image) {
        storeCell(x,y,z,Objects.requireNonNull(image,"image").tree);
    }
    /** Replace one validated world cell without replaying its leaves. Owner thread only. */
    public void replaceCell(int x,int y,int z,SparseVoxelOctree tree) {
        Objects.requireNonNull(tree,"tree");
        if(tree.size()!=RESOLUTION)throw new IllegalArgumentException("Invalid world cell resolution");
        // Copy the wrapper so later caller edits cannot change this world or freeze the caller.
        storeCell(x,y,z,tree.copy().freeze());
    }
    private void storeCell(int x,int y,int z,SparseVoxelOctree stored) {
        var position=ChunkPos.fromBlock(x,y,z);
        var bucket=chunk(position);
        if(bucket==null||bucket.owner!=owner) {
            bucket=new CellBucket(owner,bucket);
            putChunk(position,bucket);
        }
        bucket.put(localCell(x,y,z),stored);geometryRevision++;
    }

    /** Preserve replay order and remove overrides fully covered by the new region. */
    /** Replace a complete cell's canonical replay history without repeated coverage scans. */
    public static void rememberCell(Map<String,Protocol.Edit> history,Protocol.CellState state) {
        if(state.tree().size()!=RESOLUTION)throw new IllegalArgumentException("Invalid world cell resolution");
        if(history instanceof History indexed){indexed.rememberCell(state);return;}
        for(var edit:state.edits())remember(history,edit);
    }
    /** Reuse immutable records and an immutable COW index without rebuilding their keys. */
    public static void rememberCell(Map<String,Protocol.Edit> history,CellReplay replay) {
        Objects.requireNonNull(replay,"replay");
        if(history instanceof History indexed){indexed.rememberCell(replay);return;}
        for(var edit:replay.edits)remember(history,edit);
    }

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
