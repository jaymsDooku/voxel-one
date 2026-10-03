package dev.jayms.ui;

import static org.lwjgl.glfw.GLFW.*;

import dev.jayms.net.city.*;

import java.util.*;
import java.util.function.Consumer;

/** Owner decisions and limit-order entry for the city's company/private investor accounts. */
public final class CapitalDashboard {
    public int companyIndex, actorIndex, firstRow, view;
    public String quantity = "100",
            price = "5.00",
            message = "Choose an owner or investor, then review an action.";
    public int focus = -1;
    public long selectedOrder;
    private CityCommand.Capital pending;
    private List<CityStockExchange.Order> shown = List.of();

    public boolean editing() {
        return focus >= 0;
    }

    public void key(int key, int action) {
        if (action != GLFW_PRESS && action != GLFW_REPEAT) return;
        if (key == GLFW_KEY_ENTER) focus = -1;
        if (key == GLFW_KEY_BACKSPACE && focus >= 0) {
            if (focus == 0 && !quantity.isEmpty())
                quantity = quantity.substring(0, quantity.length() - 1);
            if (focus == 1 && !price.isEmpty()) price = price.substring(0, price.length() - 1);
            pending = null;
        }
    }

    public void character(int c) {
        if (focus < 0 || !((c >= '0' && c <= '9') || c == '.')) return;
        if (focus == 0 && c != '.' && quantity.length() < 10) quantity += (char) c;
        if (focus == 1 && price.length() < 12) price += (char) c;
        pending = null;
    }

    public void scroll(double amount) {
        firstRow = Math.max(0, firstRow - (int) Math.signum(amount) * 2);
    }

    public static List<CityStockExchange.Owner> actors(CityFrame city) {
        var list = new ArrayList<CityStockExchange.Owner>();
        city.economy()
                .capital()
                .investors()
                .forEach(p -> list.add(new CityStockExchange.Owner(1, p.id())));
        city.economy().firms().forEach(f -> list.add(new CityStockExchange.Owner(0, f.id())));
        return List.copyOf(list);
    }

    public static String name(CityFrame city, CityStockExchange.Owner actor) {
        return actor.kind() == 0
                ? city.economy().firms().stream()
                        .filter(f -> f.id() == actor.id())
                        .map(CityEconomy.Firm::name)
                        .findFirst()
                        .orElse("Unknown company")
                : city.economy().capital().investors().stream()
                        .filter(p -> p.id() == actor.id())
                        .map(CityCapital.Investor::name)
                        .findFirst()
                        .orElse("Unknown investor");
    }

    public static boolean available(CityFrame city) {
        if (city.economy().budget() <= 0) return false;
        for (var b : city.buildings())
            if (b.type() == 4) {
                var working =
                        city.citizens().stream()
                                .filter(
                                        c ->
                                                c.job() == b.id()
                                                        && (c.activity()
                                                                        .equals(
                                                                                "Exchange analyst"
                                                                                    + " (graduate)")
                                                                || c.activity()
                                                                        .equals(
                                                                                "Exchange office"
                                                                                    + " support"))
                                                        && city.config()
                                                                        .time(city.elapsed())
                                                                        .period()
                                                                == CityTime.Period.WORKDAY
                                                        && c.x() > b.x()
                                                        && c.x() < b.x() + 6
                                                        && c.z() > b.z()
                                                        && c.z() < b.z() + 7)
                                .toList();
                long grads =
                        working.stream()
                                .filter(c -> city.economy().capital().graduates().contains(c.id()))
                                .count();
                if (grads >= 2 && grads * 2 > working.size()) return true;
            }
        return false;
    }

    private CityEconomy.Firm company(CityFrame city) {
        var firms = city.economy().firms();
        return firms.isEmpty() ? null : firms.get(Math.floorMod(companyIndex, firms.size()));
    }

    private CityStockExchange.Owner actor(CityFrame city) {
        var actors = actors(city);
        return actors.isEmpty() ? null : actors.get(Math.floorMod(actorIndex, actors.size()));
    }

