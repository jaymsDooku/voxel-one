package dev.jayms.net;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** One read-only startup task. No GL work or source writes happen on its worker.
 * Close only requests cooperative cancellation and fences result publication.
 * It never interrupts active channel I/O from the owner. A daemon blocked in
 * filesystem I/O can remain until that call returns, then bounded reads stop. */
public final class OfflineGameLoad implements AutoCloseable {
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private final AtomicInteger editsRead=new AtomicInteger();
    private final ExecutorService worker;
    private final Future<LocalGame> result;
    private boolean closed;
    @FunctionalInterface interface Loader {
        LocalGame load(Path save,long seed,java.util.function.BooleanSupplier cancel,
                java.util.function.IntConsumer progress)throws IOException;
    }
    public OfflineGameLoad(Path save,long seed) {this(save,seed,LocalGame::new);}
    OfflineGameLoad(Path save,long seed,Loader loader) {
        java.util.Objects.requireNonNull(save);java.util.Objects.requireNonNull(loader);
        worker=Executors.newSingleThreadExecutor(task->{var thread=new Thread(task,"voxel-offline-load");thread.setDaemon(true);return thread;});
        try{result=worker.submit(()->loader.load(save,seed,cancelled::get,editsRead::set));}
        catch(RuntimeException|Error failure){cancelled.set(true);worker.shutdown();throw failure;}
    }
    public int editsRead(){return editsRead.get();}
    public boolean done(){return result.isDone();}
    /** Owner calls only after done. Never blocks waiting for file reads. */
    public LocalGame result()throws IOException {
        if(closed||cancelled.get())throw new CancellationException("Offline world loading cancelled");
        if(!result.isDone())throw new IllegalStateException("Offline world load not complete");
        try{
            var local=result.get();
            if(!local.replayComplete())throw new IOException("Offline world replay is incomplete");
            return local;
        }
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new CancellationException("Offline world loading interrupted");}
        catch(ExecutionException failure){
            var cause=failure.getCause();if(cause instanceof IOException io)throw io;
            if(cause instanceof RuntimeException runtime)throw runtime;
            if(cause instanceof Error error)throw error;
            throw new IOException("Offline world load failed",cause);
        }
    }
    @Override public void close(){
        if(!closed){closed=true;cancelled.set(true);result.cancel(false);worker.shutdown();}
    }
}
