package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.Blocks;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class BlockRaycasterTest {
    @Test
    void invalidInputsReturnBeforeWorldAccess() {
        for (float value : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertNull(BlockRaycaster.cast(null, new Vector3f(), new Vector3f(1, 0, 0), value));
            for (int axis = 0; axis < 3; axis++) {
                var origin = new Vector3f().setComponent(axis, value);
                var direction = new Vector3f(1, 1, 1).setComponent(axis, value);
                assertNull(BlockRaycaster.cast(null, origin, new Vector3f(1, 0, 0), 4));
                assertNull(BlockRaycaster.cast(null, new Vector3f(), direction, 4));
            }
        }
        assertNull(BlockRaycaster.cast(null, new Vector3f(), new Vector3f(1, 0, 0), -1));
        assertNull(BlockRaycaster.cast(null, new Vector3f(), new Vector3f(), 4));
        assertNull(BlockRaycaster.cast(null, new Vector3f(), new Vector3f(-0.0f, 0, -0.0f), 0));
    }

    private World fixture() {
        var world = new World();
        world.addChunk(new ChunkPos(-1, 0, -1), new Chunk());
        world.addChunk(new ChunkPos(0, 0, -1), new Chunk());
        world.setBlock(-3, 1, -3, Blocks.STONE);
        world.setBlock(2, 1, -3, Blocks.STONE);
        return world;
    }

    @Test
    void scaledRaysPreserveHitsAndCallerVectors() throws Exception {
        try (var world = fixture()) {
            for (int sign : new int[] {-1, 1}) {
                var origin = new Vector3f(-.5f, 1.5f, -2.5f);
                var savedOrigin = new Vector3f(origin);
                var expected = BlockRaycaster.cast(world, origin, new Vector3f(sign, 0, 0), 4);
                assertNotNull(expected);
                assertEquals(sign < 0 ? -3 : 2, expected.x());
                assertEquals(1, expected.y());
                assertEquals(-3, expected.z());
                assertEquals(-sign, expected.normalX());
                assertEquals(0, expected.normalY());
                assertEquals(0, expected.normalZ());
                assertEquals(sign < 0 ? 1.5f : 2.5f, expected.distance());
                for (float magnitude : new float[] {Float.MIN_VALUE, Float.MIN_NORMAL, 1e-20f, 1, 1e20f, Float.MAX_VALUE}) {
                    var direction = new Vector3f(sign * magnitude, 0, 0);
                    var savedDirection = new Vector3f(direction);
                    assertEquals(expected, BlockRaycaster.cast(world, origin, direction, 4));
                    assertEquals(expected, BlockRaycaster.cast(world, origin, direction, expected.distance()));
                    assertNull(BlockRaycaster.cast(world, origin, direction, Math.nextDown(expected.distance())));
                    assertEquals(savedDirection, direction);
                    assertEquals(savedOrigin, origin);
                }
            }
            assertEquals(
                    BlockRaycaster.cast(world, new Vector3f(-.5f, 1.5f, -2.5f), new Vector3f(1, 0, 0), 4),
                    BlockRaycaster.cast(world, new Vector3f(-.5f, 1.5f, -2.5f), new Vector3f(Float.MAX_VALUE, Float.MIN_VALUE, 0), 4));
            // Unequal components ensure scaling preserves direction, not just an axis.
            var origin = new Vector3f(-.5f, .25f, -2.75f);
            var expected = BlockRaycaster.cast(world, origin, new Vector3f(2, 1, 0), 4);
            assertNotNull(expected);
            for (float unit : new float[] {Float.MIN_VALUE, Float.MIN_NORMAL, 1e-20f, 1e20f, Float.MAX_VALUE / 2}) {
                var direction = new Vector3f(2 * unit, unit, 0);
                var saved = new Vector3f(direction);
                var actual = BlockRaycaster.cast(world, origin, direction, 4);
                assertNotNull(actual);
                assertEquals(expected, actual);
                assertEquals(saved, direction);
            }
        }
    }

    @Test
    void zeroReachHitsOnlyAtOriginAndUnloadedCellsMiss() throws Exception {
        try (var world = fixture()) {
            var hit = BlockRaycaster.cast(world, new Vector3f(-2.5f, 1.5f, -2.5f), new Vector3f(1, 0, 0), 0);
            assertNotNull(hit);
            assertEquals(0, hit.distance());
            assertNull(BlockRaycaster.cast(world, new Vector3f(-.5f, 1.5f, -2.5f), new Vector3f(1, 0, 0), 0));
            assertNull(BlockRaycaster.cast(world, new Vector3f(50, 1, 50), new Vector3f(1, 0, 0), 4));
        }
    }
}
