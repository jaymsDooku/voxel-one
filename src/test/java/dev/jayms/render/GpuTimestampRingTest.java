package dev.jayms.render;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GpuTimestampRingTest {
    static final class Fake implements GpuTimestampRing.Queries {
        int next, stamps, reads, deletes;
        long clock;
        final Map<Integer, Long> times = new HashMap<>();
        final Set<Integer> ready = new HashSet<>();
        public int create(){return ++next;}
        public void stamp(int id){times.put(id, clock);ready.remove(id);stamps++;}
        public boolean available(int id){return ready.contains(id);}
        public long result(int id){assertTrue(ready.contains(id),"Must never read unavailable query");reads++;return times.get(id);}
        public void delete(int id){deletes++;}
    }
    @Test void busyGpuKeepsMemoryBoundedAndSkipsWithoutReading() {
        Fake q = new Fake();
        try (var ring = new GpuTimestampRing(q)) {
            assertTrue(Float.isNaN(ring.milliseconds()));
            for(int i=0;i<100;i++){ring.begin();q.clock+=2_000_000;ring.end();}
            assertEquals(6,q.next);assertEquals(6,q.stamps);assertEquals(0,q.reads);
            assertEquals(0,ring.samples());
        }
        assertEquals(6,q.deletes);
    }
    @Test void waitsForBothTimestampsThenReusesSlot() {
        Fake q = new Fake();
        try(var ring = new GpuTimestampRing(q)) {
            ring.begin();q.clock=4_000_000;ring.end();
            q.ready.add(2);ring.begin();ring.end();
            assertEquals(0,q.reads);assertTrue(Float.isNaN(ring.milliseconds()));
            q.ready.add(1);ring.begin();ring.end();
            assertEquals(4,ring.milliseconds());assertEquals(1,ring.samples());assertEquals(2,q.reads);
            ring.begin();ring.end();assertEquals(8,q.stamps);
        }
    }
    @Test void nestedStageFailsWithoutOverwritingStart() {
        Fake q = new Fake();
        try(var ring = new GpuTimestampRing(q)) {
            ring.begin();assertThrows(IllegalStateException.class,ring::begin);
            assertEquals(1,q.stamps);ring.end();ring.end();assertEquals(2,q.stamps);
        }
    }
}
