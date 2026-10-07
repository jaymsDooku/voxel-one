package dev.jayms.net.city;

import java.io.*;
import java.util.*;

/** Convex polygon in world X/Z; voxel centres decide enclosed ground cells. */
public record Polygon(List<Point> vertices) {
    public static final class Point {
        private final float x, z;
        public Point(float x, float z) { this(x,z,false); }
        private Point(float x, float z, boolean grid) {
            int limit=grid?10000:256;
            if (!Float.isFinite(x) || !Float.isFinite(z) || Math.abs(x-8)>limit || Math.abs(z-24)>limit)
                throw new IllegalArgumentException("City limits: 512 x 512 blocks around spawn");
            this.x=x; this.z=z;
        }
        static Point grid(float x,float z) { return new Point(x,z,true); }
        public float x() { return x; }
        public float z() { return z; }
        @Override public boolean equals(Object other) {
            return other instanceof Point p && Float.compare(x,p.x)==0 && Float.compare(z,p.z)==0;
        }
        @Override public int hashCode() { return 31*Float.hashCode(x)+Float.hashCode(z); }
        @Override public String toString() { return "Point[x="+x+", z="+z+"]"; }
    }

    public record Cell(int x, int z) {}

    public Polygon {
        vertices = List.copyOf(vertices);
        if (vertices.size() < 3 || vertices.size() > 32)
            throw new IllegalArgumentException("Use 3 to 32 polygon corners");
        float sign = 0;
        double area = 0;
        for (int i = 0; i < vertices.size(); i++) {
            var a = vertices.get(i);
            var b = vertices.get((i + 1) % vertices.size());
            var c = vertices.get((i + 2) % vertices.size());
            float cross = (b.x - a.x) * (c.z - b.z) - (b.z - a.z) * (c.x - b.x);
            if (Math.hypot(a.x - b.x, a.z - b.z) < .001)
                throw new IllegalArgumentException("Remove duplicate polygon corners");
            area += a.x * b.z - b.x * a.z;
            if (Math.abs(cross) < .001) continue;
            if (sign == 0) sign = Math.signum(cross);
            else if (sign * cross <= 0)
                throw new IllegalArgumentException("Polygon must be convex");
        }
        if (Math.abs(area) / 2 < 16 || Math.abs(area) / 2 > 4096)
            throw new IllegalArgumentException(
                    "Zone area must be 16 to 4096 blocks"); // All vertices must lie on the inner
        // side of every edge (also rejects star
        // polygons).
        for (int i = 0; i < vertices.size(); i++) {
            var a = vertices.get(i);
            var b = vertices.get((i + 1) % vertices.size());
            for (var p : vertices)
                if (sign * ((b.x - a.x) * (p.z - a.z) - (b.z - a.z) * (p.x - a.x)) < -.001)
                    throw new IllegalArgumentException("Polygon edges cannot cross");
        }
    }

    public boolean contains(float x, float z) {
        float sign = 0;
        for (int i = 0; i < vertices.size(); i++) {
            var a = vertices.get(i);
            var b = vertices.get((i + 1) % vertices.size());
            float c = (b.x - a.x) * (z - a.z) - (b.z - a.z) * (x - a.x);
            if (Math.abs(c) < .001) continue;
            if (sign == 0) sign = Math.signum(c);
            else if (sign * c < 0) return false;
        }
        return true;
    }

    public Set<Cell> cells() {
        int minX = (int) Math.floor(vertices.stream().mapToDouble(Point::x).min().orElseThrow()),
                maxX = (int) Math.ceil(vertices.stream().mapToDouble(Point::x).max().orElseThrow()),
                minZ =
                        (int)
                                Math.floor(
                                        vertices.stream()
                                                .mapToDouble(Point::z)
                                                .min()
                                                .orElseThrow()),
                maxZ = (int) Math.ceil(vertices.stream().mapToDouble(Point::z).max().orElseThrow());
        var cells = new LinkedHashSet<Cell>();
        for (int x = minX; x < maxX; x++)
            for (int z = minZ; z < maxZ; z++)
                if (contains(x + .5f, z + .5f)) cells.add(new Cell(x, z));
        return cells;
    }

    public void write(DataOutput out) throws IOException {
        out.writeByte(vertices.size());
        for (var p : vertices) {
            out.writeFloat(p.x);
            out.writeFloat(p.z);
        }
    }

    public static Polygon read(DataInput in) throws IOException {
        int n = in.readUnsignedByte();
        if (n < 3 || n > 32) throw new IOException("Invalid polygon");
        var points = new ArrayList<Point>();
        try {
            for (int i = 0; i < n; i++) points.add(new Point(in.readFloat(), in.readFloat()));
            return new Polygon(points);
        } catch (IllegalArgumentException e) {
            throw new IOException(e);
        }
    }
}
