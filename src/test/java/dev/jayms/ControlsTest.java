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
