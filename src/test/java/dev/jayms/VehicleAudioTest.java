package dev.jayms;

import dev.jayms.audio.VehicleAudio;
import dev.jayms.net.city.Aviation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleAudioTest {
    @Test void onlyDepartureWindowMakesTakeoffSound() {
        for (int stage : new int[]{0, 1, 3})
            assertFalse(VehicleAudio.takingOff(new Aviation.Flight(1, 1, 2, 1, 0, 0, stage, 0)));
        assertTrue(VehicleAudio.takingOff(new Aviation.Flight(1, 1, 2, 1, 0, 0, 2, 0)));
        assertTrue(VehicleAudio.takingOff(new Aviation.Flight(1, 1, 2, 1, 0, 0, 2, 5.99)));
        assertFalse(VehicleAudio.takingOff(new Aviation.Flight(1, 1, 2, 1, 0, 0, 2, 6)));
        assertFalse(VehicleAudio.takingOff(new Aviation.Flight(1, 1, 2, 1, 0, 0, 2, 20)));
    }
}
