package dev.jayms.net;

import dev.jayms.net.structure.StructurePackage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Private owner coordinator for a published save's replacement read-only source.
 * Later journal edits retire an unconsumed candidate before a fresh worker opens.
 * No joins or filesystem work run here; overlay capture/adoption can still take
 * linear CPU time. No Main installation, editing or partial-save authority. */
final class SavedPagingReplacement implements AutoCloseable {
    static final int MAX_ATTEMPTS=3;
    enum State {OPENING,READY,RETIRING,RETRY_REQUIRED,CANCELLED,FAILED,TRANSFERRED}
    record Status(State state,int attempts,RegionReplayWorker.Status worker,IOException failure) {}
    private final Thread owner=Thread.currentThread();
    private final ReadbackSaveSnapshot.Publication publication;
    private final RegionReplayWorker retired;
    private final Path directory;
    private final RegionReplayWorker.Revision first;
    private final int capacity,maxCellLeaves;
    private RegionReplayWorker candidate;
    private RegionReplayWorker.Revision revision;
    private RegionReplayJournal.TailStamp stamp;
    private RegionReplayOverlay pending;
    private State state=State.OPENING;
    private int attempts;
    private boolean busy,restart,cancelled;
    private IOException failure;

    SavedPagingReplacement(ReadbackSaveSnapshot.Publication publication,RegionReplayWorker retired,Path directory,
            RegionReplayWorker.Revision first,int capacity,int maxCellLeaves,BooleanSupplier cancel)throws IOException {
        this.publication=Objects.requireNonNull(publication);this.retired=Objects.requireNonNull(retired);
        this.directory=Objects.requireNonNull(directory);this.first=Objects.requireNonNull(first);
        if(capacity<1||capacity>2||maxCellLeaves<0||maxCellLeaves>RegionReplayChunk.MAX_CELL_LEAVES
                ||first.world()>Long.MAX_VALUE-(MAX_ATTEMPTS-1))throw new IllegalArgumentException("Saved paging replacement budget");
        this.capacity=capacity;this.maxCellLeaves=maxCellLeaves;
        Objects.requireNonNull(cancel);busy=true;
        try{start(cancel);}finally{busy=false;}
    }
    private void enter()throws IOException {
        if(Thread.currentThread()!=owner)throw new IOException("Saved paging replacement owner required");
        if(busy)throw new IOException("Saved paging replacement reentry");busy=true;
    }
    private void start(BooleanSupplier cancel)throws IOException {
        StructurePackage.cancelled(cancel);
        var before=publication.pendingPagingStamp(cancel);var overlay=publication.pendingPagingOverlay(cancel);
        var after=publication.pendingPagingStamp(cancel);
        if(!before.equals(after))throw new IOException("Saved paging tail changed during capture");
        var next=new RegionReplayWorker.Revision(first.world()+attempts,first.models(),first.terrain());
        var factory=publication.pagingFactory(retired,directory,next,overlay);
        // Fixed private diagnostic limits. At most one worker and no more than
        // two cache regions; these are not city support/performance limits.
        var worker=new RegionReplayWorker(directory,factory,1,2,4L*1024*1024,32768);
        candidate=worker;revision=next;pending=overlay;stamp=after;attempts++;restart=false;state=State.OPENING;
    }
    Status status()throws IOException {
        if(Thread.currentThread()!=owner)throw new IOException("Saved paging replacement owner required");
        return new Status(state,attempts,candidate==null?null:candidate.status(),failure);
    }
    private void retire(boolean retry)throws IOException {
        boolean alreadyRetiring=state==State.RETIRING;restart=retry;state=State.RETIRING;
        if(candidate!=null&&!alreadyRetiring&&candidate.status().state()!=RegionReplayWorker.State.CLEANUP_FAILED)candidate.close();
    }
    /** One owner tick. Cleanup failures stay visible until explicit close retries
     * cleanup. Never opens a second worker while the first still owns resources. */
    State poll(BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(cancel);enter();
        try{return advance(cancel);}
        catch(CancellationException stop){cancelled=true;retire(false);throw stop;}
        catch(IOException|RuntimeException|Error error){failure=error instanceof IOException io?io:new IOException("Saved paging replacement failed",error);retire(false);throw error;}
        finally{busy=false;}
    }
    private State advance(BooleanSupplier cancel)throws IOException {
        if(state==State.TRANSFERRED||state==State.CANCELLED||state==State.FAILED||state==State.RETRY_REQUIRED)return state;
        StructurePackage.cancelled(cancel);
        if(state==State.RETIRING) {
            var current=candidate==null?null:candidate.status();
            if(current!=null&&current.state()!=RegionReplayWorker.State.CLOSED&&current.state()!=RegionReplayWorker.State.FAILED)return state;
            candidate=null;
            if(restart) {
                if(attempts==MAX_ATTEMPTS){failure=new IOException("Saved paging tail kept changing; retry required");state=State.RETRY_REQUIRED;}
                else start(cancel);
            }else state=cancelled?State.CANCELLED:State.FAILED;
            return state;
        }
        var current=candidate.status();
        if(current.state()!=RegionReplayWorker.State.OPENING&&current.state()!=RegionReplayWorker.State.RUNNING) {
            failure=current.failure()==null?new IOException("Saved paging worker stopped before transfer"):current.failure();
            retire(false);return state;
        }
        if(!stamp.equals(publication.pendingPagingStamp(cancel))) {retire(true);return state;}
        if(current.state()==RegionReplayWorker.State.RUNNING)state=State.READY;
        return state;
    }
    /** Preallocate the handle before the existing atomic metadata/journal transfer.
     * No callback, cancellation or allocation follows successful adoption. */
    Transfer take(BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(cancel);enter();
        try {
            if(advance(cancel)!=State.READY)throw new IOException("Saved paging replacement not ready");
            var result=new Transfer(candidate,revision);
            result.session=publication.adoptPagingScene(candidate,revision,pending,capacity,maxCellLeaves,cancel);
            candidate=null;state=State.TRANSFERRED;return result;
        }catch(CancellationException stop){cancelled=true;retire(false);throw stop;}
        finally{busy=false;}
    }
    /** Returned ownership includes both scene and worker. Close signals cleanup;
     * callers still observe the worker's terminal cleanup receipt separately. */
    static final class Transfer implements AutoCloseable {
        private final Thread owner=Thread.currentThread();
        private final RegionReplayWorker worker;
        private final RegionReplayWorker.Revision revision;
        private RegionReplayWorker.PagingSession session;
        private Transfer(RegionReplayWorker worker,RegionReplayWorker.Revision revision){this.worker=worker;this.revision=revision;}
        RegionReplayWorker worker(){return worker;}
        RegionReplayWorker.Revision revision(){return revision;}
        RegionReplayWorker.PagingSession session(){return session;}
        @Override public void close()throws Exception {
            if(Thread.currentThread()!=owner)throw new IOException("Saved paging transfer owner required");
            try{session.close();}finally{worker.close();}
        }
    }
    /** Explicit close also retries a worker's failed cleanup. A transferred
     * handle is caller-owned and never closed by this retired coordinator. */
    @Override public void close()throws IOException {
        enter();
        try{if(state!=State.TRANSFERRED){cancelled=true;restart=false;state=State.RETIRING;if(candidate==null)state=State.CANCELLED;else candidate.close();}}
        finally{busy=false;}
    }
}
