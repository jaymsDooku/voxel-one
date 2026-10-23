package dev.jayms.net;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServerTickCadenceTest {
    @Test void normalWorkKeepsDeadlinesAndSlowWorkSkipsMissedSlots() {
        var cadence=new ServerTickCadence(0);
        assertEquals(45_000_000,cadence.delayAfterTick(55_000_000));
        assertEquals(46_000_000,cadence.delayAfterTick(104_000_000));
        assertEquals(30_000_000,cadence.delayAfterTick(370_000_000));
        assertEquals(49_000_000,cadence.delayAfterTick(401_000_000));
    }
    @Test void nearDeadlineCompletionStillLeavesCommandWindow() {
        var cadence=new ServerTickCadence(0);
        assertEquals(50_500_000,cadence.delayAfterTick(99_500_000));
        assertEquals(50_000_000,cadence.delayAfterTick(150_000_000));
        for(int i=0;i<100;i++)assertTrue(cadence.delayAfterTick(10_000_000_000L+i*1_000_000_000L)>=ServerTickCadence.MIN_GAP);
    }
    @Test void itemAndBroadcastRatesAreIndependentOfTwentyHzTicksAndNeverCatchUp() {
        var cadence=new ServerTickCadence(0);int items=0,broadcast=0;
        for(long now=50_000_000;now<=1_000_000_000;now+=50_000_000){if(cadence.itemsDue(now))items++;if(cadence.broadcastDue(now))broadcast++;}
        assertEquals(10,items);assertEquals(5,broadcast);
        assertTrue(cadence.itemsDue(5_001_000_000L));assertFalse(cadence.itemsDue(5_001_000_000L));
        assertTrue(cadence.broadcastDue(5_001_000_000L));assertFalse(cadence.broadcastDue(5_001_000_000L));
        assertFalse(cadence.itemsDue(5_099_999_999L));assertTrue(cadence.itemsDue(5_100_000_000L));
        assertFalse(cadence.broadcastDue(5_199_999_999L));assertTrue(cadence.broadcastDue(5_200_000_000L));
    }
    @Test void deadlinesSurviveMonotonicClockWrapAndLongPauses() {
        long start=Long.MAX_VALUE-25_000_000;var cadence=new ServerTickCadence(start);
        assertEquals(45_000_000,cadence.delayAfterTick(start+55_000_000));
        assertFalse(cadence.itemsDue(start+99_999_999));assertTrue(cadence.itemsDue(start+100_000_000));
        assertTrue(cadence.broadcastDue(start+200_000_000));assertFalse(cadence.broadcastDue(start+200_000_000));
        long delay=cadence.delayAfterTick(start+86_400_000_000_000L);
        assertTrue(delay>=ServerTickCadence.MIN_GAP&&delay<=ServerTickCadence.PERIOD+ServerTickCadence.MIN_GAP);
    }
}
