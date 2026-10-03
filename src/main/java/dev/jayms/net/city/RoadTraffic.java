package dev.jayms.net.city;

import dev.jayms.net.city.Polygon.Cell;

import java.util.*;

/** Road centre topology and right-hand, single-file travel lanes. Derived from saved streets. */
public final class RoadTraffic {
    /**
     * Runtime lane positions may lie half a cell beyond the construction control-point boundary.
     */
    public record Waypoint(float x, float z) {
        public Waypoint {
            if (!Float.isFinite(x) || !Float.isFinite(z))
                throw new IllegalArgumentException("Invalid traffic waypoint");
        }
    }

    private final Map<Cell, Set<Cell>> edges = new LinkedHashMap<>();

    public RoadTraffic(List<CityAddresses.Street> streets) {
        for (var street : streets) {
            for (int i = 1; i < street.route().size(); i++) {
                var a = street.route().get(i - 1);
                var b = street.route().get(i);
                Cell c = new Cell((int) Math.floor(a.x()), (int) Math.floor(a.z()));
                Cell end = new Cell((int) Math.floor(b.x()), (int) Math.floor(b.z()));
                edges.computeIfAbsent(c, k -> new LinkedHashSet<>());
                while (!c.equals(end)) {
                    Cell next =
                            c.x() != end.x()
                                    ? new Cell(c.x() + Integer.signum(end.x() - c.x()), c.z())
                                    : new Cell(c.x(), c.z() + Integer.signum(end.z() - c.z()));
                    edges.computeIfAbsent(c, k -> new LinkedHashSet<>()).add(next);
                    edges.computeIfAbsent(next, k -> new LinkedHashSet<>()).add(c);
                    c = next;
                }
            }
        }
    }

    public boolean center(Cell c) {
        return edges.containsKey(c);
    }

    public boolean connected(Cell a, Cell b) {
        return edges.getOrDefault(a, Set.of()).contains(b);
    }

    public Cell nearest(float x, float z) {
        return edges.keySet().stream()
                .min(
                        Comparator.comparingDouble(
                                c -> Math.pow(c.x() + .5 - x, 2) + Math.pow(c.z() + .5 - z, 2)))
                .orElse(null);
    }

    /** Offset each directed road segment. Mitered corners keep turns on their own side. */
    public List<Waypoint> lanes(List<Cell> route, boolean mounted) {
        var result = new ArrayList<Waypoint>();
        float offset = mounted ? .5f : 1.25f;
        for (int i = 0; i < route.size(); i++) {
            Cell c = route.get(i);
            Cell previous = i > 0 ? route.get(i - 1) : c;
            Cell next = i + 1 < route.size() ? route.get(i + 1) : c;
            boolean incoming = connected(previous, c), outgoing = connected(c, next);
            int dx = outgoing ? next.x() - c.x() : incoming ? c.x() - previous.x() : 0;
            int dz = outgoing ? next.z() - c.z() : incoming ? c.z() - previous.z() : 0;
            float ox = -dz * offset, oz = dx * offset;
            if (incoming
                    && outgoing
                    && (dx != c.x() - previous.x() || dz != c.z() - previous.z())) {
                ox += -(c.z() - previous.z()) * offset;
                oz += (c.x() - previous.x()) * offset;
            }
            result.add(new Waypoint(c.x() + .5f + ox, c.z() + .5f + oz));
        }
        // A pavement miter can extend beyond the neighbouring cell samples. Trim those
        // samples on both legs rather than walking past the corner and reversing into it.
        boolean[] trimmed = new boolean[route.size()];
        for (int i = 1; i + 1 < route.size(); i++) {
            Cell previous = route.get(i - 1), c = route.get(i), next = route.get(i + 1);
            int ix = c.x() - previous.x(), iz = c.z() - previous.z();
            int ox = next.x() - c.x(), oz = next.z() - c.z();
            if (!connected(previous, c) || !connected(c, next) || ix * oz == iz * ox) continue;
            Waypoint corner = result.get(i);
            for (int j = i - 1; j >= 0; j--) {
                Cell sample = route.get(j);
                if ((c.x() - sample.x()) * iz != (c.z() - sample.z()) * ix) break;
                Waypoint point = result.get(j);
                if ((point.x() - corner.x()) * ix + (point.z() - corner.z()) * iz <= 0) break;
                trimmed[j] = true;
            }
            for (int j = i + 1; j < route.size(); j++) {
                Cell sample = route.get(j);
                if ((sample.x() - c.x()) * oz != (sample.z() - c.z()) * ox) break;
                Waypoint point = result.get(j);
                if ((point.x() - corner.x()) * ox + (point.z() - corner.z()) * oz >= 0) break;
                trimmed[j] = true;
            }
        }
        var forward = new ArrayList<Waypoint>();
        for (int i = 0; i < result.size(); i++) if (!trimmed[i]) forward.add(result.get(i));
        return forward;
    }

    /** Reserve a merge before perpendicular streams enter each other's stopping space. */
    public static boolean yields(
            int id,
            float x,
            float z,
            Collection<Waypoint> route,
            int otherId,
            float otherX,
            float otherZ,
            Collection<Waypoint> otherRoute) {
        double travelled = 0, px = x, pz = z;
        for (Waypoint point : route) {
            double dx = point.x() - px, dz = point.z() - pz;
            double length = Math.hypot(dx, dz);
            if (length < .0001) continue;
            double remaining = Math.min(length, 2 - travelled);
            dx *= remaining / length;
            dz *= remaining / length;
            double otherTravelled = 0, ox = otherX, oz = otherZ;
            for (Waypoint other : otherRoute) {
                double odx = other.x() - ox, odz = other.z() - oz;
                double otherLength = Math.hypot(odx, odz);
                if (otherLength < .0001) continue;
                double otherRemaining = Math.min(otherLength, 2 - otherTravelled);
                odx *= otherRemaining / otherLength;
                odz *= otherRemaining / otherLength;
                double cross = dx * odz - dz * odx;
                if (Math.abs(cross) > .0001) {
                    double t = ((ox - px) * odz - (oz - pz) * odx) / cross;
                    double u = ((ox - px) * dz - (oz - pz) * dx) / cross;
                    if (t >= 0 && t <= 1 && u >= 0 && u <= 1) {
                        double arrival = travelled + t * remaining;
                        double otherArrival = otherTravelled + u * otherRemaining;
                        if (otherArrival < arrival - .001
                                || Math.abs(otherArrival - arrival) <= .001 && otherId < id)
                            return true;
                    }
                }
                otherTravelled += otherRemaining;
                if (otherTravelled >= 2) break;
                ox = other.x();
                oz = other.z();
            }
            travelled += remaining;
            if (travelled >= 2) break;
            px = point.x();
            pz = point.z();
        }
        return false;
    }

    /** Swept spacing prevents tunnelling; overlapping spawns may separate, but never approach. */
    public static boolean blocks(
            float x, float z, float nx, float nz, float otherX, float otherZ, float gap) {
        double dx = nx - x, dz = nz - z, length = dx * dx + dz * dz;
        if (length < 1e-12) return false;
        double t = ((otherX - x) * dx + (otherZ - z) * dz) / length;
        if (t <= 0) return false;
        t = Math.min(1, t);
        return Math.hypot(otherX - x - t * dx, otherZ - z - t * dz) < gap;
    }
}
