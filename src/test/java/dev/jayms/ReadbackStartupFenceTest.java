package dev.jayms;

import dev.jayms.net.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ReadbackStartupFenceTest {
    @TempDir Path folder;
    @Test void metadataOnlyReplayRejectsBeforeWorldFactoryEvenWithAResidentHistory()throws Exception {
        var local=ReadbackMetadataTest.partialForTests(folder);var path=folder.resolve("partial.dat");byte[] before=Files.readAllBytes(path);
        local.edits=new WorldVoxels.History();var called=new AtomicBoolean();
        assertThrows(IOException.class,()->new OfflineWorldReplay(local,null,(seed,models,generator)->{
            called.set(true);throw new AssertionError("Partial metadata reached world factory");
        }));
        assertFalse(called.get());assertArrayEquals(before,Files.readAllBytes(path));assertFalse(local.replayComplete());
    }
}
