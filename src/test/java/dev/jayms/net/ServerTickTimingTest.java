package dev.jayms.net;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServerTickTimingTest {
    @Test void separatesWaitWorkAndStartCadenceWithNearestRankPercentiles() {
        var timing=new ServerTickTiming();var empty=timing.snapshot();assertEquals(0,empty.windowTicks());assertEquals(0,empty.startsPerSecond());assertEquals(0,empty.work().samples());
        for(int i=0;i<20;i++){long started=i*50_000_000L;timing.record(started-(i+1)*1_000_000L,started,started+(i+1)*2_000_000L,i!=7);}
        var result=timing.snapshot();assertEquals(20,result.totalTicks());assertEquals(1,result.failedTicks());assertEquals(950,result.windowSpanMs());assertEquals(20,result.startsPerSecond(),.00001);
        assertEquals(19,result.intervals().samples());assertEquals(50,result.intervals().p95Ms());
        assertEquals(10,result.lockWait().medianMs());assertEquals(19,result.lockWait().p95Ms());assertEquals(20,result.lockWait().worstMs());
        assertEquals(20,result.work().medianMs());assertEquals(38,result.work().p95Ms());assertEquals(40,result.work().worstMs());
    }
    @Test void boundedWindowDropsOldSlowSamplesButRetainsLifetimeFailuresAndSnapshots() {
        var timing=new ServerTickTiming();timing.record(0,1_000_000_000,2_000_000_000,false);var old=timing.snapshot();
        for(int i=1;i<=ServerTickTiming.CAPACITY;i++){long started=3_000_000_000L+i*50_000_000L;timing.record(started,started,started+1_000_000,true);}
        var result=timing.snapshot();assertEquals(4096,result.windowTicks());assertEquals(4097,result.totalTicks());assertEquals(1,result.failedTicks());assertEquals(1,result.work().worstMs());assertEquals(0,result.lockWait().worstMs());assertEquals(20,result.startsPerSecond(),.00001);
        assertEquals(1000,old.work().worstMs());assertEquals(1,old.windowTicks());assertEquals(0,old.startsPerSecond());
    }
    @Test void monotonicClockWrapStillMeasuresElapsedDurations() {
        var timing=new ServerTickTiming();long started=Long.MAX_VALUE-1_000_000;timing.record(started-2_000_000,started,started+3_000_000,true);
        timing.record(started+50_000_000,started+50_000_000,started+51_000_000,true);var result=timing.snapshot();assertEquals(50,result.intervals().worstMs());assertEquals(3,result.work().worstMs());assertEquals(2,result.lockWait().worstMs());
    }
}
