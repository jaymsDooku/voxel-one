package dev.jayms.net.city;

import java.io.*;
import java.util.*;

/** Public treasury and private balance sheets. Transfers never mint wages or rent. */
public final class CityEconomy {
    public static final int DEVELOPER = 0, SHOP = 1, MINE = 2, COMPANY = 0, CITIZEN = 1;
    public static final double MAX_BALANCE = 1e12;
    public static final double INITIAL_BUDGET = 10000, ROAD_COST = 4;

    public static final class Company {
        public final int id, kind;
        public final String name;
        public double cash, land, materials, wages, receipts;

        Company(int id, String name, int kind, double cash) {
            this.id = id;
            this.name = name;
            this.kind = kind;
            this.cash = cash;
        }
    }

    public record Firm(
            int id,
            String name,
            int kind,
            double cash,
            double land,
            double materials,
            double wages,
            double receipts) {}

    public record Plot(
            int id,
            int zone,
            int type,
            int x,
            int y,
            int z,
            int developer,
            double landPrice,
            double constructionCost,
            float work,
            int building) {
        public Plot progress(float value) {
            return new Plot(
                    id,
                    zone,
                    type,
                    x,
                    y,
                    z,
                    developer,
                    landPrice,
                    constructionCost,
                    value,
                    building);
        }

        public Plot complete(int value) {
            return new Plot(
                    id, zone, type, x, y, z, developer, landPrice, constructionCost, work, value);
        }
    }

    public record Property(
            int building, int ownerKind, int owner, int operator, double price, double rent) {}

    public record Contract(int building, int partyKind, int party, boolean sale, double amount) {}

