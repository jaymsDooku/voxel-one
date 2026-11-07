package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.nio.file.*;
import java.io.*;
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
    private StructurePackage largeCompressedAsset()throws Exception {
        var tree=new SparseVoxelOctree(32);var random=new Random(321);
        for(int y=0;y<32;y++)for(int z=0;z<32;z++)for(int x=0;x<32;x++)tree.set(x,y,z,0xff000000|random.nextInt(1<<24));
        var models=new ArrayList<ModelDefinition>();var cells=new ArrayList<StructurePackage.Cell>();
        for(int i=0;i<4;i++){models.add(new ModelDefinition("Stream component "+i,tree));cells.add(new StructurePackage.Cell(new StructurePackage.Position(i,0,0),null,i));}
        return new StructurePackage("Streaming export",new StructurePackage.Bounds(4,1,1,0,0,0),models,cells);
    }
    private boolean stagedBytes()throws IOException {
        try(var paths=Files.list(temp)) {
            for(var p:paths.filter(p->p.getFileName().toString().endsWith(".tmp")).toList())if(Files.size(p)>=65536)return true;
            return false;
        }
    }
    @Test void saveStreamsBeforeEncodingCompletesAndPublishesExactBytesOnlyAtCommit()throws Exception {
        var asset=largeCompressedAsset();var bytes=asset.encode();assertTrue(bytes.length>2*65536);
        var path=temp.resolve("stream.vstr");byte[] sentinel={1,2,3};Files.write(path,sentinel);var observed=new AtomicBoolean();var progress=new ArrayList<Integer>();
        asset.save(path,()->false,p->{progress.add(p);try {
            assertArrayEquals(sentinel,Files.readAllBytes(path));if(p<90&&stagedBytes())observed.set(true);
        }catch(IOException failure){throw new UncheckedIOException(failure);}});
        assertTrue(observed.get(),"Disk stage receives bytes while encoding is still in progress");assertArrayEquals(bytes,Files.readAllBytes(path));
        assertArrayEquals(bytes,StructurePackage.load(path).encode());noTemporaryFiles();
        assertEquals(0,progress.get(0));assertEquals(99,progress.get(progress.size()-1));
        for(int i=1;i<progress.size();i++)assertTrue(progress.get(i)>=progress.get(i-1));
    }
    @Test void partialStreamingFailureAndAtomicMoveFailurePreserveDestinationAndCleanStage()throws Exception {
        var asset=largeCompressedAsset();var path=temp.resolve("old.vstr");Files.writeString(path,"sentinel");var observed=new AtomicBoolean();
        assertThrows(IllegalStateException.class,()->asset.save(path,()->false,p->{try {
            if(p<90&&stagedBytes()){observed.set(true);throw new IllegalStateException("Synthetic streamed-write callback failure");}
        }catch(IOException failure){throw new UncheckedIOException(failure);}}));
        assertTrue(observed.get());assertEquals("sentinel",Files.readString(path));noTemporaryFiles();
        var blocked=temp.resolve("existing-directory");Files.createDirectory(blocked);Files.writeString(blocked.resolve("keep"),"unchanged");
        assertThrows(IOException.class,()->asset.save(blocked));assertEquals("unchanged",Files.readString(blocked.resolve("keep")));noTemporaryFiles();
        asset.save(path);assertArrayEquals(asset.encode(),Files.readAllBytes(path));noTemporaryFiles();
    }
}
