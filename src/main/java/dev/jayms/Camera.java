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
        float speed = 8.0f * deltaTime;

        Vector3f forward = getDirection();

        // Prevent flying vertically while moving forward.
        forward.y = 0;
        forward.normalize();

        Vector3f right = new Vector3f(forward)
                .cross(0, 1, 0)
                .normalize();

        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
            position().fma(speed, forward);
        }

        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
            position().fma(-speed, forward);
        }

        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
            position().fma(speed, right);
        }

        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
            position().fma(-speed, right);
        }

        if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS) {
            position().y += speed;
        }

        if (
                glfwGetKey(
                        window,
                        GLFW_KEY_LEFT_SHIFT
                ) == GLFW_PRESS
        ) {
            position().y -= speed;
        }
    }
}
