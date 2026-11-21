package dev.jayms.net;

import dev.jayms.World;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ReadbackSaveSnapshotTest {
    @TempDir Path folder;
    final class Context implements AutoCloseable {
        final ReadbackMetadataTest fixture=new ReadbackMetadataTest();final Path path;final byte[] original;
        final ExecutorService worker=Executors.newSingleThreadExecutor();
        final RegionReplayWorker.Source source;final LocalGame local;final World world;
        Context(String name)throws Exception {
            fixture.folder=Files.createDirectories(folder.resolve(name));path=fixture.fixture();original=Files.readAllBytes(path);
            source=worker.submit(()->LocalGame.openRegionalReadback(path,fixture.spool(),ReadbackMetadataTest.REV,()->false,n->{})).get(10,TimeUnit.SECONDS);
            local=source.metadata().transfer();world=new World(local.seed,local.models,local.generatorVersion);
        }
        ReadbackSaveSnapshot snapshot()throws Exception{return ReadbackSaveSnapshot.capture(local,world,RegionReplayOverlay.capture(List.of(new Protocol.Edit(0,160,0,0)),()->false),()->false);}
        ReadbackSaveSnapshot.Prepared stage(ReadbackSaveSnapshot snapshot)throws Exception{return worker.submit(()->snapshot.stage(source,()->false,n->{})).get(10,TimeUnit.SECONDS);}
        void noPrefixes()throws Exception{try(var paths=Files.list(fixture.folder)){assertFalse(paths.anyMatch(p->p.getFileName().toString().startsWith(".structure-offline-")));}}
        @Override public void close()throws Exception {
            try{world.close();worker.submit(()->{source.spool().close();return null;}).get(10,TimeUnit.SECONDS);fixture.noSpools();}
            finally{worker.shutdownNow();assertTrue(worker.awaitTermination(10,TimeUnit.SECONDS));}
        }
    }
    @Test void ownerCaptureWorkerStageOwnerCommitPreservesTailAndModelsWithoutPromotion()throws Exception {
        try(var c=new Context("roundtrip")) {
            var before=new LocalGame(c.path,0);var snapshot=c.snapshot();
            try(var prepared=c.stage(snapshot)){assertArrayEquals(c.original,Files.readAllBytes(c.path));prepared.commit(()->false);}
            var after=new LocalGame(c.path,0);var expected=new WorldVoxels.History();before.edits.values().forEach(e->WorldVoxels.remember(expected,e));WorldVoxels.remember(expected,new Protocol.Edit(0,160,0,0));
            assertEquals(new ArrayList<>(expected.values()),new ArrayList<>(after.edits.values()));assertEquals(before.health,after.health);assertEquals(before.drops,after.drops);
            for(int i=0;i<Inventory.SIZE;i++){assertEquals(before.inventory.type(i),after.inventory.type(i));assertEquals(before.inventory.count(i),after.inventory.count(i));}
            for(var entry:before.models.entries())assertEquals(entry.definition().fingerprint(),after.models.get(entry.id()).definition().fingerprint());
            assertTrue(after.models.entries().stream().anyMatch(e->e.definition().voxels().size()==32));
            assertEquals(0,c.world.editsVersion());assertTrue(c.world.getLoadedChunks().isEmpty());assertFalse(c.local.replayComplete());assertThrows(IOException.class,c.local::save);c.noPrefixes();
        }
    }
    @Test void changedHealthInventoryDropsModelsAndWorldRejectBeforePublication()throws Exception {
        for(int mode=0;mode<5;mode++)try(var c=new Context("stale-"+mode);var prepared=c.stage(c.snapshot())) {
            switch(mode) {
                case 0->c.local.health--;
                case 1->c.local.inventory.add(Blocks.STONE,1);
                case 2->c.local.drops.put(99,new ItemDrop(99,Blocks.STONE,1,1,2,3));
                case 3->c.local.models=c.local.models.copy();
                case 4->c.world.apply(new Protocol.Edit(3,160,0,Blocks.STONE));
            }
            assertThrows(IOException.class,()->prepared.commit(()->false));assertArrayEquals(c.original,Files.readAllBytes(c.path));c.noPrefixes();
        }
    }
    @Test void sameDigestForeignSourceAndChangedTerrainCannotStageThisCapture()throws Exception {
        try(var a=new Context("a");var b=new Context("b")) {
            assertEquals(a.source.fingerprint(),b.source.fingerprint());var snapshot=a.snapshot();
            b.worker.submit(()->{assertThrows(IOException.class,()->snapshot.stage(b.source,()->false,n->{}));return null;}).get(10,TimeUnit.SECONDS);a.noPrefixes();b.noPrefixes();
            a.worker.submit(()->{a.source.terrain().stressGrid(null);assertThrows(IOException.class,()->snapshot.stage(a.source,()->false,n->{}));return null;}).get(10,TimeUnit.SECONDS);a.noPrefixes();
        }
    }
    @Test void wrongOwnerCannotConsumePrefixAndCancellationCleansOnlyOwnedStage()throws Exception {
        try(var c=new Context("owner")) {
            var snapshot=c.snapshot();
            try(var prepared=c.stage(snapshot)) {
                c.worker.submit(()->{assertThrows(IOException.class,()->prepared.commit(()->false));return null;}).get(10,TimeUnit.SECONDS);
                assertArrayEquals(c.original,Files.readAllBytes(c.path));prepared.commit(()->false);
            }
        }
        try(var c=new Context("cancel")) {
            assertThrows(CancellationException.class,()->ReadbackSaveSnapshot.capture(c.local,c.world,RegionReplayOverlay.empty(),()->true));
            var snapshot=c.snapshot();
            try(var prepared=c.stage(snapshot)){assertThrows(CancellationException.class,()->prepared.commit(()->true));assertArrayEquals(c.original,Files.readAllBytes(c.path));c.noPrefixes();}
            try(var retry=c.stage(snapshot)){retry.commit(()->false);}c.noPrefixes();
        }
    }
}
