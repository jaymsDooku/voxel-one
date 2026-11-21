package dev.jayms.net.structure;

import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureBufferedCodecTest {
    @Test void legacyPrimitiveWriterAndBufferedCodecAgreeAcrossBufferBoundaries()throws Exception {
        var geometry=new SparseVoxelOctree(16);
        geometry.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.BRICKS));
        var cells=new ArrayList<StructurePackage.Cell>();
        for(int z=0;z<256;z++)for(int x=0;x<64;x++)
            cells.add(new StructurePackage.Cell(new StructurePackage.Position(x,0,z),geometry,-1));
        var asset=new StructurePackage("Buffered compatibility",new StructurePackage.Bounds(64,1,256,0,0,0),List.of(),cells);
        var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeInt(StructurePackage.MAGIC);out.writeInt(2);out.writeUTF(asset.name);
            for(int value:new int[]{64,1,256,0,0,0,0,16384})out.writeInt(value);
            for(var cell:asset.cells) {
                var p=cell.position();
                out.writeShort(p.x()/16);out.writeByte(p.y()/16);out.writeShort(p.z()/16);
                out.writeShort((p.y()%16*16+p.z()%16)*16+p.x()%16);out.writeShort(-1);cell.geometry().write(out);
            }
        }
        byte[] legacy=bytes.toByteArray(),buffered=asset.encode();
        try(var old=new GZIPInputStream(new ByteArrayInputStream(legacy));var current=new GZIPInputStream(new ByteArrayInputStream(buffered))) {
            byte[] payload=old.readAllBytes();assertTrue(payload.length>2*65536);
            assertArrayEquals(payload,current.readAllBytes());
        }
        assertArrayEquals(buffered,StructurePackage.decode(legacy).encode());
        assertArrayEquals(buffered,StructurePackage.decode(buffered).encode());
        for(int cut:new int[]{10,legacy.length/2,legacy.length-1})
            assertThrows(IOException.class,()->StructurePackage.decode(Arrays.copyOf(legacy,cut)));
        byte[] corrupt=legacy.clone();corrupt[corrupt.length-8]^=1;
        assertThrows(IOException.class,()->StructurePackage.decode(corrupt));
    }

    @Test void readAheadCannotBypassDecodedByteBudget()throws Exception {
        byte[] payload=new byte[65538];
        var budget=new StructurePackage.BudgetInput(new ByteArrayInputStream(payload),65537);
        try(var in=new DataInputStream(new BufferedInputStream(budget,65536))) {
            assertEquals(65536,in.readNBytes(65536).length);
            assertThrows(IOException.class,in::readInt);
        }
        try(var in=new BufferedInputStream(new StructurePackage.BudgetInput(new ByteArrayInputStream(new byte[65536]),65536),65536)) {
            assertEquals(65536,in.readAllBytes().length);assertEquals(-1,in.read());
        }
    }

    @Test void bufferedFlushStillEnforcesOutputByteBudget()throws Exception {
        var bytes=new ByteArrayOutputStream();
        var out=new BufferedOutputStream(new StructurePackage.BudgetOutput(bytes,65536),65536);
        out.write(new byte[65536]);out.flush();assertEquals(65536,bytes.size());
        out.write(1);assertThrows(IOException.class,out::flush);
        // The failed flush remains pending and must fail again at close.
        assertThrows(IOException.class,out::close);
        assertEquals(65536,bytes.size());
    }
}
