package dev.jayms.net.structure;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureBufferedWireTest {
    static StructureWire.Delta fixture() {
        var component=new SparseVoxelOctree(32);component.set(31,2,7,0xff123456);
        var models=List.of(new ModelLibrary.Entry(1024,"fixture",new ModelDefinition("Wire component",component)));
        var cells=new ArrayList<Protocol.CellState>();
        for(int i=0;i<16;i++) {
            var tree=new SparseVoxelOctree(16);
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
                if(((x+y+z)&1)==0)tree.fill(x,y,z,x+1,y+1,z+1,0xfe000000|((i*171+x*37+y*91+z*101)&0xffffff));
            cells.add(new Protocol.CellState(i-8,128,-2,tree));
        }
        var model=new SparseVoxelOctree(16);model.fill(0,0,0,16,16,16,WorldVoxels.encode(1024));
        cells.add(new Protocol.CellState(-9,128,-2,model));
        cells.add(new Protocol.CellState(-10,128,-2,new SparseVoxelOctree(16)));
        return new StructureWire.Delta(models,cells);
    }
    static byte[] legacy(StructureWire.Delta delta)throws Exception {
        var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeInt(delta.models().size());
            for(var entry:delta.models()){out.writeInt(entry.id());out.writeUTF(entry.author());entry.definition().write(out);}
            out.writeInt(delta.cells().size());for(var cell:delta.cells())cell.write(out);
        }
        return bytes.toByteArray();
    }
    static byte[] gzip(byte[] raw)throws Exception {
        var bytes=new ByteArrayOutputStream();try(var out=new GZIPOutputStream(bytes)){out.write(raw);}return bytes.toByteArray();
    }
    @Test void bytesAndDecodedValuesMatchLegacyAcrossBuffersWithDependenciesAndAir()throws Exception {
        var original=fixture();byte[] expected=legacy(original),actual=StructureWire.encode(original);
        assertTrue(new GZIPInputStream(new ByteArrayInputStream(actual)).readAllBytes().length>65536);
        assertArrayEquals(expected,actual);var decoded=StructureWire.decode(expected);
        assertEquals(original.models().get(0).definition().fingerprint(),decoded.models().get(0).definition().fingerprint());
        assertEquals(1024,decoded.models().get(0).id());assertEquals("fixture",decoded.models().get(0).author());
        assertEquals(original.cells().size(),decoded.cells().size());
        for(int i=0;i<original.cells().size();i++) {
            var a=original.cells().get(i);var b=decoded.cells().get(i);
            assertEquals(List.of(a.x(),a.y(),a.z()),List.of(b.x(),b.y(),b.z()));assertTrue(a.tree().sameValues(b.tree()));
            assertEquals(a.tree().leaves().size(),b.tree().leafCount());
            assertThrows(IllegalStateException.class,()->b.tree().set(0,0,0,0xff123456));
        }
        assertArrayEquals(expected,StructureWire.encode(decoded));
    }
    @Test void malformedChecksumSizeTruncationDuplicateAndTrailingDataStillReject()throws Exception {
        var original=fixture();byte[] good=legacy(original),crc=good.clone(),size=good.clone();
        crc[crc.length-8]^=1;size[size.length-1]^=1;
        for(byte[] bad:List.of(crc,size,Arrays.copyOf(good,good.length-3),new byte[]{1,2,3}))
            assertThrows(IOException.class,()->StructureWire.decode(bad));
        var cell=original.cells().get(0);
        assertEquals("Duplicate structure cell",assertThrows(IOException.class,()->StructureWire.decode(legacy(new StructureWire.Delta(List.of(),List.of(cell,cell))))).getMessage());
        byte[] raw=new GZIPInputStream(new ByteArrayInputStream(good)).readAllBytes();
        assertThrows(IOException.class,()->StructureWire.decode(gzip(Arrays.copyOf(raw,raw.length+1))));
        assertThrows(IOException.class,()->StructureWire.decode(gzip(new byte[]{-1,-1,-1,-1})));
    }
    @Test void cancellationDuringBufferedEncodeRetainsReusableInput()throws Exception {
        var delta=fixture();var calls=new AtomicInteger();
        assertThrows(CancellationException.class,()->StructureWire.encode(delta,()->calls.incrementAndGet()>6));
        assertTrue(calls.get()>6);assertArrayEquals(legacy(delta),StructureWire.encode(delta));
    }
    @Test void decodedBudgetRemainsInsideBufferAtExactAndOverflowBoundaries()throws Exception {
        // Exercise the real limiter with a shortened counter; no 512 MiB allocation claimed.
        var type=Class.forName("dev.jayms.net.structure.StructureWire$Limited");
        var constructor=type.getDeclaredConstructor(InputStream.class);constructor.setAccessible(true);
        var remaining=type.getDeclaredField("remaining");remaining.setAccessible(true);
        for(int length:new int[]{65535,65536,65537}) {
            var limited=(InputStream)constructor.newInstance(new ByteArrayInputStream(new byte[length]));remaining.setLong(limited,65536);
            try(var buffered=new BufferedInputStream(limited,65536)) {
                assertEquals(Math.min(length,65536),buffered.readNBytes(Math.min(length,65536)).length);
                if(length>65536)assertThrows(IOException.class,buffered::read);else assertEquals(-1,buffered.read());
            }
        }
    }
}
