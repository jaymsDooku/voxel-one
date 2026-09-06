package dev.jayms.collect;

import java.util.*;

public class MultiMap<K, V> {

    private final Map<K, List<V>> map = new HashMap<>();

    public void put(K key, V value) {
        map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
    }

    public List<V> get(K key) {
        List<V> values = map.get(key);
        return values == null ? List.of() : Collections.unmodifiableList(values);
    }

    public boolean remove(K key, V value) {
        List<V> values = map.get(key);

        if (values == null || !values.remove(value)) {
            return false;
        }

        if (values.isEmpty()) {
            map.remove(key);
        }

        return true;
    }

}
