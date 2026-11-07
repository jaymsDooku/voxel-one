package dev.jayms.net.structure;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** One pending owner-thread read; pump executes at most one request per frame. */
public final class StructureOwnerQueue implements AutoCloseable {
    private final Thread owner=Thread.currentThread();
    private final AtomicReference<FutureTask<?>> pending=new AtomicReference<>();
    private final AtomicBoolean closed=new AtomicBoolean();
    public <T> T call(Callable<T> read,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(read);Objects.requireNonNull(cancel);
        if(Thread.currentThread()==owner)throw new IllegalStateException("Owner read must be requested by a worker");
        StructurePackage.cancelled(cancel);
        if(closed.get())throw new IOException("Structure owner queue closed");
        var task=new FutureTask<T>(read);
        if(!pending.compareAndSet(null,task))throw new IOException("Another structure owner read is pending");
        try {
            while(true) {
                StructurePackage.cancelled(cancel);
                if(closed.get())throw new IOException("Structure owner queue closed");
                try {
                    T value=task.get(50,TimeUnit.MILLISECONDS);
                    StructurePackage.cancelled(cancel);return value;
                } catch(TimeoutException waiting) {
                    // Waiting belongs to the worker; the owner keeps running frames.
                } catch(InterruptedException interrupted) {
                    Thread.currentThread().interrupt();throw new CancellationException("Structure owner read interrupted");
                } catch(ExecutionException failure) {
                    var cause=failure.getCause();
                    if(cause instanceof IOException io)throw io;
                    if(cause instanceof RuntimeException runtime)throw runtime;
                    if(cause instanceof Error error)throw error;
                    throw new IOException("Structure owner read failed",cause);
                }
            }
        } finally {pending.compareAndSet(task,null);task.cancel(false);}
    }
    public boolean pump() {
        if(Thread.currentThread()!=owner)throw new IllegalStateException("Structure owner reads require the owner thread");
        if(closed.get())return false;
        var task=pending.getAndSet(null);if(task==null)return false;
        task.run();return true;
    }
    @Override public void close(){closed.set(true);var task=pending.getAndSet(null);if(task!=null)task.cancel(false);}
}
