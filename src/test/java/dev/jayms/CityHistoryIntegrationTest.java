package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CityHistoryIntegrationTest {
    @TempDir Path temp;
    @Test void thirtyDaysWithoutDashboardAccountingReloadAndMillionResidents()throws Exception {
        var ground=new CityTest.Ground();
        var config=new GameConfig(true,true,60,0);
        var simulation=new CitySimulation(config,ground,ground.terrain,null,ProductionCatalog.toolEra());
        simulation.collectHistory();
        String road=simulation.command(new CityCommand(CityCommand.ROAD,1,List.of(new Polygon.Point(44,24),new Polygon.Point(60,24))),1,null);
        assertTrue(simulation.economy.roadSpending>0,road);
        double spending=simulation.economy.roadSpending;
        simulation.command(new CityCommand(CityCommand.SETTLE_DISTRICT,1_000_000,List.of()),1,null);
        long began=System.nanoTime();
        for(int i=0;i<1800;i++)simulation.advance(1);
        double millis=(System.nanoTime()-began)/1e6;
        assertTrue(simulation.frame().elapsed()>=1800);
        var h=simulation.history;
        var spend=h.query(null,1,List.of("finance.spending"),0,2000,2,1000);
        assertEquals(spending,spend.series().get(0).points().stream().mapToDouble(CityHistory.Point::value).filter(Double::isFinite).sum(),1e-6);
        var population=h.query(null,1,List.of("population.total"),0,2000,2,1000);
        assertTrue(population.series().get(0).points().stream().anyMatch(p->!p.gap()&&p.value()>1_000_000));
        assertTrue(simulation.frame().citizens().size()<=128);
        Path city=temp.resolve("city");simulation.save(city);
        var restored=new CitySimulation(config,ground,ground.terrain,CitySimulation.load(city),ProductionCatalog.toolEra());
        restored.loadHistory(city);restored.collectHistory();
        assertEquals(h.identity(),restored.history.identity(),restored.history.query(null,1,List.of(),0,2000,2,1000).note());
        assertEquals(population.series(),restored.history.query(null,1,List.of("population.total"),0,2000,2,1000).series());
        long queryStart=System.nanoTime();var reply=h.query(null,2,List.of("population.total"),0,2000,2,1000);double queryMillis=(System.nanoTime()-queryStart)/1e6;
        var bytes=new java.io.ByteArrayOutputStream();HistoryWire.write(new java.io.DataOutputStream(bytes),reply);
        String report=String.format(Locale.ROOT,"HISTORY_SCALE: simulated_days=30 local_residents=%d regional_groups=%d represented_residents=%d simulation_ms=%.3f history_bytes=%d query_bytes=%d query_ms=%.3f%n",simulation.frame().citizens().size(),simulation.population.state().groups().size(),CityMetrics.from(simulation.frame()).population(),millis,Files.size(city.resolveSibling("city.history")),bytes.size(),queryMillis);
        report+=String.format(Locale.ROOT,"collection_samples=%d collection_total_ms=%.3f mean_collection_ms=%.3f\n",simulation.historySamples,simulation.historyCollectionNanos/1e6,simulation.historyCollectionNanos/1e6/Math.max(1,simulation.historySamples));
        if(Boolean.getBoolean("voxel.history.evidence"))Files.writeString(Path.of("dashboard/evidence/history-scale.txt"),report);
        System.out.print(report);
    }
}
