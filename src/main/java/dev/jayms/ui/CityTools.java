package dev.jayms.ui;

import static org.lwjgl.glfw.GLFW.*;

import dev.jayms.net.*;
import dev.jayms.net.city.*;

import org.joml.Intersectionf;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.*;
import java.util.function.Consumer;

/** Isometric planning tools. Click polygon corners, then explicitly confirm with Enter. */
public final class CityTools {
    public boolean dashboardRequested;
    public int specialKind, specialLevel = 1, specialOwner;
    private int ownerIndex;

    private int ownerId(CityFrame city) {
        if (specialOwner == 1) return city.citizens().isEmpty() ? -1 : city.citizens().get(Math.floorMod(ownerIndex, city.citizens().size())).id();
        if (specialOwner == 2) return city.economy().firms().isEmpty() ? -1 : city.economy().firms().get(Math.floorMod(ownerIndex, city.economy().firms().size())).id();
        return 0;
    }
    private static int specialRowHeight(int height) { return Math.max(12, Math.min(28, (height - 340) / 12)); }
    public int roadType;
    public boolean roadMenu;
    public int tool = -1, selectedCitizen, selectedBuilding, selectedPlot, selectedStreet;
    private final List<Polygon.Point> points = new ArrayList<>();
    private Polygon.Point hover;
    public String message = "Inspect: click a building, plot or citizen for details.";

    public boolean key(int key, Consumer<CityCommand> submit) {
        if (key == GLFW_KEY_ESCAPE && (!points.isEmpty() || tool != -1 || roadMenu)) {
            points.clear();
            tool = -1;
            roadMenu = false;
            return true;
        }
        if (key == GLFW_KEY_BACKSPACE && !points.isEmpty()) {
            points.remove(points.size() - 1);
            return true;
        }
        if (key == GLFW_KEY_ENTER && tool >= 0 && tool < 4) {
            try {
                new Polygon(points);
                submit.accept(new CityCommand(CityCommand.ZONE, tool, points));
                points.clear();
            } catch (IllegalArgumentException e) {
                message = e.getMessage();
            }
            return true;
        }
        return false;
    }

