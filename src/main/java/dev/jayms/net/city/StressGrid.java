package dev.jayms.net.city;

import dev.jayms.net.Blocks;
import java.io.*;
import java.util.*;

/** A complete, implicit grid. Every plot has a stable id; geometry is generated on demand. */
public record StressGrid(int grade) {
    public static final String NAME = "Stress Test Grid";
    public static final int SIDE = 1000, COUNT = SIDE * SIDE, PLOT = 16, ROAD = 3,
            STRIDE = PLOT + ROAD, EXTENT = SIDE * STRIDE + ROAD;
    public static final int MIN_X = 8 - EXTENT / 2, MIN_Z = 24 - EXTENT / 2;
    public StressGrid {
        if (grade < 0 || grade > 64) throw new IllegalArgumentException("Invalid stress grid grade");
    }
    public static StressGrid standard() { return new StressGrid(32); }
    public boolean contains(int x, int z) {
        return x >= MIN_X && x < MIN_X + EXTENT && z >= MIN_Z && z < MIN_Z + EXTENT;
    }
    public boolean road(int x, int z) {
        return contains(x,z) && (Math.floorMod(x-MIN_X,STRIDE)<ROAD || Math.floorMod(z-MIN_Z,STRIDE)<ROAD);
    }
    public int surface(int x, int z) {
        if (!road(x,z)) return Blocks.GRASS;
        if (Math.floorMod(z-MIN_Z,STRIDE)==1) return Blocks.ROAD_LINE_X;
        if (Math.floorMod(x-MIN_X,STRIDE)==1) return Blocks.ROAD_LINE_Z;
        return Blocks.ASPHALT;
    }
    public int plotAt(int x, int z) {
        if (!contains(x,z) || road(x,z)) return -1;
        return ((z-MIN_Z)/STRIDE)*SIDE + (x-MIN_X)/STRIDE;
    }
    /** Perimeters are ordered in four-cell rotational orbits, giving exact symmetric shares. */
    public static int rank(int x, int z) {
        Objects.checkIndex(x,SIDE); Objects.checkIndex(z,SIDE);
        int k=(Math.max(Math.abs(2*x-(SIDE-1)),Math.abs(2*z-(SIDE-1)))+1)/2;
        int lo=SIDE/2-k, hi=SIDE/2+k-1, side=2*k-1, p;
        if(z==lo && x<hi) p=x-lo;
        else if(x==hi && z<hi) p=side+z-lo;
        else if(z==hi && x>lo) p=2*side+hi-x;
        else p=3*side+hi-z;
        return (2*k-2)*(2*k-2)+4*(p%side)+p/side;
    }
    public static int type(int x, int z) {
        int n=rank(x,z);
        return n<400_000?0:n<600_000?1:n<800_000?2:3;
    }
    public CityFrame.Zone zone(int index) {
        Objects.checkIndex(index,COUNT);
        int x=index%SIDE,z=index/SIDE, bx=MIN_X+ROAD+x*STRIDE, bz=MIN_Z+ROAD+z*STRIDE;
        return new CityFrame.Zone(index+1,type(x,z),new Polygon(List.of(
                Polygon.Point.grid(bx,bz),Polygon.Point.grid(bx+PLOT,bz),
                Polygon.Point.grid(bx+PLOT,bz+PLOT),Polygon.Point.grid(bx,bz+PLOT))));
    }
    public List<CityFrame.Zone> zones() { return new Zones(this); }
    public Polygon.Cell nearestRoad(float x, float z) {
        int bx=(int)Math.floor(x), bz=(int)Math.floor(z);
        if (!contains(bx,bz)) return null;
        if (road(bx,bz)) return new Polygon.Cell(bx,bz);
        int rx=nearestStrip(bx,MIN_X), rz=nearestStrip(bz,MIN_Z);
        return Math.abs(rx+.5-x)<=Math.abs(rz+.5-z) ? new Polygon.Cell(rx,bz) : new Polygon.Cell(bx,rz);
    }
    private static int nearestStrip(int value,int origin) {
        int offset=Math.floorMod(value-origin,STRIDE), base=value-offset;
        if(offset<ROAD) return value;
        return offset-(ROAD-1)<=STRIDE-offset ? base+ROAD-1 : base+STRIDE;
    }
    private List<Polygon.Cell> junctions(Polygon.Cell cell) {
        var result=new ArrayList<Polygon.Cell>();
        if(Math.floorMod(cell.z()-MIN_Z,STRIDE)<ROAD) {
            int base=cell.x()-Math.floorMod(cell.x()-MIN_X,STRIDE)+1;
            for(int x:new int[]{base,base+STRIDE}) if(contains(x,cell.z())) result.add(new Polygon.Cell(x,cell.z()));
        }
        if(Math.floorMod(cell.x()-MIN_X,STRIDE)<ROAD) {
            int base=cell.z()-Math.floorMod(cell.z()-MIN_Z,STRIDE)+1;
            for(int z:new int[]{base,base+STRIDE}) if(contains(cell.x(),z)) result.add(new Polygon.Cell(cell.x(),z));
        }
        return result;
    }
    /** Orthogonal paths use existing road strips; no search proportional to city area. */
    public List<Polygon.Cell> route(Polygon.Cell from,Polygon.Cell to) {
        if(!road(from.x(),from.z()) || !road(to.x(),to.z())) return List.of();
        if(from.equals(to)) return List.of(from);
        List<Polygon.Cell> best=List.of(); long length=Long.MAX_VALUE;
        for(var a:junctions(from)) for(var b:junctions(to)) {
            var points=List.of(from,a,new Polygon.Cell(b.x(),a.z()),b,to);
            long d=0;
            for(int i=1;i<points.size();i++) d+=Math.abs(points.get(i).x()-points.get(i-1).x())+Math.abs(points.get(i).z()-points.get(i-1).z());
            if(d<length) { length=d; best=points; }
        }
        var result=new ArrayList<Polygon.Cell>();
        if(best.isEmpty()) return result;
        result.add(from);
        for(int i=1;i<best.size();i++) {
            var a=best.get(i-1); var b=best.get(i);
            int x=a.x(),z=a.z();
            while(x!=b.x() || z!=b.z()) { x+=Integer.signum(b.x()-x); z+=Integer.signum(b.z()-z); result.add(new Polygon.Cell(x,z)); }
        }
        return result;
    }
    public static final class Zones extends AbstractList<CityFrame.Zone> implements RandomAccess {
        private final StressGrid grid;
        private Zones(StressGrid grid) { this.grid=grid; }
        public int size() { return COUNT; }
        public CityFrame.Zone get(int index) { return grid.zone(index); }
        @Override public boolean equals(Object other) {
            // Grade changes terrain elevation, not the zone polygons in this list.
            return other instanceof Zones || super.equals(other);
        }
    }
    public long roadCells() { return (long)EXTENT*EXTENT-(long)COUNT*PLOT*PLOT; }
    public void write(DataOutput out) throws IOException { out.writeInt(grade); }
    public static StressGrid read(DataInput in) throws IOException {
        try { return new StressGrid(in.readInt()); }
        catch(IllegalArgumentException e) { throw new IOException("Invalid stress grid",e); }
    }
}
