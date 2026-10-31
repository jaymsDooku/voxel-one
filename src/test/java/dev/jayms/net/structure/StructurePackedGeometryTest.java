package dev.jayms.net.structure;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.jayms.net.structure.StructurePackage.*;

class StructurePackedGeometryTest {
    private static SparseVoxelOctree mixed(int color) {
        var tree=new SparseVoxelOctree(16);tree.fill(1,2,3,2,3,4,color);tree.fill(14,13,12,15,14,13,WorldVoxels.encode(Blocks.STONE));return tree.freeze();
    }
    private static byte[] bytes(SparseVoxelOctree tree)throws IOException {
        var out=new ByteArrayOutputStream();tree.write(new DataOutputStream(out));return out.toByteArray();
    }
    @Test void recordPrefixAndPayloadCrossSegmentsWithoutChangingEarlierSnapshots()throws Exception {
        for(int boundary:new int[]{65530,65534}) {
            var builder=new StructureGeometry.Builder();var first=mixed(0xfe123456);int a=builder.add(first);
            var early=builder.snapshot();builder.write(new byte[boundary-builder.size()]);
            var second=mixed(0xfeabcdef);int b=builder.add(second);assertEquals(boundary,b);
            var snapshot=builder.snapshot();
            for(int i=0;i<3000;i++)builder.add(mixed(0xfe000000|i));
            assertArrayEquals(bytes(first),bytes(early.read(a)));assertArrayEquals(bytes(first),bytes(snapshot.read(a)));
            assertArrayEquals(bytes(second),bytes(snapshot.read(b)));
            var output=new ByteArrayOutputStream();snapshot.write(b,new DataOutputStream(output));assertArrayEquals(bytes(second),output.toByteArray());
            assertThrows(IllegalStateException.class,()->snapshot.read(b).set(0,0,0,0xff123456));
        }
    }
    @Test void cellListsRetainPhysicalValueEqualityAndRemainIndependentOfBuilderGrowth()throws Exception {
        var builder=new StructureCells.Builder();var original=mixed(0xfe123456);
        builder.add(new Position(3,0,0),original,-1);
        var uniform=new SparseVoxelOctree(16);uniform.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.BRICKS));
        builder.add(new Position(1,0,0),uniform.freeze(),-1);builder.add(new Position(2,0,0),null,0);
        var cells=builder.build();var copy=new ArrayList<>(cells);
        assertEquals(copy,cells);assertEquals(copy.hashCode(),cells.hashCode());assertTrue(cells.contains(copy.get(2)));
        assertTrue(new HashSet<>(cells).containsAll(copy));assertEquals(0,cells.get(1).model());assertNull(cells.get(1).geometry());
        for(int i=0;i<2000;i++)builder.add(new Position(i%1024,1+i/1024,0),mixed(0xfe000000|i),-1);
        assertEquals(copy,cells);assertEquals(original.leaves(),cells.get(2).geometry().leaves());
        var different=new Cell(cells.get(2).position(),mixed(0xfe999999),-1);assertNotEquals(cells.get(2),different);
    }
    @Test void packedExportMatchesLegacyWriterIncludingRedundantBranchesAndExtremes()throws Exception {
        var raw=new ByteArrayOutputStream();var out=new DataOutputStream(raw);out.writeByte(16);out.writeByte(2);
        for(int i=0;i<8;i++) {
            if(i==0){out.writeByte(2);for(int j=0;j<8;j++){out.writeByte(1);out.writeInt(WorldVoxels.encode(Blocks.STONE));}}
            else {out.writeByte(1);out.writeInt(WorldVoxels.encode(Blocks.BRICKS));}
        }
        var legacy=SparseVoxelOctree.read(new DataInputStream(new ByteArrayInputStream(raw.toByteArray()))).freeze();
        var uniform=new SparseVoxelOctree(16);uniform.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        var model=new SparseVoxelOctree(8);model.set(1,2,3,0xff123456);
        var input=new ArrayList<>(List.of(new Cell(new Position(1023,255,1023),legacy,-1),new Cell(new Position(0,0,0),uniform.freeze(),-1),new Cell(new Position(1,0,0),mixed(0xfe123456),-1),new Cell(new Position(0,255,1023),null,0)));
        var asset=new StructurePackage("Packed export",new Bounds(1024,256,1024,0,0,0),List.of(new ModelDefinition("Model",model)),input);
        input.sort(Comparator.comparingLong(c->StructurePackage.order(c.position())));
        var reference=new ByteArrayOutputStream();
        try(var data=new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(reference),65536))) {
            data.writeInt(MAGIC);data.writeInt(VERSION);data.writeUTF(asset.name);
            var b=asset.bounds;for(int value:new int[]{b.x(),b.y(),b.z(),b.ax(),b.ay(),b.az()})data.writeInt(value);
            data.writeInt(asset.models.size());for(var dependency:asset.models)StructureModelCodec.write(data,dependency);
            data.writeInt(input.size());
            for(var cell:input) {
                var p=cell.position();data.writeShort(p.x()/16);data.writeByte(p.y()/16);data.writeShort(p.z()/16);
                data.writeShort((p.y()%16*16+p.z()%16)*16+p.x()%16);data.writeShort(cell.model());
                if(cell.model()<0)cell.geometry().write(data);
            }
        }
        assertArrayEquals(reference.toByteArray(),asset.encode());assertArrayEquals(asset.encode(),StructurePackage.decode(asset.encode()).encode());
        assertArrayEquals(bytes(legacy),bytes(asset.cells.get(3).geometry()));
        assertEquals(asset.cells,new ArrayList<>(asset.cells));
        // White uses the raw path because -1 is uniform()'s mixed sentinel.
        var white=new SparseVoxelOctree(16);white.fill(0,0,0,16,16,16,0xffffffff);
        var storage=new StructureCells.Builder(1);storage.add(new Position(0,0,0),white.freeze(),-1);
        assertArrayEquals(bytes(white),bytes(storage.build().get(0).geometry()));
    }
    @Test void storageBudgetRejectsBeforeAllocatingAnotherSegment()throws Exception {
        var builder=new StructureGeometry.Builder();var length=StructureGeometry.Builder.class.getDeclaredField("length");length.setAccessible(true);
        // Exercise the allocation guard with a synthetic counter; do not allocate 512 MiB.
        length.setInt(builder,(int)DECODED);
        assertThrows(IOException.class,()->builder.write(1));assertThrows(IOException.class,()->builder.write(new byte[]{1}));
        builder.write(new byte[0]);assertEquals(DECODED,builder.size());
    }
}
