package dev.jayms;

import dev.jayms.net.*;

import java.util.*;

/**
 * Surface-only voxel terrain. Finest tiles include trees and known edits; far tiles use broad
 * cells.
 */
public final class DistantTerrainMesher {
    private record Position(int x, int y, int z) {}

    private record Column(int x, int z) {}

    private final Terrain terrain;
    private final Map<Position, Integer> edits = new HashMap<>();
    private final Map<Column, List<Protocol.Edit>> columns = new HashMap<>();

    public DistantTerrainMesher(Terrain terrain, Collection<Protocol.Edit> snapshot) {
        this.terrain = terrain;
        for (var edit : snapshot) {
            edits.put(new Position(edit.x(), edit.y(), edit.z()), edit.type());
            columns.computeIfAbsent(new Column(edit.x(), edit.z()), k -> new ArrayList<>())
                    .add(edit);
        }
    }

    private int block(int x, int y, int z) {
        Integer edited = edits.get(new Position(x, y, z));
        return edited == null ? terrain.block(x, y, z) : edited;
    }

    private int ground(int x, int z, boolean fine) {
        int h = terrain.column(x, z).height();
        if (fine && columns.containsKey(new Column(x, z))) {
            while (h >= Terrain.MIN_Y && (block(x, h, z) == 0 || Blocks.isModel(block(x, h, z))))
                h--;
        }
        return h + 1;
    }

    public MeshData build(DistantTerrainPlan.Tile tile) {
        Builder mesh = new Builder();
        int step = tile.step(), size = tile.size();
        boolean fine = step == 1;
        int[][] heights = new int[18][18];
        for (int a = -1; a <= 16; a++)
            for (int b = -1; b <= 16; b++)
                heights[a + 1][b + 1] =
                        ground(
                                tile.x() + a * step + step / 2,
                                tile.z() + b * step + step / 2,
                                fine);
        for (int a = 0; a < 16; a++)
            for (int b = 0; b < 16; b++) {
                int x = tile.x() + a * step, z = tile.z() + b * step;
                int h = heights[a + 1][b + 1];
                var c = terrain.column(x + step / 2, z + step / 2);
                int type =
                        c.biome() == Terrain.Biome.DESERT
                                ? Blocks.SAND
                                : c.biome() == Terrain.Biome.SNOWY_MOUNTAINS
                                        ? Blocks.SNOW
                                        : Blocks.GRASS;
                if (fine && h > Terrain.MIN_Y) type = block(x, h - 1, z);
                if (h > Terrain.MIN_Y)
                    mesh.face(Face.TOP, a * step, h, b * step, step, 0, step, type);
                // Border skirts seal joins between different resolutions and unfinished neighbors.
                int west = a == 0 ? Terrain.MIN_Y : heights[a][b + 1];
                int east = a == 15 ? Terrain.MIN_Y : heights[a + 2][b + 1];
                int north = b == 0 ? Terrain.MIN_Y : heights[a + 1][b];
                int south = b == 15 ? Terrain.MIN_Y : heights[a + 1][b + 2];
                int side = type == Blocks.GRASS ? Blocks.DIRT : type;
                if (h > west)
                    mesh.face(Face.LEFT, a * step, west, b * step, 0, h - west, step, side);
                if (h > east)
                    mesh.face(Face.RIGHT, (a + 1) * step, east, b * step, 0, h - east, step, side);
                if (h > north)
                    mesh.face(Face.BACK, a * step, north, b * step, step, h - north, 0, side);
                if (h > south)
                    mesh.face(
                            Face.FRONT, a * step, south, (b + 1) * step, step, h - south, 0, side);
                if (fine) {
                    int base = c.height();
                    if (c.biome() == Terrain.Biome.FOREST
                            || columns.containsKey(new Column(x, z))) {
                        for (int y = Math.max(h, base + 1); y <= base + 9; y++)
                            addVoxel(mesh, tile, x, y, z);
                        for (var edit : columns.getOrDefault(new Column(x, z), List.of()))
                            if (edit.y() > base + 9) addVoxel(mesh, tile, x, edit.y(), z);
                    }
                }
            }
        return mesh.finish();
    }

    private void addVoxel(Builder mesh, DistantTerrainPlan.Tile tile, int x, int y, int z) {
        int type = block(x, y, z);
        if (type == 0 || Blocks.isModel(type)) return;
        for (Face face : Face.values()) {
            int neighbor = block(x + face.dx(), y + face.dy(), z + face.dz());
            if (neighbor == 0 || Blocks.isModel(neighbor))
                mesh.face(face, x - tile.x(), y, z - tile.z(), 1, 1, 1, type);
        }
    }

    private static final class Builder {
        private float[] vertices = new float[16384];
        private int[] indices = new int[4096];
        private int v, i;

        void face(Face face, float x, float y, float z, float w, float h, float d, int type) {
            if (v + 36 > vertices.length) vertices = Arrays.copyOf(vertices, vertices.length * 2);
            if (i + 6 > indices.length) indices = Arrays.copyOf(indices, indices.length * 2);
            int first = v / 9;
            float[] color = Blocks.color(type), corners = face.vertices();
            for (int a = 0; a < 4; a++) {
                vertices[v++] = x + corners[a * 3] * w;
                vertices[v++] = y + corners[a * 3 + 1] * h;
                vertices[v++] = z + corners[a * 3 + 2] * d;
                vertices[v++] = face.dx();
                vertices[v++] = face.dy();
                vertices[v++] = face.dz();
                for (float channel : color) vertices[v++] = channel;
            }
            for (int index : new int[] {0, 1, 2, 2, 3, 0}) indices[i++] = first + index;
        }

        MeshData finish() {
            return new MeshData(Arrays.copyOf(vertices, v), Arrays.copyOf(indices, i));
        }
    }
}
