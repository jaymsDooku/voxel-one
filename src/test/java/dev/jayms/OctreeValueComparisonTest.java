package dev.jayms;

import dev.jayms.net.model.SparseVoxelOctree;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class OctreeValueComparisonTest {
    private SparseVoxelOctree legacy(int depth,int color)throws IOException {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeByte(16);nodes(out,depth,color);
        return SparseVoxelOctree.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }
    private void nodes(DataOutputStream out,int depth,int color)throws IOException {
        if(depth==0){out.writeByte(color==0?0:1);if(color!=0)out.writeInt(color);return;}
        out.writeByte(2);for(int i=0;i<8;i++)nodes(out,depth-1,color);
    }
    private byte[] bytes(SparseVoxelOctree tree)throws IOException {
        var bytes=new ByteArrayOutputStream();tree.write(new DataOutputStream(bytes));return bytes.toByteArray();
    }
    @Test void redundantBranchesAndAirCompareInBothDirectionsWithoutMutation()throws Exception {
        for(int color:new int[]{0,0xff123456,0xfe345678})for(int depth=1;depth<=4;depth++) {
            var redundant=legacy(depth,color);var canonical=new SparseVoxelOctree(16);canonical.fill(0,0,0,16,16,16,color);
            byte[] before=bytes(redundant);redundant.freeze();canonical.freeze();
            assertTrue(redundant.sameValues(canonical));assertTrue(canonical.sameValues(redundant));
            assertArrayEquals(before,bytes(redundant));assertTrue(redundant.sameValues(redundant.copy()));
        }
    }
    @Test void mixedLegacyTreesDetectEveryChangedPositionAndResolution()throws Exception {
        var random=new Random(20261023);var a=legacy(4,0xff123456);var b=new SparseVoxelOctree(16);b.fill(0,0,0,16,16,16,0xff123456);
        for(int i=0;i<300;i++) {
            int x=random.nextInt(16),y=random.nextInt(16),z=random.nextInt(16),color=i%3==0?0:0xff000000|random.nextInt(0x1000000);
            a.set(x,y,z,color);b.set(x,y,z,color);assertTrue(a.sameValues(b));assertTrue(b.sameValues(a));
            var changed=b.copy();changed.set(x,y,z,color==0?0xffabcdef:0);
            assertFalse(a.sameValues(changed));assertFalse(changed.sameValues(a));
        }
        assertFalse(a.sameValues(null));assertFalse(a.sameValues(new SparseVoxelOctree(8)));
    }
}
