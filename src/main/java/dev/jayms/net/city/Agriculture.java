package dev.jayms.net.city;

import dev.jayms.net.Blocks;

import java.io.*;
import java.util.*;

/** Farmer-owned fields and finite livestock. All production needs paid, on-site labour. */
public final class Agriculture {
    public record Family(int company, List<Integer> members) {
        public Family {
            members = List.copyOf(members);
        }
    }

    public record Field(
            int building,
            int company,
            int product,
            int x,
            int y,
            int z,
            float growth,
            long harvests) {}

    public record Cow(
            int id,
            int building,
            int company,
            boolean female,
            float x,
            float y,
            float z,
            float yaw,
            float phase,
            double age,
            double fed) {}

    public record Farm(
            int building,
            int company,
            double breeding,
            double milking,
            long births,
            long slaughtered) {}

    public record State(
            boolean enabled,
            boolean pending,
            List<Family> families,
            List<Field> fields,
            List<Cow> cows,
            List<Farm> farms) {
        public State {
            families = List.copyOf(families);
            fields = List.copyOf(fields);
            cows = List.copyOf(cows);
            farms = List.copyOf(farms);
        }

        public static State empty() {
            return new State(false, false, List.of(), List.of(), List.of(), List.of());
        }

        public static State migration(ProductionCatalog catalog) {
            return new State(
                    catalog.agriculture(),
                    catalog.equals(ProductionCatalog.toolEra()) || catalog.agriculture(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of());
        }
    }

    /** ECS membership identifies family labour; livestock is a separate population entity. */
    public record Farmer(int company) {}

    public record Livestock(int company, int building) {}

    private final Ecs ecs;
    private final List<Family> families = new ArrayList<>();
    private final List<Field> fields = new ArrayList<>();
    private final List<Cow> cows = new ArrayList<>();
    private final List<Farm> farms = new ArrayList<>();
    private boolean enabled, pending;

    public void demolish(int building) {
        fields.removeIf(f -> f.building() == building);
        farms.removeIf(f -> f.building() == building);
        for (var cow : new ArrayList<>(cows))
            if (cow.building() == building) {
                ecs.remove(cow.id());
                cows.remove(cow);
            }
    }

    public Agriculture(Ecs ecs, State state) {
        this.ecs = ecs;
        enabled = state.enabled;
        pending = state.pending;
        families.addAll(state.families);
        fields.addAll(state.fields);
        cows.addAll(state.cows);
        farms.addAll(state.farms);
        for (var f : families)
            for (int id : f.members) ecs.put(id, Farmer.class, new Farmer(f.company));
        for (var c : cows) {
            ecs.restore(c.id);
            ecs.put(c.id, Livestock.class, new Livestock(c.company, c.building));
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean pending() {
        return pending;
    }

    public void initialize(CityEconomy economy, int grade) {
        if (pending && economy.resources.catalog.equals(ProductionCatalog.toolEra()))
            economy.resources.catalog = ProductionCatalog.cityGame();
        enabled = economy.resources.catalog.agriculture();
        pending = false;
        if (!enabled) return;
        economy.ensureIndustries();
        String[][] names = {
            {"Hazel Meadow", "Ash Meadow"},
            {"Willow Reed", "Finley Reed"},
            {"Briar Brook", "Ellis Brook"}
        };
        int n = 0;
        for (var firm : economy.companies())
            if (CityMaterials.farmer(firm.kind)) {
                if (families.stream().anyMatch(f -> f.company == firm.id)) continue;
                var members = new ArrayList<Integer>();
                for (String name : names[n++ % 3]) {
                    int id = ecs.create();
                    members.add(id);
                    ecs.put(
                            id,
                            CitySimulation.Position.class,
                            new CitySimulation.Position(9.5f, grade + 1.01f, 24.5f));
                    ecs.put(
                            id,
                            CitySimulation.Household.class,
                            new CitySimulation.Household(name, 1));
                    ecs.put(id, CitySimulation.Needs.class, new CitySimulation.Needs(36));
                    ecs.put(id, CitySimulation.Travel.class, new CitySimulation.Travel());
                    ecs.put(id, Farmer.class, new Farmer(firm.id));
                }
                families.add(new Family(firm.id, members));
            }
    }

    public int company(int citizen) {
        var f = ecs.get(citizen, Farmer.class);
        return f == null ? 0 : f.company;
    }

    public void completed(CityFrame.Building b, CityEconomy economy) {
        if (b.type() != 3 || farms.stream().anyMatch(f -> f.building == b.id())) return;
        var property = economy.property(b.id());
        var firm = economy.company(property.owner());
        boolean foundingHerd = farms.stream().noneMatch(f -> f.company == firm.id);
        farms.add(new Farm(b.id(), firm.id, 0, 0, 0, 0));
        if (firm.kind == CityMaterials.FARM) {
            fields.add(
                    new Field(
                            b.id(),
                            firm.id,
                            CityMaterials.WHEAT,
                            b.x() + 7,
                            b.y() + 1,
                            b.z() + 1,
                            0,
                            0));
            fields.add(
                    new Field(
                            b.id(),
                            firm.id,
                            CityMaterials.CARROT,
                            b.x() + 7,
                            b.y() + 1,
                            b.z() + 8,
                            0,
                            0));
        } else if (firm.kind == CityMaterials.SUGARCANE_FARM) {
            fields.add(
                    new Field(
                            b.id(),
                            firm.id,
                            CityMaterials.SUGARCANE,
                            b.x() + 7,
                            b.y() + 1,
                            b.z() + 1,
                            0,
                            0));
            fields.add(
                    new Field(
                            b.id(),
                            firm.id,
                            CityMaterials.SUGARCANE,
                            b.x() + 7,
                            b.y() + 1,
                            b.z() + 8,
                            0,
                            0));
        } else if (foundingHerd) {
            // Founding private breeding stock; expanding a farm never creates a free herd.
            for (int i = 0; i < 4; i++) spawn(b, firm.id, i < 2, 48);
        }
    }

    private void spawn(CityFrame.Building b, int company, boolean female, double age) {
        int id = ecs.create();
        ecs.put(id, Livestock.class, new Livestock(company, b.id()));
        cows.add(
                new Cow(
                        id,
                        b.id(),
                        company,
                        female,
                        b.x() + 8.5f,
                        b.y() + 1.01f,
                        b.z() + 3.5f + (id % 4) * 2,
                        0,
                        0,
                        age,
                        0));
    }

    public void tick(double hours) {
        for (int i = 0; i < cows.size(); i++) {
            var c = cows.get(i);
            // A bounded grazing circuit stays inside the actual privately built pen.

            double phase = c.phase + hours * .8;
            float dx = (float) (Math.sin(phase) - Math.sin(c.phase)) * .35f;
            float dz = (float) (Math.cos(phase) - Math.cos(c.phase)) * .35f;
            cows.set(
                    i,
                    new Cow(
                            c.id,
                            c.building,
                            c.company,
                            c.female,
                            c.x + dx,
                            c.y,
                            c.z + dz,
                            (float) Math.toDegrees(phase),
                            (float) phase,
                            c.age + hours,
                            Math.max(0, c.fed - hours)));
        }
    }

    public void work(
            CityFrame.Building b, CityEconomy economy, double hours, CitySimulation.Ground ground) {
        var p = economy.property(b.id());
        if (p == null || p.operator() == 0 || hours <= 0) return;
        int company = p.operator();
        var firm = economy.company(company);
        long harvested = 0;
        if (firm.kind != CityMaterials.CATTLE_FARM) {
            for (int i = 0; i < fields.size(); i++) {
                var field = fields.get(i);
                if (field.building != b.id()) continue;
                boolean soil = true;
                for (int dx = 0; dx < 4; dx++)
                    for (int dz = 0; dz < 4; dz++)
                        if (ground.type(field.x + dx, field.y - 1, field.z + dz) != Blocks.DIRT)
                            soil = false;
                if (!soil) continue;
                float growth = (float) Math.min(1, field.growth + hours / 4);
                long count = field.harvests;
                if (growth >= 1
                        && economy.resources.available(0, company, field.product)
                                < 480 * CityMaterials.UNIT) {
                    economy.resources.add(0, company, field.product, 32 * CityMaterials.UNIT);
                    harvested += 32;
                    count++;
                    growth = 0;
                }
                fields.set(
                        i,
                        new Field(
                                field.building,
                                company,
                                field.product,
                                field.x,
                                field.y,
                                field.z,
                                growth,
                                count));
            }
        } else {
            int index = -1;
            for (int i = 0; i < farms.size(); i++) if (farms.get(i).building == b.id()) index = i;
            if (index < 0) return;
            var old = farms.get(index);
            // Grain is purchased from crop farmers. No feed means no milk or breeding.
            for (int i = 0; i < cows.size(); i++) {
                var c = cows.get(i);
                if (c.building != b.id() || c.fed > 0) continue;
                if (economy.purchase(company, CityMaterials.WHEAT, CityMaterials.UNIT)
                        && economy.resources.remove(
                                0, company, CityMaterials.WHEAT, CityMaterials.UNIT))
                    cows.set(
                            i,
                            new Cow(
                                    c.id,
                                    c.building,
                                    c.company,
                                    c.female,
                                    c.x,
                                    c.y,
                                    c.z,
                                    c.yaw,
                                    c.phase,
                                    c.age,
                                    8));
            }
            long females =
                    cows.stream()
                            .filter(
                                    c ->
                                            c.building == b.id()
                                                    && c.female
                                                    && c.age >= 24
                                                    && c.fed > 0)
                            .count();
            boolean male =
                    cows.stream()
                            .anyMatch(
                                    c ->
                                            c.building == b.id()
                                                    && !c.female
                                                    && c.age >= 24
                                                    && c.fed > 0);
            double milk = old.milking + hours * females * 2, breed = old.breeding;
            long born = old.births, slaughter = old.slaughtered;
            int quantity = (int) milk;
            milk -= quantity;
            quantity =
                    (int)
                            Math.min(
                                    quantity,
                                    Math.max(
                                            0,
                                            128
                                                    - economy.resources.available(
                                                                    0, company, CityMaterials.MILK)
                                                            / CityMaterials.UNIT));
            if (quantity > 0) {
                economy.resources.add(
                        0, company, CityMaterials.MILK, quantity * CityMaterials.UNIT);
                harvested += quantity;
            }
            if (male && females > 0 && cows.stream().filter(c -> c.building == b.id()).count() < 12)
                breed += hours;
            if (breed >= 12) {
                spawn(b, company, born % 2 == 0, 0);
                born++;
                breed -= 12;
            }
            var adult =
                    cows.stream()
                            .filter(c -> c.building == b.id() && c.age >= 24 && c.fed > 0)
                            .toList();
            if (adult.size() > 3
                    && economy.resources.available(0, company, CityMaterials.BEEF)
                            < 120 * CityMaterials.UNIT) {
                var victim =
                        adult.stream()
                                .filter(c -> !c.female)
                                .skip(1)
                                .findFirst()
                                .orElse(
                                        adult.stream()
                                                .filter(Cow::female)
                                                .skip(2)
                                                .findFirst()
                                                .orElse(null));
                if (victim != null) {
                    cows.remove(victim);
                    ecs.remove(victim.id);
                    economy.resources.add(0, company, CityMaterials.BEEF, 8 * CityMaterials.UNIT);
                    harvested += 8;
                    slaughter++;
                }
            }
            farms.set(index, new Farm(b.id(), company, breed, milk, born, slaughter));
        }
        var production = economy.resources.production(company);
        economy.resources.production(
                company,
                production.progress(),
                harvested,
                0,
                harvested > 0 ? "Harvesting farm produce" : "Tending fields / livestock");
        if (harvested > 0) economy.businesses.produced(b.id(), (int) harvested);
    }

    public State state() {
        return new State(enabled, pending, families, fields, cows, farms);
    }

    public static void write(DataOutput out, State s) throws IOException {
        out.writeBoolean(s.enabled);
        out.writeBoolean(s.pending);
        out.writeInt(s.families.size());
        for (var f : s.families) {
            out.writeInt(f.company);
            out.writeInt(f.members.size());
            for (int id : f.members) out.writeInt(id);
        }
        out.writeInt(s.fields.size());
        for (var f : s.fields) {
            out.writeInt(f.building);
            out.writeInt(f.company);
            out.writeInt(f.product);
            out.writeInt(f.x);
            out.writeInt(f.y);
            out.writeInt(f.z);
            out.writeFloat(f.growth);
            out.writeLong(f.harvests);
        }
        out.writeInt(s.cows.size());
        for (var c : s.cows) {
            out.writeInt(c.id);
            out.writeInt(c.building);
            out.writeInt(c.company);
            out.writeBoolean(c.female);
            out.writeFloat(c.x);
            out.writeFloat(c.y);
            out.writeFloat(c.z);
            out.writeFloat(c.yaw);
            out.writeFloat(c.phase);
            out.writeDouble(c.age);
            out.writeDouble(c.fed);
        }
        out.writeInt(s.farms.size());
        for (var f : s.farms) {
            out.writeInt(f.building);
            out.writeInt(f.company);
            out.writeDouble(f.breeding);
            out.writeDouble(f.milking);
            out.writeLong(f.births);
            out.writeLong(f.slaughtered);
        }
    }

    private static int count(DataInput in, int limit) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > limit) throw new IOException("Invalid agriculture count");
        return n;
    }

    private static double number(DataInput in) throws IOException {
        double n = in.readDouble();
        if (!Double.isFinite(n) || n < 0 || n > 1e12)
            throw new IOException("Invalid farm progress");
        return n;
    }

    private static float coordinate(DataInput in) throws IOException {
        float v = in.readFloat();
        if (!Float.isFinite(v) || Math.abs(v) > 1e7)
            throw new IOException("Invalid cow coordinate");
        return v;
    }

    public static State read(
            DataInput in,
            CityEconomy.State economy,
            List<CityFrame.Building> buildings,
            List<CityFrame.Citizen> citizens,
            List<CityFrame.Horse> horses)
            throws IOException {
        boolean enabled = in.readBoolean(), pending = in.readBoolean();
        var families = new ArrayList<Family>();
        var fields = new ArrayList<Field>();
        var cows = new ArrayList<Cow>();
        var farms = new ArrayList<Farm>();
        var owners = new HashSet<Integer>();
        var members = new HashSet<Integer>();
        var entities = new HashSet<Integer>();
        var fieldKeys = new HashSet<String>();
        for (var c : citizens) entities.add(c.id());
        for (var h : horses) entities.add(h.id());
        for (var f : economy.firms()) entities.add(f.id());
        for (int n = count(in, 32); n > 0; n--) {
            int company = in.readInt();
            var people = new ArrayList<Integer>();
            if (!owners.add(company)
                    || economy.firms().stream()
                            .noneMatch(f -> f.id() == company && CityMaterials.farmer(f.kind())))
                throw new IOException("Invalid farm family");
            for (int m = count(in, 8); m > 0; m--) {
                int id = in.readInt();
                if (!members.add(id) || citizens.stream().noneMatch(c -> c.id() == id))
                    throw new IOException("Invalid farmer");
                people.add(id);
            }
            families.add(new Family(company, people));
        }
        for (int n = count(in, 1024); n > 0; n--) {
            var f =
                    new Field(
                            in.readInt(),
                            in.readInt(),
                            in.readInt(),
                            in.readInt(),
                            in.readInt(),
                            in.readInt(),
                            coordinate(in),
                            in.readLong());
            validateOwner(f.building, f.company, economy, buildings);
            if (f.growth < 0
                    || f.growth > 1
                    || f.harvests < 0
                    || !(f.product == CityMaterials.WHEAT
                            || f.product == CityMaterials.CARROT
                            || f.product == CityMaterials.SUGARCANE))
                throw new IOException("Invalid crop");
            var b = buildings.stream().filter(v -> v.id() == f.building).findFirst().orElseThrow();
            var owner =
                    economy.firms().stream()
                            .filter(v -> v.id() == f.company)
                            .findFirst()
                            .orElseThrow();
            if (!fieldKeys.add(f.building + ":" + f.x + ":" + f.z)
                    || f.x < b.x() + 6
                    || (long) f.x + 4 > b.x() + 12
                    || f.z < b.z()
                    || (long) f.z + 4 > b.z() + 14
                    || f.y != b.y() + 1
                    || owner.kind() == CityMaterials.CATTLE_FARM
                    || ((f.product == CityMaterials.SUGARCANE)
                            != (owner.kind() == CityMaterials.SUGARCANE_FARM)))
                throw new IOException("Crop outside owned field");
            fields.add(f);
        }
        for (int n = count(in, 256); n > 0; n--) {
            var c =
                    new Cow(
                            in.readInt(),
                            in.readInt(),
                            in.readInt(),
                            in.readBoolean(),
                            coordinate(in),
                            coordinate(in),
                            coordinate(in),
                            coordinate(in),
                            coordinate(in),
                            number(in),
                            number(in));
            validateOwner(c.building, c.company, economy, buildings);
            if (c.id <= 0 || !entities.add(c.id) || c.fed > 8)
                throw new IOException("Invalid livestock");
            var b = buildings.stream().filter(v -> v.id() == c.building).findFirst().orElseThrow();
            if (c.x < b.x() + 6
                    || c.x > b.x() + 12
                    || c.z < b.z()
                    || c.z > b.z() + 14
                    || Math.abs(c.y - b.y() - 1.01f) > .1
                    || economy.firms().stream()
                            .noneMatch(
                                    f ->
                                            f.id() == c.company
                                                    && f.kind() == CityMaterials.CATTLE_FARM))
                throw new IOException("Cow outside owned cattle pen");
            cows.add(c);
        }
        var ids = new HashSet<Integer>();
        for (int n = count(in, 512); n > 0; n--) {
            var f =
                    new Farm(
                            in.readInt(),
                            in.readInt(),
                            number(in),
                            number(in),
                            in.readLong(),
                            in.readLong());
            validateOwner(f.building, f.company, economy, buildings);
            if (!ids.add(f.building)
                    || f.breeding >= 12
                    || f.milking >= 1
                    || f.births < 0
                    || f.slaughtered < 0) throw new IOException("Invalid farm state");
            farms.add(f);
        }
        for (var f : fields)
            if (!ids.contains(f.building)) throw new IOException("Crop has no farm");
        for (var c : cows) if (!ids.contains(c.building)) throw new IOException("Cow has no farm");
        if (enabled)
            for (var b : buildings)
                if (b.type() == 3) {
                    var property =
                            economy.properties().stream()
                                    .filter(p -> p.building() == b.id())
                                    .findFirst()
                                    .orElse(null);
                    if (property == null || !ids.contains(b.id()))
                        throw new IOException("Untracked agricultural building");
                    validateOwner(b.id(), property.owner(), economy, buildings);
                }
        if (!enabled
                && !pending
                && (!families.isEmpty()
                        || !fields.isEmpty()
                        || !cows.isEmpty()
                        || !farms.isEmpty()))
            throw new IOException("Disabled agriculture has entities");
        return new State(enabled, pending, families, fields, cows, farms);
    }

    private static void validateOwner(
            int building, int company, CityEconomy.State e, List<CityFrame.Building> bs)
            throws IOException {
        if (bs.stream().noneMatch(b -> b.id() == building && b.type() == 3)
                || e.properties().stream()
                        .noneMatch(
                                p ->
                                        p.building() == building
                                                && p.ownerKind() == 0
                                                && p.owner() == company
                                                && p.operator() == company)
                || e.firms().stream()
                        .noneMatch(f -> f.id() == company && CityMaterials.farmer(f.kind())))
            throw new IOException("Farm must belong to its farmer");
    }
}
