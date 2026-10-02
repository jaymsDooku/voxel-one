package dev.jayms.ui;

import static org.lwjgl.glfw.GLFW.*;

import dev.jayms.net.city.*;

import java.util.*;

/** Mayor monitoring of workplace operations and their parent private companies. */
public final class BusinessDashboard {
    public int view, filter, selected, firstRow;
    public String search = "";
    public boolean searchFocus;
    private List<BusinessMetrics.Location> displayed = List.of();
    private static final int TOP = 138, ROW = 32;
    private static final String[] FILTERS = {"All", "Attention", "Commercial", "Industrial"};

    public List<BusinessMetrics.Location> rows(CityFrame city) {
        String q = search.toLowerCase(Locale.ROOT);
        return BusinessMetrics.from(city).locations().stream()
                .filter(
                        l ->
                                filter == 0
                                        || filter == 1 && l.attention()
                                        || filter == 2 && l.building().type() == 1
                                        || filter == 3 && l.building().type() == 2)
                .filter(l -> (l.name() + " " + l.status()).toLowerCase(Locale.ROOT).contains(q))
                .sorted(
                        Comparator.<BusinessMetrics.Location, Boolean>comparing(
                                        BusinessMetrics.Location::attention)
                                .reversed()
                                .thenComparingInt(l -> l.building().id()))
                .toList();
    }

    public void key(int key, int action) {
        if (action != GLFW_PRESS && action != GLFW_REPEAT) return;
        if (searchFocus) {
            if (key == GLFW_KEY_BACKSPACE && !search.isEmpty())
                search = search.substring(0, search.length() - 1);
            if (key == GLFW_KEY_ENTER) searchFocus = false;
            firstRow = 0;
            return;
        }
        if (key == GLFW_KEY_DOWN) firstRow++;
        if (key == GLFW_KEY_UP) firstRow = Math.max(0, firstRow - 1);
        if (key == GLFW_KEY_PAGE_DOWN) firstRow += 5;
        if (key == GLFW_KEY_PAGE_UP) firstRow = Math.max(0, firstRow - 5);
    }

    public void character(int c) {
        if (searchFocus && c >= 32 && c <= 126 && search.length() < 40) {
            search += (char) c;
            firstRow = 0;
        }
    }

    public void scroll(double amount) {
        firstRow = Math.max(0, firstRow - (int) Math.signum(amount) * 2);
    }

    public void click(float x, float y, int w, int h) {
        if (x < 24 || x >= w - 24) return;
        if (y >= TOP && y <= TOP + 30) {
            view = x < 24 + (w - 48) / 2f ? 0 : 1;
            selected = 0;
            firstRow = 0;
            searchFocus = false;
            return;
        }
        if (selected != 0) {
            if (y >= 178 && y <= 208) {
                selected = 0;
                firstRow = 0;
            }
            return;
        }
        if (view == 1) return;
        if (y >= 178 && y <= 208) {
            filter = Math.min(3, (int) ((x - 24) / ((w - 48) / 4f)));
            firstRow = 0;
            searchFocus = false;
            return;
        }
        if (y >= 218 && y <= 246) {
            searchFocus = true;
            return;
        }
        searchFocus = false;
        if (y >= 466 && y < h - 40) {
            int i = (int) ((y - 466) / ROW);
            if (i < displayed.size()) {
                selected = displayed.get(i).building().id();
                firstRow = 0;
            }
        }
    }

    private static String fmt(String f, Object... a) {
        return String.format(Locale.ROOT, f, a);
    }

    private static void text(Overlay ui, String s, float x, float y, float width, float scale) {
        while (!s.isEmpty() && ui.textWidth(s, scale) > width) s = s.substring(0, s.length() - 1);
        ui.text(s, x, y, scale);
    }

    private static void panel(Overlay ui, float x, float y, float w, float h) {
        ui.rectangle(x, y, w, h, .055f, .085f, .115f, 1);
    }

