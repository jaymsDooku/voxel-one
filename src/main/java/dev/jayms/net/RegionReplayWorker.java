package dev.jayms.net;

import dev.jayms.ChunkPos;
import dev.jayms.World;
import dev.jayms.net.structure.StructurePackage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/** Private asynchronous readback, not a live archive or installation API.
 * The factory runs once on this worker and must validate the complete world,
 * including its tail, before returning an owned source. On factory failure it
 * must clean any resources it has not returned. No index or spool crosses threads.
 * Callers still own real authority/model/terrain identity and revision fences. */
final class RegionReplayWorker implements AutoCloseable {
    static final int MAX_QUEUED=8;
    record Revision(long world,long models,long terrain) {}
    record Source(RegionReplaySpool spool,Terrain terrain,Predicate<Protocol.Edit> allowed,Revision revision,String fingerprint,LocalGame.ReadbackMetadata metadata) {
        Source(RegionReplaySpool spool,Terrain terrain,Predicate<Protocol.Edit> allowed,Revision revision){this(spool,terrain,allowed,revision,"",null);}
        Source(RegionReplaySpool spool,Terrain terrain,Predicate<Protocol.Edit> allowed,Revision revision,String fingerprint){this(spool,terrain,allowed,revision,fingerprint,null);}
        Source {
            Objects.requireNonNull(spool);Objects.requireNonNull(terrain);Objects.requireNonNull(allowed);Objects.requireNonNull(revision);Objects.requireNonNull(fingerprint);
            if(metadata!=null&&!fingerprint.equals(metadata.fingerprint()))throw new IllegalArgumentException("Replay metadata fingerprint mismatch");
            if(!fingerprint.isEmpty()&&!fingerprint.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Replay source fingerprint");
        }
    }
    @FunctionalInterface interface Factory {Source open(BooleanSupplier cancel,IntConsumer checkedRows)throws IOException;}
    enum State {OPENING,RUNNING,CLOSING,CLEANUP_FAILED,CLOSED,FAILED}
    enum Phase {QUEUED,LOADING,READY,CANCELLED,FAILED}
    record Status(State state,int sourceRows,IOException failure) {}
    record Progress(Phase phase,IOException failure) {}
    private record Outcome(Phase phase,RegionReplayPage page,IOException failure) {}
    final class Request {
        private final ChunkPos region;
        private final RegionReplayPage.CellRange range;
        private final int maxRows;
        private final Revision revision;
        private final World.ReplayFence fence;
        private final AtomicInteger checkedRows=new AtomicInteger();
        private final AtomicReference<Outcome> outcome=new AtomicReference<>(new Outcome(Phase.QUEUED,null,null));
        private Request(ChunkPos region,RegionReplayPage.CellRange range,int maxRows,Revision revision,World.ReplayFence fence) {
            this.region=region;this.range=range;this.maxRows=maxRows;this.revision=revision;this.fence=fence;
        }
        Progress outcome(){var value=outcome.get();return new Progress(value.phase(),value.failure());}
        int checkedRows(){return checkedRows.get();}
        boolean cancelled(){return outcome.get().phase()==Phase.CANCELLED;}
        /** Nonblocking; cancellation wins if it precedes terminal publication. */
        boolean cancel() {
            for(;;) {
                var old=outcome.get();if(terminal(old.phase()))return false;
                if(outcome.compareAndSet(old,new Outcome(Phase.CANCELLED,null,null))) {
                    synchronized(gate){queue.remove(this);gate.notifyAll();}return true;
                }
            }
        }
        private void finish(Outcome next) {
            for(;;){var old=outcome.get();if(terminal(old.phase()))return;if(outcome.compareAndSet(old,next))return;}
        }
        /** Owner-side nonblocking extraction. These supplied revision tags do
         * not replace checks against actual live object identities/revisions. */
        RegionReplayPage result(Revision current)throws IOException {
            owner();
            if(fence!=null)throw new IOException("Replay request requires its world fence");
            return extract(current);
        }
        /** Checks actual owner/world state. Still a detached result: source
         * generation, dependencies, coverage and installation remain separate. */
        RegionReplayPage result(Revision current,World world)throws IOException {
            owner();
            if(fence==null)throw new IOException("Replay request has no world fence");
            if(stopping.get()||status.state()!=State.RUNNING)throw new IOException("Replay worker no longer active");
            validateWorld(world);var page=extract(current);validateWorld(world);return page;
        }
        private void validateWorld(World world)throws IOException {
            try{fence.validate(world);}
            catch(IllegalStateException|ConcurrentModificationException changed){throw new IOException("Replay target world changed",changed);}
        }
        private RegionReplayPage extract(Revision current)throws IOException {
            owner();Objects.requireNonNull(current);
            if(!revision.equals(current))throw new IOException("Replay request revision changed");
            var value=outcome.get();
            if(value.phase()==Phase.CANCELLED)throw new CancellationException("Replay request cancelled");
            if(value.phase()==Phase.FAILED)throw value.failure();
            if(value.phase()!=Phase.READY)throw new IllegalStateException("Replay request not complete");
            return value.page();
        }
    }
    private static boolean terminal(Phase phase){return phase==Phase.READY||phase==Phase.CANCELLED||phase==Phase.FAILED;}
    private final Thread owner=Thread.currentThread();
    private final Object gate=new Object();
    private final ArrayDeque<Request> queue=new ArrayDeque<>();
    private final AtomicBoolean stopping=new AtomicBoolean();
    private final int capacity;
    private volatile Status status=new Status(State.OPENING,0,null);
    private volatile String fingerprint="";
    private LocalGame.ReadbackMetadata metadata;
    private Revision sourceRevision;
    String fingerprint(){return fingerprint;}
    /** Owner-only, nonblocking single transfer. Prepared maps become owner-owned;
     * the worker's private model-policy maps remain separate. No live install. */
    LocalGame metadataResult(Revision current)throws IOException {
        owner();Objects.requireNonNull(current);
        synchronized(gate) {
            if(stopping.get()||status.state()!=State.RUNNING)throw new IOException("Replay metadata not available");
            if(!current.equals(sourceRevision))throw new IOException("Replay metadata revision changed");
            if(metadata==null)throw new IOException("Replay metadata unavailable or already transferred");
            var result=metadata.transfer();metadata=null;return result;
        }
    }
    private Request active; // worker-owned except under gate during close
    private boolean retryCleanup;

