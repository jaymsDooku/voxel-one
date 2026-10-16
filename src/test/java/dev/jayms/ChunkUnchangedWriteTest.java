package dev.jayms;

import dev.jayms.net.Blocks;
import dev.jayms.net.Protocol;
import dev.jayms.net.WorldVoxels;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChunkUnchangedWriteTest {
    @Test
    void repeatedUniformWritesPreserveAllState() {
        for (int type : new int[] {0, Blocks.STONE, Blocks.FLOWER_POT}) {
            for (boolean clean : new boolean[] {false, true}) {
                var chunk = new Chunk();
                chunk.setBlock(2, 3, 4, type);
                chunk.setBlock(5, 6, 7, Blocks.FLOWER_POT);
                if (clean) assertTrue(chunk.installMesh(null, null, chunk.meshRevision()));
                long geometry = chunk.geometryVersion(), mesh = chunk.meshRevision();
                boolean dirty = chunk.dirty();
                var models = java.util.Map.copyOf(chunk.models());
                var cell = chunk.cell(2, 3, 4).leaves();
                var snapshot = chunk.snapshot().leaves();
                for (int i = 0; i < 3; i++) chunk.setBlock(2, 3, 4, type);
                assertEquals(geometry, chunk.geometryVersion());
                assertEquals(mesh, chunk.meshRevision());
                assertEquals(dirty, chunk.dirty());
                assertEquals(models, chunk.models());
                assertEquals(cell, chunk.cell(2, 3, 4).leaves());
                assertEquals(snapshot, chunk.snapshot().leaves());
            }
        }
    }

    @Test
    void changedValuesMaintainModelsAndInvalidate() {
        var chunk = new Chunk();
        for (int type : new int[] {Blocks.STONE, Blocks.FLOWER_POT, Blocks.FLOWER_POT + 1, 0}) {
            chunk.installMesh(null, null, chunk.meshRevision());
            long geometry = chunk.geometryVersion(), mesh = chunk.meshRevision();
            chunk.setBlock(2, 3, 4, type);
            assertEquals(geometry + 1, chunk.geometryVersion());
            assertEquals(mesh + 1, chunk.meshRevision());
            assertTrue(chunk.dirty());
            assertEquals(WorldVoxels.encode(type), chunk.cell(2, 3, 4).uniform(0, 0, 0, 16));
            assertEquals(Blocks.isModel(type) ? java.util.Map.of(chunk.index(2, 3, 4), type)
                    : java.util.Map.of(), chunk.models());
        }
    }

    @Test
    void mixedCellsAlwaysTakeReplacementPathIncludingSentinelCollision() {
        for (int type : new int[] {0, Blocks.STONE, Blocks.FLOWER_POT, -1}) {
            var chunk = new Chunk();
            chunk.setBlock(2, 3, 4, Blocks.FLOWER_POT);
            chunk.apply(new Protocol.Edit(2, 3, 4, 0, 1, 0, 0, 0));
            assertEquals(-1, chunk.cell(2, 3, 4).uniform(0, 0, 0, 16));
            var before = chunk.cell(2, 3, 4).leaves();
            chunk.installMesh(null, null, chunk.meshRevision());
            long geometry = chunk.geometryVersion(), mesh = chunk.meshRevision();
            chunk.setBlock(2, 3, 4, type);
            assertEquals(geometry + 1, chunk.geometryVersion());
            assertEquals(mesh + 1, chunk.meshRevision());
            assertTrue(chunk.dirty());
            assertNotEquals(before, chunk.cell(2, 3, 4).leaves());
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++)
                assertEquals(WorldVoxels.encode(type), chunk.cell(2, 3, 4).get(x, y, z));
            assertEquals(Blocks.isModel(type) ? java.util.Map.of(chunk.index(2, 3, 4), type)
                    : java.util.Map.of(), chunk.models());
        }
    }

    @Test
    void outOfBoundsAirWritesStillThrowWithoutMutation() {
        var chunk = new Chunk();
        for (int axis = 0; axis < 3; axis++) for (int value : new int[] {-1, 16, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            int[] xyz = {0, 0, 0}; xyz[axis] = value;
            assertThrows(IndexOutOfBoundsException.class, () -> chunk.setBlock(xyz[0], xyz[1], xyz[2], 0));
        }
        assertEquals(0, chunk.geometryVersion());
        assertEquals(0, chunk.meshRevision());
        assertTrue(chunk.isEmpty());
    }
}
