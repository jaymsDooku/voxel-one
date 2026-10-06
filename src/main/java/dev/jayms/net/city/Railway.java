package dev.jayms.net.city;

import java.io.*;
import java.util.*;
import dev.jayms.net.city.Polygon.Cell;

/** Authoritative automatic depot/station service. One train per connected network avoids collisions. */
public final class Railway {
    public record Track(int x, int z, int y) {}
    public record Passenger(int citizen, int destination) {}
    public record Train(int id, int depot, float x, float y, float z, float yaw, float phase,
                        int stop, float dwell, List<Passenger> passengers) {
        public Train { passengers = List.copyOf(passengers); }
    }
    public record State(List<Track> tracks, List<Train> trains) {
        public State { tracks = List.copyOf(tracks); trains = List.copyOf(trains); }
        public static State empty() { return new State(List.of(), List.of()); }
        public void write(DataOutput out) throws IOException {
            out.writeInt(tracks.size());
            for (var r : tracks) { out.writeInt(r.x()); out.writeInt(r.z()); out.writeInt(r.y()); }
            out.writeInt(trains.size());
            for (var t : trains) {
                out.writeInt(t.id()); out.writeInt(t.depot()); out.writeFloat(t.x()); out.writeFloat(t.y());
                out.writeFloat(t.z()); out.writeFloat(t.yaw()); out.writeFloat(t.phase());
                out.writeInt(t.stop()); out.writeFloat(t.dwell()); out.writeInt(t.passengers().size());
                for (var p : t.passengers()) { out.writeInt(p.citizen()); out.writeInt(p.destination()); }
            }
        }
        public static State read(DataInput in, List<CityFrame.Building> buildings, List<CityFrame.Citizen> citizens) throws IOException {
            var tracks = new ArrayList<Track>(); var cells = new HashSet<Cell>();
            for (int i=0,n=count(in,8192); i<n; i++) {
                int x=in.readInt(), z=in.readInt(), y=in.readInt();
                if (Math.abs((long)x-8)>256 || Math.abs((long)z-24)>256 || y < -31 || y > 89 || !cells.add(new Cell(x,z))) throw new IOException("Invalid rail track");
                tracks.add(new Track(x,z,y));
            }
            var trains = new ArrayList<Train>(); var ids = new HashSet<Integer>(); var riders = new HashSet<Integer>();
            for (int i=0,n=count(in,32);i<n;i++) {
                int id=in.readInt(), depot=in.readInt(); float x=finite(in), y=finite(in), z=finite(in), yaw=finite(in), phase=finite(in);
                int stop=in.readInt(); float dwell=finite(in);
                if (id!=depot || !ids.add(id) || dwell<0 || dwell>10 || buildings.stream().noneMatch(b->b.id()==depot && b.type()==SpecialBuildings.RAIL_DEPOT)
                        || !cells.contains(new Cell((int)Math.floor(x),(int)Math.floor(z)))
                        || buildings.stream().noneMatch(b->b.id()==stop && (b.type()==SpecialBuildings.RAIL_STATION || b.id()==depot))) throw new IOException("Invalid steam train");
                var passengers = new ArrayList<Passenger>();
                for (int j=0,m=count(in,16);j<m;j++) {
                    int citizen=in.readInt(), destination=in.readInt();
                    if (!riders.add(citizen) || citizens.stream().noneMatch(c->c.id()==citizen)
                            || buildings.stream().noneMatch(b->b.id()==destination && b.type()==SpecialBuildings.RAIL_STATION)) throw new IOException("Invalid rail passenger");
                    passengers.add(new Passenger(citizen,destination));
                }
                trains.add(new Train(id,depot,x,y,z,yaw,phase,stop,dwell,passengers));
            }
            return new State(tracks,trains);
        }
        private static int count(DataInput in,int max) throws IOException { int n=in.readInt(); if(n<0 || n>max) throw new IOException("Invalid railway count"); return n; }
        private static float finite(DataInput in) throws IOException { float f=in.readFloat(); if(!Float.isFinite(f)||Math.abs(f)>1e7) throw new IOException("Invalid railway number");return f; }
    }
    private final LinkedHashMap<Cell,Track> tracks = new LinkedHashMap<>();
    private final Map<Integer, Set<Integer>> services = new HashMap<>();
    private final LinkedHashMap<Integer,Train> trains = new LinkedHashMap<>();
    public Railway() {}
    public Railway(State state) { for(var r:state.tracks()) tracks.put(new Cell(r.x(),r.z()),r); for(var t:state.trains()) trains.put(t.id(),t); }
    public State state() { return new State(new ArrayList<>(tracks.values()),new ArrayList<>(trains.values())); }
    public boolean contains(int x,int z) { return tracks.containsKey(new Cell(x,z)); }
    public int size() { return tracks.size(); }
    public void add(Collection<Cell> cells,int grade) { for(var c:cells) tracks.putIfAbsent(c,new Track(c.x(),c.z(),grade)); }
    public static Cell dock(CityFrame.Building b) { return new Cell(b.x()+2,b.z()+8); }
    public boolean connected(CityFrame.Building a, CityFrame.Building b) { return !path(dock(a),dock(b)).isEmpty(); }
    private List<Cell> path(Cell start,Cell end) {
        if (!tracks.containsKey(start)||!tracks.containsKey(end)) return List.of();
        var previous=new HashMap<Cell,Cell>(); var queue=new ArrayDeque<Cell>(); previous.put(start,start);queue.add(start);
        while(!queue.isEmpty() && !previous.containsKey(end)) {
            var c=queue.remove();
            for(var n:List.of(new Cell(c.x()+1,c.z()),new Cell(c.x()-1,c.z()),new Cell(c.x(),c.z()+1),new Cell(c.x(),c.z()-1)))
                if(tracks.containsKey(n)&&!previous.containsKey(n)) { previous.put(n,c);queue.add(n); }
        }
        if(!previous.containsKey(end)) return List.of();
        var result=new ArrayList<Cell>(); for(var c=end;;c=previous.get(c)) {result.add(c);if(c.equals(start))break;}
        Collections.reverse(result);return result;
    }
    public interface Riders { void move(int citizen,float x,float y,float z,String activity,boolean arrived); boolean exists(int citizen); }
    public void tick(float dt,List<CityFrame.Building> buildings,Riders riders) {
        services.clear();
        var depots=buildings.stream().filter(b->b.type()==SpecialBuildings.RAIL_DEPOT).sorted(Comparator.comparingInt(CityFrame.Building::id)).toList();
        var stations=buildings.stream().filter(b->b.type()==SpecialBuildings.RAIL_STATION).sorted(Comparator.comparingInt(CityFrame.Building::id)).toList();
        for(var t:new ArrayList<>(trains.values())) if(depots.stream().noneMatch(b->b.id()==t.depot())) {
            for(var p:t.passengers()) riders.move(p.citizen(),t.x(),t.y(),t.z(),"Rail depot removed",true);trains.remove(t.id());
        }
        for(var d:depots) if(trains.size()<32 && !trains.containsKey(d.id()) && tracks.containsKey(dock(d))) {
            var c=dock(d);trains.put(d.id(),new Train(d.id(),d.id(),c.x()+.5f,d.y(),c.z()+.5f,0,0,d.id(),10,List.of()));
        }
        for(var old:new ArrayList<>(trains.values())) {
            var depot=depots.stream().filter(b->b.id()==old.depot()).findFirst().orElseThrow();
            var stops=new ArrayList<CityFrame.Building>(); stops.add(depot);
            for(var s:stations) if(connected(depot,s))stops.add(s);
            boolean service=stops.size()>=3 && depots.stream().noneMatch(d->d.id()<depot.id()&&connected(depot,d));
            if(service) {var served=new HashSet<Integer>();for(var station:stops)if(station.type()==SpecialBuildings.RAIL_STATION)served.add(station.id());services.put(old.id(),served);}
            float x=old.x(),z=old.z(),yaw=old.yaw(),phase=old.phase(),dwell=old.dwell();int stop=old.stop();
            if (stops.stream().noneMatch(b->b.id()==old.stop())) {stop=depot.id();dwell=0;}
            var passengers=new ArrayList<>(old.passengers());
            passengers.removeIf(p->!riders.exists(p.citizen()));
            for(var p:new ArrayList<>(passengers)) {
                var destination=stations.stream().filter(s->s.id()==p.destination()).findFirst().orElse(null);
                if(!service || destination==null || !connected(depot,destination)) {
                    var landing=depot;
                    riders.move(p.citizen(),landing.x()+2.5f,landing.y()+1,landing.z()-1.5f,"Rail service unavailable",true);passengers.remove(p);
                }
            }
            if(!service) {var c=dock(depot);x=c.x()+.5f;z=c.z()+.5f;stop=depot.id();dwell=10;}
            else if(dwell>0) dwell=Math.max(0,dwell-dt);
            else {
                int index=0;for(int i=0;i<stops.size();i++)if(stops.get(i).id()==stop)index=i;
                var target=stops.get((index+1)%stops.size());var end=dock(target);
                var route=path(new Cell((int)Math.floor(x),(int)Math.floor(z)),end);
                if(route.isEmpty()) {var c=dock(depot);x=c.x()+.5f;z=c.z()+.5f;stop=depot.id();dwell=10;}
                else {
                    var next=route.get(0); if(route.size()>1 && ((x-next.x()-.5f)*(float)Math.sin(Math.toRadians(yaw))+(z-next.z()-.5f)*(float)Math.cos(Math.toRadians(yaw))>=-.001f))next=route.get(1);
                    float dx=next.x()+.5f-x,dz=next.z()+.5f-z, distance=(float)Math.hypot(dx,dz),move=Math.min(distance,dt*3);
                    if(distance>.001) {x+=dx/distance*move;z+=dz/distance*move;yaw=(float)Math.toDegrees(Math.atan2(dx,dz));phase+=move;}
                    if(Math.hypot(x-end.x()-.5f,z-end.z()-.5f)<.01) {
                        stop=target.id();dwell=stop==depot.id()?10:4;
                        for(var p:new ArrayList<>(passengers)) if(p.destination()==stop || stop==depot.id()) {
                            riders.move(p.citizen(),target.x()+2.5f,target.y()+1,target.z()-1.5f,"Left train",true);passengers.remove(p);
                        }
                    }
                }
            }
            var train=new Train(old.id(),old.depot(),x,depot.y(),z,yaw,phase,stop,dwell,passengers);trains.put(train.id(),train);
            for(var p:passengers) riders.move(p.citizen(),x,depot.y()+1,z,"Riding steam train",false);
        }
    }
    public boolean aboard(int citizen) { return trains.values().stream().anyMatch(t->t.passengers().stream().anyMatch(p->p.citizen()==citizen)); }
    public boolean board(int citizen,int source,int destination) {
        if(aboard(citizen)) return true;
        for(var t:new ArrayList<>(trains.values())) if(source!=destination && services.getOrDefault(t.id(),Set.of()).containsAll(List.of(source,destination)) && t.stop()==source && t.dwell()>0 && t.passengers().size()<16) {
            var passengers=new ArrayList<>(t.passengers());passengers.add(new Passenger(citizen,destination));
            trains.put(t.id(),new Train(t.id(),t.depot(),t.x(),t.y(),t.z(),t.yaw(),t.phase(),t.stop(),t.dwell(),passengers));return true;
        }
        return false;
    }
    public boolean served(CityFrame.Building station,List<CityFrame.Building> buildings) {
        for(var d:buildings) if(d.type()==SpecialBuildings.RAIL_DEPOT && connected(d,station)
                && buildings.stream().filter(b->b.type()==SpecialBuildings.RAIL_STATION&&connected(d,b)).count()>=2) return true;
        return false;
    }
}
