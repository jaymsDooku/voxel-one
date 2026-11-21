package dev.jayms.net;

import dev.jayms.World;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class JournalTailAdoptionTest {
    @TempDir Path folder;
    ReadbackSaveSnapshotTest contexts()throws IOException {var t=new ReadbackSaveSnapshotTest();t.folder=Files.createDirectories(folder.resolve("contexts"));return t;}
    @Test public void trackedEditsDuringSaveRemainLiveAndSurviveNextSave()throws Exception {
        try(var c=contexts().new Context("tracked")) {
            var journal=new RegionReplayJournal();var editor=new RegionReplayEditor(c.world,journal);var saved=new Protocol.Edit(0,160,0,0);var later=new Protocol.Edit(3,160,0,Blocks.GLASS);
            editor.apply(saved,()->false);var prefix=editor.capture(()->false);var snapshot=ReadbackSaveSnapshot.captureJournal(c.local,c.world,prefix,()->false);
            try(var prepared=c.stage(snapshot)) {
                var publication=prepared.authorize(()->false);editor.apply(later,()->false);c.local.health--;
                var directory=Files.createDirectories(c.fixture.folder.resolve("next"));var source=c.worker.submit(()->{publication.publish();return publication.reopenPublished(directory,ReadbackMetadataTest.REV,()->false,n->{});}).get(10,TimeUnit.SECONDS);
                try {
                    assertThrows(IOException.class,()->publication.adoptCurrentTail(source,()->false));assertThrows(CancellationException.class,()->publication.adoptJournalTail(source,()->true));
                    c.worker.submit(()->{assertThrows(IOException.class,()->publication.adoptJournalTail(source,()->false));return null;}).get(10,TimeUnit.SECONDS);
                    var next=publication.adoptJournalTail(source,()->false);assertEquals(c.local.health,next.health);assertEquals(2,c.world.editsVersion());assertEquals(List.of(saved,later),editor.capture(()->false).overlay().rows());
                    assertEquals(List.of(later),publication.acknowledgeOverlay(source,()->false).rows());assertEquals(2,c.world.editsVersion());
                    var first=new LocalGame(c.path,0);assertTrue(first.edits.values().contains(saved));assertFalse(first.edits.values().contains(later));
                    var nextSnapshot=ReadbackSaveSnapshot.captureJournal(next,c.world,editor.capture(()->false),()->false);
                    var second=c.worker.submit(()->nextSnapshot.stage(source,()->false,n->{})).get(10,TimeUnit.SECONDS);
                    try(second){var write=second.authorize(()->false);c.worker.submit(()->{write.publish();write.close();return null;}).get(10,TimeUnit.SECONDS);}
                    var after=new LocalGame(c.path,0);assertTrue(after.edits.values().contains(saved));assertTrue(after.edits.values().contains(later));assertEquals(next.health,after.health);assertFalse(next.replayComplete());assertThrows(IOException.class,next::save);
                }finally{c.worker.submit(()->{source.spool().close();publication.close();return null;}).get(10,TimeUnit.SECONDS);}
            }c.noPrefixes();
        }
    }
    @Test public void untrackedChangesModelsAndUnboundJournalCannotAdoptMetadata()throws Exception {
        var tests=contexts();
        for(int mode=0;mode<3;mode++)try(var c=tests.new Context("reject-"+mode)) {
            var journal=new RegionReplayJournal();var saved=new Protocol.Edit(0,160,0,0);
            if(mode==2)journal.append(List.of(saved),()->false);else new RegionReplayEditor(c.world,journal).apply(saved,()->false);
            var snapshot=ReadbackSaveSnapshot.captureJournal(c.local,c.world,journal.capture(()->false),()->false);
            try(var prepared=c.stage(snapshot)) {
                var publication=prepared.authorize(()->false);var directory=Files.createDirectories(c.fixture.folder.resolve("next"));
                var source=c.worker.submit(()->{publication.publish();return publication.reopenPublished(directory,ReadbackMetadataTest.REV,()->false,n->{});}).get(10,TimeUnit.SECONDS);
                try {
                    if(mode==0)c.world.apply(new Protocol.Edit(3,160,0,Blocks.GLASS));else if(mode==1)c.local.models=c.local.models.copy();
                    byte[] before=Files.readAllBytes(c.path);assertThrows(IOException.class,()->publication.adoptJournalTail(source,()->false));assertArrayEquals(before,Files.readAllBytes(c.path));assertNotNull(source.metadata().transfer());
                }finally{c.worker.submit(()->{source.spool().close();publication.close();return null;}).get(10,TimeUnit.SECONDS);}
            }
        }
        try(var c=tests.new Context("foreign");var other=new World(42)) {
            var journal=new RegionReplayJournal();var editor=new RegionReplayEditor(other,journal);var prefix=editor.capture(()->false);
            assertThrows(IOException.class,()->ReadbackSaveSnapshot.captureJournal(c.local,c.world,prefix,()->false));
        }
    }
}
