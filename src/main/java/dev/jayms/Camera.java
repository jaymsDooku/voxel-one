package dev.jayms;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

public final class Camera {

    private final Vector3f position =
            new Vector3f(8.0f, 10.0f, 24.0f);

    private float yaw = -90.0f;
    private float pitch = -20.0f;

    public Matrix4f createViewMatrix() {
        Vector3f direction = getDirection();

        return new Matrix4f().lookAt(
                position,
                new Vector3f(position).add(direction),
                new Vector3f(0, 1, 0)
        );
    }

    public Vector3f getDirection() {
        float yawRadians =
                (float) Math.toRadians(yaw);

        float pitchRadians =
                (float) Math.toRadians(pitch);

        return new Vector3f(
                (float) (
                        Math.cos(yawRadians)
                                * Math.cos(pitchRadians)
                ),
                (float) Math.sin(pitchRadians),
                (float) (
                        Math.sin(yawRadians)
                                * Math.cos(pitchRadians)
                )
        ).normalize();
    }

    public Vector3f position() {
        return position;
    }

    public float yaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float pitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = Math.max(
                -89.0f,
                Math.min(89.0f, pitch)
        );
    }

    public void updateCamera(
            long window,
            float deltaTime
    ) {
        int forward = pressed(window, GLFW_KEY_W) - pressed(window, GLFW_KEY_S);
        int strafe = pressed(window, GLFW_KEY_D) - pressed(window, GLFW_KEY_A);
        int vertical = pressed(window, GLFW_KEY_SPACE) - pressed(window, GLFW_KEY_LEFT_SHIFT);
        position.add(movementDelta(forward, strafe, vertical, deltaTime));
    }

    private static int pressed(long window, int key) {
        return glfwGetKey(window, key) == GLFW_PRESS ? 1 : 0;
    }

    /** Computes free-camera displacement from signed input axes without polling GLFW.
     * Horizontal movement follows yaw alone, regardless of the camera pitch.
     */
    public Vector3f movementDelta(int forward, int strafe, int vertical, float deltaTime) {
        double radians = Math.toRadians(yaw);
        float cosine = (float) Math.cos(radians);
        float sine = (float) Math.sin(radians);
        Vector3f movement = new Vector3f(
                forward * cosine - strafe * sine,
                vertical,
                forward * sine + strafe * cosine);
        if (movement.lengthSquared() > 0.0f) {
            movement.normalize().mul(8.0f * deltaTime);
        }
        return movement;
    }
}
