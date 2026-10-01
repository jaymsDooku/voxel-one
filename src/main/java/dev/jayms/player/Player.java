package dev.jayms.player;

import dev.jayms.*;
import org.joml.Vector3f;
import static org.lwjgl.glfw.GLFW.*;

public class Player {
    public static final float RADIUS = .3f, HEIGHT = 1.8f, EYE_HEIGHT = 1.6f;
    private final Vector3f position;
    private float yaw, pitch, verticalVelocity;
    private boolean grounded, thirdPerson, jumpHeld;
    private final Camera camera;
    public Player(Vector3f position, float yaw, float pitch, Camera camera) {
        this.position = new Vector3f(position); this.yaw = yaw; this.pitch = pitch; this.camera = camera; syncCamera();
    }
    public void syncCamera() {
        camera.setYaw(yaw); camera.setPitch(pitch); camera.position().set(eyePosition());
    }
    public void toggleView() { thirdPerson = !thirdPerson; }
    public boolean thirdPerson() { return thirdPerson; }
    public void updateCamera(World world) {
        syncCamera();
        if (thirdPerson) {
            Vector3f back = camera.getDirection().negate();
            var hit = BlockRaycaster.cast(world, eyePosition(), back, 4);
            float distance = 4;
            if (hit != null) {
                // Small steps prevent the camera crossing the near face of a voxel.
                for (distance = .1f; distance < 4; distance += .1f) {
                    Vector3f p = eyePosition().fma(distance, back);
                    if (solid(world, (int)Math.floor(p.x), (int)Math.floor(p.y), (int)Math.floor(p.z))) { distance = Math.max(0, distance - .2f); break; }
                }
            }
            camera.position().fma(-distance, camera.getDirection());
        }
    }
    public void look(float dx, float dy) { yaw = (yaw + dx) % 360; pitch = Math.max(-89, Math.min(89, pitch + dy)); syncCamera(); }
    public void update(long window, float dt, World world) {
        float forward = (down(window, GLFW_KEY_W) ? 1 : 0) - (down(window, GLFW_KEY_S) ? 1 : 0);
        float right = (down(window, GLFW_KEY_D) ? 1 : 0) - (down(window, GLFW_KEY_A) ? 1 : 0);
        step(world, dt, forward, right, down(window, GLFW_KEY_SPACE), down(window, GLFW_KEY_LEFT_SHIFT));
        updateCamera(world);
    }
    private boolean down(long w, int key) { return glfwGetKey(w, key) == GLFW_PRESS; }
    public void step(World world, float dt, float forward, float right, boolean jump, boolean sprint) {
        dt = Math.min(Math.max(dt, 0), .1f);
        Vector3f direction = new Vector3f((float)Math.cos(Math.toRadians(yaw)), 0, (float)Math.sin(Math.toRadians(yaw)));
        Vector3f movement = new Vector3f(direction).mul(forward).fma(right, new Vector3f(direction).cross(0, 1, 0));
        if (movement.lengthSquared() > 1) movement.normalize();
        movement.mul(sprint ? 8 : 5);
        if (jump && !jumpHeld && grounded) { verticalVelocity = 8; grounded = false; }
        jumpHeld = jump;
        int steps = Math.max(1, (int)Math.ceil(dt / .008f)); float step = dt / steps;
        for (int i = 0; i < steps; i++) {
            moveAxis(world, movement.x * step, 0); moveAxis(world, movement.z * step, 2);
            verticalVelocity = Math.max(-30, verticalVelocity - 24 * step);
            boolean blocked = moveAxis(world, verticalVelocity * step, 1);
            grounded = blocked && verticalVelocity < 0;
            if (blocked) verticalVelocity = 0;
        }
        syncCamera();
    }
    private boolean moveAxis(World world, float delta, int axis) {
        if (delta == 0) return false;
        float start = position.get(axis); position.setComponent(axis, start + delta);
        if (!collides(world)) return false;
        float low = 0, high = 1;
        for (int i = 0; i < 14; i++) { float mid = (low + high) / 2; position.setComponent(axis, start + delta * mid); if (collides(world)) high = mid; else low = mid; }
        position.setComponent(axis, start + delta * low); return true;
    }
    private boolean collides(World world) {
        for (int x = (int)Math.floor(position.x - RADIUS); x <= (int)Math.floor(position.x + RADIUS - .0001f); x++)
            for (int y = (int)Math.floor(position.y); y <= (int)Math.floor(position.y + HEIGHT - .0001f); y++)
                for (int z = (int)Math.floor(position.z - RADIUS); z <= (int)Math.floor(position.z + RADIUS - .0001f); z++)
                    if (solid(world, x, y, z)) return true;
        return false;
    }
    private static boolean solid(World world, int x, int y, int z) { return !world.isLoaded(x, y, z) || world.getBlock(x, y, z) != ChunkGenerator.AIR; }
    public boolean overlaps(int x, int y, int z) { return position.x + RADIUS > x && position.x - RADIUS < x + 1 && position.y + HEIGHT > y && position.y < y + 1 && position.z + RADIUS > z && position.z - RADIUS < z + 1; }
    public Vector3f position() { return new Vector3f(position); }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public boolean grounded() { return grounded; }
    public Vector3f eyePosition() { return new Vector3f(position).add(0, EYE_HEIGHT, 0); }
}
