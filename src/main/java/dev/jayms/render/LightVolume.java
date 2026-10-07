package dev.jayms.render;

import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;

import java.util.*;

/** Bounded irradiance with adaptive transport through the world's actual voxel leaves. */
public final class LightVolume {
    @FunctionalInterface
    public interface Sampler {
        int value(int x, int y, int z);

        /** Required when value is mixed (-1). Values are encoded world materials, not model RGB. */
        default SparseVoxelOctree detail(int x, int y, int z) {
            return null;
        }
    }

    private static final int UNIT = 32, FULL = 15 * UNIT;
    public final int x, y, z, width, height, length;
    public final byte[] rgba;

    /** 0 for uniform cells; otherwise a root in the packed, sparse fine-light octree. */
    public final int[] roots;

    public int[] fine;
    public TransportField transport;
    private final Map<Integer, Detail> details = new HashMap<>();

    private record Detail(int resolution, SparseVoxelOctree nodes) {}

    private int[] material, cell, geometry, queue;
    private short[] sky, red, green, blue;
    private boolean[] queued;
    private int count, head, tail, size;

    private LightVolume(int x, int y, int z, int w, int h, int l) {
        this.x = x;
        this.y = y;
        this.z = z;
        width = w;
        height = h;
        length = l;
        int n = Math.multiplyExact(Math.multiplyExact(w, h), l);
        if (n > 3_000_000) throw new IllegalArgumentException("Lighting volume too large");
        rgba = new byte[n * 4];
        roots = new int[n];
        count = n;
        material = new int[n];
        cell = new int[n];
        geometry = new int[n];
    }

    public static LightVolume bake(int x, int y, int z, int w, int h, int l, Sampler source) {
        return bake(x, y, z, w, h, l, 1, source);
    }

