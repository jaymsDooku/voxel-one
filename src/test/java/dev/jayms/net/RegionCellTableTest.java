package dev.jayms.net;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegionCellTableTest {
    @Test void sparseDenseGrowthAndReplacementMatchMapAcrossAllLocalKeys() {
        var actual=new RegionCellTable<Integer>();var expected=new HashMap<Integer,Integer>();
        var order=new ArrayList<Integer>();for(int i=0;i<4096;i++)order.add(i);
        Collections.shuffle(order,new Random(20261109));
        for(int key:order) {
            assertEquals(expected.put(key,key+1),actual.put(key,Integer.valueOf(key+1)));
            assertEquals(expected.size(),actual.size());assertEquals(expected.get(key),actual.get(key));
            if(actual.size()%127==0)assertEquals(expected,actual);
        }
        assertEquals(expected,actual);assertEquals(4096,actual.size());
        for(int key:order)assertEquals(expected.put(key,-key),actual.put(key,Integer.valueOf(-key)));
        assertEquals(expected,actual);assertTrue(actual.anyMatch(v->v==-4095));assertFalse(actual.anyMatch(v->v>0));
    }
    @Test void copiesDetachSparseAndDenseArraysButRetainImmutableValueIdentity() {
        for(int count:new int[]{2,127,1024,1025,4096}) {
            var source=new RegionCellTable<Object>();
            for(int i=0;i<count;i++)source.put(i,new Object());
            var before=new HashMap<>(source);var copy=new RegionCellTable<>(source);
            for(int i=0;i<count;i++)assertSame(source.get(i),copy.get(i));
            copy.put(0,new Object());assertSame(before.get(0),source.get(0));
            source.put(count-1,new Object());assertSame(before.get(count-1),copy.get(count-1));
            if(count<4096){copy.put(4095,new Object());assertNull(source.get(4095));}
        }
    }
    @Test void adversarialSparseProbesAndBoundaryKeysHaveExactLookup() {
        var table=new RegionCellTable<Integer>();
        for(int i=0;i<4096;i+=128)table.put(i,Integer.valueOf(i+1));
        table.put(4095,Integer.valueOf(12));
        for(int i=0;i<4096;i++)assertEquals(i==4095?Integer.valueOf(12):i%128==0?Integer.valueOf(i+1):null,table.get(i));
        assertNull(table.get(-1));assertNull(table.get(4096));assertNull(table.get("0"));assertNull(table.get(null));
        int size=table.size();assertThrows(IllegalArgumentException.class,()->table.put(4096,Integer.valueOf(1)));
        assertThrows(IllegalArgumentException.class,()->table.put(-1,Integer.valueOf(1)));
        assertThrows(NullPointerException.class,()->table.put(3,null));assertEquals(size,table.size());
    }
    @Test void entryViewsAreReadOnlyAndOnlyTheirOwnTableInvalidatesIteration() {
        var table=new RegionCellTable<String>();table.put(0,"a");table.put(4095,"b");
        var view=table.entrySet();var iterator=view.iterator();var copy=new RegionCellTable<>(table);
        copy.put(0,"copy");assertTrue(iterator.hasNext());
        assertThrows(UnsupportedOperationException.class,()->view.clear());
        assertThrows(UnsupportedOperationException.class,()->view.iterator().next().setValue("bad"));
        table.put(0,"new");assertThrows(ConcurrentModificationException.class,iterator::hasNext);
        assertEquals(2,view.size());assertEquals(Map.of(0,"new",4095,"b").entrySet(),view);
    }
}
