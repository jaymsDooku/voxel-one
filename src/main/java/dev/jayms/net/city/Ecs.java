package dev.jayms.net.city;

import java.util.*;

/** Deterministic entity/component storage, reusable by headless game simulations. */
public final class Ecs {
    private int next;
    private final Set<Integer> entities = new LinkedHashSet<>();
    private final Map<Class<?>, Map<Integer, Object>> components = new HashMap<>();

    public int create() {
        int id = ++next;
        entities.add(id);
        return id;
    }

    public void restore(int id) {
        if (id <= 0) throw new IllegalArgumentException("Entity id");
        entities.add(id);
        next = Math.max(next, id);
    }

    public <T> void put(int id, Class<T> type, T value) {
        if (!entities.contains(id)) throw new IllegalArgumentException("Missing entity");
        components.computeIfAbsent(type, k -> new LinkedHashMap<>()).put(id, type.cast(value));
    }

    public <T> T get(int id, Class<T> type) {
        return type.cast(components.getOrDefault(type, Map.of()).get(id));
    }

    public List<Integer> query(Class<?>... types) {
        var result = new ArrayList<Integer>();
        for (int id : entities) {
            boolean match = true;
            for (var type : types)
                if (!components.getOrDefault(type, Map.of()).containsKey(id)) {
                    match = false;
                    break;
                }
            if (match) result.add(id);
        }
        return List.copyOf(result);
    }

    public void remove(int id) {
        entities.remove(id);
        components.values().forEach(m -> m.remove(id));
    }
}
