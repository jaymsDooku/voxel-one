import dev.jayms.net.structure.StructurePackage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Real disk-reader lifecycle plus deterministic blocked-worker edge checks. */
public final class AsyncRegionReadCheck {
    static final StructurePackage.Position POSITION=new StructurePackage.Position(0,0,0);
    static int rejects;
    static void require(boolean ok){if(!ok)throw new AssertionError();}
    interface Action{void run()throws Exception;}
    static void reject(Class<? extends Throwable> type,Action action)throws Exception {
        try{action.run();}catch(Throwable failure){if(failure instanceof ExecutionException)failure=failure.getCause();if(type.isInstance(failure)){rejects++;return;}throw new AssertionError(failure);}
        throw new AssertionError("Expected rejection");
    }
    static void await(CountDownLatch latch)throws Exception{require(latch.await(10,TimeUnit.SECONDS));}
    static void edges()throws Exception {
        var started=new CountDownLatch(1);var release=new CountDownLatch(1);var readCalls=new AtomicInteger();
        var closeThread=new AtomicReference<Thread>();var readerThread=new AtomicReference<Thread>();
        var async=new AsyncRegionReadPrototype(cancel->{
            readerThread.set(Thread.currentThread());started.countDown();await(release);
            return new AsyncRegionReadPrototype.Reader(){
                public Optional<StructurePackage.Cell> cell(StructurePackage.Position p,java.util.function.BooleanSupplier stop){readCalls.incrementAndGet();DiskPackageIndexPrototype.check(stop);return Optional.empty();}
                public void close(){closeThread.set(Thread.currentThread());}
            };
        },2);
        try {
            var first=async.request(List.of(POSITION));await(started);require(!first.isDone());
            var second=async.request(List.of(POSITION));var third=async.request(List.of(POSITION));
            reject(RejectedExecutionException.class,()->async.request(List.of(POSITION)).get(10,TimeUnit.SECONDS));
            var reentered=new AtomicReference<CompletableFuture<List<Optional<StructurePackage.Cell>>>>();
            first.whenComplete((value,failure)->reentered.set(async.request(List.of(POSITION))));
            async.invalidate();require(first.isCancelled()&&second.isCancelled()&&third.isCancelled());rejects++;
            var next=async.request(List.of(POSITION));require(!next.isDone());release.countDown();
            require(reentered.get().get(10,TimeUnit.SECONDS).equals(List.of(Optional.empty())));
            require(next.get(10,TimeUnit.SECONDS).equals(List.of(Optional.empty()))&&readCalls.get()==2);
            require(readerThread.get()!=Thread.currentThread());
            var cleanup=async.closeAsync();require(cleanup==async.closeAsync());cleanup.get(10,TimeUnit.SECONDS);
            require(readerThread.get()==closeThread.get());
            reject(IOException.class,()->async.request(List.of(POSITION)).get(10,TimeUnit.SECONDS));
            reject(IllegalArgumentException.class,()->async.request(Collections.nCopies(1025,POSITION)));
        }finally{release.countDown();async.closeAsync().get(10,TimeUnit.SECONDS);}

        var entered=new CountDownLatch(1);var unblock=new CountDownLatch(1);var calls=new AtomicInteger();
        var cancellation=new AsyncRegionReadPrototype(cancel->new AsyncRegionReadPrototype.Reader(){
            public Optional<StructurePackage.Cell> cell(StructurePackage.Position p,java.util.function.BooleanSupplier stop)throws IOException {
                if(calls.incrementAndGet()==2){entered.countDown();try{await(unblock);}catch(Exception e){throw new IOException(e);}}
                DiskPackageIndexPrototype.check(stop);return Optional.empty();
            }
            public void close(){}
        },2);
        try {
            var partial=cancellation.request(List.of(POSITION,POSITION,POSITION));await(entered);
            require(!partial.isDone());partial.cancel(false);unblock.countDown();
            reject(CancellationException.class,()->partial.get(10,TimeUnit.SECONDS));
            require(cancellation.request(List.of(POSITION)).get(10,TimeUnit.SECONDS).size()==1&&calls.get()==3);
        }finally{unblock.countDown();cancellation.closeAsync().get(10,TimeUnit.SECONDS);}

        var reading=new CountDownLatch(1);var finish=new CountDownLatch(1);var cleaned=new AtomicBoolean();
        var closing=new AsyncRegionReadPrototype(cancel->new AsyncRegionReadPrototype.Reader(){
            public Optional<StructurePackage.Cell> cell(StructurePackage.Position p,java.util.function.BooleanSupplier stop)throws IOException {
                reading.countDown();try{await(finish);}catch(Exception e){throw new IOException(e);}
                DiskPackageIndexPrototype.check(stop);return Optional.empty();
            }
            public void close(){cleaned.set(true);}
        },1);
        try {
            var running=closing.request(List.of(POSITION));await(reading);var queued=closing.request(List.of(POSITION));
            var cleanup=closing.closeAsync();require(running.isCancelled()&&queued.isCancelled()&&!cleanup.isDone()&&!cleaned.get());rejects++;
            finish.countDown();cleanup.get(10,TimeUnit.SECONDS);require(cleaned.get());
        }finally{finish.countDown();closing.closeAsync().get(10,TimeUnit.SECONDS);}

        // Startup failure is visible and can retry; close failure is also visible.
        var opens=new AtomicInteger();
        var failures=new AsyncRegionReadPrototype(cancel->{if(opens.incrementAndGet()==1)throw new IOException("Synthetic open failure");return new AsyncRegionReadPrototype.Reader(){
            public Optional<StructurePackage.Cell> cell(StructurePackage.Position p,java.util.function.BooleanSupplier stop){return Optional.empty();}
            public void close()throws IOException{throw new IOException("Synthetic close failure");}
        };},1);
        reject(IOException.class,()->failures.request(List.of(POSITION)).get(10,TimeUnit.SECONDS));
        require(failures.request(List.of(POSITION)).get(10,TimeUnit.SECONDS).size()==1);
        reject(IOException.class,()->failures.closeAsync().get(10,TimeUnit.SECONDS));
    }
    static void disk(Path root)throws Exception {
        var expected=DiskRegionCacheCheck.fixture();var source=root.resolve("async.vstr");expected.save(source);
        var cacheFile=new AtomicReference<Path>();var storageThread=new AtomicReference<Thread>();
        var async=new AsyncRegionReadPrototype(cancel->{
            storageThread.set(Thread.currentThread());var index=DiskPackageIndexPrototype.open(source,root,cancel);cacheFile.set(index.decoded);
            var cache=new DiskRegionCachePrototype(index,2,1024*1024);
            return new AsyncRegionReadPrototype.Reader(){
                public Optional<StructurePackage.Cell> cell(StructurePackage.Position p,java.util.function.BooleanSupplier stop)throws IOException{return cache.cell(p.x(),p.y(),p.z(),stop);}
                public void close()throws IOException{cache.close();index.close();}
            };
        },2);
        try {
            var positions=new ArrayList<StructurePackage.Position>();for(var cell:expected.cells)positions.add(cell.position());
            positions.add(new StructurePackage.Position(48,0,0));
            var loaded=async.request(positions).get(10,TimeUnit.SECONDS);
            require(storageThread.get()!=Thread.currentThread()&&loaded.size()==5);
            for(int i=0;i<expected.cells.size();i++)require(loaded.get(i).orElseThrow().equals(expected.cells.get(i)));
            require(loaded.get(4).isEmpty());
            reject(UnsupportedOperationException.class,()->loaded.clear());
            reject(IllegalStateException.class,()->loaded.get(0).orElseThrow().geometry().set(0,0,0,1));
            reject(IllegalArgumentException.class,()->async.request(List.of(new StructurePackage.Position(-1,0,0))).get(10,TimeUnit.SECONDS));
            require(async.request(List.of(POSITION)).get(10,TimeUnit.SECONDS).get(0).orElseThrow().equals(expected.cells.get(0)));
            async.closeAsync().get(10,TimeUnit.SECONDS);require(!Files.exists(cacheFile.get()));
            require(loaded.get(0).orElseThrow().equals(expected.cells.get(0))); // lifetime after close
        }finally{async.closeAsync().get(10,TimeUnit.SECONDS);}
    }
    public static void main(String[] args)throws Exception {
        edges();Path root=Files.createTempDirectory(Path.of("target"),"async-region-check-");
        try{disk(root);}finally{try(var files=Files.list(root)){for(var p:files.toList())Files.delete(p);}Files.delete(root);}
        String result="{\"status\":\"passed\",\"rejection_cases\":"+rejects+",\"disk_cells\":4,\"known_absent\":1,\"worker_only_io\":true,\"qualification\":false}";
        System.out.println(result);
        if(args.length==1&&args[0].equals("--evidence"))javax.swing.SwingUtilities.invokeAndWait(()->{
            var frame=new javax.swing.JFrame("Voxel One async region CLI evidence");
            var text=new javax.swing.JTextArea("Voxel One: asynchronous region read integration\n\nActual CLI assertions passed before opening this window.\n"+result+"\n\nReal disk index/cache opened, read and closed on one storage thread.\nFour exact cells plus one known-absent cell returned in one immutable list.\nPending reads never returned an empty result. Queue overflow rejected.\nCancellation discarded partial batches. Invalidated views stayed cancelled.\nRead failure retried; cleanup failure surfaced; owned disk cache removed.\nFrozen results survive eviction and reader close.\n\nPrivate adapter only; game world, caps and save formats unchanged.\nLive world integration and all city/max, GPU and 32-client gates pending.");
            text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);text.setFont(new java.awt.Font(java.awt.Font.MONOSPACED,0,15));text.setMargin(new java.awt.Insets(20,20,20,20));frame.add(text);frame.setSize(1000,540);frame.setLocation(60,60);frame.setVisible(true);
            var timer=new javax.swing.Timer(15000,e->frame.dispose());timer.setRepeats(false);timer.start();
        });
    }
}
