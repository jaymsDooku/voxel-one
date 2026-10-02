package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.render.*;

import org.junit.jupiter.api.Test;

class LightOcclusionTest {
    private static final int STONE = WorldVoxels.encode(Blocks.STONE);
    private static final int RED = 0xfeff0000;

    private LightVolume chamber(SparseVoxelOctree wall, int axis, int origin) {
        return LightVolume.bake(
                origin,
                origin,
                origin,
                9,
                9,
                9,
                new LightVolume.Sampler() {
                    private int a(int x, int y, int z) {
                        return (axis == 0 ? x : axis == 1 ? y : z) - origin;
                    }

                    public int value(int x, int y, int z) {
                        x -= origin;
                        y -= origin;
                        z -= origin;
                        if (x == 0 || y == 0 || z == 0 || x == 8 || y == 8 || z == 8) return STONE;
                        int a = axis == 0 ? x : axis == 1 ? y : z;
                        if (a == 4) return wall.uniform(0, 0, 0, wall.size());
                        if (a == 2
                                && (axis == 0
                                        ? y == 4 && z == 4
                                        : axis == 1 ? x == 4 && z == 4 : x == 4 && y == 4))
                            return RED;
                        return 0;
                    }

                    public SparseVoxelOctree detail(int x, int y, int z) {
                        return wall;
                    }
                });
    }

    private static SparseVoxelOctree slab(int resolution, int axis, int thickness, int value) {
        var tree = new SparseVoxelOctree(resolution);
        int lo = resolution / 2, hi = lo + thickness;
        tree.fill(
                axis == 0 ? lo : 0,
                axis == 1 ? lo : 0,
                axis == 2 ? lo : 0,
                axis == 0 ? hi : resolution,
                axis == 1 ? hi : resolution,
                axis == 2 ? hi : resolution,
                value);
        return tree;
    }

    private static float red(LightVolume v, int axis, int origin, float along) {
        return v.sample(
                        origin + (axis == 0 ? along : 4.5f),
                        origin + (axis == 1 ? along : 4.5f),
                        origin + (axis == 2 ? along : 4.5f))[0];
    }

    @Test
    void solidWallsAtEveryFractionAndAxisHaveTwoIndependentLightSides() {
        for (int axis = 0; axis < 3; axis++)
            for (int thickness : new int[] {1, 2, 4, 8})
                for (int origin : new int[] {0, -17, 14}) {
                    var v = chamber(slab(16, axis, thickness, STONE), axis, origin);
                    assertTrue(red(v, axis, origin, 4.25f) > .8f, "Front air remains lit");
                    assertTrue(red(v, axis, origin, 4.501f) < .04f, "Opaque leaf stays dark");
                    assertTrue(
                            red(v, axis, origin, 5.001f) < .04f,
                            "No light behind even a 1/16 wall");
                    assertTrue(
                            red(v, axis, origin, 6.5f) < .04f,
                            "No propagation across negative/chunk coordinates");
                }
    }

    @Test
    void glassAttenuatesAndAirTransmitsWhileOpaqueBuildingMaterialsBlock() {
        for (int material :
                new int[] {
                    Blocks.STONE,
                    Blocks.WOOD,
                    Blocks.LEAVES,
                    Blocks.PLANKS,
                    Blocks.BRICKS,
                    Blocks.SAND
                }) {
            var wall = new SparseVoxelOctree(16);
            wall.fill(0, 0, 0, 16, 16, 16, WorldVoxels.encode(material));
            assertTrue(red(chamber(wall, 0, 0), 0, 0, 6.5f) < .04f, Blocks.name(material));
        }
        var air = chamber(new SparseVoxelOctree(16), 0, 0);
        var glass = new SparseVoxelOctree(16);
        glass.fill(0, 0, 0, 16, 16, 16, WorldVoxels.encode(Blocks.GLASS));
        var transmitting = chamber(glass, 0, 0);
        assertTrue(red(transmitting, 0, 0, 6.5f) > .5f);
        assertTrue(red(transmitting, 0, 0, 6.5f) < red(air, 0, 0, 6.5f));
        assertTrue(
                red(chamber(slab(16, 0, 1, WorldVoxels.encode(Blocks.GLASS)), 0, 0), 0, 0, 6.5f)
                        > .5f);
    }

