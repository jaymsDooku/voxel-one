package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StructureEncodeCancellationTest {
    @TempDir Path temp;
    private StructurePackage asset()throws Exception {
        var model=new SparseVoxelOctree(8);model.fill(0,0,0,8,8,8,0xff123456);
        var geometry=new SparseVoxelOctree(16);geometry.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        var cells=new ArrayList<StructurePackage.Cell>();
        for(int z=0;z<16;z++)for(int x=0;x<16;x++)cells.add(new StructurePackage.Cell(new StructurePackage.Position(x,0,z),x==0&&z==0?null:geometry,x==0&&z==0?0:-1));
        return new StructurePackage("Encode cancellation",new StructurePackage.Bounds(16,1,16,0,0,0),List.of(new ModelDefinition("Component",model)),cells);
    }
    private void noTemporaryFiles()throws Exception {
        try(var files=Files.list(temp)){assertTrue(files.noneMatch(p->p.getFileName().toString().endsWith(".tmp")));}
    }
    @Test void encodingCancelsAcrossStagesAndPreservesWireBytes()throws Exception {
        var asset=asset();var bytes=asset.encode();
        for(int stage:new int[]{0,25,62,99,100}) {
            var cancel=new AtomicBoolean();assertThrows(CancellationException.class,()->asset.encode(cancel::get,p->{if(p>=stage)cancel.set(true);}));
        }
        var progress=new ArrayList<Integer>();assertArrayEquals(bytes,asset.encode(()->false,progress::add));
        assertEquals(0,progress.get(0));assertEquals(100,progress.get(progress.size()-1));
        for(int i=1;i<progress.size();i++)assertTrue(progress.get(i)>=progress.get(i-1));
        assertArrayEquals(bytes,StructurePackage.decode(bytes).encode());
    }
    @Test void cancelledSavePreservesOldFileCleansStagingAndCanRetry()throws Exception {
        var asset=asset();var path=temp.resolve("existing.vstr");byte[] sentinel={1,2,3,4};Files.write(path,sentinel);
        for(int stage:new int[]{0,25,90,99}) {
            var cancel=new AtomicBoolean();assertThrows(CancellationException.class,()->asset.save(path,cancel::get,p->{if(p>=stage)cancel.set(true);}));
            assertArrayEquals(sentinel,Files.readAllBytes(path));noTemporaryFiles();
        }
        assertThrows(CancellationException.class,()->asset.save(temp.resolve("new.vstr"),()->true,p->{}));assertFalse(Files.exists(temp.resolve("new.vstr")));
        var progress=new ArrayList<Integer>();asset.save(path,()->false,progress::add);assertArrayEquals(asset.encode(),Files.readAllBytes(path));noTemporaryFiles();
        for(int i=1;i<progress.size();i++)assertTrue(progress.get(i)>=progress.get(i-1)&&progress.get(i)<100);
    }
    @Test void progressFailureBeforeCommitPreservesDestinationAndInterruptedEntryStops()throws Exception {
        var asset=asset();var path=temp.resolve("existing.vstr");Files.writeString(path,"sentinel");
        assertThrows(IllegalStateException.class,()->asset.save(path,()->false,p->{if(p>=99)throw new IllegalStateException("Synthetic progress failure");}));
        assertEquals("sentinel",Files.readString(path));noTemporaryFiles();
        Thread.currentThread().interrupt();
        try {assertThrows(CancellationException.class,()->asset.encode(()->false,p->fail("Interrupted encode must stop before progress")));}
        finally {Thread.interrupted();}
    }
}
