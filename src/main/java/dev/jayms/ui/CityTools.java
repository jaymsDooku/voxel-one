package dev.jayms.ui;

import static org.lwjgl.glfw.GLFW.*;

import dev.jayms.net.*;
import dev.jayms.net.city.*;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.*;
import java.util.function.Consumer;

/** Isometric planning tools. Click polygon corners, then explicitly confirm with Enter. */
public final class CityTools {
    public int tool = -1, selectedCitizen;
    private final List<Polygon.Point> points = new ArrayList<>();
    public String message = "Choose a tool; click citizens to inspect their household.";

    public boolean key(int key, Consumer<CityCommand> submit) {
        if (key == GLFW_KEY_ESCAPE && (!points.isEmpty() || tool != -1)) {
            points.clear();
            tool = -1;
            return true;
        }
        if (key == GLFW_KEY_BACKSPACE && !points.isEmpty()) {
            points.remove(points.size() - 1);
            return true;
        }
        if (key == GLFW_KEY_ENTER && tool >= 0 && tool < 3) {
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
        float top = height - 196;
        if (y >= top && y <= top + 34 && x >= 16 && x < width - 16) {
            int index = (int) ((x - 16) / ((width - 32) / 5f));
            tool = new int[] {-1, 3, 0, 1, 2}[Math.min(4, index)];
            points.clear();
            return;
        }
        if (y < 130 || y > height - 200) return;
        if (tool == -1) {
            float best = 22 * 22;
            selectedCitizen = 0;
            for (var c : city.citizens()) {
                var p = project(c.x(), c.y() + 1, c.z(), projection, view, width, height);
                if (p != null && p.distanceSquared(x, y) < best) {
                    best = p.distanceSquared(x, y);
                    selectedCitizen = c.id();
                }
            }
            return;
        }
        Matrix4f inverse = new Matrix4f(projection).mul(view).invert();
        Vector3f a = new Vector3f(), b = new Vector3f();
        a.set(x / width * 2 - 1, 1 - y / height * 2, -1);
        b.set(x / width * 2 - 1, 1 - y / height * 2, 1);
        inverse.transformProject(a);
        inverse.transformProject(b);
        float ground = city.roads().isEmpty() ? 32 : city.roads().get(0).y() + 1.03f;
        float t = (ground - a.y) / (b.y - a.y);
        if (t < 0 || t > 1) return;
        var hit = new Vector3f(a).lerp(b, t);
        try {
            points.add(new Polygon.Point(Math.round(hit.x * 2) / 2f, Math.round(hit.z * 2) / 2f));
            if (tool == 3 && points.size() == 2) {
                submit.accept(new CityCommand(CityCommand.ROAD, 0, points));
                points.clear();
            }
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
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
        double hour = city.config().hour(city.elapsed());
        String time =
                String.format(
                        java.util.Locale.ROOT, "%02d:%02d", (int) hour, (int) (hour % 1 * 60));
        int hungry = (int) city.citizens().stream().filter(c -> c.hunger() < 35).count();
        ui.rectangle(12, 68, w - 24, 61, .025f, .045f, .07f, .9f);
        ui.text(
                "VOXEL CITY ONE | "
                        + time
                        + " | Citizens "
                        + city.citizens().size()
                        + " | Buildings "
                        + city.buildings().size()
                        + " | Hungry "
                        + hungry,
                22,
                78,
                1.5f);
        ui.text(
                "Homes "
                        + city.citizens().stream().filter(c -> c.home() != 0).count()
                        + " / "
                        + city.citizens().size()
                        + " | Jobs "
                        + city.citizens().stream().filter(c -> c.job() != 0).count()
                        + " / "
                        + city.citizens().size()
                        + " | F6: plan city | H: dismount",
                22,
                103,
                1.4f);
        if (!isometric) return;
        if(!message.startsWith("Choose"))ui.text(message,20,156,1.3f,1,.7f,.2f,1);
        float ground = city.roads().isEmpty() ? 32 : city.roads().get(0).y() + 1.04f;
        for (var zone : city.zones()) {
            float[] color =
                    zone.type() == 0
                            ? new float[] {.3f, .95f, .5f}
                            : zone.type() == 1
                                    ? new float[] {.25f, .65f, 1}
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
        boolean valid = true;
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
        float bw = (w - 32) / 5f, top = h - 196;
        String[] labels = {"Inspect", "Dirt road", "Residential", "Commercial", "Industrial"};
        for (int i = 0; i < 5; i++) {
            boolean active = tool == new int[] {-1, 3, 0, 1, 2}[i];
            ui.rectangle(
                    16 + i * bw,
                    top,
                    bw - 5,
                    34,
                    active ? .12f : .035f,
                    active ? .32f : .09f,
                    active ? .36f : .13f,
                    .95f);
            ui.text(labels[i], 22 + i * bw, top + 11, 1.4f);
        }
        ui.rectangle(16, top + 38, w - 32, 35, .015f, .025f, .04f, .85f);
        ui.text(
                tool == 3
                        ? "Click two road endpoints. Roads follow the voxel grid."
                        : tool >= 0
                                ? "Click convex polygon corners | Enter: build | Backspace: undo |"
                                      + " Esc: cancel"
                                : "WASD: pan | Wheel: zoom | Home: horizon | F6: walk in the city",
                24,
                top + 48,
                1.25f);
        var citizen =
                city.citizens().stream()
                        .filter(c -> c.id() == selectedCitizen)
                        .findFirst()
                        .orElse(null);
        if (citizen != null) {
            float x = Math.max(16, w - 350);
            ui.rectangle(x, 140, 334, 119, .025f, .04f, .065f, .95f);
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
            ui.text("Home #" + citizen.home() + " | Mine job #" + citizen.job(), x + 10, 202, 1.4f);
            ui.text(citizen.activity(), x + 10, 227, 1.4f);
        }
    }
}
