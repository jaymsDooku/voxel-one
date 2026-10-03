package dev.jayms.net.city;

import java.util.*;

/** Read-only operating indicators calculated from the same city snapshot as the population view. */
public record BusinessMetrics(List<Location> locations, List<Company> companies) {
    public record Location(
            CityFrame.Building building,
            CityEconomy.Firm firm,
            CityEconomy.Property property,
            CityBusinesses.Record account,
            List<CityFrame.Citizen> employees,
            int working,
            int onSite,
            String status,
            boolean attention,
            double dailyPayroll,
            String address) {
        public String name() {
            return (firm == null ? "Vacant workplace" : firm.name())
                    + (building.id() >= CityMaterials.YARD ? " | field yard" : " | " + address);
        }

        public int capacity() {
            return building.type() == 1 ? 2 : building.capacity();
        }

        public boolean open() {
            return status.equals("Open");
        }

        public CityBusinesses.Totals total() {
            return account == null ? CityBusinesses.Totals.empty() : account.total();
        }

        public CityBusinesses.Totals today() {
            return account == null ? CityBusinesses.Totals.empty() : account.today().totals();
        }
    }

    public record Company(
            CityEconomy.Firm firm,
            int employees,
            int locations,
            int properties,
            double revenue,
            double expenses,
            double profit) {}

    public BusinessMetrics {
        locations = List.copyOf(locations);
        companies = List.copyOf(companies);
    }

    public double operatingCash() {
        return companies.stream()
                .filter(c -> c.firm.kind() != 0)
                .mapToDouble(c -> c.firm.cash())
                .sum();
    }

    public double revenue() {
        return companies.stream().mapToDouble(Company::revenue).sum();
    }

    public double expenses() {
        return companies.stream().mapToDouble(Company::expenses).sum();
    }

    public int employees() {
        return locations.stream().mapToInt(l -> l.employees.size()).sum();
    }

