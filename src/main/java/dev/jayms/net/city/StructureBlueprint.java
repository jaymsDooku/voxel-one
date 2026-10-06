package dev.jayms.net.city;

import dev.jayms.net.*;

import java.util.*;

/** Procedural, walkable voxel structures; real world blocks share engine octree collision. */
public final class StructureBlueprint {
    public static final int WIDTH = 6, DEPTH = 7;

    public static int width(int type) {
        return type == SpecialBuildings.AIRPORT ? Aviation.WIDTH : type == 3 ? 12 : WIDTH;
    }

    public static int depth(int type) {
        return type == SpecialBuildings.PORT ? Shipping.DEPTH : type == SpecialBuildings.AIRPORT ? Aviation.depth(1) : type == 3 ? 14 : DEPTH;
    }

    public static List<Protocol.Edit> generate(int type, int x, int y, int z) {
        if (type == SpecialBuildings.AIRPORT) return Aviation.blueprint(x, y, z, 1);
        if (type == SpecialBuildings.EXCHANGE) {
            var office = new ArrayList<>(generate(1, x, y, z));
            // Two banks of desks leave the central entrance and walking aisle clear.
            for (int dx : new int[] {1, 4})
                for (int dz : new int[] {3, 5}) {
                    office.add(new Protocol.Edit(x + dx, y + 1, z + dz, Blocks.PLANKS));
                    office.add(new Protocol.Edit(x + dx, y + 2, z + dz, Blocks.GLASS));
                }
            office.add(new Protocol.Edit(x + 1, y + 3, z, Blocks.LED).withColor(0x40e0b0));
            office.add(new Protocol.Edit(x + 4, y + 3, z, Blocks.LED).withColor(0x40e0b0));
            return office;
        }
        var edits = new ArrayList<Protocol.Edit>();
        for (int i = 0; i < WIDTH; i++)
            for (int k = 0; k < DEPTH; k++)
                for (int j = 0; j < 7; j++) {
                    int block = 0;
                    if (j == 0) block = type == 2 ? Blocks.STONE : Blocks.PLANKS;
                    else if (j < 4 && (i == 0 || i == WIDTH - 1 || k == 0 || k == DEPTH - 1)) {
                        block = type == 2 ? Blocks.WOOD : type == 1 ? Blocks.BRICKS : Blocks.PLANKS;
                        if ((k == 0 || k == DEPTH - 1) && (i == 2 || i == 3) && j < 3) block = 0;
                        if (!((k == 0 || k == DEPTH - 1) && (i == 2 || i == 3))
                                && j == 2
                                && (i == 0 || i == 5 || k == 6)
                                && (k == 2 || k == 4 || i == 2 || i == 3)) block = Blocks.GLASS;
                    } else if (j == 4) block = type == 2 ? Blocks.WOOD : Blocks.BRICKS;
                    else if (type != 2 && j == 5 && i > 0 && i < 5) block = Blocks.BRICKS;
                    else if (type != 2 && j == 6 && i > 1 && i < 4) block = Blocks.BRICKS;
                    if (type == 2 && i >= 2 && i <= 3 && k >= 3 && k <= 4 && j == 0) block = 0;
                    edits.add(new Protocol.Edit(x + i, y + j, z + k, block));
                }
        // Fine voxel porch beams and shelf details use the world's sparse octrees.
        for (int side : new int[] {0, 5})
            for (int j = 1; j < 4; j++)
                edits.add(
                        Protocol.Edit.at(
                                x + side + .5, y + j, z - .25, Blocks.piece(Blocks.WOOD, 1), 1));
        edits.add(
                new Protocol.Edit(x + 1, y + 3, z + 1, Blocks.LED)
                        .withColor(type == 1 ? 0xffbf68 : 0xffe4a0));
        if (type == 1) {
            for (int k = 3; k < 6; k++)
                edits.add(new Protocol.Edit(x + 1, y + 1, z + k, Blocks.PLANKS));
        }
        if (type == 2) {
            for (int j = -1; j >= -4; j--)
                for (int i = 2; i <= 3; i++)
                    for (int k = 3; k <= 4; k++)
                        edits.add(new Protocol.Edit(x + i, y + j, z + k, 0));
            for (int j = 0; j < 4; j++)
                edits.add(new Protocol.Edit(x + 4, y + j, z + 3, Blocks.STONE));
        }
        return edits;
    }

