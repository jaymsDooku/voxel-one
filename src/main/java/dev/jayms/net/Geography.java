package dev.jayms.net;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Version 2 geography. The bounded drainage graph is immutable and sampled in world coordinates.
 * Tributaries terminate at trunk nodes, with monotonically descending water elevations to the sea.
 * Outside the pilot watershed, broad continental noise continues without region/chunk seams.
 */
public final class Geography {
    public record Fields(
            int height,
            int waterLevel,
            double temperature,
            double moisture,
            double fertility,
            double geology,
            boolean rockExposure,
            boolean settlement) {}

    public record Node(double x, double z, double level) {}

    public record Reach(Node upstream, Node downstream, double width) {}

    public static final int SEA_LEVEL = 14;
    private final boolean oceans;
    private final long seed;
    private final List<Reach> reaches;
    private final Map<Long, Fields> cache =
            new LinkedHashMap<>(4096, .75f, true) {
                protected boolean removeEldestEntry(Map.Entry<Long, Fields> e) {
                    return size() > 32768;
                }
            };

    public Geography(long seed) { this(seed, false); }

    public Geography(long seed, boolean oceans) {
        this.seed = seed;
        this.oceans = oceans;
        List<Reach> graph = new ArrayList<>();
        Node previous = null;
        for (int z = -384; z <= 448; z += 64) {
            Node node =
                    new Node(64 + (noise(0, z / 140.0, 23) - .5) * 40, z, 24 - (z + 384) / 83.2);
            if (previous != null) graph.add(new Reach(previous, node, 9));
            if (z == -128 || z == 128) {
                for (int side : new int[] {-1, 1}) {
                    Node source = new Node(node.x + side * 300, z - 180, node.level + 8);
                    Node bend = new Node(node.x + side * 140, z - 64, node.level + 3);
                    graph.add(new Reach(source, bend, 7));
                    graph.add(new Reach(bend, node, 7));
                }
            }
            previous = node;
        }
        reaches = List.copyOf(graph);
    }

    public List<Reach> drainage() {
        return reaches;
    }

    private double value(int x, int z, long salt) {
        long n = seed ^ x * 341873128712L ^ z * 132897987541L ^ salt;
        n = (n ^ n >>> 30) * 0xbf58476d1ce4e5b9L;
        n = (n ^ n >>> 27) * 0x94d049bb133111ebL;
        return ((n ^ n >>> 31) >>> 11) * 0x1.0p-53;
    }

    private double noise(double x, double z, long salt) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double a = curve(x - ix), b = curve(z - iz);
        return mix(
                mix(value(ix, iz, salt), value(ix + 1, iz, salt), a),
                mix(value(ix, iz + 1, salt), value(ix + 1, iz + 1, salt), a),
                b);
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static double curve(double v) {
        v = clamp(v);
        return v * v * (3 - 2 * v);
    }

