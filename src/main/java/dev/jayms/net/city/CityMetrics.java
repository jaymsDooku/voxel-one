package dev.jayms.net.city;

import java.util.*;

/** Read-only population indicators calculated from an authoritative city snapshot. */
public record CityMetrics(
        int population,
        int housed,
        int employed,
        int hungry,
        int criticalHunger,
        int lowFunds,
        int blockedRoutes,
        double averageHunger,
        double totalSavings,
        double averageSavings,
        double medianSavings,
        int beds,
        int vacantBeds,
        int foodStock,
        int projects,
        List<Group> groups) {
    public record Group(
            int cohort,
            int population,
            int housed,
            int employed,
            int hungry,
            double averageHunger,
            double averageSavings,
            double medianSavings) {}

    public CityMetrics {
        groups = List.copyOf(groups);
    }

    public double employmentPercent() {
        return population == 0 ? 0 : 100.0 * employed / population;
    }

    public double housingPercent() {
        return population == 0 ? 0 : 100.0 * housed / population;
    }

    public static boolean housed(CityFrame f, CityFrame.Citizen c) {
        return f.buildings().stream().anyMatch(b -> b.id() == c.home() && b.type() == 0);
    }

    public static int employer(CityFrame f, CityFrame.Citizen c) {
        if (c.job() >= CityMaterials.YARD) {
            int id = c.job() - CityMaterials.YARD;
            return f.economy().firms().stream()
                            .anyMatch(firm -> firm.id() == id && firm.kind() >= 2)
                    ? id
                    : 0;
        }
        int company =
                c.job() < 0
                        ? f.economy().plots().stream()
                                .filter(p -> p.id() == -c.job() && p.building() == 0)
                                .mapToInt(CityEconomy.Plot::developer)
                                .findFirst()
                                .orElse(0)
                        : f.economy().properties().stream()
                                .filter(p -> p.building() == c.job())
                                .mapToInt(CityEconomy.Property::operator)
                                .findFirst()
                                .orElse(0);
        boolean workplace =
                c.job() < 0
                        || f.buildings().stream().anyMatch(b -> b.id() == c.job() && b.type() != 0);
        return workplace && f.economy().firms().stream().anyMatch(p -> p.id() == company)
                ? company
                : 0;
    }

    public static boolean blocked(CityFrame.Citizen c) {
        return c.activity().contains("obstructed")
                || c.activity().contains("No connected")
                || c.activity().contains("Waiting for a clear route");
    }

    public static int urgency(CityFrame f, CityFrame.Citizen c) {
        return (c.hunger() < 10 ? 8 : c.hunger() < 35 ? 4 : 0)
                + (!housed(f, c) ? 2 : 0)
                + (employer(f, c) == 0 ? 2 : 0)
                + (c.money() < 3 ? 1 : 0)
                + (blocked(c) ? 1 : 0);
    }

    private static double median(List<CityFrame.Citizen> citizens) {
        var a = citizens.stream().mapToDouble(CityFrame.Citizen::money).sorted().toArray();
        return a.length == 0
                ? 0
                : a.length % 2 == 1 ? a[a.length / 2] : (a[a.length / 2 - 1] + a[a.length / 2]) / 2;
    }

    public static CityMetrics from(CityFrame f) {
        var groups = new ArrayList<Group>();
        for (int cohort = 0; cohort < 3; cohort++) {
            final int group = cohort;
            var cs = f.citizens().stream().filter(c -> c.cohort() == group).toList();
            groups.add(
                    new Group(
                            cohort,
                            cs.size(),
                            (int) cs.stream().filter(c -> housed(f, c)).count(),
                            (int) cs.stream().filter(c -> employer(f, c) != 0).count(),
                            (int) cs.stream().filter(c -> c.hunger() < 35).count(),
                            cs.stream().mapToDouble(CityFrame.Citizen::hunger).average().orElse(0),
                            cs.stream().mapToDouble(CityFrame.Citizen::money).average().orElse(0),
                            median(cs)));
        }
        int housed = groups.stream().mapToInt(Group::housed).sum(),
                beds =
                        f.buildings().stream()
                                .filter(b -> b.type() == 0)
                                .mapToInt(CityFrame.Building::capacity)
                                .sum();
        double savings = f.citizens().stream().mapToDouble(CityFrame.Citizen::money).sum();
        return new CityMetrics(
                f.citizens().size(),
                housed,
                groups.stream().mapToInt(Group::employed).sum(),
                groups.stream().mapToInt(Group::hungry).sum(),
                (int) f.citizens().stream().filter(c -> c.hunger() < 10).count(),
                (int) f.citizens().stream().filter(c -> c.money() < 3).count(),
                (int) f.citizens().stream().filter(CityMetrics::blocked).count(),
                f.citizens().stream().mapToDouble(CityFrame.Citizen::hunger).average().orElse(0),
                savings,
                f.citizens().isEmpty() ? 0 : savings / f.citizens().size(),
                median(f.citizens()),
                beds,
                Math.max(0, beds - housed),
                f.buildings().stream()
                        .filter(b -> b.type() == 1)
                        .mapToInt(CityFrame.Building::stock)
                        .sum(),
                (int) f.economy().plots().stream().filter(p -> p.building() == 0).count(),
                groups);
    }
}
