package dev.jayms;

import dev.jayms.net.city.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HistoryStressGridTest {
    @Test void millionImplicitPlotsUseExactCountersWithoutScanningZones()throws Exception {
        var grid=StressGrid.standard();
        var city=new CityFrame(GameConfig.cityGame(),0,List.of(),List.of(),List.of(),List.of(),List.of(),CityEconomy.State.empty(),CityAddresses.empty(),Agriculture.State.empty(),RegionalPopulation.State.empty(),Aviation.State.empty(),Railway.State.empty(),grid);
        assertEquals(1_000_000,city.zones().size());
        long began=System.nanoTime();HistoryCatalog.Snapshot snapshot=null;
        for(int i=0;i<1000;i++)snapshot=HistoryCatalog.from(city);
        double millis=(System.nanoTime()-began)/1e6;
        assertEquals((double)grid.roadCells(),snapshot.values().get("development.roads"));
        assertEquals(1_000_000,snapshot.values().get("development.available"));
        assertEquals(102_400_000,snapshot.values().get("development.zone.0"));
        assertEquals(51_200_000,snapshot.values().get("development.zone.3"));
        String report=String.format(Locale.ROOT,"Implicit stress grid: plots=1000000 developed_buildings=0 local_residents=0 captures=1000 extraction_ms=%.3f mean_ms=%.6f retained_catalog_entries=%d. Exact road/zone/available-site counters checked. No claim of a million developed buildings.\n",millis,millis/1000,snapshot.metrics().size());
        if(Boolean.getBoolean("voxel.history.evidence"))Files.writeString(Path.of("dashboard/evidence/history-implicit-grid-scale.txt"),report);
    }
}