    private static double mix(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private double valleyMacro(double x, double z) {
        double hill = Math.exp(-Math.pow((Math.abs(x) - 210) / 85.0, 2));
        double pass = 1 - .85 * Math.exp(-Math.pow((z - 160) / 45.0, 2));
        return 26 + (noise(x / 180, z / 180, 71) - .5) * 4 + hill * pass * 39;
    }

    private double broad(double x, double z) {
        double continent = noise(x / 950, z / 950, 31);
        double ridge = 1 - Math.abs(2 * noise(x / 290, z / 290, 43) - 1);
        double mountains = curve((ridge - .52) / .48);
        return 12 + curve((continent - .22) / .58) * 15 + mountains * 38;
    }

    public synchronized Fields fields(int x, int z) {
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        return cache.computeIfAbsent(key, k -> sample(x, z));
    }

    // A broad open sea beyond a seeded, gently curved shore; includes the pilot river mouth.
    private double shore(int x) { return 225 + (noise(x / 180.0, 0, 827) - .5) * 24; }

    public boolean ocean(int x, int z) {
        return oceans && z >= shore(x) && fields(x, z).height() < SEA_LEVEL;
    }

    private Fields sample(int x, int z) {
        // Soft watershed boundary: heights and fields blend before reaching the bounded graph edge.
        double pilot = 1 - curve((Math.max(Math.abs(x), Math.abs(z)) - 400) / 180.0);
        double hill = Math.exp(-Math.pow((Math.abs(x) - 210) / 85.0, 2));
        double pass = 1 - .85 * Math.exp(-Math.pow((z - 160) / 45.0, 2));
        double valley = valleyMacro(x, z);
        double h = mix(broad(x, z), valley, pilot);
        double slopeX =
                mix(
                                broad(x + 2, z) - broad(x - 2, z),
                                valleyMacro(x + 2, z) - valleyMacro(x - 2, z),
                                pilot)
                        / 4;
        double slopeZ =
                mix(
                                broad(x, z + 2) - broad(x, z - 2),
                                valleyMacro(x, z + 2) - valleyMacro(x, z - 2),
                                pilot)
                        / 4;
        double slope = Math.hypot(slopeX, slopeZ);
        // Fine erosion follows the macro slope. Flat land receives no high-frequency displacement.
        h += curve((slope - .12) / .5) * (noise(x / 13.0, z / 13.0, 73) - .5) * 5;
        double coast = curve((z - 350) / 70.0) * pilot;
        h = mix(h, 9, coast);
        double distance = Double.POSITIVE_INFINITY, level = 14, width = 5;
        for (Reach reach : reaches) {
            Node a = reach.upstream, b = reach.downstream;
            double dx = b.x - a.x, dz = b.z - a.z;
            double t = clamp(((x - a.x) * dx + (z - a.z) * dz) / (dx * dx + dz * dz));
            double d = Math.hypot(x - mix(a.x, b.x, t), z - mix(a.z, b.z, t));
            if (d < distance) {
                distance = d;
                level = mix(a.level, b.level, t);
                width = reach.width;
            }
        }
        double floodplain = (1 - curve((distance - width) / 28)) * pilot;
        h = mix(h, Math.min(h, level + 2), floodplain);
        boolean channel = distance <= width && pilot > 0;
        if (channel) {
            double bed = level - 3 + 5 * curve(distance / width);
            h = mix(h, Math.min(h, bed), pilot);
        }
        // Spawn settlement terrace: 48x48 level land with a smooth apron and no tree anchors.
        double terrace = 1 - curve((Math.max(Math.abs(x - 8), Math.abs(z - 24)) - 24) / 24.0);
        terrace *= curve((distance - width) / 3);
        h = mix(h, 26, terrace);
        boolean settlement = terrace == 1;
        boolean quarry = Math.hypot(x - 132, z + 72) < 18;
        double geology =
                clamp(noise(x / 110.0, z / 110.0, 509) * .8 + hill * .3 + (quarry ? .5 : 0));
        double temperature =
                clamp(noise(x / 420.0, z / 420.0, 91) * .9 + .2 - Math.max(0, h - 40) / 90);
        double moisture =
                clamp(noise(x / 270.0, z / 270.0, 173) * .8 + floodplain * .35 + hill * .15);
        double fertility = clamp(.25 + moisture * .65 + floodplain * .25 - hill * .5);
        if (settlement) {
            moisture = .48;
            fertility = .85;
        }
        if (oceans) {
            double marine = curve((z - shore(x) + 32) / 64);
            h = mix(h, -6 + noise(x / 100.0, z / 100.0, 829) * 7, marine);
            if (marine > 0 && h < SEA_LEVEL) {
                moisture = Math.max(.65, moisture);
                fertility *= 1 - marine;
            }
        }
        int water = Terrain.MIN_Y;
        if (channel && terrace == 0) water = (int) Math.floor(level);
        if (coast > .5 && h < 14) water = Math.max(water, 14);
        if (oceans && z > shore(x) - 32 && h < SEA_LEVEL) water = SEA_LEVEL;
        return new Fields(
                (int) Math.floor(Math.max(Terrain.MIN_Y + 4, Math.min(88, h))),
                water,
                temperature,
                moisture,
                fertility,
                geology,
                quarry || slope > .55 && h > 40,
                settlement);
    }
}
