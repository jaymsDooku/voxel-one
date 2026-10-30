package dev.jayms.net.structure;

import java.util.concurrent.CancellationException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureOrderTest {
    @Test void stableSpatialOrderMatchesReferenceAcrossAllKeyBits()throws Exception {
        var random=new Random(7891);int[] keys=new int[100000];
        for(int i=0;i<keys.length;i++)keys[i]=random.nextInt(1<<28);
        keys[0]=0;keys[1]=(1<<28)-1;keys[2]=0;keys[3]=keys[1];
        var expected=new Integer[keys.length];for(int i=0;i<keys.length;i++)expected[i]=i;
        Arrays.sort(expected,Comparator.comparingInt(i->keys[i]));
        int[] before=keys.clone(),actual=StructureOrder.indices(keys,keys.length,()->false);
        assertArrayEquals(before,keys);
        for(int i=0;i<actual.length;i++)assertEquals(expected[i].intValue(),actual[i]);
        assertEquals(0,StructureOrder.indices(new int[0],0,()->false).length);
    }
    @Test void cancellationDuringEveryRadixPhaseLeavesInputUntouched() {
        int[] keys=new int[8192];for(int i=0;i<keys.length;i++)keys[i]=keys.length-i;
        for(int at:new int[]{1,5,12,20,35,60,75,81}) {
            var calls=new AtomicInteger();int[] before=keys.clone();
            assertThrows(CancellationException.class,()->StructureOrder.indices(keys,keys.length,()->calls.incrementAndGet()>=at));
            assertArrayEquals(before,keys);
        }
    }
    @Test void invalidKeysCountsAndInterruptedWorkerAreRejected() {
        assertThrows(IllegalArgumentException.class,()->StructureOrder.indices(new int[]{-1},1,()->false));
        assertThrows(IllegalArgumentException.class,()->StructureOrder.indices(new int[]{1<<28},1,()->false));
        assertThrows(IllegalArgumentException.class,()->StructureOrder.indices(new int[1],2,()->false));
        Thread.currentThread().interrupt();
        try {assertThrows(CancellationException.class,()->StructureOrder.indices(new int[]{1},1,()->false));}
        finally {Thread.interrupted();}
    }
}
