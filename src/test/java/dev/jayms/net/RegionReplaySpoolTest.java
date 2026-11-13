package dev.jayms.net;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegionReplaySpoolTest {
    @TempDir Path folder;
    private DataInputStream input(byte[] bytes){return new DataInputStream(new ByteArrayInputStream(bytes));}
    private Path spoolPath()throws IOException {
        try(var paths=Files.list(folder)){return paths.filter(p->p.getFileName().toString().startsWith(".replay-spool-")).findFirst().orElseThrow();}
    }
    private void empty()throws IOException {
        try(var paths=Files.list(folder)){assertTrue(paths.noneMatch(p->p.getFileName().toString().startsWith(".replay-")));}
    }
    @Test void boundedStagingRetainsOrderTailAndNoGlobalHistory()throws Exception {
        var history=RegionReplayTest.fixture();byte[] encoded=RegionReplayTest.encode(history);
        var framed=new ByteArrayOutputStream();framed.write(encoded);new DataOutputStream(framed).writeInt(0x12345678);
        var source=input(framed.toByteArray());var before=new ArrayList<>(history.values());
        RegionReplaySpool staged;
        try(var spool=RegionReplaySpool.stage(folder,source,e->true,()->false)) {
            staged=spool;assertEquals(1102,spool.size());assertEquals(encoded.length,spool.encodedBytes());
            assertEquals(0x12345678,source.readInt());assertEquals(-1,source.read());
            assertArrayEquals(encoded,Files.readAllBytes(spoolPath()));
            history.clear();var restored=new ArrayList<Protocol.Edit>();var sizes=new ArrayList<Integer>();
            spool.visit(e->true,()->false,(region,section)->{assertThrows(UnsupportedOperationException.class,section::clear);sizes.add(section.size());restored.addAll(section);});
            assertEquals(List.of(1024,76,1,1),sizes);assertEquals(before,restored);
            // Canonical map replay remains a caller choice, not a spool requirement.
            var replay=new WorldVoxels.History();spool.visit(e->true,()->false,(r,s)->s.forEach(e->WorldVoxels.remember(replay,e)));
            assertEquals(before,new ArrayList<>(replay.values()));
        }
        empty();staged.close();assertThrows(IOException.class,staged::size);
        assertThrows(IOException.class,()->staged.visit(e->true,()->false,(r,s)->fail()));
    }
    @Test void stagingFailureCancellationAndBudgetsDeleteOnlyOwnTemporaryFile()throws Exception {
        Files.writeString(folder.resolve("keep"),"unchanged");byte[] encoded=RegionReplayTest.encode(RegionReplayTest.fixture());
        var cancelled=new AtomicBoolean();var checks=new int[1];
        assertThrows(CancellationException.class,()->RegionReplaySpool.stage(folder,input(encoded),e->{if(++checks[0]==1025)cancelled.set(true);return true;},cancelled::get));empty();
        var source=input(encoded);assertThrows(CancellationException.class,()->RegionReplaySpool.stage(folder,source,e->true,()->true));assertEquals(encoded.length,source.available());empty();
        byte[] invalid=encoded.clone();invalid[24+1024*24+16]^=1;
        assertThrows(IOException.class,()->RegionReplaySpool.stage(folder,input(invalid),e->true,()->false));empty();
        assertThrows(IOException.class,()->RegionReplaySpool.stage(folder,input(encoded),e->false,()->false));empty();
        assertThrows(IOException.class,()->RegionReplaySpool.stage(folder,input(encoded),e->true,()->false,encoded.length-1));empty();
        assertThrows(IllegalArgumentException.class,()->RegionReplaySpool.stage(folder,input(encoded),e->true,()->false,3));
        assertThrows(IllegalArgumentException.class,()->RegionReplaySpool.stage(folder,input(encoded),e->true,()->false,RegionReplaySpool.MAX_BYTES+1));empty();
        assertEquals("unchanged",Files.readString(folder.resolve("keep")));
        try(var spool=RegionReplaySpool.stage(folder,input(new byte[4]),e->true,()->false,4)) {
            assertEquals(0,spool.size());assertEquals(4,spool.encodedBytes());spool.visit(e->true,()->false,(r,s)->fail());
        }
        empty();
    }
    @Test void privateReadFailureNeverPublishesAndAllowsRetry()throws Exception {
        byte[] encoded=RegionReplayTest.encode(RegionReplayTest.fixture());
        try(var spool=RegionReplaySpool.stage(folder,input(encoded),e->true,()->false)) {
            var path=spoolPath();var delivered=new ArrayList<List<Protocol.Edit>>();
            byte[] invalid=encoded.clone();invalid[24+1024*24+16]^=1;Files.write(path,invalid);
            assertThrows(IOException.class,()->spool.visit(e->true,()->false,(r,s)->delivered.add(s)));assertEquals(1,delivered.size());
            Files.write(path,encoded);var count=new int[1];spool.visit(e->true,()->false,(r,s)->count[0]+=s.size());assertEquals(1102,count[0]);
            var cancel=new AtomicBoolean();assertThrows(CancellationException.class,()->spool.visit(e->true,cancel::get,(r,s)->cancel.set(true)));
            var failure=new IOException("Synthetic sink failure");assertSame(failure,assertThrows(IOException.class,()->spool.visit(e->true,()->false,(r,s)->{throw failure;})));
            spool.visit(e->true,()->false,(r,s)->{});
            byte[] wrong=encoded.clone();ByteBuffer.wrap(wrong).putInt(1);Files.write(path,wrong);
            assertThrows(IOException.class,()->spool.visit(e->true,()->false,(r,s)->fail()));
            Files.write(path,Arrays.copyOf(encoded,encoded.length+1));assertThrows(IOException.class,()->spool.visit(e->true,()->false,(r,s)->fail()));
            Files.write(path,Arrays.copyOf(encoded,encoded.length-1));assertThrows(IOException.class,()->spool.visit(e->true,()->false,(r,s)->fail()));
        }
        empty();
    }
    @Test void workerOwnershipReentrancyAndInterruptAreExplicit()throws Exception {
        byte[] encoded=RegionReplayTest.encode(RegionReplayTest.fixture());
        try(var spool=RegionReplaySpool.stage(folder,input(encoded),e->true,()->false)) {
            var worker=Executors.newSingleThreadExecutor();
            try{worker.submit(()->{
                assertThrows(IOException.class,spool::size);assertThrows(IOException.class,spool::close);
                assertThrows(IOException.class,()->spool.visit(e->true,()->false,(r,s)->fail()));
            }).get(5,TimeUnit.SECONDS);}finally{worker.shutdownNow();assertTrue(worker.awaitTermination(5,TimeUnit.SECONDS));}
            spool.visit(e->true,()->false,(r,s)->{
                assertThrows(IOException.class,spool::close);assertThrows(IOException.class,spool::size);
                assertThrows(IOException.class,()->spool.visit(e->true,()->false,(rr,ss)->fail()));
            });
            try {Thread.currentThread().interrupt();assertThrows(CancellationException.class,()->spool.visit(e->true,()->false,(r,s)->fail()));}
            finally{Thread.interrupted();}
            assertEquals(1102,spool.size());
        }
        empty();
    }
}
