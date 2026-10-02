package dev.jayms.net.city;

import java.io.*;
import java.util.*;

/** Stable street identities and sequential property numbers, independent of company ownership. */
public final class CityAddresses {
    public record Street(int id, String name, List<Polygon.Point> route) {
        public Street {
            route = List.copyOf(route);
        }
    }

    public record Address(int building, int street, int number) {}

    public record State(List<Street> streets, List<Address> addresses) {
        public State {
            streets = List.copyOf(streets);
            addresses = List.copyOf(addresses);
        }

        public String buildingName(int building) {
            var address =
                    addresses.stream().filter(a -> a.building == building).findFirst().orElse(null);
            return address == null
                    ? "Building #" + building
                    : address.number + " " + streetName(address.street);
        }

        public String streetName(int id) {
            return streets.stream()
                    .filter(s -> s.id == id)
                    .map(Street::name)
                    .findFirst()
                    .orElse("Unnamed road");
        }

        public Street nearest(float x, float z) {
            return streets.stream()
                    .min(Comparator.comparingDouble(s -> distance(s, x, z)))
                    .orElse(null);
        }
    }

    private final List<Street> streets = new ArrayList<>();
    private final List<Address> addresses = new ArrayList<>();

    public CityAddresses(State state) {
        streets.addAll(state.streets);
        addresses.addAll(state.addresses);
    }

    public static State empty() {
        return new State(List.of(), List.of());
    }

