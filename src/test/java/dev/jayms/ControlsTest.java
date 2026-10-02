package dev.jayms;

import static dev.jayms.ui.Controls.Action.*;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.ui.Controls;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

class ControlsTest {
    @TempDir Path temp;

    @Test
    void bindingsSwapPersistAndReset() throws Exception {
        Path file = temp.resolve("controls.properties");
        var c = new Controls(file);
        c.bind(FORWARD, c.code(BACKWARD));
        c.bind(FLY, -3);
        c.sensitivity = .2f;
        c.save();
        c = new Controls(file);
        assertEquals(BACKWARD.defaultCode, c.code(FORWARD));
        assertEquals(FORWARD.defaultCode, c.code(BACKWARD));
        assertEquals(-3, c.code(FLY));
        assertEquals(.2f, c.sensitivity, .001);
        c.reset();
        c.save();
        assertEquals(FORWARD.defaultCode, new Controls(file).code(FORWARD));
    }

    @Test
    void newDashboardBindingPreservesOlderCustomF9Binding() throws Exception {
        Path file = temp.resolve("legacy-controls.properties");
        var c = new Controls(file);
        c.bind(FORWARD, org.lwjgl.glfw.GLFW.GLFW_KEY_F9);
        c.save();
        var props = new java.util.Properties();
        try (var in = Files.newInputStream(file)) {
            props.load(in);
        }
        props.remove("MAYOR_DASHBOARD");
        try (var out = Files.newOutputStream(file)) {
            props.store(out, "Legacy controls");
        }
        var loaded = new Controls(file);
        assertEquals(org.lwjgl.glfw.GLFW.GLFW_KEY_F9, loaded.code(FORWARD));
        assertNotEquals(loaded.code(FORWARD), loaded.code(MAYOR_DASHBOARD));
        assertEquals(BACKWARD.defaultCode, loaded.code(BACKWARD));
    }

    @Test
    void newRecorderPreservesAnOlderCustomF10Binding() throws Exception {
        Path file = temp.resolve("old-controls.properties");
        var controls = new Controls(file);
        controls.bind(FORWARD, org.lwjgl.glfw.GLFW.GLFW_KEY_F10);
        controls.save();
        var properties = new java.util.Properties();
        try (var input = Files.newInputStream(file)) {
            properties.load(input);
        }
        properties.remove("RECORD");
        try (var output = Files.newOutputStream(file)) {
            properties.store(output, "Before recorder");
        }
        var loaded = new Controls(file);
        assertEquals(org.lwjgl.glfw.GLFW.GLFW_KEY_F10, loaded.code(FORWARD));
        assertNotEquals(loaded.code(FORWARD), loaded.code(RECORD));
        assertEquals(MAYOR_DASHBOARD.defaultCode, loaded.code(MAYOR_DASHBOARD));
        loaded.bind(RECORD, -3);
        assertEquals(-3, new Controls(file).code(RECORD));
    }

    @Test
    void escapeIsReservedAndCorruptConfigFallsBack() throws Exception {
        Path file = temp.resolve("controls.properties");
        var c = new Controls(file);
        final var controls = c;
        assertThrows(java.io.IOException.class, () -> controls.bind(FLY, 256));
        Files.writeString(file, "FORWARD=banana\nsensitivity=NaN\n");
        c = new Controls(file);
        assertEquals(FORWARD.defaultCode, c.code(FORWARD));
        assertEquals(.1f, c.sensitivity, .001);
    }
}
