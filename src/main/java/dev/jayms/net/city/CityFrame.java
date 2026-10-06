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
        CityEconomy.State economy,
        CityAddresses.State addresses,
        Agriculture.State agriculture,
        RegionalPopulation.State population) {
    public CityFrame(GameConfig config, double elapsed, List<Road> roads, List<Zone> zones,
            List<Building> buildings, List<Citizen> citizens, List<Horse> horses,
            CityEconomy.State economy, CityAddresses.State addresses, Agriculture.State agriculture) {
        this(config, elapsed, roads, zones, buildings, citizens, horses, economy, addresses,
                agriculture, RegionalPopulation.State.empty());
    }

    public CityFrame(
            GameConfig config,
            double elapsed,
            List<Road> roads,
            List<Zone> zones,
            List<Building> buildings,
            List<Citizen> citizens,
            List<Horse> horses,
            CityEconomy.State economy,
            CityAddresses.State addresses) {
        this(
                config,
                elapsed,
                roads,
                zones,
                buildings,
                citizens,
                horses,
                economy,
                addresses,
                Agriculture.State.empty());
    }

    public CityFrame(
            GameConfig config,
            double elapsed,
            List<Road> roads,
            List<Zone> zones,
            List<Building> buildings,
            List<Citizen> citizens,
            List<Horse> horses,
            CityEconomy.State economy) {
        this(
                config,
                elapsed,
                roads,
                zones,
                buildings,
                citizens,
                horses,
                economy,
                CityAddresses.migrate(roads, buildings));
    }

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

    public record Road(int x, int z, int y, int type) {
        public Road(int x, int z, int y) { this(x, z, y, 0); }
        public Road { RoadTypes.validate(type); }
    }

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
            String activity, double age, CitizenLife.Gender gender, CitizenLife.Education education,
            double study, int spouse, int mother, int father, int school, double lastBirthAge) {
        public Citizen(int id, String name, int cohort, float x, float y, float z, float yaw,
                float phase, float hunger, float money, int home, int job, int horse, String activity) {
            this(id, name, cohort, x, y, z, yaw, phase, hunger, money, home, job, horse, activity,
                    24 + (id - 1) % 8, id % 2 != 0 ? CitizenLife.Gender.FEMALE : CitizenLife.Gender.MALE,
                    cohort == 0 ? CitizenLife.Education.NONE : cohort == 1 ? CitizenLife.Education.TECHNICAL : CitizenLife.Education.UNIVERSITY,
                    0, 0, 0, 0, 0, -10);
        }
    }

    public record Horse(int id, float x, float y, float z, float yaw, float phase, int rider) {}

    public CityFrame {
        roads = List.copyOf(roads);
        zones = List.copyOf(zones);
        buildings = List.copyOf(buildings);
        citizens = List.copyOf(citizens);
        horses = List.copyOf(horses);
    }

    /** Local residents and the bounded, individually simulated nearby district pool. */
    public List<Citizen> visibleCitizens() {
        if (population.agents().isEmpty()) return citizens;
        var result=new ArrayList<>(citizens);
        result.addAll(RegionalPopulation.citizens(population));
        return List.copyOf(result);
    }

    public static CityFrame empty(GameConfig config) {
        return new CityFrame(config, 0, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public void write(DataOutput out) throws IOException {
        write(out, 11);
    }

    public void write(DataOutput out, int version) throws IOException {
        if (version < 11 && !population.groups().isEmpty())
            throw new IOException("Regional population requires city format 11");
        config.write(out);
        out.writeDouble(elapsed);
        out.writeInt(roads.size());
        for (var r : roads) {
            out.writeInt(r.x);
            out.writeInt(r.z);
            out.writeInt(r.y);
            if (version >= 10) out.writeByte(r.type);
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
            if (version >= 9) {
                out.writeDouble(c.age); out.writeByte(c.gender.ordinal());
                out.writeByte(c.education.ordinal()); out.writeDouble(c.study);
                out.writeInt(c.spouse); out.writeInt(c.mother); out.writeInt(c.father);
                out.writeInt(c.school); out.writeDouble(c.lastBirthAge);
            }
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
        if (version >= 2) economy.write(out, version);
        if (version >= 5) CityAddresses.write(out, addresses);
        if (version >= 6) Agriculture.write(out, agriculture);
        if (version >= 11) RegionalPopulation.write(out, population);
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
        return read(in, legacy ? 1 : 11);
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
            int type = version >= 10 ? in.readUnsignedByte() : 0;
            if (type > 3) throw new IOException("Invalid road type");
            roads.add(new Road(x, z, y, type));
        }
        var zones = new ArrayList<Zone>();
        for (int i = 0, n = count(in, 128); i < n; i++) {
            int id = in.readInt(), type = in.readUnsignedByte();
            if (id < 1 || type > 3) throw new IOException("Invalid zone");
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
                    || (type > 3 && !SpecialBuildings.special(type))
                    || (type == SpecialBuildings.EXCHANGE && version < 8)
                    || (type >= 20 && version < 9)
                    || capacity < 1
                    || capacity > 32
                    || stock < 0
                    || (!SpecialBuildings.special(type) && stock > 1000)
                    || (SpecialBuildings.special(type) && (zone < -2 || zone > 0 || (zone == 0 ? stock != 0 : stock < 1)))
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
            var citizen = new Citizen(id, name, cohort, x, y, z, yaw, phase, hunger, money, home, job, horse, activity);
            if (version >= 9) {
                double age = in.readDouble(); int gender = in.readUnsignedByte(), education = in.readUnsignedByte();
                double study = in.readDouble();
                int spouse = in.readInt(), mother = in.readInt(), father = in.readInt(), school = in.readInt();
                double lastBirth = in.readDouble();
                if (!Double.isFinite(age) || age < 0 || age > 10000 || gender > 1 || education > 4
                        || !Double.isFinite(study) || study < 0 || study > 8
                        || spouse < 0 || mother < 0 || father < 0 || school < 0
                        || !Double.isFinite(lastBirth) || lastBirth > age)
                    throw new IOException("Invalid citizen life history");
                citizen = new Citizen(id, name, cohort, x, y, z, yaw, phase, hunger, money, home, job, horse, activity,
                        age, CitizenLife.Gender.values()[gender], CitizenLife.Education.values()[education],
                        study, spouse, mother, father, school, lastBirth);
            }
            citizens.add(citizen);
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
        var economy = version < 2 ? CityEconomy.State.empty() : CityEconomy.State.read(in, version);
        var addresses =
                version < 5
                        ? CityAddresses.migrate(roads, buildings)
                        : CityAddresses.read(in, buildings);
        var agriculture =
                version >= 6
                        ? Agriculture.read(in, economy, buildings, citizens, horses)
                        : Agriculture.State.migration(economy.resources().catalog());
        if (version >= 8) {
            try {
                CityCapital.validate(economy.capital(), economy.firms(), citizens);
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid capital ownership", e);
            }
        }
        if (version >= 9) {
            var people = new HashMap<Integer, Citizen>();
            for (var c : citizens)
                if (people.put(c.id(), c) != null) throw new IOException("Duplicate citizen");
            for (var c : citizens) {
                if (c.spouse() != 0 && (c.spouse() == c.id() || !people.containsKey(c.spouse())
                        || people.get(c.spouse()).spouse() != c.id() || c.age() <= 18
                        || people.get(c.spouse()).age() <= 18)) throw new IOException("Invalid spouse");
                if (c.mother() != 0 && (!people.containsKey(c.mother()) || c.mother() == c.id())
                        || c.father() != 0 && (!people.containsKey(c.father()) || c.father() == c.id()))
                    throw new IOException("Invalid parent");
                if (c.school() != 0 && buildings.stream().noneMatch(b -> b.id() == c.school()
                        && SpecialBuildings.special(b.type()) && b.type() != SpecialBuildings.EXCHANGE
                        && (SpecialBuildings.kind(b.type()) == 1 || SpecialBuildings.kind(b.type()) == 2
                            || SpecialBuildings.kind(b.type()) == 3 || SpecialBuildings.kind(b.type()) == 5)))
                    throw new IOException("Invalid school");
            }
        }
        return new CityFrame(
                config,
                elapsed,
                roads,
                zones,
                buildings,
                citizens,
                horses,
                economy,
                addresses,
                agriculture,
                version >= 11 ? RegionalPopulation.read(in) : RegionalPopulation.State.empty());
    }
}
