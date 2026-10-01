package dev.jayms;

import dev.jayms.net.Terrain;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Orthographic sky view fitted to the currently loaded chunk footprint. */
public final class IsometricCamera {
    private final Camera camera = new Camera();
    private float zoom = 1;

    public IsometricCamera() {
        camera.setYaw(-135);
        camera.setPitch((float) -Math.toDegrees(Math.atan(1 / Math.sqrt(2))));
    }

    public Camera camera() {
        return camera;
    }

    public float zoom() {
        return zoom;
    }

    public void zoom(double steps) {
        if (!Double.isFinite(steps)) return;
        zoom =
                Math.max(
                        .25f,
                        Math.min(
                                8,
                                zoom * (float) Math.pow(1.15, Math.max(-20, Math.min(20, steps)))));
    }

    public void fit() {
        zoom = 1;
    }

    public Matrix4f projection(World world, int width, int height) {
        float minX = Float.POSITIVE_INFINITY,
                minZ = minX,
                maxX = Float.NEGATIVE_INFINITY,
                maxZ = maxX;
        for (ChunkPos p : world.getLoadedChunks().keySet()) {
            minX = Math.min(minX, p.chunkX() * 16);
            maxX = Math.max(maxX, p.chunkX() * 16 + 16);
            minZ = Math.min(minZ, p.chunkZ() * 16);
            maxZ = Math.max(maxZ, p.chunkZ() * 16 + 16);
        }
        if (!Float.isFinite(minX)) {
            minX = minZ = 0;
            maxX = maxZ = 16;
        }
        float minY = Terrain.MIN_Y, maxY = Terrain.MAX_Y + 1;
        Vector3f center = new Vector3f((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
        camera.position().set(center).fma(-320, camera.getDirection());
        Matrix4f view = camera.createViewMatrix();
        float aspect = (float) Math.max(1, width) / Math.max(1, height), extent = 1;
        float usableWidth = Math.max(1, width - 40), usableHeight = Math.max(1, height - 220);
        for (float x : new float[] {minX, maxX})
            for (float y : new float[] {minY, maxY})
                for (float z : new float[] {minZ, maxZ}) {
                    Vector3f projected = view.transformPosition(new Vector3f(x, y, z));
                    extent =
                            Math.max(
                                    extent,
                                    Math.max(
                                            Math.abs(projected.y) * height / usableHeight,
                                            Math.abs(projected.x) * height / usableWidth));
                }
        float halfHeight = extent * 1.1f / zoom;
        // Reserve the top status panel and bottom inventory HUD when fitting the world.
        float offset = -60f / Math.max(1, height) * halfHeight;
        return new Matrix4f()
                .ortho(
                        -halfHeight * aspect,
                        halfHeight * aspect,
                        offset - halfHeight,
                        offset + halfHeight,
                        .1f,
                        1000);
    }
}
