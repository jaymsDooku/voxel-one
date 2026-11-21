package dev.jayms.net;

import dev.jayms.ChunkPos;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegionReplayIndexTest {
    @TempDir Path folder;
    static DataInputStream input(byte[] bytes){return new DataInputStream(new ByteArrayInputStream(bytes));}
    Path file(String prefix)throws IOException {try(var paths=Files.list(folder)){return paths.filter(p->p.getFileName().toString().startsWith(prefix)).findFirst().orElseThrow();}}
    void empty()throws IOException {try(var paths=Files.list(folder)){assertTrue(paths.noneMatch(p->p.getFileName().toString().startsWith(".replay-")));}}
    @Test void selectiveReadsKeepRecurringSectionOrderAndLeaveSourceTailUnread()throws Exception {
        var history=RegionReplayTest.fixture();var bytes=RegionReplayTest.encode(history);var framed=new ByteArrayOutputStream();framed.write(bytes);new DataOutputStream(framed).writeInt(7391);
        var source=input(framed.toByteArray());
        try(var spool=RegionReplaySpool.stage(folder,source,e->true,()->false)) {
            assertEquals(7391,source.readInt());assertEquals(4L*28,Files.size(file(".replay-index-")));
            for(var region:List.of(new ChunkPos(0,8,0),new ChunkPos(-1,8,-1),new ChunkPos(7,8,7))) {
                var expected=history.values().stream().filter(e->ChunkPos.fromBlock(e.x(),e.y(),e.z()).equals(region)).toList();
                var actual=new ArrayList<Protocol.Edit>();var checks=new AtomicInteger();
                spool.visitRegion(region,e->{checks.incrementAndGet();return true;},()->false,(p,section)->{
                    assertEquals(region,p);assertTrue(section.size()<=1024);assertThrows(UnsupportedOperationException.class,section::clear);actual.addAll(section);
                });
                assertEquals(expected,actual);assertEquals(expected.size(),checks.get());
            }
            var all=new ArrayList<Protocol.Edit>();spool.visit(e->true,()->false,(p,section)->all.addAll(section));assertEquals(new ArrayList<>(history.values()),all);
            assertArrayEquals(bytes,Files.readAllBytes(file(".replay-spool-")));
        }
        empty();
    }
    @Test void indexCorruptionRejectsBeforeDeliveryAndSelectedPayloadHasSealedChecksum()throws Exception {
        var bytes=RegionReplayTest.encode(RegionReplayTest.fixture());var region=new ChunkPos(-1,8,-1);
        try(var spool=RegionReplaySpool.stage(folder,input(bytes),e->true,()->false)) {
            var path=file(".replay-index-");var valid=Files.readAllBytes(path);
            for(int variant=0;variant<4;variant++) {
                var bad=valid.clone();if(variant==0)bad[24]^=1;
                if(variant==1)bad=Arrays.copyOf(bad,bad.length-1);
                if(variant==2)bad=Arrays.copyOf(bad,bad.length+1);
                if(variant==3)ByteBuffer.wrap(bad).putInt(20,1025);
                Files.write(path,bad);assertThrows(IOException.class,()->spool.visitRegion(region,e->true,()->false,(p,s)->fail("Invalid index delivered rows")));
            }
            Files.write(path,valid);var data=file(".replay-spool-");var bad=bytes.clone();int offset=4+20+1024*24+20+76*24;
            bad[offset+20+23]^=1;var crc=new CRC32();crc.update(bad,offset+20,24);ByteBuffer.wrap(bad).putInt(offset+16,(int)crc.getValue());Files.write(data,bad);
            assertThrows(IOException.class,()->spool.visitRegion(region,e->true,()->false,(p,s)->fail("Changed selected payload delivered")));
            // A different region never reads this payload; full traversal still rejects it.
            var count=new AtomicInteger();spool.visitRegion(new ChunkPos(0,8,0),e->true,()->false,(p,s)->count.addAndGet(s.size()));assertEquals(1101,count.get());
            assertThrows(IOException.class,()->spool.visit(e->true,()->false,(p,s)->{}));
            Files.write(data,bytes);spool.visitRegion(region,e->true,()->false,(p,s)->assertEquals(1,s.size()));
        }
        empty();
    }
    @Test void selectiveCancellationOwnershipReentrancyAndRetryStayPrivate()throws Exception {
        var bytes=RegionReplayTest.encode(RegionReplayTest.fixture());var region=new ChunkPos(0,8,0);
        try(var spool=RegionReplaySpool.stage(folder,input(bytes),e->true,()->false)) {
            var checks=new AtomicInteger();assertThrows(CancellationException.class,()->spool.visitRegion(region,e->true,()->checks.incrementAndGet()>3,(p,s)->fail()));
            var cancelled=new AtomicBoolean();assertThrows(CancellationException.class,()->spool.visitRegion(region,e->true,cancelled::get,(p,s)->cancelled.set(true)));
            var worker=Executors.newSingleThreadExecutor();try{worker.submit(()->assertThrows(IOException.class,()->spool.visitRegion(region,e->true,()->false,(p,s)->fail()))).get(5,TimeUnit.SECONDS);}finally{worker.shutdownNow();assertTrue(worker.awaitTermination(5,TimeUnit.SECONDS));}
            spool.visitRegion(region,e->true,()->false,(p,s)->{
                assertThrows(IOException.class,spool::close);assertThrows(IOException.class,()->spool.visitRegion(region,e->true,()->false,(r,ss)->fail()));
            });
            try{Thread.currentThread().interrupt();assertThrows(CancellationException.class,()->spool.visitRegion(region,e->true,()->false,(p,s)->fail()));}finally{Thread.interrupted();}
            spool.visitRegion(region,e->true,()->false,(p,s)->{});
        }
        empty();
    }
    @Test void failedStagesAndEmptyReplayCleanBothFilesAndPreserveUnrelatedData()throws Exception {
        Files.writeString(folder.resolve("keep"),"unchanged");var bytes=RegionReplayTest.encode(RegionReplayTest.fixture());
        assertThrows(IOException.class,()->RegionReplaySpool.stage(folder,input(bytes),e->true,()->false,bytes.length-1));empty();
        assertThrows(CancellationException.class,()->RegionReplaySpool.stage(folder,input(bytes),e->true,()->false,bytes.length,n->{if(n>=128)throw new CancellationException();}));empty();
        try(var spool=RegionReplaySpool.stage(folder,input(new byte[4]),e->true,()->false,4)) {
            assertEquals(0,Files.size(file(".replay-index-")));spool.visitRegion(new ChunkPos(0,8,0),e->true,()->false,(p,s)->fail());
        }
        empty();assertEquals("unchanged",Files.readString(folder.resolve("keep")));
    }
}
