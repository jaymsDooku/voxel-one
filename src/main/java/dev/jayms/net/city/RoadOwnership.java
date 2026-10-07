package dev.jayms.net.city;

import dev.jayms.net.Blocks;
import java.io.*;
import java.util.*;

/** Durable per-street ownership, independent of the visible paint at a crossing. */
public final class RoadOwnership {
    private RoadOwnership() {}
    public static final int MAX_CELLS = 65_536;

    public record OwnedCell(int x, int z, int type, int surface, long paint) {
        public OwnedCell {
            RoadTypes.validate(type);
            if (Math.abs((long)x-8)>256 || Math.abs((long)z-24)>256 || paint<=0 || paint==Long.MAX_VALUE
                    || type==0 && surface!=Blocks.DIRT
                    || type!=0 && surface!=Blocks.ASPHALT && surface!=Blocks.ROAD_LINE_X && surface!=Blocks.ROAD_LINE_Z)
                throw new IllegalArgumentException("Invalid road ownership cell");
        }
        public Polygon.Cell cell() { return new Polygon.Cell(x,z); }
    }

    public record Footprint(int street, List<OwnedCell> cells, boolean inferred) {
        public Footprint {
            cells=List.copyOf(cells);
            if(street<1 || cells.isEmpty() || cells.size()>8192
                    || cells.stream().map(OwnedCell::cell).distinct().count()!=cells.size())
                throw new IllegalArgumentException("Invalid road footprint");
        }
    }

    public static List<Footprint> paint(List<Footprint> old, int street, int type,
            Map<Polygon.Cell,Integer> surfaces, boolean replace) {
        long stamp=old.stream().flatMap(f->f.cells().stream()).mapToLong(OwnedCell::paint).max().orElse(0)+1;
        var cells=new LinkedHashMap<Polygon.Cell,OwnedCell>();
        var previous=old.stream().filter(f->f.street()==street).findFirst().orElse(null);
        if(!replace && previous!=null) for(var c:previous.cells()) cells.put(c.cell(),c);
        for(var e:surfaces.entrySet()) cells.put(e.getKey(),new OwnedCell(e.getKey().x(),e.getKey().z(),type,e.getValue(),stamp));
        var result=new ArrayList<Footprint>();
        for(var f:old) if(f.street()!=street) result.add(f);
        result.add(new Footprint(street,new ArrayList<>(cells.values()),!replace && previous!=null && previous.inferred()));
        if(result.stream().mapToInt(f->f.cells().size()).sum()>MAX_CELLS)
            throw new IllegalArgumentException("Too many overlapping road cells");
        return List.copyOf(result);
    }

    public static List<Footprint> forFrame(CityFrame frame) {
        return frame.addresses().roadFootprints().isEmpty() ? infer(frame) : frame.addresses().roadFootprints();
    }

    /** Old snapshots lack ownership. Infer width from route centers away from crossings once. */
    public static List<Footprint> infer(CityFrame frame) { return infer(frame.roads(),frame.addresses()); }

