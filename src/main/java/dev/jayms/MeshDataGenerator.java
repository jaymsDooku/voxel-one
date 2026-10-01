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

                    for (Face face : Face.values()) {
                        int nX = x + face.dx();
                        int nY = y + face.dy();
                        int nZ = z + face.dz();

                        if (chunk.neighbor(nX, nY, nZ) != 0
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

        return new MeshData(toFloatArray(vertices), toIntArray(indices));
    }

    private static void addFace(
            List<Float> vertices,
            List<Integer> indices,
            Face face,
            int blockX,
            int blockY,
            int blockZ,
            int vertexOffset,
            int type) {
        float[] faceVertices = face.vertices();

        for (int i = 0; i < 4; i++) {
            int positionOffset = i * 3;

            vertices.add(blockX + faceVertices[positionOffset]);

            vertices.add(blockY + faceVertices[positionOffset + 1]);

            vertices.add(blockZ + faceVertices[positionOffset + 2]);

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
