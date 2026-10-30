package dev.jayms;

import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StructureFileReadTest {
    @TempDir Path folder;
    private byte[] packageBytes()throws Exception {
        var tree=new SparseVoxelOctree(32);var random=new Random(321);
        for(int y=0;y<32;y++)for(int z=0;z<32;z++)for(int x=0;x<32;x++)tree.set(x,y,z,0xff000000|random.nextInt(1<<24));
        var asset=new StructurePackage("File read",new StructurePackage.Bounds(2,1,1,0,0,0),List.of(new ModelDefinition("First",tree),new ModelDefinition("Second",tree)),List.of(
            new StructurePackage.Cell(new StructurePackage.Position(0,0,0),null,0),new StructurePackage.Cell(new StructurePackage.Position(1,0,0),null,1)));
        var bytes=asset.encode();assertTrue(bytes.length>2*65536);return bytes;
    }
    @Test void readingAndDecodeProgressStayMonotonicAndPreservePackageBytes()throws Exception {
        byte[] bytes=packageBytes();var path=folder.resolve("models.vstr");Files.write(path,bytes);var progress=new ArrayList<Integer>();
        assertArrayEquals(bytes,StructurePackage.load(path,()->false,progress::add).encode());
        assertEquals(0,progress.get(0));assertEquals(100,progress.get(progress.size()-1));
        assertTrue(progress.stream().anyMatch(p->p>0&&p<10),"File-read phase exposes progress before decoding");
        for(int i=1;i<progress.size();i++)assertTrue(progress.get(i)>=progress.get(i-1)&&progress.get(i)<=100);
        assertArrayEquals(bytes,StructurePackage.load(path).encode());
    }
    @Test void cancellationDuringReadStopsBeforeMalformedPayloadDecode()throws Exception {
        var path=folder.resolve("cancel.vstr");byte[] malformed=new byte[3*65536];Arrays.fill(malformed,(byte)42);Files.write(path,malformed);
        var cancel=new AtomicBoolean();var progress=new ArrayList<Integer>();
        assertThrows(CancellationException.class,()->StructurePackage.load(path,cancel::get,p->{progress.add(p);if(p>0&&p<10)cancel.set(true);}));
        assertFalse(progress.contains(10));assertFalse(progress.contains(100));assertArrayEquals(malformed,Files.readAllBytes(path));
        assertThrows(IOException.class,()->StructurePackage.load(path));
    }
    @Test void growingAndTruncatedFilesFailBeforeCompletionAndOversizeFilesAreRejected()throws Exception {
        byte[] bytes=packageBytes();
        for(boolean grow:new boolean[]{false,true}) {
            var path=folder.resolve(grow?"growing.vstr":"truncated.vstr");Files.write(path,bytes);var changed=new AtomicBoolean();var progress=new ArrayList<Integer>();
            var failure=assertThrows(IOException.class,()->StructurePackage.load(path,()->false,p->{
                progress.add(p);
                if(p>0&&p<10&&changed.compareAndSet(false,true)) {
                    try {
                        if(grow)Files.write(path,new byte[]{1},StandardOpenOption.APPEND);
                        else try(var channel=FileChannel.open(path,StandardOpenOption.WRITE)){channel.truncate(65536);}
                    }catch(IOException problem){throw new UncheckedIOException(problem);}
                }
            }));
            assertTrue(changed.get());assertTrue(failure.getMessage().contains("changed while reading"));assertFalse(progress.contains(100));
        }
        var oversized=folder.resolve("oversized.vstr");
        try(var out=new RandomAccessFile(oversized.toFile(),"rw")){out.setLength(StructurePackage.COMPRESSED+1);}
        assertThrows(IOException.class,()->StructurePackage.load(oversized));
    }
}
