package dev.jayms;

import dev.jayms.net.model.SparseVoxelOctree;
import java.io.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PackedCellRotationTest {
    static byte[] bytes(SparseVoxelOctree tree)throws IOException{return SparseVoxelPackedCellTest.bytes(tree);}
    static SparseVoxelOctree read(byte[] wire)throws IOException{return SparseVoxelOctree.read(new DataInputStream(new ByteArrayInputStream(wire)),16);}
    static void check(SparseVoxelOctree tree)throws Exception {
        byte[] before=bytes(tree);var packed=SparseVoxelOctree.readPackedCell(before);
        for(int r=0;r<4;r++) {
            var expected=tree.rotatedY(r);var actual=packed.rotatedY(r);
            assertArrayEquals(bytes(expected),bytes(actual));assertEquals(expected.nodes(),actual.nodes());assertEquals(expected.leafCount(),actual.leafCount());assertEquals(expected.occupied(),actual.occupied());
            assertTrue(actual.isFrozen());assertTrue(expected.sameValues(actual));
            // Opaque white is also -1; use topology rather than the query sentinel.
            if(expected.nodes()>1)assertTrue(SparseVoxelPackedCellTest.packedWithoutGraph(actual));
            for(int i=0;i<4096;i++) {
                int x=i%16,y=i/16%16,z=i/256,rx=switch(r){case 1->15-z;case 2->15-x;case 3->z;default->x;},rz=switch(r){case 1->x;case 2->15-z;case 3->15-x;default->z;};
                assertEquals(tree.get(x,y,z),actual.get(rx,y,rz));
            }
            var copy=actual.copy();copy.fill(0,0,0,16,16,16,0);assertArrayEquals(bytes(expected),bytes(actual));
        }
        var graphCycle=tree;var packedCycle=packed;for(int i=0;i<4;i++){graphCycle=graphCycle.rotatedY(1);packedCycle=packedCycle.rotatedY(1);}
        assertArrayEquals(bytes(graphCycle),bytes(packedCycle));assertArrayEquals(before,bytes(packed));
    }
    @Test void sparseRandomAndDenseCellsMatchGraphValuesBytesAndCounters()throws Exception {
        var random=new Random(982731);
        for(int n=0;n<32;n++) {
            var tree=new SparseVoxelOctree(16);
            for(int i=0;i<128;i++) {
                int side=1<<random.nextInt(4),x=random.nextInt(16/side)*side,y=random.nextInt(16/side)*side,z=random.nextInt(16/side)*side;
                tree.fill(x,y,z,x+side,y+side,z+side,i%3==0?0:(i%2==0?0xff000000:0xfe000000)|random.nextInt(1<<24));
            }
            check(tree);
        }
        var dense=new SparseVoxelOctree(16);for(int i=0;i<4096;i++)dense.fill(i%16,i/16%16,i/256,i%16+1,i/16%16+1,i/256+1,0xfe000000|i);check(dense);
    }
    static void legacy(DataOutputStream out,int side,Random random,int uniform)throws IOException {
        if(side==1||random.nextInt(3)==0){out.writeByte(uniform==0?0:1);if(uniform!=0)out.writeInt(uniform);return;}
        out.writeByte(2);for(int i=0;i<8;i++)legacy(out,side/2,random,uniform);
    }
    @Test void redundantAirWhiteAndWorldBranchesFoldExactlyLikeExistingRotations()throws Exception {
        var random=new Random(83276);
        for(int color:new int[]{0,0xffffffff,0xff123456,0xfe123456})for(int n=0;n<8;n++) {
            var buffer=new ByteArrayOutputStream();var out=new DataOutputStream(buffer);out.writeByte(16);out.writeByte(2);
            for(int i=0;i<8;i++)legacy(out,8,random,color);check(read(buffer.toByteArray()));
        }
        for(int color:new int[]{0,0xffffffff,0xfe123456}){var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,color);check(tree);}
    }
    @Test void packedModelFallbackRetainsAllSupportedModelSizes()throws Exception {
        for(int size:new int[]{8,16,32}) {
            var tree=new SparseVoxelOctree(size);for(int i=0;i<size*size*size;i++)tree.set(i%size,i/size%size,i/(size*size),0xff000000|i);
            var packed=tree.packedModelCopy();for(int r=0;r<4;r++)assertArrayEquals(bytes(tree.rotatedY(r)),bytes(packed.rotatedY(r)));
        }
    }
    @Test void invalidTurnsCancellationAndCallerEditsLeavePackedSourceIntact()throws Exception {
        var tree=SparseVoxelPackedCellTest.fixture();var packed=tree.packedCellCopy();byte[] before=bytes(packed);
        for(int r:new int[]{-1,4,Integer.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->packed.rotatedY(r));
        Thread.currentThread().interrupt();try{for(int r=0;r<4;r++){int turn=r;assertThrows(CancellationException.class,()->packed.rotatedY(turn));}}finally{Thread.interrupted();}
        tree.fill(0,0,0,16,16,16,0);assertArrayEquals(before,bytes(packed));
        var rotated=packed.rotatedY(1);assertThrows(IllegalStateException.class,()->rotated.set(0,0,0,0xffffffff));assertArrayEquals(before,bytes(packed));
    }
}
