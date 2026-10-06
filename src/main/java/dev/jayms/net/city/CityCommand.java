package dev.jayms.net.city;

import java.io.*;
import java.util.*;

public record CityCommand(int kind, int value, List<Polygon.Point> points, int ownerKind, int ownerId, Capital capital) {
    public static final int ROAD = 1, ZONE = 2, RIDE = 3, DEMOLISH = 4, SPECIAL = 5, EXCHANGE = 6, CAPITAL = 7, SETTLE_DISTRICT = 8, FOCUS_DISTRICT = 9, RUNWAY = 10, FLIGHT = 11, DELETE_ROAD = 12, EDIT_ROAD = 13, RAIL = 14;

    public record Capital(
            int action,
            int company,
            int ownerKind,
            int owner,
            long shares,
            long price,
            long order) {
        public Capital {
            if (action < 0
                    || action > 3
                    || company < 1
                    || ownerKind < 0
                    || ownerKind > 1
                    || owner < 1
                    || action < 3
                            && (shares < 1
                                    || shares > CityStockExchange.MAX_SHARES
                                    || price < 1
                                    || price > CityStockExchange.MAX_PRICE)
                    || action == 3 && order < 1)
                throw new IllegalArgumentException("Invalid capital command");
        }
    }

    public CityCommand(int kind, int value, List<Polygon.Point> points) {
        this(kind, value, points, 0, 0, null);
    }

    public CityCommand(int kind, int value, List<Polygon.Point> points, int ownerKind, int ownerId) { this(kind, value, points, ownerKind, ownerId, null); }

    public CityCommand(Capital capital) {
        this(CAPITAL, 0, List.of(), 0, 0, capital);
    }

    public CityCommand {
        points = List.copyOf(points);
        if (kind < 1
                || kind > 14
                || points.size() > 32
                || (kind == CAPITAL) != (capital != null)
                || kind == CAPITAL && !points.isEmpty())
            throw new IllegalArgumentException("Invalid city command");
        if ((kind == SETTLE_DISTRICT || kind == FOCUS_DISTRICT)
                && (!points.isEmpty() || ownerKind!=0 || ownerId!=0 || value<0
                    || kind==SETTLE_DISTRICT && value!=1000 && value!=100_000 && value!=1_000_000))
            throw new IllegalArgumentException("Invalid district command");
        if ((kind == DELETE_ROAD || kind == EDIT_ROAD) && (value<=0
                || kind==DELETE_ROAD && !points.isEmpty()
                || kind==EDIT_ROAD && (points.size()!=1 || points.get(0).z()!=0
                    || points.get(0).x()!=Math.floor(points.get(0).x())
                    || points.get(0).x()<0 || points.get(0).x()>3)))
            throw new IllegalArgumentException("Invalid road section command");
        if (kind == DEMOLISH && (value <= 0 || !points.isEmpty()))
            throw new IllegalArgumentException("Demolition needs a building ID and no points");
    }

    public void write(DataOutput out) throws IOException {
        out.writeByte(kind);
        out.writeInt(value);
        out.writeByte(points.size());
        if (kind == SPECIAL || kind == FLIGHT) { out.writeByte(ownerKind); out.writeInt(ownerId); }
        for (var p : points) {
            out.writeFloat(p.x());
            out.writeFloat(p.z());
        }
        if (capital != null) {
            out.writeByte(capital.action());
            out.writeInt(capital.company());
            out.writeByte(capital.ownerKind());
            out.writeInt(capital.owner());
            out.writeLong(capital.shares());
            out.writeLong(capital.price());
            out.writeLong(capital.order());
        }
    }

    public static CityCommand read(DataInput in) throws IOException {
        int kind = in.readUnsignedByte(), value = in.readInt(), n = in.readUnsignedByte();
        if (n > 32) throw new IOException("Too many city points");
        int ownerKind = (kind == SPECIAL || kind == FLIGHT) ? in.readUnsignedByte() : 0;
        int ownerId = (kind == SPECIAL || kind == FLIGHT) ? in.readInt() : 0;
        try {
            var points = new ArrayList<Polygon.Point>();
            for (int i = 0; i < n; i++)
                points.add(new Polygon.Point(in.readFloat(), in.readFloat()));
            var capital =
                    kind == CAPITAL
                            ? new Capital(
                                    in.readUnsignedByte(),
                                    in.readInt(),
                                    in.readUnsignedByte(),
                                    in.readInt(),
                                    in.readLong(),
                                    in.readLong(),
                                    in.readLong())
                            : null;
            return new CityCommand(kind, value, points, ownerKind, ownerId, capital);
        } catch (IllegalArgumentException e) {
            throw new IOException(e);
        }
    }
}
