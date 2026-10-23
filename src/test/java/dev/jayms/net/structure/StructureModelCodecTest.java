package dev.jayms.net.structure;

import dev.jayms.net.model.*;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.jayms.net.structure.StructurePackage.*;

class StructureModelCodecTest {
    void unmerged(DataOutputStream out,int x,int y,int z,int side)throws Exception {
        if(side==1){out.writeByte(1);out.writeInt(x<2&&y<2&&z<2?0xff123456:0xff000000|(x+8*y+64*z));return;}
        out.writeByte(2);int half=side/2;
        for(int i=0;i<8;i++)unmerged(out,x+((i&1)==0?0:half),y+((i&2)==0?0:half),z+((i&4)==0?0:half),half);
    }
    @Test void retainsExactLegacyIdentityForRedundantOctreeBranches()throws Exception {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeByte(8);unmerged(out,0,0,0,8);
        var tree=SparseVoxelOctree.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        var model=new ModelDefinition("Unmerged",tree);byte[] encoded=packed(model);assertEquals(0,encoded[0]);
        var restored=StructureModelCodec.read(new DataInputStream(new ByteArrayInputStream(encoded)));
        assertEquals(model.fingerprint(),restored.fingerprint());
    }
    byte[] packed(ModelDefinition model)throws Exception {
        var bytes=new ByteArrayOutputStream();StructureModelCodec.write(new DataOutputStream(bytes),model);return bytes.toByteArray();
    }
    @Test void preservesAirBlackColoursAndCoordinatesAtEveryResolution()throws Exception {
        for(int size:new int[]{8,16,32})for(boolean dense:new boolean[]{false,true}) {
            var tree=new SparseVoxelOctree(size);var random=new Random(20261021);
            for(int z=0;z<size;z++)for(int y=0;y<size;y++)for(int x=0;x<size;x++)
                if(dense||random.nextInt(4)==0)tree.set(x,y,z,0xff000000|random.nextInt(1<<24));
            tree.set(0,0,0,0xff000000);tree.set(size-1,size-1,size-1,0xff123456);tree.set(1,2,3,0);
            var model=new ModelDefinition("Colour component",tree);byte[] data=packed(model);
            try(var in=new DataInputStream(new ByteArrayInputStream(data))) {
                var restored=StructureModelCodec.read(in);assertEquals(-1,in.read());assertEquals(model.fingerprint(),restored.fingerprint());
                for(int z=0;z<size;z++)for(int y=0;y<size;y++)for(int x=0;x<size;x++)assertEquals(tree.get(x,y,z),restored.voxels().get(x,y,z));
                assertThrows(IllegalStateException.class,()->restored.voxels().set(0,0,0,0));
            }
            assertTrue(data.length<=1+2+32+1+4096+98304);
        }
    }
    @Test void mergesUniformPackedModelsBackIntoCanonicalLegacyTrees()throws Exception {
        var tree=new SparseVoxelOctree(32);tree.fill(0,0,0,32,32,32,0xff654321);var model=new ModelDefinition("Uniform",tree);
        var restored=StructureModelCodec.read(new DataInputStream(new ByteArrayInputStream(packed(model))));
        assertEquals(1,restored.voxels().leaves().size());assertEquals(model.fingerprint(),restored.fingerprint());
        assertEquals(0,packed(model)[0]);
        var legacy=new ByteArrayOutputStream();model.write(new DataOutputStream(legacy));
        assertArrayEquals(legacy.toByteArray(),Arrays.copyOfRange(packed(model),1,packed(model).length));
    }
    @Test void readsSpecifiedBitmapOrderAndDistinguishesBlackFromAir()throws Exception {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeByte(1);out.writeUTF("Wire sample");out.writeByte(8);
        byte[] bitmap=new byte[64];for(int i:new int[]{0,209,511})bitmap[i>>>3]|=(byte)(1<<(i&7));out.write(bitmap);
        out.write(new byte[]{0,0,0,(byte)0xab,(byte)0xcd,(byte)0xef,0x23,0x45,0x67});
        var model=StructureModelCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(0xff000000,model.voxels().get(0,0,0));assertEquals(0,model.voxels().get(1,0,0));
        assertEquals(0xffabcdef,model.voxels().get(1,2,3));assertEquals(0xff234567,model.voxels().get(7,7,7));assertEquals(3,model.voxels().occupied());
    }
    @Test void rejectsBadResolutionTruncatedOccupancyAndColours()throws Exception {
        for(int size:new int[]{0,7,64,255}) {
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeByte(1);out.writeUTF("Bad");out.writeByte(size);
            assertThrows(IOException.class,()->StructureModelCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        }
        var tree=new SparseVoxelOctree(8);tree.set(0,0,0,0xff000000);byte[] good=packed(new ModelDefinition("Black",tree));
        for(int length:new int[]{0,1,8,good.length-3,good.length-2,good.length-1})
            assertThrows(IOException.class,()->StructureModelCodec.read(new DataInputStream(new ByteArrayInputStream(Arrays.copyOf(good,length)))));
        assertThrows(IOException.class,()->StructureModelCodec.read(new DataInputStream(new ByteArrayInputStream(new byte[]{2}))));
        for(int bitmapLength:new int[]{0,63,64}) {
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeByte(1);out.writeUTF("Truncated");out.writeByte(8);
            byte[] bitmap=new byte[bitmapLength];if(bitmapLength>0)bitmap[0]=1;out.write(bitmap);
            assertThrows(IOException.class,()->StructureModelCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        }
    }
    @Test void legacyVersionOneStillLoadsAndResavesAsVersionTwo()throws Exception {
        var tree=new SparseVoxelOctree(32);tree.set(1,3,5,0xffabcdef);var model=new ModelDefinition("Legacy",tree);
        var bytes=new ByteArrayOutputStream();
        try(var gzip=new GZIPOutputStream(bytes);var out=new DataOutputStream(gzip)) {
            out.writeInt(MAGIC);out.writeInt(1);out.writeUTF("Legacy structure");
            for(int n:new int[]{80,256,16,1,2,3})out.writeInt(n);
            out.writeInt(1);model.write(out);out.writeInt(1);
            out.writeShort(4);out.writeByte(15);out.writeShort(0);out.writeShort((15*16+15)*16+15);out.writeShort(0);
        }
        var asset=StructurePackage.decode(bytes.toByteArray());assertEquals(model.fingerprint(),asset.models.get(0).fingerprint());
        assertEquals(new Position(79,255,15),asset.cells.get(0).position());
        byte[] current=asset.encode();assertFalse(Arrays.equals(bytes.toByteArray(),current));
        var restored=StructurePackage.decode(current);assertEquals(asset.bounds,restored.bounds);assertEquals(model.fingerprint(),restored.models.get(0).fingerprint());
        assertArrayEquals(current,restored.encode());
    }
    @Test void rejectsDuplicateDependenciesBeforeReadingCells()throws Exception {
        var tree=new SparseVoxelOctree(8);tree.set(0,0,0,0xff123456);var model=new ModelDefinition("Duplicate",tree);
        for(int version:new int[]{1,2}) {
            var bytes=new ByteArrayOutputStream();
            try(var gzip=new GZIPOutputStream(bytes);var out=new DataOutputStream(gzip)) {
                out.writeInt(MAGIC);out.writeInt(version);out.writeUTF("Early validation");for(int n:new int[]{1,1,1,0,0,0})out.writeInt(n);
                out.writeInt(2);for(int i=0;i<2;i++){if(version==1)model.write(out);else StructureModelCodec.write(out,model);}
                // No cell count follows: dependency validation must reject before asking for it.
            }
            assertEquals("Empty or duplicate dependency",assertThrows(IOException.class,()->StructurePackage.decode(bytes.toByteArray())).getMessage());
        }
    }
}