    public void click(
            float x, float y, int w, int h, CityFrame city, Consumer<CityCommand> submit) {
        if (x < 24 || x >= w - 24) return;
        var company = company(city);
        var actor = actor(city);
        if (company == null || actor == null) return;
        if (y >= 138 && y < 172) {
            companyIndex += x < (w / 2f) ? -1 : 1;
            pending = null;
            selectedOrder = 0;
            firstRow = 0;
            return;
        }
        if (y >= 180 && y < 214) {
            actorIndex += x < (w / 2f) ? -1 : 1;
            pending = null;
            selectedOrder = 0;
            return;
        }
        focus = -1;
        if (y >= 300 && y < 332) {
            focus = x < w / 2f ? 0 : 1;
            return;
        }
        if (y >= 344 && y < 378) {
            int action = Math.min(3, (int) ((x - 24) / ((w - 48) / 4f)));
            try {
                long cents =
                        action == 3
                                ? 0
                                : new java.math.BigDecimal(price)
                                        .movePointRight(2)
                                        .longValueExact();
                long shares = action == 3 ? 0 : Long.parseLong(quantity);
                if (action == 3
                        && city.economy().capital().book().orders().stream()
                                .noneMatch(
                                        o ->
                                                o.id() == selectedOrder
                                                        && o.company() == company.id()
                                                        && o.owner().equals(actor)))
                    throw new IllegalArgumentException(
                            "Select one of this investor's orders to cancel");
                pending =
                        new CityCommand.Capital(
                                action,
                                company.id(),
                                actor.kind(),
                                actor.id(),
                                shares,
                                cents,
                                selectedOrder);
                message =
                        (new String[] {
                                            "Offer new shares",
                                            "Buy at limit",
                                            "Sell at limit",
                                            "Cancel order"
                                        })
                                        [action]
                                + " as "
                                + name(city, actor)
                                + (action == 3
                                        ? " #" + selectedOrder
                                        : " | " + shares + " shares @ $" + money(cents));
            } catch (RuntimeException e) {
                pending = null;
                message =
                        "Enter a positive whole share quantity and price in dollars; select an"
                                + " owned order for cancellation.";
            }
            return;
        }
        if (y >= 390 && y < 424 && pending != null) {
            if (x < w / 2f) {
                submit.accept(new CityCommand(pending));
                message = "Submitted. The server validates ownership, staffing, shares and cash.";
            } else message = "Action dismissed.";
            pending = null;
            return;
        }
        if (y >= 466 && y < 496) {
            view = Math.min(2, (int) ((x - 24) / ((w - 48) / 3f)));
            firstRow = 0;
            return;
        }
        if (view == 0 && y >= 532 && y < h - 44) {
            int i = (int) ((y - 532) / 28);
            if (i < shown.size()) {
                selectedOrder = shown.get(i).id();
                pending = null;
            }
        }
    }

    private static String money(long cents) {
        return String.format(Locale.ROOT, "%.2f", cents / 100.0);
    }

    private static void line(Overlay ui, String text, float y, int w) {
        while (!text.isEmpty() && ui.textWidth(text, 1.2f) > w - 68)
            text = text.substring(0, text.length() - 1);
        ui.text(text, 34, y, 1.2f);
    }

    private static void button(Overlay ui, String text, float x, float y, float width) {
        ui.rectangle(x, y, width - 6, 32, .08f, .19f, .23f, 1);
        while (!text.isEmpty() && ui.textWidth(text, 1.2f) > width - 20)
            text = text.substring(0, text.length() - 1);
        ui.text(text, x + 9, y + 10, 1.2f);
    }

