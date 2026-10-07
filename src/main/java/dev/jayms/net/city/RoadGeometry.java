package dev.jayms.net.city;

import dev.jayms.net.Blocks;
import java.util.*;

/** Shared road footprint for placement, boundary snapping and section actions. */
public final class RoadGeometry {
    private RoadGeometry() {}

    public static List<Polygon.Point> centers(List<Polygon.Point> points, int type) {
        RoadTypes.validate(type);
        points = RoadRoute.points(points);
        var result = new ArrayList<Polygon.Point>();
        for (int i = 1; i < points.size(); i++) {
            var a = points.get(i - 1); var b = points.get(i);
            int x = (int)Math.floor(a.x()), z = (int)Math.floor(a.z());
            int bx = (int)Math.floor(b.x()), bz = (int)Math.floor(b.z());
            int dx = Math.abs(bx-x), dz = Math.abs(bz-z);
            if (Math.max(dx,dz) > 768) throw new IllegalArgumentException("Road too long: use shorter sections");
            int sx = Integer.signum(bx-x), sz = Integer.signum(bz-z), error = dx-dz;
            while (true) {
                result.add(new Polygon.Point(x,z));
                if (x == bx && z == bz) break;
                int twice = error*2;
                if (twice > -dz) { error -= dz; x += sx; }
                if (twice < dx) { error += dx; z += sz; }
            }
        }
        return result;
    }

    public static Map<Polygon.Cell,Integer> surfaces(List<Polygon.Point> points, int type) {
        points = RoadRoute.points(points);
        var result = new LinkedHashMap<Polygon.Cell,Integer>();
        int radius = RoadTypes.width(type)/2;
        for (int i=1;i<points.size();i++) {
            var a=points.get(i-1); var b=points.get(i);
            boolean alongX = Math.abs(Math.floor(b.x())-Math.floor(a.x())) >= Math.abs(Math.floor(b.z())-Math.floor(a.z()));
            for (var p : centers(List.of(a,b),type)) {
                int x=(int)p.x(), z=(int)p.z();
                for (int offset=-radius;offset<=radius;offset++) {
                    var cell=new Polygon.Cell(x+(alongX?0:offset),z+(alongX?offset:0));
                    if (type==0) for (int end=-1;end<=1;end++)
                        result.put(new Polygon.Cell(cell.x()+(alongX?end:0),cell.z()+(alongX?0:end)),Blocks.DIRT);
                    int surface=type==0?Blocks.DIRT:Blocks.ASPHALT;
                    if (type!=0 && (offset+radius)%2==1) surface=alongX?Blocks.ROAD_LINE_X:Blocks.ROAD_LINE_Z;
                    result.put(cell,surface);
                }
            }
        }
        return result;
    }

    public static Polygon.Point snapZone(Polygon.Point origin, Polygon.Point target, int type, CityFrame city) {
        var path=centers(List.of(origin,target),type);
        Polygon.Point previous = origin;
        for (var center:path) {
            for (var cell:surfaces(List.of(origin,center),type).keySet())
                for (var zone:city.zones())
                    if (zone.polygon().contains(cell.x()+.5f,cell.z()+.5f)) return previous;
            previous = center;
        }
        return target;
    }

    public static int streetAt(CityFrame city, CityFrame.Road road) {
        var cell=new Polygon.Cell(road.x(),road.z());
        var owners=RoadOwnership.forFrame(city).stream()
                .filter(f->f.cells().stream().anyMatch(c->c.cell().equals(cell)))
                .map(RoadOwnership.Footprint::street).collect(java.util.stream.Collectors.toSet());
        return city.addresses().streets().stream().filter(s->owners.contains(s.id()))
                .min(Comparator.comparingDouble(s->CityAddresses.distance(s,road.x(),road.z())))
                .map(CityAddresses.Street::id).orElse(0);
    }

    /** A street retains its exact owned cells even when a crossing paints over them. */
    public static List<CityFrame.Road> section(CityFrame city, int street) {
        var cells=new HashSet<Polygon.Cell>();
        for(var f:RoadOwnership.forFrame(city)) if(f.street()==street)
            for(var c:f.cells()) cells.add(c.cell());
        return city.roads().stream().filter(r->cells.contains(new Polygon.Cell(r.x(),r.z()))).toList();
    }
}
