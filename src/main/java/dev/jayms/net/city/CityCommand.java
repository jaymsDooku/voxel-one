package dev.jayms.net.city;

import java.io.*;
import java.util.*;

public record CityCommand(int kind, int value, List<Polygon.Point> points) {
    public static final int ROAD = 1, ZONE = 2, RIDE = 3;

    public CityCommand {
        points = List.copyOf(points);
        if (kind < 1 || kind > 3 || points.size() > 32)
            throw new IllegalArgumentException("Invalid city command");
    }

    public void write(DataOutput out) throws IOException {
        out.writeByte(kind);
        out.writeInt(value);
        out.writeByte(points.size());
        for (var p : points) {
            out.writeFloat(p.x());
            out.writeFloat(p.z());
        }
    }

    public static CityCommand read(DataInput in) throws IOException {
        int kind = in.readUnsignedByte(), value = in.readInt(), n = in.readUnsignedByte();
        if (n > 32) throw new IOException("Too many city points");
        try {
            var points = new ArrayList<Polygon.Point>();
            for (int i = 0; i < n; i++)
                points.add(new Polygon.Point(in.readFloat(), in.readFloat()));
            return new CityCommand(kind, value, points);
        } catch (IllegalArgumentException e) {
            throw new IOException(e);
        }
    }
}