    public record State(
            double budget,
            double roadSpending,
            double landRevenue,
            double rentClock,
            List<Firm> firms,
            List<Plot> plots,
            List<Property> properties,
            List<Contract> contracts,
            List<CityBusinesses.Record> businesses,
            CityMaterials.State resources,
            CityCapital.State capital) {
        public State(
                double budget,
                double roadSpending,
                double landRevenue,
                double rentClock,
                List<Firm> firms,
                List<Plot> plots,
                List<Property> properties,
                List<Contract> contracts,
                List<CityBusinesses.Record> businesses,
                CityMaterials.State resources) {
            this(
                    budget,
                    roadSpending,
                    landRevenue,
                    rentClock,
                    firms,
                    plots,
                    properties,
                    contracts,
                    businesses,
                    resources,
                    CityCapital.State.empty());
        }

        public State(
                double budget,
                double roadSpending,
                double landRevenue,
                double rentClock,
                List<Firm> firms,
                List<Plot> plots,
                List<Property> properties,
                List<Contract> contracts,
                List<CityBusinesses.Record> businesses) {
            this(
                    budget,
                    roadSpending,
                    landRevenue,
                    rentClock,
                    firms,
                    plots,
                    properties,
                    contracts,
                    businesses,
                    CityMaterials.State.empty());
        }

        public State(
                double budget,
                double roadSpending,
                double landRevenue,
                double rentClock,
                List<Firm> firms,
                List<Plot> plots,
                List<Property> properties,
                List<Contract> contracts) {
            this(
                    budget,
                    roadSpending,
                    landRevenue,
                    rentClock,
                    firms,
                    plots,
                    properties,
                    contracts,
                    List.of());
        }

        public State {
            Objects.requireNonNull(resources);
            Objects.requireNonNull(capital);
            businesses = List.copyOf(businesses);
            firms = List.copyOf(firms);
            plots = List.copyOf(plots);
            properties = List.copyOf(properties);
            contracts = List.copyOf(contracts);
        }

        public static State empty() {
            return new State(INITIAL_BUDGET, 0, 0, 0, List.of(), List.of(), List.of(), List.of());
        }

        public void write(DataOutput out) throws IOException {
            write(out, 8);
        }

        public void write(DataOutput out, boolean legacy) throws IOException {
            write(out, legacy ? 2 : 8);
        }

        public void write(DataOutput out, int version) throws IOException {
            out.writeDouble(budget);
            out.writeDouble(roadSpending);
            out.writeDouble(landRevenue);
            out.writeDouble(rentClock);
            out.writeInt(firms.size());
            for (var f : firms) {
                out.writeInt(f.id);
                out.writeUTF(f.name);
                out.writeByte(f.kind);
                out.writeDouble(f.cash);
                out.writeDouble(f.land);
                out.writeDouble(f.materials);
                out.writeDouble(f.wages);
                out.writeDouble(f.receipts);
            }
            out.writeInt(plots.size());
            for (var p : plots) {
                out.writeInt(p.id);
                out.writeInt(p.zone);
                out.writeByte(p.type);
                out.writeInt(p.x);
                out.writeInt(p.y);
                out.writeInt(p.z);
                out.writeInt(p.developer);
                out.writeDouble(p.landPrice);
                out.writeDouble(p.constructionCost);
                out.writeFloat(p.work);
                out.writeInt(p.building);
            }
            out.writeInt(properties.size());
            for (var p : properties) {
                out.writeInt(p.building);
                out.writeByte(p.ownerKind);
                out.writeInt(p.owner);
                out.writeInt(p.operator);
                out.writeDouble(p.price);
                out.writeDouble(p.rent);
            }
            out.writeInt(contracts.size());
            for (var c : contracts) {
                out.writeInt(c.building);
                out.writeByte(c.partyKind);
                out.writeInt(c.party);
                out.writeBoolean(c.sale);
                out.writeDouble(c.amount);
            }
            if (version >= 3) CityBusinesses.write(out, businesses);
            if (version >= 4) CityMaterials.write(out, resources, version);
            if (version >= 8) CityCapital.write(out, capital);
        }

        public static State read(DataInput in) throws IOException {
            return read(in, 8);
        }

        public static State read(DataInput in, boolean legacy) throws IOException {
            return read(in, legacy ? 2 : 8);
        }

        public static State read(DataInput in, int version) throws IOException {
            double budget = money(in), roads = money(in), land = money(in), clock = money(in);
            var firms = new ArrayList<Firm>();
            for (int i = 0, n = count(in, 128); i < n; i++) {
                int id = id(in);
                String name = in.readUTF();
                int kind = in.readUnsignedByte();
                if (name.length() > 48 || kind > CityMaterials.MAX_KIND)
                    throw new IOException("Invalid company");
                firms.add(
                        new Firm(
                                id, name, kind, money(in), money(in), money(in), money(in),
                                money(in)));
            }
            var plots = new ArrayList<Plot>();
            for (int i = 0, n = count(in, 512); i < n; i++) {
                int id = id(in),
                        zone = id(in),
                        type = in.readUnsignedByte(),
                        x = in.readInt(),
                        y = in.readInt(),
                        z = in.readInt(),
                        developer = id(in);
                double price = money(in), cost = money(in);
                float work = in.readFloat();
                int building = in.readInt();
                if (type > 3
                        || Math.abs((long) x - 8) > 256
                        || Math.abs((long) z - 24) > 256
                        || y < -27
                        || y > 89
                        || !Float.isFinite(work)
                        || work < 0
                        || work > 100
                        || building < 0) throw new IOException("Invalid plot");
                plots.add(
                        new Plot(id, zone, type, x, y, z, developer, price, cost, work, building));
            }
            var properties = new ArrayList<Property>();
            for (int i = 0, n = count(in, 512); i < n; i++) {
                int b = id(in),
                        kind = in.readUnsignedByte(),
                        owner = id(in),
                        operator = in.readInt();
                if (kind > 1 || operator < 0) throw new IOException("Invalid owner");
                properties.add(new Property(b, kind, owner, operator, money(in), money(in)));
            }
            var contracts = new ArrayList<Contract>();
            for (int i = 0, n = count(in, 1024); i < n; i++) {
                int b = id(in), kind = in.readUnsignedByte(), party = id(in);
                if (kind > 1) throw new IOException("Invalid contract");
                contracts.add(new Contract(b, kind, party, in.readBoolean(), money(in)));
            }
            var state =
                    new State(
                            budget,
                            roads,
                            land,
                            clock,
                            firms,
                            plots,
                            properties,
                            contracts,
                            version < 3 ? List.of() : CityBusinesses.read(in),
                            version < 4
                                    ? CityMaterials.State.empty()
                                    : CityMaterials.read(in, version),
                            version >= 8 ? CityCapital.read(in) : CityCapital.State.empty());
            if (version >= 7) {
                for (var firm : firms)
                    if (state.resources.catalog().businesses().type(firm.kind()) == null)
                        throw new IOException("Unknown business type");
            }
            for (var stock : state.resources.stocks())
                if (stock.ownerKind() == COMPANY
                        && firms.stream().noneMatch(f -> f.id() == stock.owner()))
                    throw new IOException("Unknown material company");
            for (var project : state.resources.projects()) {
                var plot =
                        plots.stream()
                                .filter(p -> p.id() == project.plot())
                                .findFirst()
                                .orElse(null);
                if (plot == null
                        || !project.materials()
                                .equals(
                                        CityMaterials.requirements(
                                                plot.type(), project.businessKind()))
                        || project.reserved() && plot.building() != 0
                        || project.consumed() && plot.building() == 0)
                    throw new IOException("Invalid construction material recipe");
            }
            for (var production : state.resources.production())
                if (firms.stream()
                        .noneMatch(f -> f.id() == production.company() && f.kind() >= MINE))
                    throw new IOException("Unknown harvesting company");
            for (var batch : state.resources.batches())
                if (firms.stream()
                        .noneMatch(
                                f ->
                                        f.id() == batch.company()
                                                && state
                                                        .resources
                                                        .catalog()
                                                        .recipes(f.kind())
                                                        .stream()
                                                        .anyMatch(
                                                                r ->
                                                                        r.id().equals(
                                                                                        batch
                                                                                                .recipe()))))
                    throw new IOException("Unknown manufacturing company / recipe");
            return state;
        }

        private static double money(DataInput in) throws IOException {
            double n = in.readDouble();
            if (!Double.isFinite(n) || n < 0 || n > MAX_BALANCE)
                throw new IOException("Invalid balance");
            return n;
        }

        private static int count(DataInput in, int max) throws IOException {
            int n = in.readInt();
            if (n < 0 || n > max) throw new IOException("Invalid economy count");
            return n;
        }

        private static int id(DataInput in) throws IOException {
            int n = in.readInt();
            if (n < 1) throw new IOException("Invalid economy reference");
            return n;
        }
    }

