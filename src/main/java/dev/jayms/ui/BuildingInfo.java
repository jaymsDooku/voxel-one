package dev.jayms.ui;

import static org.lwjgl.glfw.GLFW.*;

import dev.jayms.net.city.*;

import java.util.*;

/** Live property inspector shared by first-person and isometric picking. */
public final class BuildingInfo {
    public boolean open, confirmDemolition;
    public int building, plot, firstRow;

    public void show(int building, int plot) {
        this.building = building;
        this.plot = plot;
        firstRow = 0;
        confirmDemolition = false;
        open = building != 0 || plot != 0;
    }

    public void key(int key, int action) {
        if (action != GLFW_PRESS && action != GLFW_REPEAT) return;
        if (key == GLFW_KEY_ESCAPE) open = false;
        if (key == GLFW_KEY_DOWN || key == GLFW_KEY_PAGE_DOWN)
            firstRow += key == GLFW_KEY_DOWN ? 1 : 5;
        if (key == GLFW_KEY_UP || key == GLFW_KEY_PAGE_UP)
            firstRow = Math.max(0, firstRow - (key == GLFW_KEY_UP ? 1 : 5));
    }

    public void scroll(double amount) {
        firstRow = Math.max(0, firstRow - (int) Math.signum(amount) * 2);
    }

    public void click(float x, float y, int w, int h) {
        float left = (w - Math.min(760, w - 32)) / 2f;
        if (y >= 34 && y <= 72 && x >= w - left - 92 && x <= w - left - 12) open = false;
    }

    public void click(
            float x,
            float y,
            int w,
            int h,
            CityFrame city,
            java.util.function.Consumer<CityCommand> send) {
        click(x, y, w, h);
        float left = (w - Math.min(760, w - 32)) / 2f;
        if (!open || x < left + 18 || x > left + 238 || y < h - 102 || y > h - 64) return;
        if (city.buildings().stream().noneMatch(b -> b.id() == building)) return;
        if (!confirmDemolition) confirmDemolition = true;
        else {
            send.accept(new CityCommand(CityCommand.DEMOLISH, building, List.of()));
            open = false;
            confirmDemolition = false;
        }
    }

    public static String owner(CityFrame city, int kind, int id) {
        if (id == 0) return "None";
        return kind == CityEconomy.COMPANY
                ? city.economy().firms().stream()
                        .filter(f -> f.id() == id)
                        .map(CityEconomy.Firm::name)
                        .findFirst()
                        .orElse("Company #" + id)
                : city.citizens().stream()
                        .filter(c -> c.id() == id)
                        .map(CityFrame.Citizen::name)
                        .findFirst()
                        .orElse("Citizen #" + id);
    }

