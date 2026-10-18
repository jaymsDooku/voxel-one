package dev.jayms;

import dev.jayms.net.model.*;
import dev.jayms.render.WorldSkyVisibility;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class ActivePreparationShutdownTest {
    /** Many identical empty tiles reproduce the previously unchecked build/merge loops without large allocations. */
    private static <T> List<T> busyInput(T value, CountDownLatch active, AtomicBoolean release) {
        return new AbstractList<>() {
            public int size() { return Integer.MAX_VALUE; }
            public T get(int index) {
                active.countDown();
                if (release.get()) throw new CancellationException("Test fixture released");
                return value;
            }
        };
    }
    private static void closesActiveSky(boolean merge) throws Exception {
        var started=new CountDownLatch(1);var exited=new CountDownLatch(1);var release=new AtomicBoolean();
        var empty=new SparseVoxelOctree(256).freeze();
        var tile=WorldSkyVisibility.build(new WorldSkyVisibility.Input(List.of(),List.of(),new ModelLibrary()));
        var scheduler=new TerrainScheduler(42,3);
        Future<?> active=null,queued=null;
        try {
            active=scheduler.prepare(()->{
                try {
                    if(merge)return WorldSkyVisibility.merge(busyInput(tile,started,release));
                    return WorldSkyVisibility.build(new WorldSkyVisibility.Input(busyInput(new WorldSkyVisibility.Geometry(new ChunkPos(0,0,0),empty),started,release),List.of(),new ModelLibrary()));
                } finally { exited.countDown(); }
            });
            assertTrue(started.await(2,TimeUnit.SECONDS),"Real sky preparation entered its loop");
            var queuedRan=new AtomicBoolean();queued=scheduler.prepare(()->{queuedRan.set(true);return null;});
            long begin=System.nanoTime();scheduler.close();
            assertTrue(System.nanoTime()-begin<TimeUnit.SECONDS.toNanos(2),"Close must not reach the5second watchdog");
            assertEquals(0,exited.getCount(),"Worker body exited before close returned");
            assertTrue(active.isCancelled(),"Active future must be cancelled, not merely exceptionally completed");
            assertTrue(queued.isCancelled());assertFalse(queuedRan.get());
            assertThrows(IllegalStateException.class,()->scheduler.prepare(()->null));
            scheduler.close();
        } finally { release.set(true);scheduler.close(); }
    }
    @Test void closeCancelsRunningEmptyTileBuild()throws Exception { closesActiveSky(false); }
    @Test void closeCancelsRunningMergeInputScan()throws Exception { closesActiveSky(true); }
    @Test void cancelledPreparationDoesNotPoisonNextTerrainOrWorld()throws Exception {
        var active=new CountDownLatch(1);var exited=new CountDownLatch(1);
        try(var world=new World(42)) {
            var future=world.prepareTerrainTask(()->{active.countDown();try{while(true){if(Thread.currentThread().isInterrupted())throw new CancellationException();}}finally{exited.countDown();}});
            assertTrue(active.await(2,TimeUnit.SECONDS));future.cancel(true);assertTrue(exited.await(2,TimeUnit.SECONDS));
            assertEquals(17,world.prepareTerrainTask(()->17).get(2,TimeUnit.SECONDS));
        }
        try(var next=new World(999)){assertEquals(999,next.prepareTerrainTask(()->999).get(2,TimeUnit.SECONDS));}
    }
    @Test void interruptedOctreeTraversalAndEmptyMergeStopWithoutClearingFlag() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class,()->new SparseVoxelOctree(256).leavesInterruptibly());
            assertThrows(CancellationException.class,()->WorldSkyVisibility.merge(List.of()));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }
}