    public static List<Footprint> infer(List<CityFrame.Road> roads,CityAddresses.State addresses) {
        var actual=new LinkedHashMap<Polygon.Cell,CityFrame.Road>();
        for(var r:roads) actual.put(new Polygon.Cell(r.x(),r.z()),r);
        var result=new ArrayList<Footprint>(); var covered=new HashSet<Polygon.Cell>();
        for(var street:addresses.streets()) {
            int[] votes=new int[4], fallback=new int[4];
            for(var p:RoadGeometry.centers(street.route(),1)) {
                var r=actual.get(new Polygon.Cell((int)p.x(),(int)p.z()));
                if(r==null) continue;
                fallback[r.type()]++;
                boolean crossing=addresses.streets().stream().anyMatch(s->s.id()!=street.id()
                        && CityAddresses.distance(s,p.x(),p.z())<.01);
                if(!crossing) votes[r.type()]++;
            }
            if(Arrays.stream(votes).sum()==0) votes=fallback;
            int type=0; for(int i=1;i<4;i++) if(votes[i]>votes[type]) type=i;
            var cells=new ArrayList<OwnedCell>();
            for(var e:RoadGeometry.surfaces(street.route(),type).entrySet()) {
                var r=actual.get(e.getKey()); if(r==null) continue;
                cells.add(new OwnedCell(r.x(),r.z(),type,e.getValue(),r.type()==type?2:1)); covered.add(e.getKey());
            }
            if(!cells.isEmpty()) result.add(new Footprint(street.id(),cells,true));
        }
        // Preserve legacy cells whose old route metadata did not describe their exact shape.
        for(var e:actual.entrySet()) if(!covered.contains(e.getKey())) {
            var street=addresses.nearest(e.getKey().x(),e.getKey().z());
            if(street==null) continue;
            var r=e.getValue(); int surface=r.type()==0?Blocks.DIRT:Blocks.ASPHALT;
            var own=new OwnedCell(r.x(),r.z(),r.type(),surface,2);
            int index=-1; for(int i=0;i<result.size();i++) if(result.get(i).street()==street.id()) index=i;
            var cells=index<0?new ArrayList<OwnedCell>():new ArrayList<>(result.get(index).cells()); cells.add(own);
            var f=new Footprint(street.id(),cells,true);
            if(index<0) result.add(f); else result.set(index,f);
        }
        return List.copyOf(result);
    }

    public static Map<Polygon.Cell,OwnedCell> visible(List<Footprint> footprints) {
        var result=new LinkedHashMap<Polygon.Cell,OwnedCell>();
        for(var f:footprints) for(var c:f.cells()) {
            var previous=result.get(c.cell());
            if(previous==null || c.paint()>=previous.paint()) result.put(c.cell(),c);
        }
        return result;
    }

    public static void write(DataOutput out, List<Footprint> footprints) throws IOException {
        out.writeInt(footprints.size());
        for(var f:footprints) {
            out.writeInt(f.street()); out.writeBoolean(f.inferred()); out.writeInt(f.cells().size());
            for(var c:f.cells()) {
                out.writeInt(c.x()); out.writeInt(c.z()); out.writeByte(c.type());
                out.writeInt(c.surface()); out.writeLong(c.paint());
            }
        }
    }

    public static List<Footprint> read(DataInput in, CityAddresses.State addresses, List<CityFrame.Road> roads) throws IOException {
        int n=count(in,512), total=0; var result=new ArrayList<Footprint>(); var ids=new HashSet<Integer>();
        var actual=new HashSet<Polygon.Cell>(); for(var r:roads) actual.add(new Polygon.Cell(r.x(),r.z()));
        var covered=new HashSet<Polygon.Cell>();
        try {
            for(int i=0;i<n;i++) {
                int id=in.readInt(); boolean inferred=in.readBoolean(); int size=count(in,8192); total+=size;
                if(total>MAX_CELLS || !ids.add(id) || addresses.streets().stream().noneMatch(s->s.id()==id))
                    throw new IOException("Invalid road footprint owner");
                var cells=new ArrayList<OwnedCell>();
                for(int j=0;j<size;j++) {
                    var c=new OwnedCell(in.readInt(),in.readInt(),in.readUnsignedByte(),in.readInt(),in.readLong());
                    if(!actual.contains(c.cell())) throw new IOException("Road footprint outside road cells");
                    covered.add(c.cell()); cells.add(c);
                }
                result.add(new Footprint(id,cells,inferred));
            }
        } catch(IllegalArgumentException e) { throw new IOException("Invalid road ownership",e); }
        if(!covered.equals(actual) && !addresses.streets().isEmpty()) throw new IOException("Missing road ownership");
        return List.copyOf(result);
    }

    private static int count(DataInput in, int max) throws IOException {
        int n=in.readInt(); if(n<0 || n>max) throw new IOException("Invalid road ownership count"); return n;
    }
}
