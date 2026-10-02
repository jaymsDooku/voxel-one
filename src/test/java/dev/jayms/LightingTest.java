package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.render.LightVolume;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;

class LightingTest {
    @TempDir Path temp;

    private static int stone() {
        return WorldVoxels.encode(Blocks.STONE);
    }

    @Test
    void roofBlocksSkyAndColoredLightFallsOffBehindWalls() {
        var dark =
                LightVolume.bake(
                        0,
                        0,
                        0,
                        9,
                        9,
                        9,
                        (x, y, z) ->
                                x == 0 || z == 0 || x == 8 || z == 8 || y == 0 || y == 8
                                        ? stone()
                                        : 0);
        assertTrue(dark.sample(4, 4, 4)[2] < .04f);
        var red =
                LightVolume.bake(
                        0,
                        0,
                        0,
                        9,
                        9,
                        9,
                        (x, y, z) ->
                                x == 0 || z == 0 || x == 8 || z == 8 || y == 0 || y == 8 || x == 5
                                        ? stone()
                                        : x == 2 && y == 4 && z == 4 ? 0xfeff0000 : 0);
        assertTrue(red.sample(3, 4, 4)[0] > 1);
        assertTrue(red.sample(3, 4, 4)[2] < .04f);
        assertTrue(red.sample(4, 4, 4)[0] < red.sample(3, 4, 4)[0]);
        assertTrue(red.sample(6, 4, 4)[0] < .04f);
    }

    @Test
    void lightsMixAndSunlightBouncesSurfaceColour() {
        var volume =
                LightVolume.bake(
                        0,
                        0,
                        0,
                        9,
                        9,
                        9,
                        (x, y, z) ->
                                y == 8 || y == 0 || x == 0 || x == 8 || z == 0 || z == 8
                                        ? stone()
                                        : y == 4 && z == 4 && x == 2
                                                ? 0xfeff0000
                                                : y == 4 && z == 4 && x == 6 ? 0xfe0000ff : 0);
        var c = volume.sample(4, 4, 4);
        assertTrue(c[0] > .7f && c[2] > .7f);
        assertTrue(c[1] < .04f);
        var bounce =
                LightVolume.bake(
                        0,
                        0,
                        0,
                        9,
                        9,
                        9,
                        (x, y, z) -> y == 1 ? WorldVoxels.encode(Blocks.BRICKS) : 0);
        var bounced = bounce.sample(4, 2, 4);
        assertTrue(bounced[0] > .24f); // outdoor sky plus a diffuse first bounce
        assertTrue(bounced[0] - .24f > bounced[2] - .45f);
    }

    @Test
    void coloredWholeAndFineLightsHaveEmissionAndStableSurfaceColours() throws Exception {
        for (int depth : new int[] {0, 1, 4}) {
            var edit =
                    new Protocol.Edit(0, 0, 0, Blocks.piece(Blocks.LED, depth), depth, 0, 0, 0)
                            .withColor(0x12abcd);
            Chunk chunk = new Chunk();
            chunk.apply(edit);
            var mesh = MeshDataGenerator.generate(chunk);
            assertTrue(mesh.vertices().length > 0);
            for (int i = 0; i < mesh.vertices().length; i += 9) {
                assertEquals(0x12 / 255f, mesh.vertices()[i + 6], .001);
                assertEquals(0xab / 255f, mesh.vertices()[i + 7], .001);
                assertEquals(0xcd / 255f, mesh.vertices()[i + 8], .001);
                assertEquals(4, mesh.surface()[i / 3]);
            }
            var tree = chunk.cell(0, 0, 0);
            var bytes = new ByteArrayOutputStream();
            tree.write(new DataOutputStream(bytes));
            var copy =
                    SparseVoxelOctree.read(
                            new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
            assertEquals(0x12abcd, WorldVoxels.lightColor(copy.get(0, 0, 0)));
        }
    }

    @Test
    void wireColorValidationAndCellRollbackRetainExactRgb() throws Exception {
        var edit =
                new Protocol.Edit(-1, 40, 2, Blocks.piece(Blocks.LED, 2), 2, 1, 2, 3)
                        .withColor(0x0000ff);
        var out = new ByteArrayOutputStream();
        edit.write(new DataOutputStream(out));
        assertEquals(
                edit,
                Protocol.Edit.read(
                        new DataInputStream(new ByteArrayInputStream(out.toByteArray()))));
        World world = new World();
        world.apply(edit);
        assertEquals(
                edit,
                new Protocol.CellState(
                                edit.x(),
                                edit.y(),
                                edit.z(),
                                world.cell(edit.x(), edit.y(), edit.z()))
                        .edits().stream()
                                .filter(e -> Blocks.material(e.type()) == Blocks.LED)
                                .findFirst()
                                .orElseThrow());
        assertFalse(edit.withColor(-1).valid());
        assertFalse(edit.withColor(0x1000000).valid());
        assertFalse(new Protocol.Edit(0, 1, 2, Blocks.STONE).withColor(1).valid());
    }

    @Test
    void localSaveRetainsRgbAndMigratesV5() throws Exception {
        var save = temp.resolve("new.dat");
        var game = new LocalGame(save, Terrain.DEFAULT_SEED);
        var edit = new Protocol.Edit(8, 70, 24, Blocks.LED).withColor(0x00f123);
        game.inventory.add(Blocks.LED, 1);
        assertTrue(game.edit(edit, 0, 0));
        game.save();
        var reload = new LocalGame(save, 0);
        assertEquals(edit, reload.edits.get(edit.key()));
        var old = temp.resolve("old.dat");
        try (var out = new DataOutputStream(Files.newOutputStream(old))) {
            out.writeInt(5);
            out.writeLong(Terrain.DEFAULT_SEED);
            new ModelLibrary().write(out);
            new Inventory().write(out);
            out.writeByte(20);
            out.writeInt(1);
            out.writeInt(8);
            out.writeInt(70);
            out.writeInt(24);
            out.writeInt(Blocks.piece(Blocks.STONE, 1));
            out.writeByte(1);
            out.writeByte(0);
            out.writeByte(0);
            out.writeByte(0);
            out.writeInt(0);
        }
        var migrated = new LocalGame(old, 0);
        assertEquals(1, migrated.edits.size());
        migrated.save();
        assertEquals(migrated.edits, new LocalGame(old, 0).edits);
    }
}
