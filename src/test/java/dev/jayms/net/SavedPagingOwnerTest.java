package dev.jayms.net;

import dev.jayms.World;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class SavedPagingOwnerTest {
    @TempDir Path folder;
    SavedPagingReplacementTest.Fixture fixture(String name)throws Exception {
        var tests=new SavedPagingReplacementTest();tests.folder=Files.createDirectories(folder.resolve(name));return tests.new Fixture(name);
    }
    @Test public void immediateAndWarmInstallPublishOneCoherentViewAndKeepRetirementExplicit()throws Exception {
        for(boolean warm:new boolean[]{false,true})try(var f=fixture("install-"+warm);var replacement=f.open();var owner=new SavedPagingOwner(f.c.local,f.c.world)) {
            var old=owner.view();assertSame(f.c.local,old.local());assertSame(f.c.world,old.world());assertNull(old.transfer());var disk=Files.readAllBytes(f.c.path);
            SavedPagingReplacementTest.ready(replacement);if(warm){replacement.prepare(()->false);SavedPagingWarmupTest.warm(replacement);}
            var transfer=replacement.takeInto(owner,()->false);var view=owner.view();assertNotSame(old,view);assertSame(transfer,view.transfer());assertSame(transfer.session().local(),view.local());assertSame(transfer.session().world(),view.world());
            assertThrows(IOException.class,old::local);assertThrows(IOException.class,old::world);assertThrows(IOException.class,old::transfer);assertFalse(f.c.world.isClosed());
            assertEquals(List.of(PendingReplayOverlayTest.fraction()),f.editor.capture(()->false).overlay().rows());assertArrayEquals(disk,Files.readAllBytes(f.c.path));
            var nextWorld=view.world();owner.close();RegionReplayWorkerTest.closed(transfer.worker());assertTrue(nextWorld.isClosed());assertFalse(f.c.world.isClosed());assertThrows(IOException.class,owner::view);assertThrows(IOException.class,view::world);
            transfer.retirePrevious(f.c.world,()->false);assertTrue(f.c.world.isClosed());owner.close();
        }
    }
    @Test public void wrongSlotRejectsBeforeSourceTransferAndMatchingSlotCanStillInstall()throws Exception {
        try(var f=fixture("right");var other=fixture("foreign");var replacement=f.open();var right=new SavedPagingOwner(f.c.local,f.c.world);var wrong=new SavedPagingOwner(other.c.local,other.c.world)) {
            SavedPagingReplacementTest.ready(replacement);var old=right.view();var foreign=wrong.view();
            assertThrows(IOException.class,()->replacement.takeInto(wrong,()->false));assertSame(old,right.view());assertSame(foreign,wrong.view());assertEquals(SavedPagingReplacement.State.READY,replacement.status().state());assertEquals(2,f.editor.capture(()->false).overlay().rows().size());
            var transfer=replacement.takeInto(right,()->false);assertSame(transfer,right.view().transfer());assertSame(foreign,wrong.view());transfer.retirePrevious(f.c.world,()->false);right.close();RegionReplayWorkerTest.closed(transfer.worker());
        }
    }
    @Test public void everyCancellationHookLeavesSlotAndSavedPrefixUntouched()throws Exception {
        int count;
        try(var f=fixture("hooks");var replacement=f.open();var owner=new SavedPagingOwner(f.c.local,f.c.world)) {
            SavedPagingReplacementTest.ready(replacement);replacement.prepare(()->false);var calls=new AtomicInteger();var transfer=replacement.takeInto(owner,()->{calls.incrementAndGet();return false;});count=calls.get();
            transfer.retirePrevious(f.c.world,()->false);owner.close();RegionReplayWorkerTest.closed(transfer.worker());
        }
        assertTrue(count>10&&count<=256);
        for(int stop=1;stop<=count;stop++)try(var f=fixture("hook-"+stop);var replacement=f.open();var owner=new SavedPagingOwner(f.c.local,f.c.world)) {
            SavedPagingReplacementTest.ready(replacement);replacement.prepare(()->false);var old=owner.view();var disk=Files.readAllBytes(f.c.path);var calls=new AtomicInteger();int point=stop;
            assertThrows(CancellationException.class,()->replacement.takeInto(owner,()->calls.incrementAndGet()==point));assertEquals(point,calls.get());assertSame(old,owner.view());assertSame(f.c.world,owner.view().world());assertFalse(f.c.world.isClosed());
            assertEquals(2,f.editor.capture(()->false).overlay().rows().size());assertArrayEquals(disk,Files.readAllBytes(f.c.path));SavedPagingReplacementTest.stopped(replacement);
        }
    }
    @Test public void laterGeometryRejectsInstallThenFreshCandidateCanUseSameSlot()throws Exception {
        try(var f=fixture("later");var replacement=f.open();var owner=new SavedPagingOwner(f.c.local,f.c.world)) {
            SavedPagingReplacementTest.ready(replacement);replacement.prepare(()->false);var old=owner.view();var calls=new AtomicInteger();var later=new Protocol.Edit(6,160,2,Blocks.STONE);
            assertThrows(IOException.class,()->replacement.takeInto(owner,()->{if(calls.incrementAndGet()==1)try{f.editor.apply(later,()->false);}catch(Exception error){throw new RuntimeException(error);}return false;}));
            assertSame(old,owner.view());assertEquals(3,f.editor.capture(()->false).overlay().rows().size());SavedPagingReplacementTest.ready(replacement);assertEquals(2,replacement.status().attempts());
            replacement.prepare(()->false);SavedPagingWarmupTest.warm(replacement);var transfer=replacement.takeInto(owner,()->false);
            assertEquals(WorldVoxels.encode(later),owner.view().world().cell(6,160,2).get(0,0,0));assertSame(transfer.session().local(),owner.view().local());transfer.retirePrevious(f.c.world,()->false);owner.close();RegionReplayWorkerTest.closed(transfer.worker());
        }
    }
    @Test public void slotForeignOwnerAndCallbackReentryNeverExposeIntermediateView()throws Exception {
        try(var f=fixture("owner");var replacement=f.open();var owner=new SavedPagingOwner(f.c.local,f.c.world)) {
            SavedPagingReplacementTest.ready(replacement);var old=owner.view();
            f.c.worker.submit(()->{assertThrows(IOException.class,owner::view);assertThrows(IOException.class,old::world);assertThrows(IOException.class,owner::close);assertThrows(IOException.class,()->replacement.takeInto(owner,()->false));return null;}).get(10,TimeUnit.SECONDS);
            var transfer=replacement.takeInto(owner,()->{assertThrows(IOException.class,owner::view);assertThrows(IOException.class,old::world);assertThrows(IOException.class,owner::close);assertThrows(IOException.class,()->replacement.takeInto(owner,()->false));return false;});
            assertSame(transfer,owner.view().transfer());assertThrows(IOException.class,()->replacement.takeInto(owner,()->false));transfer.retirePrevious(f.c.world,()->false);owner.close();RegionReplayWorkerTest.closed(transfer.worker());
        }
    }
    @Test public void malformedClosedOrChangedPairsRejectWithoutConsumption()throws Exception {
        try(var f=fixture("pairs");var replacement=f.open();var wrongSeed=new World(f.c.local.seed+1,f.c.local.models,f.c.local.generatorVersion);var wrongModels=new World(f.c.local.seed);var owner=new SavedPagingOwner(f.c.local,f.c.world)) {
            assertThrows(IOException.class,()->new SavedPagingOwner(f.c.local,wrongSeed));assertThrows(IOException.class,()->new SavedPagingOwner(f.c.local,wrongModels));
            wrongSeed.close();assertThrows(IOException.class,()->new SavedPagingOwner(f.c.local,wrongSeed));SavedPagingReplacementTest.ready(replacement);var models=f.c.local.models;f.c.local.models=models.copy();
            assertThrows(IOException.class,owner::view);assertThrows(IOException.class,()->replacement.takeInto(owner,()->false));assertEquals(2,f.editor.capture(()->false).overlay().rows().size());f.c.local.models=models;
            var transfer=replacement.takeInto(owner,()->false);transfer.retirePrevious(f.c.world,()->false);owner.close();RegionReplayWorkerTest.closed(transfer.worker());
        }
    }
    @Test public void closedOwnerRejectsPendingCandidateAndCallerRetiresItSeparately()throws Exception {
        try(var f=fixture("closed");var replacement=f.open();var owner=new SavedPagingOwner(f.c.local,f.c.world)) {
            SavedPagingReplacementTest.ready(replacement);replacement.prepare(()->false);var pending=replacement.warmingWorld();owner.close();assertTrue(owner.isClosed());
            assertThrows(IOException.class,()->replacement.takeInto(owner,()->false));assertFalse(pending.isClosed());replacement.close();SavedPagingReplacementTest.stopped(replacement);assertTrue(pending.isClosed());
        }
    }
    @Test public void externallyClosedCurrentWorldCannotSupplyAViewOrInstall()throws Exception {
        try(var f=fixture("external");var replacement=f.open();var owner=new SavedPagingOwner(f.c.local,f.c.world)) {
            SavedPagingReplacementTest.ready(replacement);var held=owner.view();f.c.world.close();assertThrows(IOException.class,owner::view);assertThrows(IOException.class,held::local);assertThrows(IOException.class,()->replacement.takeInto(owner,()->false));
        }
    }
}