    private final Ecs ecs;
    public final CityBusinesses businesses;
    public final CityMaterials resources;
    public final CityCapital capital;
    public double budget = INITIAL_BUDGET, roadSpending, landRevenue, rentClock;
    public final List<Plot> plots = new ArrayList<>();
    public final List<Property> properties = new ArrayList<>();
    public final List<Contract> contracts = new ArrayList<>();

    public CityEconomy(Ecs ecs, State state) {
        this(ecs, state, ProductionCatalog.cityGame());
    }

    public CityEconomy(Ecs ecs, State state, ProductionCatalog catalog) {
        this.ecs = ecs;
        businesses = new CityBusinesses(state == null ? List.of() : state.businesses());
        resources =
                new CityMaterials(
                        state == null
                                ? new CityMaterials.State(List.of(), List.of(), List.of(), catalog)
                                : state.resources());
        if (state != null) {
            budget = state.budget;
            roadSpending = state.roadSpending;
            landRevenue = state.landRevenue;
            rentClock = state.rentClock;
            for (var f : state.firms) {
                ecs.restore(f.id);
                var c = new Company(f.id, f.name, f.kind, f.cash);
                c.land = f.land;
                c.materials = f.materials;
                c.wages = f.wages;
                c.receipts = f.receipts;
                ecs.put(f.id, Company.class, c);
            }
            plots.addAll(state.plots);
            properties.addAll(state.properties);
            contracts.addAll(state.contracts);
        }
        // Legacy city frames had no economy firms. Seed their catalog before adopt() while
        // respecting configured catalogs whose starting roster is intentionally empty.
        if (state == null || state.firms().isEmpty()) ensureIndustries();
        capital =
                new CityCapital(this, state == null ? CityCapital.State.empty() : state.capital());
    }

    /** Idempotently seed configured companies; saved accounts and cash are never replaced. */
    public void ensureIndustries() {
        for (var seed : resources.catalog.businesses().companies())
            if (companies().stream()
                    .noneMatch(c -> c.kind == seed.type() && c.name.equals(seed.name())))
                create(seed.name(), seed.type(), seed.cash());
    }

