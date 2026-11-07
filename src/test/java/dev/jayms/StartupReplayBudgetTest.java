package dev.jayms;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StartupReplayBudgetTest {
    @Test void recordCheckpointsDoNotForceAFramePerBatch() {
        var budget=new StartupReplayBudget(0);int polls=0,draws=0;
        // 32,768 cheap records in 256 batches: one first acknowledgement, not
        // 256 swaps. Frequent event polling and geometry throughput coexist.
        for(int i=1;i<=32768;i++)if(budget.afterEdit(i*100L)) {
            polls++;if(budget.drawDue(i*100L)){draws++;budget.frameFinished(i*100L);}
            budget.resumed(i*100L);
        }
        assertEquals(256,polls);assertEquals(1,draws);
    }
    @Test void timeCheckpointsAndRedrawCadenceRemainIndependent() {
        var budget=new StartupReplayBudget(100);
        assertFalse(budget.afterEdit(2_000_099));assertTrue(budget.afterEdit(2_000_100));
        assertTrue(budget.drawDue(2_000_100));budget.frameFinished(12_000_100);budget.resumed(12_000_100);
        assertFalse(budget.afterEdit(14_000_099));assertTrue(budget.afterEdit(14_000_100));
        assertFalse(budget.drawDue(28_666_766));assertTrue(budget.drawDue(28_666_767));
        // The interval begins after a slow draw, not before it.
        budget.frameFinished(100_000_000);assertFalse(budget.drawDue(116_666_666));assertTrue(budget.drawDue(116_666_667));
    }
    @Test void signedNanoTimeWrapDoesNotBreakShortIntervals() {
        long start=Long.MAX_VALUE-100;var budget=new StartupReplayBudget(start);
        assertFalse(budget.afterEdit(start+1));assertTrue(budget.afterEdit(start+2_000_000L));
        budget.frameFinished(start+2_000_000L);budget.resumed(start+2_000_000L);
        assertFalse(budget.drawDue(start+18_666_666L));assertTrue(budget.drawDue(start+18_666_667L));
    }
}
