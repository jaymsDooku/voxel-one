package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StructureDecodeCancellationTest {
    @TempDir Path temp;
    private StructurePackage asset()throws Exception {
        var model=new SparseVoxelOctree(8);model.fill(0,0,0,8,8,8,0xff123456);
        var geometry=new SparseVoxelOctree(16);geometry.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        var cells=new ArrayList<StructurePackage.Cell>();
        for(int z=0;z<16;z++)for(int x=0;x<16;x++)cells.add(new StructurePackage.Cell(new StructurePackage.Position(x,0,z),x==0&&z==0?null:geometry,x==0&&z==0?0:-1));
        return new StructurePackage("Decode cancellation",new StructurePackage.Bounds(16,1,16,0,0,0),List.of(new ModelDefinition("Component",model)),cells);
    }
    @Test void progressAndLegacyEntryPointsPreserveExactPackage()throws Exception {
        var original=asset();var bytes=original.encode();var progress=new ArrayList<Integer>();
        var decoded=StructurePackage.decode(bytes,()->false,progress::add);
        assertArrayEquals(bytes,decoded.encode());assertArrayEquals(bytes,StructurePackage.decode(bytes).encode());
        assertEquals(0,progress.get(0));assertEquals(100,progress.get(progress.size()-1));
        for(int i=1;i<progress.size();i++)assertTrue(progress.get(i)>=progress.get(i-1)&&progress.get(i)<=100);
        var path=temp.resolve("long path with spaces.vstr");Files.write(path,bytes);
        assertArrayEquals(bytes,StructurePackage.load(path,()->false,p->{}).encode());assertArrayEquals(bytes,StructurePackage.load(path).encode());
    }
    @Test void cancellationAtEntryParsingAndLateValidationReturnsNoPackage()throws Exception {
        var bytes=asset().encode();var checks=new AtomicInteger();StructurePackage.decode(bytes,()->{checks.incrementAndGet();return false;},p->{});
        int total=checks.get();assertTrue(total>256);
        for(int limit:new int[]{0,3,20,total/2,total-4,total-1}) {
            var count=new AtomicInteger();assertThrows(CancellationException.class,()->StructurePackage.decode(bytes,()->count.incrementAndGet()>limit,p->{}));
        }
        for(int stage:new int[]{0,25,62,99,100}) {
            var cancel=new AtomicBoolean();assertThrows(CancellationException.class,()->StructurePackage.decode(bytes,cancel::get,p->{if(p>=stage)cancel.set(true);}));
        }
        var path=temp.resolve("cancel.vstr");Files.write(path,bytes);
        assertThrows(CancellationException.class,()->StructurePackage.load(path,()->true,p->fail("Cancelled file should not start parsing")));
        try(var files=Files.list(temp)){assertEquals(1,files.count());}assertArrayEquals(bytes,Files.readAllBytes(path));
        // A cancelled attempt neither poisons subsequent import nor changes legacy decode.
        assertArrayEquals(bytes,StructurePackage.decode(bytes).encode());
    }
    @Test void malformedInputNeverReportsValidatedCompletionAndInterruptStopsEntry()throws Exception {
        var bytes=asset().encode();bytes[bytes.length-8]^=1;var progress=new ArrayList<Integer>();
        assertThrows(java.io.IOException.class,()->StructurePackage.decode(bytes,()->false,progress::add));assertFalse(progress.contains(100));
        Thread.currentThread().interrupt();
        try {assertThrows(CancellationException.class,()->StructurePackage.decode(new byte[0],()->false,p->fail("Interrupted decode must stop before progress")));}
        finally {Thread.interrupted();}
    }
}