    private void create(String name, int kind, double cash) {
        int id = ecs.create();
        ecs.put(id, Company.class, new Company(id, name, kind, cash));
    }

    public List<Company> companies() {
        return ecs.query(Company.class).stream().map(id -> ecs.get(id, Company.class)).toList();
    }

    public Company company(int id) {
        return ecs.get(id, Company.class);
    }

    public Property property(int building) {
        return properties.stream().filter(p -> p.building == building).findFirst().orElse(null);
    }

    public Plot project(int id) {
        return plots.stream().filter(p -> p.id == id && p.building == 0).findFirst().orElse(null);
    }

    public boolean overlaps(int x, int z) {
        return overlaps(x, z, 6, 7);
    }

    public boolean overlaps(int x, int z, int width, int depth) {
        return plots.stream()
                .anyMatch(
                        p ->
                                p.building == 0
                                        && x - 1 < p.x + StructureBlueprint.width(p.type) + 1
                                        && x + width + 1 > p.x - 1
                                        && z - 2 < p.z + StructureBlueprint.depth(p.type) + 1
                                        && z + depth + 1 > p.z - 2);
    }

    public boolean roads(int cells) {
        double cost = cells * ROAD_COST;
        if (budget < cost) return false;
        budget -= cost;
        roadSpending += cost;
        return true;
    }

    public Plot buyPlot(int zone, int type, int x, int y, int z) {
        return buyPlot(zone, type, x, y, z, type);
    }

    public Plot buyPlot(int zone, int type, int x, int y, int z, int businessKind) {
        if (type == 3 && !CityMaterials.farmer(businessKind)) return null;
        double land =
                (type == 3 ? 48 : 24)
                        * pressure(
                                ecs.query(CitySimulation.Household.class).size(),
                                16 + plots.stream().filter(p -> p.type() == type).count() * 4);
        var recipe = CityMaterials.requirements(type, businessKind);
        double cost =
                recipe.stream()
                        .mapToDouble(
                                a ->
                                        a.units()
                                                / (double) CityMaterials.UNIT
                                                * marketPrice(a.material()))
                        .sum();
        var developer =
                companies().stream()
                        .filter(
                                c ->
                                        c.kind == (type == 3 ? businessKind : DEVELOPER)
                                                && c.cash
                                                        >= land
                                                                + 10
                                                                + recipe.stream()
                                                                        .mapToDouble(
                                                                                a ->
                                                                                        Math.max(
                                                                                                        0,
                                                                                                        a
                                                                                                                        .units()
                                                                                                                - resources
                                                                                                                        .available(
                                                                                                                                COMPANY,
                                                                                                                                c.id,
                                                                                                                                a
                                                                                                                                        .material()))
                                                                                                / (double)
                                                                                                        CityMaterials
                                                                                                                .UNIT
                                                                                                * marketPrice(
                                                                                                        a
                                                                                                                .material()))
                                                                        .sum())
                        .max(Comparator.comparingDouble(c -> c.cash))
                        .orElse(null);
        if (developer == null) return null;
        developer.cash -= land;
        developer.land += land;
        budget += land;
        landRevenue += land;
        var p =
                new Plot(
                        plots.stream().mapToInt(Plot::id).max().orElse(0) + 1,
                        zone,
                        type,
                        x,
                        y,
                        z,
                        developer.id,
                        land,
                        cost,
                        0,
                        0);
        plots.add(p);
        resources.plan(p, businessKind);
        return p;
    }

    public boolean wage(int company, double value, CitySimulation.Needs needs) {
        var firm = company(company);
        if (firm == null || !Double.isFinite(value) || value <= 0 || firm.cash < value)
            return false;
        firm.cash -= value;
        firm.wages += value;
        needs.money += (float) value;
        return true;
    }

    public boolean businessWage(
            int building, double value, double hours, CitySimulation.Needs needs) {
        var property = property(building);
        if (property == null || !wage(property.operator(), value, needs)) {
            businesses.missedWage(building);
            return false;
        }
        businesses.wage(building, value, hours);
        return true;
    }

    public void work(int plot, float dt) {
        var p = project(plot);
        if (p != null && resources.project(plot) != null && resources.project(plot).reserved())
            plots.set(plots.indexOf(p), p.progress(Math.min(100, p.work + dt)));
    }

