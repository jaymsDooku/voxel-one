package dev.jayms.ui;

import dev.jayms.net.city.CityFrame;

import java.util.*;

/** One metric per chart so dollars, counts and percentages never share a misleading scale. */
public final class MetricTrends {
    public boolean open;
    private int domain, entity, metric;
    private static final String[] DOMAINS = {"Population", "Companies", "Government", "Workplaces"};
    private List<String> entities = List.of(), metrics = List.of();

    public void selectDomain(int value) {
        domain = value;
        entity = metric = 0;
    }

    public void click(float x, float y, int w) {
        if (x < 24 || x >= w - 24) return;
        if (y >= 138 && y <= 170) {
            domain = Math.min(3, (int) ((x - 24) / ((w - 48) / 4f)));
            entity = metric = 0;
        }
        if (y >= 184 && y <= 214 && !entities.isEmpty()) {
            entity = Math.floorMod(entity + (x < w / 2f ? -1 : 1), entities.size());
            metric = 0;
        }
        if (y >= 226 && y <= 256 && !metrics.isEmpty())
            metric = Math.floorMod(metric + (x < w / 2f ? -1 : 1), metrics.size());
    }

    private static void label(Overlay ui, String s, float x, float y, float width) {
        while (!s.isEmpty() && ui.textWidth(s, 1.2f) > width) s = s.substring(0, s.length() - 1);
        ui.text(s, x, y, 1.2f);
    }

    public void render(Overlay ui, int w, int h, CityFrame city, MetricHistory history) {
        var samples = history.samples();
        for (int i = 0; i < 4; i++) {
            float x = 24 + i * (w - 48) / 4f;
            ui.rectangle(x, 138, (w - 48) / 4f - 6, 32, .055f, .085f, .115f, 1);
            label(ui, DOMAINS[i], x + 10, 148, (w - 48) / 4f - 20);
            if (domain == i) ui.rectangle(x, 168, (w - 48) / 4f - 6, 2, .3f, .85f, .7f, 1);
        }
        var keys =
                samples.isEmpty()
                        ? Set.<String>of()
                        : samples.get(samples.size() - 1).values().keySet();
        entities =
                keys.stream()
                        .filter(k -> k.startsWith(DOMAINS[domain] + " / "))
                        .map(k -> k.split(" / ")[1])
                        .distinct()
                        .toList();
        entity = Math.min(entity, Math.max(0, entities.size() - 1));
        String prefix =
                DOMAINS[domain] + " / " + (entities.isEmpty() ? "" : entities.get(entity)) + " / ";
        metrics = keys.stream().filter(k -> k.startsWith(prefix)).toList();
        metric = Math.min(metric, Math.max(0, metrics.size() - 1));
        label(
                ui,
                "<  " + (entities.isEmpty() ? "No entities" : entities.get(entity)) + "  >",
                36,
                194,
                w - 72);
        label(
                ui,
                "<  "
                        + (metrics.isEmpty()
                                ? "No metrics"
                                : metrics.get(metric).substring(prefix.length()))
                        + "  >",
                36,
                236,
                w - 72);
        label(
                ui,
                "Click left / right half of each selector to change entity or metric",
                36,
                270,
                w - 72);
        if (metrics.isEmpty()) return;
        String key = metrics.get(metric);
        var points = samples.stream().filter(s -> s.values().containsKey(key)).toList();
        if (points.isEmpty()) return;
        double min = points.stream().mapToDouble(s -> s.values().get(key)).min().orElse(0);
        double max = points.stream().mapToDouble(s -> s.values().get(key)).max().orElse(0);
        if (max == min) {
            double pad = Math.max(1, Math.abs(min) * .05);
            min -= pad;
            max += pad;
        }
        float left = 110, right = w - 36, top = 310, bottom = Math.max(top + 40, h - 110);
        for (int i = 0; i <= 4; i++) {
            float y = top + (bottom - top) * i / 4;
            ui.rectangle(left, y, right - left, 1, .12f, .18f, .22f, 1);
            label(ui, String.format(Locale.ROOT, "%.1f", max - (max - min) * i / 4), 26, y - 4, 80);
        }
        double start = samples.get(0).elapsed(), end = samples.get(samples.size() - 1).elapsed();
        MetricHistory.Sample previous = null;
        float px = 0, py = 0;
        for (var s : samples) {
            Double value = s.values().get(key);
            if (value == null) {
                previous = null;
                continue;
            }
            float x =
                    left
                            + (float) ((s.elapsed() - start) / Math.max(1, end - start))
                                    * (right - left);
            float y = bottom - (float) ((value - min) / (max - min)) * (bottom - top);
            if (previous != null) ui.line(px, py, x, y, 2, .3f, .85f, .7f, 1);
            ui.rectangle(x - 2, y - 2, 4, 4, .3f, .85f, .7f, 1);
            previous = s;
            px = x;
            py = y;
        }
        label(ui, city.config().time(start).label(), left, bottom + 14, (right - left) / 2);
        label(
                ui,
                city.config().time(end).label(),
                (left + right) / 2,
                bottom + 14,
                (right - left) / 2);
        label(
                ui,
                String.format(
                        Locale.ROOT,
                        "Latest %.2f | %d observations | Simulation time (seconds)",
                        points.get(points.size() - 1).values().get(key),
                        points.size()),
                36,
                bottom + 36,
                w - 72);
        label(
                ui,
                "Session history: 5s samples, last 240; finance flows are cumulative, balances are"
                        + " current.",
                36,
                bottom + 56,
                w - 72);
    }
}