    private static void card(
            Overlay ui, float x, float y, float w, String label, String value, String sub) {
        panel(ui, x, y, w, 76);
        text(ui, label, x + 10, y + 9, w - 20, 1.2f);
        text(ui, value, x + 10, y + 29, w - 20, 2);
        text(ui, sub, x + 10, y + 55, w - 20, 1.1f);
    }

    private static String sector(int kind) {
        return kind == 0 ? "Developer" : kind == 1 ? "Commercial" : "Industrial";
    }

    public void render(Overlay ui, int w, int h, CityFrame city) {
        for (int i = 0; i < 2; i++) {
            float x = 24 + i * (w - 48) / 2f;
            panel(ui, x, TOP, (w - 48) / 2f - 6, 30);
            text(
                    ui,
                    i == 0 ? "Operating businesses" : "Private companies",
                    x + 10,
                    TOP + 9,
                    (w - 48) / 2f - 20,
                    1.25f);
            if (view == i) ui.rectangle(x, TOP + 28, (w - 48) / 2f - 6, 2, .3f, .85f, .7f, 1);
        }
        var metrics = BusinessMetrics.from(city);
        if (selected != 0) {
            var l =
                    metrics.locations().stream()
                            .filter(v -> v.building().id() == selected)
                            .findFirst()
                            .orElse(null);
            if (l != null) {
                detail(ui, w, h, city, l);
                return;
            }
            selected = 0;
        }
        if (view == 1) {
            companies(ui, w, h, metrics);
            return;
        }
        for (int i = 0; i < 4; i++) {
            float x = 24 + i * (w - 48) / 4f;
            panel(ui, x, 178, (w - 48) / 4f - 6, 30);
            text(ui, FILTERS[i], x + 9, 187, (w - 48) / 4f - 20, 1.2f);
            if (filter == i) ui.rectangle(x, 206, (w - 48) / 4f - 6, 2, .3f, .85f, .7f, 1);
        }
        panel(ui, 24, 218, w - 48, 28);
        text(ui, (searchFocus ? "> " : "Search: ") + search, 34, 227, w - 70, 1.2f);
        int cols = 3;
        float cw = (w - 72) / 3f;
        String[][] values = {
            {"Operating cash", fmt("$%.0f", metrics.operatingCash()), "Shop and mining companies"},
            {"Tracked sales", fmt("$%.1f", metrics.revenue()), "Meals and mine deliveries"},
            {
                "Operating result",
                fmt("$%.1f", metrics.revenue() - metrics.expenses()),
                "Sales less operating costs"
            },
            {
                "Open locations",
                fmt(
                        "%d / %d",
                        metrics.locations().stream().filter(BusinessMetrics.Location::open).count(),
                        metrics.locations().size()),
                "Actual staffing and hours"
            },
            {"Employees", fmt("%d", metrics.employees()), "Assigned to workplaces"},
            {
                "Needs attention",
                fmt(
                        "%d",
                        metrics.locations().stream()
                                .filter(BusinessMetrics.Location::attention)
                                .count()),
                "Cash, staffing and losses"
            }
        };
        for (int i = 0; i < values.length; i++)
            card(
                    ui,
                    24 + i % cols * (cw + 12),
                    258 + i / cols * 84,
                    cw,
                    values[i][0],
                    values[i][1],
                    values[i][2]);
        var rows = rows(city);
        int count = Math.max(1, (h - 506) / ROW);
        firstRow = Math.min(firstRow, Math.max(0, rows.size() - count));
        displayed = rows.subList(firstRow, Math.min(rows.size(), firstRow + count));
        boolean wide = w >= 1000;
        float[] edge =
                wide
                        ? new float[] {0, .32f, .5f, .61f, .75f, .87f, 1}
                        : new float[] {0, .42f, .69f, .83f, 1};
        String[] headers =
                wide
                        ? new String[] {"Business", "Status", "Staff", "Sales", "Result", "Stock"}
                        : new String[] {"Business", "Status", "Staff", "Stock"};
        tableHeader(ui, w, 436, edge, headers);
        for (int i = 0; i < displayed.size(); i++) {
            var l = displayed.get(i);
            String[] valuesRow =
                    wide
                            ? new String[] {
                                l.name(),
                                l.status(),
                                l.onSite() + "/" + l.employees().size(),
                                fmt("$%.1f", l.total().revenue()),
                                fmt("$%.1f", l.total().profit()),
                                "" + l.building().stock()
                            }
                            : new String[] {
                                l.name(),
                                l.status(),
                                l.onSite() + "/" + l.employees().size(),
                                "" + l.building().stock()
                            };
            tableRow(ui, w, 466 + i * ROW, edge, valuesRow, l.attention());
        }
        if (rows.isEmpty()) ui.text("No operating locations match this view.", 34, 480, 1.25f);
        text(
                ui,
                fmt(
                        "Showing %d-%d of %d | Click a business for accounts and staff",
                        rows.isEmpty() ? 0 : firstRow + 1,
                        firstRow + displayed.size(),
                        rows.size()),
                24,
                h - 42,
                w - 48,
                1.1f);
    }

