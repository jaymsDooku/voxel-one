package dev.jayms.ui;

import static org.lwjgl.glfw.GLFW.*;

import dev.jayms.net.city.*;

import java.util.*;
import java.util.function.IntConsumer;

/** Live, read-only mayor screen. Citizen selection returns to the existing world inspector. */
public final class MayorDashboard {
    public final RegionalDashboard regions = new RegionalDashboard();
    public final BusinessDashboard businesses = new BusinessDashboard();
    public final MetricHistory history = new MetricHistory();
    public final MetricTrends trends = new MetricTrends();
    public final CapitalDashboard capital = new CapitalDashboard();
    public java.util.function.Consumer<CityCommand> submit = c -> {};
    public boolean open;
    public int tab, filter, firstRow;
    public boolean sortBySavings, searchFocus;
    public String search = "";
    private List<CityFrame.Citizen> displayedRows = List.of();
    public static final String[] TABS = {
        "Overview", "Groups", "Citizens", "Finances", "Businesses", "Exchange"
    };
    private static final String[] FILTERS = {"All", "Attention", "Hungry", "No home", "No job"};
    private static final int TOP = 138, TABLE = 242, ROW = 30;

    public void show() {
        open = true;
        searchFocus = false;
        businesses.searchFocus = false;
    }

    public void close() {
        open = false;
        searchFocus = false;
        businesses.searchFocus = false;
    }

    public void key(int key, int action) {
        if (trends.open) return;
        if (action != GLFW_PRESS && action != GLFW_REPEAT) return;
        if (tab == 5) {
            capital.key(key, action);
            searchFocus = capital.editing();
            return;
        }
        if (tab == 4) {
            businesses.key(key, action);
            searchFocus = businesses.searchFocus;
            return;
        }
        if (searchFocus) {
            if (key == GLFW_KEY_BACKSPACE && !search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
                firstRow = 0;
            }
            if (key == GLFW_KEY_ENTER) searchFocus = false;
            return;
        }
        if (tab == 2) {
            if (key == GLFW_KEY_DOWN) firstRow++;
            if (key == GLFW_KEY_UP) firstRow = Math.max(0, firstRow - 1);
            if (key == GLFW_KEY_PAGE_DOWN) firstRow += 8;
            if (key == GLFW_KEY_PAGE_UP) firstRow = Math.max(0, firstRow - 8);
        }
    }

    public void character(int c) {
        if (trends.open) return;
        if (open && tab == 5) {
            capital.character(c);
            return;
        }
        if (open && tab == 4) {
            businesses.character(c);
            return;
        }
        if (open && tab == 2 && searchFocus && c >= 32 && c <= 126 && search.length() < 40) {
            search += (char) c;
            firstRow = 0;
        }
    }

    public void scroll(double amount) {
        if (tab == 6) { regions.scroll(amount); return; }
        if (trends.open) return;
        if (tab == 5) {
            capital.scroll(amount);
            return;
        }
        if (tab == 4) {
            businesses.scroll(amount);
            return;
        }
        if (tab == 2) firstRow = Math.max(0, firstRow - (int) Math.signum(amount) * 3);
    }

    public List<CityFrame.Citizen> rows(CityFrame city) {
        String query = search.toLowerCase(Locale.ROOT);
        Comparator<CityFrame.Citizen> order =
                sortBySavings
                        ? Comparator.comparingDouble(CityFrame.Citizen::money)
                        : Comparator.<CityFrame.Citizen>comparingInt(
                                        c -> CityMetrics.urgency(city, c))
                                .reversed()
                                .thenComparingDouble(CityFrame.Citizen::hunger);
        return city.citizens().stream()
                .filter(
                        c ->
                                switch (filter) {
                                    case 1 -> CityMetrics.urgency(city, c) > 0;
                                    case 2 -> c.hunger() < 35;
                                    case 3 -> !CityMetrics.housed(city, c);
                                    case 4 -> CityMetrics.employer(city, c) == 0;
                                    default -> true;
                                })
                .filter(
                        c ->
                                (c.name()
                                                + " "
                                                + CitySimulation.COHORTS[c.cohort()]
                                                + " "
                                                + c.activity())
                                        .toLowerCase(Locale.ROOT)
                                        .contains(query))
                .sorted(order.thenComparingInt(CityFrame.Citizen::id))
                .toList();
    }

