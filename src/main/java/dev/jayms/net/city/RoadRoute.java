package dev.jayms.net.city;

import java.util.ArrayList;
import java.util.List;

/** Roads connect the selected grid cells directly at any heading. */
public final class RoadRoute {
    private RoadRoute() {}

    /** Rail tracks still use cardinal legs. */
    public static List<Polygon.Point> railPoints(List<Polygon.Point> endpoints) {
        var route = new ArrayList<Polygon.Point>();
        for (var b : points(endpoints)) {
            if (!route.isEmpty()) {
                var a = route.get(route.size() - 1);
                if (a.x() != b.x() && a.z() != b.z()) route.add(new Polygon.Point(b.x(), a.z()));
            }
            route.add(b);
        }
        return List.copyOf(route);
    }

    public static List<Polygon.Point> points(List<Polygon.Point> endpoints) {
        var route = new ArrayList<Polygon.Point>();
        for (var endpoint : endpoints) {
            var b = new Polygon.Point((float) Math.floor(endpoint.x()), (float) Math.floor(endpoint.z()));
            route.add(b);
        }
        return List.copyOf(route);
    }
}