    @Test
    void openingInAFineWallLetsLightThroughAndClosingItRemovesLight() {
        var wall = slab(16, 0, 1, STONE);
        assertTrue(red(chamber(wall, 0, 0), 0, 0, 6.5f) < .04f);
        wall.fill(8, 6, 6, 9, 10, 10, 0);
        assertTrue(red(chamber(wall, 0, 0), 0, 0, 6.5f) > .5f);
        wall.fill(8, 6, 6, 9, 10, 10, STONE);
        assertTrue(red(chamber(wall, 0, 0), 0, 0, 6.5f) < .04f);
    }

    @Test
    void fineLightBuriedInSolidMaterialCannotEscapeItsCell() {
        var buried = new SparseVoxelOctree(16);
        buried.fill(0, 0, 0, 16, 16, 16, STONE);
        buried.fill(7, 7, 7, 9, 9, 9, RED);
        var v =
                LightVolume.bake(
                        0,
                        0,
                        0,
                        5,
                        5,
                        5,
                        0,
                        new LightVolume.Sampler() {
                            public int value(int x, int y, int z) {
                                return x == 2 && y == 2 && z == 2 ? -1 : 0;
                            }

                            public SparseVoxelOctree detail(int x, int y, int z) {
                                return buried;
                            }
                        });
        assertTrue(v.sample(2.5f, 2.5f, 2.5f)[0] > 1.5f);
        for (int[] p :
                new int[][] {{1, 2, 2}, {3, 2, 2}, {2, 1, 2}, {2, 3, 2}, {2, 2, 1}, {2, 2, 3}})
            assertTrue(v.sample(p[0], p[1], p[2])[0] < .04f);
    }

    @Test
    void opaqueModelRgbIsNotConfusedWithGlassAnd32VoxelModelsBlockLight() {
        var model =
                slab(
                        32,
                        0,
                        1,
                        0xff0000a7); // Low colour byte deliberately matches glass's block ID.
        var entry = new ModelLibrary.Entry(9, "test", new ModelDefinition("Thin wall", model));
        var geometry = WorldLighting.modelGeometry(entry);
        assertEquals(STONE, geometry.get(16, 0, 0));
        var volume = chamber(geometry, 0, 0);
        assertTrue(red(volume, 0, 0, 4.25f) > .8f);
        assertTrue(red(volume, 0, 0, 4.54f) < .04f);
        assertTrue(red(volume, 0, 0, 6.5f) < .04f);
    }

    @Test
    void fractionalOpaqueRoofBlocksSkyButGlassRoofTransmitsIt() {
        for (int material : new int[] {Blocks.STONE, Blocks.GLASS}) {
            var roof = slab(16, 1, 1, WorldVoxels.encode(material));
            var v =
                    LightVolume.bake(
                            0,
                            0,
                            0,
                            5,
                            7,
                            5,
                            new LightVolume.Sampler() {
                                public int value(int x, int y, int z) {
                                    return x == 0 || x == 4 || z == 0 || z == 4 || y == 0
                                            ? STONE
                                            : y == 4 ? -1 : 0;
                                }

                                public SparseVoxelOctree detail(int x, int y, int z) {
                                    return roof;
                                }
                            });
            assertTrue(v.sample(2.5f, 4.8f, 2.5f)[2] > .4f);
            if (material == Blocks.STONE) assertTrue(v.sample(2.5f, 4.25f, 2.5f)[2] < .04f);
            else assertTrue(v.sample(2.5f, 4.25f, 2.5f)[2] > .3f);
        }
    }
}
