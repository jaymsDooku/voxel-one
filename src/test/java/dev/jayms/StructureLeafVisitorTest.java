package dev.jayms;

import dev.jayms.net.model.SparseVoxelOctree;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureLeafVisitorTest {
    @Test void visitorMatchesListCoordinatesOrderAndStoredLeafCount()throws Exception {
        var random=new Random(7623);
        for(int size:new int[]{1,8,16,32,256}) {
            var tree=new SparseVoxelOctree(size);
            var empty=new ArrayList<SparseVoxelOctree.Leaf>();
            tree.forEachLeaf((x,y,z,side,color)->empty.add(new SparseVoxelOctree.Leaf(x,y,z,side,color)));
            assertTrue(empty.isEmpty());
            tree.fill(0,0,0,size,size,size,0xff123456);
            for(int i=0;i<200;i++)tree.set(random.nextInt(size),random.nextInt(size),random.nextInt(size),i%3==0?0:0xff000000|random.nextInt(1<<24));
            tree.freeze();var visited=new ArrayList<SparseVoxelOctree.Leaf>();
            tree.forEachLeaf((x,y,z,side,color)->visited.add(new SparseVoxelOctree.Leaf(x,y,z,side,color)));
            assertEquals(tree.leaves(),visited);assertEquals(tree.leafCount(),visited.size());
        }
    }
    @Test void checkedFailureStopsTraversalAndLeavesPersistentSnapshotUnchanged()throws Exception {
        var tree=new SparseVoxelOctree(16);
        for(int x=0;x<16;x+=2)tree.set(x,0,0,0xff123456);
        tree.freeze();var before=tree.leaves();var failure=new IOException("stop validation");int[] calls={0};
        assertSame(failure,assertThrows(IOException.class,()->tree.forEachLeaf((x,y,z,side,color)->{if(++calls[0]==3)throw failure;})));
        assertEquals(3,calls[0]);assertEquals(before,tree.leaves());
        assertThrows(NullPointerException.class,()->new SparseVoxelOctree(16).forEachLeaf(null));
        calls[0]=0;tree.forEachLeaf((x,y,z,side,color)->calls[0]++);assertEquals(before.size(),calls[0]);
    }
}
