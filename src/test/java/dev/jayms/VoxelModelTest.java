package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.player.Player;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Path;
import java.util.Random;

class VoxelModelTest {
    @TempDir Path temp;

    @Test
    void octreeMergesUniformRegionsAndSnapshotsRemainIndependent() throws Exception {
        SparseVoxelOctree tree = new SparseVoxelOctree(32);
        tree.fill(0, 0, 0, 32, 32, 32, 0xff554433);
        assertEquals(1, tree.nodes());
        assertEquals(32768, tree.occupied());
        var snapshot = tree.copy();
        tree.set(15, 17, 19, 0);
        assertEquals(32767, tree.occupied());
        assertEquals(0xff554433, snapshot.get(15, 17, 19));
        assertEquals(0, tree.get(15, 17, 19));
        tree.set(15, 17, 19, 0xff554433);
        assertEquals(1, tree.nodes());
        tree.freeze();
        assertThrows(IllegalStateException.class, () -> tree.set(0, 0, 0, 0));
        SparseVoxelOctree copy = tree.copy();
        copy.set(0, 0, 0, 0);
        assertEquals(0xff554433, tree.get(0, 0, 0));
        var bytes = new ByteArrayOutputStream();
        tree.write(new DataOutputStream(bytes));
        assertEquals(6, bytes.size());
        var loaded =
                SparseVoxelOctree.read(
                        new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(1, loaded.nodes());
        assertEquals(32768, loaded.occupied());
    }

    @Test
    void randomEditsSurviveOctreeSerializationAndInvalidDataIsRejected() throws Exception {
        SparseVoxelOctree tree = new SparseVoxelOctree(16);
        int[] dense = new int[4096];
        Random r = new Random(42);
        for (int i = 0; i < 1500; i++) {
            int x = r.nextInt(16),
                    y = r.nextInt(16),
                    z = r.nextInt(16),
                    c = r.nextBoolean() ? 0 : 0xff336699;
            tree.set(x, y, z, c);
            dense[x + 16 * y + 256 * z] = c;
        }
        var bytes = new ByteArrayOutputStream();
        tree.write(new DataOutputStream(bytes));
        var loaded =
                SparseVoxelOctree.read(
                        new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        for (int x = 0; x < 16; x++)
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    assertEquals(dense[x + 16 * y + 256 * z], loaded.get(x, y, z));
        assertThrows(
                IOException.class,
                () ->
                        SparseVoxelOctree.read(
                                new DataInputStream(new ByteArrayInputStream(new byte[] {3, 2}))));
        byte[] malicious = new byte[10];
        malicious[0] = 8;
        java.util.Arrays.fill(malicious, 1, 10, (byte) 2);
        assertThrows(
                IOException.class,
                () ->
                        SparseVoxelOctree.read(
                                new DataInputStream(new ByteArrayInputStream(malicious))));
        assertThrows(IllegalArgumentException.class, () -> new ModelDefinition("../escape", tree));
    }

    @Test
    void flowerPotHasHolesPrecisePickingAndCompactSurface() throws Exception {
        var model = ModelGenerators.flowerPot();
        var tree = model.voxels();
        assertTrue(tree.occupied() > 1000);
        assertFalse(tree.intersects(.9f, .2f, .4f, .99f, .3f, .6f));
        assertFalse(tree.intersects(.60f, .34f, .49f, .62f, .36f, .51f));
        assertTrue(tree.intersects(.76f, .34f, .49f, .80f, .36f, .51f));
        assertNull(tree.raycast(.95f, .35f, 2, 0, 0, -1, 0, 3));
        var hit = tree.raycast(.5f, .35f, 2, 0, 0, -1, 0, 3);
        assertNotNull(hit);
        assertEquals(1, hit.nz());
        assertTrue(hit.distance() > 1 && hit.distance() < 2);
        var geometry = ModelMesher.mesh(tree);
        assertTrue(geometry.quads() < tree.occupied());
        var bytes = new ByteArrayOutputStream();
        model.write(new DataOutputStream(bytes));
        assertTrue(bytes.size() < 32768 * 4 / 5);
        assertEquals(
                model.fingerprint(),
                ModelDefinition.read(
                                new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())))
                        .fingerprint());
    }

