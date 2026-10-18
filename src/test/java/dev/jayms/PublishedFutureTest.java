package dev.jayms;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PublishedFutureTest {
    @Test void readyResultDoesNotWaitForWorkerCompletionMetadata()throws Exception {
        var metadata=new CountDownLatch(1);var release=new CountDownLatch(1);
        var task=new PublishedFuture<int[]>(()->new int[]{7,8,9}){
            @Override protected void set(int[] value){
                metadata.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}super.set(value);
            }
        };
        var worker=new Thread(task);
        try {
            worker.start();assertTrue(metadata.await(5,TimeUnit.SECONDS));assertTrue(worker.isAlive());
            assertTrue(task.isDone());assertArrayEquals(new int[]{7,8,9},task.get(1,TimeUnit.SECONDS));
            assertFalse(task.cancel(true),"Published immutable results remain readable during metadata completion");
        }finally{release.countDown();worker.join(1000);}
        assertFalse(worker.isAlive());
    }
    @Test void activeAndQueuedCancellationPreserveInterruptionAndDoNotRunQueuedWork()throws Exception {
        var entered=new CountDownLatch(1);var interrupted=new AtomicBoolean();
        var active=new PublishedFuture<Void>(()->{entered.countDown();try{new CountDownLatch(1).await();return null;}catch(InterruptedException e){interrupted.set(true);throw e;}});
        var worker=new Thread(active);worker.start();assertTrue(entered.await(5,TimeUnit.SECONDS));assertTrue(active.cancel(true));worker.join(1000);
        assertTrue(active.isCancelled());assertTrue(interrupted.get());assertFalse(worker.isAlive());assertThrows(CancellationException.class,active::get);assertFalse(active.cancel(true));
        var ran=new AtomicBoolean();var queued=new PublishedFuture<Void>(()->{ran.set(true);return null;});assertTrue(queued.cancel(false));queued.run();assertFalse(ran.get());assertThrows(CancellationException.class,queued::get);
    }
    @Test void failuresRetainTheirOriginalCauseAndPendingReadsRespectTimeout()throws Exception {
        var original=new CancellationException("task failure");var task=new PublishedFuture<Void>(()->{throw original;});
        assertThrows(TimeoutException.class,()->task.get(0,TimeUnit.NANOSECONDS));task.run();assertFalse(task.isCancelled());
        assertSame(original,assertThrows(ExecutionException.class,task::get).getCause());
    }
}
