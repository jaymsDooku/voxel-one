package dev.jayms;

import dev.jayms.DistantTerrainPlan.Tile;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A retiring tile and every replacement within its footprint share one dither threshold. */
final class DistantTerrainTransitions {
    static final float DURATION = .4f;
    private Set<Tile> previous = Set.of();
    private final Map<Tile, Float> starts = new HashMap<>();
    private Map<Tile, Tile> sources = Map.of();

    void select(List<Tile> selected, Set<Tile> ready, float now) {
        expire(now);
        var nextStarts = new HashMap<Tile, Float>();
        var nextSources = new HashMap<Tile, Tile>();
        for (Tile tile : selected) {
            Tile previousOwner;
            if (previous.contains(tile)) {
                previousOwner = sources.get(tile);
                if (previousOwner == null || !starts.containsKey(previousOwner)
                        || !ready.contains(previousOwner)) continue;
            } else {
                previousOwner = refinementSource(tile, ready);
            }
            if (previousOwner != null) {
                nextSources.put(tile, previousOwner);
                nextStarts.put(previousOwner, starts.getOrDefault(previousOwner, now));
            }
        }
        starts.clear();
        starts.putAll(nextStarts);
        sources = nextSources;
        previous = Set.copyOf(selected);
    }

    private Tile refinementSource(Tile tile, Set<Tile> ready) {
        Tile ancestor = tile;
        Tile previousOwner = null;
        while (ancestor.size() < DistantTerrainPlan.ROOT_SIZE) {
            int size = ancestor.size() * 2;
            ancestor = new Tile(Math.floorDiv(ancestor.x(), size) * size,
                    Math.floorDiv(ancestor.z(), size) * size, size);
            if (!ready.contains(ancestor)) continue;
            // A second refinement during a fade inherits its original source.
            if (starts.containsKey(ancestor)) return ancestor;
            if (previous.contains(ancestor)) previousOwner = ancestor;
        }
        return previousOwner;
    }

    void expire(float now) {
        if (!starts.isEmpty()) starts.values().removeIf(start -> now - start >= DURATION);
    }

    Set<Tile> parents() {
        return starts.keySet();
    }

    float fraction(Tile tile, float now) {
        Float start = starts.get(sources.getOrDefault(tile, tile));
        return start == null ? 1f : Math.min(1f, (now - start) / DURATION);
    }
}
