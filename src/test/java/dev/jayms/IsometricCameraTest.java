package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

class IsometricCameraTest {
    @Test
    void rotatedOverviewFitsAllWorldCornersAtWideAndTallSizes() {
        var bounds = new DistantTerrainPlan(-160, -272).bounds();
        var overview = new IsometricCamera();
        for (int turn = 0; turn < 24; turn++) {
            for (int[] size : new int[][] {{1280, 720}, {640, 1000}}) {
                var combined =
                        overview.projection(bounds, size[0], size[1])
                                .mul(overview.camera().createViewMatrix());
                for (float x : new float[] {bounds.minX(), bounds.maxX()})
                    for (float z : new float[] {bounds.minZ(), bounds.maxZ()})
                        for (float y : new float[] {-32, 96}) {
                            var clip = combined.transform(new Vector4f(x, y, z, 1));
                            assertEquals(1, clip.w);
                            assertTrue(Math.abs(clip.x) < 1 && Math.abs(clip.z) < 1);
                            float screenY = (.5f - clip.y * .5f) * size[1];
                            assertTrue(screenY > 80 && screenY < size[1] - 140);
                        }
            }
            overview.rotateDegrees(15);
        }
    }

    @Test
    void freeOrbitPreservesFocusZoomAndPitchAndWrapsInBothDirections() {
        var overview = new IsometricCamera();
        overview.cityMode();
        overview.focus(18, 28, 24);
        overview.zoom(-5);
        float zoom = overview.zoom(), pitch = overview.camera().pitch();
        overview.rotateDegrees(37.5f);
        assertEquals(-97.5f, overview.camera().yaw());
        for (int i = 0; i < 24; i++) {
            overview.rotateDegrees(15);
            overview.projection(new World(), 1280, 720);
            var direction = new Vector3f(18, 24, 28).sub(overview.camera().position()).normalize();
            assertEquals(0, direction.distance(overview.camera().getDirection()), 1e-4);
            assertEquals(zoom, overview.zoom());
            assertEquals(pitch, overview.camera().pitch());
        }
        assertEquals(-97.5f, overview.camera().yaw());
        overview.rotateDegrees(-720);
        assertEquals(-97.5f, overview.camera().yaw());
        overview.rotateDegrees(Float.NaN);
        overview.rotateDegrees(Float.POSITIVE_INFINITY);
        assertEquals(-97.5f, overview.camera().yaw());
    }

    @Test
    void quarterTurnsPreserveFocusZoomPitchAndIsometricAxes() {
        var overview = new IsometricCamera();
        overview.cityMode();
        overview.focus(18, 28, 24);
        overview.zoom(-5);
        float zoom = overview.zoom(), pitch = overview.camera().pitch();
        for (int turn = 0; turn < 4; turn++) {
            overview.rotate(1);
            var combined =
                    overview.projection(new World(), 1280, 720)
                            .mul(overview.camera().createViewMatrix());
            var direction = new Vector3f(18, 24, 28).sub(overview.camera().position()).normalize();
            assertEquals(0, direction.distance(overview.camera().getDirection()), 1e-4);
            assertEquals(zoom, overview.zoom());
            assertEquals(pitch, overview.camera().pitch());
            float length = -1;
            for (int axis = 0; axis < 3; axis++) {
                var projected = combined.transformDirection(new Vector3f().setComponent(axis, 1));
                float next = (float) Math.hypot(projected.x * 1280, projected.y * 720);
                if (length >= 0) assertEquals(length, next, 1e-3);
                length = next;
            }
        }
        assertEquals(-135, overview.camera().yaw());
        overview.rotate(-1);
        assertEquals(135, overview.camera().yaw());
        overview.rotate(1);
        assertEquals(-135, overview.camera().yaw());
    }

    @Test
    void panningFollowsScreenAxesAtEveryOrientation() {
        var overview = new IsometricCamera();
        for (int turn = 0; turn < 24; turn++) {
            overview.focus(0, 0, 32);
            overview.panRelative(1, 0);
            var movement =
                    overview.camera()
                            .createViewMatrix()
                            .transformDirection(
                                    new Vector3f(overview.focusX(), 0, overview.focusZ()));
            assertTrue(movement.x > 0);
            assertEquals(0, movement.y, 1e-5);
            overview.focus(0, 0, 32);
            overview.panRelative(0, 1);
            movement =
                    overview.camera()
                            .createViewMatrix()
                            .transformDirection(
                                    new Vector3f(overview.focusX(), 0, overview.focusZ()));
            assertTrue(movement.y > 0);
            assertEquals(0, movement.x, 1e-5);
            overview.rotateDegrees(15);
        }
    }

