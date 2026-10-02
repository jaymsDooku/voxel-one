package dev.jayms.render;

import dev.jayms.net.*;

/** Bounded voxel irradiance: skylight, one diffuse bounce and occluded RGB light propagation. */
public final class LightVolume {
    @FunctionalInterface
    public interface Sampler {
        int value(int x, int y, int z);
    }

    public final int x, y, z, width, height, length;
    public final byte[] rgba;
    private byte[] sky, red, green, blue, opacity;
    private int[] material;
    private int[] queue;
    private boolean[] queued;
    private int head, tail, size;

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
        sky = new byte[n];
        red = new byte[n];
        green = new byte[n];
        blue = new byte[n];
        opacity = new byte[n];
        material = new int[n];
        queue = new int[n];
        queued = new boolean[n];
    }

    public static LightVolume bake(int x, int y, int z, int w, int h, int l, Sampler source) {
        return bake(x, y, z, w, h, l, 1, source);
    }

    public static LightVolume bake(
            int x, int y, int z, int w, int h, int l, float ambient, Sampler source) {
        LightVolume v = new LightVolume(x, y, z, w, h, l);
        for (int dz = 0; dz < l; dz++)
            for (int dy = 0; dy < h; dy++)
                for (int dx = 0; dx < w; dx++) {
                    int i = v.index(dx, dy, dz),
                            value = source.value(x + dx, y + dy, z + dz),
                            type = WorldVoxels.decode(value);
                    v.material[i] = value;
                    v.opacity[i] =
                            (byte)
                                    (type == 0
                                            ? 0
                                            : type == Blocks.PARTIAL
                                                    ? 120
                                                    : type == Blocks.GLASS
                                                            ? 60
                                                            : Blocks.isModel(type) ? 40 : 255);
                }
        for (int dz = 0; dz < l; dz++)
            for (int dx = 0; dx < w; dx++) {
                int light = 15;
                for (int dy = h - 1; dy >= 0; dy--) {
                    int i = v.index(dx, dy, dz);
                    int op = v.opacity[i] & 255;
                    if (op == 255) light = 0;
                    else if (op > 0) light = Math.max(0, light - 2);
                    v.sky[i] = (byte) light;
                    if (light > 0) v.offer(i);
                }
            }
        v.propagate(true);
        for (int i = 0; i < v.material.length; i++) {
            int type = WorldVoxels.decode(v.material[i]);
            if (type == Blocks.LED) {
                int c = WorldVoxels.lightColor(v.material[i]);
                v.red[i] = (byte) ((c >> 16 & 255) * 15 / 255);
                v.green[i] = (byte) ((c >> 8 & 255) * 15 / 255);
                v.blue[i] = (byte) ((c & 255) * 15 / 255);
                v.offer(i);
            } else if ((v.opacity[i] & 255) == 0) {
                int dx = i % w, dy = i / w % h, dz = i / (w * h);
                for (int face = 0; face < 6; face++) {
                    int j = v.neighbor(dx, dy, dz, face);
                    if (j < 0 || (v.opacity[j] & 255) < 200) continue;
                    // Reflect sunlit surface albedo into the adjacent air cell.
                    float cosine =
                            switch (face) {
                                case 1 -> .45f;
                                case 3 -> .78f;
                                case 4 -> .45f;
                                default -> 0;
                            };
                    int sun = v.sky[i] & 255;
                    if (sun < 8 || cosine == 0) continue;
                    int color = WorldVoxels.surfaceColor(v.material[j]);
                    float strength = 5 * cosine * sun / 15f * ambient;
                    v.red[i] =
                            (byte)
                                    Math.max(
                                            v.red[i] & 255,
                                            Math.round((color >> 16 & 255) / 255f * strength));
                    v.green[i] =
                            (byte)
                                    Math.max(
                                            v.green[i] & 255,
                                            Math.round((color >> 8 & 255) / 255f * strength));
                    v.blue[i] =
                            (byte)
                                    Math.max(
                                            v.blue[i] & 255,
                                            Math.round((color & 255) / 255f * strength));
                    v.offer(i);
                }
            }
        }
        v.propagate(false);
        for (int i = 0; i < v.material.length; i++) {
            float skylight = (v.sky[i] & 255) / 15f;
            skylight *= skylight * ambient;
            v.rgba[i * 4] =
                    (byte)
                            Math.min(
                                    255,
                                    Math.round(
                                            (.015f
                                                            + .24f * skylight
                                                            + (v.red[i] & 255) / 15f * 1.8f)
                                                    * 127));
            v.rgba[i * 4 + 1] =
                    (byte)
                            Math.min(
                                    255,
                                    Math.round(
                                            (.018f
                                                            + .32f * skylight
                                                            + (v.green[i] & 255) / 15f * 1.8f)
                                                    * 127));
            v.rgba[i * 4 + 2] =
                    (byte)
                            Math.min(
                                    255,
                                    Math.round(
                                            (.025f
                                                            + .45f * skylight
                                                            + (v.blue[i] & 255) / 15f * 1.8f)
                                                    * 127));
            v.rgba[i * 4 + 3] = (byte) 255;
        }
        v.sky = v.red = v.green = v.blue = v.opacity = null;
        v.material = v.queue = null;
        v.queued = null;
        return v;
    }

    private int index(int a, int b, int c) {
        return a + width * (b + height * c);
    }

    private int neighbor(int a, int b, int c, int f) {
        switch (f) {
            case 0 -> a++;
            case 1 -> a--;
            case 2 -> b++;
            case 3 -> b--;
            case 4 -> c++;
            case 5 -> c--;
        }
        return a < 0 || b < 0 || c < 0 || a >= width || b >= height || c >= length
                ? -1
                : index(a, b, c);
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
            int a = i % width, b = i / width % height, c = i / (width * height);
            for (int f = 0; f < 6; f++) {
                int j = neighbor(a, b, c, f);
                if (j < 0 || (opacity[j] & 255) == 255) continue;
                int loss = (opacity[j] & 255) > 0 ? 3 : 1;
                boolean changed = false;
                if (sunlight) {
                    int value = (sky[i] & 255) - loss;
                    if (value > (sky[j] & 255)) {
                        sky[j] = (byte) value;
                        changed = true;
                    }
                } else {
                    changed =
                            spread(red, i, j, loss)
                                    | spread(green, i, j, loss)
                                    | spread(blue, i, j, loss);
                }
                if (changed) offer(j);
            }
        }
    }

    private boolean spread(byte[] channel, int i, int j, int loss) {
        int v = (channel[i] & 255) - loss;
        if (v > (channel[j] & 255)) {
            channel[j] = (byte) v;
            return true;
        }
        return false;
    }

    public float[] sample(int a, int b, int c) {
        a -= x;
        b -= y;
        c -= z;
        if (a < 0 || b < 0 || c < 0 || a >= width || b >= height || c >= length)
            return new float[] {.24f, .32f, .45f};
        int i = index(a, b, c) * 4;
        return new float[] {
            (rgba[i] & 255) / 127f, (rgba[i + 1] & 255) / 127f, (rgba[i + 2] & 255) / 127f
        };
    }
}
