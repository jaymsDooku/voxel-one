package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.StressGrid;
import dev.jayms.net.model.ModelLibrary;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Startup-only private geometry preparation. The worker owns its World until
 * result transfers it to the render owner. It creates no chunks, GL objects,
 * terrain scheduler or file handles. Cancellation can therefore discard a late
 * result without owner-side disposal or joining a blocked worker. */
final class OfflineWorldReplay implements AutoCloseable {
    @FunctionalInterface interface Factory {
        World create(long seed,ModelLibrary models,int generator);
    }
    private final LocalGame local;
    private final WorldVoxels.History source;
    private final long revision,modelRevision;
    private final ModelLibrary models;
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private final AtomicInteger applied=new AtomicInteger();
    private final ExecutorService worker;
    private final Future<World> result;
    private boolean closed,transferred;

    OfflineWorldReplay(LocalGame local,StressGrid grid)throws IOException {
        this(local,grid,World::new);
    }
    OfflineWorldReplay(LocalGame local,StressGrid grid,Factory factory)throws IOException {
        this.local=Objects.requireNonNull(local);Objects.requireNonNull(factory);
        if(!(local.edits instanceof WorldVoxels.History history))throw new IOException("Indexed offline replay history required");
        source=history;revision=source.revision();models=local.models;modelRevision=models.revision();
        var snapshot=WorldVoxels.History.copyOf(source);
        worker=Executors.newSingleThreadExecutor(task->{var thread=new Thread(task,"voxel-offline-replay");thread.setDaemon(true);return thread;});
        try {
            result=worker.submit(()->{
                World world=factory.create(local.seed,models,local.generatorVersion);
                try {
                    if(grid!=null)world.terrain().stressGrid(grid);
                    world.replayInitial(snapshot.values(),cancelled::get,applied::set);
                    checkCancelled();return world;
                }catch(RuntimeException|Error failure) {
                    try{world.close();}catch(Exception cleanup){failure.addSuppressed(cleanup);}throw failure;
                }
            });
        }catch(RuntimeException|Error failure){cancelled.set(true);worker.shutdown();throw failure;}
    }
    private void checkCancelled(){if(cancelled.get())throw new CancellationException("Offline world replay cancelled");}
    int applied(){return applied.get();}
    boolean done(){return result.isDone();}
    /** Owner only, after done. A changed source rejects the complete private
     * result; neither stale geometry nor a partial prefix is published. */
    World result()throws IOException {
        if(closed||cancelled.get())throw new CancellationException("Offline world replay cancelled");
        if(transferred)throw new IllegalStateException("Offline world replay already transferred");
        if(!result.isDone())throw new IllegalStateException("Offline world replay not complete");
        if(local.edits!=source||source.revision()!=revision||local.models!=models||models.revision()!=modelRevision)
            throw new IOException("Offline world changed during replay");
        try {
            World world=result.get();transferred=true;return world;
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new CancellationException("Offline world replay interrupted");}
        catch(ExecutionException failure) {
            var cause=failure.getCause();if(cause instanceof RuntimeException runtime)throw runtime;
            if(cause instanceof Error error)throw error;
            throw new IOException("Offline world replay failed",cause);
        }
    }
    @Override public void close(){if(!closed){closed=true;cancelled.set(true);result.cancel(false);worker.shutdown();}}
}