    public void click(
            float x,
            float y,
            int width,
            int height,
            Matrix4f projection,
            Matrix4f view,
            CityFrame city,
            Consumer<CityCommand> submit) {
        if (x >= width - 146 && x <= width - 24 && y >= 74 && y <= 100) {
            dashboardRequested = true;
            return;
        }
        if (roadMenu) {
            if (x >= 16 && x <= Math.min(450, width - 16) && y >= 140 && y < 252) {
                roadType = (int) ((y - 140) / 28);
                roadMenu = false;
                tool = 4;
                points.clear();
                message = "Choose the first endpoint for " + RoadTypes.NAMES[roadType];
                return;
            }
        }
        if (tool == 6 && x >= 16 && x <= 450 && y >= 140 && y < 140+12*specialRowHeight(height)) {
            int row = (int)((y-140)/specialRowHeight(height));
            if (row < 6) specialKind = row;
            if (row == 6) specialLevel = specialLevel % 3 + 1;
            if (row == 7) { specialOwner = (specialOwner+1)%3; ownerIndex = 0; }
            if (row == 8) ownerIndex++;
            if (row >= 9) { tool = row - 1; message = row == 9 ? "Airport: click a cleared road-accessible site (2000)" : row == 10 ? "Expand: click an airport (1000 per runway, max 3)" : "Flight: inspect an adult citizen first, then click the destination airport"; }
            return;
        }
        float top = height - 196;
        if (y >= top && y <= top + 34 && x >= 16 && x < width - 16) {
            int index = (int) ((x - 16) / ((width - 32) / 9f));
            tool = new int[] {-1, 4, 0, 1, 2, 3, 5, 6, 7}[Math.min(8, index)];
            points.clear();
            roadMenu = tool == 4;
            return;
        }
        if (roadMenu) return;
        if (y < 130 || y > height - 200) return;
        if (tool == 5) return;
        if (tool == -1) {
            float best = 22 * 22;
            selectedCitizen = selectedBuilding = selectedPlot = selectedStreet = 0;
            for (var c : city.visibleCitizens()) {
                var p = project(c.x(), c.y() + 1, c.z(), projection, view, width, height);
                if (p != null && p.distanceSquared(x, y) < best) {
                    best = p.distanceSquared(x, y);
                    selectedCitizen = c.id();
                }
            }
            if (selectedCitizen == 0) {
                var inverse = new Matrix4f(projection).mul(view).invert();
                var near =
                        inverse.transformProject(
                                new Vector3f(x / width * 2 - 1, 1 - y / height * 2, -1));
                var far =
                        inverse.transformProject(
                                new Vector3f(x / width * 2 - 1, 1 - y / height * 2, 1));
                selectRay(near, far.sub(near).normalize(), Float.POSITIVE_INFINITY, city);
            }
            return;
        }
        try {
            var candidate = cursorPoint(x, y, width, height, projection, view, city);
            if (candidate == null) return;
            if (tool == 6) {
                int id = ownerId(city);
                if (id < 0) { message = "No eligible owners available"; return; }
                submit.accept(new CityCommand(CityCommand.SPECIAL, SpecialBuildings.type(specialKind, specialLevel),
                        List.of(new Polygon.Point((float)Math.floor(candidate.x()), (float)Math.floor(candidate.z()))), specialOwner, id));
                return;
            }
            if (tool == 8) {
                submit.accept(new CityCommand(CityCommand.SPECIAL, SpecialBuildings.AIRPORT, List.of(candidate), 0, 0));
                return;
            }
            if (tool == 9 || tool == 10) {
                var airport = city.buildings().stream().filter(b -> b.type() == SpecialBuildings.AIRPORT
                        && candidate.x() >= b.x() && candidate.x() < b.x()+Aviation.WIDTH
                        && candidate.z() >= b.z() && candidate.z() < b.z()+Aviation.depth(Aviation.runways(b))).findFirst().orElse(null);
                if (airport == null) { message = "Click an airport"; return; }
                if (tool == 10 && selectedCitizen == 0) { message = "Inspect an adult citizen first"; return; }
                submit.accept(tool == 9 ? new CityCommand(CityCommand.RUNWAY, airport.id(), List.of())
                        : new CityCommand(CityCommand.FLIGHT, airport.id(), List.of(), 0, selectedCitizen));
                return;
            }
            if (tool == 7) {
                submit.accept(new CityCommand(CityCommand.EXCHANGE, 0, List.of(candidate)));
                tool = -1;
                return;
            }
            points.add(candidate);
            if (tool == 4 && points.size() == 2) {
                submit.accept(new CityCommand(CityCommand.ROAD, roadType, points));
                points.clear();
            }
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
    }

    /** Shared by preview and click so the highlighted point is the submitted point. */
    public Polygon.Point cursorPoint(float x, float y, int w, int h,
            Matrix4f projection, Matrix4f view, CityFrame city) {
        if (roadMenu || tool < 0 || (tool > 4 && (tool < 6 || tool > 10)) || y < 130 || y > h - 200) return null;
        var inverse = new Matrix4f(projection).mul(view).invert();
        var a = inverse.transformProject(new Vector3f(x / w * 2 - 1, 1 - y / h * 2, -1));
        var b = inverse.transformProject(new Vector3f(x / w * 2 - 1, 1 - y / h * 2, 1));
        float ground = city.roads().isEmpty() ? 32 : city.roads().get(0).y() + 1.03f;
        float t = (ground - a.y) / (b.y - a.y);
        if (!Float.isFinite(t) || t < 0 || t > 1) return null;
        var hit = new Vector3f(a).lerp(b, t);
        var raw = new Polygon.Point(Math.round(hit.x * 2) / 2f, Math.round(hit.z * 2) / 2f);
        if (tool == 6 || tool == 7) return raw;
        Polygon.Point best = raw;
        float distance = 10 * 10;
        if (!points.isEmpty()) {
            for (var target : BuildingGuide.targets(points.get(0))) {
                var screen = project(target.x(), ground, target.z(), projection, view, w, h);
                if (screen != null && screen.distanceSquared(x, y) < distance
                        && BuildingGuide.distanceSquared(raw, target) <= 4) {
                    best = target;
                    distance = screen.distanceSquared(x, y);
                }
            }
        }
        return tool < 4 ? BuildingGuide.snapRoad(best, city) : best;
    }

    public void hover(float x, float y, int w, int h, Matrix4f projection,
            Matrix4f view, CityFrame city) {
        try {
            hover = cursorPoint(x, y, w, h, projection, view, city);
        } catch (IllegalArgumentException ignored) {
            hover = null;
        }
    }

    private void renderGuide(Overlay ui, int w, int h, Matrix4f projection,
            Matrix4f view, float ground) {
        if (points.isEmpty() || tool < 0 || tool > 4) return;
        var origin = points.get(0);
        for (int radius : BuildingGuide.RADII) {
            for (int i = 0; i < 180; i++) {
                double a = i * Math.PI * 2 / 180, b = (i + 1) * Math.PI * 2 / 180;
                edge(ui, project(origin.x() + radius * (float) Math.cos(a), ground,
                                origin.z() + radius * (float) Math.sin(a), projection, view, w, h),
                        project(origin.x() + radius * (float) Math.cos(b), ground,
                                origin.z() + radius * (float) Math.sin(b), projection, view, w, h),
                        .35f, .8f, 1);
            }
        }
        for (int direction = 0; direction < 8; direction++) {
            double angle = direction * Math.PI / 4;
            for (int d = 3; d <= 60; d += 3) {
                var p = project(origin.x() + d * (float) Math.cos(angle), ground,
                        origin.z() + d * (float) Math.sin(angle), projection, view, w, h);
                if (p != null && p.x >= 16 && p.x < w - 16 && p.y >= 130 && p.y < h - 200)
                    ui.rectangle(p.x - 1, p.y - 1, 3, 3, .6f, .85f, 1, .8f);
            }
        }
        for (var target : BuildingGuide.targets(origin)) {
            var p = project(target.x(), ground, target.z(), projection, view, w, h);
            if (p != null) ui.rectangle(p.x - 3, p.y - 3, 6, 6, .4f, .85f, 1, 1);
        }
        if (hover != null) {
            var p = project(hover.x(), ground, hover.z(), projection, view, w, h);
            var last = points.get(points.size() - 1);
            edge(ui, project(last.x(), ground, last.z(), projection, view, w, h), p, 1, 1, .2f);
            if (p != null) ui.rectangle(p.x - 5, p.y - 5, 10, 10, 1, 1, .2f, 1);
        }
    }

    /** Pick the nearest physical property, rather than an approximate screen-space circle. */
    public boolean selectRay(Vector3f origin, Vector3f direction, float limit, CityFrame city) {
        selectedCitizen = selectedBuilding = selectedPlot = selectedStreet = 0;
        selectedStreet = 0;
        var interval = new Vector2f();
        float best = limit;
        for (var b : city.buildings())
            if (Intersectionf.intersectRayAab(
                            origin.x,
                            origin.y,
                            origin.z,
                            direction.x,
                            direction.y,
                            direction.z,
                            b.x(),
                            b.y(),
                            b.z(),
                            b.x() + StructureBlueprint.width(b.type()),
                            b.y() + 7,
                            b.z() + (b.type() == SpecialBuildings.AIRPORT ? Aviation.depth(Aviation.runways(b)) : StructureBlueprint.depth(b.type())),
                            interval)
                    && interval.y >= 0
                    && Math.max(0, interval.x) < best) {
                best = Math.max(0, interval.x);
                selectedBuilding = b.id();
            }
        for (var p : city.economy().plots())
            if (p.building() == 0)
                if (Intersectionf.intersectRayAab(
                                origin.x,
                                origin.y,
                                origin.z,
                                direction.x,
                                direction.y,
                                direction.z,
                                p.x(),
                                p.y() - .1f,
                                p.z(),
                                p.x() + StructureBlueprint.width(p.type()),
                                p.y() + 1,
                                p.z() + StructureBlueprint.depth(p.type()),
                                interval)
                        && interval.y >= 0
                        && Math.max(0, interval.x) < best) {
                    best = Math.max(0, interval.x);
                    selectedPlot = p.id();
                    selectedBuilding = 0;
                }
        if (selectedBuilding == 0 && selectedPlot == 0 && direction.y < -.0001f) {
            for (var road : city.roads()) {
                float t = (road.y() + 1 - origin.y) / direction.y;
                float x = origin.x + direction.x * t, z = origin.z + direction.z * t;
                if (t >= 0
                        && t <= limit
                        && x >= road.x()
                        && x < road.x() + 1
                        && z >= road.z()
                        && z < road.z() + 1) {
                    var street = city.addresses().nearest(x, z);
                    if (street != null) selectedStreet = street.id();
                    break;
                }
            }
        }
        return selectedBuilding != 0 || selectedPlot != 0;
    }

    private static Vector2f project(
            float x, float y, float z, Matrix4f projection, Matrix4f view, int w, int h) {
        var p = new Vector4f(x, y, z, 1);
        view.transform(p);
        projection.transform(p);
        if (p.w <= 0 || p.z < -p.w || p.z > p.w) return null;
        return new Vector2f((p.x / p.w * .5f + .5f) * w, (.5f - p.y / p.w * .5f) * h);
    }

    private static void edge(Overlay ui, Vector2f a, Vector2f b, float r, float g, float blue) {
        if (a == null || b == null) return;
        float len = a.distance(b);
        int steps = Math.min(1000, Math.max(1, (int) (len / 2)));
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            ui.rectangle(
                    a.x + (b.x - a.x) * t - 1, a.y + (b.y - a.y) * t - 1, 3, 3, r, g, blue, .8f);
        }
    }