    public static BusinessMetrics from(CityFrame city) {
        var time = city.config().time(city.elapsed());
        var locations = new ArrayList<Location>();
        var workplaces = new ArrayList<>(city.buildings());
        if (city.config().city())
            for (var f : city.economy().firms())
                if (f.kind() >= 2
                        && city.economy().properties().stream()
                                .noneMatch(p -> p.operator() == f.id()))
                    workplaces.add(
                            new CityFrame.Building(
                                    CityMaterials.YARD + f.id(),
                                    0,
                                    2,
                                    -8 + (f.kind() - 2) * 6,
                                    city.roads().isEmpty() ? 32 : city.roads().get(0).y(),
                                    23,
                                    1,
                                    (int)
                                            (city.economy()
                                                            .resources()
                                                            .available(
                                                                    0,
                                                                    f.id(),
                                                                    city.economy()
                                                                            .resources()
                                                                            .catalog()
                                                                            .output(f.kind()))
                                                    / CityMaterials.UNIT)));
        for (var b : workplaces)
            if (b.type() != 0 && b.type() != 4) {
                var property =
                        city.economy().properties().stream()
                                .filter(p -> p.building() == b.id())
                                .findFirst()
                                .orElse(null);
                if (b.id() >= CityMaterials.YARD)
                    property =
                            new CityEconomy.Property(
                                    b.id(),
                                    0,
                                    b.id() - CityMaterials.YARD,
                                    b.id() - CityMaterials.YARD,
                                    0,
                                    0);
                final var owned = property;
                var firm =
                        property == null
                                ? null
                                : city.economy().firms().stream()
                                        .filter(
                                                f ->
                                                        f.id() == owned.operator()
                                                                && (f.kind() == b.type()
                                                                        || b.type() == 3
                                                                                && CityMaterials
                                                                                        .farmer(
                                                                                                f
                                                                                                        .kind())
                                                                        || b.type() == 2
                                                                                && f.kind() >= 2))
                                        .findFirst()
                                        .orElse(null);
                var account =
                        city.economy().businesses().stream()
                                .filter(
                                        a ->
                                                a.building() == b.id()
                                                        && firm != null
                                                        && a.company() == firm.id())
                                .findFirst()
                                .orElse(null);
                var staff =
                        city.citizens().stream()
                                .filter(c -> c.job() == b.id() && firm != null)
                                .sorted(Comparator.comparingInt(CityFrame.Citizen::id))
                                .toList();
                int working =
                        (int)
                                staff.stream()
                                        .filter(
                                                c ->
                                                        c.activity().equals("Working in shop")
                                                                || c.activity()
                                                                        .equals("Working in mine")
                                                                || c.activity()
                                                                        .startsWith("Working: "))
                                        .count();
                int present = 0;
                for (int i = 0; i < staff.size(); i++) {
                    var c = staff.get(i);
                    boolean shift =
                            b.type() == 1
                                    ? CityBusinesses.shopShift(time.hour(), i)
                                    : time.period() == CityTime.Period.WORKDAY;
                    if (shift
                            && c.x() > b.x()
                            && c.x() < b.x() + 6
                            && c.z() > b.z()
                            && c.z() < b.z() + 7) present++;
                }
                boolean hours =
                        b.type() == 1 ? time.shopsOpen() : time.period() == CityTime.Period.WORKDAY;
                double payroll =
                        staff.stream()
                                .mapToDouble(
                                        c -> b.type() == 1 ? 1.8 * 8 : (1.8 + c.cohort() * .3) * 9)
                                .sum();
                String status =
                        firm != null
                                        && b.id() >= CityMaterials.YARD
                                        && city.agriculture().enabled()
                                        && CityMaterials.farmer(firm.kind())
                                ? "Needs agricultural land"
                                : firm != null
                                                && b.id() >= CityMaterials.YARD
                                                && !city.economy()
                                                        .resources()
                                                        .catalog()
                                                        .recipes(firm.kind())
                                                        .isEmpty()
                                                && city
                                                        .economy()
                                                        .resources()
                                                        .catalog()
                                                        .recipes(firm.kind())
                                                        .stream()
                                                        .allMatch(
                                                                ProductionCatalog.Recipe
                                                                        ::requiresFactory)
                                        ? "Needs industrial factory"
                                        : firm == null
                                                ? "Vacant"
                                                : !hours
                                                        ? "Closed"
                                                        : firm.cash() < .01
                                                                ? "Unfunded"
                                                                : staff.isEmpty()
                                                                        ? "No employees"
                                                                        : present == 0
                                                                                ? "Awaiting staff"
                                                                                : b.type() == 1
                                                                                                && b
                                                                                                                .stock()
                                                                                                        == 0
                                                                                        ? "Out of"
                                                                                              + " stock"
                                                                                        : b.type()
                                                                                                                == 2
                                                                                                        && b
                                                                                                                        .stock()
                                                                                                                >= city.economy()
                                                                                                                        .resources()
                                                                                                                        .catalog()
                                                                                                                        .capacity(
                                                                                                                                firm
                                                                                                                                        .kind())
                                                                                                ? "Storage"
                                                                                                      + " full"
                                                                                                : "Open";
                boolean alerts =
                        firm == null
                                || staff.isEmpty()
                                || firm.cash()
                                        < payroll
                                                + (property.owner() != property.operator()
                                                        ? property.rent()
                                                        : 0)
                                || hours
                                        && (present == 0
                                                || b.type() == 1 && b.stock() == 0
                                                || b.type() == 2
                                                        && b.stock()
                                                                >= city.economy()
                                                                        .resources()
                                                                        .catalog()
                                                                        .capacity(firm.kind()))
                                || account != null
                                        && (account.total().profit() < 0
                                                || account.today().totals().missedRent() > 0
                                                || account.today().totals().missedWages() > 0);
                locations.add(
                        new Location(
                                b,
                                firm,
                                property,
                                account,
                                List.copyOf(staff),
                                working,
                                present,
                                status,
                                alerts,
                                payroll,
                                city.addresses().buildingName(b.id())));
            }
        var companies = new ArrayList<Company>();
        for (var f : city.economy().firms()) {
            var ls =
                    locations.stream()
                            .filter(l -> l.firm != null && l.firm.id() == f.id())
                            .toList();
            int employees =
                    (int)
                            city.citizens().stream()
                                    .filter(c -> CityMetrics.employer(city, c) == f.id())
                                    .count();
            int properties =
                    (int)
                            city.economy().properties().stream()
                                    .filter(
                                            p ->
                                                    p.ownerKind() == CityEconomy.COMPANY
                                                            && p.owner() == f.id())
                                    .count();
            var accounts =
                    city.economy().businesses().stream()
                            .filter(a -> a.company() == f.id())
                            .toList();
            double revenue = accounts.stream().mapToDouble(a -> a.total().revenue()).sum(),
                    expenses = accounts.stream().mapToDouble(a -> a.total().expenses()).sum();
            companies.add(
                    new Company(
                            f,
                            employees,
                            ls.size(),
                            properties,
                            revenue,
                            expenses,
                            revenue - expenses));
        }
        return new BusinessMetrics(locations, companies);
    }
}
