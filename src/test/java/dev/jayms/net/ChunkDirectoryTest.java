package dev.jayms.net;

import dev.jayms.ChunkPos;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChunkDirectoryTest {
    private static <V> void equal(ChunkDirectory<V> actual,Map<ChunkPos,V> expected) {
        actual.validate();var copy=new HashMap<ChunkPos,V>();for(var e:actual){assertNull(copy.put(e.getKey(),e.getValue()));assertSame(e.getValue(),actual.get(e.getKey()));assertSame(e.getValue(),actual.get(e.getKey().chunkX(),e.getKey().chunkY(),e.getKey().chunkZ()));}
        assertEquals(expected,copy);assertEquals(expected.size(),actual.size());assertTrue(actual.trieDepth()<=7);
    }
    @Test void mixedEditsAndOldRootsMatchIndependentMap() {
        var root=new ChunkDirectory<Integer>(8192);var expected=new HashMap<ChunkPos,Integer>();var random=new Random(19342);
        var roots=new ArrayDeque<ChunkDirectory<Integer>>();var copies=new ArrayDeque<Map<ChunkPos,Integer>>();
        for(int i=0;i<25000;i++) {
            var key=new ChunkPos(random.nextInt(61)-30,random.nextInt(3)-1,random.nextInt(61)-30);
            if(random.nextBoolean()){Integer v=random.nextInt();root=root.with(key,v);expected.put(key,v);}else{root=root.without(key);expected.remove(key);}
            if(i%251==0){if(roots.size()==8)equal(roots.remove(),copies.remove());roots.add(root);copies.add(new HashMap<>(expected));}
            if(i%127==0)equal(root,expected);
        }
        equal(root,expected);while(!roots.isEmpty())equal(roots.remove(),copies.remove());
    }
    @Test void ordinarySignedGridAvoidsMirroredCoordinateCollisionClusters() {
        var root=new ChunkDirectory<Integer>(16384);var expected=new HashMap<ChunkPos,Integer>();
        for(int z=-32;z<32;z++)for(int x=-64;x<64;x++){var k=new ChunkPos(x,8,z);Integer value=expected.size();root=root.with(k,value);expected.put(k,value);}
        equal(root,expected);assertTrue(root.collisionEntries()<=16,"Signed grid hash distribution regressed");
    }
    @Test void fullHashCollisionsUseIndependentBalancedFallback() {
        var root=new ChunkDirectory<Integer>(4096,k->0);var expected=new HashMap<ChunkPos,Integer>();
        for(int i=0;i<2048;i++){var key=new ChunkPos(i-1024,i%3,-i);Integer v=i;root=root.with(key,v);expected.put(key,v);}
        equal(root,expected);var held=root;var original=new HashMap<>(expected);
        for(var key:new ArrayList<>(expected.keySet())){root=root.without(key);expected.remove(key);if(expected.size()%127==0)equal(root,expected);}
        equal(root,Map.of());equal(held,original);assertEquals(0,held.trieDepth());
    }
    @Test void signBitDeepRoutesAndBranchCollapseRemainExact() {
        var root=new ChunkDirectory<Integer>(4096,ChunkPos::chunkX);var expected=new HashMap<ChunkPos,Integer>();
        for(int h:new int[]{0,31,32,63,1023,1024,1<<30,Integer.MIN_VALUE,-1,Integer.MAX_VALUE}){var key=new ChunkPos(h,2,3);Integer v=h;root=root.with(key,v);expected.put(key,v);}
        equal(root,expected);assertEquals(7,root.trieDepth());var held=root;
        for(var key:new ArrayList<>(expected.keySet())){root=root.without(key);expected.remove(key);equal(root,expected);}held.validate();
        root=new ChunkDirectory<>(4096,ChunkPos::chunkX);for(int h:new int[]{0,32,64,1})root=root.with(new ChunkPos(h,0,0),h);
        root=root.without(new ChunkPos(1,0,0));assertEquals(Integer.valueOf(32),root.get(new ChunkPos(32,0,0)));root.validate();
        root=root.without(new ChunkPos(64,0,0)).without(new ChunkPos(32,0,0));assertEquals(0,root.trieDepth());assertEquals(Integer.valueOf(0),root.get(new ChunkPos(0,0,0)));
        var mixed=new ChunkDirectory<Integer>(4096,k->k.chunkY()==0?0:Integer.MIN_VALUE);expected.clear();
        for(int i=0;i<128;i++)for(int y=0;y<2;y++){var key=new ChunkPos(i,y,-i);Integer v=i+y;mixed=mixed.with(key,v);expected.put(key,v);}equal(mixed,expected);assertEquals(7,mixed.trieDepth());
        for(var key:new ArrayList<>(expected.keySet()))if(key.chunkY()==0){mixed=mixed.without(key);expected.remove(key);}equal(mixed,expected);assertEquals(0,mixed.trieDepth());
    }
    @Test void capacityNoOpsAndReadOnlyIterationDoNotChangePriorRoots() {
        assertThrows(IllegalArgumentException.class,()->new ChunkDirectory<>(0));assertThrows(NullPointerException.class,()->new ChunkDirectory<Integer>(1,null));
        var empty=new ChunkDirectory<Integer>(Integer.MAX_VALUE);assertSame(empty,empty.cleared());assertSame(empty,empty.without(new ChunkPos(1,2,3)));assertThrows(NoSuchElementException.class,()->empty.iterator().next());
        assertThrows(NullPointerException.class,()->empty.get(null));assertThrows(NullPointerException.class,()->empty.with(null,1));assertThrows(NullPointerException.class,()->empty.with(new ChunkPos(0,0,0),null));assertThrows(NullPointerException.class,()->empty.without(null));
        var a=new ChunkPos(Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MIN_VALUE);var b=new ChunkPos(Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE);
        var full=new ChunkDirectory<Integer>(2).with(a,1).with(b,2);assertSame(full,full.with(a,full.get(a)));assertThrows(IllegalArgumentException.class,()->full.with(new ChunkPos(0,0,0),3));
        var iterator=full.iterator();var changed=full.with(a,7).cleared();assertEquals(0,changed.size());var copy=new HashMap<ChunkPos,Integer>();while(iterator.hasNext()){var e=iterator.next();copy.put(e.getKey(),e.getValue());assertThrows(UnsupportedOperationException.class,()->e.setValue(8));}
        assertThrows(UnsupportedOperationException.class,iterator::remove);equal(full,Map.of(a,1,b,2));assertEquals(Map.of(a,1,b,2),copy);
    }
    @Test void frozenRootCanBeTransferredToWorkerWithoutSharingFutureEdits()throws Exception {
        var source=new ChunkDirectory<Integer>(4096);for(int i=0;i<1024;i++)source=source.with(new ChunkPos(i,0,-i),i);var held=source;
        var worker=Executors.newSingleThreadExecutor();try {
            var changed=worker.submit(()->{var result=held;for(int i=0;i<1024;i++)result=result.with(new ChunkPos(i,0,-i),-i);result.validate();return result;}).get(10,TimeUnit.SECONDS);
            for(int i=0;i<1024;i++){assertEquals(Integer.valueOf(i),held.get(new ChunkPos(i,0,-i)));assertEquals(Integer.valueOf(-i),changed.get(new ChunkPos(i,0,-i)));}
        }finally{worker.shutdownNow();assertTrue(worker.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void frozenWorldQueriesShareOnlyAtomicCacheEntriesAcrossWorkers()throws Exception {
        var source=new WorldVoxels(new Terrain(42));int[] xs={-17,16,48,80},zs={-1,0,32,48};int[] types={Blocks.STONE,Blocks.BRICKS,Blocks.GLASS,0};
        for(int i=0;i<3;i++)source.apply(new Protocol.Edit(xs[i],128,zs[i],types[i]));var frozen=source.snapshot();var start=new CountDownLatch(1);var workers=Executors.newFixedThreadPool(4);var futures=new ArrayList<Future<?>>();
        try {
            for(int t=0;t<4;t++){int id=t;futures.add(workers.submit(()->{start.await();for(int i=0;i<50000;i++){int step=(i&1024)==0?i:i/16;int k=(step*31+id)&3;if(frozen.type(xs[k],128,zs[k])!=types[k])throw new AssertionError("Torn cache entry");}return null;}));}
            source.apply(new Protocol.Edit(xs[1],128,zs[1],Blocks.LED));start.countDown();for(var future:futures)future.get(10,TimeUnit.SECONDS);
            assertEquals(Blocks.LED,source.type(xs[1],128,zs[1]));assertEquals(Blocks.BRICKS,frozen.type(xs[1],128,zs[1]));
        }finally{start.countDown();workers.shutdownNow();assertTrue(workers.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void repeatedReadsDistinguishEveryCoordinateAndInvalidateMissingBuckets() {
        var source=new WorldVoxels(new Terrain(42));
        int[][] cells={{-17,128,-1},{-1,128,-1},{-17,144,-1},{-17,128,15},{80,128,80}};
        int[] types={Blocks.STONE,Blocks.BRICKS,Blocks.GLASS,Blocks.LED,0};
        for(int i=0;i<4;i++)source.apply(new Protocol.Edit(cells[i][0],cells[i][1],cells[i][2],types[i]));
        for(int pass=0;pass<4;pass++)for(int i=0;i<cells.length;i++)for(int repeat=0;repeat<32;repeat++)
            assertEquals(types[i],source.type(cells[i][0],cells[i][1],cells[i][2]));
        var old=source.snapshot();source.apply(new Protocol.Edit(80,128,80,Blocks.BRICKS));
        for(int repeat=0;repeat<32;repeat++){assertEquals(Blocks.BRICKS,source.type(80,128,80));assertEquals(0,old.type(80,128,80));}
        source.clear();for(int repeat=0;repeat<32;repeat++)assertEquals(0,source.type(-17,128,-1));
    }
    @Test void worldLookupCacheTracksRootAndOwnedBucketChangesAcrossSnapshotsAndClear() {
        var source=new WorldVoxels(new Terrain(42));int x=-17,y=128,z=-1;var position=ChunkPos.fromBlock(x,y,z);
        assertEquals(0,source.type(x,y,z));source.apply(new Protocol.Edit(x,y,z,Blocks.STONE));assertEquals(Blocks.STONE,source.type(x,y,z));
        var old=source.snapshot();var oldStamp=old.chunkStamp(position);source.apply(new Protocol.Edit(x,y,z,Blocks.BRICKS));assertEquals(Blocks.BRICKS,source.type(x,y,z));assertEquals(Blocks.STONE,old.type(x,y,z));assertEquals(oldStamp,old.chunkStamp(position));
        source.apply(new Protocol.Edit(x+1,y,z,Blocks.GLASS));assertEquals(Blocks.GLASS,source.type(x+1,y,z));assertEquals(Blocks.BRICKS,source.type(x,y,z));assertEquals(0,old.type(x+1,y,z));
        var selected=source.snapshot(List.of(position));source.apply(new Protocol.Edit(1000,y,1000,Blocks.BRICKS));assertEquals(Blocks.BRICKS,source.type(1000,y,1000));assertEquals(0,selected.type(1000,y,1000));
        var retained=source.snapshot();source.clear();assertEquals(0,source.type(x,y,z));assertEquals(Blocks.BRICKS,retained.type(x,y,z));assertEquals(Blocks.GLASS,retained.type(x+1,y,z));
        source.apply(new Protocol.Edit(x,y,z,Blocks.LED).withColor(0x123456));assertEquals(Blocks.LED,source.type(x,y,z));assertEquals(Blocks.BRICKS,retained.type(x,y,z));assertEquals(Blocks.STONE,old.type(x,y,z));
    }
}