    public static LightVolume bake(
            int x, int y, int z, int w, int h, int l, float ambient, Sampler source) {
        LightVolume v = new LightVolume(x, y, z, w, h, l);
        int n = w * h * l;
        for (int c = 0; c < n; c++) {
            int dx = c % w, dy = c / w % h, dz = c / (w * h);
            int value = source.value(x + dx, y + dy, z + dz);
            v.cell[c] = c;
            v.geometry[c] = UNIT << 18;
            v.material[c] = value;
            if (value == -1) {
                var tree =
                        Objects.requireNonNull(
                                source.detail(x + dx, y + dy, z + dz),
                                "Mixed lighting cell needs voxel geometry");
                int r = tree.size();
                if (r != 8 && r != 16 && r != 32)
                    throw new IllegalArgumentException("Lighting detail resolution");
                var d = new Detail(r, new SparseVoxelOctree(r));
                v.details.put(c, d);
                v.expand(tree, d, c, 0, 0, 0, r);
            }
        }
        v.sky = new short[v.count];
        v.red = new short[v.count];
        v.green = new short[v.count];
        v.blue = new short[v.count];
        v.queue = new int[v.count];
        v.queued = new boolean[v.count];
        // Only columns containing detail need sub-voxel sky rays; ordinary terrain stays coarse.
        for (int dz = 0; dz < l; dz++)
            for (int dx = 0; dx < w; dx++) {
                boolean detailed = false;
                for (int dy = 0; dy < h; dy++)
                    if (v.details.containsKey(v.index(dx, dy, dz))) {
                        detailed = true;
                        break;
                    }
                int r = detailed ? UNIT : 1;
                for (int az = 0; az < r; az++)
                    for (int ax = 0; ax < r; ax++) {
                        int light = FULL;
                        for (int micro = h * UNIT - 1; micro >= 0; ) {
                            int id = v.at(dx * UNIT + ax, micro, dz * UNIT + az);
                            int side = v.side(id), op = opacity(v.material[id]);
                            if (op == 255) light = 0;
                            else if (op != 0) light = Math.max(0, light - 2 * side);
                            if (light > v.sky[id]) {
                                v.sky[id] = (short) light;
                                v.offer(id);
                            }
                            micro -= side;
                        }
                    }
            }
        v.propagate(true);
        for (int i = 0; i < v.count; i++) {
            if (v.material[i] == -1) continue;
            if (WorldVoxels.decode(v.material[i]) == Blocks.LED) {
                int color = WorldVoxels.lightColor(v.material[i]);
                v.red[i] = (short) ((color >> 16 & 255) * FULL / 255);
                v.green[i] = (short) ((color >> 8 & 255) * FULL / 255);
                v.blue[i] = (short) ((color & 255) * FULL / 255);
                v.offer(i);
            } else if (opacity(v.material[i]) == 0 && v.sky[i] >= 8 * UNIT) {
                int g = v.geometry[i], s = v.side(i), c = v.cell[i];
                int a = c % w * UNIT + (g & 63),
                        b = c / w % h * UNIT + (g >> 6 & 63),
                        d = c / (w * h) * UNIT + (g >> 12 & 63);
                for (int f = 0; f < 6; f++) {
                    float cosine =
                            switch (f) {
                                case 1, 4 -> .45f;
                                case 3 -> .78f;
                                default -> 0;
                            };
                    if (cosine == 0) continue;
                    int j =
                            v.at(
                                    a + (f == 0 ? s : f == 1 ? -1 : s / 2),
                                    b + (f == 2 ? s : f == 3 ? -1 : s / 2),
                                    d + (f == 4 ? s : f == 5 ? -1 : s / 2));
                    if (j < 0 || opacity(v.material[j]) != 255) continue;
                    int color = WorldVoxels.surfaceColor(v.material[j]);
                    float strength = 5 * UNIT * cosine * v.sky[i] / FULL * ambient;
                    v.red[i] =
                            (short)
                                    Math.max(
                                            v.red[i],
                                            Math.round((color >> 16 & 255) / 255f * strength));
                    v.green[i] =
                            (short)
                                    Math.max(
                                            v.green[i],
                                            Math.round((color >> 8 & 255) / 255f * strength));
                    v.blue[i] =
                            (short)
                                    Math.max(
                                            v.blue[i], Math.round((color & 255) / 255f * strength));
                    v.offer(i);
                }
            }
        }
        v.propagate(false);
        int[] colors = new int[v.count];
        for (int i = 0; i < v.count; i++) {
            float sky = v.sky[i] / (float) FULL;
            sky *= sky * ambient;
            int r =
                    Math.min(
                            255,
                            Math.round(
                                    (.015f + .24f * sky + v.red[i] / (float) FULL * 1.8f) * 127));
            int g =
                    Math.min(
                            255,
                            Math.round(
                                    (.018f + .32f * sky + v.green[i] / (float) FULL * 1.8f) * 127));
            int b =
                    Math.min(
                            255,
                            Math.round(
                                    (.025f + .45f * sky + v.blue[i] / (float) FULL * 1.8f) * 127));
            int visibility = 128 + Math.round(v.sky[i] / (float) FULL * 127);
            colors[i] = visibility << 24 | b << 16 | g << 8 | r;
            if (i < n) {
                v.rgba[i * 4] = (byte) r;
                v.rgba[i * 4 + 1] = (byte) g;
                v.rgba[i * 4 + 2] = (byte) b;
                v.rgba[i * 4 + 3] = (byte) visibility;
            }
        }
        var packed = new ArrayList<Integer>();
        for (var e : v.details.entrySet()) {
            int root = v.pack(e.getValue(), colors, packed, 0, 0, 0, e.getValue().resolution);
            v.roots[e.getKey()] = root >= 0 ? root + 1 : root;
        }
        // Buffer textures require storage even when there are no refined cells.
        v.fine =
                packed.isEmpty()
                        ? new int[] {0}
                        : packed.stream().mapToInt(Integer::intValue).toArray();
        v.transport = new TransportField(w,h,l,v.material,v.rgba);
        v.material = v.cell = v.geometry = v.queue = null;
        v.sky = v.red = v.green = v.blue = null;
        v.queued = null;
        v.details.clear();
        return v;
    }

    private static int opacity(int value) {
        int type = WorldVoxels.decode(value);
        // Collision solidity is independent of optical transmission. All other materials are
        // opaque.
        return type == Blocks.AIR ? 0 : type == Blocks.GLASS || type == Blocks.WATER ? 1 : 255;
    }

    private void expand(SparseVoxelOctree tree, Detail d, int c, int a, int b, int z, int s) {
        int value = tree.uniform(a, b, z, s);
        if (value == -1) {
            int h = s / 2;
            for (int f = 0; f < 8; f++)
                expand(
                        tree,
                        d,
                        c,
                        a + ((f & 1) != 0 ? h : 0),
                        b + ((f & 2) != 0 ? h : 0),
                        z + ((f & 4) != 0 ? h : 0),
                        h);
            return;
        }
        if (count == material.length) {
            int n = Math.max(count + 1, count + count / 4);
            material = Arrays.copyOf(material, n);
            cell = Arrays.copyOf(cell, n);
            geometry = Arrays.copyOf(geometry, n);
        }
        int id = count++, scale = UNIT / d.resolution;
        material[id] = value;
        cell[id] = c;
        geometry[id] = a * scale | b * scale << 6 | z * scale << 12 | s * scale << 18;
        // Keep the lookup sparse too: a half-block of air remains one leaf, not 2,048 IDs.
        d.nodes.fill(a, b, z, a + s, b + s, z + s, 0xff000000 | id);
    }

    private int index(int a, int b, int c) {
        return a + width * (b + height * c);
    }

    private int side(int i) {
        return geometry[i] >>> 18;
    }