    RegionReplayWorker(Path directory,Factory factory,int capacity,int maxRegions,long maxBytes,int maxRegionRows) {
        Objects.requireNonNull(directory);Objects.requireNonNull(factory);
        if(capacity<1||capacity>MAX_QUEUED||maxRegions<1||maxRegions>RegionReplayCache.MAX_REGIONS||
                maxBytes<0||maxBytes>RegionReplayCache.MAX_BYTES||maxRegionRows<0||maxRegionRows>LocalGame.MAX_SAVED_EDITS||
                (long)maxRegionRows*RegionReplayCells.RECORD_BYTES>maxBytes)
            throw new IllegalArgumentException("Replay worker budget");
        this.capacity=capacity;
        var thread=new Thread(()->run(directory,factory,maxRegions,maxBytes,maxRegionRows),"voxel-region-readback");
        thread.setDaemon(true);thread.start();
    }
    Status status(){return status;}
    private void owner()throws IOException{if(Thread.currentThread()!=owner)throw new IOException("Replay worker owner required");}
    Request submit(ChunkPos region,RegionReplayPage.CellRange range,int maxRows,Revision revision)throws IOException {
        return enqueue(region,range,maxRows,revision,null);
    }
    /** Owner-side constant-field capture; no model/history copy or file I/O. */
    Request submitFenced(ChunkPos region,RegionReplayPage.CellRange range,int maxRows,Revision revision,World world)throws IOException {
        owner();Objects.requireNonNull(world);
        return enqueue(region,range,maxRows,revision,world.beginReplayFence());
    }
    private Request enqueue(ChunkPos region,RegionReplayPage.CellRange range,int maxRows,Revision revision,World.ReplayFence fence)throws IOException {
        owner();Objects.requireNonNull(region);Objects.requireNonNull(range);Objects.requireNonNull(revision);
        if(range.size()>RegionReplayPage.MAX_CELLS||maxRows<0||maxRows>RegionReplayPage.MAX_ROWS)
            throw new IllegalArgumentException("Replay worker page budget");
        synchronized(gate) {
            if(stopping.get())throw new IOException("Replay worker not accepting requests");
            if(queue.size()>=capacity)throw new IOException("Replay worker queue full");
            var request=new Request(region,range,maxRows,revision,fence);queue.addLast(request);gate.notifyAll();return request;
        }
    }
    private void run(Path directory,Factory factory,int maxRegions,long maxBytes,int maxRegionRows) {
        Source source=null;RegionReplayCache cache=null;IOException failure=null;
        try {
            source=Objects.requireNonNull(factory.open(stopping::get,rows->{
                synchronized(gate){if(!stopping.get())status=new Status(State.OPENING,Math.max(status.sourceRows(),Math.max(0,Math.min(rows,LocalGame.MAX_SAVED_EDITS))),null);}
            }));
            StructurePackage.cancelled(stopping::get);
            fingerprint=source.fingerprint();metadata=source.metadata();sourceRevision=source.revision();
            var opened=source;
            cache=new RegionReplayCache(directory,source.spool(),maxRegions,maxBytes,maxRegionRows,edit->{
                var request=active;if(request!=null)request.checkedRows.incrementAndGet();return opened.allowed().test(edit);
            });
            synchronized(gate){if(!stopping.get())status=new Status(State.RUNNING,status.sourceRows(),null);}
            for(;;) {
                Request request;
                synchronized(gate) {
                    while(queue.isEmpty()&&!stopping.get())gate.wait();
                    if(stopping.get())break;request=queue.removeFirst();active=request;
                }
                try {
                    if(request.cancelled())continue;
                    request.finish(new Outcome(Phase.LOADING,null,null));
                    if(!request.revision.equals(source.revision()))throw new IOException("Replay source revision mismatch");
                    var page=cache.load(request.region,request.range,source.terrain(),request.maxRows,
                            ()->stopping.get()||request.cancelled());
                    StructurePackage.cancelled(()->stopping.get()||request.cancelled());
                    request.finish(new Outcome(Phase.READY,page,null));
                }catch(CancellationException cancelled){request.cancel();}
                catch(IOException|RuntimeException error){request.finish(new Outcome(Phase.FAILED,null,new IOException("Replay page request failed",error)));}
                catch(Error error){request.finish(new Outcome(Phase.FAILED,null,new IOException("Replay page worker failed",error)));throw error;}
                finally{synchronized(gate){active=null;}}
            }
        }catch(CancellationException cancelled) {
            if(!stopping.get())failure=new IOException("Replay source cancelled",cancelled);
        }catch(Exception|Error error){failure=new IOException("Replay worker failed",error);}
        finally {
            synchronized(gate) {
                stopping.set(true);
                if(active!=null)active.finish(new Outcome(Phase.FAILED,null,new IOException("Replay worker stopped")));
                for(var request:queue) {
                    if(failure==null)request.finish(new Outcome(Phase.CANCELLED,null,null));
                    else request.finish(new Outcome(Phase.FAILED,null,failure));
                }
                queue.clear();active=null;metadata=null;status=new Status(State.CLOSING,status.sourceRows(),failure);
            }
            // Cleanup stays on the resource-owning worker. Retry only after an
            // explicit close call; never loop/poll on filesystem failures.
            boolean cacheClosed=cache==null,sourceClosed=source==null;
            for(;;) {
                IOException cleanup=null;
                if(!cacheClosed)try{cache.close();cacheClosed=true;}catch(IOException error){cleanup=error;}
                if(!sourceClosed)try{source.spool().close();sourceClosed=true;}catch(IOException error){if(cleanup==null)cleanup=error;else cleanup.addSuppressed(error);}
                synchronized(gate) {
                    if(cleanup==null){status=new Status(failure==null?State.CLOSED:State.FAILED,status.sourceRows(),failure);return;}
                    status=new Status(State.CLEANUP_FAILED,status.sourceRows(),cleanup);
                    while(!retryCleanup)try{gate.wait();}catch(InterruptedException interrupted){/* Retry still needs an explicit close. */}
                    retryCleanup=false;
                }
            }
        }
    }
    /** Owner-side signal only: never joins the worker or waits for I/O. A later
     * close retries failed cleanup on that same worker. Poll status for receipt. */
    @Override public void close()throws IOException {
        owner();synchronized(gate) {
            if(status.state()==State.CLOSED||status.state()==State.FAILED)return;
            stopping.set(true);if(status.state()==State.CLEANUP_FAILED)retryCleanup=true;
            if(active!=null)active.cancel();
            for(var request:queue)request.finish(new Outcome(Phase.CANCELLED,null,null));queue.clear();
            if(status.state()!=State.CLEANUP_FAILED)status=new Status(State.CLOSING,status.sourceRows(),null);
            gate.notifyAll();
        }
    }
}
