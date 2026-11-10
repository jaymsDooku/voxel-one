package dev.jayms.net;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegionReplayCountingCopyTest {
    @TempDir Path folder;
    RegionReplayCellsTest fixture(){var test=new RegionReplayCellsTest();test.folder=folder;return test;}
    static final List<Protocol.Edit> ROWS=List.of(RegionReplayWindowTest.whole(0,Blocks.STONE),RegionReplayWindowTest.whole(4095,Blocks.GLASS));
    void noScratch()throws IOException {
        try(var paths=Files.list(folder)){assertTrue(paths.noneMatch(p->p.getFileName().toString().startsWith(".replay-count-")||p.getFileName().toString().startsWith(".replay-cells-")));}
    }
    @Test void corruptedCountingCopyNeverTransfersCandidateAndRetryCleansBothFiles()throws Exception {
        var test=fixture();
        for(int mode=0;mode<4;mode++) {
            final int change=mode;var altered=new AtomicBoolean();
            try(var source=test.spool(ROWS)) {
                assertThrows(IOException.class,()->RegionReplayCells.build(folder,source,RegionReplayCellsTest.REGION,2,e->true,()->{
                    try(var paths=Files.list(folder)) {
                        var list=paths.toList();boolean candidate=list.stream().anyMatch(p->p.getFileName().toString().startsWith(".replay-cells-"));
                        if(candidate&&altered.compareAndSet(false,true)) {
                            var raw=list.stream().filter(p->p.getFileName().toString().startsWith(".replay-count-")).findFirst().orElseThrow();var bytes=Files.readAllBytes(raw);
                            if(change==0)ByteBuffer.wrap(bytes).putInt(16,Blocks.GLASS); // valid row, wrong sealed CRC
                            if(change==1)bytes=Arrays.copyOf(bytes,bytes.length-1);
                            if(change==2)bytes=Arrays.copyOf(bytes,bytes.length+1);
                            if(change==3)ByteBuffer.wrap(bytes).putInt(0,1);
                            Files.write(raw,bytes);
                        }
                    }catch(IOException error){throw new UncheckedIOException(error);}return false;
                }));assertTrue(altered.get());noScratch();
                try(var cells=RegionReplayCells.build(folder,source,RegionReplayCellsTest.REGION,2,e->true,()->false)) {
                    assertEquals(List.of(ROWS.get(0)),cells.readWindow(new RegionReplayPage.CellRange(0,1),1,e->true,()->false));
                    assertEquals(List.of(ROWS.get(1)),cells.readWindow(new RegionReplayPage.CellRange(4095,4096),1,e->true,()->false));
                }
                noScratch();
            }
        }
    }
    @Test void validatedCopyOutlivesLaterSourcePayloadChangeWithoutGrantingSourceAuthority()throws Exception {
        var test=fixture();try(var source=test.spool(ROWS)) {
            var path=test.file(".replay-spool-");var original=Files.readAllBytes(path);var bad=original.clone();bad[bad.length-1]^=1;var changed=new AtomicBoolean();var calls=new AtomicInteger();
            try(var cells=RegionReplayCells.build(folder,source,RegionReplayCellsTest.REGION,2,e->{calls.incrementAndGet();return true;},()->{
                try(var paths=Files.list(folder)) {
                    if(paths.anyMatch(p->p.getFileName().toString().startsWith(".replay-cells-"))&&changed.compareAndSet(false,true))Files.write(path,bad);
                }catch(IOException error){throw new UncheckedIOException(error);}return false;
            })) {
                assertTrue(changed.get());assertEquals(2,calls.get());
                assertEquals(List.of(ROWS.get(1)),cells.readWindow(new RegionReplayPage.CellRange(4095,4096),1,e->true,()->false));
                assertThrows(IOException.class,()->RegionReplayCells.build(folder,source,RegionReplayCellsTest.REGION,2,e->true,()->false));
            }
            Files.write(path,original);noScratch();
        }
    }
    @Test void failedScratchDeletionStaysOwnedAndBlocksNewBuildUntilCleanup()throws Exception {
        var test=fixture();var source=test.spool(ROWS);var scratch=source.scratch(folder,false);var path=scratch.path();
        Files.delete(path);Files.createDirectory(path);var child=Files.writeString(path.resolve("synthetic-blocker"),"synthetic");
        try {
            assertThrows(IOException.class,scratch::close);assertThrows(IOException.class,()->source.scratch(folder,false));
            assertThrows(IOException.class,source::close);assertTrue(Files.exists(child));
        }finally{Files.deleteIfExists(child);scratch.close();source.close();}
        try(var paths=Files.list(folder)){assertEquals(0,paths.count());}
    }
    @Test void scratchOwnershipAndSuccessfulTransferAreExplicit()throws Exception {
        var test=fixture();try(var source=test.spool(ROWS)) {
            var scratch=source.scratch(folder,true);var failure=new AtomicReference<Throwable>();
            var thread=new Thread(()->{try{scratch.close();}catch(Throwable error){failure.set(error);}});thread.start();thread.join(5000);assertFalse(thread.isAlive());assertInstanceOf(IOException.class,failure.get());assertTrue(Files.exists(scratch.path()));
            var transferred=scratch.release();source.close();scratch.close();assertTrue(Files.exists(transferred));Files.delete(transferred);
        }
    }
}
