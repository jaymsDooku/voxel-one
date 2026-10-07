package dev.jayms.net.city;

import dev.jayms.net.*;
import dev.jayms.World;
import java.util.*;

/** Automatic local cargo service. Saved city time gives identical poses on reload and clients. */
public final class ShippingRoutes {
    public static final int MAX_PORTS=16, MAX_DISTANCE=2048, CLEARANCE=13;
    public static final double SPEED=4, DWELL=8;
    public record Point(float x,float z) {}
    public record Route(int origin,int destination,List<Point> points,double length) {
        public Route { points=List.copyOf(points); }
    }
    public record Ship(int origin,int destination,float x,float z,float yaw,boolean sailing) {}
    private World world;
    private long revision;
    private List<CityFrame.Building> ports=List.of();
    private List<Route> cached=List.of();
    public List<Route> routes(CityFrame city,World ground) {
        var next=city.buildings().stream().filter(b->b.type()==SpecialBuildings.PORT)
                .sorted(Comparator.comparingInt(CityFrame.Building::x).thenComparingInt(CityFrame.Building::id))
                .limit(MAX_PORTS).toList();
        if(ground==world&&ground.editsVersion()==revision&&next.equals(ports))return cached;
        var terrain=ground.terrain();
        var result=new ArrayList<Route>();
        // Neighbor links form a bounded coastal network, without crossing routes at each port.
        for(int i=1;i<next.size();i++) {
            var a=next.get(i-1);var b=next.get(i);
            if(Math.hypot(a.x()-b.x(),a.z()-b.z())>MAX_DISTANCE)continue;
            var start=new Point(a.x()+3,a.z()+34);var end=new Point(b.x()+3,b.z()+34);
            float offshore=Math.max(300,Math.max(start.z(),end.z())+16);
            var points=List.of(start,new Point(start.x(),offshore),new Point(end.x(),offshore),end);
            double length=0;boolean clear=true;
            for(int j=1;j<points.size();j++) {
                var p=points.get(j-1);var q=points.get(j);
                length+=Math.hypot(p.x()-q.x(),p.z()-q.z());
                // Axis-aligned corridor, including turning circles and hull footprint.
                for(int x=(int)Math.min(p.x(),q.x())-CLEARANCE;x<=Math.max(p.x(),q.x())+CLEARANCE&&clear;x++)
                    for(int z=(int)Math.min(p.z(),q.z())-CLEARANCE;z<=Math.max(p.z(),q.z())+CLEARANCE;z++)
                        if(!terrain.ocean(x,z)||!clearColumn(ground,x,z)) {clear=false;break;}
            }
            if(clear&&length>0)result.add(new Route(a.id(),b.id(),points,length));
        }
        world=ground;revision=ground.editsVersion();ports=next;
        return cached=List.copyOf(result);
    }
    private static boolean clearColumn(World world,int x,int z) {
        if(world.sample(x,14,z)!=Blocks.WATER)return false;
        // Include the hull, cargo and mast, including partial voxel obstructions.
        for(int y=15;y<=23;y++)if(world.sample(x,y,z)!=0)return false;
        return true;
    }
    public List<Ship> ships(CityFrame city,World ground) {
        return routes(city,ground).stream().map(r->sample(r,city.elapsed())).toList();
    }
    public static Ship sample(Route r,double elapsed) {
        double travel=r.length()/SPEED,period=2*(travel+DWELL);
        double clock=Math.max(0,elapsed)%period;
        boolean reverse=clock>=travel+DWELL;
        double leg=reverse?clock-travel-DWELL:clock;
        boolean sailing=leg>=DWELL;
        double distance=sailing?Math.min(r.length(),(leg-DWELL)*SPEED):0;
        var points=new ArrayList<>(r.points());if(reverse)Collections.reverse(points);
        for(int i=1;i<points.size();i++) {
            var a=points.get(i-1);var b=points.get(i);double length=Math.hypot(b.x()-a.x(),b.z()-a.z());
            if(length==0)continue;
            if(distance<=length||i==points.size()-1) {
                float t=(float)Math.min(1,distance/length);
                return new Ship(r.origin(),r.destination(),a.x()+(b.x()-a.x())*t,a.z()+(b.z()-a.z())*t,
                        (float)Math.atan2(b.x()-a.x(),b.z()-a.z()),sailing);
            }
            distance-=length;
        }
        throw new IllegalArgumentException("Empty shipping route");
    }
}
