package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class IsometricOrbitDragTest {
    @Test
    void dragUsesRelativeMotionAndSensitivityAndStopsOnRelease() {
        var camera = new IsometricCamera();
        var drag = new IsometricOrbitDrag();
        drag.move(100, .5f, camera);
        assertEquals(-135, camera.camera().yaw());
        drag.begin();
        assertTrue(drag.active());
        drag.move(1000, .5f, camera);
        assertEquals(-135, camera.camera().yaw());
        drag.move(1075, .5f, camera);
        assertEquals(-97.5f, camera.camera().yaw());
        drag.move(1795, .5f, camera);
        assertEquals(-97.5f, camera.camera().yaw());
        drag.move(1075, .5f, camera);
        assertEquals(-97.5f, camera.camera().yaw());
        drag.end();
        assertFalse(drag.active());
        drag.move(2000, .5f, camera);
        assertEquals(-97.5f, camera.camera().yaw());
        drag.begin();
        drag.move(-200, .5f, camera);
        assertEquals(-97.5f, camera.camera().yaw());
        drag.move(-220, .5f, camera);
        assertEquals(-107.5f, camera.camera().yaw());
    }
}
