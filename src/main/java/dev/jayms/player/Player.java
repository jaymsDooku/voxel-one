package dev.jayms.player;

import dev.jayms.Camera;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.glfwGetKey;

public class Player {

    private static final float EYE_HEIGHT = 1.6f;

    private Vector3f position;
    private float yaw;
    private float pitch;

    private Camera camera;

    public Player(Vector3f position, float yaw, float pitch, Camera camera) {
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
        this.camera = camera;
        syncCamera();
    }

    public void syncCamera() {
        camera.setYaw(yaw);
        camera.setPitch(pitch);
        camera.position()
                .set(eyePosition())
                .fma(-4.0f, camera.getDirection());
    }

    public void look(float deltaYaw, float deltaPitch) {
        yaw += deltaYaw;
        pitch = Math.max(-89.0f, Math.min(89.0f, pitch + deltaPitch));
        syncCamera();
    }

    public void update(
            long window,
            float deltaTime
    ) {
        float speed = 8.0f * deltaTime;

        Vector3f forward = camera.getDirection();

        // Prevent flying vertically while moving forward.
        forward.y = 0;
        forward.normalize();

        Vector3f right = new Vector3f(forward)
                .cross(0, 1, 0)
                .normalize();

        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
            position.fma(speed, forward);
        }

        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
            position.fma(-speed, forward);
        }

        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
            position.fma(speed, right);
        }

        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
            position.fma(-speed, right);
        }

        if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS) {
            position.y += speed;
        }

        if (
                glfwGetKey(
                        window,
                        GLFW_KEY_LEFT_SHIFT
                ) == GLFW_PRESS
        ) {
            position.y -= speed;
        }

        syncCamera();
    }

    public Vector3f position() {
        return new Vector3f(position);
    }

    public float yaw() {
        return yaw;
    }

    public Vector3f eyePosition() {
        return new Vector3f(position).add(0, EYE_HEIGHT, 0);
    }

}
