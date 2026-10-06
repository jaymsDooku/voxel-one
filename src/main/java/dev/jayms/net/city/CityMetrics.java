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
        return f.buildings().stream()
                .anyMatch(b -> b.id() == c.home() && (b.type() == 0 || b.type() == 3));
    }

    public static int employer(CityFrame f, CityFrame.Citizen c) {
        if (f.buildings().stream().anyMatch(b -> b.id() == c.job() && b.type() == SpecialBuildings.EXCHANGE)) return -1;
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

    private record Bin(int cohort, int count, int homes, int jobs, double hunger,
            double savings, int blocked) {}

    private static double weightedMedian(List<Bin> bins) {
        var sorted=bins.stream().filter(b -> b.count()>0)
                .sorted(Comparator.comparingDouble(b -> b.savings()/b.count())).toList();
        long n=sorted.stream().mapToLong(Bin::count).sum();
        if(n==0) return 0;
        long lo=(n-1)/2, hi=n/2, seen=0; double a=0,b=0;
        for(var row:sorted) {
            long end=seen+row.count();
            if(lo>=seen && lo<end) a=row.savings()/row.count();
            if(hi>=seen && hi<end) { b=row.savings()/row.count(); break; }
            seen=end;
        }
        return (a+b)/2;
    }

    public static CityMetrics from(CityFrame f) {
        var bins=new ArrayList<Bin>();
        for(var c:f.citizens()) bins.add(new Bin(c.cohort(),1,housed(f,c)?1:0,
                employer(f,c)!=0?1:0,c.hunger(),c.money(),blocked(c)?1:0));
        for(var g:f.population().groups()) if(g.count()>0)
            bins.add(new Bin(g.cohort(),g.count(),g.housed(),g.employed(),g.hunger(),g.savings(),0));
        var byId=new HashMap<Integer,RegionalPopulation.Group>();
        f.population().groups().forEach(g -> byId.put(g.id(),g));
        for(var a:f.population().agents()) bins.add(new Bin(byId.get(a.group()).cohort(),1,
                a.housed()?1:0,a.employed()?1:0,a.hunger(),a.savings(),0));
        var groups=new ArrayList<Group>();
        for(int cohort=0;cohort<3;cohort++) {
            final int key=cohort;
            var rows=bins.stream().filter(b -> b.cohort()==key).toList();
            int n=rows.stream().mapToInt(Bin::count).sum();
            groups.add(new Group(cohort,n,rows.stream().mapToInt(Bin::homes).sum(),
                    rows.stream().mapToInt(Bin::jobs).sum(),
                    rows.stream().filter(b->b.hunger()<35).mapToInt(Bin::count).sum(),
                    n==0?0:rows.stream().mapToDouble(b->b.hunger()*b.count()).sum()/n,
                    n==0?0:rows.stream().mapToDouble(Bin::savings).sum()/n,weightedMedian(rows)));
        }
        int n=bins.stream().mapToInt(Bin::count).sum();
        int housed=groups.stream().mapToInt(Group::housed).sum();
        int beds=f.buildings().stream().filter(b->b.type()==0 || b.type()==3)
                .mapToInt(CityFrame.Building::capacity).sum()
                +f.population().groups().stream().mapToInt(RegionalPopulation.Group::housed).sum()
                +(int)f.population().agents().stream().filter(RegionalPopulation.Agent::housed).count();
        double savings=bins.stream().mapToDouble(Bin::savings).sum();
        double food=f.buildings().stream().filter(b->b.type()==1).mapToInt(CityFrame.Building::stock).sum()
                +f.population().groups().stream().mapToDouble(RegionalPopulation.Group::food).sum();
        return new CityMetrics(n,housed,groups.stream().mapToInt(Group::employed).sum(),
                groups.stream().mapToInt(Group::hungry).sum(),
                bins.stream().filter(b->b.hunger()<10).mapToInt(Bin::count).sum(),
                bins.stream().filter(b->b.savings()/b.count()<3).mapToInt(Bin::count).sum(),
                bins.stream().mapToInt(Bin::blocked).sum(),
                n==0?0:bins.stream().mapToDouble(b->b.hunger()*b.count()).sum()/n,
                savings,n==0?0:savings/n,weightedMedian(bins),beds,Math.max(0,beds-housed),
                (int)Math.min(Integer.MAX_VALUE,food),
                (int)f.economy().plots().stream().filter(p->p.building()==0).count(),groups);
    }
}
