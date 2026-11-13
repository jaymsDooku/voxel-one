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
    private Set<Path> stages()throws IOException {
        try(var paths=Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return paths.filter(p->p.getFileName().toString().startsWith("voxel-structure-import-")).collect(java.util.stream.Collectors.toSet());
        }
    }
    @Test void stagedImportRejectsBadFootersAndTrailingMembersWithoutLeakingOrChangingSource()throws Exception {
        byte[] good=packageBytes();var before=stages();var path=folder.resolve("strict.vstr");
        for(int variant=0;variant<4;variant++) {
            byte[] bytes=Arrays.copyOf(good,good.length+(variant==2?1:variant==3?good.length:0));
            if(variant==0)bytes[bytes.length-8]^=1;
            if(variant==1)bytes[bytes.length-4]^=1;
            if(variant==3)System.arraycopy(good,0,bytes,good.length,good.length);
            Files.write(path,bytes);var progress=new ArrayList<Integer>();
            assertThrows(IOException.class,()->StructurePackage.load(path,()->false,progress::add));
            assertFalse(progress.contains(100));assertArrayEquals(bytes,Files.readAllBytes(path));assertEquals(before,stages());
        }
        Files.write(path,good);assertArrayEquals(good,StructurePackage.load(path).encode());assertEquals(before,stages());
    }
    @Test void cancellationAndCallbackFailureDuringStagingOrDecodeCleanOwnedFileAndAllowRetry()throws Exception {
        byte[] good=packageBytes();var path=folder.resolve("cancel-stage.vstr");Files.write(path,good);var before=stages();
        for(int threshold:new int[]{1,30,99,100}) {
            var cancel=new AtomicBoolean();
            assertThrows(CancellationException.class,()->StructurePackage.load(path,cancel::get,p->{if(p>=threshold)cancel.set(true);}));
            assertEquals(before,stages());assertArrayEquals(good,Files.readAllBytes(path));
        }
        assertThrows(IllegalStateException.class,()->StructurePackage.load(path,()->false,p->{if(p>0)throw new IllegalStateException("Synthetic callback failure");}));
        assertEquals(before,stages());assertArrayEquals(good,StructurePackage.load(path).encode());
    }
    @Test void stageReadsOneOpenedSourceWhenItsPathIsReplaced()throws Exception {
        byte[] good=packageBytes();var path=folder.resolve("replaced.vstr");Files.write(path,good);var moved=folder.resolve("original.vstr");var changed=new AtomicBoolean();var before=stages();
        var result=StructurePackage.load(path,()->false,p->{
            if(p>0&&p<10&&changed.compareAndSet(false,true))try {
                Files.move(path,moved);Files.write(path,new byte[]{42});
            }catch(IOException failure){throw new UncheckedIOException(failure);}
        });
        assertTrue(changed.get());assertArrayEquals(good,result.encode());assertArrayEquals(good,Files.readAllBytes(moved));assertArrayEquals(new byte[]{42},Files.readAllBytes(path));assertEquals(before,stages());
    }
}
