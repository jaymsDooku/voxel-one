package dev.jayms;

import dev.jayms.net.model.SparseVoxelOctree;
import dev.jayms.net.structure.StructureEngine;
import java.io.*;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureRotationTreeTest {
    private static SparseVoxelOctree reference(SparseVoxelOctree source,int rotation) {
        int size=source.size();
        if(rotation==0||source.uniform(0,0,0,size)!=-1)return source.copy().freeze();
        var result=new SparseVoxelOctree(size);
        for(var leaf:source.leaves()) {
            int x=leaf.x(),z=leaf.z(),side=leaf.side();
            int rx=switch(rotation){case 1->size-z-side;case 2->size-x-side;case 3->z;default->x;};
            int rz=switch(rotation){case 1->x;case 2->size-z-side;case 3->size-x-side;default->z;};
            result.fill(rx,leaf.y(),rz,rx+side,leaf.y()+side,rz+side,leaf.color());
        }
        return result.freeze();
    }
    private static byte[] bytes(SparseVoxelOctree tree)throws IOException {
        var out=new ByteArrayOutputStream();tree.write(new DataOutputStream(out));return out.toByteArray();
    }
    @Test void allRotationsMatchLeafFillBytesAndCoordinateReference()throws Exception {
        var random=new Random(712763);
        for(int size:new int[]{1,8,16,32,256}) {
            var tree=new SparseVoxelOctree(size);
            tree.fill(0,0,0,size,size,size,0xff123456);
            for(int i=0;i<400;i++) {
                int x=random.nextInt(size),y=random.nextInt(size),z=random.nextInt(size);
                tree.fill(x,y,z,x+1,y+1,z+1,i%3==0?0:(i%2==0?0xfe000000:0xff000000)|random.nextInt(1<<24));
            }
            byte[] before=bytes(tree);
            for(int rotation=0;rotation<4;rotation++) {
                var rotated=StructureEngine.rotate(tree,rotation);
                assertArrayEquals(bytes(reference(tree,rotation)),bytes(rotated));
                for(int i=0;i<200;i++) {
                    int x=random.nextInt(size),y=random.nextInt(size),z=random.nextInt(size);
                    int rx=switch(rotation){case 1->size-1-z;case 2->size-1-x;case 3->z;default->x;};
                    int rz=switch(rotation){case 1->x;case 2->size-1-z;case 3->size-1-x;default->z;};
                    assertEquals(tree.get(x,y,z),rotated.get(rx,y,rz));
                }
                assertThrows(IllegalStateException.class,()->rotated.set(0,0,0,0xff000001));
            }
            var cycle=tree;for(int i=0;i<4;i++)cycle=StructureEngine.rotate(cycle,1);
            assertArrayEquals(before,bytes(cycle));assertArrayEquals(before,bytes(tree));
        }
    }
    @Test void legacyBranchesAirAndUniformTreesRetainExistingWireBehavior()throws Exception {
        for(boolean uniform:new boolean[]{false,true}) {
            var buffer=new ByteArrayOutputStream();var out=new DataOutputStream(buffer);
            out.writeByte(8);out.writeByte(2);
            for(int child=0;child<8;child++) {
                if(child==0) {
                    out.writeByte(2);for(int i=0;i<8;i++){out.writeByte(1);out.writeInt(0xff123456);}
                } else {out.writeByte(1);out.writeInt(uniform?0xff123456:0xff654321);}
            }
            var tree=SparseVoxelOctree.read(new DataInputStream(new ByteArrayInputStream(buffer.toByteArray()))).freeze();
            byte[] before=bytes(tree);
            for(int r=0;r<4;r++)assertArrayEquals(bytes(reference(tree,r)),bytes(tree.rotatedY(r)));
            assertArrayEquals(before,bytes(tree));
        }
        for(int r=0;r<4;r++)assertEquals(0,new SparseVoxelOctree(16).rotatedY(r).occupied());
        var source=new SparseVoxelOctree(16);source.set(1,2,3,0xff123456);
        var rotated=source.rotatedY(1);source.set(1,2,3,0xffabcdef);
        assertEquals(0xff123456,rotated.get(12,2,1));
    }
    @Test void invalidTurnsAndInterruptionLeaveSourceUnchanged()throws Exception {
        var tree=new SparseVoxelOctree(16);tree.set(1,2,3,0xff123456);byte[] before=bytes(tree);
        for(int r:new int[]{-1,4,Integer.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->tree.rotatedY(r));
        Thread.currentThread().interrupt();
        try {for(int r=0;r<4;r++){int turn=r;assertThrows(java.util.concurrent.CancellationException.class,()->tree.rotatedY(turn));}}
        finally {Thread.interrupted();}
        assertArrayEquals(before,bytes(tree));assertEquals(1,tree.rotatedY(1).occupied());
    }
}
