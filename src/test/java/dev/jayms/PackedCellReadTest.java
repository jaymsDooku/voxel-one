package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PackedCellReadTest {
    static byte[] bytes(SparseVoxelOctree tree)throws IOException{return SparseVoxelPackedCellTest.bytes(tree);}
    static SparseVoxelOctree reference(byte[] wire)throws IOException {
        try(var in=new DataInputStream(new ByteArrayInputStream(wire))) {
            var tree=SparseVoxelOctree.read(in,16);if(in.read()!=-1)throw new IOException("Trailing reference bytes");return tree;
        }
    }
    static void equivalent(SparseVoxelOctree expected,SparseVoxelOctree actual)throws Exception {
        assertTrue(actual.isFrozen());assertArrayEquals(bytes(expected),bytes(actual));assertTrue(expected.sameValues(actual));
        assertEquals(expected.nodes(),actual.nodes());assertEquals(expected.leafCount(),actual.leafCount());assertEquals(expected.occupied(),actual.occupied());assertEquals(expected.isEmpty(),actual.isEmpty());
        for(int side:new int[]{1,2,4,8,16})for(int y=0;y<16;y+=side)for(int z=0;z<16;z+=side)for(int x=0;x<16;x+=side)assertEquals(expected.uniform(x,y,z,side),actual.uniform(x,y,z,side));
        for(int r=0;r<4;r++)assertArrayEquals(bytes(expected.rotatedY(r)),bytes(actual.rotatedY(r)));
    }
    @Test void randomCellsPreserveQueriesCountersAndWireWithoutRetainingGraphs()throws Exception {
        var random=new Random(59268);
        for(int n=0;n<24;n++) {
            var source=new SparseVoxelOctree(16);
            for(int i=0;i<32;i++){int side=1<<random.nextInt(4),x=random.nextInt(16/side)*side,y=random.nextInt(16/side)*side,z=random.nextInt(16/side)*side;source.fill(x,y,z,x+side,y+side,z+side,(i%2==0?0xff000000:0xfe000000)|random.nextInt(1<<24));}
            var actual=SparseVoxelOctree.readPackedCell(bytes(source));equivalent(source,actual);
            assertTrue(SparseVoxelPackedCellTest.packedWithoutGraph(actual));
            var zero=actual.rotatedY(0);assertNotSame(actual,zero);assertTrue(SparseVoxelPackedCellTest.packedWithoutGraph(zero));
            source.fill(0,0,0,16,16,16,0);assertArrayEquals(bytes(actual),bytes(zero));
            var copy=actual.copy();copy.fill(0,0,0,16,16,16,0);assertFalse(actual.isEmpty());
            assertThrows(IllegalStateException.class,()->actual.set(0,0,0,0));assertTrue(SparseVoxelPackedCellTest.packedWithoutGraph(actual));
        }
    }
    @Test void airUniformAndNoncanonicalBranchesKeepTheirOriginalMeaning()throws Exception {
        equivalent(new SparseVoxelOctree(16),SparseVoxelOctree.readPackedCell(new byte[]{16,0}));
        for(int color:new int[]{0xffabcdef,0xfe123456}) {
            var uniform=new SparseVoxelOctree(16);uniform.fill(0,0,0,16,16,16,color);equivalent(uniform,SparseVoxelOctree.readPackedCell(bytes(uniform)));
        }
        for(boolean air:new boolean[]{false,true}) {
            var buffer=new ByteArrayOutputStream();var out=new DataOutputStream(buffer);out.writeByte(16);out.writeByte(2);
            for(int i=0;i<8;i++){out.writeByte(air?0:1);if(!air)out.writeInt(0xffabcdef);}
            var wire=buffer.toByteArray();var packed=SparseVoxelOctree.readPackedCell(wire);equivalent(reference(wire),packed);
            assertTrue(SparseVoxelPackedCellTest.packedWithoutGraph(packed));assertArrayEquals(wire,bytes(packed.rotatedY(0)));
        }
        var model=new SparseVoxelOctree(16);for(int i=0;i<4096;i++)model.set(i%16,i/16%16,i/256,0xff000000|i);
        var direct=SparseVoxelOctree.readPackedCell(bytes(model));equivalent(model,direct);assertTrue(SparseVoxelPackedCellTest.packedWithoutGraph(direct));
        var packed=model.packedModelCopy();var root=SparseVoxelOctree.class.getDeclaredField("root");root.setAccessible(true);
        assertSame(root.get(packed),root.get(packed.rotatedY(0)));assertArrayEquals(bytes(model),bytes(packed.rotatedY(0)));
    }
    @Test void inputAndOutputBuffersCannotMutateTheSnapshot()throws Exception {
        byte[] wire=bytes(SparseVoxelPackedCellTest.fixture()),expected=wire.clone();var packed=SparseVoxelOctree.readPackedCell(wire);Arrays.fill(wire,(byte)0);
        try(var out=new DataOutputStream(new OutputStream(){public void write(int value){}public void write(byte[] data,int offset,int count){Arrays.fill(data,offset,offset+count,(byte)0);}})){packed.write(out);}
        assertArrayEquals(expected,bytes(packed));assertTrue(SparseVoxelPackedCellTest.packedWithoutGraph(packed));
        Thread.currentThread().interrupt();try{assertThrows(CancellationException.class,()->SparseVoxelOctree.readPackedCell(expected));assertThrows(CancellationException.class,()->packed.rotatedY(0));}finally{Thread.interrupted();}
        assertArrayEquals(expected,bytes(packed));
    }
    @Test void malformedRecordsRejectAndLegacyStreamHeadersRemainCompatible()throws Exception {
        var invalid=new ArrayList<byte[]>();invalid.add(new byte[0]);invalid.add(new byte[65537]);invalid.add(new byte[]{16});invalid.add(new byte[]{8,0});invalid.add(new byte[]{32,0});invalid.add(new byte[]{16,3});invalid.add(new byte[]{16,2,2,2,2,2});
        for(int color:new int[]{0,0xfd123456,0x00123456}){var b=new ByteArrayOutputStream();var out=new DataOutputStream(b);out.writeByte(16);out.writeByte(1);out.writeInt(color);invalid.add(b.toByteArray());}
        byte[] valid=bytes(SparseVoxelPackedCellTest.fixture());for(int i=0;i<valid.length;i++)invalid.add(Arrays.copyOf(valid,i));invalid.add(Arrays.copyOf(valid,valid.length+1));
        for(byte[] wire:invalid)assertThrows(IOException.class,()->SparseVoxelOctree.readPackedCell(wire));
        byte[] extended={0,0,16,0};assertTrue(reference(extended).isEmpty());assertThrows(IOException.class,()->SparseVoxelOctree.readPackedCell(extended));
        var random=new Random(5910);
        for(int i=0;i<256;i++) {
            byte[] wire=valid.clone();wire[1+random.nextInt(wire.length-1)]=(byte)random.nextInt(256);
            SparseVoxelOctree expected;
            try{expected=reference(wire);}catch(IOException failure){assertThrows(IOException.class,()->SparseVoxelOctree.readPackedCell(wire));continue;}
            assertArrayEquals(bytes(expected),bytes(SparseVoxelOctree.readPackedCell(wire)));
        }
    }
    @Test void packageCellsUsePackedReadsAndPreserveRoundTrips()throws Exception {
        var source=new SparseVoxelOctree(16);source.fill(1,2,3,7,8,9,WorldVoxels.encode(Blocks.BRICKS));
        var asset=new StructurePackage("Packed reader",new StructurePackage.Bounds(2,1,1,0,0,0),List.of(),List.of(new StructurePackage.Cell(new StructurePackage.Position(0,0,0),source,-1)));
        byte[] encoded=asset.encode();var loaded=StructurePackage.decode(encoded);var cell=loaded.cells.get(0).geometry();
        assertTrue(SparseVoxelPackedCellTest.packedWithoutGraph(cell));equivalent(source,cell);assertArrayEquals(encoded,loaded.encode());
        assertTrue(SparseVoxelPackedCellTest.packedWithoutGraph(cell.rotatedY(0)));
    }
}