    public void render(Overlay ui, int w, int h, CityFrame city) {
        var company = company(city);
        var actor = actor(city);
        if (company == null || actor == null) {
            line(ui, "No company accounts available.", 150, w);
            return;
        }
        var book = city.economy().capital().book();
        var listing =
                book.listings().stream()
                        .filter(l -> l.company() == company.id())
                        .findFirst()
                        .orElse(null);
        button(ui, "< Company: " + company.name() + " >", 24, 138, w - 48);
        button(ui, "< Act as: " + name(city, actor) + " >", 24, 180, w - 48);
        long held =
                book.holdings().stream()
                        .filter(a -> a.company() == company.id() && a.owner().equals(actor))
                        .mapToLong(CityStockExchange.Holding::shares)
                        .sum();
        long free =
                held
                        - book.orders().stream()
                                .filter(
                                        o ->
                                                o.company() == company.id()
                                                        && o.owner().equals(actor)
                                                        && !o.buy())
                                .mapToLong(CityStockExchange.Order::shares)
                                .sum();
        long cents =
                actor.kind() == 0
                        ? city.economy().firms().stream()
                                .filter(f -> f.id() == actor.id())
                                .mapToLong(f -> (long) Math.floor(f.cash() * 100 + 1e-7))
                                .findFirst()
                                .orElse(0)
                        : city.economy().capital().investors().stream()
                                .filter(p -> p.id() == actor.id())
                                .mapToLong(CityCapital.Investor::cents)
                                .findFirst()
                                .orElse(0);
        line(
                ui,
                (listing == null ? "Private" : listing.publicCompany() ? "Public" : "Private")
                        + " | Founder: "
                        + (listing == null ? "--" : name(city, listing.founder()))
                        + " | Issued shares: "
                        + (listing == null ? 0 : listing.issued()),
                230,
                w);
        line(
                ui,
                "Shares held: "
                        + held
                        + " ("
                        + free
                        + " free) | Available cash $"
                        + money(cents)
                        + " | Last trade: "
                        + (listing == null || listing.lastPrice() == 0
                                ? "No trades"
                                : "$" + money(listing.lastPrice())),
                253,
                w);
        line(
                ui,
                available(city)
                        ? "Exchange open: staffed graduate offices"
                        : "Exchange closed: needs office, paid graduate majority on site, and"
                                + " 08:00-17:00",
                277,
                w);
        button(ui, (focus == 0 ? "> " : "") + "Shares: " + quantity, 24, 300, (w - 48) / 2f);
        button(
                ui,
                (focus == 1 ? "> " : "") + "Limit price $: " + price,
                24 + (w - 48) / 2f,
                300,
                (w - 48) / 2f);
        String[] actions = {"Go public", "Buy", "Sell", "Cancel selected"};
        for (int i = 0; i < 4; i++)
            button(ui, actions[i], 24 + i * (w - 48) / 4f, 344, (w - 48) / 4f);
        if (pending != null) {
            button(ui, "Confirm action", 24, 390, (w - 48) / 2f);
            button(ui, "Dismiss", 24 + (w - 48) / 2f, 390, (w - 48) / 2f);
        }
        line(ui, message, 439, w);
        String[] tabs = {"Order book", "Shareholders", "Recent trades"};
        for (int i = 0; i < 3; i++) button(ui, tabs[i], 24 + i * (w - 48) / 3f, 466, (w - 48) / 3f);
        line(
                ui,
                "Primary sales fund the company. Secondary sales pay the seller. Prices follow"
                        + " crossing limit orders.",
                511,
                w);
        var rows = new ArrayList<String>();
        shown = List.of();
        if (view == 0) {
            var orders =
                    book.orders().stream()
                            .filter(o -> o.company() == company.id())
                            .sorted(
                                    Comparator.comparing(CityStockExchange.Order::buy)
                                            .reversed()
                                            .thenComparingLong(
                                                    o -> o.buy() ? -o.price() : o.price())
                                            .thenComparingLong(CityStockExchange.Order::id))
                            .toList();
            int count = Math.max(1, (h - 580) / 28);
            firstRow = Math.min(firstRow, Math.max(0, orders.size() - count));
            shown = orders.subList(firstRow, Math.min(orders.size(), firstRow + count));
            for (var o : shown)
                rows.add(
                        (selectedOrder == o.id() ? "> " : "")
                                + "#"
                                + o.id()
                                + " "
                                + (o.buy() ? "BUY" : "SELL")
                                + " "
                                + o.shares()
                                + " @ $"
                                + money(o.price())
                                + " | "
                                + name(city, o.owner())
                                + (o.primary() ? " | New issue" : ""));
        } else if (view == 1) {
            for (var a : book.holdings())
                if (a.company() == company.id())
                    rows.add(
                            name(city, a.owner())
                                    + " | "
                                    + a.shares()
                                    + " shares | "
                                    + String.format(
                                            Locale.ROOT,
                                            "%.2f%%",
                                            listing == null
                                                    ? 0
                                                    : 100.0 * a.shares() / listing.issued()));
        } else {
            var trades = new ArrayList<>(book.trades());
            Collections.reverse(trades);
            for (var t : trades)
                if (t.company() == company.id())
                    rows.add(
                            t.shares()
                                    + " @ $"
                                    + money(t.price())
                                    + " | "
                                    + name(city, t.buyer())
                                    + " bought from "
                                    + name(city, t.seller()));
        }
        if (view != 0) {
            int count = Math.max(1, (h - 580) / 28);
            firstRow = Math.min(firstRow, Math.max(0, rows.size() - count));
            rows = new ArrayList<>(rows.subList(firstRow, Math.min(rows.size(), firstRow + count)));
        }
        for (int i = 0; i < rows.size(); i++) line(ui, rows.get(i), 542 + i * 28, w);
        if (rows.isEmpty()) line(ui, "No entries in this view.", 542, w);
    }
}
