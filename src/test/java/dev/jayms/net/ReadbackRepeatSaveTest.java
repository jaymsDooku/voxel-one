package dev.jayms.net;

import dev.jayms.World;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class ReadbackRepeatSaveTest {
    @TempDir Path folder;
    ReadbackSaveSnapshotTest contexts()throws IOException {var tests=new ReadbackSaveSnapshotTest();tests.folder=Files.createDirectories(folder.resolve("contexts"));return tests;}
    @Test public void freshSourceSupportsSecondSaveWithoutReplacingLiveOwnerState()throws Exception {
        try(var c=contexts().new Context("twice");var prepared=c.stage(c.snapshot())) {
            var publication=prepared.authorize(()->false);c.local.health--;int liveHealth=c.local.health;
            var directory=Files.createDirectories(c.fixture.folder.resolve("next-source"));
            c.worker.submit(()->{assertThrows(IOException.class,()->publication.reopenPublished(directory,ReadbackMetadataTest.REV,()->false,n->{}));publication.publish();return null;}).get(10,TimeUnit.SECONDS);
            assertThrows(IOException.class,()->publication.reopenPublished(directory,ReadbackMetadataTest.REV,()->false,n->{}));
            var source=c.worker.submit(()->publication.reopenPublished(directory,ReadbackMetadataTest.REV,()->false,n->{})).get(10,TimeUnit.SECONDS);
            try {
                assertNotEquals(c.local.readbackFingerprint(),source.fingerprint());assertNotSame(c.local.readbackGeneration(),source.metadata().generation());
                assertEquals(liveHealth,c.local.health);assertFalse(publication.snapshotCurrent());
                var next=source.metadata().transfer();next.health=liveHealth;
                try(var world=new World(next.seed,next.models,next.generatorVersion)) {
                    var overlay=RegionReplayOverlay.capture(List.of(new Protocol.Edit(3,160,0,Blocks.GLASS)),()->false);
                    var snapshot=ReadbackSaveSnapshot.capture(next,world,overlay,()->false);
                    var second=c.worker.submit(()->snapshot.stage(source,()->false,n->{})).get(10,TimeUnit.SECONDS);
                    try(second){var write=second.authorize(()->false);c.worker.submit(()->{write.publish();write.close();return null;}).get(10,TimeUnit.SECONDS);assertTrue(write.snapshotCurrent());}
                }
                var saved=new LocalGame(c.path,0);assertEquals(liveHealth,saved.health);assertTrue(saved.edits.values().stream().anyMatch(e->e.type()==Blocks.GLASS));
                assertEquals(c.local.drops,saved.drops);assertFalse(c.local.replayComplete());assertThrows(IOException.class,c.local::save);
                c.worker.submit(()->{assertThrows(IOException.class,()->publication.reopenPublished(directory,ReadbackMetadataTest.REV,()->false,n->{}));return null;}).get(10,TimeUnit.SECONDS);
            }finally{c.worker.submit(()->{source.spool().close();publication.close();return null;}).get(10,TimeUnit.SECONDS);}
            try(var files=Files.list(directory)){assertEquals(0,files.count());}c.noPrefixes();
        }
    }
    @Test public void cancelledReopenRetriesButDifferentValidSnapshotCannotBeAdopted()throws Exception {
        try(var c=contexts().new Context("reopen");var prepared=c.stage(c.snapshot())) {
            var publication=prepared.authorize(()->false);var directory=Files.createDirectories(c.fixture.folder.resolve("reopen-source"));
            c.worker.submit(()->{publication.publish();assertThrows(CancellationException.class,()->publication.reopenPublished(directory,ReadbackMetadataTest.REV,()->true,n->{}));return null;}).get(10,TimeUnit.SECONDS);
            try(var files=Files.list(directory)){assertEquals(0,files.count());}
            byte[] published=Files.readAllBytes(c.path);Files.write(c.path,c.original); // Different, fully valid world.
            c.worker.submit(()->{assertThrows(IOException.class,()->publication.reopenPublished(directory,ReadbackMetadataTest.REV,()->false,n->{}));return null;}).get(10,TimeUnit.SECONDS);
            assertArrayEquals(c.original,Files.readAllBytes(c.path));try(var files=Files.list(directory)){assertEquals(0,files.count());}
            Files.write(c.path,published);
            c.worker.submit(()->{var source=publication.reopenPublished(directory,ReadbackMetadataTest.REV,()->false,n->{});source.spool().close();publication.close();return null;}).get(10,TimeUnit.SECONDS);
            c.noPrefixes();try(var files=Files.list(directory)){assertEquals(0,files.count());}
        }
    }
}
