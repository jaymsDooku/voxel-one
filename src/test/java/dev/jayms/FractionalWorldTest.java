package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.player.Player;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;

class FractionalWorldTest {
    @Test
    void worldScaleOctreesCompressUniformRegionsAndShareImmutableSnapshots() throws Exception {
        var tree = new SparseVoxelOctree(256);
        tree.fill(0, 0, 0, 256, 256, 256, WorldVoxels.encode(Blocks.STONE));
        assertEquals(1, tree.nodes());
        var snapshot = tree.copy().freeze();
        tree.fill(0, 0, 0, 128, 128, 128, 0);
        assertEquals(0, tree.uniform(0, 0, 0, 128));
        assertEquals(WorldVoxels.encode(Blocks.STONE), snapshot.get(1, 1, 1));
        assertTrue(tree.nodes() < 16);
        var bytes = new ByteArrayOutputStream();
        tree.write(new DataOutputStream(bytes));
        var loaded =
                SparseVoxelOctree.read(
                        new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(256, loaded.size());
        assertEquals(tree.nodes(), loaded.nodes());
        assertEquals(tree.get(255, 255, 255), loaded.get(255, 255, 255));
        assertThrows(IllegalArgumentException.class, () -> new ModelDefinition("Oversized", tree));
    }

    @Test
    void pickingCollisionAndGreedyGeometryRespectHalfQuarterAndSixteenthCubes() {
        World world = new World();
        Chunk chunk = new Chunk();
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) chunk.setBlock(x, 0, z, Blocks.STONE);
        world.addChunk(new ChunkPos(0, 0, 0), chunk);
        for (int depth : new int[] {1, 2, 4}) {
            world.setBlock(8, 1, 8, 0);
            var edit =
                    new Protocol.Edit(8, 1, 8, Blocks.piece(Blocks.STONE, depth), depth, 0, 0, 0);
            world.apply(edit);
            assertEquals(Blocks.PARTIAL, world.getBlock(8, 1, 8));
            var hit =
                    BlockRaycaster.cast(
                            world, new Vector3f(8.01f, 4, 8.01f), new Vector3f(0, -1, 0), 6);
            assertEquals(depth, hit.depth());
            assertEquals(1, hit.y());
            assertEquals(3 - edit.size(), hit.distance(), .0001);
            var miss =
                    BlockRaycaster.cast(
                            world, new Vector3f(8.9f, 4, 8.9f), new Vector3f(0, -1, 0), 6);
            assertEquals(0, miss.y());
            Player player =
                    new Player(
                            new Vector3f(8.01f, 1 + edit.size() + .01f, 8.01f), 0, 0, new Camera());
            for (int i = 0; i < 120; i++) player.step(world, 1f / 60, 0, 0, false, false);
            assertEquals(1 + edit.size(), player.position().y, .002);
        }
        var isolated = new Chunk();
        isolated.apply(new Protocol.Edit(2, 2, 2, Blocks.piece(Blocks.STONE, 1), 1, 0, 0, 0));
        var mesh = MeshDataGenerator.generate(isolated);
        assertEquals(36, mesh.indices().length, "Uniform half cube should merge into six faces");
        for (int i = 0; i < mesh.vertices().length; i += 9) {
            assertTrue(mesh.vertices()[i] >= 2 && mesh.vertices()[i] <= 2.5);
            assertTrue(mesh.vertices()[i + 1] >= 2 && mesh.vertices()[i + 1] <= 2.5);
        }
    }

    @Test
    void orderedOverridesReplayAndFullReplacementRemovesOldSubdivisions() {
        var history = new LinkedHashMap<String, Protocol.Edit>();
        var terrain = new Terrain(42);
        var voxels = new WorldVoxels(terrain);
        var base = new Protocol.Edit(-1, 70, -1, Blocks.STONE);
        var carve = new Protocol.Edit(-1, 70, -1, 0, 1, 0, 0, 0);
        var wood = new Protocol.Edit(-1, 70, -1, Blocks.piece(Blocks.WOOD, 2), 2, 0, 0, 0);
        for (var edit : List.of(base, carve, wood)) {
            voxels.apply(edit);
            WorldVoxels.remember(history, edit);
        }
        assertEquals(Blocks.PARTIAL, voxels.region(base));
        assertEquals(wood.type(), voxels.region(wood));
        var replay = new WorldVoxels(terrain);
        history.values().forEach(replay::apply);
        assertEquals(voxels.cell(-1, 70, -1).leaves(), replay.cell(-1, 70, -1).leaves());
        var correction = new Protocol.CellState(-1, 70, -1, voxels.cell(-1, 70, -1));
        var corrected = new WorldVoxels(terrain);
        correction.edits().forEach(corrected::apply);
        assertEquals(voxels.cell(-1, 70, -1).leaves(), corrected.cell(-1, 70, -1).leaves());
        WorldVoxels.remember(history, base);
        assertEquals(1, history.size());
    }

    @Test
    void fractionalWireAndCellSnapshotsRoundTripAndRejectMismatchedItemDepth() throws Exception {
        var edit = new Protocol.Edit(-1, 40, -2, Blocks.piece(Blocks.BRICKS, 4), 4, 15, 8, 0);
        assertTrue(edit.valid());
        assertFalse(new Protocol.Edit(-1, 40, -2, Blocks.STONE, 1, 0, 0, 0).valid());
        var bytes = new ByteArrayOutputStream();
        edit.write(new DataOutputStream(bytes));
        assertEquals(
                edit,
                Protocol.Edit.read(
                        new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        var voxels = new WorldVoxels(new Terrain(42));
        voxels.apply(new Protocol.Edit(-1, 40, -2, 0));
        voxels.apply(edit);
        bytes.reset();
        new Protocol.CellState(-1, 40, -2, voxels.cell(-1, 40, -2))
                .write(new DataOutputStream(bytes));
        var state =
                Protocol.CellState.read(
                        new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(voxels.cell(-1, 40, -2).leaves(), state.tree().leaves());
    }

    @Test
    void fractionalSurfaceOffsetsRemainPreciseNearTheWorldBoundary() {
        for (int x : new int[] {999999, -999999}) {
            var outside =
                    Protocol.Edit.at(x + .5 + .0001, 40, -2, Blocks.piece(Blocks.STONE, 4), 4);
            var inside = Protocol.Edit.at(x + .5 - .0001, 40, -2, 0, 4);
            assertTrue(outside.valid());
            assertTrue(inside.valid());
            assertEquals(x, inside.x());
            assertEquals(7, inside.ix());
            assertEquals(8, outside.ix());
        }
    }

    @Test
    void clippedOctreeRayRetainsTheEntryFaceNormalForBreaking() {
        var tree = new SparseVoxelOctree(16);
        tree.fill(8, 0, 8, 16, 8, 16, WorldVoxels.encode(Blocks.STONE));
        var hit = tree.raycast(.5f, .25f, 2.5f, 0, 0, -1, 1.500001f, 3);
        assertNotNull(hit);
        assertEquals(1, hit.nz());
        assertEquals(.5f, hit.side());
    }

    @Test
    void worldMeshingHandlesAFullCellOfSeparateSixteenthCubes() throws Exception {
        var mesh =
                ModelMesher.mesh(
                        16,
                        (x, y, z) ->
                                x < 0
                                                || y < 0
                                                || z < 0
                                                || x >= 16
                                                || y >= 16
                                                || z >= 16
                                                || (x + y + z) % 2 != 0
                                        ? 0
                                        : 0xff888888,
                        24576);
        assertEquals(12288, mesh.quads());
    }
}
