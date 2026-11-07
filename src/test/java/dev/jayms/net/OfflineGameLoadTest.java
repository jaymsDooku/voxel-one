package dev.jayms.net;

import java.io.*;
import dev.jayms.net.model.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class OfflineGameLoadTest {
    @TempDir Path folder;
    private Path fixture(boolean regional)throws Exception {
        Path path=folder.resolve(regional?"regional.dat":"legacy.dat");var game=new LocalGame(path,187);
        game.edits=RegionReplayTest.fixture();game.inventory.add(Blocks.STONE,3);game.health=13;
        var tree=new SparseVoxelOctree(32);tree.set(31,30,29,0xff123456);
        int model=game.models.stageStructureModel(new ModelDefinition("Worker model",tree)).id();
        WorldVoxels.remember(game.edits,new Protocol.Edit(32,180,32,model));
        if(regional)try(var staged=StagedOfflineSave.stageRegions(path,game.seed,game.generatorVersion,game.models,(WorldVoxels.History)game.edits,()->false,p->{})) {
            staged.publish(game.inventory,game.health,List.of(),0);
        }else game.save();return path;
    }
    private void complete(OfflineGameLoad load)throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(!load.done()&&System.nanoTime()<end)Thread.sleep(1);
        assertTrue(load.done(),"Worker load timed out");
    }
    @Test void actualLegacyAndRegionalLoadsPreserveWorldStateAndBytes()throws Exception {
        for(boolean regional:List.of(false,true)) {
            Path path=fixture(regional);byte[] before=Files.readAllBytes(path);var expected=new LocalGame(path,0);
            try(var load=new OfflineGameLoad(path,0)) {
                complete(load);var actual=load.result();assertEquals(1103,load.editsRead());
                assertEquals(expected.seed,actual.seed);assertEquals(expected.generatorVersion,actual.generatorVersion);
                assertEquals(13,actual.health);assertEquals(3,actual.inventory.countType(Blocks.STONE));
                assertEquals(new ArrayList<>(expected.edits.values()),new ArrayList<>(actual.edits.values()));
                assertEquals(expected.models.entries().stream().map(e->e.id()+":"+e.definition().fingerprint()).toList(),
                        actual.models.entries().stream().map(e->e.id()+":"+e.definition().fingerprint()).toList());
                assertArrayEquals(before,Files.readAllBytes(path));actual.save();assertArrayEquals(before,Files.readAllBytes(path));
            }
        }
    }
    @Test void cancellationProgressAndCallbackFailureNeverWriteSource()throws Exception {
        for(boolean regional:List.of(false,true)) {
            Path path=fixture(regional);byte[] before=Files.readAllBytes(path);var cancel=new AtomicBoolean();var progress=new ArrayList<Integer>();
            assertThrows(CancellationException.class,()->new LocalGame(path,0,cancel::get,n->{progress.add(n);if(n>=128)cancel.set(true);}));
            assertEquals(List.of(0,regional?0:128),progress.subList(0,2));assertTrue(progress.get(progress.size()-1)>=128);
            assertArrayEquals(before,Files.readAllBytes(path));
            assertThrows(CancellationException.class,()->new LocalGame(path,0,()->true,n->fail()));
            var failure=new IllegalStateException("Synthetic progress failure");
            assertSame(failure,assertThrows(IllegalStateException.class,()->new LocalGame(path,0,()->false,n->{if(n>0)throw failure;})));
            assertArrayEquals(before,Files.readAllBytes(path));
            var all=new ArrayList<Integer>();new LocalGame(path,0,()->false,all::add);
            assertEquals(1103,all.get(all.size()-1));for(int i=1;i<all.size();i++)assertTrue(all.get(i)>=all.get(i-1));
        }
    }
    @Test void asyncFailureAndNewWorldHaveNoPartialPublication()throws Exception {
        Path path=folder.resolve("bad.dat");byte[] before={1,2,3};Files.write(path,before);
        try(var load=new OfflineGameLoad(path,0)){complete(load);assertThrows(IOException.class,load::result);assertArrayEquals(before,Files.readAllBytes(path));}
        Path missing=folder.resolve("missing/world.dat");
        try(var load=new OfflineGameLoad(missing,92)){complete(load);assertEquals(92,load.result().seed);assertEquals(0,load.editsRead());assertFalse(Files.exists(missing.getParent()));}
    }
    @Test void ownerNeverWaitsForWorkerAndLateCompletionCannotPublishAfterCancel()throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var stopped=new CountDownLatch(1);var worker=new AtomicReference<Thread>();var interrupted=new AtomicBoolean();var sawCancel=new AtomicBoolean();
        var load=new OfflineGameLoad(folder.resolve("absent.dat"),72,(path,seed,cancel,progress)->{
            worker.set(Thread.currentThread());progress.accept(256);entered.countDown();
            try {
                // Simulate stalled I/O. Owner close must not invoke interruption:
                // NIO's interrupt-driven channel closure can wait for active I/O.
                while(release.getCount()!=0)try{release.await();}catch(InterruptedException signal){interrupted.set(true);}
                if(Thread.interrupted())interrupted.set(true);
                sawCancel.set(cancel.getAsBoolean());return new LocalGame(path,seed);
            }finally{stopped.countDown();}
        });
        try {
            assertTrue(entered.await(5,TimeUnit.SECONDS));assertNotSame(Thread.currentThread(),worker.get());assertTrue(worker.get().isDaemon());
            assertEquals(256,load.editsRead());assertFalse(load.done());assertThrows(IllegalStateException.class,load::result);
            load.close();assertTrue(load.done());assertThrows(CancellationException.class,load::result);
            // Worker still blocked: close returned without joining it or touching source.
            assertEquals(1,stopped.getCount());assertFalse(Files.exists(folder.resolve("absent.dat")));
        }finally{load.close();release.countDown();assertTrue(stopped.await(5,TimeUnit.SECONDS));worker.get().join(5000);assertFalse(worker.get().isAlive());}
        // Assertions after join cannot be swallowed by the cancelled Future.
        assertTrue(sawCancel.get(),"Worker did not observe cooperative cancellation");
        assertFalse(interrupted.get(),"Owner interrupted the active I/O worker");
        assertThrows(CancellationException.class,load::result);
    }
}
