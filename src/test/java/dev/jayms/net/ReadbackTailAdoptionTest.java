package dev.jayms.net;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class ReadbackTailAdoptionTest {
    @TempDir Path folder;
    ReadbackSaveSnapshotTest contexts()throws IOException {var t=new ReadbackSaveSnapshotTest();t.folder=Files.createDirectories(folder.resolve("contexts"));return t;}
    @Test public void ownerAdoptsCurrentTailWithFreshGenerationAndIndependentCopies()throws Exception {
        try(var c=contexts().new Context("adopt");var prepared=c.stage(c.snapshot())) {
            var publication=prepared.authorize(()->false);var directory=Files.createDirectories(c.fixture.folder.resolve("next"));
            var source=c.worker.submit(()->{publication.publish();return publication.reopenPublished(directory,ReadbackMetadataTest.REV,()->false,n->{});}).get(10,TimeUnit.SECONDS);
            try {
                c.local.health--;c.local.inventory.add(Blocks.STONE,1);c.local.drops.put(99,new ItemDrop(99,Blocks.STONE,1,1,2,3));
                c.worker.submit(()->{assertThrows(IOException.class,()->publication.adoptCurrentTail(source,()->false));return null;}).get(10,TimeUnit.SECONDS);
                var forged=new RegionReplayWorker.Source(source.spool(),source.terrain(),source.allowed(),source.revision(),source.fingerprint(),source.metadata());
                assertThrows(IOException.class,()->publication.adoptCurrentTail(forged,()->false));
                assertThrows(CancellationException.class,()->publication.adoptCurrentTail(source,()->true));
                var next=publication.adoptCurrentTail(source,()->false);
                assertEquals(c.local.health,next.health);assertEquals(c.local.drops,next.drops);assertNotSame(c.local.drops,next.drops);assertNotSame(c.local.inventory,next.inventory);assertSame(c.local.models,next.models);assertSame(c.world.models(),next.models);
                assertSame(source.metadata().generation(),next.readbackGeneration());assertEquals(source.fingerprint(),next.readbackFingerprint());assertNotSame(c.local.readbackGeneration(),next.readbackGeneration());assertFalse(next.replayComplete());assertTrue(next.edits.isEmpty());assertThrows(IOException.class,next::save);
                assertThrows(IOException.class,()->publication.adoptCurrentTail(source,()->false));
                var snapshot=ReadbackSaveSnapshot.capture(next,c.world,RegionReplayOverlay.empty(),()->false);
                var stage=c.worker.submit(()->snapshot.stage(source,()->false,n->{})).get(10,TimeUnit.SECONDS);
                try(stage){var save=stage.authorize(()->false);c.worker.submit(()->{save.publish();save.close();return null;}).get(10,TimeUnit.SECONDS);}
                var saved=new LocalGame(c.path,0);assertEquals(next.health,saved.health);assertEquals(next.drops,saved.drops);
                next.inventory.add(Blocks.STONE,1);assertNotEquals(c.local.inventory.count(0),next.inventory.count(0));next.drops.clear();assertFalse(c.local.drops.isEmpty());
            }finally{c.worker.submit(()->{source.spool().close();publication.close();return null;}).get(10,TimeUnit.SECONDS);}
            c.noPrefixes();
        }
    }
    @Test public void geometryModelsAndInvalidTailRejectWithoutConsumingMetadata()throws Exception {
        var contexts=contexts();
        for(int mode=0;mode<3;mode++)try(var c=contexts.new Context("stale-"+mode);var prepared=c.stage(c.snapshot())) {
            var publication=prepared.authorize(()->false);var directory=Files.createDirectories(c.fixture.folder.resolve("next"));
            var source=c.worker.submit(()->{publication.publish();return publication.reopenPublished(directory,ReadbackMetadataTest.REV,()->false,n->{});}).get(10,TimeUnit.SECONDS);
            byte[] before=Files.readAllBytes(c.path);
            try {
                switch(mode){case 0->c.world.apply(new Protocol.Edit(3,160,0,Blocks.GLASS));case 1->c.local.models=c.local.models.copy();case 2->c.local.health=-1;}
                assertThrows(IOException.class,()->publication.adoptCurrentTail(source,()->false));assertArrayEquals(before,Files.readAllBytes(c.path));
                // Rejection must leave the independently decoded metadata transferable.
                var untouched=source.metadata().transfer();assertEquals(17,untouched.health);assertFalse(untouched.replayComplete());
            }finally{c.worker.submit(()->{source.spool().close();publication.close();return null;}).get(10,TimeUnit.SECONDS);}
        }
    }
}
