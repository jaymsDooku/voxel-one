package dev.jayms;

import java.util.ArrayList;
import java.util.List;

public final class MeshDataGenerator {

    private static final int[] FACE_INDICES = {
        0, 1, 2,
        2, 3, 0
    };

    public static MeshData generate(Chunk chunk) {
        List<Float> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        int vertexCount = 0;

        for (int y = 0; y < Chunk.HEIGHT; y++) {
            for (int z = 0; z < Chunk.LENGTH; z++) {
                for (int x = 0; x < Chunk.WIDTH; x++) {
                    if (chunk.getBlock(x, y, z) == 0
                            || dev.jayms.net.Blocks.isModel(chunk.getBlock(x, y, z))) {
                        continue;
                    }

                    if (chunk.getBlock(x, y, z) == dev.jayms.net.Blocks.PARTIAL) {
                        vertexCount = addPartial(vertices, indices, chunk, x, y, z, vertexCount);
                        continue;
                    }
                    for (Face face : Face.values()) {
                        int nX = x + face.dx();
                        int nY = y + face.dy();
                        int nZ = z + face.dz();

                        if (chunk.neighbor(nX, nY, nZ) != 0
                                && chunk.neighbor(nX, nY, nZ) != dev.jayms.net.Blocks.PARTIAL
                                && !dev.jayms.net.Blocks.isModel(chunk.neighbor(nX, nY, nZ))) {
                            continue;
                        }

                        addFace(
                                vertices,
                                indices,
                                face,
                                x,
                                y,
                                z,
                                vertexCount,
                                chunk.getBlock(x, y, z));

                        vertexCount += 4;
                    }
                }
            }
        }

        float[] v = toFloatArray(vertices);
        float[] surface = new float[v.length / 9 * 3];
        for (int base = 0; base < v.length; base += 36) {
            float cx = 0, cy = 0, cz = 0;
            for (int j = 0; j < 4; j++) {
                cx += v[base + j * 9];
                cy += v[base + j * 9 + 1];
                cz += v[base + j * 9 + 2];
            }
            int fx = (int) Math.floor((cx / 4 - v[base + 3] * .001f) * 16),
                    fy = (int) Math.floor((cy / 4 - v[base + 4] * .001f) * 16),
                    fz = (int) Math.floor((cz / 4 - v[base + 5] * .001f) * 16);
            int raw = chunk.value(fx, fy, fz), type = dev.jayms.net.WorldVoxels.decode(raw);
            for (int j = 0; j < 4; j++) {
                int i = base + j * 9;
                if (type == dev.jayms.net.Blocks.LED) {
                    int c = dev.jayms.net.WorldVoxels.lightColor(raw);
                    v[i + 6] = (c >> 16 & 255) / 255f;
                    v[i + 7] = (c >> 8 & 255) / 255f;
                    v[i + 8] = (c & 255) / 255f;
                }
                surface[i / 3] = type == dev.jayms.net.Blocks.LED ? 4 : 0;
                surface[i / 3 + 1] =
                        type == dev.jayms.net.Blocks.GLASS
                                ? .12f
                                : type == dev.jayms.net.Blocks.LED ? .25f : .85f;
                surface[i / 3 + 2] =
                        dev.jayms.render.MaterialTextures.layer(
                                type == 1 && v[i + 4] < 1 ? 2 : type);
            }
        }
        return new MeshData(v, toIntArray(indices), surface);
    }

    private static void addFace(
            List<Float> vertices,
            List<Integer> indices,
            Face face,
            float blockX,
            float blockY,
            float blockZ,
            int vertexOffset,
            int type) {
        addFace(vertices, indices, face, blockX, blockY, blockZ, vertexOffset, type, 1);
    }

    private static void addFace(
            List<Float> vertices,
            List<Integer> indices,
            Face face,
            float blockX,
            float blockY,
            float blockZ,
            int vertexOffset,
            int type,
            float scale) {
        float[] faceVertices = face.vertices();

        for (int i = 0; i < 4; i++) {
            int positionOffset = i * 3;

            vertices.add(blockX + faceVertices[positionOffset] * scale);

            vertices.add(blockY + faceVertices[positionOffset + 1] * scale);

            vertices.add(blockZ + faceVertices[positionOffset + 2] * scale);

            vertices.add((float) face.dx());
            vertices.add((float) face.dy());
            vertices.add((float) face.dz());
            float[] color = dev.jayms.net.Blocks.color(type);
            if (type == 1 && face.dy() < 1) color = dev.jayms.net.Blocks.color(2);
            for (float component : color) vertices.add(component);
        }

        for (int index : FACE_INDICES) {
            indices.add(vertexOffset + index);
        }
    }

    private static int addPartial(
            List<Float> vertices,
            List<Integer> indices,
            Chunk chunk,
            int bx,
            int by,
            int bz,
            int count) {
        try {
            var geometry =
                    dev.jayms.net.model.ModelMesher.mesh(
                            16,
                            (x, y, z) ->
                                    dev.jayms.net.WorldVoxels.surfaceColor(
                                            chunk.value(bx * 16 + x, by * 16 + y, bz * 16 + z)),
                            24576);
            float[] v = geometry.vertices();
            for (int i = 0; i < v.length; i += 9) {
                vertices.add(v[i] + bx);
                vertices.add(v[i + 1] + by);
                vertices.add(v[i + 2] + bz);
                for (int j = 3; j < 9; j++) vertices.add(v[i + j]);
            }
            for (int index : geometry.indices()) indices.add(count + index);
            count += v.length / 9;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Fractional surface exceeds mesh budget", e);
        }
        return count;
    }

    private static float[] toFloatArray(List<Float> values) {
        float[] result = new float[values.size()];

        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }

        return result;
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] result = new int[values.size()];

        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }

        return result;
    }
}
