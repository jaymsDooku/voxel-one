package dev.jayms.net;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PersistentOrderedMapTest {
    private static <V> void equal(Map<String,V> expected,PersistentOrderedMap<V> actual) {
        actual.validate();assertEquals(expected,actual);assertEquals(actual,expected);
        assertEquals(expected.hashCode(),actual.hashCode());
        assertEquals(new ArrayList<>(expected.entrySet()),new ArrayList<>(actual.entrySet()));
    }
    @Test void mixedUpdatesMatchInsertionOrderAndRetainIndependentRoots() {
        var random=new Random(5381);var actual=new PersistentOrderedMap<Integer>();var expected=new LinkedHashMap<String,Integer>();
        var held=new ArrayList<PersistentOrderedMap<Integer>>();var oracles=new ArrayList<LinkedHashMap<String,Integer>>();
        for(int i=0;i<25000;i++) {
            String key="cell:"+random.nextInt(2048);int value=random.nextInt();
            switch(random.nextInt(5)) {
                case 0->assertEquals(expected.remove(key),actual.remove(key));
                case 1->{assertEquals(expected.get(key),actual.get(key));assertEquals(expected.containsKey(key),actual.containsKey(key));}
                default->assertEquals(expected.put(key,value),actual.put(key,value));
            }
            if(i%3000==0){equal(expected,actual);held.add(new PersistentOrderedMap<>(actual));oracles.add(new LinkedHashMap<>(expected));}
        }
        equal(expected,actual);
        for(int i=0;i<held.size();i++) {
            var copy=held.get(i);equal(oracles.get(i),copy);copy.put("held-only",i);assertFalse(actual.containsKey("held-only"));
            actual.put("source-only",i);assertFalse(copy.containsKey("source-only"));
        }
        actual.clear();assertEquals(0,actual.size());actual.put("new",1);equal(Map.of("new",1),actual);
    }
    @Test void sortedInsertionRemovalReplacementAndReinsertHaveExactOrder() {
        var actual=new PersistentOrderedMap<Integer>();var expected=new LinkedHashMap<String,Integer>();
        for(int i=0;i<8192;i++){String key=String.format(Locale.ROOT,"%05d",i);actual.put(key,i);expected.put(key,i);}
        var held=new PersistentOrderedMap<>(actual);var old=new LinkedHashMap<>(expected);
        for(int i=8191;i>=0;i-=2){String key=String.format(Locale.ROOT,"%05d",i);assertEquals(expected.remove(key),actual.remove(key));}
        for(int i=0;i<8192;i+=3){String key=String.format(Locale.ROOT,"%05d",i);assertEquals(expected.put(key,-i),actual.put(key,-i));}
        equal(expected,actual);equal(old,held);
        for(var key:new ArrayList<>(expected.keySet())){assertEquals(expected.remove(key),actual.remove(key));if(actual.size()%128==0)actual.validate();}
        equal(expected,actual);
    }
    @Test void fullStringHashCollisionsKeepBalancedExactKeysAndOldSnapshots() {
        var map=new PersistentOrderedMap<Integer>();var expected=new LinkedHashMap<String,Integer>();var hashes=new HashSet<Integer>();
        for(int i=0;i<1024;i++) {
            var key=new StringBuilder();for(int bit=0;bit<10;bit++)key.append((i&(1<<bit))==0?"Aa":"BB");
            String s=key.toString();hashes.add(s.hashCode());map.put(s,i);expected.put(s,i);
        }
        assertEquals(1,hashes.size());equal(expected,map);var held=new PersistentOrderedMap<>(map);var original=new LinkedHashMap<>(expected);
        var keys=new ArrayList<>(expected.keySet());
        for(int i=0;i<keys.size();i++) {
            String key=keys.get(i);if(i%2==0)assertEquals(expected.remove(key),map.remove(key));else assertEquals(expected.put(key,-i),map.put(key,-i));
        }
        equal(expected,map);equal(original,held);
        for(var key:new ArrayList<>(expected.keySet()))map.remove(key);
        map.validate();assertTrue(map.isEmpty());equal(original,held);
    }
    @Test void viewsAreReadOnlyDynamicAndIteratorsAreInvalidatedOnlyByTheirOwnMap() {
        var map=new PersistentOrderedMap<Integer>();var view=map.entrySet();map.put("Aa",1);map.put("BB",2);
        assertEquals(2,view.size());assertTrue(view.contains(Map.entry("Aa",1)));
        var entry=view.iterator().next();assertEquals(Map.entry("Aa",1),entry);assertEquals(Map.entry("Aa",1).hashCode(),entry.hashCode());
        assertThrows(UnsupportedOperationException.class,()->entry.setValue(4));assertThrows(UnsupportedOperationException.class,()->view.clear());
        var iterator=map.entrySet().iterator();var held=new PersistentOrderedMap<>(map);map.put("Aa",3);
        assertThrows(ConcurrentModificationException.class,iterator::hasNext);assertEquals(1,held.get("Aa"));
        var stable=held.entrySet().iterator();map.clear();assertTrue(stable.hasNext());assertEquals("Aa",stable.next().getKey());
        assertNull(map.get(null));assertNull(map.remove(new Object()));assertFalse(map.containsKey(1));
        assertThrows(NullPointerException.class,()->map.put(null,1));assertThrows(NullPointerException.class,()->map.put("x",null));assertTrue(map.isEmpty());
    }
    @Test void snapshotWorkerEditsLeaveSourceOrderAndValuesUntouched()throws Exception {
        var source=new PersistentOrderedMap<Integer>();var expected=new LinkedHashMap<String,Integer>();
        for(int i=0;i<4096;i++){source.put("cell"+i,i);expected.put("cell"+i,i);}
        var snapshot=new PersistentOrderedMap<>(source);var worker=Executors.newSingleThreadExecutor();
        try {
            var changed=worker.submit(()->{for(int i=0;i<4096;i++){snapshot.remove("cell"+i);snapshot.put("cell"+i,-i);}snapshot.validate();return snapshot;}).get(10,TimeUnit.SECONDS);
            equal(expected,source);assertEquals(-4095,changed.get("cell4095"));source.clear();assertEquals(4096,changed.size());
        }finally{worker.shutdownNow();assertTrue(worker.awaitTermination(10,TimeUnit.SECONDS));}
    }
}
