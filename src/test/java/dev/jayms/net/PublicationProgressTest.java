package dev.jayms.net;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class PublicationProgressTest {
    @TempDir Path folder;
    @Test public void boundedHashProgressAndCancellationPreserveGeneration()throws Exception {
        byte[] bytes=new byte[3*65536+17];new Random(91).nextBytes(bytes);
        var target=Files.write(folder.resolve("world.dat"),bytes);var source=Files.writeString(folder.resolve("stage.tmp"),"snapshot");
        var generation=WorldSavePublication.capture(target,bytes.length,()->false);assertEquals(bytes.length,generation.bytes());
        var cancelled=new AtomicBoolean();var progress=new ArrayList<Long>();var claimed=new AtomicBoolean();
        assertThrows(CancellationException.class,()->WorldSavePublication.replaceIfUnchanged(source,target,generation,cancelled::get,()->{claimed.set(true);return true;},n->{progress.add(n);if(n>=65536)cancelled.set(true);}));
        assertEquals(List.of(0L,65536L),progress);assertFalse(claimed.get());assertArrayEquals(bytes,Files.readAllBytes(target));assertEquals("snapshot",Files.readString(source));
        progress.clear();WorldSavePublication.replaceIfUnchanged(source,target,generation,()->false,()->true,progress::add);
        assertEquals(List.of(0L,65536L,131072L,196608L,196625L),progress);assertEquals("snapshot",Files.readString(target));
    }
    @Test public void observerFailureCannotPublishOrAdvanceEpoch()throws Exception {
        var target=Files.writeString(folder.resolve("world.dat"),"original");var source=Files.writeString(folder.resolve("stage.tmp"),"snapshot");
        var generation=WorldSavePublication.capture(target,1024,()->false);var failure=new IllegalStateException("Synthetic observer failure");
        assertSame(failure,assertThrows(IllegalStateException.class,()->WorldSavePublication.replaceIfUnchanged(source,target,generation,()->false,()->true,n->{if(n>0)throw failure;})));
        assertEquals("original",Files.readString(target));WorldSavePublication.replaceIfUnchanged(source,target,generation,()->false);assertEquals("snapshot",Files.readString(target));
    }
    @Test public void publicationOwnerCanObserveWorkerProgressWithoutFileAccess()throws Exception {
        var tests=new ReadbackSaveSnapshotTest();tests.folder=Files.createDirectories(folder.resolve("contexts"));
        try(var c=tests.new Context("progress");var prepared=c.stage(c.snapshot())) {
            var publication=prepared.authorize(()->false);var before=publication.progress();
            assertEquals(ReadbackSaveSnapshot.Phase.AUTHORIZED,before.phase());assertEquals(0,before.verifiedBytes());assertEquals(c.original.length,before.totalBytes());
            var work=c.worker.submit(()->{publication.publish();publication.close();return null;});
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(!work.isDone()&&System.nanoTime()<deadline){var state=publication.progress();assertTrue(state.verifiedBytes()>=0&&state.verifiedBytes()<=state.totalBytes());Thread.sleep(1);}
            work.get(10,TimeUnit.SECONDS);var after=publication.progress();assertEquals(ReadbackSaveSnapshot.Phase.PUBLISHED,after.phase());assertEquals(after.totalBytes(),after.verifiedBytes());assertTrue(publication.snapshotCurrent());c.noPrefixes();
        }
    }
}
