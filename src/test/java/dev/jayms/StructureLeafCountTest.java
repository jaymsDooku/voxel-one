package dev.jayms;

import dev.jayms.net.model.SparseVoxelOctree;
import java.io.*;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureLeafCountTest {
    private byte[] bytes(SparseVoxelOctree tree)throws IOException {
        var bytes=new ByteArrayOutputStream();tree.write(new DataOutputStream(bytes));return bytes.toByteArray();
    }
    @Test void countMatchesTraversalForEmptySolidFragmentedAndPersistentSnapshots()throws Exception {
        var random=new Random(792);
        for(int size:new int[]{1,8,16,32,256}) {
            var tree=new SparseVoxelOctree(size);assertEquals(0,tree.leafCount());
            tree.fill(0,0,0,size,size,size,0xff123456);assertEquals(1,tree.leafCount());
            var solid=tree.copy().freeze();
            for(int i=0;i<200;i++) {
                tree.set(random.nextInt(size),random.nextInt(size),random.nextInt(size),i%3==0?0:0xff000000|random.nextInt(1<<24));
                assertEquals(tree.leaves().size(),tree.leafCount());
            }
            byte[] before=bytes(tree);assertEquals(tree.leaves().size(),tree.leafCount());assertArrayEquals(before,bytes(tree));
            assertEquals(1,solid.leafCount());assertEquals(solid.leaves().size(),solid.leafCount());
            var decoded=SparseVoxelOctree.read(new DataInputStream(new ByteArrayInputStream(before)));
            assertEquals(tree.leafCount(),decoded.leafCount());
        }
    }
    @Test void legacyRedundantBranchesCountStoredLeavesNotOccupiedVolume()throws Exception {
        var output=new ByteArrayOutputStream();var data=new DataOutputStream(output);
        data.writeByte(16);data.writeByte(2);
        for(int child=0;child<8;child++){data.writeByte(1);data.writeInt(0xff123456);}
        byte[] original=output.toByteArray();
        var tree=SparseVoxelOctree.read(new DataInputStream(new ByteArrayInputStream(original)));
        assertEquals(8,tree.leafCount());assertEquals(8,tree.leaves().size());assertEquals(4096,tree.occupied());
        assertArrayEquals(original,bytes(tree));
    }
}
