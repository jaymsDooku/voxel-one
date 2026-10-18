package dev.jayms;

import dev.jayms.net.Terrain;
import dev.jayms.net.city.StressGrid;
import java.util.*;
import java.util.concurrent.*;

/** Detached base terrain only. One worker owns its terrain caches; the caller owns all world edits. */
public final class TerrainScheduler implements AutoCloseable {
    /** Fixed detached-chunk window; the owner still attaches one chunk per deadline check. */
    public static final int CAPACITY = 16;
    public record Result(ChunkPos position, Chunk chunk, long generationNanos, long requestedNanos) {}
    @FunctionalInterface interface Generator { Chunk generate(ChunkPos position, Terrain terrain); }
    private static final class Job {
        final ChunkPos position;
        final StressGrid grid;
        final long requested = System.nanoTime();
        boolean running, obsolete, done;
        Result result;
        Throwable failure;
        Job(ChunkPos position, StressGrid grid) { this.position=position; this.grid=grid; }
    }
    private final Map<ChunkPos, Job> jobs = new LinkedHashMap<>();
    private final ArrayDeque<FutureTask<?>> preparation=new ArrayDeque<>();
    private int preparing;
    private FutureTask<?> activePreparation;
    private final Thread worker;
    /** Two coalesced sky tasks share this CPU lane; they cannot crowd out terrain. */
    public synchronized <T> Future<T> prepare(Callable<T> work) {
        if(closed)throw new IllegalStateException("Terrain scheduler closed");
        if(preparing>=2)throw new RejectedExecutionException("CPU preparation capacity reached");
        var future=new PublishedFuture<T>(work);preparation.add(future);preparing++;notifyAll();return future;
    }
    private boolean closed;
    public long discarded;

    public TerrainScheduler(long seed, int version) { this(seed,version,ChunkGenerator::generate); }
    TerrainScheduler(long seed, int version, Generator generator) {
        worker = new Thread(() -> work(new Terrain(seed,version),generator), "terrain-generation");
        worker.setDaemon(true);
        worker.start();
    }
    public synchronized boolean request(ChunkPos position, StressGrid grid) {
        if(closed || jobs.size()>=CAPACITY || jobs.containsKey(position)) return false;
        jobs.put(position,new Job(position,grid));notifyAll();return true;
    }
    public synchronized int pending() { return jobs.size(); }
    public synchronized long oldestMillis() {
        long oldest=System.nanoTime();for(Job j:jobs.values())oldest=Math.min(oldest,j.requested);
        return (System.nanoTime()-oldest)/1_000_000;
    }
    /** Running obsolete work keeps its slot until it exits. Reversals cannot create hidden work. */
    public synchronized void cancel(ChunkPos position) {
        Job j=jobs.get(position);if(j==null)return;
        if(j.running&&!j.done)j.obsolete=true;else jobs.remove(position);
    }
    public synchronized void discardOutside(int x,int z,int radius) {
        for(var it=jobs.values().iterator();it.hasNext();) {
            Job j=it.next();
            if(Math.abs(j.position.chunkX()-x)<=radius&&Math.abs(j.position.chunkZ()-z)<=radius)continue;
            if(j.running&&!j.done)j.obsolete=true;else it.remove();
        }
    }
    public synchronized Result poll() {
        for(var it=jobs.values().iterator();it.hasNext();) {
            Job j=it.next();if(!j.done)continue;it.remove();
            if(j.obsolete){discarded++;continue;}
            if(j.failure!=null)throw new IllegalStateException("Detached terrain generation failed",j.failure);
            return j.result;
        }
        return null;
    }
    private void work(Terrain terrain,Generator generator) {
        boolean lastTerrain=false;
        while(true) {
            Job job;FutureTask<?> auxiliary=null;
            synchronized(this) {
                while(true) {
                    if(closed)return;
                    job=jobs.values().stream().filter(j->!j.running&&!j.done).findFirst().orElse(null);
                    if(!preparation.isEmpty()&&(lastTerrain||job==null)){auxiliary=preparation.remove();activePreparation=auxiliary;lastTerrain=false;break;}
                    if(job!=null){job.running=true;lastTerrain=true;break;}
                    try{wait();}catch(InterruptedException e){if(closed)return;}
                }
            }
            if(auxiliary!=null) {
                try{auxiliary.run();}finally{
                    synchronized(this){activePreparation=null;preparing--;notifyAll();}
                    Thread.interrupted();
                }
                continue;
            }
            Result result=null;Throwable failure=null;
            try {
                if(terrain.stressGrid()!=job.grid)terrain.stressGrid(job.grid);
                long start=System.nanoTime();Chunk chunk=generator.generate(job.position,terrain);
                result=new Result(job.position,chunk,System.nanoTime()-start,job.requested);
            }catch(Throwable e){failure=e;}
            synchronized(this) {
                if(closed)return;
                job.result=result;job.failure=failure;job.done=true;
                if(job.obsolete){jobs.remove(job.position,job);discarded++;}
                notifyAll();
            }
        }
    }
    @Override public void close() {
        synchronized(this){closed=true;jobs.clear();for(var task:preparation)task.cancel(false);preparing-=preparation.size();preparation.clear();if(activePreparation!=null)activePreparation.cancel(true);notifyAll();}
        worker.interrupt();
        try{worker.join(5000);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        if(worker.isAlive())throw new IllegalStateException("Terrain worker did not stop");
    }
}