    public void render(
            Overlay ui,
            int w,
            int h,
            Matrix4f projection,
            Matrix4f view,
            CityFrame city,
            boolean isometric) {
        String time = city.config().time(city.elapsed()).label();
        var metrics = CityMetrics.from(city);
        int hungry = metrics.hungry();
        ui.rectangle(12, 68, w - 24, 61, .025f, .045f, .07f, .9f);
        ui.text(
                w < 900
                        ? time + " | Citizens " + metrics.population()
                        : "VOXEL CITY ONE | "
                                + time
                                + " | Citizens "
                                + metrics.population()
                                + " | Buildings "
                                + city.buildings().size()
                                + " | Hungry "
                                + hungry,
                22,
                78,
                1.5f);
        ui.text(
                String.format(
                        Locale.ROOT,
                        "MAYOR $%.0f | Roads $%.0f | Land sales +$%.0f | Homes %d/%d | Jobs %d/%d",
                        city.economy().budget(),
                        city.economy().roadSpending(),
                        city.economy().landRevenue(),
                        metrics.housed(),
                        metrics.population(),
                        metrics.employed(),
                        metrics.population()),
                22,
                103,
                1.4f);
        if (isometric) {
            ui.rectangle(w - 146, 74, 122, 26, .12f, .27f, .3f, 1);
            ui.text("Dashboard", w - 134, 82, 1.3f);
        }
        if (!isometric) return;
        if (!message.startsWith("Choose")) ui.text(message, 20, tool == 6 ? 486 : 156, 1.3f, 1, .7f, .2f, 1);
        float ground = city.roads().isEmpty() ? 32 : city.roads().get(0).y() + 1.04f;
        for (var zone : city.zones()) {
            float[] color =
                    zone.type() == 0
                            ? new float[] {.3f, .95f, .5f}
                            : zone.type() == 1
                                    ? new float[] {.25f, .65f, 1}
                                    : zone.type() == 3
                                            ? new float[] {.65f, .9f, .25f}
                                            : new float[] {1, .7f, .2f};
            var vs = zone.polygon().vertices();
            for (int i = 0; i < vs.size(); i++) {
                var a = vs.get(i);
                var b = vs.get((i + 1) % vs.size());
                edge(
                        ui,
                        project(a.x(), ground, a.z(), projection, view, w, h),
                        project(b.x(), ground, b.z(), projection, view, w, h),
                        color[0],
                        color[1],
                        color[2]);
            }
            var a = vs.get(0);
            var p = project(a.x(), ground + 1, a.z(), projection, view, w, h);
            if (p != null)
                ui.text(
                        CitySimulation.ZONES[zone.type()],
                        p.x,
                        p.y,
                        1.2f,
                        color[0],
                        color[1],
                        color[2],
                        1);
        }
        renderGuide(ui, w, h, projection, view, ground);
        boolean valid = true;
        if (tool == -1 && selectedCitizen == 0 && selectedStreet != 0) {
            ui.rectangle(16, 140, Math.min(400, w - 32), 65, .025f, .04f, .065f, .95f);
            ui.text(city.addresses().streetName(selectedStreet), 28, 152, 1.5f);
            ui.text("Public road | Mayor-owned access", 28, 179, 1.2f);
        }
        try {
            if (points.size() >= 3) new Polygon(points);
        } catch (IllegalArgumentException e) {
            valid = false;
        }
        for (int i = 0; i < points.size(); i++) {
            var a = points.get(i);
            var p = project(a.x(), ground, a.z(), projection, view, w, h);
            if (p != null) ui.rectangle(p.x - 4, p.y - 4, 8, 8, 1, valid ? 1 : .2f, .2f, 1);
            if (i > 0) {
                var b = points.get(i - 1);
                edge(
                        ui,
                        project(b.x(), ground, b.z(), projection, view, w, h),
                        p,
                        1,
                        valid ? 1 : .2f,
                        .2f);
            }
        }
        if (points.size() >= 3) {
            var a = points.get(0);
            var b = points.get(points.size() - 1);
            edge(
                    ui,
                    project(a.x(), ground, a.z(), projection, view, w, h),
                    project(b.x(), ground, b.z(), projection, view, w, h),
                    1,
                    valid ? 1 : .2f,
                    .2f);
        }
        if (tool == 6) {
            ui.rectangle(16,140,434,12*specialRowHeight(h),.025f,.04f,.065f,.95f);
            for (int row=0; row<12; row++) {
                String label;
                if (row < 6) label = (row == specialKind ? "> " : "  ") + SpecialBuildings.NAMES[row];
                else if (row == 6) label = "Level: " + specialLevel + " (click to cycle)";
                else if (row == 7) label = "Ownership: " + new String[]{"City government","Private individual","Private company"}[specialOwner];
                else if (row == 8) label = "Owner: " + (specialOwner == 0 ? "City government" : BuildingInfo.owner(city, specialOwner == 2 ? CityEconomy.COMPANY : 0, ownerId(city))) + " (click: next)";
                else if (row == 9) label = "Airport (city): 2000 | place terminal + runway";
                else if (row == 10) label = "Expand airport: 1000 | click airport";
                else label = "Book flight: inspect citizen, click destination";
                ui.text(label,24,149+row*specialRowHeight(h),Math.min(1.15f,specialRowHeight(h)/24f));
            }
        }
        if (roadMenu) {
            ui.rectangle(16, 140, Math.min(434, w - 32), 112, .025f, .04f, .065f, .97f);
            for (int row = 0; row < RoadTypes.NAMES.length; row++) {
                ui.text((row == roadType ? "> " : "  ") + RoadTypes.NAMES[row], 24, 149 + row * 28, 1.3f);
            }
        }
        float bw = (w - 32) / 9f, top = h - 196;
        String[] labels = {
            "Inspect",
            "Roads",
            "Residential",
            "Commercial",
            "Industrial",
            "Agriculture",
            "Economy",
            "Special", "Exchange"
        };
        for (int i = 0; i < 9; i++) {
            boolean active = tool == new int[] {-1, 4, 0, 1, 2, 3, 5, 6, 7}[i];
            ui.rectangle(
                    16 + i * bw,
                    top,
                    bw - 5,
                    34,
                    active ? .12f : .035f,
                    active ? .32f : .09f,
                    active ? .36f : .13f,
                    .95f);
            ui.text(labels[i], 22 + i * bw, top + 11, w < 900 ? 1f : 1.3f);
        }
        ui.rectangle(16, top + 38, w - 32, 35, .015f, .025f, .04f, .85f);
        ui.text(
                tool == 6
                        ? "Select building, level and owner | Click clear unzoned land: 6 x 9 | Front faces north | Esc: cancel"
                        : tool == 7
                                ? "Choose clear land near a road | Exchange: $600 | Graduate office staff"
                        : tool == 8 ? "Airport: 36 x 27 clear site | North entrance touches road | $2000 | Esc: cancel"
                        : tool == 9 ? "Click airport to add a runway | $1000 | Clear 36 x 16 strip to south | Maximum 3 runways"
                        : tool == 10 ? "Inspect an adult citizen, then click destination airport | Citizen walks to a connected origin"
                        : tool == 4
                        ? roadMenu ? "Choose a road | Esc: cancel" : RoadTypes.NAMES[roadType] + " | Click two endpoints | $4 per new or upgraded cell | Esc: cancel"
                        : tool >= 0 && tool < 4
                                ? "Click convex polygon corners | Enter: zone | Backspace: undo |"
                                        + " Esc: cancel"
                                : tool == 5
                                        ? "Private companies fund construction. Zoning is free."
                                                + " Inspect a building for ownership."
                                        : "WASD: pan | Wheel: zoom | Home: horizon | F6: walk in"
                                                + " the city",
                24,
                top + 48,
                1.25f);
        var citizen =
                city.visibleCitizens().stream()
                        .filter(c -> c.id() == selectedCitizen)
                        .findFirst()
                        .orElse(null);
        if (citizen != null && citizen.id() >= RegionalPopulation.AGENT_ID_BASE) {
            var agent=city.population().agents().stream().filter(a->a.id()==citizen.id()).findFirst().orElseThrow();
            var group=city.population().groups().stream().filter(g->g.id()==agent.group()).findFirst().orElseThrow();
            float x=Math.max(16,w-380);
            ui.rectangle(x,140,364,190,.025f,.04f,.065f,.95f);
            ui.text(citizen.name(),x+10,152,1.3f);
            ui.text(String.format(Locale.ROOT,"Money %.1f | Hunger %.1f / 100",agent.savings(),agent.hunger()),x+10,181,1.25f);
            ui.text(CitySimulation.COHORTS[group.cohort()]+" | Company #"+group.company(),x+10,211,1.1f);
            ui.text("Regional home: "+(agent.housed()?"Housed":"None")+" | "+(agent.employed()?"Employed":"No job"),x+10,240,1.15f);
            ui.text("Individual resident in the active district",x+10,271,1.05f);
            ui.text("Rejoins cohort simulation when out of view",x+10,299,1.05f);
            return;
        }
        if (citizen != null) {
            float x = Math.max(16, w - 350);
            ui.rectangle(x, 140, 334, 297, .025f, .04f, .065f, .95f);
            ui.text(
                    citizen.name() + " | " + CitySimulation.COHORTS[citizen.cohort()],
                    x + 10,
                    152,
                    1.35f);
            ui.text(
                    String.format(
                            java.util.Locale.ROOT,
                            "Money %.1f | Hunger %.0f / 100",
                            citizen.money(),
                            citizen.hunger()),
                    x + 10,
                    177,
                    1.4f);
            ui.text(
                    "Home: "
                            + (citizen.home() == 0
                                    ? "None"
                                    : city.addresses().buildingName(citizen.home()))
                            + " | "
                            + (citizen.job() < 0
                                    ? "Construction #" + (-citizen.job())
                                    : "Work #" + citizen.job()),
                    x + 10,
                    202,
                    1.3f);
            int employer =
                    citizen.job() < 0
                            ? city.economy().plots().stream()
                                    .filter(p -> p.id() == -citizen.job())
                                    .mapToInt(CityEconomy.Plot::developer)
                                    .findFirst()
                                    .orElse(0)
                            : city.economy().properties().stream()
                                    .filter(p -> p.building() == citizen.job())
                                    .mapToInt(CityEconomy.Property::operator)
                                    .findFirst()
                                    .orElse(0);
            ui.text("Employer: " + owner(city, CityEconomy.COMPANY, employer), x + 10, 252, 1.15f);
            ui.text(citizen.activity(), x + 10, 227, 1.15f);
            ui.text("Age " + (int)citizen.age() + " | " + citizen.gender(), x + 10, 277, 1.15f);
            ui.text("Education: " + citizen.education(), x + 10, 302, 1.15f);
            ui.text("Spouse: " + (citizen.spouse() == 0 ? "None" : "#" + citizen.spouse())
                    + " | Parents: " + citizen.mother() + "/" + citizen.father(), x + 10, 327, 1.05f);
            ui.text("School: " + (citizen.school() == 0 ? "None" : "#" + citizen.school())
                    + String.format(java.util.Locale.ROOT, " | Study %.2f years", citizen.study()), x + 10, 352, 1.05f);
            ui.text("Year: 12 city days | Classes 08:00-14:00", x + 10, 377, 1.05f);
            ui.text("Careers: " + (citizen.age() < 18 ? "Too young to work" : CitizenLife.career(citizen.education())), x + 10, 402, 1f);
        }
        if (tool == 5) {
            float x = Math.max(16, w - 410);
            ui.rectangle(x, 170, 394, 210, .025f, .04f, .065f, .97f);
            ui.text("PRIVATE COMPANIES", x + 10, 182, 1.4f);
            int row = 0;
            for (var firm : city.economy().firms()) {
                ui.text(firm.name(), x + 10, 212 + row * 29, 1.15f);
                ui.text(
                        String.format(Locale.ROOT, "$%.0f", firm.cash()),
                        x + 320,
                        212 + row * 29,
                        1.15f);
                row++;
            }
            ui.text(
                    "Construction jobs: "
                            + city.citizens().stream().filter(c -> c.job() < 0).count(),
                    x + 10,
                    358,
                    1.15f);
        }
        for (var plot : city.economy().plots())
            if (plot.building() == 0) {
                var p = project(plot.x() + 3, plot.y() + 1, plot.z() + 3, projection, view, w, h);
                if (p != null)
                    ui.text(
                            "Private build " + (int) (plot.work() / 8 * 100) + "%",
                            p.x,
                            p.y,
                            1.1f,
                            1,
                            .8f,
                            .3f,
                            1);
            }
        var property =
                city.economy().properties().stream()
                        .filter(p -> p.building() == selectedBuilding)
                        .findFirst()
                        .orElse(null);
        var plot =
                city.economy().plots().stream()
                        .filter(p -> p.id() == selectedPlot)
                        .findFirst()
                        .orElse(null);
        if (tool == -1 && selectedCitizen == 0 && (property != null || plot != null)) {
            float x = Math.max(16, w - 410);
            ui.rectangle(x, 170, 394, 130, .025f, .04f, .065f, .97f);
            if (property != null) {
                ui.text(city.addresses().buildingName(property.building()), x + 10, 182, 1.4f);
                ui.text(
                        "Owner: " + owner(city, property.ownerKind(), property.owner()),
                        x + 10,
                        207,
                        1.15f);
                ui.text(
                        "Business: " + owner(city, CityEconomy.COMPANY, property.operator()),
                        x + 10,
                        232,
                        1.15f);
                ui.text(
                        String.format(
                                Locale.ROOT,
                                "Value $%.0f | Rent $%.1f / day",
                                property.price(),
                                property.rent()),
                        x + 10,
                        257,
                        1.2f);
                long tenants =
                        city.economy().contracts().stream()
                                .filter(c -> c.building() == property.building() && !c.sale())
                                .count();
                ui.text("Active rental agreements: " + tenants, x + 10, 282, 1.1f);
            } else {
                ui.text("PLOT #" + plot.id() + " | PRIVATE DEVELOPMENT", x + 10, 182, 1.25f);
                ui.text(owner(city, CityEconomy.COMPANY, plot.developer()), x + 10, 207, 1.15f);
                ui.text(
                        String.format(
                                Locale.ROOT,
                                "Land $%.0f | Materials $%.0f",
                                plot.landPrice(),
                                plot.constructionCost()),
                        x + 10,
                        232,
                        1.2f);
                ui.text(
                        "Construction " + (int) (plot.work() / 8 * 100) + "% | Mayor cost $0",
                        x + 10,
                        257,
                        1.2f);
            }
        }
    }

    private static String owner(CityFrame city, int kind, int id) {
        if (id == 0) return "None";
        return kind == CityEconomy.COMPANY
                ? city.economy().firms().stream()
                        .filter(c -> c.id() == id)
                        .map(CityEconomy.Firm::name)
                        .findFirst()
                        .orElse("Company #" + id)
                : city.citizens().stream()
                        .filter(c -> c.id() == id)
                        .map(CityFrame.Citizen::name)
                        .findFirst()
                        .orElse("Citizen #" + id);
    }
}