    public void completed(Plot plot, int building) {
        plots.set(plots.indexOf(project(plot.id)), plot.complete(building));
        properties.add(
                new Property(
                        building,
                        COMPANY,
                        plot.developer,
                        plot.type == 3 ? plot.developer : 0,
                        plot.type == 0 ? 50 : plot.type == 1 ? 240 : 400,
                        plot.type == 3 ? 0 : plot.type == 0 ? .6 : 6));
    }

    /**
     * Adopt existing private buildings on save upgrade without charging the mayor or rebuilding.
     */
    public void adopt(List<CityFrame.Building> buildings) {
        for (var b : buildings)
            if (!SpecialBuildings.special(b.type()) && property(b.id()) == null) {
                var dev =
                        companies().stream()
                                .filter(c -> c.kind == DEVELOPER)
                                .toList()
                                .get((b.id() - 1) % 3);
                properties.add(
                        new Property(
                                b.id(),
                                COMPANY,
                                dev.id,
                                0,
                                b.type() == 0 ? 50 : b.type() == 1 ? 240 : 400,
                                b.type() == 0 ? .6 : 6));
            }
    }

    private void credit(Property p, double amount) {
        if (p.ownerKind == COMPANY) {
            var c = company(p.owner);
            c.cash += amount;
            c.receipts += amount;
        } else {
            var n = ecs.get(p.owner, CitySimulation.Needs.class);
            if (n != null) n.money += (float) amount;
        }
    }

    public int operate(CityFrame.Building b) {
        var p = property(b.id());
        if (p == null) return 0;
        if (p.operator != 0) {
            businesses.open(b.id(), p.operator);
            return p.operator;
        }
        int purpose =
                plots.stream()
                        .filter(plot -> plot.building() == b.id())
                        .map(plot -> resources.project(plot.id()))
                        .filter(Objects::nonNull)
                        .mapToInt(CityMaterials.Project::businessKind)
                        .findFirst()
                        .orElse(b.type());
        var operator = companies().stream().filter(c -> c.kind == purpose).findFirst().orElse(null);
        if (operator == null) return 0;
        boolean buy = b.type() == 1 && operator.cash >= p.price;
        double amount = buy ? p.price : p.rent;
        if (operator.cash < amount) return 0;
        operator.cash -= amount;
        businesses.open(b.id(), operator.id);
        if (!buy) businesses.rent(b.id(), amount);
        credit(p, amount);
        contracts.add(new Contract(b.id(), COMPANY, operator.id, buy, amount));
        properties.set(
                properties.indexOf(p),
                new Property(
                        b.id(),
                        buy ? COMPANY : p.ownerKind,
                        buy ? operator.id : p.owner,
                        operator.id,
                        p.price,
                        p.rent));
        return operator.id;
    }

    public boolean house(int citizen, CityFrame.Building b) {
        var p = property(b.id());
        var n = ecs.get(citizen, CitySimulation.Needs.class);
        var h = ecs.get(citizen, CitySimulation.Household.class);
        if (p == null) return false;
        boolean sale = p.ownerKind == COMPANY && h.cohort == 2 && n.money >= p.price;
        double amount = sale ? p.price : p.rent;
        if (n.money < amount) return false;
        n.money -= (float) amount;
        credit(p, amount);
        contracts.add(new Contract(b.id(), CITIZEN, citizen, sale, amount));
        if (sale)
            properties.set(
                    properties.indexOf(p),
                    new Property(b.id(), CITIZEN, citizen, 0, p.price, p.rent));
        return true;
    }

    public void rent(double dt) {
        rentClock += dt;
        while (rentClock >= 1) {
            rentClock -= 1;
            for (var contract : contracts) {
                if (contract.sale) continue;
                var p = property(contract.building);
                if (p == null || p.ownerKind == contract.partyKind && p.owner == contract.party)
                    continue;
                double amount = contract.amount;
                if (contract.partyKind == COMPANY) {
                    var c = company(contract.party);
                    if (c.cash < amount) {
                        businesses.missedRent(contract.building);
                        continue;
                    }
                    c.cash -= amount;
                    businesses.rent(contract.building, amount);
                } else {
                    var n = ecs.get(contract.party, CitySimulation.Needs.class);
                    if (n.money < amount) continue;
                    n.money -= (float) amount;
                }
                credit(p, amount);
            }
        }
    }