    private static void tableHeader(Overlay ui, int w, float y, float[] edges, String[] values) {
        panel(ui, 24, y, w - 48, 28);
        for (int i = 0; i < values.length; i++)
            text(
                    ui,
                    values[i],
                    32 + (w - 64) * edges[i],
                    y + 9,
                    (w - 64) * (edges[i + 1] - edges[i]) - 12,
                    1.2f);
    }

    private static void tableRow(
            Overlay ui, int w, float y, float[] edges, String[] values, boolean alert) {
        panel(ui, 24, y, w - 48, ROW - 3);
        if (alert) ui.rectangle(24, y, 3, ROW - 3, 1, .65f, .25f, 1);
        for (int i = 0; i < values.length; i++)
            text(
                    ui,
                    values[i],
                    32 + (w - 64) * edges[i],
                    y + 10,
                    (w - 64) * (edges[i + 1] - edges[i]) - 12,
                    1.15f);
    }

    private void detail(Overlay ui, int w, int h, CityFrame city, BusinessMetrics.Location l) {
        panel(ui, 24, 178, w - 48, 30);
        text(ui, "< All businesses | " + l.name(), 34, 187, w - 68, 1.25f);
        var b = l.building();
        var t = l.total();
        var p = l.property();
        String owner =
                p == null
                        ? "Unowned"
                        : p.ownerKind() == CityEconomy.CITIZEN
                                ? "Private citizen"
                                : city.economy().firms().stream()
                                        .filter(f -> f.id() == p.owner())
                                        .map(CityEconomy.Firm::name)
                                        .findFirst()
                                        .orElse("Unknown owner");
        text(
                ui,
                sector(b.type())
                        + " | "
                        + l.status()
                        + " | "
                        + (b.type() == 1 ? "06:00-22:00" : "08:00-17:00")
                        + " | Building #"
                        + b.id()
                        + " at "
                        + b.x()
                        + ", "
                        + b.z(),
                24,
                218,
                w - 48,
                1.2f);
        text(
                ui,
                "Owner: "
                        + owner
                        + " | "
                        + (l.firm() == null
                                ? "No operator"
                                : p != null
                                                && l.firm() != null
                                                && p.ownerKind() == CityEconomy.COMPANY
                                                && p.owner() == l.firm().id()
                                        ? "Company-owned"
                                        : "Leased workplace")
                        + " | Tracking since Day "
                        + (l.account() == null ? "--" : l.account().startedDay()),
                24,
                240,
                w - 48,
                1.1f);
        float cw = (w - 72) / 3f;
        String[][] cards = {
            {
                "Company cash",
                fmt("$%.1f", l.firm() == null ? 0 : l.firm().cash()),
                "Shared by its locations"
            },
            {"Tracked sales", fmt("$%.1f", t.revenue()), fmt("%d units sold", t.sold())},
            {"Operating result", fmt("$%.1f", t.profit()), fmt("Costs $%.1f", t.expenses())},
            {
                "Staff on site",
                l.onSite() + " / " + l.employees().size(),
                "Capacity " + l.capacity() + " | Working " + l.working()
            },
            {
                "Stock / produced",
                "" + b.stock() + " / " + t.produced(),
                b.type() == 1
                        ? fmt("%d supplies received", t.received())
                        : fmt(
                                "%.1f units per paid hour",
                                t.workHours() == 0 ? 0 : t.produced() / t.workHours())
            },
            {
                "Wages / rent",
                fmt("$%.1f / $%.1f", t.wages(), t.rent()),
                fmt("Supplies $%.1f", t.supplies())
            }
        };
        for (int i = 0; i < cards.length; i++)
            card(
                    ui,
                    24 + i % 3 * (cw + 12),
                    264 + i / 3 * 84,
                    cw,
                    cards[i][0],
                    cards[i][1],
                    cards[i][2]);
        var days = new ArrayList<CityBusinesses.Day>();
        if (l.account() != null) {
            days.addAll(l.account().history());
            days.add(l.account().today());
            Collections.reverse(days);
        }
        text(ui, "DAILY ACCOUNTS | Current day is in progress", 24, 444, w - 48, 1.2f);
        float[] edges = {0, .22f, .45f, .7f, 1};
        tableHeader(ui, w, 466, edges, new String[] {"Day", "Sales", "Costs", "Result"});
        int max = Math.max(1, (h - 550) / ROW);
        firstRow = Math.min(firstRow, Math.max(0, days.size() - max));
        int shown = Math.min(max, days.size() - firstRow);
        for (int i = 0; i < shown; i++) {
            var d = days.get(firstRow + i);
            tableRow(
                    ui,
                    w,
                    498 + i * ROW,
                    edges,
                    new String[] {
                        "" + d.day(),
                        fmt("$%.1f", d.totals().revenue()),
                        fmt("$%.1f", d.totals().expenses()),
                        fmt("$%.1f", d.totals().profit())
                    },
                    d.totals().profit() < 0);
        }
        text(
                ui,
                "Wages, supplies and rent are costs; property purchases are investments.",
                24,
                h - 44,
                w - 48,
                1.05f);
    }

