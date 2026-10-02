package dev.jayms.net.city;

import java.io.*;
import java.util.*;

/** Immutable, bounded snapshot shared by persistence, network and the city HUD. */
public record CityFrame(
        GameConfig config,
        double elapsed,
        List<Road> roads,
        List<Zone> zones,
        List<Building> buildings,
        List<Citizen> citizens,
        List<Horse> horses,
        CityEconomy.State economy) {
    public CityFrame(
            GameConfig config,
            double elapsed,
            List<Road> roads,
            List<Zone> zones,
            List<Building> buildings,
            List<Citizen> citizens,
            List<Horse> horses) {
        this(config, elapsed, roads, zones, buildings, citizens, horses, CityEconomy.State.empty());
    }

    public record Road(int x, int z, int y) {}

    public record Zone(int id, int type, Polygon polygon) {}

    public record Building(
            int id, int zone, int type, int x, int y, int z, int capacity, int stock) {}

    public record Citizen(
            int id,
            String name,
            int cohort,
            float x,
            float y,
            float z,
            float yaw,
            float phase,
            float hunger,
            float money,
            int home,
            int job,
            int horse,
            String activity) {}

    public record Horse(int id, float x, float y, float z, float yaw, float phase, int rider) {}

    public CityFrame {
        roads = List.copyOf(roads);
        zones = List.copyOf(zones);
        buildings = List.copyOf(buildings);
        citizens = List.copyOf(citizens);
        horses = List.copyOf(horses);
    }

    public static CityFrame empty(GameConfig config) {
        return new CityFrame(config, 0, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public void write(DataOutput out) throws IOException {
        write(out, 3);
    }

    public void write(DataOutput out, int version) throws IOException {
        config.write(out);
        out.writeDouble(elapsed);
        out.writeInt(roads.size());
        for (var r : roads) {
            out.writeInt(r.x);
            out.writeInt(r.z);
            out.writeInt(r.y);
        }
        out.writeInt(zones.size());
        for (var z : zones) {
            out.writeInt(z.id);
            out.writeByte(z.type);
            z.polygon.write(out);
        }
        out.writeInt(buildings.size());
        for (var b : buildings) {
            out.writeInt(b.id);
            out.writeInt(b.zone);
            out.writeByte(b.type);
            out.writeInt(b.x);
            out.writeInt(b.y);
            out.writeInt(b.z);
            out.writeInt(b.capacity);
            out.writeInt(b.stock);
        }
        out.writeInt(citizens.size());
        for (var c : citizens) {
            out.writeInt(c.id);
            out.writeUTF(c.name);
            out.writeByte(c.cohort);
            out.writeFloat(c.x);
            out.writeFloat(c.y);
            out.writeFloat(c.z);
            out.writeFloat(c.yaw);
            out.writeFloat(c.phase);
            out.writeFloat(c.hunger);
            out.writeFloat(c.money);
            out.writeInt(c.home);
            out.writeInt(c.job);
            out.writeInt(c.horse);
            out.writeUTF(c.activity);
        }
        out.writeInt(horses.size());
        for (var h : horses) {
            out.writeInt(h.id);
            out.writeFloat(h.x);
            out.writeFloat(h.y);
            out.writeFloat(h.z);
            out.writeFloat(h.yaw);
            out.writeFloat(h.phase);
            out.writeInt(h.rider);
        }
        if (version >= 2) economy.write(out, version < 3);
    }

    private static int count(DataInput in, int max) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > max) throw new IOException("Invalid city snapshot count");
        return n;
    }

    private static float number(DataInput in) throws IOException {
        float v = in.readFloat();
        if (!Float.isFinite(v) || Math.abs(v) > 1e7) throw new IOException("Invalid city number");
        return v;
    }

    public static CityFrame read(DataInput in) throws IOException {
        return read(in, false);
    }

    public static CityFrame read(DataInput in, boolean legacy) throws IOException {
        return read(in, legacy ? 1 : 3);
    }

    public static CityFrame read(DataInput in, int version) throws IOException {
        var config = GameConfig.read(in);
        double elapsed = in.readDouble();
        if (!Double.isFinite(elapsed) || elapsed < 0) throw new IOException("Invalid clock");
        var roads = new ArrayList<Road>();
        for (int i = 0, n = count(in, 8192); i < n; i++) {
            int x = in.readInt(), z = in.readInt(), y = in.readInt();
            if (Math.abs((long) x - 8) > 256 || Math.abs((long) z - 24) > 256 || y < -31 || y > 89)
                throw new IOException("Invalid road");
            roads.add(new Road(x, z, y));
        }
        var zones = new ArrayList<Zone>();
        for (int i = 0, n = count(in, 128); i < n; i++) {
            int id = in.readInt(), type = in.readUnsignedByte();
            if (id < 1 || type > 2) throw new IOException("Invalid zone");
            zones.add(new Zone(id, type, Polygon.read(in)));
        }
        var buildings = new ArrayList<Building>();
        for (int i = 0, n = count(in, 512); i < n; i++) {
            int id = in.readInt(),
                    zone = in.readInt(),
                    type = in.readUnsignedByte(),
                    x = in.readInt(),
                    y = in.readInt(),
                    z = in.readInt(),
                    capacity = in.readInt(),
                    stock = in.readInt();
            if (id < 1
                    || type > 2
                    || capacity < 1
                    || capacity > 32
                    || stock < 0
                    || stock > 1000
                    || y < -27
                    || y > 89
                    || Math.abs((long) x - 8) > 256
                    || Math.abs((long) z - 24) > 256) throw new IOException("Invalid building");
            buildings.add(new Building(id, zone, type, x, y, z, capacity, stock));
        }
        var citizens = new ArrayList<Citizen>();
        for (int i = 0, n = count(in, 128); i < n; i++) {
            int id = in.readInt();
            String name = in.readUTF();
            int cohort = in.readUnsignedByte();
            float x = number(in),
                    y = number(in),
                    z = number(in),
                    yaw = number(in),
                    phase = number(in),
                    hunger = number(in),
                    money = number(in);
            int home = in.readInt(), job = in.readInt(), horse = in.readInt();
            String activity = in.readUTF();
            if (id < 1
                    || name.length() > 32
                    || cohort > 2
                    || hunger < 0
                    || hunger > 100
                    || money < 0
                    || activity.length() > 64) throw new IOException("Invalid citizen");
            citizens.add(
                    new Citizen(
                            id, name, cohort, x, y, z, yaw, phase, hunger, money, home, job, horse,
                            activity));
        }
        var horses = new ArrayList<Horse>();
        for (int i = 0, n = count(in, 64); i < n; i++)
            horses.add(
                    new Horse(
                            in.readInt(),
                            number(in),
                            number(in),
                            number(in),
                            number(in),
                            number(in),
                            in.readInt()));
        return new CityFrame(
                config,
                elapsed,
                roads,
                zones,
                buildings,
                citizens,
                horses,
                version < 2 ? CityEconomy.State.empty() : CityEconomy.State.read(in, version < 3));
    }
}
