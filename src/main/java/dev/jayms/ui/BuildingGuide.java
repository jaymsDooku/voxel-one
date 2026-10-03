package dev.jayms.ui;

import dev.jayms.net.city.CityFrame;
import dev.jayms.net.city.Polygon;
import java.util.ArrayList;
import java.util.List;

/** World-space geometry for the planning overlay. Distances are in blocks. */
public final class BuildingGuide {
    private BuildingGuide() {}
    static final int[] RADII = {30, 60};

    public static List<Polygon.Point> targets(Polygon.Point origin) {
        var result = new ArrayList<Polygon.Point>();
        for (int radius : RADII) {
            addTarget(result, origin.x() + radius, origin.z());
            addTarget(result, origin.x() - radius, origin.z());
            addTarget(result, origin.x(), origin.z() + radius);
            addTarget(result, origin.x(), origin.z() - radius);
        }
        return List.copyOf(result);
    }

    private static void addTarget(List<Polygon.Point> result, float x, float z) {
        try {
            result.add(new Polygon.Point(x, z));
        } catch (IllegalArgumentException ignored) {
            // The guide may extend beyond city limits; those points cannot be selected.
        }
    }

    static float distanceSquared(Polygon.Point a, Polygon.Point b) {
        float dx = a.x() - b.x(), dz = a.z() - b.z();
        return dx * dx + dz * dz;
    }

    /** Snap to exposed road-cell edges, so an outward zone does not cover road cells. */
    public static Polygon.Point snapRoad(Polygon.Point raw, CityFrame city) {
        var cells = new java.util.HashSet<Polygon.Cell>();
        boolean onRoad = false;
        for (var road : city.roads()) {
            cells.add(new Polygon.Cell(road.x(), road.z()));
            if (raw.x() >= road.x() && raw.x() <= road.x() + 1
                    && raw.z() >= road.z() && raw.z() <= road.z() + 1) onRoad = true;
        }
        if (!onRoad) return raw;
        Polygon.Point best = raw;
        float distance = Float.POSITIVE_INFINITY;
        // Iterate the stable frame order for deterministic ties. Only exposed edges count:
        // internal cell seams and street centerlines are not usable zone boundaries.
        for (var road : city.roads()) {
            int x = road.x(), z = road.z();
            float alongX = Math.max(x, Math.min(x + 1, raw.x()));
            float alongZ = Math.max(z, Math.min(z + 1, raw.z()));
            float[][] edges = {{x, alongZ}, {x + 1, alongZ}, {alongX, z}, {alongX, z + 1}};
            int[][] neighbours = {{x - 1, z}, {x + 1, z}, {x, z - 1}, {x, z + 1}};
            for (int i = 0; i < 4; i++) {
                if (cells.contains(new Polygon.Cell(neighbours[i][0], neighbours[i][1]))) continue;
                float dx = raw.x() - edges[i][0], dz = raw.z() - edges[i][1];
                float d = dx * dx + dz * dz;
                if (d < distance) {
                    try {
                        best = new Polygon.Point(edges[i][0], edges[i][1]);
                        distance = d;
                    } catch (IllegalArgumentException ignored) {
                        // Ignore edges outside the selectable city bounds.
                    }
                }
            }
        }
        return best;
    }
}