    private void companies(Overlay ui, int w, int h, BusinessMetrics m) {
        text(ui, "Company ownership, employment and lifetime finances", 24, 186, w - 48, 1.3f);
        var rows = m.companies();
        int max = Math.max(1, (h - 268) / 88);
        firstRow = Math.min(firstRow, Math.max(0, rows.size() - max));
        for (int i = 0; i < Math.min(max, rows.size() - firstRow); i++) {
            var c = rows.get(firstRow + i);
            var f = c.firm();
            float y = 218 + i * 88;
            panel(ui, 24, y, w - 48, 80);
            text(ui, f.name() + " | " + sector(f.kind()), 36, y + 10, w - 72, 1.45f);
            text(
                    ui,
                    fmt(
                            "Cash $%.1f | Employees %d | Locations %d | Owned properties %d",
                            f.cash(), c.employees(), c.locations(), c.properties()),
                    36,
                    y + 34,
                    w - 72,
                    1.2f);
            text(
                    ui,
                    f.kind() == 0
                            ? fmt(
                                    "Receipts $%.1f | Land $%.1f | Materials $%.1f | Wages $%.1f",
                                    f.receipts(), f.land(), f.materials(), f.wages())
                            : fmt(
                                    "Tracked sales $%.1f | Operating costs $%.1f | Result $%.1f",
                                    c.revenue(), c.expenses(), c.profit()),
                    36,
                    y + 56,
                    w - 72,
                    1.15f);
        }
        text(
                ui,
                "Receipts include property sales and rent. Operating results cover tracked"
                        + " workplaces.",
                24,
                h - 44,
                w - 48,
                1.05f);
    }
}
