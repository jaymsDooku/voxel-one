package dev.jayms.net.model;

import java.io.IOException;
import java.util.Arrays;

/** Greedily merge coplanar, same-color surface faces. Interior voxels emit no triangles. */
public final class ModelMesher {
    public record Geometry(float[] vertices, int[] indices, int quads) {}

    public static final int MAX_QUADS = 8192;

    public static Geometry mesh(SparseVoxelOctree tree) throws IOException {
        return mesh(tree.size(), tree::get);
    }

    @FunctionalInterface
    public interface VoxelSampler {
        int get(int x, int y, int z);
    }

    public static Geometry mesh(int size, VoxelSampler sampler) throws IOException {
        return mesh(size, sampler, MAX_QUADS);
    }

    public static Geometry mesh(int size, VoxelSampler sampler, int maximumQuads)
            throws IOException {
        int quads = 0;
        float[] vertices = new float[36 * 128];
        int[] indices = new int[6 * 128];
        long[] mask = new long[size * size];
        for (int axis = 0; axis < 3; axis++) {
            int u = (axis + 1) % 3, v = (axis + 2) % 3;
            int[] cell = new int[3];
            for (int slice = -1; slice < size; slice++) {
                for (int j = 0; j < size; j++)
                    for (int i = 0; i < size; i++) {
                        cell[axis] = slice;
                        cell[u] = i;
                        cell[v] = j;
                        int a = sampler.get(cell[0], cell[1], cell[2]);
                        cell[axis]++;
                        int b = sampler.get(cell[0], cell[1], cell[2]);
                        mask[i + j * size] =
                                a != 0 && b == 0 && slice >= 0
                                        ? ((a & 0xffffffffL) << 1) | 1
                                        : a == 0 && b != 0 && slice + 1 < size
                                                ? (b & 0xffffffffL) << 1
                                                : 0;
                    }
                for (int j = 0; j < size; j++)
                    for (int i = 0; i < size; ) {
                        long key = mask[i + j * size];
                        if (key == 0) {
                            i++;
                            continue;
                        }
                        int width = 1;
                        while (i + width < size && mask[i + width + j * size] == key) width++;
                        int height = 1;
                        outer:
                        while (j + height < size) {
                            for (int k = 0; k < width; k++)
                                if (mask[i + k + (j + height) * size] != key) break outer;
                            height++;
                        }
                        if (quads >= maximumQuads)
                            throw new IOException(
                                    "Surface exceeds "
                                            + maximumQuads
                                            + " merged faces; simplify the design");
                        if ((quads + 1) * 36 > vertices.length) {
                            vertices = Arrays.copyOf(vertices, vertices.length * 2);
                            indices = Arrays.copyOf(indices, indices.length * 2);
                        }
                        boolean positive = (key & 1) == 1;
                        int color = (int) (key >>> 1);
                        float[] base = new float[3];
                        base[axis] = (slice + 1f) / size;
                        base[u] = (float) i / size;
                        base[v] = (float) j / size;
                        for (int corner = 0; corner < 4; corner++) {
                            int c = positive ? corner : new int[] {0, 3, 2, 1}[corner];
                            float[] p = base.clone();
                            if (c == 1 || c == 2) p[u] += (float) width / size;
                            if (c == 2 || c == 3) p[v] += (float) height / size;
                            int offset = quads * 36 + corner * 9;
                            System.arraycopy(p, 0, vertices, offset, 3);
                            vertices[offset + 3 + axis] = positive ? 1 : -1;
                            vertices[offset + 6] = ((color >>> 16) & 255) / 255f;
                            vertices[offset + 7] = ((color >>> 8) & 255) / 255f;
                            vertices[offset + 8] = (color & 255) / 255f;
                        }
                        int[] order = {0, 1, 2, 2, 3, 0};
                        for (int k = 0; k < 6; k++) indices[quads * 6 + k] = quads * 4 + order[k];
                        quads++;
                        for (int y = 0; y < height; y++)
                            Arrays.fill(mask, (j + y) * size + i, (j + y) * size + i + width, 0);
                        i += width;
                    }
            }
        }
        return new Geometry(
                Arrays.copyOf(vertices, quads * 36), Arrays.copyOf(indices, quads * 6), quads);
    }

    private ModelMesher() {}
}
