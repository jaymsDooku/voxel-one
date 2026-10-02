package dev.jayms.player;

import dev.jayms.*;
import dev.jayms.net.Blocks;
import dev.jayms.net.Protocol;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class Player {
    public static final float RADIUS = .3f, HEIGHT = 1.8f, EYE_HEIGHT = 1.6f;
    private final Vector3f position, lastSafe, velocity = new Vector3f();
    private float yaw, pitch, verticalVelocity, walkPhase, walkAmount;

    public enum CameraView {
        FIRST_PERSON,
        THIRD_PERSON,
        FRONT
    }

    private float swingProgress = 1;
    private int heldItem;
    private int heldColor = 0xffffff;

    public int heldColor() {
        return heldColor;
    }

    public void heldColor(int rgb) {
        if (rgb < 0 || rgb > 0xffffff) throw new IllegalArgumentException("Invalid light colour");
        heldColor = rgb;
    }

    public static final float SWING_SECONDS = .3f, PLACEMENT_SECONDS = .25f;
    private boolean placingSwing;

    private CameraView cameraView = CameraView.FIRST_PERSON;
    private boolean grounded, flying;
    private final Camera camera;

    public Player(Vector3f position, float yaw, float pitch, Camera camera) {
        this.position = new Vector3f(position);
        this.lastSafe = new Vector3f(position);
        this.yaw = yaw;
        this.pitch = pitch;
        this.camera = camera;
        syncCamera();
    }

    public void syncCamera() {
        camera.setYaw(yaw);
        camera.setPitch(pitch);
        camera.position().set(eyePosition());
    }

    public void toggleView() {
        cameraView =
                switch (cameraView) {
                    case FIRST_PERSON -> CameraView.THIRD_PERSON;
                    case THIRD_PERSON -> CameraView.FRONT;
                    case FRONT -> CameraView.FIRST_PERSON;
                };
    }

    public boolean thirdPerson() {
        return cameraView != CameraView.FIRST_PERSON;
    }

    public CameraView cameraView() {
        return cameraView;
    }

    /** Player aim stays independent of the camera looking back at the avatar. */
    public Vector3f facingDirection() {
        double yawRadians = Math.toRadians(yaw), pitchRadians = Math.toRadians(pitch);
        return new Vector3f(
                        (float) (Math.cos(yawRadians) * Math.cos(pitchRadians)),
                        (float) Math.sin(pitchRadians),
                        (float) (Math.sin(yawRadians) * Math.cos(pitchRadians)))
                .normalize();
    }

    public void toggleFlight() {
        flying = !flying;
        verticalVelocity = 0;
        grounded = false;
        velocity.y = 0;
    }

    public boolean flying() {
        return flying;
    }

    public float walkPhase() {
        return walkPhase;
    }

    public float walkAmount() {
        return walkAmount;
    }

    public void swing() {
        swing(false);
    }

    public void swing(boolean placing) {
        if (swingProgress >= .5f || placingSwing != placing) {
            swingProgress = 0;
            placingSwing = placing;
        }
    }

    public boolean placingSwing() {
        return placingSwing;
    }

    public float swingProgress() {
        return swingProgress;
    }

    public void heldItem(int type) {
        if (!dev.jayms.net.Blocks.valid(type))
            throw new IllegalArgumentException("Invalid held item");
        heldItem = type;
    }

    public int heldItem() {
        return heldItem;
    }

    public void updateCamera(World world) {
        syncCamera();
        if (thirdPerson()) {
            Vector3f offset = facingDirection().mul(cameraView == CameraView.FRONT ? 1 : -1);
            float distance;
            for (distance = .1f; distance < 4; distance += .1f) {
                Vector3f p = eyePosition().fma(distance, offset);
                if (solid(
                        world,
                        (int) Math.floor(p.x),
                        (int) Math.floor(p.y),
                        (int) Math.floor(p.z))) {
                    distance = Math.max(0, distance - .2f);
                    break;
                }
            }
            camera.position().fma(Math.min(4, distance), offset);
            if (cameraView == CameraView.FRONT) {
                camera.setYaw(yaw + 180);
                camera.setPitch(-pitch);
            }
        }
    }

    public void look(float dx, float dy) {
        yaw = (yaw + dx) % 360;
        pitch = Math.max(-89, Math.min(89, pitch + dy));
        syncCamera();
    }

    public void step(
            World world, float dt, float forward, float right, boolean jump, boolean sprint) {
        step(world, dt, forward, right, jump, sprint, false);
    }

    public void step(
            World world,
            float dt,
            float forward,
            float right,
            boolean jump,
            boolean sprint,
            boolean descend) {
        dt = Math.min(Math.max(dt, 0), .1f);
        swingProgress =
                Math.min(
                        1, swingProgress + dt / (placingSwing ? PLACEMENT_SECONDS : SWING_SECONDS));
        resolvePenetration(world);
        Vector3f direction =
                new Vector3f(
                        (float) Math.cos(Math.toRadians(yaw)),
                        0,
                        (float) Math.sin(Math.toRadians(yaw)));
        Vector3f target =
                new Vector3f(direction)
                        .mul(forward)
                        .fma(right, new Vector3f(direction).cross(0, 1, 0));
        if (flying) target.y = (jump ? 1 : 0) - (descend ? 1 : 0);
        if (target.lengthSquared() > 1) target.normalize();
        target.mul(flying ? (sprint ? 14 : 8) : (sprint ? 8 : 5));
        if (!flying && jump && grounded) {
            verticalVelocity = 8;
            grounded = false;
        }
        int steps = Math.max(1, (int) Math.ceil(dt / .008f));
        float step = dt / steps;
        Vector3f before = new Vector3f(position);
        for (int i = 0; i < steps; i++) {
            float response = 1 - (float) Math.exp(-(target.lengthSquared() == 0 ? 28 : 20) * step);
            velocity.lerp(target, response);
            if (moveAxis(world, velocity.x * step, 0)) velocity.x = 0;
            if (moveAxis(world, velocity.z * step, 2)) velocity.z = 0;
            if (flying) {
                if (moveAxis(world, velocity.y * step, 1)) velocity.y = 0;
                grounded = false;
            } else {
                verticalVelocity = Math.max(-30, verticalVelocity - 24 * step);
                boolean blocked = moveAxis(world, verticalVelocity * step, 1);
                grounded = blocked && verticalVelocity < 0;
                if (blocked) verticalVelocity = 0;
            }
        }
        float moved = (float) Math.hypot(position.x - before.x, position.z - before.z);
        if (grounded && !flying) walkPhase += moved * (4f * .6662f);
        float amount = grounded && !flying && dt > 0 ? Math.min(1, moved / dt / 5) : 0;
        walkAmount += (amount - walkAmount) * (1 - (float) Math.exp(-8 * dt));
        if (!collides(world)) lastSafe.set(position);
        syncCamera();
    }

    private boolean moveAxis(World world, float delta, int axis) {
        if (delta == 0) return false;
        float start = position.get(axis);
        position.setComponent(axis, start + delta);
        if (!collides(world)) return false;
        float low = 0, high = 1;
        for (int i = 0; i < 14; i++) {
            float mid = (low + high) / 2;
            position.setComponent(axis, start + delta * mid);
            if (collides(world)) high = mid;
            else low = mid;
        }
        position.setComponent(axis, start + delta * low);
        return true;
    }

    /** A late remote edit can surround the predicted player. Find the nearest free block face. */
    public void resolvePenetration(World world) {
        if (!collides(world)) return;
        Vector3f original = new Vector3f(position), best = null;
        float closest = Float.POSITIVE_INFINITY;
        List<Vector3f> candidates = new ArrayList<>();
        for (int x = (int) Math.floor(position.x - RADIUS);
                x <= (int) Math.floor(position.x + RADIUS);
                x++)
            for (int y = (int) Math.floor(position.y);
                    y <= (int) Math.floor(position.y + HEIGHT);
                    y++)
                for (int z = (int) Math.floor(position.z - RADIUS);
                        z <= (int) Math.floor(position.z + RADIUS);
                        z++) {
                    if (!solid(world, x, y, z) || !overlaps(x, y, z)) continue;
                    candidates.add(new Vector3f(original).setComponent(0, x - RADIUS - .001f));
                    candidates.add(new Vector3f(original).setComponent(0, x + 1 + RADIUS + .001f));
                    candidates.add(new Vector3f(original).setComponent(1, y - HEIGHT - .001f));
                    candidates.add(new Vector3f(original).setComponent(1, y + 1 + .001f));
                    candidates.add(new Vector3f(original).setComponent(2, z - RADIUS - .001f));
                    candidates.add(new Vector3f(original).setComponent(2, z + 1 + RADIUS + .001f));
                }
        for (int i = 1; i <= 8; i++) candidates.add(new Vector3f(original).add(0, i, 0));
        candidates.add(new Vector3f(lastSafe));
        for (var candidate : candidates) {
            position.set(candidate);
            float distance = candidate.distanceSquared(original);
            if (distance < closest && !collides(world)) {
                best = new Vector3f(candidate);
                closest = distance;
            }
        }
        position.set(best == null ? original : best);
        velocity.zero();
        verticalVelocity = 0;
        grounded = false;
        if (best != null) lastSafe.set(best);
    }

    public boolean collides(World world) {
        for (int x = (int) Math.floor(position.x - RADIUS);
                x <= (int) Math.floor(position.x + RADIUS - .0001f);
                x++)
            for (int y = (int) Math.floor(position.y);
                    y <= (int) Math.floor(position.y + HEIGHT - .0001f);
                    y++)
                for (int z = (int) Math.floor(position.z - RADIUS);
                        z <= (int) Math.floor(position.z + RADIUS - .0001f);
                        z++)
                    if (solid(world, x, y, z)) {
                        if (!world.isLoaded(x, y, z)) return true;
                        int type = world.getBlock(x, y, z);
                        if (overlaps(world, x, y, z, type)) return true;
                    }
        return false;
    }

    private static boolean solid(World world, int x, int y, int z) {
        return !world.isLoaded(x, y, z) || world.getBlock(x, y, z) != ChunkGenerator.AIR;
    }

    public boolean overlaps(World world, int x, int y, int z, int type) {
        if (type == Blocks.PARTIAL)
            return world.cell(x, y, z)
                    .intersects(
                            position.x - RADIUS - x,
                            position.y - y,
                            position.z - RADIUS - z,
                            position.x + RADIUS - x,
                            position.y + HEIGHT - y,
                            position.z + RADIUS - z);
        var model = world.models().get(type);
        return model == null
                ? overlaps(x, y, z)
                : model.definition()
                        .voxels()
                        .intersects(
                                position.x - RADIUS - x,
                                position.y - y,
                                position.z - RADIUS - z,
                                position.x + RADIUS - x,
                                position.y + HEIGHT - y,
                                position.z + RADIUS - z);
    }

    public boolean overlaps(World world, Protocol.Edit e) {
        if (e.depth() == 0) return overlaps(world, e.x(), e.y(), e.z(), e.type());
        return position.x + RADIUS > e.minX()
                && position.x - RADIUS < e.minX() + e.size()
                && position.y + HEIGHT > e.minY()
                && position.y < e.minY() + e.size()
                && position.z + RADIUS > e.minZ()
                && position.z - RADIUS < e.minZ() + e.size();
    }

    public boolean overlaps(int x, int y, int z) {
        return position.x + RADIUS > x
                && position.x - RADIUS < x + 1
                && position.y + HEIGHT > y
                && position.y < y + 1
                && position.z + RADIUS > z
                && position.z - RADIUS < z + 1;
    }

    public Protocol.Pose pose(int id) {
        return new Protocol.Pose(
                id,
                position.x,
                position.y,
                position.z,
                yaw,
                pitch,
                walkPhase,
                walkAmount,
                flying,
                swingProgress,
                heldItem,
                placingSwing,
                heldColor);
    }

    public Vector3f position() {
        return new Vector3f(position);
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public boolean grounded() {
        return grounded;
    }

    public Vector3f eyePosition() {
        return new Vector3f(position).add(0, EYE_HEIGHT, 0);
    }
}
