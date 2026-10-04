package dev.jayms;

import dev.jayms.net.Terrain;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Orthographic sky view fitted to the visible world footprint, including distant terrain. */
public final class IsometricCamera {
    private final Camera camera = new Camera();
    private float zoom = 1, maxZoom = 64;

    public void cityMode() {
        maxZoom = 256;
    }

    private float panX, panZ, focusY = 32;

    public void focus(float x, float z, float y) {
        focus(x, z);
        focusY = y;
    }

    private boolean focused;

    public boolean focused() {
        return focused;
    }

    public float focusX() {
        return panX;
    }

    public float focusZ() {
        return panZ;
    }

    public void focus(float x, float z) {
        focused = true;
        panX = x;
        panZ = z;
        zoom = maxZoom > 64 ? 128 : 64;
    }

    public void pan(float x, float z) {
        focused = true;
        panX = Math.max(-248, Math.min(264, panX + x));
        panZ = Math.max(-232, Math.min(280, panZ + z));
    }

    public IsometricCamera() {
        camera.setYaw(-135);
        camera.setPitch((float) -Math.toDegrees(Math.atan(1 / Math.sqrt(2))));
    }

    /** Orbit in quarter turns, keeping equal foreshortening of all three world axes. */
    public void rotate(int quarterTurns) {
        int turns = Math.floorMod(quarterTurns, 4);
        camera.setYaw(-135 + Math.floorMod(Math.round((camera.yaw() + 135) / 90) + turns, 4) * 90);
    }

    /** Orbit freely around the current focus without changing elevation or zoom. */
    public void rotateDegrees(float degrees) {
        if (!Float.isFinite(degrees)) return;
        double yaw = (double) camera.yaw() + degrees;
        camera.setYaw((float) (((yaw + 180) % 360 + 360) % 360 - 180));
    }

    /** Pan along screen right/up on the ground plane at the current orientation. */
    public void panRelative(float right, float forward) {
        Vector3f direction = camera.getDirection();
        // Retain the existing pan speed at the default diagonal orientation.
        float scale =
                (float) Math.sqrt(2 / (direction.x * direction.x + direction.z * direction.z));
        pan(
                (direction.x * forward - direction.z * right) * scale,
                (direction.z * forward + direction.x * right) * scale);
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
                                maxZoom,
                                zoom * (float) Math.pow(1.15, Math.max(-20, Math.min(20, steps)))));
    }

    public void fit() {
        zoom = 1;
        focused = false;
        panX = panZ = 0;
    }

    public Matrix4f projection(World world, int width, int height) {
        return projection(WorldBounds.loaded(world), width, height);
    }

    public Matrix4f projection(WorldBounds bounds, int width, int height) {
        float minX = bounds.minX(),
                minZ = bounds.minZ(),
                maxX = bounds.maxX(),
                maxZ = bounds.maxZ();
        float minY = Terrain.MIN_Y, maxY = Terrain.MAX_Y + 1;
        Vector3f center = new Vector3f((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
        float radius = new Vector3f(maxX - minX, maxY - minY, maxZ - minZ).length() / 2;
        float distance = radius + 128;
        camera.position().set(center).fma(-distance, camera.getDirection());
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
        if (focused) {
            camera.position().x += panX - center.x;
            camera.position().z += panZ - center.z;
            camera.position().y += focusY - center.y;
        }
        return new Matrix4f()
                .ortho(
                        -halfHeight * aspect,
                        halfHeight * aspect,
                        offset - halfHeight,
                        offset + halfHeight,
                        .1f,
                        distance + radius + 128);
    }
}
