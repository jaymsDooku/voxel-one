package dev.jayms.net.city.parcel;

import dev.jayms.net.city.Polygon.Cell;
import java.util.*;
import java.util.function.ToDoubleFunction;

/** Raster parcels use world ground cells, not render voxels. Inputs and outputs are immutable. */
public interface ParcelGenerator {
    List<Parcel> generate(Request request);

    record Parcel(int id, Set<Cell> cells) {
        public Parcel { cells = Collections.unmodifiableSet(new LinkedHashSet<>(cells)); }
    }

    record Request(Set<Cell> cells, Set<Cell> frontage, int targetArea, long seed,
                   ToDoubleFunction<Cell> terrainCost, List<Parcel> previous) {
        public Request {
            cells = Collections.unmodifiableSet(new LinkedHashSet<>(cells));
            frontage = Set.copyOf(frontage);
            previous = List.copyOf(previous);
            Objects.requireNonNull(terrainCost);
            if (targetArea < 4 || targetArea > 4096 || cells.size() > 4096)
                throw new IllegalArgumentException("Parcel target must be 4 to 4096 cells; zone maximum 4096");
        }
    }
}
