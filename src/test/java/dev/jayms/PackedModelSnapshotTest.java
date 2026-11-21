package dev.jayms;

import dev.jayms.net.model.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PackedModelSnapshotTest {
    static byte[] bytes(SparseVoxelOctree tree)throws IOException {
        var bytes=new ByteArrayOutputStream();tree.write(new DataOutputStream(bytes));return bytes.toByteArray();
    }
    @Test void denseModelKeepsEveryColourAndMeshAfterCompaction()throws Exception {
        var source=new SparseVoxelOctree(32);
        for(int z=0;z<32;z++)for(int y=0;y<32;y++)for(int x=0;x<32;x++)
            source.set(x,y,z,0xff000000|x|(y<<8)|(z<<16));
        var packed=source.packedModelCopy();
        assertArrayEquals(bytes(source),bytes(packed));
        for(int z=0;z<32;z++)for(int y=0;y<32;y++)for(int x=0;x<32;x++)
            assertEquals(source.get(x,y,z),packed.get(x,y,z));
        assertEquals(ModelMesher.count(source),ModelMesher.count(packed));
        assertTrue(source.sameValues(packed));
        var copy=packed.copy();copy.set(31,31,31,0);assertEquals(source.get(31,31,31),packed.get(31,31,31));
        assertThrows(IllegalStateException.class,()->packed.fill(0,0,0,1,1,1,0));
    }
    @Test void allResolutionsKeepSamplesMeshIdentityAndIndependentEdits()throws Exception {
        for(int size:new int[]{8,16,32}) {
            var source=new SparseVoxelOctree(size);
            source.fill(0,0,0,size,size,size,0xff123456);
            source.fill(0,0,0,size/2,size/2,size/2,0);
            source.set(size-1,size-1,size-1,0xffabcdef);
            var packed=source.packedModelCopy();
            assertArrayEquals(bytes(source),bytes(packed));
            assertEquals(source.nodes(),packed.nodes());assertEquals(source.occupied(),packed.occupied());
            assertEquals(source.leafCount(),packed.leafCount());assertTrue(source.sameValues(packed));
            for(int z=-1;z<=size;z++)for(int y=-1;y<=size;y++)for(int x=-1;x<=size;x++)
                assertEquals(source.get(x,y,z),packed.get(x,y,z));
            assertEquals(ModelMesher.count(source),ModelMesher.count(packed));
            assertEquals(new ModelDefinition("same",source).fingerprint(),new ModelDefinition("same",packed).fingerprint());
            assertThrows(IllegalStateException.class,()->packed.set(0,0,0,0xffffffff));
            var copy=packed.copy();copy.set(0,0,0,0xffffffff);assertEquals(0,packed.get(0,0,0));
            source.set(size-1,size-1,size-1,0);assertEquals(0xffabcdef,packed.get(size-1,size-1,size-1));
            assertArrayEquals(bytes(packed),bytes(packed.packedModelCopy()));
        }
    }
    @Test void redundantBranchesRetainExactWireIdentity()throws Exception {
        var encoded=new ByteArrayOutputStream();var out=new DataOutputStream(encoded);
        out.writeByte(8);out.writeByte(2);
        for(int i=0;i<8;i++){out.writeByte(1);out.writeInt(0xff123456);}
        var original=SparseVoxelOctree.read(new DataInputStream(new ByteArrayInputStream(encoded.toByteArray())),8);
        var packed=original.packedModelCopy();assertArrayEquals(encoded.toByteArray(),bytes(packed));
        assertEquals(9,packed.nodes());assertEquals(8,packed.leafCount());
        assertArrayEquals(encoded.toByteArray(),bytes(packed.copy()));
        assertEquals(0xff123456,packed.get(7,7,7));
    }
    @Test void packedWorldCellsCanBecomeModelsWithoutLosingGeometry()throws Exception {
        var source=new SparseVoxelOctree(16);source.set(1,2,3,0xffabcdef);
        var model=source.packedCellCopy().packedModelCopy();
        assertArrayEquals(bytes(source),bytes(model));assertEquals(0xffabcdef,model.get(1,2,3));
        for(int size:new int[]{1,2,4,64})assertThrows(IllegalArgumentException.class,()->new SparseVoxelOctree(size).packedModelCopy());
        Thread.currentThread().interrupt();
        try{assertThrows(java.util.concurrent.CancellationException.class,source::packedModelCopy);}
        finally{Thread.interrupted();}
    }
}