    public void meal(int building, double amount) {
        meal(building, 1, amount);
    }

    public void meal(int building, int portions, double amount) {
        var p = property(building);
        if (p != null && p.operator != 0) {
            var c = company(p.operator);
            c.cash += amount;
            c.receipts += amount;
            businesses.sale(building, portions, amount);
        }
    }

    public int account(int company) {
        int id =
                businesses.records().stream()
                        .filter(a -> a.company() == company && a.building() < CityMaterials.YARD)
                        .mapToInt(CityBusinesses.Record::building)
                        .findFirst()
                        .orElse(CityMaterials.YARD + company);
        businesses.open(id, company);
        return id;
    }

    private double cash(int kind, int owner) {
        var c = company(owner);
        var n = ecs.get(owner, CitySimulation.Needs.class);
        return kind == COMPANY ? c == null ? -1 : c.cash : n == null ? -1 : n.money;
    }

    private void cash(int kind, int owner, double value) {
        if (kind == COMPANY) company(owner).cash += value;
        else ecs.get(owner, CitySimulation.Needs.class).money += (float) value;
    }

    /**
     * Bounded clearing pressure; catalogue prices are reference values, not transaction prices.
     * Quotes derive from saved stock, projects and households, so reloads cannot reset a market.
     */
    private static double pressure(double demand, double supply) {
        return Math.max(.25, Math.min(4, Math.sqrt((demand + 1) / (supply + 1))));
    }

    public double marketPrice(int material) {
        double demand = 0;
        if (resources.catalog.nutrition(material) > 0)
            demand +=
                    ecs.query(CitySimulation.Needs.class).stream()
                                    .mapToDouble(
                                            id ->
                                                    Math.max(
                                                                    0,
                                                                    100
                                                                            - ecs.get(
                                                                                            id,
                                                                                            CitySimulation
                                                                                                    .Needs
                                                                                                    .class)
                                                                                    .hunger)
                                                            / resources.catalog.nutrition(material))
                                    .sum()
                            / resources.catalog.food().size();
        for (var plot : plots) {
            if (plot.building() != 0) continue;
            var project = resources.project(plot.id());
            if (project != null && project.reserved()) continue;
            var requirements =
                    project == null
                            ? CityMaterials.requirements(plot.type(), plot.type())
                            : project.materials();
            for (var a : requirements)
                if (a.material() == material)
                    demand +=
                            Math.max(
                                            0,
                                            a.units()
                                                    - resources.available(
                                                            COMPANY, plot.developer(), material))
                                    / (double) CityMaterials.UNIT;
        }
        for (var firm : companies())
            for (var recipe : resources.catalog.recipes(firm.kind))
                demand +=
                        Math.max(
                                        0,
                                        recipe.inputs().getOrDefault(material, 0)
                                                        * CityMaterials.UNIT
                                                - resources.available(COMPANY, firm.id, material))
                                / (double) CityMaterials.UNIT;
        for (var equipment : resources.catalog.equipment())
            if (equipment.product() == material)
                for (var firm : companies())
                    if (firm.kind == equipment.companyKind())
                        demand +=
                                Math.max(
                                                0,
                                                CityMaterials.UNIT
                                                        - resources.available(
                                                                COMPANY, firm.id, material))
                                        / (double) CityMaterials.UNIT;
        double supply = resources.availableSupply(material) / (double) CityMaterials.UNIT;
        return resources.catalog.price(material) * pressure(demand, supply);
    }

    /** Competing sellers discount abundant inventories and charge more for scarce stock. */
    private double sellerDiscount(int sellerKind, int seller, int material) {
        double stock =
                resources.available(sellerKind, seller, material) / (double) CityMaterials.UNIT;
        return .75 + 1 / Math.sqrt(stock + 1);
    }

    public double offer(int sellerKind, int seller, int material) {
        return marketPrice(material) * sellerDiscount(sellerKind, seller, material);
    }

    public record FoodOffer(int product, int portions, double price, int nutrition) {}

