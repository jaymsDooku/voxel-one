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

    public record LanePoint(Waypoint point,int sourceIndex) {}

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

    /** Join touching road end caps, whose named centre lines can be two cells apart. */
    public RoadTraffic(List<CityAddresses.Street> streets, Set<Cell> roadCells) {
        this(streets);
        var centers=new ArrayList<>(edges.keySet());
        for(var c:centers) for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
            var middle=new Cell(c.x()+d[0],c.z()+d[1]);
            var end=new Cell(c.x()+2*d[0],c.z()+2*d[1]);
            if(!edges.containsKey(end) || !roadCells.contains(middle)) continue;
            edges.computeIfAbsent(c,k->new LinkedHashSet<>()).add(middle);
            edges.computeIfAbsent(middle,k->new LinkedHashSet<>()).add(c);
            edges.get(middle).add(end);
            edges.get(end).add(middle);
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
        return lanePlan(route,mounted).stream().map(LanePoint::point).toList();
    }

    public List<LanePoint> lanePlan(List<Cell> route, boolean mounted) {
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
        var forward = new ArrayList<LanePoint>();
        for (int i = 0; i < result.size(); i++) if (!trimmed[i]) forward.add(new LanePoint(result.get(i),i));
        return forward;
    }

    private record Approach(boolean inLane, double distance) {}

    private static Approach approach(float x, float z, Collection<Waypoint> route) {
        var points=route.iterator();
        Waypoint first=null;
        double distance=Double.POSITIVE_INFINITY;
        while(points.hasNext()) {
            var point=points.next();double d=Math.hypot(point.x()-x,point.z()-z);
            if(d>.0001) {first=point;distance=d;break;}
        }
        if(first==null) return new Approach(false,distance);
        while(points.hasNext()) {
            var next=points.next();double dx=next.x()-first.x(),dz=next.z()-first.z();
            double length=Math.hypot(dx,dz);
            if(length>.0001) return new Approach(
                    Math.abs(dx*(z-first.z())-dz*(x-first.x()))/length<.001,distance);
        }
        return new Approach(true,distance);
    }

    /** Whether a traveller has joined the direction of its next road leg. */
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
        return yields(id,x,z,route,otherId,otherX,otherZ,otherRoute,.5f);
    }

    public static boolean yields(int id,float x,float z,Collection<Waypoint> route,
            int otherId,float otherX,float otherZ,Collection<Waypoint> otherRoute,float width) {
        var own=approach(x,z,route);var otherApproach=approach(otherX,otherZ,otherRoute);
        // One ordering across every conflict avoids mutually reserving different crossings.
        // Travellers already in their lane clear it before newcomers merge into that stream.
        boolean otherPriority=otherApproach.inLane()!=own.inLane() ? otherApproach.inLane()
                : otherApproach.distance()<own.distance()-.001
                || Math.abs(otherApproach.distance()-own.distance())<=.001 && otherId<id;
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
                // Compare angles independently of segment length. A tiny remaining
                // lookahead segment must not turn a real corner into a head-on aisle.
                double parallelTolerance = .0001 * remaining * otherRemaining;
                // Single-file followers keep their longitudinal gap instead of reserving
                // each other's future line. Opposing and crossing bodies reserve an aisle
                // even when their centre lines narrowly miss each other.
                boolean sameDirection=Math.abs(cross)<=parallelTolerance && dx*odx+dz*odz>0;
                if(otherPriority && !sameDirection) {
                    if(Math.abs(cross)>parallelTolerance) {
                        double t=((ox-px)*odz-(oz-pz)*odx)/cross;
                        double u=((ox-px)*dz-(oz-pz)*dx)/cross;
                        if(t>=0 && t<=1 && u>=0 && u<=1) return true;
                    }
                    double clearance=Math.min(
                            Math.min(pointSegment(px,pz,ox,oz,ox+odx,oz+odz),
                                    pointSegment(px+dx,pz+dz,ox,oz,ox+odx,oz+odz)),
                            Math.min(pointSegment(ox,oz,px,pz,px+dx,pz+dz),
                                    pointSegment(ox+odx,oz+odz,px,pz,px+dx,pz+dz)));
                    if(clearance<(width-.00001)*(width-.00001)) return true;
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

    private static double pointSegment(double x,double z,double ax,double az,double bx,double bz) {
        double dx=bx-ax,dz=bz-az,length=dx*dx+dz*dz;
        double t=length==0 ? 0 : Math.max(0,Math.min(1,((x-ax)*dx+(z-az)*dz)/length));
        double ex=x-ax-t*dx,ez=z-az-t*dz;
        return ex*ex+ez*ez;
    }

    /** Swept spacing prevents tunnelling; overlapping spawns may separate, but never approach. */
    public static boolean blocks(
            float x, float z, float nx, float nz, float otherX, float otherZ, float gap) {
        double dx = nx - x, dz = nz - z, length = dx * dx + dz * dz;
        if (length < 1e-12) return false;
        double t = ((otherX - x) * dx + (otherZ - z) * dz) / length;
        if (t <= 0) return false;
        t = Math.min(1, t);
        return Math.hypot(otherX - x - t * dx, otherZ - z - t * dz) < gap - .00001;
    }
}