    public List<String> lines(CityFrame city) {
        var rows = new ArrayList<String>();
        var b = city.buildings().stream().filter(v -> v.id() == building).findFirst().orElse(null);
        var p =
                city.economy().plots().stream()
                        .filter(v -> plot != 0 ? v.id() == plot : v.building() == building)
                        .findFirst()
                        .orElse(null);
        var property =
                city.economy().properties().stream()
                        .filter(v -> v.building() == building)
                        .findFirst()
                        .orElse(null);
        if (b == null && p == null) {
            rows.add("This property is no longer available.");
            return rows;
        }
        int type = b == null ? p.type() : b.type();
        rows.add(
                (SpecialBuildings.special(type) ? SpecialBuildings.name(type) : CitySimulation.ZONES[type])
                        + (b == null ? " development plot #" + p.id() : " building #" + b.id()));
        if (b != null) rows.add("Address: " + city.addresses().buildingName(b.id()));
        rows.add(
                "Location: "
                        + (b == null ? p.x() : b.x())
                        + ", "
                        + (b == null ? p.y() : b.y())
                        + ", "
                        + (b == null ? p.z() : b.z()));
        if (b != null && SpecialBuildings.special(type)) {
            rows.add("Owner: " + (b.zone() == 0 ? "City government" : owner(city, b.zone() == -2 ? CityEconomy.COMPANY : 0, b.stock())));
            rows.add("Level: " + SpecialBuildings.level(type));
        }
        if (property != null) {
            rows.add("Owner: " + owner(city, property.ownerKind(), property.owner()));
            rows.add(
                    String.format(
                            Locale.ROOT,
                            "Property value $%.0f | Rent $%.2f / game day",
                            property.price(),
                            property.rent()));
            if (type == 0) {
                var residents = city.citizens().stream().filter(c -> c.home() == b.id()).toList();
                rows.add("Residents: " + residents.size() + " / " + b.capacity());
                for (var c : residents) rows.add("  " + c.name() + " | " + c.activity());
            } else {
                rows.add("Operator: " + owner(city, 0, property.operator()));
                var firm =
                        city.economy().firms().stream()
                                .filter(f -> f.id() == property.operator())
                                .findFirst()
                                .orElse(null);
                if (firm != null) {
                    rows.add(
                            city.economy().resources().catalog().businesses().sector(firm.kind())
                                    + " | Output: "
                                    + city.economy().resources().catalog().outputs(firm.kind()));
                    rows.add(
                            String.format(
                                    Locale.ROOT,
                                    "Company cash $%.2f | Staff %d",
                                    firm.cash(),
                                    city.citizens().stream()
                                            .filter(c -> CityMetrics.employer(city, c) == firm.id())
                                            .count()));
                    city.economy().resources().production().stream()
                            .filter(v -> v.company() == firm.id())
                            .findFirst()
                            .ifPresent(
                                    v ->
                                            rows.add(
                                                    "Production: "
                                                            + v.status()
                                                            + " | Harvested "
                                                            + v.harvested()));
                    if (type == 3) {
                        rows.add("Farmer-owned land, barn and fields; no developer lease.");
                        city.agriculture().families().stream()
                                .filter(f -> f.company() == firm.id())
                                .forEach(
                                        f ->
                                                f.members()
                                                        .forEach(
                                                                id ->
                                                                        city.citizens().stream()
                                                                                .filter(
                                                                                        c ->
                                                                                                c
                                                                                                                .id()
                                                                                                        == id)
                                                                                .findFirst()
                                                                                .ifPresent(
                                                                                        c ->
                                                                                                rows
                                                                                                        .add(
                                                                                                                "Family:"
                                                                                                                    + " "
                                                                                                                        + c
                                                                                                                                .name()
                                                                                                                        + " | "
                                                                                                                        + c
                                                                                                                                .activity()))));
                        city.agriculture().fields().stream()
                                .filter(f -> f.building() == b.id())
                                .forEach(
                                        f ->
                                                rows.add(
                                                        city.economy()
                                                                        .resources()
                                                                        .catalog()
                                                                        .name(f.product())
                                                                + " field: "
                                                                + Math.round(f.growth() * 100)
                                                                + "% | Harvests "
                                                                + f.harvests()));
                        var cows =
                                city.agriculture().cows().stream()
                                        .filter(c -> c.building() == b.id())
                                        .toList();
                        if (!cows.isEmpty())
                            rows.add(
                                    "Cattle: "
                                            + cows.size()
                                            + " | Calves "
                                            + cows.stream().filter(c -> c.age() < 24).count()
                                            + " | Fed "
                                            + cows.stream().filter(c -> c.fed() > 0).count());
                        city.agriculture().farms().stream()
                                .filter(f -> f.building() == b.id())
                                .forEach(
                                        f -> {
                                            if (f.births() > 0 || !cows.isEmpty())
                                                rows.add(
                                                        "Calves born "
                                                                + f.births()
                                                                + " | Cattle harvested "
                                                                + f.slaughtered());
                                        });
                    }
                    var catalog = city.economy().resources().catalog();
                    var equipment = catalog.equipment(firm.kind());
                    if (equipment != null) {
                        boolean owned =
                                city.economy()
                                                .resources()
                                                .available(0, firm.id(), equipment.product())
                                        >= CityMaterials.UNIT;
                        rows.add(
                                "Equipment: "
                                        + catalog.name(equipment.product())
                                        + (owned
                                                ? " owned | "
                                                        + equipment.multiplier()
                                                        + "x productivity"
                                                : " awaiting purchase | 1x productivity"));
                        rows.add(
                                "Tools are durable, privately owned; one equips this company's"
                                        + " crew.");
                    }
                    for (var line : catalog.recipes(firm.kind())) {
                        String inputs =
                                line.inputs().entrySet().stream()
                                        .map(e -> e.getValue() + " " + catalog.name(e.getKey()))
                                        .collect(java.util.stream.Collectors.joining(" + "));
                        rows.add(
                                inputs + " -> " + line.count() + " " + catalog.name(line.output()));
                        if (line.requiresFactory())
                            rows.add(
                                    "Factory line: "
                                            + line.batchesPerHour()
                                            + " batches / worker-hour (shared time)");
                    }
                    rows.add("BUSINESS MATERIALS (available for owned use or sale)");
                    addStocks(rows, city, 0, firm.id());
                    if (type == 1)
                        rows.add("Food on shelves: " + b.stock() + " | Opening hours 06:00-22:00");
                }
            }
            rows.add("OWNER MATERIALS (separate private inventory)");
            addStocks(rows, city, property.ownerKind(), property.owner());
        }
        if (p != null) {
            rows.add(
                    (p.type() == 3 ? "FARM BUILDER: " : "DEVELOPER: ")
                            + owner(city, 0, p.developer()));
            var recipe = city.economy().resources().project(p.id());
            rows.add(
                    p.building() != 0
                            ? "Construction complete"
                            : recipe != null && recipe.reserved()
                                    ? "Materials reserved | Building "
                                            + (int) Math.min(100, p.work() / 8 * 100)
                                            + "%"
                                    : "Waiting for developer-owned materials");
            if (p.building() != 0 && (recipe == null || !recipe.consumed())) {
                rows.add("Existing property: historical construction materials were not recorded.");
                return List.copyOf(rows);
            }
            rows.add("MATERIAL                       AVAILABLE / REQUIRED");
            for (var a :
                    recipe == null ? CityMaterials.requirements(p.type()) : recipe.materials()) {
                long available =
                        recipe != null && recipe.reserved()
                                ? a.units()
                                : city.economy()
                                        .resources()
                                        .available(0, p.developer(), a.material());
                rows.add(
                        CityMaterials.name(a.material())
                                + ": "
                                + (recipe != null && recipe.consumed()
                                        ? CityMaterials.quantity(a.units())
                                                + " used in this building"
                                        : CityMaterials.quantity(available)
                                                + " / "
                                                + CityMaterials.quantity(a.units())));
            }
            rows.add("Reserved materials belong to this developer and cannot supply another plot.");
        } else rows.add("Existing property: historical construction materials were not recorded.");
        return List.copyOf(rows);
    }