    public void click(float x, float y, int w, int h, CityFrame city, IntConsumer select) {
        if (x >= w - 400 && x <= w - 276 && y >= 48 && y <= 76) {
            tab = 6;
            trends.open = searchFocus = businesses.searchFocus = false;
            return;
        }
        if (x >= w - 260 && x <= w - 136 && y >= 48 && y <= 76) {
            trends.open = !trends.open;
            if (trends.open) trends.selectDomain(tab == 3 ? 2 : tab == 4 ? 1 : 0);
            searchFocus = businesses.searchFocus = false;
            return;
        }
        if (x >= w - 124 && x <= w - 24 && y >= 48 && y <= 76) {
            close();
            return;
        }
        if (y >= 88 && y <= 120 && x >= 24 && x < w - 24) {
            trends.open = false;
            tab = Math.min(TABS.length - 1, (int) ((x - 24) / ((w - 48) / (float) TABS.length)));
            businesses.searchFocus = false;
            searchFocus = false;
            firstRow = 0;
            return;
        }
        if (trends.open) {
            trends.click(x, y, w);
            return;
        }
        if (tab == 6) { regions.click(x, y, w, h, city, submit, select); return; }
        if (tab == 5) {
            capital.click(x, y, w, h, city, submit);
            searchFocus = capital.editing();
            return;
        }
        if (tab == 4) {
            businesses.click(x, y, w, h);
            searchFocus = businesses.searchFocus;
            return;
        }
        if (tab != 2) return;
        if (y >= TOP && y <= TOP + 30 && x >= 24 && x < w - 24) {
            filter = Math.min(4, (int) ((x - 24) / ((w - 48) / 5f)));
            firstRow = 0;
            searchFocus = false;
            return;
        }
        if (y >= 178 && y <= 208) {
            searchFocus = x >= 24 && x < w - 184;
            if (x >= w - 172 && x < w - 24) {
                sortBySavings = !sortBySavings;
                firstRow = 0;
            }
            return;
        }
        searchFocus = false;
        if (y >= TABLE && y < h - 40 && x >= 24 && x < w - 24) {
            int index = (int) ((y - TABLE) / ROW);
            if (index < displayedRows.size()) select.accept(displayedRows.get(index).id());
        }
    }

    private static String fmt(String format, Object... args) {
        return String.format(Locale.ROOT, format, args);
    }

    private static void text(Overlay ui, String value, float x, float y, float width, float scale) {
        if (ui.textWidth(value, scale) > width) {
            while (!value.isEmpty() && ui.textWidth(value + "...", scale) > width)
                value = value.substring(0, value.length() - 1);
            value += "...";
        }
        ui.text(value, x, y, scale);
    }

    private static void panel(Overlay ui, float x, float y, float w, float h) {
        ui.rectangle(x, y, w, h, .055f, .085f, .115f, 1);
    }

    private static void bar(Overlay ui, float x, float y, float width, double percent) {
        ui.rectangle(x, y, width, 5, .12f, .18f, .22f, 1);
        ui.rectangle(
                x,
                y,
                (float) (width * Math.max(0, Math.min(100, percent)) / 100),
                5,
                .3f,
                .85f,
                .7f,
                1);
    }

    private static void card(
            Overlay ui,
            float x,
            float y,
            float width,
            String title,
            String value,
            String detail,
            double percent) {
        panel(ui, x, y, width, 76);
        text(ui, title, x + 12, y + 9, width - 24, 1.3f);
        text(ui, value, x + 12, y + 29, width - 24, 2.2f);
        text(ui, detail, x + 12, y + 55, width - 24, 1.15f);
        if (percent >= 0) bar(ui, x + 12, y + 69, width - 24, percent);
    }

