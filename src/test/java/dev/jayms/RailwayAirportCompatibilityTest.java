package dev.jayms;

import dev.jayms.net.city.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RailwayAirportCompatibilityTest {
    private void rejectAtomically(AviationTest.Fixture f, CityCommand command) {
        var before = f.city().frame();
        var blocks = Map.copyOf(f.ground().blocks);
        int batches = f.ground().batches;
        assertEquals("Airport cannot cover rails", AviationTest.command(f.city(), command));
        assertEquals(before, f.city().frame());
        assertEquals(blocks, f.ground().blocks);
        assertEquals(batches, f.ground().batches);
    }
    @Test void airportRejectsExistingRailsWithoutEditsOrSpending() {
        var f = AviationTest.fixture();
        assertTrue(AviationTest.command(f.city(), RailwayTest.rail(48,60,132,60)).startsWith("Rail built"));
        rejectAtomically(f, AviationTest.permit(50));
    }
    @Test void expansionRejectsExistingRailsWithoutEditsOrSpending() {
        var f = AviationTest.fixture();
        assertEquals("Permitted Airport with 1 runway", AviationTest.command(f.city(), AviationTest.permit(50)));
        int id = f.city().frame().buildings().get(0).id();
        assertTrue(AviationTest.command(f.city(), RailwayTest.rail(50,84,85,84)).startsWith("Rail built"));
        rejectAtomically(f, new CityCommand(CityCommand.RUNWAY,id,List.of()));
        // A separate airport and its expansion beside the tracks still work.
        assertEquals("Permitted Airport with 1 runway", AviationTest.command(f.city(), AviationTest.permit(100)));
        int adjacent = f.city().frame().buildings().get(1).id();
        assertEquals("Airport expanded to 2 runways", AviationTest.command(f.city(), new CityCommand(CityCommand.RUNWAY,adjacent,List.of())));
        assertEquals(36, f.city().frame().railway().tracks().size());
    }
}
