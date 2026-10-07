package dev.jayms;

import dev.jayms.net.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class CitySavesTest {
    @TempDir Path folder;
    @Test void separateSlotsAndLegacySave() throws Exception {
        Path original = folder.resolve("offline-city.dat");
        new LocalGame(original, 42).save();
        Files.writeString(CitySaves.sidecar(original, ".jeep"), "synthetic vehicle");
        var saves = new CitySaves(original);
        Path copy = saves.create("Harbour", original, 99);
        Path fresh = saves.create("Hills", null, 99);
        assertEquals(42, new LocalGame(copy, 0).seed);
        assertEquals(99, new LocalGame(fresh, 0).seed);
        assertEquals("synthetic vehicle", Files.readString(CitySaves.sidecar(copy, ".jeep")));
        assertFalse(Files.exists(CitySaves.sidecar(fresh, ".jeep")));
        assertEquals(3, saves.list().size());
        assertEquals(3, new CitySaves(fresh).list().size());
        assertThrows(java.io.IOException.class, () -> saves.create("harbour", null, 0));
        assertThrows(java.io.IOException.class, () -> saves.create("../escape", null, 0));
        assertThrows(java.io.IOException.class, () -> saves.create("Original city", null, 0));
        assertEquals(42, new LocalGame(original, 0).seed);
    }
    @Test void failedCopyCleansSlotAndPreservesSource() throws Exception {
        Path original = folder.resolve("offline-city.dat");
        Files.writeString(original, "invalid world");
        var saves = new CitySaves(original);
        assertThrows(java.io.IOException.class, () -> saves.create("Broken", original, 0));
        assertFalse(Files.exists(folder.resolve("city-saves/Broken")));
        assertEquals("invalid world", Files.readString(original));
        Path fresh = saves.create("Broken", null, 7);
        CitySaves.validate(fresh, 0);
    }
}
