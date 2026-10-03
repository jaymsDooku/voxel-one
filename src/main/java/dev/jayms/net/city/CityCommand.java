package dev.jayms.net.city;

import java.io.*;
import java.util.*;

public record CityCommand(int kind, int value, List<Polygon.Point> points, int ownerKind, int ownerId) {
    public CityCommand(int kind, int value, List<Polygon.Point> points) { this(kind, value, points, 0, 0); }
    public static final int ROAD = 1, ZONE = 2, RIDE = 3, DEMOLISH = 4, SPECIAL = 5;

    public CityCommand {
        points = List.copyOf(points);
        if (kind < 1 || kind > 5 || points.size() > 32)
            throw new IllegalArgumentException("Invalid city command");
        if (kind == DEMOLISH && (value <= 0 || !points.isEmpty()))
            throw new IllegalArgumentException("Demolition needs a building ID and no points");
    }

    public void write(DataOutput out) throws IOException {
        out.writeByte(kind);
        out.writeInt(value);
        out.writeByte(points.size());
        if (kind == SPECIAL) { out.writeByte(ownerKind); out.writeInt(ownerId); }
        for (var p : points) {
            out.writeFloat(p.x());
            out.writeFloat(p.z());
        }
    }

    public static CityCommand read(DataInput in) throws IOException {
        int kind = in.readUnsignedByte(), value = in.readInt(), n = in.readUnsignedByte();
        if (n > 32) throw new IOException("Too many city points");
        int ownerKind = kind == SPECIAL ? in.readUnsignedByte() : 0;
        int ownerId = kind == SPECIAL ? in.readInt() : 0;
        try {
            var points = new ArrayList<Polygon.Point>();
            for (int i = 0; i < n; i++)
                points.add(new Polygon.Point(in.readFloat(), in.readFloat()));
            return new CityCommand(kind, value, points, ownerKind, ownerId);
        } catch (IllegalArgumentException e) {
            throw new IOException(e);
        }
    }
}