    public void render(
            Overlay ui, int w, int h, CityFrame city, String closeKey, boolean connected) {
        ui.rectangle(0, 0, w, h, .025f, .04f, .06f, 1);
        ui.text("MAYOR DASHBOARD", 24, 20, 2.1f);
        String clock =
                city.config().time(city.elapsed()).label()
                        + " | "
                        + (connected
                                ? "Live city metrics"
                                : "Disconnected - last received city snapshot");
        text(ui, clock, 24, 55, w - 450, 1.15f);
        panel(ui, w - 400, 48, 124, 28);
        ui.text("Districts", w - 389, 56, 1.3f);
        if (tab == 6) ui.rectangle(w - 400, 74, 124, 2, .3f, .85f, .7f, 1);
        panel(ui, w - 260, 48, 124, 28);
        ui.text(trends.open ? "Snapshot" : "Trends", w - 246, 56, 1.3f);
        panel(ui, w - 124, 48, 100, 28);
        ui.text("Back", w - 98, 56, 1.3f);
        float bw = (w - 48) / (float) TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            panel(ui, 24 + i * bw, 88, bw - 6, 32);
            if (i == tab) ui.rectangle(24 + i * bw, 118, bw - 6, 2, .3f, .85f, .7f, 1);
            ui.text(TABS[i], 36 + i * bw, 98, 1.4f);
        }
        if (trends.open) {
            trends.render(ui, w, h, city, history);
            return;
        }
        var m = CityMetrics.from(city);
        if (!city.population().groups().isEmpty() && tab < 2)
            ui.text("Regional needs and savings use cohort estimates", 24, 126, 1.0f);
        switch (tab) {
            case 0 -> overview(ui, w, h, m);
            case 1 -> groups(ui, w, h, m);
            case 2 -> citizens(ui, w, h, city);
            case 3 -> finances(ui, w, h, city, m);
            case 4 -> businesses.render(ui, w, h, city);
            case 5 -> capital.render(ui, w, h, city);
            case 6 -> regions.render(ui, w, h, city);
            default -> throw new IllegalStateException("Dashboard tab");
        }
        text(
                ui,
                tab == 2
                        ? "Click a citizen to locate and inspect | Wheel / arrows: scroll | Esc / "
                                + closeKey
                                + ": close"
                        : "Live city snapshot | Esc / "
                                + closeKey
                                + ": close | Simulation continues",
                24,
                h - 24,
                w - 48,
                1.1f);
    }

    private void overview(Overlay ui, int w, int h, CityMetrics m) {
        int columns = w >= 900 ? 3 : 2;
        float cw = (w - 48 - (columns - 1) * 12) / (float) columns;
        String[][] data = {
            {"Population", "" + m.population(), "Local and regional residents"},
            {
                "Housing",
                m.housed() + " / " + m.population(),
                fmt("%.0f%% housed | %d vacant beds", m.housingPercent(), m.vacantBeds())
            },
            {
                "Employment",
                m.employed() + " / " + m.population(),
                fmt("%.0f%% employed", m.employmentPercent())
            },
            {"Average hunger", fmt("%.1f / 100", m.averageHunger()), "Higher is better"},
            {
                "Average savings",
                fmt("$%.1f", m.averageSavings()),
                fmt("Estimated median $%.1f", m.medianSavings())
            },
            {"Hungry citizens", "" + m.hungry(), m.criticalHunger() + " critical (below 10)"}
        };
        for (int i = 0; i < data.length; i++)
            card(
                    ui,
                    24 + i % columns * (cw + 12),
                    TOP + i / columns * 88,
                    cw,
                    data[i][0],
                    data[i][1],
                    data[i][2],
                    i == 1
                            ? m.housingPercent()
                            : i == 2 ? m.employmentPercent() : i == 3 ? m.averageHunger() : -1);
        float y = TOP + ((data.length + columns - 1) / columns) * 88 + 4;
        panel(ui, 24, y, w - 48, 132);
        ui.text("NEEDS ATTENTION", 36, y + 12, 1.4f);
        text(
                ui,
                fmt(
                        "Hungry %d | Without a home %d | Without work %d",
                        m.hungry(), m.population() - m.housed(), m.population() - m.employed()),
                36,
                y + 39,
                w - 72,
                1.25f);
        text(
                ui,
                fmt(
                        "Cannot afford $3 food: %d | Blocked travel: %d",
                        m.lowFunds(), m.blockedRoutes()),
                36,
                y + 65,
                w - 72,
                1.25f);
        text(
                ui,
                fmt(
                        "Food in shops: %d | Housing capacity: %d | Private builds: %d",
                        m.foodStock(), m.beds(), m.projects()),
                36,
                y + 91,
                w - 72,
                1.2f);
    }

    private void groups(Overlay ui, int w, int h, CityMetrics m) {
        for (int i = 0; i < 3; i++) {
            var g = m.groups().get(i);
            float y = TOP + i * 132;
            panel(ui, 24, y, w - 48, 120);
            ui.text(CitySimulation.COHORTS[i], 36, y + 12, 1.5f);
            text(
                    ui,
                    fmt(
                            "%d citizens (%.0f%%) | Homes %d/%d | Jobs %d/%d",
                            g.population(),
                            m.population() == 0 ? 0 : 100.0 * g.population() / m.population(),
                            g.housed(),
                            g.population(),
                            g.employed(),
                            g.population()),
                    36,
                    y + 40,
                    w - 72,
                    1.2f);
            text(
                    ui,
                    fmt(
                            "Hunger %.1f / 100 | Hungry %d | Savings avg $%.1f | Median $%.1f",
                            g.averageHunger(), g.hungry(), g.averageSavings(), g.medianSavings()),
                    36,
                    y + 69,
                    w - 72,
                    1.15f);
            bar(ui, 36, y + 102, w - 72, g.averageHunger());
        }
    }

    private void citizens(Overlay ui, int w, int h, CityFrame city) {
        float fw = (w - 48) / 5f;
        for (int i = 0; i < 5; i++) {
            panel(ui, 24 + i * fw, TOP, fw - 5, 30);
            if (i == filter) ui.rectangle(24 + i * fw, TOP + 28, fw - 5, 2, .3f, .85f, .7f, 1);
            text(ui, FILTERS[i], 34 + i * fw, TOP + 9, fw - 20, 1.2f);
        }
        panel(ui, 24, 178, w - 208, 30);
        text(
                ui,
                search.isEmpty() ? "Search name, group or activity" : search,
                34,
                187,
                w - 230,
                1.15f);
        if (searchFocus) ui.rectangle(24, 206, w - 208, 2, .3f, .85f, .7f, 1);
        panel(ui, w - 172, 178, 148, 30);
        ui.text(sortBySavings ? "Sort: money" : "Sort: needs", w - 160, 187, 1.15f);
        var rows = rows(city);
        int visible = Math.max(1, (h - 40 - TABLE) / ROW);
        firstRow = Math.max(0, Math.min(firstRow, Math.max(0, rows.size() - visible)));
        displayedRows =
                List.copyOf(rows.subList(firstRow, Math.min(rows.size(), firstRow + visible)));
        float width = w - 48;
        boolean wide = w >= 1000;
        float[] edges =
                wide
                        ? new float[] {0, .14f, .32f, .43f, .66f, .75f, .84f, 1}
                        : new float[] {0, .26f, .48f, .65f, .81f, 1};
        String[] headers =
                wide
                        ? new String[] {
                            "Citizen", "Group", "Home", "Employer", "Hunger", "Savings", "Activity"
                        }
                        : new String[] {"Citizen", "Home", "Work", "Hunger", "Savings"};
        for (int i = 0; i < headers.length; i++)
            text(
                    ui,
                    headers[i],
                    32 + width * edges[i],
                    219,
                    width * (edges[i + 1] - edges[i]) - 12,
                    1.25f);
        for (int row = 0; row < displayedRows.size(); row++) {
            var c = displayedRows.get(row);
            float y = TABLE + row * ROW;
            panel(ui, 24, y, width, ROW - 3);
            int employer = CityMetrics.employer(city, c);
            String firm =
                    city.economy().firms().stream()
                            .filter(f -> f.id() == employer)
                            .map(CityEconomy.Firm::name)
                            .findFirst()
                            .orElse(employer == -1 ? "Stock exchange" : "None");
            String home = CityMetrics.housed(city, c) ? "#" + c.home() : "None",
                    work = employer == 0 ? "None" : c.job() < 0 ? "Building" : "Employed";
            String[] values =
                    wide
                            ? new String[] {
                                c.name(),
                                CitySimulation.COHORTS[c.cohort()],
                                home,
                                firm,
                                fmt("%.0f", c.hunger()),
                                fmt("$%.1f", c.money()),
                                c.activity()
                            }
                            : new String[] {
                                c.name(),
                                home,
                                work,
                                fmt("%.0f", c.hunger()),
                                fmt("$%.1f", c.money())
                            };
            if (CityMetrics.urgency(city, c) > 0) ui.rectangle(24, y, 3, ROW - 3, 1, .65f, .25f, 1);
            for (int i = 0; i < values.length; i++)
                text(
                        ui,
                        values[i],
                        32 + width * edges[i],
                        y + 9,
                        width * (edges[i + 1] - edges[i]) - 12,
                        1.2f);
        }
        if (rows.isEmpty()) ui.text("No citizens match this view.", 36, TABLE + 20, 1.4f);
    }

    private void finances(Overlay ui, int w, int h, CityFrame city, CityMetrics m) {
        var e = city.economy();
        int cols = w >= 900 ? 3 : 2;
        float cw = (w - 48 - (cols - 1) * 12) / (float) cols;
        String[][] data = {
            {"Mayor treasury", fmt("$%.0f", e.budget()), "Public city budget"},
            {"Public road spending", fmt("$%.0f", e.roadSpending()), "Total spent to date"},
            {"Public land income", fmt("$%.0f", e.landRevenue()), "Total received to date"},
            {"Household savings", fmt("$%.1f", m.totalSavings()), "All citizens combined"},
            {
                "Private company cash",
                fmt("$%.0f", e.firms().stream().mapToDouble(CityEconomy.Firm::cash).sum()
                        + city.population().groups().stream().mapToDouble(RegionalPopulation.Group::treasury).sum()),
                "Local and regional business balances"
            },
            {
                "Local company wages paid",
                fmt("$%.1f", e.firms().stream().mapToDouble(CityEconomy.Firm::wages).sum()),
                "Total paid to date"
            }
        };
        for (int i = 0; i < data.length; i++)
            card(
                    ui,
                    24 + i % cols * (cw + 12),
                    TOP + i / cols * 88,
                    cw,
                    data[i][0],
                    data[i][1],
                    data[i][2],
                    -1);
        float y = TOP + ((data.length + cols - 1) / cols) * 88;
        panel(ui, 24, y, w - 48, h - y - 44);
        ui.text("LOCAL COMPANIES (regional firms: Districts)", 36, y + 10, 1.35f);
        int row = 0;
        for (var f : e.firms()) {
            if (y + 40 + row * 25 > h - 50) break;
            long staff =
                    city.citizens().stream()
                            .filter(c -> CityMetrics.employer(city, c) == f.id())
                            .count();
            text(
                    ui,
                    fmt(
                            "%s | Cash $%.0f | Staff %d | Wages paid $%.1f",
                            f.name(), f.cash(), staff, f.wages()),
                    36,
                    y + 38 + row * 25,
                    w - 72,
                    1.1f);
            row++;
        }
    }
}
