package dev.jayms.net.structure;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.jayms.net.structure.StructurePackage.*;

class StructureGeometryDecodeTest {
    static byte[] bytes(SparseVoxelOctree tree)throws IOException {
        var bytes=new ByteArrayOutputStream();tree.write(new DataOutputStream(bytes));return bytes.toByteArray();
    }
    static DataInputStream input(byte[] bytes){return new DataInputStream(new ByteArrayInputStream(bytes));}
    static byte[] leaf(int color)throws IOException {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeByte(16);out.writeByte(1);out.writeInt(color);return bytes.toByteArray();
    }
    static SparseVoxelOctree dense() {
        var tree=new SparseVoxelOctree(16);
        for(int i=0;i<4096;i++)tree.fill(i%16,(i/16)%16,i/256,i%16+1,(i/16)%16+1,i/256+1,0xfe000000|i);
        return tree;
    }
    @Test void directWireMatchesConstructorAcrossRandomGeometryAndExtendedHeaders()throws Exception {
        var random=new Random(73019);
        for(int n=0;n<128;n++) {
            var tree=new SparseVoxelOctree(16);
            for(int i=0;i<24;i++) {
                int side=1<<random.nextInt(4),x=random.nextInt(16/side)*side,y=random.nextInt(16/side)*side,z=random.nextInt(16/side)*side;
                tree.fill(x,y,z,x+side,y+side,z+side,i%2==0?WorldVoxels.encode(Blocks.STONE):0xfe000000|random.nextInt(1<<24));
            }
            byte[] wire=bytes(tree);
            if(n%2==0){var extended=new ByteArrayOutputStream();extended.write(new byte[]{0,0,16});extended.write(wire,1,wire.length-1);wire=extended.toByteArray();}
            var builder=new StructureCells.Builder(1);var p=new Position(0,0,0);
            assertEquals(tree.leafCount(),builder.readGeometry(p,input(wire),MAX_LEAVES,()->false));
            var decoded=builder.buildOrdered(()->false).get(0);
            assertArrayEquals(bytes(tree),bytes(decoded.geometry()));
            var reference=new StructurePackage("Reference",new Bounds(1,1,1,0,0,0),List.of(),List.of(new Cell(p,tree,-1)));
            var actual=new StructurePackage("Reference",reference.bounds,List.of(),List.of(decoded));
            assertArrayEquals(reference.encode(),actual.encode());
            assertArrayEquals(reference.encode(),StructurePackage.decode(actual.encode()).encode());
            assertThrows(IllegalStateException.class,()->decoded.geometry().set(0,0,0,0xffffffff));
        }
    }
    @Test void redundantBranchesAndCrossSegmentRecordsRemainByteExactAndFrozen()throws Exception {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeByte(16);out.writeByte(2);
        for(int i=0;i<8;i++){out.writeByte(1);out.writeInt(WorldVoxels.encode(Blocks.STONE));}
        byte[] wire=bytes.toByteArray();
        for(int boundary:new int[]{65530,65534,65535}) {
            var builder=new StructureGeometry.Builder();var original=dense();int first=builder.add(original);var old=builder.snapshot();
            builder.write(new byte[boundary-builder.size()]);
            var decoded=builder.readValidated(input(wire),8,()->false);assertFalse(decoded.uniform());assertEquals(8,decoded.leaves());
            var snapshot=builder.snapshot();assertEquals(boundary,decoded.value());
            for(int i=0;i<200;i++)builder.readValidated(input(wire),8,()->false);
            assertArrayEquals(bytes(original),bytes(old.read(first)));
            assertArrayEquals(wire,bytes(snapshot.read(decoded.value())));
            var exported=new ByteArrayOutputStream();snapshot.write(decoded.value(),new DataOutputStream(exported));assertArrayEquals(wire,exported.toByteArray());
        }
    }
    @Test void invalidDepthResolutionMaterialsAndTruncationCannotPublishCells()throws Exception {
        var bad=new ArrayList<byte[]>();
        bad.add(new byte[]{16,0});bad.add(new byte[]{16,3});bad.add(new byte[]{32,0});bad.add(new byte[]{0,1,0,0});
        bad.add(new byte[]{16,2,2,2,2,2});bad.add(new byte[]{16,2,0,0,0,0,0,0,0,0});
        for(int color:new int[]{0,0xff000000,0xff123456,WorldVoxels.encode(Blocks.FLOWER_POT)})bad.add(leaf(color));
        var nested=new ByteArrayOutputStream();var data=new DataOutputStream(nested);data.writeByte(16);data.writeByte(2);data.writeByte(1);data.writeInt(WorldVoxels.encode(Blocks.WATER));for(int i=1;i<8;i++)data.writeByte(0);bad.add(nested.toByteArray());
        var partial=new SparseVoxelOctree(16);partial.fill(1,2,3,2,3,4,0xfe123456);byte[] valid=bytes(partial);
        for(int i=0;i<valid.length;i++)bad.add(Arrays.copyOf(valid,i));
        for(byte[] wire:bad) {
            var builder=new StructureCells.Builder();assertThrows(IOException.class,()->builder.readGeometry(new Position(0,0,0),input(wire),MAX_LEAVES,()->false));assertEquals(0,builder.size());
        }
    }
    @Test void leafBudgetAndCancellationPreserveEarlierPublishedSnapshots()throws Exception {
        var tree=dense();byte[] wire=bytes(tree);var p=new Position(0,0,0);
        var exact=new StructureCells.Builder(1);assertEquals(4096,exact.readGeometry(p,input(wire),4096,()->false));
        assertArrayEquals(wire,bytes(exact.buildOrdered(()->false).get(0).geometry()));
        var builder=new StructureCells.Builder();builder.add(p,tree.freeze(),-1);var early=builder.buildOrdered(()->false);
        assertThrows(IOException.class,()->builder.readGeometry(new Position(1,0,0),input(wire),4095,()->false));assertEquals(1,builder.size());
        var calls=new AtomicInteger();assertThrows(CancellationException.class,()->builder.readGeometry(new Position(1,0,0),input(wire),4096,()->calls.incrementAndGet()>128));assertEquals(1,builder.size());
        assertArrayEquals(wire,bytes(early.get(0).geometry()));assertArrayEquals(wire,bytes(builder.buildOrdered(()->false).get(0).geometry()));
        var arena=new StructureGeometry.Builder();var length=StructureGeometry.Builder.class.getDeclaredField("length");length.setAccessible(true);length.setInt(arena,(int)DECODED);
        assertThrows(IOException.class,()->arena.readValidated(input(wire),4096,()->false));
    }
}
