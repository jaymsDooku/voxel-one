package dev.jayms.ui;

import dev.jayms.net.city.*;

import java.util.*;

/** Bounded client-session observations; never fabricates history before connection. */
public final class MetricHistory {
    public static final int LIMIT = 240;
    public static final double INTERVAL = 5;

    public record Sample(double elapsed, Map<String, Double> values) {
        public Sample {
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
    }

    private final Deque<Sample> samples = new ArrayDeque<>();
    private GameConfig config;

    public List<Sample> samples() {
        return List.copyOf(samples);
    }

    public void observe(CityFrame city) {
        if (!Double.isFinite(city.elapsed())) return;
        if (!Objects.equals(config, city.config())
                || !samples.isEmpty() && city.elapsed() < samples.getLast().elapsed()) {
            samples.clear();
            config = city.config();
        }
        if (!samples.isEmpty() && city.elapsed() - samples.getLast().elapsed() < INTERVAL) return;
        var values = new LinkedHashMap<String, Double>();
        var m = CityMetrics.from(city);
        numeric(values, "Population / All / ", m);
        values.put("Population / All / Housing (%)", m.housingPercent());
        values.put("Population / All / Employment (%)", m.employmentPercent());
        for (var g : m.groups())
            numeric(values, "Population / " + CitySimulation.COHORTS[g.cohort()] + " / ", g);
        var e = city.economy();
        values.put("Government / City / Treasury ($)", e.budget());
        values.put("Government / City / Road spending ($, cumulative)", e.roadSpending());
        values.put("Government / City / Land income ($, cumulative)", e.landRevenue());
        var b = BusinessMetrics.from(city);
        values.put("Companies / All / Operating cash ($)", b.operatingCash());
        values.put("Companies / All / Sales ($, cumulative)", b.revenue());
        values.put("Companies / All / Costs ($, cumulative)", b.expenses());
        values.put("Companies / All / Result ($, cumulative)", b.revenue() - b.expenses());
        values.put("Companies / All / Employees", (double) b.employees());
        for (var c : b.companies()) {
            String prefix = "Companies / " + c.firm().name() + " #" + c.firm().id() + " / ";
            numeric(values, prefix, c);
            numeric(values, prefix, c.firm());
        }
        for (var l : b.locations()) {
            String prefix = "Workplaces / " + l.name() + " #" + l.building().id() + " / ";
            numeric(values, prefix, l.total());
            values.put(prefix + "Profit ($, cumulative)", l.total().profit());
            values.put(prefix + "Expenses ($, cumulative)", l.total().expenses());
            values.put(prefix + "Stock", (double) l.building().stock());
            values.put(prefix + "Employees", (double) l.employees().size());
            values.put(prefix + "On site", (double) l.onSite());
            values.put(prefix + "Working", (double) l.working());
        }
        samples.addLast(new Sample(city.elapsed(), values));
        while (samples.size() > LIMIT) samples.removeFirst();
    }

    // Numeric components of these read-only metric records stay in sync with dashboard fields.
    private static void numeric(Map<String, Double> values, String prefix, Record record) {
        for (var component : record.getClass().getRecordComponents()) {
            String name = component.getName();
            if (Set.of("id", "kind", "cohort").contains(name)) continue;
            try {
                Object value = component.getAccessor().invoke(record);
                if (value instanceof Number n && Double.isFinite(n.doubleValue())) {
                    String label = name.replaceAll("([a-z])([A-Z])", "$1 $2");
                    boolean money =
                            Set.of(
                                            "cash",
                                            "land",
                                            "materials",
                                            "wages",
                                            "receipts",
                                            "revenue",
                                            "expenses",
                                            "profit",
                                            "supplies",
                                            "rent",
                                            "totalSavings",
                                            "averageSavings",
                                            "medianSavings")
                                    .contains(name);
                    values.put(
                            prefix
                                    + label
                                    + (money
                                            ? " ($)"
                                            : name.equals("averageHunger") ? " (0-100)" : ""),
                            n.doubleValue());
                }
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
