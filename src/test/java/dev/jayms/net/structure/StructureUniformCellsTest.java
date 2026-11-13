package dev.jayms.net.structure;

import dev.jayms.net.model.SparseVoxelOctree;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureUniformCellsTest {
    static SparseVoxelOctree tree(int color){var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,0xfe000000|(color&0xffffff));return tree.freeze();}
    @Test void growingPrimitiveCacheMatchesMapForAllKeyBitsAndPreservesReferences() {
        int maximum=20_000;var cache=new StructureUniformCells(maximum,()->false);var reference=new HashMap<Integer,SparseVoxelOctree>();
        var random=new Random(7352461);int[] special={0,-1,Integer.MIN_VALUE,Integer.MAX_VALUE};
        while(reference.size()<maximum) {
            int key=reference.size()<special.length?special[reference.size()]:random.nextInt();
            if(reference.containsKey(key))continue;
            var value=tree(reference.size());reference.put(key,value);cache.put(key,value);assertSame(value,cache.get(key));
        }
        var replacement=tree(0x123456);cache.put(0,replacement);reference.put(0,replacement);
        for(var entry:reference.entrySet())assertSame(entry.getValue(),cache.get(entry.getKey()));
        int absent=123;while(reference.containsKey(absent))absent++;int missing=absent;
        assertNull(cache.get(missing));assertThrows(IllegalStateException.class,()->cache.put(missing,tree(7)));
        assertNull(cache.get(missing));for(var entry:reference.entrySet())assertSame(entry.getValue(),cache.get(entry.getKey()));
    }
    @Test void zeroAndInvalidBudgetsRejectBeforeInsertion() {
        var empty=new StructureUniformCells(0,()->false);assertNull(empty.get(0));
        assertThrows(IllegalStateException.class,()->empty.put(0,tree(0)));
        assertThrows(IllegalArgumentException.class,()->new StructureUniformCells(-1,()->false));
        assertThrows(IllegalArgumentException.class,()->new StructureUniformCells(StructurePackage.MAX_LEAVES+1,()->false));
        assertThrows(NullPointerException.class,()->new StructureUniformCells(1,null));
        assertThrows(NullPointerException.class,()->new StructureUniformCells(1,()->false).put(0,null));
    }
    @Test void interruptedGrowthLeavesOldTableIntactAndCanRetry() {
        var armed=new AtomicBoolean();var checks=new AtomicInteger();
        var cache=new StructureUniformCells(4096,()->armed.get()&&checks.incrementAndGet()>=2);
        var expected=new ArrayList<SparseVoxelOctree>();for(int i=0;i<1024;i++){var value=tree(i);expected.add(value);cache.put(i,value);}
        armed.set(true);assertThrows(CancellationException.class,()->cache.put(1024,tree(1024)));
        armed.set(false);assertNull(cache.get(1024));for(int i=0;i<1024;i++)assertSame(expected.get(i),cache.get(i));
        var next=tree(1024);cache.put(1024,next);assertSame(next,cache.get(1024));for(int i=0;i<1024;i++)assertSame(expected.get(i),cache.get(i));
    }
}
