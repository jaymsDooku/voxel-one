package dev.jayms.net;

import dev.jayms.ChunkPos;
import dev.jayms.CloseSequence;
import dev.jayms.World;
import dev.jayms.net.structure.StructurePackage;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Bounded read-only saved-world rendering session. Opening validates/spools on
 * one worker; poll creates a partial World without complete geometry replay.
 * READY means metadata/scene availability, not loaded cells or full coverage.
 * No LocalGame, save, edit, Main-gameplay or qualified city support is exposed. */
public final class ReadOnlyWorldSession implements AutoCloseable {
    public static final int MAX_VISIBLE_CHUNKS=2;
    public static final int MAX_CELL_LEAVES=RegionReplayChunk.MAX_CELL_LEAVES;
    public enum State {OPENING,READY,UNAVAILABLE,CLOSING,CLEANUP_FAILED,CLOSED,FAILED}
    /** CPU/GPU counts during a callback describe the last completed owner step. */
    public record Status(State state,int checkedRows,int readyCpuChunks,int readyGpuChunks,int failedChunks,boolean ownerStepActive,Exception failure) {}
    private static final RegionReplayWorker.Revision REVISION=new RegionReplayWorker.Revision(1,0,0);
    private final Thread owner=Thread.currentThread();
    private final RegionReplayWorker worker;
    private final int capacity,maxCellLeaves;
    private RegionReplayWorker.PagingSession session;
    private boolean busy,closing,worldClean;
    private Exception worldFailure;
    private int cpuReady,gpuReady,failedChunks;

    public ReadOnlyWorldSession(Path file,Path scratch)throws IOException {this(file,scratch,1,8192);}
    public ReadOnlyWorldSession(Path file,Path scratch,int capacity,int maxCellLeaves)throws IOException {
        Objects.requireNonNull(file);Objects.requireNonNull(scratch);
        if(capacity<1||capacity>MAX_VISIBLE_CHUNKS||maxCellLeaves<0||maxCellLeaves>MAX_CELL_LEAVES)
            throw new IllegalArgumentException("Read-only world scene budget");
        this.capacity=capacity;this.maxCellLeaves=maxCellLeaves;var input=file.toAbsolutePath().normalize();var directory=scratch.toAbsolutePath().normalize();
        worker=new RegionReplayWorker(directory,(cancel,progress)->{
            Files.createDirectories(directory);return LocalGame.openRegionalReadback(input,directory,REVISION,cancel,progress);
        },1,2,4L*1024*1024,32768);
    }
    private void owner()throws IOException {if(Thread.currentThread()!=owner)throw new IOException("Read-only world owner required");}
    private void enter()throws IOException {owner();if(busy||closing)throw new IOException("Read-only world unavailable or reentry");busy=true;}
    private RegionReplayWorker.PagingSession ready()throws IOException {
        if(session==null||closing||worker.status().state()!=RegionReplayWorker.State.RUNNING)throw new IOException("Read-only world not ready");return session;
    }
    private void remember()throws IOException {var current=session.scene().status();cpuReady=current.data().ready();gpuReady=current.gpuEntries();failedChunks=current.data().failed();}
    /** Nonblocking owner step. Cancellation before scene commit leaves metadata
     * retryable. Close cancels the lifetime and starts asynchronous cleanup. */
    public Status poll(BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(cancel);enter();
        try {
            StructurePackage.cancelled(cancel);var current=worker.status();
            if(current.state()==RegionReplayWorker.State.RUNNING) {
                if(session==null)session=worker.metadataResultScene(REVISION,capacity,maxCellLeaves,()->StructurePackage.cancelled(cancel));
                remember();
            }else if(current.state()!=RegionReplayWorker.State.OPENING)throw current.failure()==null?new IOException("Read-only world source stopped"):current.failure();
        }finally{busy=false;}
        return status();
    }
    public Status status()throws IOException {
        owner();var current=worker.status();State state;Exception failure=worldFailure==null?current.failure():worldFailure;
        if(closing) {
            if(worldFailure!=null||current.state()==RegionReplayWorker.State.CLEANUP_FAILED)state=State.CLEANUP_FAILED;
            else if(worldClean&&(current.state()==RegionReplayWorker.State.CLOSED||current.state()==RegionReplayWorker.State.FAILED))state=State.CLOSED;
            else state=State.CLOSING;
        }else if(current.state()==RegionReplayWorker.State.OPENING)state=State.OPENING;
        else if(current.state()==RegionReplayWorker.State.FAILED)state=State.FAILED;
        else if(current.state()==RegionReplayWorker.State.CLEANUP_FAILED)state=State.CLEANUP_FAILED;
        else if(current.state()!=RegionReplayWorker.State.RUNNING)state=State.UNAVAILABLE;
        else if(session==null)state=State.OPENING;
        else {
            state=State.READY;
            if(!busy)try{remember();}catch(IOException|RuntimeException invalid){state=State.UNAVAILABLE;failure=invalid;}
        }
        return new Status(state,current.sourceRows(),cpuReady,gpuReady,failedChunks,busy,failure);
    }
    /** Unknown/unmeshed cells remain loading boundaries in this partial World. */
    public World world()throws IOException {
        owner();if(busy)throw new IOException("Read-only world reentry");var result=ready();remember();return result.world();
    }
    public void desired(List<ChunkPos> chunks)throws IOException {
        enter();try{ready().scene().desired(chunks);remember();}finally{busy=false;}
    }
    public IOException failure(ChunkPos chunk)throws IOException {
        owner();if(busy)throw new IOException("Read-only world reentry");return ready().scene().failure(Objects.requireNonNull(chunk));
    }
    public void tick()throws IOException {enter();try{ready().scene().tick();remember();}finally{busy=false;}}
    /** Must run with the caller's current GL context. Existing scene upload and
     * cancellation budgets apply; this does not promise frame-time qualification. */
    public int pumpGpu(int bytes,long deadline,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(cancel);enter();try{int used=ready().scene().pumpGpu(bytes,deadline,cancel);remember();return used;}finally{busy=false;}
    }
    /** Explicit resource retry only; status/poll never retry cleanup. */
    public void retryCleanup()throws IOException {enter();try{ready().scene().retryCleanup();remember();}finally{busy=false;}}
    @Override public void close()throws Exception {
        owner();if(busy)throw new IOException("Read-only world close reentry");closing=true;busy=true;var cleanup=new CloseSequence();
        try {
            if(session==null)worldClean=true;
            else if(!worldClean)cleanup.run(()->{
                try{session.close();worldClean=true;worldFailure=null;cpuReady=0;gpuReady=0;failedChunks=0;}
                catch(Exception error){worldFailure=error;throw error;}
                catch(Error error){worldFailure=new IOException("Read-only world cleanup failed",error);throw error;}
            });
            cleanup.run(worker::close);cleanup.finish();
        }finally{busy=false;}
    }
}