    @Test
    void greedyMeshCullsInteriorsPreservesColorsAndHasOutwardWinding() throws Exception {
        SparseVoxelOctree tree = new SparseVoxelOctree(8);
        tree.fill(0, 0, 0, 8, 8, 8, 0xffd08040);
        var geometry = ModelMesher.mesh(tree);
        assertEquals(6, geometry.quads());
        for (int face = 0; face < 6; face++) {
            int o = face * 36;
            Vector3f
                    a =
                            new Vector3f(
                                    geometry.vertices()[o],
                                    geometry.vertices()[o + 1],
                                    geometry.vertices()[o + 2]),
                    b =
                            new Vector3f(
                                    geometry.vertices()[o + 9],
                                    geometry.vertices()[o + 10],
                                    geometry.vertices()[o + 11]),
                    c =
                            new Vector3f(
                                    geometry.vertices()[o + 18],
                                    geometry.vertices()[o + 19],
                                    geometry.vertices()[o + 20]);
            Vector3f normal =
                    new Vector3f(
                            geometry.vertices()[o + 3],
                            geometry.vertices()[o + 4],
                            geometry.vertices()[o + 5]);
            assertTrue(b.sub(a).cross(c.sub(a)).dot(normal) > 0);
            assertEquals(208 / 255f, geometry.vertices()[o + 6], 1e-6);
        }
        tree = new SparseVoxelOctree(8);
        tree.set(1, 1, 1, 0xffff0000);
        tree.set(2, 1, 1, 0xff00ff00);
        assertEquals(10, ModelMesher.mesh(tree).quads());
    }

    @Test
    void modelsUseActualVoxelsForCollisionAndRayHits() throws Exception {
        World world = new World();
        Chunk chunk = new Chunk();
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) chunk.setBlock(x, 0, z, Blocks.STONE);
        world.addChunk(new ChunkPos(0, 0, 0), chunk);
        world.setBlock(8, 1, 8, Blocks.FLOWER_POT);
        Player beside = new Player(new Vector3f(9.15f, 1.001f, 8.5f), 0, 0, new Camera());
        assertFalse(beside.collides(world));
        Player inside = new Player(new Vector3f(8.5f, 1.001f, 8.5f), 0, 0, new Camera());
        assertTrue(inside.collides(world));
        assertNull(
                BlockRaycaster.cast(
                        world, new Vector3f(8.95f, 1.35f, 12), new Vector3f(0, 0, -1), 6));
        assertNotNull(
                BlockRaycaster.cast(
                        world, new Vector3f(8.5f, 1.35f, 12), new Vector3f(0, 0, -1), 6));
        assertEquals(1, chunk.models().size());
        world.setBlock(8, 1, 8, 0);
        assertTrue(chunk.models().isEmpty());
    }

    @Test
    void customModelsAreImmutableDeduplicatedAndSavedOffline() throws Exception {
        ModelLibrary library = new ModelLibrary();
        var definition = ModelGenerators.flowerPot();
        assertEquals(8, library.register(definition, "alice").id());
        definition.voxels().set(0, 0, 0, 0xffabcdef);
        var custom = library.register(definition, "alice");
        assertEquals(9, custom.id());
        assertEquals(9, library.register(definition, "bob").id());
        definition.voxels().set(0, 0, 0, 0);
        assertEquals(0xffabcdef, custom.definition().voxels().get(0, 0, 0));
        LocalGame game = new LocalGame(temp.resolve("world.dat"), 42);
        game.createModel(custom.definition());
        int type = game.inventory.type(0);
        assertEquals(9, type);
        assertTrue(game.edit(new Protocol.Edit(1, 40, 1, type), 0, 0));
        game.save();
        var loaded = new LocalGame(temp.resolve("world.dat"), 999);
        assertEquals(
                custom.definition().fingerprint(),
                loaded.models.get(type).definition().fingerprint());
        assertEquals(type, loaded.edits.get("1,40,1").type());
    }
}
