import dev.jayms.ChunkPos;
import java.util.*;

/** Private 64-shard directory. Not wired into the game. Each mutable wrapper has
 * one owner; snapshot belongs under that owner's lock. Values need their own
 * immutable/COW protocol. Snapshots copy 64 references, not the stored entries. */
public final class SharedHashChunkDirectoryPrototype<V> implements Iterable<Map.Entry<ChunkPos,V>> {
    private static final int SHARDS=64;
    private static final class Shard<V> {
        final Object owner;final LinkedHashMap<ChunkPos,V> entries;
        Shard(Object owner,Shard<V> source){this.owner=owner;entries=source==null?new LinkedHashMap<>():new LinkedHashMap<>(source.entries);}
    }
    private Shard<V>[] shards;
    private Object owner=new Object();
    private int size;
    private long copiedEntries;
    private final int budget;
    @SuppressWarnings("unchecked") public SharedHashChunkDirectoryPrototype(int budget) {
        if(budget<1||budget>1_048_576)throw new IllegalArgumentException("Directory entry budget");
        this.budget=budget;shards=(Shard<V>[])new Shard<?>[SHARDS];
    }
    private SharedHashChunkDirectoryPrototype(SharedHashChunkDirectoryPrototype<V> source) {
        budget=source.budget;size=source.size;shards=source.shards.clone();source.owner=new Object();
    }
    /** Full-coordinate mix spreads record-hash collisions across shards. It does not
     * prove a worst-case shard bound: adversarial keys can still land together. */
    private static int shard(ChunkPos key) {
        long h=(long)key.chunkX()*0x9e3779b185ebca87L^(long)key.chunkY()*0xc2b2ae3d27d4eb4fL^(long)key.chunkZ()*0x165667b19e3779f9L;
        h^=h>>>33;h*=0xff51afd7ed558ccdL;h^=h>>>33;return (int)h&(SHARDS-1);
    }
    public int size(){return size;}
    public SharedHashChunkDirectoryPrototype<V> snapshot(){return new SharedHashChunkDirectoryPrototype<>(this);}
    public V get(ChunkPos key){Objects.requireNonNull(key);var s=shards[shard(key)];return s==null?null:s.entries.get(key);}
    private Shard<V> writable(int index) {
        var s=shards[index];if(s==null||s.owner!=owner){int copied=s==null?0:s.entries.size();s=new Shard<>(owner,s);shards[index]=s;copiedEntries+=copied;}return s;
    }
    public void put(ChunkPos key,V value) {
        Objects.requireNonNull(key);Objects.requireNonNull(value);int index=shard(key);var s=shards[index];var old=s==null?null:s.entries.get(key);
        if(old==value)return;
        if(old==null&&size==budget)throw new IllegalArgumentException("Directory entry budget exceeded");
        writable(index).entries.put(key,value);if(old==null)size++;
    }
    public void remove(ChunkPos key) {
        Objects.requireNonNull(key);int index=shard(key);var s=shards[index];if(s==null||!s.entries.containsKey(key))return;
        var next=writable(index);next.entries.remove(key);size--;if(next.entries.isEmpty())shards[index]=null;
    }
    @SuppressWarnings("unchecked") public void clear(){shards=(Shard<V>[])new Shard<?>[SHARDS];owner=new Object();size=0;}
    /** Iterators are stable across later puts/removes/clear on either wrapper.
     * They freeze shard ownership but do not freeze mutable values. No global
     * insertion order contract: linked iteration occurs within each shard. */
    @Override public Iterator<Map.Entry<ChunkPos,V>> iterator() {
        var captured=snapshot();
        return new Iterator<>() {
            private int index;
            private Iterator<Map.Entry<ChunkPos,V>> entries=Collections.emptyIterator();
            public boolean hasNext(){while(!entries.hasNext()&&index<SHARDS){var s=captured.shards[index++];if(s!=null)entries=s.entries.entrySet().iterator();}return entries.hasNext();}
            public Map.Entry<ChunkPos,V> next(){if(!hasNext())throw new NoSuchElementException();var e=entries.next();return Map.entry(e.getKey(),e.getValue());}
        };
    }
    /** Diagnostic only, walks all shards. A write after snapshot can copy this many
     * entries from one shard; size alone is not a main-thread latency bound. */
    /** Cumulative shard entries copied by this wrapper; snapshots start at zero. */
    public long copiedEntries(){return copiedEntries;}
    public int maxShardSize(){int largest=0;for(var s:shards)if(s!=null)largest=Math.max(largest,s.entries.size());return largest;}
}
