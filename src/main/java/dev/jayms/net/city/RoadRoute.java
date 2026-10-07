package dev.jayms.net.city;

import java.util.ArrayList;
import java.util.List;

/** Roads follow the selected cells along X, then Z, with one right-angle bend. */
public final class RoadRoute {
    private RoadRoute() {}

    public static List<Polygon.Point> points(List<Polygon.Point> endpoints) {
        var route = new ArrayList<Polygon.Point>();
        for (var endpoint : endpoints) {
            var b = new Polygon.Point((float) Math.floor(endpoint.x()), (float) Math.floor(endpoint.z()));
            if (!route.isEmpty()) {
                var a = route.get(route.size() - 1);
                if (a.x() != b.x() && a.z() != b.z())
                    route.add(new Polygon.Point(b.x(), a.z()));
            }
            route.add(b);
        }
        return List.copyOf(route);
    }
}
