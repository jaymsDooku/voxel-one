package dev.jayms.net;

import dev.jayms.ChunkPos;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class SavedPagingWarmupTest {
    @TempDir Path folder;
    SavedPagingReplacementTest.Fixture fixture(String name)throws Exception {
        var tests=new SavedPagingReplacementTest();tests.folder=Files.createDirectories(folder.resolve(name));return tests.new Fixture(name);
    }
    static void warm(SavedPagingReplacement replacement)throws Exception {
        replacement.warmingScene().desired(List.of(new ChunkPos(0,10,0)));long end=System.nanoTime()+10_000_000_000L;
        while(replacement.warmingScene().status().data().ready()!=1&&System.nanoTime()<end){replacement.warmingScene().tick();Thread.sleep(5);}
        assertEquals(1,replacement.warmingScene().status().data().ready());
    }
    @Test public void warmedSceneTransfersLatestTailAndAcknowledgesOnlyAtTake()throws Exception {
        try(var f=fixture("transfer");var replacement=f.open()) {
            var disk=Files.readAllBytes(f.c.path);SavedPagingReplacementTest.ready(replacement);
            assertThrows(IOException.class,replacement::warmingWorld);assertEquals(SavedPagingReplacement.State.WARMING,replacement.prepare(()->false));
            var world=replacement.warmingWorld();var scene=replacement.warmingScene();var binding=replacement.warmingBinding();warm(replacement);
            assertEquals(List.of(f.saved,PendingReplayOverlayTest.fraction()),f.editor.capture(()->false).overlay().rows());
            f.c.local.health--;f.c.local.inventory.add(Blocks.STONE,1);f.c.local.drops.put(91,new ItemDrop(91,Blocks.STONE,1,1,2,3));
            assertEquals(SavedPagingReplacement.State.WARMING,replacement.poll(()->false));assertThrows(IOException.class,()->replacement.prepare(()->false));
            try(var transfer=replacement.take(()->false)) {
                var session=transfer.session();assertSame(world,session.world());assertSame(scene,session.scene());assertSame(binding,session.binding());assertEquals(1,scene.status().data().ready());
                assertEquals(f.c.local.health,session.local().health);assertEquals(f.c.local.drops,session.local().drops);assertNotSame(f.c.local.drops,session.local().drops);assertNotSame(f.c.local.inventory,session.local().inventory);
                for(int i=0;i<Inventory.SIZE;i++){assertEquals(f.c.local.inventory.type(i),session.local().inventory.type(i));assertEquals(f.c.local.inventory.count(i),session.local().inventory.count(i));}
                assertEquals(List.of(PendingReplayOverlayTest.fraction()),f.editor.capture(()->false).overlay().rows());
                assertEquals(SavedPagingReplacement.State.TRANSFERRED,replacement.status().state());replacement.close();assertEquals(1,scene.status().data().ready());
                assertThrows(IOException.class,replacement::warmingScene);assertThrows(IOException.class,session.local()::save);assertFalse(f.c.world.hasReplayReadView());assertArrayEquals(disk,Files.readAllBytes(f.c.path));
                transfer.close();RegionReplayWorkerTest.closed(transfer.worker());
            }
        }
    }
    @Test public void editsDuringWarmupRetireBeforeFreshAttempt()throws Exception {
        try(var f=fixture("later");var replacement=f.open()) {
            SavedPagingReplacementTest.ready(replacement);replacement.prepare(()->false);warm(replacement);var stale=replacement.warmingWorld();
            var later=new Protocol.Edit(6,160,2,Blocks.LED).withColor(0xed593c);f.editor.apply(later,()->false);
            assertEquals(SavedPagingReplacement.State.RETIRING,replacement.poll(()->false));assertThrows(IllegalStateException.class,()->stale.cell(0,160,0));assertThrows(IOException.class,replacement::warmingScene);
            assertEquals(3,f.editor.capture(()->false).overlay().rows().size());SavedPagingReplacementTest.ready(replacement);assertEquals(2,replacement.status().attempts());
            replacement.prepare(()->false);warm(replacement);
            try(var transfer=replacement.take(()->false)) {
                assertNotSame(stale,transfer.session().world());assertEquals(WorldVoxels.encode(later),transfer.session().world().cell(6,160,2).get(0,0,0));
                assertEquals(List.of(PendingReplayOverlayTest.fraction(),later),f.editor.capture(()->false).overlay().rows());
                transfer.close();RegionReplayWorkerTest.closed(transfer.worker());
            }
        }
    }
    @Test public void warmupChurnExhaustsThreeAttemptsWithoutAcknowledging()throws Exception {
        try(var f=fixture("bounded");var replacement=f.open()) {
            byte[] disk=Files.readAllBytes(f.c.path);
            for(int i=0;i<SavedPagingReplacement.MAX_ATTEMPTS;i++) {
                SavedPagingReplacementTest.ready(replacement);replacement.prepare(()->false);var world=replacement.warmingWorld();
                f.editor.apply(new Protocol.Edit(7+i,160,2,Blocks.STONE),()->false);assertEquals(SavedPagingReplacement.State.RETIRING,replacement.poll(()->false));
                assertThrows(IllegalStateException.class,()->world.cell(0,160,0));
            }
            SavedPagingReplacementTest.stopped(replacement);assertEquals(SavedPagingReplacement.State.RETRY_REQUIRED,replacement.status().state());assertEquals(3,replacement.status().attempts());
            assertEquals(5,f.editor.capture(()->false).overlay().rows().size());assertArrayEquals(disk,Files.readAllBytes(f.c.path));
        }
    }
    @Test public void warmupCancellationRetiresWorldAndLeavesPrefix()throws Exception {
        for(boolean take:new boolean[]{false,true})try(var f=fixture("cancel-"+take);var replacement=f.open()) {
            SavedPagingReplacementTest.ready(replacement);replacement.prepare(()->false);warm(replacement);var world=replacement.warmingWorld();
            if(take)assertThrows(CancellationException.class,()->replacement.take(()->true));else assertThrows(CancellationException.class,()->replacement.poll(()->true));
            SavedPagingReplacementTest.stopped(replacement);assertEquals(SavedPagingReplacement.State.CANCELLED,replacement.status().state());assertThrows(IllegalStateException.class,()->world.cell(0,160,0));
            assertEquals(2,f.editor.capture(()->false).overlay().rows().size());
        }
    }
    @Test public void warmupOwnerAndReentryGuardsKeepCandidatePrivate()throws Exception {
        try(var f=fixture("owner");var replacement=f.open()) {
            SavedPagingReplacementTest.ready(replacement);replacement.prepare(()->false);
            f.c.worker.submit(()->{assertThrows(IOException.class,replacement::warmingWorld);assertThrows(IOException.class,()->replacement.prepare(()->false));assertThrows(IOException.class,replacement::close);return null;}).get(10,TimeUnit.SECONDS);
            assertEquals(SavedPagingReplacement.State.WARMING,replacement.poll(()->{assertThrows(IOException.class,replacement::warmingScene);assertThrows(IOException.class,replacement::close);return false;}));
            replacement.close();SavedPagingReplacementTest.stopped(replacement);assertEquals(2,f.editor.capture(()->false).overlay().rows().size());
        }
    }
    @Test public void everyWarmFinalTransferCancellationHookKeepsPrefixAndDisk()throws Exception {
        int count;
        try(var f=fixture("hooks");var replacement=f.open()) {
            SavedPagingReplacementTest.ready(replacement);replacement.prepare(()->false);var calls=new AtomicInteger();
            try(var transfer=replacement.take(()->{calls.incrementAndGet();return false;})){count=calls.get();transfer.close();RegionReplayWorkerTest.closed(transfer.worker());}
        }
        assertTrue(count>10&&count<=256);
        for(int stop=1;stop<=count;stop++)try(var f=fixture("hook-"+stop);var replacement=f.open()) {
            var disk=Files.readAllBytes(f.c.path);SavedPagingReplacementTest.ready(replacement);replacement.prepare(()->false);var world=replacement.warmingWorld();var calls=new AtomicInteger();int point=stop;
            assertThrows(CancellationException.class,()->replacement.take(()->calls.incrementAndGet()==point));SavedPagingReplacementTest.stopped(replacement);
            assertEquals(point,calls.get());assertEquals(SavedPagingReplacement.State.CANCELLED,replacement.status().state());assertThrows(IllegalStateException.class,()->world.cell(0,160,0));
            assertEquals(2,f.editor.capture(()->false).overlay().rows().size());assertArrayEquals(disk,Files.readAllBytes(f.c.path));
        }
    }
    @Test public void warmupCleanupFailureWaitsForExplicitClose()throws Exception {
        try(var f=fixture("cleanup");var replacement=f.open()) {
            SavedPagingReplacementTest.ready(replacement);replacement.prepare(()->false);var world=replacement.warmingWorld();Path blocked;
            try(var files=Files.list(f.directory)){blocked=files.filter(p->p.getFileName().toString().startsWith(".replay-spool-")).findFirst().orElseThrow();}
            Files.delete(blocked);Files.createDirectory(blocked);Files.writeString(blocked.resolve("hold"),"synthetic");
            try {
                f.editor.apply(new Protocol.Edit(6,160,2,Blocks.STONE),()->false);replacement.poll(()->false);
                long end=System.nanoTime()+10_000_000_000L;
                while(replacement.status().worker().state()!=RegionReplayWorker.State.CLEANUP_FAILED&&System.nanoTime()<end){replacement.poll(()->false);Thread.sleep(5);}
                assertEquals(RegionReplayWorker.State.CLEANUP_FAILED,replacement.status().worker().state());assertThrows(IllegalStateException.class,()->world.cell(0,160,0));
                Files.delete(blocked.resolve("hold"));Files.delete(blocked);
                for(int i=0;i<5;i++){assertEquals(SavedPagingReplacement.State.RETIRING,replacement.poll(()->false));assertEquals(RegionReplayWorker.State.CLEANUP_FAILED,replacement.status().worker().state());}
                assertThrows(CancellationException.class,()->replacement.poll(()->true));assertEquals(RegionReplayWorker.State.CLEANUP_FAILED,replacement.status().worker().state());
                assertEquals(1,replacement.status().attempts());replacement.close();SavedPagingReplacementTest.stopped(replacement);assertEquals(SavedPagingReplacement.State.CANCELLED,replacement.status().state());
            }finally{Files.deleteIfExists(blocked.resolve("hold"));Files.deleteIfExists(blocked);replacement.close();SavedPagingReplacementTest.stopped(replacement);}
            assertEquals(3,f.editor.capture(()->false).overlay().rows().size());
        }
    }
}