    private int at(int a, int b, int c) {
        if (a < 0
                || b < 0
                || c < 0
                || a >= width * UNIT
                || b >= height * UNIT
                || c >= length * UNIT) return -1;
        int i = index(a / UNIT, b / UNIT, c / UNIT);
        var d = details.get(i);
        if (d == null) return i;
        int scale = UNIT / d.resolution;
        return d.nodes.get((a % UNIT) / scale, (b % UNIT) / scale, (c % UNIT) / scale) & 0xffffff;
    }

    private void offer(int i) {
        if (queued[i]) return;
        queued[i] = true;
        queue[tail] = i;
        tail = (tail + 1) % queue.length;
        size++;
    }

    private void propagate(boolean sunlight) {
        while (size > 0) {
            int i = queue[head];
            head = (head + 1) % queue.length;
            size--;
            queued[i] = false;
            int c = cell[i], g = geometry[i], s = side(i);
            int a = c % width * UNIT + (g & 63),
                    b = c / width % height * UNIT + (g >> 6 & 63),
                    d = c / (width * height) * UNIT + (g >> 12 & 63);
            for (int f = 0; f < 6; f++) {
                // Tile the face at the adjacent leaf sizes, never connecting across a solid leaf.
                int step = s;
                for (int v = 0; v < s; v += step) {
                    int rowStep = s;
                    for (int u = 0; u < s; ) {
                        int j =
                                at(
                                        a + (f < 2 ? (f == 0 ? s : -1) : u),
                                        b + (f == 2 ? s : f == 3 ? -1 : f < 2 ? u : v),
                                        d + (f > 3 ? (f == 4 ? s : -1) : v));
                        int span = j < 0 ? s : Math.min(s, side(j));
                        rowStep = Math.min(rowStep, span);
                        u += span;
                        if (j < 0 || opacity(material[j]) == 255) continue;
                        int loss =
                                Math.max(1, (s + side(j)) / 2)
                                        * (opacity(material[j]) == 0 ? 1 : 3);
                        boolean changed;
                        if (sunlight) {
                            int light = sky[i] - loss;
                            changed = light > sky[j];
                            if (changed) sky[j] = (short) light;
                        } else
                            changed =
                                    spread(red, i, j, loss)
                                            | spread(green, i, j, loss)
                                            | spread(blue, i, j, loss);
                        if (changed) offer(j);
                    }
                    step = rowStep;
                }
            }
        }
    }

    private boolean spread(short[] channel, int i, int j, int loss) {
        int value = channel[i] - loss;
        if (value <= channel[j]) return false;
        channel[j] = (short) value;
        return true;
    }

    private int pack(Detail d, int[] colors, ArrayList<Integer> data, int x, int y, int z, int s) {
        int node = d.nodes.uniform(x, y, z, s);
        if (node != -1) return colors[node & 0xffffff];
        int h = s / 2;
        int[] children = new int[8];
        for (int f = 0; f < 8; f++)
            children[f] =
                    pack(
                            d,
                            colors,
                            data,
                            x + ((f & 1) != 0 ? h : 0),
                            y + ((f & 2) != 0 ? h : 0),
                            z + ((f & 4) != 0 ? h : 0),
                            h);
        boolean same = children[0] < 0;
        for (int child : children) same &= child == children[0];
        if (same) return children[0];
        int root = data.size();
        for (int child : children) data.add(child);
        return root;
    }

    public float[] sample(int a, int b, int c) {
        return sample(a + .5f, b + .5f, c + .5f);
    }

    /** Exact same nearest-cell / sparse-leaf lookup as the fragment shader. */
    public float[] sample(float a, float b, float c) {
        a -= x;
        b -= y;
        c -= z;
        if (a < 0 || b < 0 || c < 0 || a >= width || b >= height || c >= length)
            return new float[] {.24f, .32f, .45f};
        int i = index((int) a, (int) b, (int) c), node = roots[i];
        if (node == 0)
            return new float[] {
                (rgba[i * 4] & 255) / 127f,
                (rgba[i * 4 + 1] & 255) / 127f,
                (rgba[i * 4 + 2] & 255) / 127f
            };
        if (node > 0) node--;
        float fx = a - (int) a, fy = b - (int) b, fz = c - (int) c;
        for (int depth = 0; node >= 0 && depth < 5; depth++) {
            fx *= 2;
            fy *= 2;
            fz *= 2;
            int child = (fx >= 1 ? 1 : 0) | (fy >= 1 ? 2 : 0) | (fz >= 1 ? 4 : 0);
            fx %= 1;
            fy %= 1;
            fz %= 1;
            node = fine[node + child];
        }
        return new float[] {
            (node & 255) / 127f, (node >> 8 & 255) / 127f, (node >> 16 & 255) / 127f
        };
    }
}