    public FoodOffer cheapestFood(int company, double money, int nutrition) {
        return resources.catalog.food().stream()
                .map(
                        product -> {
                            int value = resources.catalog.nutrition(product);
                            int portions =
                                    Math.max(1, (Math.max(1, nutrition) + value - 1) / value);
                            return new FoodOffer(
                                    product,
                                    portions,
                                    portions * offer(COMPANY, company, product),
                                    portions * value);
                        })
                .filter(
                        food ->
                                food.price() <= money
                                        && resources.available(COMPANY, company, food.product())
                                                >= food.portions() * CityMaterials.UNIT)
                .min(
                        Comparator.comparingDouble(FoodOffer::price)
                                .thenComparingInt(FoodOffer::product))
                .orElse(null);
    }

    /** Select an affordable meal before moving either inventory or money. */
    public FoodOffer buyMeal(int citizen, int building, int nutrition) {
        var p = property(building);
        var needs = ecs.get(citizen, CitySimulation.Needs.class);
        if (p == null || p.operator() == 0 || company(p.operator()) == null || needs == null)
            return null;
        var food = cheapestFood(p.operator(), needs.money, nutrition);
        if (food == null) return null;
        resources.remove(
                COMPANY, p.operator(), food.product(), food.portions() * CityMaterials.UNIT);
        needs.money -= (float) food.price();
        meal(building, food.portions(), food.price());
        needs.hunger = Math.min(100, needs.hunger + food.nutrition());
        return food;
    }

    public double housingCost(int citizen, int building) {
        var p = property(building);
        var needs = ecs.get(citizen, CitySimulation.Needs.class);
        var household = ecs.get(citizen, CitySimulation.Household.class);
        if (p == null || needs == null || household == null) return Double.POSITIVE_INFINITY;
        return p.ownerKind() == COMPANY && household.cohort == 2 && needs.money >= p.price()
                ? p.price()
                : p.rent();
    }

    public double labourRate(int company, double reference) {
        long workers =
                ecs.query(CitySimulation.Household.class).stream()
                        .filter(
                                id -> {
                                    int job = ecs.get(id, CitySimulation.Household.class).job;
                                    var p = job < 0 ? project(-job) : null;
                                    var b = job > 0 ? property(job) : null;
                                    return p != null && p.developer() == company
                                            || b != null && b.operator() == company
                                            || job == CityMaterials.YARD + company;
                                })
                        .count();
        long unemployed =
                ecs.query(CitySimulation.Household.class).stream()
                        .filter(id -> ecs.get(id, CitySimulation.Household.class).job == 0)
                        .count();
        long vacancies =
                plots.stream().filter(p -> p.building() == 0 && p.developer() == company).count()
                                * 2
                        + properties.stream().filter(p -> p.operator() == company).count() * 2;
        return reference * pressure(Math.max(2, vacancies), workers + unemployed);
    }

    /** Only new offers move; existing leases retain the negotiated contract amount. */
    public void priceProperties(List<CityFrame.Building> buildings) {
        for (var b : buildings) {
            var p = property(b.id());
            if (p == null) continue;
            long capacity =
                    buildings.stream()
                            .filter(a -> a.type() == b.type())
                            .mapToLong(CityFrame.Building::capacity)
                            .sum();
            long demand =
                    b.type() == 0
                            ? ecs.query(CitySimulation.Household.class).size()
                            : companies().stream()
                                            .filter(
                                                    c ->
                                                            b.type() == 3
                                                                    ? CityMaterials.farmer(c.kind)
                                                                    : b.type() == 2
                                                                            ? c.kind >= 2
                                                                                    && !CityMaterials
                                                                                            .farmer(
                                                                                                    c.kind)
                                                                            : c.kind == SHOP)
                                            .count()
                                    * 2;
            long occupied = contracts.stream().filter(c -> c.building() == b.id()).count();
            double factor = pressure(demand, capacity) * pressure(occupied + 1, b.capacity());
            properties.set(
                    properties.indexOf(p),
                    new Property(
                            p.building(),
                            p.ownerKind(),
                            p.owner(),
                            p.operator(),
                            (b.type() == 0 ? 50 : b.type() == 1 ? 240 : 400) * factor,
                            (b.type() == 3 ? 0 : b.type() == 0 ? .6 : 6) * factor));
        }
    }

