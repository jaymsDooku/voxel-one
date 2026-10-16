package dev.jayms;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CameraMovementTest {
    private static void vector(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, 0.00001f);
        assertEquals(expected.y, actual.y, 0.00001f);
        assertEquals(expected.z, actual.z, 0.00001f);
        assertTrue(actual.isFinite());
    }

    @Test void allInputCombinationsHaveConstantSpeed() {
        Camera camera = new Camera();
        for (int forward = -1; forward <= 1; forward++)
            for (int strafe = -1; strafe <= 1; strafe++)
                for (int up = -1; up <= 1; up++) {
                    Vector3f delta = camera.movementDelta(forward, strafe, up, 1);
                    assertTrue(delta.isFinite());
                    assertEquals(forward == 0 && strafe == 0 && up == 0 ? 0 : 8,
                            delta.length(), 0.00001f);
                }
    }

    @Test void forwardDiagonalAndVerticalDirections() {
        Camera camera = new Camera();
        vector(new Vector3f(0, 0, -8), camera.movementDelta(1, 0, 0, 1));
        vector(new Vector3f(1, 0, -1).normalize().mul(8), camera.movementDelta(1, 1, 0, 1));
        vector(new Vector3f(1, 1, -1).normalize().mul(8), camera.movementDelta(1, 1, 1, 1));
    }

    @Test void yawFollowsHeadingAndPitchDoesNotAffectHorizontalMovement() {
        Camera camera = new Camera();
        for (float yaw : new float[]{-180, -90, 0, 37, 90, 180, 450}) {
            camera.setYaw(yaw);
            for (float pitch : new float[]{-89, -20, 0, 45, 89}) {
                camera.setPitch(pitch);
                double angle = Math.toRadians(yaw);
                vector(new Vector3f((float)Math.cos(angle), 0, (float)Math.sin(angle)).mul(8),
                        camera.movementDelta(1, 0, 0, 1));
                vector(new Vector3f(-(float)Math.sin(angle), 0, (float)Math.cos(angle)).mul(8),
                        camera.movementDelta(0, 1, 0, 1));
            }
        }
    }

    @Test void cancellationZeroTimeAndFrameScaling() {
        Camera camera = new Camera();
        vector(new Vector3f(), camera.movementDelta(1 - 1, 1 - 1, 1 - 1, 1));
        vector(camera.movementDelta(0, 1, 0, 1), camera.movementDelta(1 - 1, 1, 1 - 1, 1));
        vector(new Vector3f(), camera.movementDelta(1, 1, 1, 0));
        vector(camera.movementDelta(1, 1, 1, 1).mul(0.25f), camera.movementDelta(1, 1, 1, 0.25f));
    }
}
