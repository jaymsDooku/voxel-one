package dev.jayms.net.city;

import dev.jayms.net.Blocks;

import java.io.*;
import java.util.*;

/** Game-owned types and starting companies. Numeric type IDs are stable save identities. */
public record BusinessCatalog(List<Type> types, List<Company> companies) {
    public record Type(int id, String name, List<Integer> harvest, double rate, int capacity) {
        public Type {
            harvest = List.copyOf(harvest);
        }
    }

    public record Company(String key, String name, int type, double cash) {}

    public BusinessCatalog {
        types = List.copyOf(types);
        companies = List.copyOf(companies);
        if (types.size() > 64 || companies.size() > 128)
            throw new IllegalArgumentException("Business catalog too large");
        var ids = new HashSet<Integer>();
        for (var t : types) {
            if (t.id < 0
                    || t.id > 63
                    || !ids.add(t.id)
                    || !label(t.name)
                    || !Double.isFinite(t.rate)
                    || t.rate <= 0
                    || t.rate > 4096
                    || t.capacity < 1
                    || t.capacity > 4096
                    || t.harvest.size() > 8
                    || new HashSet<>(t.harvest).size() != t.harvest.size())
                throw new IllegalArgumentException("Invalid business type " + t.id);
            for (int material : t.harvest)
                if (material != Blocks.STONE
                        && material != Blocks.WOOD
                        && material != Blocks.DIRT
                        && material != Blocks.SAND)
                    throw new IllegalArgumentException("Unsupported natural material " + material);
        }
        var keys = new HashSet<String>();
        var identities = new HashSet<String>();
        for (var c : companies)
            if (!c.key.matches("[a-zA-Z0-9_-]{1,48}")
                    || !keys.add(c.key)
                    || !label(c.name)
                    || !identities.add(c.type + ":" + c.name)
                    || !ids.contains(c.type)
                    || !Double.isFinite(c.cash)
                    || c.cash < 0
                    || c.cash > 1e9)
                throw new IllegalArgumentException("Invalid starting company " + c.key);
    }

    private static boolean label(String value) {
        return !value.isBlank() && value.length() <= 48;
    }

    public Type type(int id) {
        return types.stream().filter(t -> t.id == id).findFirst().orElse(null);
    }

    public String sector(int id) {
        var t = type(id);
        return t == null ? CityMaterials.sector(id) : t.name;
    }

    public static BusinessCatalog defaults(boolean agriculture) {
        var types = new ArrayList<Type>();
        for (int id = 0; id <= (agriculture ? 14 : 8); id++)
            types.add(
                    new Type(
                            id,
                            CityMaterials.sector(id),
                            id == 2
                                    ? List.of(Blocks.STONE)
                                    : id == 3
                                            ? List.of(Blocks.WOOD)
                                            : id == 5 ? List.of(Blocks.SAND) : List.of(),
                            id == 7 ? 16 : 256,
                            CityMaterials.capacity(id)));
        String[] names = {
            "Oak & Stone Developers",
            "Riverbend Properties",
            "Horizon Builders",
            "Town Market",
            "Valley Mining",
            "Pinewood Logging",
            "Stonecraft Brickworks",
            "Dune Glassworks",
            "Bright Spark Lighting",
            "Meadow Farm",
            "Stone & Timber Tools",
            "Reed Family Sugarcane",
            "Brook Family Cattle",
            "Valley Flour Mill",
            "Oak Road Bakery",
            "Cane Sugar Refinery",
            "Carrot Cake Kitchen"
        };
        var companies = new ArrayList<Company>();
        for (int i = 0; i < (agriculture ? names.length : 11); i++)
            companies.add(
                    new Company(
                            "default-" + i,
                            names[i],
                            i < 3 ? 0 : i - 2,
                            i < 3 ? 1200 : i == 4 ? 2000 : 1500));
        return new BusinessCatalog(types, companies);
    }

    public static BusinessCatalog load(Properties p, boolean agriculture) {
        var defaults = defaults(agriculture);
        var types = new ArrayList<>(defaults.types);
        var configured = new HashSet<Integer>();
        for (String value : list(p.getProperty("business.types", ""))) {
            int id = Integer.parseInt(value);
            String prefix = "business." + id + ".";
            var harvest =
                    list(p.getProperty(prefix + "harvest", "")).stream()
                            .map(Integer::parseInt)
                            .toList();
            var type =
                    new Type(
                            id,
                            required(p, prefix + "name"),
                            harvest,
                            Double.parseDouble(p.getProperty(prefix + "rate", "256")),
                            Integer.parseInt(p.getProperty(prefix + "capacity", "512")));
            if (!configured.add(id))
                throw new IllegalArgumentException("Duplicate business type " + id);
            types.removeIf(t -> t.id == id);
            types.add(type);
        }
        var companies = new ArrayList<Company>();
        String inherit = p.getProperty("companies.inherit-defaults", "true");
        if (!inherit.equals("true") && !inherit.equals("false"))
            throw new IllegalArgumentException("companies.inherit-defaults must be true or false");
        if (Boolean.parseBoolean(inherit)) companies.addAll(defaults.companies);
        for (String key : list(p.getProperty("companies", ""))) {
            String prefix = "company." + key + ".";
            companies.add(
                    new Company(
                            key,
                            required(p, prefix + "name"),
                            Integer.parseInt(required(p, prefix + "type")),
                            Double.parseDouble(p.getProperty(prefix + "cash", "1500"))));
        }
        return new BusinessCatalog(types, companies);
    }

    private static List<String> list(String s) {
        return Arrays.stream(s.split(",")).map(String::trim).filter(v -> !v.isEmpty()).toList();
    }

    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
        return value.trim();
    }

    public void write(DataOutput out) throws IOException {
        out.writeInt(types.size());
        for (var t : types) {
            out.writeByte(t.id);
            out.writeUTF(t.name);
            out.writeDouble(t.rate);
            out.writeInt(t.capacity);
            out.writeInt(t.harvest.size());
            for (int m : t.harvest) out.writeInt(m);
        }
        out.writeInt(companies.size());
        for (var c : companies) {
            out.writeUTF(c.key);
            out.writeUTF(c.name);
            out.writeByte(c.type);
            out.writeDouble(c.cash);
        }
    }

    public static BusinessCatalog read(DataInput in) throws IOException {
        try {
            var types = new ArrayList<Type>();
            for (int n = count(in, 64); n > 0; n--) {
                int id = in.readUnsignedByte();
                String name = in.readUTF();
                double rate = in.readDouble();
                int capacity = in.readInt();
                var harvest = new ArrayList<Integer>();
                for (int m = count(in, 8); m > 0; m--) harvest.add(in.readInt());
                types.add(new Type(id, name, harvest, rate, capacity));
            }
            var companies = new ArrayList<Company>();
            for (int n = count(in, 128); n > 0; n--)
                companies.add(
                        new Company(
                                in.readUTF(),
                                in.readUTF(),
                                in.readUnsignedByte(),
                                in.readDouble()));
            return new BusinessCatalog(types, companies);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid business catalog", e);
        }
    }

    private static int count(DataInput in, int max) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > max) throw new IOException("Invalid business count");
        return n;
    }
}
