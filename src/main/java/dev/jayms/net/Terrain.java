package dev.jayms.net;

import java.util.*;

/** Seeded value noise and deterministic features, independent of chunk generation order. */
public final class Terrain {
    public static final long DEFAULT_SEED = 748291L;
    public static final int MIN_Y = -32, MAX_Y = 95, LIMIT = 1_000_000;

    public enum Biome {
        PLAINS,
        FOREST,
        DESERT,
        SNOWY_MOUNTAINS
    }

    public record Column(int height, Biome biome) {}

    public final long seed;
    private final Map<Long, Column> columns =
            new LinkedHashMap<>(4096, .75f, true) {
                protected boolean removeEldestEntry(Map.Entry<Long, Column> e) {
                    return size() > 32768;
                }
            };

    public Terrain(long seed) {
        this.seed = seed;
    }

    private long hash(int x, int z, long salt) {
        long n = seed ^ (x * 341873128712L) ^ (z * 132897987541L) ^ salt;
        n = (n ^ (n >>> 30)) * 0xbf58476d1ce4e5b9L;
        n = (n ^ (n >>> 27)) * 0x94d049bb133111ebL;
        return n ^ (n >>> 31);
    }

    private double value(int x, int z, long salt) {
        return (hash(x, z, salt) >>> 11) * 0x1.0p-53;
    }

    private double noise(double x, double z, long salt) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double a = x - ix, b = z - iz;
        a = a * a * (3 - 2 * a);
        b = b * b * (3 - 2 * b);
        double lo = value(ix, iz, salt) * (1 - a) + value(ix + 1, iz, salt) * a,
                hi = value(ix, iz + 1, salt) * (1 - a) + value(ix + 1, iz + 1, salt) * a;
        return lo * (1 - b) + hi * b;
    }

    public Column column(int x, int z) {
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        return columns.computeIfAbsent(
                key,
                k -> {
                    double climate = noise(x / 180.0, z / 180.0, 91),
                            wet = noise(x / 150.0, z / 150.0, 173);
                    Biome biome =
                            climate < .30
                                    ? Biome.SNOWY_MOUNTAINS
                                    : climate > .66
                                            ? Biome.DESERT
                                            : wet > .51 ? Biome.FOREST : Biome.PLAINS;
                    double h =
                            13
                                    + noise(x / 95.0, z / 95.0, 31) * 18
                                    + noise(x / 32.0, z / 32.0, 7) * 6
                                    + noise(x / 12.0, z / 12.0, 11) * 2;
                    h += Math.max(0, (.36 - climate) / .36) * 32;
                    return new Column((int) Math.floor(h), biome);
                });
    }

    public int block(int x, int y, int z) {
        if (y < MIN_Y || y > MAX_Y) return 0;
        Column c = column(x, z);
        int h = c.height;
        if (y <= h) {
            if (y < h - 5
                    && y > MIN_Y + 2
                    && noise(x / 18.0 + y * .19, z / 18.0 - y * .17, 401) > .82
                    && noise(x / 30.0, z / 30.0 + y * .11, 509) > .60) return 0;
            if (y == h)
                return c.biome == Biome.DESERT
                        ? Blocks.SAND
                        : c.biome == Biome.SNOWY_MOUNTAINS ? Blocks.SNOW : Blocks.GRASS;
            if (y >= h - 3) return c.biome == Biome.DESERT ? Blocks.SAND : Blocks.DIRT;
            return Blocks.STONE;
        }
        if (y > h + 9) return 0;
        int gx = Math.floorDiv(x, 12), gz = Math.floorDiv(z, 12);
        for (int a = gx - 1; a <= gx + 1; a++)
            for (int b = gz - 1; b <= gz + 1; b++) {
                int tx = a * 12 + 2 + (int) (value(a, b, 211) * 8),
                        tz = b * 12 + 2 + (int) (value(a, b, 277) * 8);
                if (Math.abs(tx - x) > 2 || Math.abs(tz - z) > 2) continue;
                Column tree = column(tx, tz);
                if (tree.biome != Biome.FOREST || value(a, b, 307) > .78) continue;
                int base = tree.height + 1, top = base + 4 + (int) (value(a, b, 331) * 2);
                if (x == tx && z == tz && y >= base && y < top) return Blocks.WOOD;
                if (y >= top - 2
                        && y <= top + 1
                        && Math.abs(tx - x) + Math.abs(tz - z) + (y == top + 1 ? 1 : 0) <= 3)
                    return Blocks.LEAVES;
            }
        return 0;
    }
}
