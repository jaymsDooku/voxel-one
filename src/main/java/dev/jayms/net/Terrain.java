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

    public static final int LEGACY_VERSION = 1, GEOGRAPHY_VERSION = 2, CURRENT_VERSION = 3;
    public final int version;
    public final long seed;
    private final Geography geography;
    private dev.jayms.net.city.StressGrid stressGrid;
    public void stressGrid(dev.jayms.net.city.StressGrid grid) { stressGrid=grid; columns.clear(); }
    public dev.jayms.net.city.StressGrid stressGrid() { return stressGrid; }
    private final Map<Long, Column> columns =
            new LinkedHashMap<>(4096, .75f, true) {
                protected boolean removeEldestEntry(Map.Entry<Long, Column> e) {
                    return size() > 32768;
                }
            };

    public Terrain(long seed) {
        this(seed, CURRENT_VERSION);
    }

    public Terrain(long seed, int version) {
        if (version < LEGACY_VERSION || version > CURRENT_VERSION)
            throw new IllegalArgumentException("Unsupported terrain generator version: " + version);
        this.seed = seed;
        this.version = version;
        geography = version >= GEOGRAPHY_VERSION ? new Geography(seed, version >= 3) : null;
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

    public Geography.Fields fields(int x, int z) {
        return geography == null ? null : geography.fields(x, z);
    }

    /** Ocean identity comes from the generator, never from placed water or river channels. */
    public boolean ocean(int x, int z) {
        return geography != null && geography.ocean(x, z);
    }

    public int surfaceHeight(int x, int z) {
        if (stressGrid != null && stressGrid.contains(x,z)) return stressGrid.grade();
        return Math.max(
                column(x, z).height(), geography == null ? MIN_Y : fields(x, z).waterLevel());
    }

    public synchronized Column column(int x, int z) {
        if (stressGrid != null && stressGrid.contains(x,z)) return new Column(stressGrid.grade(),Biome.PLAINS);
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        return columns.computeIfAbsent(
                key,
                k -> {
                    if (geography != null) {
                        var f = fields(x, z);
                        Biome biome =
                                f.temperature() < .28
                                        ? Biome.SNOWY_MOUNTAINS
                                        : f.moisture() < .28
                                                ? Biome.DESERT
                                                : f.moisture() > .53 && f.fertility() > .3
                                                        ? Biome.FOREST
                                                        : Biome.PLAINS;
                        return new Column(f.height(), biome);
                    }
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
        if (stressGrid != null && stressGrid.contains(x,z)) {
            if (y > stressGrid.grade()) return Blocks.AIR;
            return y == stressGrid.grade() ? stressGrid.surface(x,z) : y > stressGrid.grade()-5 ? Blocks.DIRT : Blocks.STONE;
        }
        Column c = column(x, z);
        int h = c.height;
        if (y <= h) {
            if (y < h - 5
                    && y > MIN_Y + 2
                    && noise(x / 18.0 + y * .19, z / 18.0 - y * .17, 401) > .82
                    && noise(x / 30.0, z / 30.0 + y * .11, 509) > .60) return 0;
            if (geography != null && fields(x, z).rockExposure() && y >= h - 3) return Blocks.STONE;
            if (geography != null
                    && y < h - 4
                    && y > MIN_Y + 2
                    && fields(x, z).geology() > .58
                    && value(Math.floorDiv(x, 5), Math.floorDiv(z, 5), 719 + Math.floorDiv(y, 4))
                            > .78) return Blocks.MINERAL;
            if (y == h)
                return c.biome == Biome.DESERT
                        ? Blocks.SAND
                        : c.biome == Biome.SNOWY_MOUNTAINS ? Blocks.SNOW : Blocks.GRASS;
            if (y >= h - 3) return c.biome == Biome.DESERT ? Blocks.SAND : Blocks.DIRT;
            return Blocks.STONE;
        }
        if (geography != null && y <= fields(x, z).waterLevel()) return Blocks.WATER;
        if (y > h + 9) return 0;
        int gx = Math.floorDiv(x, 12), gz = Math.floorDiv(z, 12);
        for (int a = gx - 1; a <= gx + 1; a++)
            for (int b = gz - 1; b <= gz + 1; b++) {
                int tx = a * 12 + 2 + (int) (value(a, b, 211) * 8),
                        tz = b * 12 + 2 + (int) (value(a, b, 277) * 8);
                if (Math.abs(tx - x) > 2 || Math.abs(tz - z) > 2) continue;
                Column tree = column(tx, tz);
                if (geography != null
                        && (fields(tx, tz).waterLevel() >= tree.height
                                || fields(tx, tz).settlement()
                                || fields(tx, tz).rockExposure())) continue;
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
