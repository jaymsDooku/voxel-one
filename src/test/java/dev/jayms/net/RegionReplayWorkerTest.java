package dev.jayms.net;

import dev.jayms.ChunkPos;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegionReplayWorkerTest {
    @TempDir Path folder;
    static final RegionReplayWorker.Revision REV=new RegionReplayWorker.Revision(1,2,3);
    static final RegionReplayPage.CellRange RANGE=new RegionReplayPage.CellRange(0,1);
    static final ChunkPos REGION=new ChunkPos(0,0,0);
    static final List<Protocol.Edit> ROWS=List.of(new Protocol.Edit(0,0,0,Blocks.STONE));
    static void await(BooleanSupplier done)throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(!done.getAsBoolean()&&System.nanoTime()<end)LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        assertTrue(done.getAsBoolean(),"Worker state deadline");
    }
    static void release(CountDownLatch latch)throws IOException {
        try{if(!latch.await(10,TimeUnit.SECONDS))throw new IOException("Synthetic latch deadline");}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException(interrupted);}
    }
    RegionReplayWorker.Factory factory(java.util.function.Predicate<Protocol.Edit> allowed)throws Exception {
        var encoded=new ByteArrayOutputStream();encoded.write(RegionReplayLoaderTest.encodeRows(ROWS));new DataOutputStream(encoded).writeInt(0);byte[] bytes=encoded.toByteArray();
        return (cancel,progress)->{
            var input=new DataInputStream(new ByteArrayInputStream(bytes));
            var source=RegionReplaySpool.stage(folder,input,e->true,cancel,RegionReplaySpool.MAX_BYTES,progress);
            try{LocalGame.readRegionalTail(input,cancel);return new RegionReplayWorker.Source(source,new Terrain(42),allowed,REV);}
            catch(IOException|RuntimeException|Error failure){source.close();throw failure;}
        };
    }
    RegionReplayWorker worker(RegionReplayWorker.Factory factory,int capacity){return new RegionReplayWorker(folder,factory,capacity,1,28,1);}
    static RegionReplayWorker.Request request(RegionReplayWorker worker)throws Exception{return worker.submit(REGION,RANGE,1,REV);}
    static void done(RegionReplayWorker.Request request)throws Exception{await(()->request.outcome().phase()!=RegionReplayWorker.Phase.QUEUED&&request.outcome().phase()!=RegionReplayWorker.Phase.LOADING);}
    static void closed(RegionReplayWorker worker)throws Exception {worker.close();await(()->worker.status().state()==RegionReplayWorker.State.CLOSED||worker.status().state()==RegionReplayWorker.State.FAILED);}
    void noFiles()throws Exception{try(var files=Files.list(folder)){assertEquals(0,files.count());}}

    @Test void sourceAndReadsStayOnOneWorkerAndDetachedResultSurvivesClose()throws Exception {
        Thread owner=Thread.currentThread();var actual=new AtomicReference<Thread>();var open=factory(e->{assertSame(actual.get(),Thread.currentThread());return true;});
        var worker=worker((cancel,progress)->{assertNotSame(owner,Thread.currentThread());actual.set(Thread.currentThread());progress.accept(-1);progress.accept(100);progress.accept(3);return open.open(cancel,progress);},2);
        try {
            var request=request(worker);done(request);assertEquals(RegionReplayWorker.Phase.READY,request.outcome().phase());
            assertEquals(2,request.checkedRows());assertEquals(100,worker.status().sourceRows());
            var page=request.result(REV);assertEquals(ROWS,new ArrayList<>(page.history().values()));
            var world=new WorldVoxels(new Terrain(42));ROWS.forEach(world::apply);assertTrue(world.cell(0,0,0).sameValues(page.cells().values().iterator().next().tree()));
            assertThrows(IOException.class,()->request.result(new RegionReplayWorker.Revision(2,2,3)));
            closed(worker);assertEquals(1,page.cells().size());assertEquals(1,request.result(REV).history().size());assertFalse(request.cancel());noFiles();
        }finally{closed(worker);}
    }
    @Test void boundedOpeningQueueCancellationFreesCapacityAndCloseDoesNotJoinFactory()throws Exception {
        var entered=new CountDownLatch(1);var proceed=new CountDownLatch(1);var open=factory(e->true);
        var worker=worker((cancel,progress)->{entered.countDown();release(proceed);return open.open(cancel,progress);},2);
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS));var first=request(worker);var second=request(worker);
            assertThrows(IOException.class,()->request(worker));assertTrue(first.cancel());assertFalse(first.cancel());
            var third=request(worker);worker.close(); // must return while the factory remains blocked
            assertEquals(1,proceed.getCount());assertEquals(RegionReplayWorker.Phase.CANCELLED,second.outcome().phase());assertEquals(RegionReplayWorker.Phase.CANCELLED,third.outcome().phase());
            assertThrows(IOException.class,()->request(worker));assertThrows(CancellationException.class,()->first.result(REV));
        }finally{proceed.countDown();closed(worker);noFiles();}
    }
    @Test void cancelDuringReadNeverPublishesLatePageAndNextRequestCanRetry()throws Exception {
        var entered=new CountDownLatch(1);var proceed=new CountDownLatch(1);var block=new AtomicBoolean(true);
        var worker=worker(factory(e->{if(block.getAndSet(false)){entered.countDown();try{release(proceed);}catch(IOException failure){throw new UncheckedIOException(failure);}}return true;}),2);
        try {
            var first=request(worker);assertTrue(entered.await(10,TimeUnit.SECONDS));assertEquals(RegionReplayWorker.Phase.LOADING,first.outcome().phase());
            assertThrows(IllegalStateException.class,()->first.result(REV));assertTrue(first.cancel());var second=request(worker);proceed.countDown();done(second);
            assertEquals(RegionReplayWorker.Phase.CANCELLED,first.outcome().phase());assertEquals(RegionReplayWorker.Phase.READY,second.outcome().phase());assertEquals(1,second.result(REV).history().size());
        }finally{proceed.countDown();closed(worker);noFiles();}
    }
    @Test void requestPolicyFailureAndSourceRevisionMismatchDoNotPoisonWorker()throws Exception {
        var allow=new AtomicBoolean(false);var worker=worker(factory(e->allow.get()),2);
        try {
            var stale=worker.submit(REGION,RANGE,1,new RegionReplayWorker.Revision(9,2,3));done(stale);assertEquals(0,stale.checkedRows());assertThrows(IOException.class,()->stale.result(new RegionReplayWorker.Revision(9,2,3)));
            var failed=request(worker);done(failed);assertEquals(RegionReplayWorker.Phase.FAILED,failed.outcome().phase());assertThrows(IOException.class,()->failed.result(REV));
            allow.set(true);var good=request(worker);done(good);assertEquals(1,good.result(REV).history().size());
            var executor=Executors.newSingleThreadExecutor();try {
                assertTrue(executor.submit(()->{try{worker.close();return false;}catch(IOException expected){return true;}}).get());
                assertTrue(executor.submit(()->{try{good.result(REV);return false;}catch(IOException expected){return true;}}).get());
            }finally{executor.shutdownNow();}
        }finally{closed(worker);noFiles();}
    }
    @Test void sourceFailureCompletesQueuedRequestsAndCleanupFailureWaitsForExplicitRetry()throws Exception {
        var entered=new CountDownLatch(1);var proceed=new CountDownLatch(1);
        var failed=worker((cancel,progress)->{entered.countDown();release(proceed);throw new IOException("Synthetic source invalid");},2);
        assertTrue(entered.await(10,TimeUnit.SECONDS));var rejected=request(failed);proceed.countDown();done(rejected);
        await(()->failed.status().state()==RegionReplayWorker.State.FAILED);assertEquals(RegionReplayWorker.Phase.FAILED,rejected.outcome().phase());closed(failed);noFiles();
        var worker=worker(factory(e->true),2);Path path=null;
        try {
            var good=request(worker);done(good);assertEquals(RegionReplayWorker.Phase.READY,good.outcome().phase());
            try(var files=Files.list(folder)){path=files.filter(p->p.getFileName().toString().startsWith(".replay-cells-")).findFirst().orElseThrow();}
            Files.delete(path);Files.createDirectory(path);Files.writeString(path.resolve("hold"),"synthetic");
            worker.close();await(()->worker.status().state()==RegionReplayWorker.State.CLEANUP_FAILED);
            assertNotNull(worker.status().failure());assertThrows(IOException.class,()->request(worker));
            Files.delete(path.resolve("hold"));Files.delete(path);path=null;
            // No retry signal was sent. The worker must still report failed cleanup.
            assertEquals(RegionReplayWorker.State.CLEANUP_FAILED,worker.status().state());closed(worker);noFiles();
        }finally{if(path!=null){Files.deleteIfExists(path.resolve("hold"));Files.deleteIfExists(path);}closed(worker);}
    }
    @Test void invalidRegionalTailNeverReturnsAPageAndOwnedSpoolIsRemoved()throws Exception {
        for(byte[] tail:List.of(new byte[0],new byte[]{-1,-1,-1,-1},new byte[]{0,0,0,0,1})) {
            var encoded=new ByteArrayOutputStream();encoded.write(RegionReplayLoaderTest.encodeRows(ROWS));encoded.write(tail);
            var entered=new CountDownLatch(1);var proceed=new CountDownLatch(1);
            var worker=worker((cancel,progress)->{
                entered.countDown();release(proceed);var input=new DataInputStream(new ByteArrayInputStream(encoded.toByteArray()));
                var source=RegionReplaySpool.stage(folder,input,e->true,cancel);
                try {LocalGame.readRegionalTail(input,cancel);return new RegionReplayWorker.Source(source,new Terrain(42),e->true,REV);}
                catch(IOException|RuntimeException|Error failure){source.close();throw failure;}
            },1);
            try {assertTrue(entered.await(10,TimeUnit.SECONDS));var request=request(worker);proceed.countDown();done(request);
                assertEquals(RegionReplayWorker.Phase.FAILED,request.outcome().phase());assertThrows(IOException.class,()->request.result(REV));
                await(()->worker.status().state()==RegionReplayWorker.State.FAILED);
            }finally{proceed.countDown();closed(worker);noFiles();}
        }
    }
    @Test void invalidBudgetsRejectBeforeFactoryAndFatalPolicyDoesNotStrandRequest()throws Exception {
        var opened=new AtomicBoolean();RegionReplayWorker.Factory factory=(c,p)->{opened.set(true);throw new IOException();};
        assertThrows(IllegalArgumentException.class,()->new RegionReplayWorker(folder,factory,0,1,28,1));
        assertThrows(IllegalArgumentException.class,()->new RegionReplayWorker(folder,factory,9,1,28,1));
        assertThrows(IllegalArgumentException.class,()->new RegionReplayWorker(folder,factory,1,1,27,1));assertFalse(opened.get());
        var worker=worker(factory(e->{throw new AssertionError("Synthetic fatal policy");}),1);
        try{var request=request(worker);done(request);assertEquals(RegionReplayWorker.Phase.FAILED,request.outcome().phase());await(()->worker.status().state()==RegionReplayWorker.State.FAILED);}
        finally{closed(worker);noFiles();}
    }
}
