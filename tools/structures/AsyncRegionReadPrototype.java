import dev.jayms.net.structure.StructurePackage;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Private storage adapter, not installed in the game. Never interpret an unfinished
 * future as empty terrain. Reader open/read/close all run on one storage thread. */
public final class AsyncRegionReadPrototype implements AutoCloseable {
    public interface Reader extends AutoCloseable {
        Optional<StructurePackage.Cell> cell(StructurePackage.Position position,BooleanSupplier cancel)throws IOException;
        @Override void close()throws IOException;
    }
    public interface Factory { Reader open(BooleanSupplier cancel)throws Exception; }
    private final Factory factory;
    private final ThreadPoolExecutor executor;
    private final Set<CompletableFuture<List<Optional<StructurePackage.Cell>>>> pending=new HashSet<>();
    private Reader reader;
    private long generation;
    private boolean closed;
    public AsyncRegionReadPrototype(Factory factory,int queueCapacity) {
        this.factory=Objects.requireNonNull(factory);
        if(queueCapacity<1||queueCapacity>128)throw new IllegalArgumentException("Read queue budget");
        executor=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(queueCapacity),r->{
            var t=new Thread(r,"synthetic-region-reader");t.setDaemon(true);return t;
        },new ThreadPoolExecutor.AbortPolicy());
    }
    /** Caller polls/observes completion. This method performs no disk I/O or waiting.
     * Batches contain at most 1024 positions; cancellation never yields a partial list. */
    public synchronized CompletableFuture<List<Optional<StructurePackage.Cell>>> request(List<StructurePackage.Position> positions) {
        Objects.requireNonNull(positions);
        if(positions.size()>1024)throw new IllegalArgumentException("Read batch budget");
        var batch=List.copyOf(positions);var result=new CompletableFuture<List<Optional<StructurePackage.Cell>>>();
        if(closed){result.completeExceptionally(new IOException("Region reader closed"));return result;}
        long expected=generation;pending.add(result);
        try{executor.execute(()->read(batch,result,expected));}
        catch(RejectedExecutionException full){pending.remove(result);result.completeExceptionally(full);}
        return result;
    }
    private synchronized boolean stale(CompletableFuture<?> result,long expected){return closed||generation!=expected||result.isCancelled();}
    private void read(List<StructurePackage.Position> batch,CompletableFuture<List<Optional<StructurePackage.Cell>>> result,long expected) {
        try {
            BooleanSupplier cancel=()->Thread.currentThread().isInterrupted()||stale(result,expected);
            DiskPackageIndexPrototype.check(cancel);
            if(reader==null)reader=Objects.requireNonNull(factory.open(cancel));
            var cells=new ArrayList<Optional<StructurePackage.Cell>>(batch.size());
            for(var p:batch){DiskPackageIndexPrototype.check(cancel);cells.add(Objects.requireNonNull(reader.cell(p,cancel)));}
            synchronized(this) {
                if(stale(result,expected))throw new CancellationException();
                result.complete(List.copyOf(cells));
            }
        }catch(Exception failure){result.completeExceptionally(failure);}
        finally{synchronized(this){pending.remove(result);}}
    }
    /** Invalidate pending views of the same immutable source. Does not replace that source.
     * World identity/root publication still needs an authoritative owner-side check. */
    public synchronized void invalidate() {
        generation++;
        var obsolete=List.copyOf(pending);
        pending.clear();executor.getQueue().clear();
        // Cancellation callbacks may synchronously submit a fresh-generation request.
        for(var request:obsolete)request.cancel(false);
    }
    /** Completion includes storage-thread cleanup. Close never waits on the caller. */
    public synchronized CompletableFuture<Void> closeAsync() {
        if(closed)return cleanup;
        closed=true;var obsolete=List.copyOf(pending);pending.clear();
        // Drop queued read tasks; a running read observes closed through its cancel flag.
        executor.getQueue().clear();
        for(var request:obsolete)request.cancel(false);
        executor.execute(()->{
            try{if(reader!=null)reader.close();cleanup.complete(null);}
            catch(Exception failure){cleanup.completeExceptionally(failure);}
            finally{executor.shutdown();}
        });
        return cleanup;
    }
    private final CompletableFuture<Void> cleanup=new CompletableFuture<>();
    @Override public void close(){closeAsync();}
}
