package dev.jayms.net;

import dev.jayms.ChunkPos;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegionReplayDirectorySnapshotTest {
    @TempDir Path folder;
    private Path file(String prefix)throws IOException {
        try(var paths=Files.list(folder)){return paths.filter(p->p.getFileName().toString().startsWith(prefix)).findFirst().orElseThrow();}
    }
    private byte[] sections(int count)throws IOException {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeInt(count);
        for(int i=0;i<count;i++) {
            var body=new ByteArrayOutputStream();var row=new Protocol.Edit(i%16,128,0,1);row.write(new DataOutputStream(body));
            RegionReplay.writeSection(out,new ChunkPos(0,8,0),1,body);
        }
        out.flush();return bytes.toByteArray();
    }
    @Test void boundedSnapshotKeepsValidatedEntriesWhenOriginalIndexChangesDuringDelivery()throws Exception {
        var bytes=sections(3);
        try(var source=RegionReplaySpool.stage(folder,new DataInputStream(new ByteArrayInputStream(bytes)),e->true,()->false)) {
            var index=file(".replay-index-");var original=Files.readAllBytes(index);var actual=new ArrayList<Protocol.Edit>();
            source.visitRegion(new ChunkPos(0,8,0),e->true,()->false,(p,rows)->{
                actual.addAll(rows);
                if(actual.size()==1)Files.write(index,new byte[original.length]);
            });
            assertEquals(List.of(0,1,2),actual.stream().map(Protocol.Edit::x).toList());
            // The snapshot is local to one visit. A later visit validates again.
            assertThrows(IOException.class,()->source.visitRegion(new ChunkPos(0,8,0),e->true,()->false,(p,r)->fail()));
            Files.write(index,original);
            source.visitRegion(new ChunkPos(7,8,7),e->true,()->false,(p,r)->fail("Empty selection delivered"));
        }
        try(var files=Files.list(folder)){assertEquals(0,files.count());}
    }
    @Test void thresholdAndOverflowPreserveOrderAndRowBudget()throws Exception {
        for(int count:List.of(RegionReplaySpool.SELECTED_DIRECTORY_RECORDS,RegionReplaySpool.SELECTED_DIRECTORY_RECORDS+1)) {
            var bytes=sections(count);
            try(var source=RegionReplaySpool.stage(folder,new DataInputStream(new ByteArrayInputStream(bytes)),e->true,()->false)) {
                var next=new AtomicInteger();
                source.visitRegion(new ChunkPos(0,8,0),count,e->true,()->false,(p,rows)->{
                    assertEquals(1,rows.size());assertEquals(next.getAndIncrement()%16,rows.get(0).x());
                });
                assertEquals(count,next.get());var policy=new AtomicInteger();
                assertThrows(IOException.class,()->source.visitRegion(new ChunkPos(0,8,0),count-1,e->{policy.incrementAndGet();return true;},()->false,(p,r)->fail()));
                assertEquals(0,policy.get());
            }
        }
    }
    @Test void selectedPayloadStillMatchesSealedDirectoryAndGlobalCorruptionRejectsBeforePolicy()throws Exception {
        var bytes=sections(3);
        try(var source=RegionReplaySpool.stage(folder,new DataInputStream(new ByteArrayInputStream(bytes)),e->true,()->false)) {
            var data=file(".replay-spool-");var bad=bytes.clone();bad[bad.length-1]^=1;Files.write(data,bad);
            assertThrows(IOException.class,()->source.visitRegion(new ChunkPos(0,8,0),e->true,()->false,(p,r)->{}));
            Files.write(data,bytes);var index=file(".replay-index-");var contents=Files.readAllBytes(index);contents[contents.length-1]^=1;Files.write(index,contents);
            var policy=new AtomicInteger();
            assertThrows(IOException.class,()->source.visitRegion(new ChunkPos(0,8,0),e->{policy.incrementAndGet();return true;},()->false,(p,r)->fail()));
            assertEquals(0,policy.get());
        }
    }
}
