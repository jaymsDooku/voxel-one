package dev.jayms;

import dev.jayms.net.city.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CityHistoryTest {
    @TempDir Path temp;
    final GameConfig clock=new GameConfig(true,true,288,0);
    CityHistory.Metric metric(String id,String unit,CityHistory.Kind kind) {
        return new CityHistory.Metric(id,id,unit,"Population",kind,"Synthetic city","Measured test source",2);
    }
    List<CityHistory.Point> points(CityHistory h,String id,int resolution,double end) {
        return h.query(h.identity(),1,List.of(id),0,end,resolution,1000).series().get(0).points();
    }
    @Test void exactHalfOpenEventsNegativeProfitAndWeightedRatios() {
        var h=new CityHistory();h.events=true;
        var metrics=List.of(metric("population.total","people",CityHistory.Kind.STOCK),metric("population.employment","%",CityHistory.Kind.RATIO),metric("revenue","$",CityHistory.Kind.COUNTER),metric("profit","$",CityHistory.Kind.SIGNED_COUNTER));
        for(int i=0;i<=24;i++) {
            if(i==11){h.event("revenue",11.9,9,clock);h.event("profit",11.9,-4,clock);}
            if(i==12){h.event("revenue",12,7,clock);}
            h.observe(i,clock,metrics,Map.of("population.total",i<6?10.0:30.0,"population.employment",i<6?100.0:0.0,"revenue",0.0,"profit",0.0));
        }
        var revenue=points(h,"revenue",1,24);
        assertEquals(9,revenue.get(0).value());assertEquals(7,revenue.get(1).value());
        assertEquals(-4,points(h,"profit",1,24).get(0).value());
        assertEquals(25,points(h,"population.employment",1,24).get(0).value(),1e-6);
        assertEquals(20,points(h,"population.total",1,24).get(0).value());
        assertFalse(revenue.get(0).partial());
    }
    @Test void retentionGapsEpochsAndCaps() {
        var h=new CityHistory();var metrics=List.of(metric("population.total","people",CityHistory.Kind.STOCK));
        for(int i=0;i<=288*400;i++)h.observe(i,clock,metrics,Map.of("population.total",(double)(i%10)));
        assertTrue(points(h,"population.total",0,288*400).size()<=289);
        assertTrue(points(h,"population.total",1,288*400).size()<=721);
        assertTrue(points(h,"population.total",2,288*400).size()<=366);
        assertTrue(h.query(h.identity(),1,List.of("population.total"),0,288*400,2,1000).availableStart()>0);
        assertThrows(IllegalArgumentException.class,()->h.query(h.identity(),1,List.of("population.total"),0,288*400,2,2));
        assertThrows(IllegalArgumentException.class,()->h.query(UUID.randomUUID(),1,List.of("population.total"),0,10,0,1000));
        h.observe(100,clock,metrics,Map.of("population.total",8.0));
        assertEquals(2,h.query(null,1,List.of(),0,100,0,1000).epochs().size());
        h.observe(101,new GameConfig(true,false,576,8),metrics,Map.of("population.total",9.0));
        assertTrue(h.query(null,1,List.of(),0,101,0,1000).epochs().get(2).label(101).contains("frozen calendar"));
        h.observe(120,new GameConfig(true,false,576,8),metrics,Map.of("population.total",9.0));
        assertTrue(points(h,"population.total",0,120).stream().anyMatch(CityHistory.Point::gap));
    }
    @Test void atomicRoundTripCorruptionAndWorldBinding()throws Exception {
        var h=new CityHistory();var metrics=List.of(metric("treasury","$",CityHistory.Kind.STOCK));
        for(int i=0;i<25;i++)h.observe(i,clock,metrics,Map.of("treasury",-10.0));
        Path world=temp.resolve("world.city"),history=temp.resolve("world.city.history");Files.writeString(world,"synthetic world");
        h.save(history,world);assertTrue(Files.size(history)<CityHistory.MAX_BYTES);
        var restored=CityHistory.load(history,world,clock,24);assertEquals(h.identity(),restored.identity());assertEquals(points(h,"treasury",1,24),points(restored,"treasury",1,24));
        Files.writeString(world,"restored different world");assertTrue(CityHistory.load(history,world,clock,24).catalog().isEmpty());
        Files.write(history,new byte[]{1,2,3});assertTrue(CityHistory.load(history,world,clock,24).catalog().isEmpty());
        assertTrue(CityHistory.load(temp.resolve("legacy"),world,clock,24).catalog().isEmpty());
    }
    @Test void boundedWireRoundTripAndUnits()throws Exception {
        var h=new CityHistory();var metrics=List.of(metric("cash","$",CityHistory.Kind.STOCK),metric("sales","$",CityHistory.Kind.COUNTER));
        h.observe(0,clock,metrics,Map.of("cash",1.0,"sales",0.0));h.observe(1,clock,metrics,Map.of("cash",1.0,"sales",2.0));
        assertThrows(IllegalArgumentException.class,()->h.query(null,1,List.of("cash","sales"),0,1,0,1000));
        assertThrows(IllegalArgumentException.class,()->new HistoryWire.Request(null,1,List.of(),0,1,0,1001));
        assertThrows(IllegalArgumentException.class,()->h.query(null,1,List.of("missing"),0,1,0,1000));
        var reply=h.query(null,1,List.of("cash"),0,1,0,1000);var bytes=new ByteArrayOutputStream();HistoryWire.write(new DataOutputStream(bytes),reply);
        assertEquals(reply,HistoryWire.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }
    @Test void fixedQueryEdgesDoNotIncludeLaterEventsAndNonfiniteDataStayMissing() {
        var h=new CityHistory();h.events=true;var metrics=List.of(metric("revenue","$",CityHistory.Kind.COUNTER));
        h.observe(0,clock,metrics,Map.of("revenue",0.0));h.event("revenue",5,3,clock);h.event("revenue",11,7,clock);
        for(int i=1;i<=24;i++)h.observe(i,clock,metrics,Map.of("revenue",10.0));
        var clipped=h.query(null,1,List.of("revenue"),0,10,1,1000).series().get(0).points();
        assertTrue(clipped.stream().allMatch(CityHistory.Point::gap));
        var complete=h.query(null,1,List.of("revenue"),0,12,1,1000).series().get(0).points();
        assertEquals(1,complete.size());assertEquals(10,complete.get(0).value());
        var stock=new CityHistory();var stocks=List.of(metric("stock","people",CityHistory.Kind.STOCK));
        stock.observe(0,clock,stocks,Map.of("stock",1.0));stock.observe(1,clock,stocks,Map.of("stock",Double.NaN));
        assertTrue(points(stock,"stock",1,12).get(0).gap());
    }
    @Test void renameCapOmissionAndClosureKeepIdentityWithoutFalseClosure() {
        var h=new CityHistory();h.events=true;
        var first=metric("company.1.revenue","$",CityHistory.Kind.COUNTER);
        h.observe(0,clock,List.of(first),Map.of(first.id(),0.0),Set.of("company.1."));
        h.event(first.id(),.5,6,clock);h.observe(1,clock,List.of(first),Map.of(first.id(),6.0),Set.of("company.1."));
        var renamed=new CityHistory.Metric(first.id(),"Renamed company","$","Businesses",first.kind(),"Company #1","Measured account",2);
        h.observe(2,clock,List.of(renamed),Map.of(first.id(),6.0),Set.of("company.1."));
        assertEquals("Renamed company",h.catalog().get(0).name());assertEquals(6,points(h,first.id(),0,1).get(0).value());
        h.observe(3,clock,List.of(),Map.of(),Set.of("company.1."));assertTrue(h.catalog().get(0).scope().contains("detail cap"));assertFalse(h.catalog().get(0).scope().contains("Inactive"));
        h.observe(4,clock,List.of(),Map.of(),Set.of());assertTrue(h.catalog().get(0).scope().contains("Inactive observed"));
    }
    @Test void checksumAndOversizeRejectWithoutChangingWorld()throws Exception {
        var h=new CityHistory();var metrics=List.of(metric("stock","people",CityHistory.Kind.STOCK));
        h.observe(0,clock,metrics,Map.of("stock",1.0));h.observe(1,clock,metrics,Map.of("stock",2.0));
        Path world=temp.resolve("valid-city"),file=temp.resolve("history");Files.writeString(world,"Valid city bytes");h.save(file,world);
        var bytes=Files.readAllBytes(file);bytes[bytes.length-1]^=1;Files.write(file,bytes);
        assertTrue(CityHistory.load(file,world,clock,1).catalog().isEmpty());assertEquals("Valid city bytes",Files.readString(world));
        try(var oversized=new RandomAccessFile(file.toFile(),"rw")){oversized.setLength(CityHistory.MAX_BYTES+1);}
        assertTrue(CityHistory.load(file,world,clock,1).catalog().isEmpty());assertEquals("Valid city bytes",Files.readString(world));
    }

    @Test void eventFlowCoverageNeverBridgesMissedSamples() {
        var h=new CityHistory();h.events=true;
        var metrics=List.of(metric("revenue","$",CityHistory.Kind.COUNTER));
        h.observe(0,clock,metrics,Map.of("revenue",0.0));
        h.event("revenue",.5,10,clock);
        h.observe(1,clock,metrics,Map.of("revenue",10.0));
        h.observe(24,clock,metrics,Map.of("revenue",10.0));
        var hourly=points(h,"revenue",1,24);
        assertEquals(2,hourly.size());
        assertEquals(1,hourly.get(0).coverage());
        assertEquals(0,hourly.get(1).coverage());
        assertTrue(hourly.stream().allMatch(p->p.gap()&&p.partial()&&Double.isNaN(p.value())));
        assertEquals(1,points(h,"revenue",2,24).get(0).coverage());
        assertTrue(points(h,"revenue",2,24).get(0).gap());
    }

}