    private static double distance(Street street, float x, float z) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 1; i < street.route.size(); i++) {
            var a = street.route.get(i - 1);
            var b = street.route.get(i);
            double dx = b.x() - a.x(), dz = b.z() - a.z(), length = dx * dx + dz * dz;
            double t =
                    length == 0
                            ? 0
                            : Math.max(
                                    0, Math.min(1, ((x - a.x()) * dx + (z - a.z()) * dz) / length));
            double ex = x - a.x() - t * dx, ez = z - a.z() - t * dz;
            best = Math.min(best, ex * ex + ez * ez);
        }
        return best;
    }

    private static String generated(int id) {
        String[] names = {
            "Oak Road",
            "River Lane",
            "Cedar Street",
            "Stone Road",
            "Meadow Lane",
            "Pine Street",
            "Willow Road",
            "Maple Lane",
            "Birch Street",
            "Horizon Road",
            "Orchard Lane",
            "Mill Street"
        };
        return names[(id - 1) % names.length]
                + (id > names.length ? " " + ((id - 1) / names.length + 1) : "");
    }

    /** Collinear, touching extensions retain the street name and all existing addresses. */
    public String road(List<Polygon.Point> points) {
        points =
                points.stream()
                        .map(
                                p ->
                                        new Polygon.Point(
                                                (float) Math.floor(p.x()),
                                                (float) Math.floor(p.z())))
                        .toList();
        if (streets.size() >= 512) throw new IllegalArgumentException("Too many named roads");
        if (points.size() == 2) {
            var a = points.get(0);
            var b = points.get(1);
            for (int i = 0; i < streets.size(); i++) {
                var s = streets.get(i);
                if (s.route.size() != 2) continue;
                var c = s.route.get(0);
                var d = s.route.get(1);
                boolean horizontal = a.z() == b.z() && c.z() == d.z() && a.z() == c.z();
                boolean vertical = a.x() == b.x() && c.x() == d.x() && a.x() == c.x();
                if (!horizontal && !vertical) continue;
                float lo = Math.min(horizontal ? a.x() : a.z(), horizontal ? b.x() : b.z());
                float hi = Math.max(horizontal ? a.x() : a.z(), horizontal ? b.x() : b.z());
                float oldLo = Math.min(horizontal ? c.x() : c.z(), horizontal ? d.x() : d.z());
                float oldHi = Math.max(horizontal ? c.x() : c.z(), horizontal ? d.x() : d.z());
                if (lo > oldHi + 2 || hi < oldLo - 2) continue;
                var start =
                        horizontal
                                ? new Polygon.Point(Math.min(lo, oldLo), a.z())
                                : new Polygon.Point(a.x(), Math.min(lo, oldLo));
                var end =
                        horizontal
                                ? new Polygon.Point(Math.max(hi, oldHi), a.z())
                                : new Polygon.Point(a.x(), Math.max(hi, oldHi));
                streets.set(i, new Street(s.id, s.name, List.of(start, end)));
                return s.name;
            }
        }
        int id = streets.stream().mapToInt(Street::id).max().orElse(0) + 1;
        // The road builder walks along X, then Z; store that actual route, including bends.
        var route = new ArrayList<Polygon.Point>();
        route.add(points.get(0));
        for (int i = 1; i < points.size(); i++) {
            var a = points.get(i - 1);
            var b = points.get(i);
            if (a.x() != b.x() && a.z() != b.z()) route.add(new Polygon.Point(b.x(), a.z()));
            route.add(b);
        }
        streets.add(new Street(id, generated(id), route));
        return generated(id);
    }

    public State state(List<CityFrame.Building> buildings) {
        for (var b :
                buildings.stream()
                        .sorted(Comparator.comparingInt(CityFrame.Building::id))
                        .toList()) {
            if (addresses.stream().anyMatch(a -> a.building == b.id())) continue;
            Street street =
                    streets.stream()
                            .min(
                                    Comparator.comparingDouble(
                                            s ->
                                                    Math.min(
                                                            distance(s, b.x() + 2.5f, b.z()),
                                                            distance(
                                                                    s,
                                                                    b.x() + 2.5f,
                                                                    b.z()
                                                                            + StructureBlueprint
                                                                                    .DEPTH))))
                            .orElse(null);
            if (street == null) continue;
            int number =
                    addresses.stream()
                                    .filter(a -> a.street == street.id)
                                    .mapToInt(Address::number)
                                    .max()
                                    .orElse(0)
                            + 1;
            addresses.add(new Address(b.id(), street.id, number));
        }
        return new State(streets, addresses);
    }

    /** Reconstruct center lines from old three-cell-wide roads without changing any world cells. */
    public static State migrate(List<CityFrame.Road> roads, List<CityFrame.Building> buildings) {
        var remaining = new HashSet<Polygon.Cell>();
        for (var r : roads) remaining.add(new Polygon.Cell(r.x(), r.z()));
        var names = new CityAddresses(empty());
        // The historical crossroads has known center lines. Include extensions before tracing
        // branches.
        for (boolean horizontal : new boolean[] {true, false}) {
            if (!remaining.contains(new Polygon.Cell(8, 24))) continue;
            int lo = horizontal ? 8 : 24, hi = lo;
            while (remaining.contains(
                    new Polygon.Cell(horizontal ? lo - 1 : 8, horizontal ? 24 : lo - 1))) lo--;
            while (remaining.contains(
                    new Polygon.Cell(horizontal ? hi + 1 : 8, horizontal ? 24 : hi + 1))) hi++;
            if (hi - lo < 3) continue;
            int start = lo + 1, end = hi - 1;
            names.road(
                    List.of(
                            new Polygon.Point(horizontal ? start : 8, horizontal ? 24 : start),
                            new Polygon.Point(horizontal ? end : 8, horizontal ? 24 : end)));
            // Trace both founding center lines from the complete cell set before removing either.
        }
        for (var s : names.streets) remaining.removeIf(c -> distance(s, c.x(), c.z()) <= 2);
        while (!remaining.isEmpty() && names.streets.size() < 512) {
            List<Polygon.Point> best = null;
            int length = 0;
            for (var c :
                    remaining.stream()
                            .sorted(
                                    Comparator.comparingInt(Polygon.Cell::z)
                                            .thenComparingInt(Polygon.Cell::x))
                            .toList()) {
                for (boolean horizontal : new boolean[] {true, false}) {
                    int n = 0;
                    while (n <= 514
                            && remaining.contains(
                                    new Polygon.Cell(
                                            c.x() + (horizontal ? n : 0),
                                            c.z() + (horizontal ? 0 : n)))) n++;
                    if (n > length) {
                        length = n;
                        best =
                                List.of(
                                        new Polygon.Point(c.x(), c.z()),
                                        new Polygon.Point(
                                                c.x() + (horizontal ? n - 1 : 0),
                                                c.z() + (horizontal ? 0 : n - 1)));
                    }
                }
            }
            var a = best.get(0);
            var b = best.get(1);
            names.road(best);
            remaining.removeIf(
                    c ->
                            c.x() >= Math.min(a.x(), b.x()) - 2
                                    && c.x() <= Math.max(a.x(), b.x()) + 2
                                    && c.z() >= Math.min(a.z(), b.z()) - 2
                                    && c.z() <= Math.max(a.z(), b.z()) + 2);
        }
        return names.state(buildings);
    }

    public static void write(DataOutput out, State state) throws IOException {
        out.writeInt(state.streets.size());
        for (var s : state.streets) {
            out.writeInt(s.id);
            out.writeUTF(s.name);
            out.writeInt(s.route.size());
            for (var p : s.route) {
                out.writeFloat(p.x());
                out.writeFloat(p.z());
            }
        }
        out.writeInt(state.addresses.size());
        for (var a : state.addresses) {
            out.writeInt(a.building);
            out.writeInt(a.street);
            out.writeInt(a.number);
        }
    }

    public static State read(DataInput in, List<CityFrame.Building> buildings) throws IOException {
        var streets = new ArrayList<Street>();
        var ids = new HashSet<Integer>();
        for (int n = count(in, 512); n > 0; n--) {
            int id = in.readInt();
            String name = in.readUTF();
            int count = count(in, 64);
            if (id < 1 || !ids.add(id) || name.isBlank() || name.length() > 48 || count < 2)
                throw new IOException("Invalid street");
            var route = new ArrayList<Polygon.Point>();
            for (int i = 0; i < count; i++) {
                float x = in.readFloat(), z = in.readFloat();
                if (!Float.isFinite(x)
                        || !Float.isFinite(z)
                        || Math.abs(x - 8) > 256
                        || Math.abs(z - 24) > 256) throw new IOException("Invalid street route");
                route.add(new Polygon.Point(x, z));
            }
            streets.add(new Street(id, name, route));
        }
        var addresses = new ArrayList<Address>();
        var properties = new HashSet<Integer>();
        var numbers = new HashSet<String>();
        for (int n = count(in, 512); n > 0; n--) {
            int building = in.readInt(), street = in.readInt(), number = in.readInt();
            if (!properties.add(building)
                    || !ids.contains(street)
                    || number < 1
                    || number > 512
                    || !numbers.add(street + ":" + number)
                    || buildings.stream().noneMatch(b -> b.id() == building))
                throw new IOException("Invalid property address");
            addresses.add(new Address(building, street, number));
        }
        if (!streets.isEmpty() && addresses.size() != buildings.size())
            throw new IOException("Missing property address");
        return new State(streets, addresses);
    }

    private static int count(DataInput in, int maximum) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > maximum) throw new IOException("Invalid address count");
        return n;
    }
}
