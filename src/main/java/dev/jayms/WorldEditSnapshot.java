package dev.jayms;

import dev.jayms.net.Protocol;
import dev.jayms.net.WorldVoxels;
import java.util.*;

/** Detached replay roots. Create on the world's owner thread before worker use. */
final class WorldEditSnapshot extends AbstractMap<String, Protocol.Edit> {
    private final Map<ChunkPos, WorldVoxels.History> chunks = new LinkedHashMap<>();
    private final int size;

    /** Sparse chunk roots cost more than flat rows. Eight rows/chunk is a conservative
     * storage choice, not a world admission limit or a latency guarantee. */
    static Map<String, Protocol.Edit> capture(Map<ChunkPos, ? extends Map<String, Protocol.Edit>> source) {
        long rows = 0;
        for (var history : source.values()) rows += history.size();
        if (rows <= 8L * source.size()) {
            var flat = new LinkedHashMap<String, Protocol.Edit>();
            source.values().forEach(flat::putAll);
            return Collections.unmodifiableMap(flat);
        }
        return Collections.unmodifiableMap(new WorldEditSnapshot(source));
    }

    WorldEditSnapshot(Map<ChunkPos, ? extends Map<String, Protocol.Edit>> source) {
        int count = 0;
        for (var entry : source.entrySet()) {
            var history = WorldVoxels.History.copyOf(entry.getValue());
            chunks.put(entry.getKey(), history);
            count = Math.addExact(count, history.size());
        }
        size = count;
    }

    @Override public int size() { return size; }
    @Override public boolean containsKey(Object key) { return get(key) != null; }

    @Override public Protocol.Edit get(Object key) {
        if (!(key instanceof String text)) return null;
        int a = text.indexOf(','), b = text.indexOf(',', a + 1);
        int end = text.indexOf('/');
        if (end < 0) end = text.length();
        if (a < 1 || b <= a + 1 || end <= b + 1) return null;
        try {
            int x = Integer.parseInt(text, 0, a, 10);
            int y = Integer.parseInt(text, a + 1, b, 10);
            int z = Integer.parseInt(text, b + 1, end, 10);
            var history = chunks.get(ChunkPos.fromBlock(x, y, z));
            return history == null ? null : history.get(text);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    @Override public Set<Entry<String, Protocol.Edit>> entrySet() {
        return new AbstractSet<>() {
            @Override public int size() { return size; }
            @Override public Iterator<Entry<String, Protocol.Edit>> iterator() {
                var histories = chunks.values().iterator();
                return new Iterator<>() {
                    private Iterator<Entry<String, Protocol.Edit>> rows = Collections.emptyIterator();
                    @Override public boolean hasNext() {
                        while (!rows.hasNext() && histories.hasNext()) rows = histories.next().entrySet().iterator();
                        return rows.hasNext();
                    }
                    @Override public Entry<String, Protocol.Edit> next() {
                        if (!hasNext()) throw new NoSuchElementException();
                        return rows.next();
                    }
                };
            }
        };
    }
}
