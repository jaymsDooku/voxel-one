package dev.jayms.net;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class ColdReplaySceneTest {
    @TempDir Path folder;
    ReadbackMetadataTest fixture(String name)throws Exception {
        var tests=new ReadbackMetadataTest();tests.folder=Files.createDirectories(folder.resolve(name));return tests;
    }
    @Test public void coldSceneTransfersSavedMetadataOnceWithoutCompleteReplayOrSaveAuthority()throws Exception {
        var f=fixture("cold");var path=f.fixture();var disk=Files.readAllBytes(path);var worker=f.worker(path);
        try {
            ReadbackMetadataTest.ready(worker);
            try(var session=worker.metadataResultScene(ReadbackMetadataTest.REV,1,8192,()->{})) {
                assertFalse(session.local().replayComplete());assertTrue(session.local().edits.isEmpty());assertNull(session.local().city);assertEquals(17,session.local().health);assertEquals(2,session.local().drops.size());
                assertEquals(3,session.local().inventory.countType(Blocks.STONE));assertEquals(1,session.local().inventory.countType(9));assertTrue(session.local().models.hasStructureModels());assertSame(session.local().models,session.world().models());
                assertTrue(session.world().hasReplayReadView());assertTrue(session.world().getLoadedChunks().isEmpty());assertEquals(0,session.scene().status().data().ready());assertTrue(session.journal().capture(()->false).overlay().rows().isEmpty());
                assertThrows(IOException.class,session.local()::save);assertThrows(IOException.class,()->session.local().startGame(dev.jayms.net.city.GameConfig.sandbox(),null));
                assertThrows(IOException.class,()->worker.metadataResult(ReadbackMetadataTest.REV));assertThrows(IOException.class,()->worker.metadataResultScene(ReadbackMetadataTest.REV,1,8192,()->{}));assertArrayEquals(disk,Files.readAllBytes(path));
            }
        }finally{RegionReplayWorkerTest.closed(worker);f.noSpools();}
    }
    @Test public void failedGuardAndReentryKeepColdMetadataAvailableForRetry()throws Exception {
        var f=fixture("retry");var worker=f.worker(f.fixture());
        try {
            ReadbackMetadataTest.ready(worker);
            assertThrows(CancellationException.class,()->worker.metadataResultScene(ReadbackMetadataTest.REV,1,8192,()->{
                assertThrows(IOException.class,()->worker.metadataResult(ReadbackMetadataTest.REV));assertThrows(IOException.class,()->worker.metadataResultScene(ReadbackMetadataTest.REV,1,8192,()->{}));throw new CancellationException();
            }));
            assertThrows(IOException.class,()->worker.metadataResultScene(ReadbackMetadataTest.REV,1,8192,()->{throw new IOException("Synthetic owner guard");}));
            try(var session=worker.metadataResultScene(ReadbackMetadataTest.REV,1,8192,()->{})){assertEquals(17,session.local().health);assertFalse(session.world().isClosed());}
        }finally{RegionReplayWorkerTest.closed(worker);f.noSpools();}
    }
    @Test public void invalidBudgetRevisionAndForeignOwnerRejectBeforeConsumption()throws Exception {
        var f=fixture("guards");var worker=f.worker(f.fixture());var executor=Executors.newSingleThreadExecutor();
        try {
            ReadbackMetadataTest.ready(worker);
            assertThrows(IllegalArgumentException.class,()->worker.metadataResultScene(ReadbackMetadataTest.REV,3,8192,()->{}));
            assertThrows(IllegalArgumentException.class,()->worker.metadataResultScene(ReadbackMetadataTest.REV,1,RegionReplayChunk.MAX_CELL_LEAVES+1,()->{}));
            assertThrows(IOException.class,()->worker.metadataResultScene(new RegionReplayWorker.Revision(9,9,9),1,8192,()->{}));
            executor.submit(()->{assertThrows(IOException.class,()->worker.metadataResultScene(ReadbackMetadataTest.REV,1,8192,()->{}));return null;}).get(10,TimeUnit.SECONDS);
            try(var session=worker.metadataResultScene(ReadbackMetadataTest.REV,1,8192,()->{})){assertNotNull(session.world());}
        }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS));RegionReplayWorkerTest.closed(worker);f.noSpools();}
    }
    @Test public void pendingOverlayCannotBeMistakenForColdSavedBaseline()throws Exception {
        var f=fixture("overlay");var path=f.fixture();var scratch=f.spool();var pending=RegionReplayOverlay.capture(java.util.List.of(new Protocol.Edit(4,160,0,Blocks.STONE)),()->false);
        var worker=new RegionReplayWorker(scratch,(cancel,progress)->{var source=LocalGame.openRegionalReadback(path,scratch,ReadbackMetadataTest.REV,cancel,progress);return new RegionReplayWorker.Source(source.spool(),source.terrain(),source.allowed(),source.revision(),source.fingerprint(),source.metadata(),pending);},1,1,56,2);
        try{ReadbackMetadataTest.ready(worker);assertThrows(IOException.class,()->worker.metadataResultScene(ReadbackMetadataTest.REV,1,8192,()->{}));assertFalse(worker.metadataResult(ReadbackMetadataTest.REV).replayComplete());}
        finally{RegionReplayWorkerTest.closed(worker);f.noSpools();}
    }
}