    /** Walkable civic structures: clear central aisle, desks, and a service-coloured sign. */
    public static List<Protocol.Edit> special(int type, int x, int y, int z) {
        if (type == SpecialBuildings.AIRPORT) return Aviation.blueprint(x, y, z, 1);
        if (type == SpecialBuildings.PORT) return Shipping.port(x, y, z);
        if (type == SpecialBuildings.EXCHANGE) return generate(type, x, y, z);
        if (!SpecialBuildings.special(type)) throw new IllegalArgumentException("Invalid special building");
        var edits = new ArrayList<>(generate(0, x, y, z));
        int kind = SpecialBuildings.kind(type);
        int[] colors = {0xffd166, 0x80ed99, 0x57ccff, 0xc792ea, 0x5588ff, 0xffaa55};
        for (int level = 0; level < SpecialBuildings.level(type); level++)
            edits.add(new Protocol.Edit(x + 1 + level, y + 4, z + 3, Blocks.LED).withColor(colors[kind]));
        for (int row = 0; row < (kind == 0 || kind == 4 ? 1 : 3); row++) {
            edits.add(new Protocol.Edit(x + 1, y + 1, z + 2 + row, kind == 4 ? Blocks.STONE : Blocks.PLANKS));
            edits.add(new Protocol.Edit(x + 4, y + 1, z + 2 + row, Blocks.PLANKS));
        }
        return edits;
    }

    /** Resource companies get workshops; only the quarry needs a mine shaft. */
    public static List<Protocol.Edit> generate(int type, int businessKind, int x, int y, int z) {
        if (type == 0 && businessKind == 50) {
            var tower = new ArrayList<>(generate(0, x, y, z));
            // Two real floors on the same footprint; extra blocks enter the project reservation.
            for (int dx = 0; dx < WIDTH; dx++) for (int dz = 0; dz < DEPTH; dz++)
                for (int dy = 4; dy <= 9; dy++) {
                    int block = dy == 4 || dy == 9 ? Blocks.BRICKS
                            : dx == 0 || dx == WIDTH - 1 || dz == 0 || dz == DEPTH - 1 ? Blocks.BRICKS : Blocks.AIR;
                    if (dy == 6 && (dx == 0 || dx == WIDTH - 1) && (dz == 2 || dz == 4)) block = Blocks.GLASS;
                    tower.add(new Protocol.Edit(x + dx, y + dy, z + dz, block));
                }
            // Open stairwell and ascending steps connect both floors.
            for (int step = 1; step <= 3; step++) {
                tower.add(new Protocol.Edit(x + 4, y + 4, z + step + 2, Blocks.AIR));
                tower.add(new Protocol.Edit(x + 4, y + step, z + step + 2, Blocks.PLANKS));
            }
            tower.add(new Protocol.Edit(x + 3, y + 4, z + 5, Blocks.PLANKS));
            return tower;
        }
        if (type == 3) {
            var farm = new ArrayList<>(generate(0, x, y, z));
            // Paid soil beds or a wooden pen occupy the rest of the owned plot.
            for (int dx = 6; dx < 12; dx++)
                for (int dz = 0; dz < 14; dz++) {
                    farm.add(new Protocol.Edit(x + dx, y, z + dz, Blocks.DIRT));
                    if (businessKind == CityMaterials.CATTLE_FARM
                            && (dx == 6 || dx == 11 || dz == 0 || dz == 13))
                        farm.add(
                                Protocol.Edit.at(
                                        x + dx + .25,
                                        y + 1,
                                        z + dz + .25,
                                        Blocks.piece(Blocks.WOOD, 1),
                                        1));
                }
            return farm;
        }
        if (type != 2 || businessKind == CityEconomy.MINE) return generate(type, x, y, z);
        var edits = new ArrayList<>(generate(0, x, y, z));
        int bench =
                businessKind == CityMaterials.LOGGING
                        ? Blocks.WOOD
                        : businessKind == CityMaterials.MASONRY
                                ? Blocks.BRICKS
                                : businessKind == CityMaterials.FARM ? Blocks.PLANKS : Blocks.STONE;
        edits.add(new Protocol.Edit(x + 4, y + 1, z + 4, bench));
        if (businessKind == CityMaterials.GLASSWORKS)
            edits.add(new Protocol.Edit(x + 4, y + 2, z + 4, Blocks.GLASS));
        if (businessKind == CityMaterials.LIGHTING)
            for (int i = 0; i < 3; i++)
                edits.add(
                        new Protocol.Edit(x + 3 + i, y + 2, z + 5, Blocks.LED)
                                .withColor(new int[] {0xff5544, 0x55ff88, 0x5588ff}[i]));
        if (businessKind == CityMaterials.TOOLS) {
            // Workbenches and tiny voxel tool silhouettes; the central aisle stays walkable.
            edits.add(new Protocol.Edit(x + 1, y + 1, z + 4, Blocks.STONE));
            edits.add(new Protocol.Edit(x + 4, y + 1, z + 5, Blocks.WOOD));
            for (int j = 0; j < 4; j++)
                edits.add(
                        Protocol.Edit.at(
                                x + 1.5,
                                y + 2 + j * .25,
                                z + 4.5,
                                Blocks.piece(Blocks.WOOD, 2),
                                2));
            for (int i = -1; i <= 1; i++)
                edits.add(
                        Protocol.Edit.at(
                                x + 1.5 + i * .25,
                                y + 2.75,
                                z + 4.5,
                                Blocks.piece(Blocks.STONE, 2),
                                2));
        }
        return edits;
    }

    private StructureBlueprint() {}
}
