package dev.jayms;

import dev.jayms.net.model.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PackedLeafVisitorTest {
    static byte[] bytes(SparseVoxelOctree t)throws IOException{return SparseVoxelPackedCellTest.bytes(t);}
    static List<SparseVoxelOctree.Leaf> visited(SparseVoxelOctree t)throws IOException {
        var leaves=new ArrayList<SparseVoxelOctree.Leaf>();t.forEachLeaf((x,y,z,side,color)->leaves.add(new SparseVoxelOctree.Leaf(x,y,z,side,color)));return leaves;
    }
    static boolean packedWithoutGraph(SparseVoxelOctree tree)throws Exception {
        var root=SparseVoxelOctree.class.getDeclaredField("root");root.setAccessible(true);var value=root.get(tree);if(value==null)return false;
        var type=value.getClass();if(type.getSimpleName().equals("PackedModel"))type=type.getSuperclass();if(!type.getSimpleName().equals("PackedCell"))return false;
        var children=type.getSuperclass().getDeclaredField("children");children.setAccessible(true);var bytes=type.getDeclaredField("bytes");bytes.setAccessible(true);
        return children.get(value)==null&&bytes.get(value) instanceof byte[];
    }
    static void check(SparseVoxelOctree source,SparseVoxelOctree packed)throws Exception {
        boolean initiallyPacked=packedWithoutGraph(packed);byte[] before=bytes(packed);var expected=source.leaves();assertEquals(expected,visited(packed));assertEquals(expected.size(),packed.leafCount());
        assertArrayEquals(before,bytes(packed));assertEquals(initiallyPacked,packedWithoutGraph(packed));
        var reentered=new boolean[1];packed.forEachLeaf((x,y,z,side,color)->{if(!reentered[0]){reentered[0]=true;assertEquals(expected,visited(packed));}});
        assertEquals(!expected.isEmpty(),reentered[0]);assertArrayEquals(before,bytes(packed));assertEquals(initiallyPacked,packedWithoutGraph(packed));
    }
    @Test void randomAndDenseModelImagesKeepExactStoredOrderCoordinatesAndColours()throws Exception {
        var random=new Random(372181);
        for(int size:new int[]{8,16,32})for(int n=0;n<8;n++) {
            var tree=new SparseVoxelOctree(size);tree.fill(0,0,0,size,size,size,0xffabcdef);
            for(int i=0;i<128;i++)tree.set(random.nextInt(size),random.nextInt(size),random.nextInt(size),i%3==0?0:0xff000000|random.nextInt(1<<24));
            check(tree,tree.packedModelCopy());
        }
        var dense=new SparseVoxelOctree(32);for(int i=0;i<32768;i++)dense.set(i%32,i/32%32,i/1024,0xff000000|i);var packed=dense.packedModelCopy();assertTrue(packedWithoutGraph(packed));check(dense,packed);
    }
    @Test void worldImagesKeepFractionalValuesAndRedundantTopology()throws Exception {
        var world=new SparseVoxelOctree(16);world.fill(0,0,0,8,8,8,0xfe123456);world.fill(15,15,15,16,16,16,0xffffffff);check(world,world.packedCellCopy());
        for(int color:new int[]{0,0xffffffff,0xff123456,0xfe123456}) {
            var b=new ByteArrayOutputStream();var out=new DataOutputStream(b);out.writeByte(16);out.writeByte(2);
            for(int i=0;i<8;i++){out.writeByte(2);for(int j=0;j<8;j++){out.writeByte(color==0?0:1);if(color!=0)out.writeInt(color);}}
            var source=SparseVoxelOctree.read(new DataInputStream(new ByteArrayInputStream(b.toByteArray())),16);check(source,source.packedCellCopy());
        }
    }
    @Test void checkedRuntimeAndCancellationCallbacksStopExactlyAndPreserveSource()throws Exception {
        var source=new SparseVoxelOctree(16);for(int x=0;x<16;x+=2)source.set(x,0,0,0xff123456);var packed=source.packedCellCopy();byte[] before=bytes(packed);
        var io=new IOException("stop");var runtime=new IllegalArgumentException("stop");var cancel=new CancellationException("stop");
        for(Exception failure:new Exception[]{io,runtime,cancel}) {
            int[] calls={0};var thrown=assertThrows(failure.getClass(),()->packed.forEachLeaf((x,y,z,side,color)->{if(++calls[0]==3){if(failure instanceof IOException checked)throw checked;throw (RuntimeException)failure;}}));
            assertSame(failure,thrown);assertEquals(3,calls[0]);assertArrayEquals(before,bytes(packed));assertEquals(source.leaves(),visited(packed));
        }
        assertThrows(NullPointerException.class,()->packed.forEachLeaf(null));assertTrue(SparseVoxelPackedCellTest.packedWithoutGraph(packed));
    }
    @Test void modelValidationKeepsFingerprintNameEmptyAndOpacityRules()throws Exception {
        var source=new SparseVoxelOctree(32);for(int i=0;i<32768;i++)source.set(i%32,i/32%32,i/1024,0xff000000|i);var packed=source.packedModelCopy();byte[] before=bytes(packed);
        var actual=new ModelDefinition("  Model  ",packed);assertEquals("Model",actual.name());assertSame(packed,actual.voxels());assertEquals(new ModelDefinition("Model",source).fingerprint(),actual.fingerprint());assertArrayEquals(before,bytes(packed));assertTrue(packedWithoutGraph(packed));
        assertEquals(0,new ModelDefinition("Empty",new SparseVoxelOctree(8)).voxels().occupied());
        var invalid=new SparseVoxelOctree(16);invalid.fill(1,2,3,2,3,4,0xfe123456);
        for(var tree:List.of(invalid,invalid.packedCellCopy(),invalid.packedModelCopy()))assertEquals("Models require opaque ARGB colours",assertThrows(IllegalArgumentException.class,()->new ModelDefinition("Invalid",tree)).getMessage());
        var denseInvalid=new SparseVoxelOctree(16);for(int i=0;i<4096;i++)denseInvalid.fill(i%16,i/16%16,i/256,i%16+1,i/16%16+1,i/256+1,0xfe000000|i);var invalidPacked=denseInvalid.packedModelCopy();assertTrue(packedWithoutGraph(invalidPacked));assertEquals("Models require opaque ARGB colours",assertThrows(IllegalArgumentException.class,()->new ModelDefinition("Invalid",invalidPacked)).getMessage());
        for(String name:new String[]{null,"","!"})assertThrows(IllegalArgumentException.class,()->new ModelDefinition(name,packed));
    }
}