    /** Seller stocks and buyer cash are validated before either is moved. */
    public boolean trade(
            int sellerKind, int seller, int buyerKind, int buyer, int material, long units) {
        return trade(
                sellerKind,
                seller,
                buyerKind,
                buyer,
                material,
                units,
                offer(sellerKind, seller, material));
    }

    private boolean trade(
            int sellerKind,
            int seller,
            int buyerKind,
            int buyer,
            int material,
            long units,
            double price) {
        if (sellerKind < 0
                || sellerKind > CITIZEN
                || buyerKind < 0
                || buyerKind > CITIZEN
                || sellerKind == buyerKind && seller == buyer
                || !resources.catalog.valid(material)
                || units <= 0
                || units > 1_000_000_000L
                || resources.available(sellerKind, seller, material) < units) return false;
        double amount = units / (double) CityMaterials.UNIT * price;
        if (cash(sellerKind, seller) < 0 || cash(buyerKind, buyer) < amount) return false;
        if (resources.available(buyerKind, buyer, material) > 1_000_000_000L - units) return false;
        resources.remove(sellerKind, seller, material, units);
        resources.add(buyerKind, buyer, material, units);
        cash(buyerKind, buyer, -amount);
        cash(sellerKind, seller, amount);
        int count = (int) Math.max(1, units / CityMaterials.UNIT);
        if (sellerKind == COMPANY) {
            company(seller).receipts += amount;
            businesses.sale(account(seller), count, amount);
        }
        if (buyerKind == COMPANY) {
            if (company(buyer).kind == DEVELOPER) company(buyer).materials += amount;
            else businesses.delivery(account(buyer), count, amount);
        }
        return true;
    }

    public boolean purchase(int company, int material, long needed) {
        return purchase(COMPANY, company, material, needed);
    }

    public boolean purchase(int buyerKind, int buyer, int material, long needed) {
        if (buyerKind < COMPANY
                || buyerKind > CITIZEN
                || cash(buyerKind, buyer) < 0
                || !resources.catalog.valid(material)
                || needed < 0
                || needed > 1_000_000_000L) return false;
        long missing = needed - resources.available(buyerKind, buyer, material);
        if (missing <= 0) return true;
        for (var seller :
                companies().stream()
                        .sorted(
                                Comparator.comparingDouble(
                                                (Company c) ->
                                                        sellerDiscount(COMPANY, c.id, material))
                                        .thenComparingInt(c -> c.id))
                        .toList())
            if (!(buyerKind == COMPANY && seller.id == buyer)
                    && seller.kind != DEVELOPER
                    && seller.kind != SHOP
                    && !(CityMaterials.farmer(seller.kind)
                            && CityMaterials.buildingMaterial(material))) {
                double price = offer(COMPANY, seller.id, material);
                long affordable =
                        (long) Math.floor(cash(buyerKind, buyer) / price * CityMaterials.UNIT);
                long units =
                        Math.min(
                                affordable,
                                Math.min(
                                        missing,
                                        resources.available(COMPANY, seller.id, material)));
                if (units > 0
                        && trade(COMPANY, seller.id, buyerKind, buyer, material, units, price))
                    missing -= units;
                if (missing == 0) return true;
            }
        return false;
    }

    public boolean supply(Plot plot) {
        var project = resources.plan(plot, plot.type());
        if (project.reserved()) return true;
        for (var a : project.materials()) purchase(plot.developer(), a.material(), a.units());
        return resources.reserve(plot);
    }

    public boolean delivery(int mine, int shop) {
        var m = property(mine);
        var s = property(shop);
        if (m == null || s == null || m.operator == 0 || s.operator == 0) return false;
        return trade(
                COMPANY,
                m.operator,
                COMPANY,
                s.operator,
                resources.catalog.output(company(m.operator).kind),
                CityMaterials.UNIT);
    }

    public State state() {
        return new State(
                budget,
                roadSpending,
                landRevenue,
                rentClock,
                companies().stream()
                        .map(
                                c ->
                                        new Firm(
                                                c.id,
                                                c.name,
                                                c.kind,
                                                c.cash,
                                                c.land,
                                                c.materials,
                                                c.wages,
                                                c.receipts))
                        .toList(),
                plots,
                properties,
                contracts,
                businesses.records(),
                resources.state(),
                capital.state());
    }
}