    @Test
    void orthographicOverviewFitsLoadedWorldAtWideAndTallAspectRatios() {
        World world = new World();
        world.addChunk(new ChunkPos(-4, 0, -4), new Chunk());
        world.addChunk(new ChunkPos(4, 0, 4), new Chunk());
        IsometricCamera overview = new IsometricCamera();
        for (int[] size : new int[][] {{1280, 720}, {640, 1000}}) {
            Matrix4f combined =
                    overview.projection(world, size[0], size[1])
                            .mul(overview.camera().createViewMatrix());
            for (float x : new float[] {-64, 80})
                for (float y : new float[] {-32, 96})
                    for (float z : new float[] {-64, 80}) {
                        Vector4f clip = combined.transform(new Vector4f(x, y, z, 1));
                        assertEquals(1, clip.w, 1e-6);
                        assertTrue(Math.abs(clip.x) < 1);
                        assertTrue(Math.abs(clip.y) < 1);
                        assertTrue(Math.abs(clip.z) < 1);
                        float screenY = (.5f - clip.y * .5f) * size[1];
                        assertTrue(
                                screenY > 80 && screenY < size[1] - 140,
                                "World overlaps HUD at " + screenY);
                    }
            Vector3f target = new Vector3f(8, 32, 8),
                    direction = target.sub(overview.camera().position()).normalize();
            assertTrue(direction.distance(overview.camera().getDirection()) < 1e-5);
            float[] projected = new float[3];
            for (int i = 0; i < 3; i++) {
                Vector3f axis = new Vector3f().setComponent(i, 1);
                combined.transformDirection(axis);
                projected[i] = (float) Math.hypot(axis.x * size[0], axis.y * size[1]);
            }
            assertEquals(projected[0], projected[1], 1e-4);
            assertEquals(projected[1], projected[2], 1e-4);
        }
    }

    @Test
    void loadedWorldEdgesAreClosedAndAdjacentChunksHideSharedFaces() {
        World world = new World();
        Chunk left = new Chunk();
        left.setBlock(15, 8, 8, 3);
        world.addChunk(new ChunkPos(0, 0, 0), left);
        assertEquals(36, MeshDataGenerator.generate(left).indices().length);
        Chunk right = new Chunk();
        right.setBlock(0, 8, 8, 3);
        world.addChunk(new ChunkPos(1, 0, 0), right);
        assertEquals(30, MeshDataGenerator.generate(left).indices().length);
        assertEquals(30, MeshDataGenerator.generate(right).indices().length);
        world.setBlock(16, 8, 8, 0);
        assertEquals(36, MeshDataGenerator.generate(left).indices().length);
    }

    @Test
    void zoomIsBoundedAndResetRestoresWorldFit() {
        World world = new World();
        IsometricCamera overview = new IsometricCamera();
        float before = overview.projection(world, 1280, 720).m00();
        overview.zoom(3);
        assertTrue(overview.projection(world, 1280, 720).m00() > before);
        for (int i = 0; i < 100; i++) overview.zoom(20);
        assertEquals(64, overview.zoom());
        overview.zoom(Double.NaN);
        assertEquals(64, overview.zoom());
        for (int i = 0; i < 100; i++) overview.zoom(-20);
        assertEquals(.25f, overview.zoom());
        overview.fit();
        assertEquals(before, overview.projection(world, 1280, 720).m00(), 1e-6);
    }

    @Test
    void overviewFitsDistantTerrainWithoutDepthClippingAtBothAspectRatios() {
        var bounds = new DistantTerrainPlan(-160, -272).bounds();
        var overview = new IsometricCamera();
        for (int[] size : new int[][] {{1280, 720}, {640, 1000}}) {
            Matrix4f combined =
                    overview.projection(bounds, size[0], size[1])
                            .mul(overview.camera().createViewMatrix());
            for (float x : new float[] {bounds.minX(), bounds.maxX()})
                for (float z : new float[] {bounds.minZ(), bounds.maxZ()})
                    for (float y : new float[] {-32, 96}) {
                        Vector4f clip = combined.transform(new Vector4f(x, y, z, 1));
                        assertTrue(
                                Math.abs(clip.x) < 1
                                        && Math.abs(clip.y) < 1
                                        && Math.abs(clip.z) < 1);
                        float sy = (.5f - clip.y * .5f) * size[1];
                        assertTrue(sy > 80 && sy < size[1] - 140);
                    }
        }
    }
}