    private static void addStocks(List<String> rows, CityFrame city, int kind, int owner) {
        var stocks =
                city.economy().resources().stocks().stream()
                        .filter(s -> s.ownerKind() == kind && s.owner() == owner)
                        .toList();
        if (stocks.isEmpty()) rows.add("  None");
        for (var s : stocks)
            rows.add(
                    "  "
                            + city.economy().resources().catalog().name(s.material())
                            + ": "
                            + CityMaterials.quantity(s.units()));
    }

    public void render(Overlay ui, int w, int h, CityFrame city) {
        float width = Math.min(760, w - 32), left = (w - width) / 2;
        ui.rectangle(0, 0, w, h, 0, 0, 0, .65f);
        ui.rectangle(left, 24, width, h - 48, .025f, .045f, .07f, .98f);
        ui.text("BUILDING INFORMATION", left + 18, 43, 1.8f);
        ui.rectangle(w - left - 92, 34, 80, 38, .13f, .2f, .27f, 1);
        ui.text("Close", w - left - 82, 47, 1.4f);
        var rows = lines(city);
        int count = Math.max(1, (h - 210) / 25);
        firstRow = Math.min(firstRow, Math.max(0, rows.size() - count));
        for (int i = 0; i < Math.min(count, rows.size() - firstRow); i++) {
            String line = rows.get(firstRow + i);
            while (!line.isEmpty() && ui.textWidth(line, 1.25f) > width - 36)
                line = line.substring(0, line.length() - 1);
            ui.text(line, left + 18, 92 + i * 25, 1.25f);
        }
        if (city.buildings().stream().anyMatch(b -> b.id() == building)) {
            ui.rectangle(left + 18, h - 102, 220, 38, .55f, .12f, .1f, 1);
            ui.text(
                    confirmDemolition ? "Confirm demolition" : "Demolish building",
                    left + 28,
                    h - 90,
                    1.3f);
            ui.text("No refund; zoned land can redevelop", left + 248, h - 90, 1.0f);
        }
        ui.text(
                "Scroll / arrows for details | Esc to close | Simulation continues",
                left + 18,
                h - 51,
                1.1f);
    }
}
