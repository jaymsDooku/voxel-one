package dev.jayms;

import org.joml.FrustumIntersection;
import java.util.*;
import java.util.concurrent.*;

/** One CPU worker, at most four immutable jobs, one sliced GL upload. Owned by the render thread. */
public final class DetailedMeshScheduler implements AutoCloseable {
    public static final int MAX_JOBS=4;
    public static final long MAX_RESULT_BYTES=48L*1024*1024;
    public record Result(ChunkPos position,Chunk chunk,long revision,MeshData data,long meshNanos,long created) {}
    private record Job(ChunkPos pos,Chunk chunk,long revision,long modelRevision,long created,Future<Result> future) {}
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(MAX_JOBS),r->{var t=new Thread(r,"detailed-mesh");t.setDaemon(true);return t;});
    private final Map<ChunkPos,Job> jobs=new LinkedHashMap<>();
    private final Map<ChunkPos,Long> failed=new HashMap<>();
    private final Map<ChunkPos,Long> waiting=new HashMap<>();
    private record Capture(ChunkPos pos,Chunk chunk,long revision,long models,long created,ChunkSnapshot snapshot) {}
    private Capture capture;
    private Mesh.Upload upload;private Result uploading;
    private long uploadModels;
    public long totalNanos,snapshotNanos,meshNanos,uploadNanos,uploadBytes,rejected;
    public String error="";
    private boolean closed;
    public int queued(){return jobs.size()+(upload==null?0:1)+(capture==null?0:1);}
    public int waiting(){return waiting.size();}
    public long oldestMillis(){long now=System.nanoTime(),oldest=now;for(var j:jobs.values())oldest=Math.min(oldest,j.created);for(long t:waiting.values())oldest=Math.min(oldest,t);if(uploading!=null)oldest=Math.min(oldest,uploading.created);if(capture!=null)oldest=Math.min(oldest,capture.created);return (now-oldest)/1_000_000;}
    public static long bytes(MeshData d){return d==null?0:4L*(d.vertices().length+d.indices().length+(d.surface()==null?0:d.surface().length));}
    private boolean valid(World world,Job j){return world.getLoadedChunks().get(j.pos)==j.chunk&&j.chunk.meshRevision()==j.revision&&world.models().revision()==j.modelRevision;}
    public boolean request(World world,ChunkPos p) {
        if(closed||capture!=null||queued()>=MAX_JOBS||jobs.containsKey(p)||uploading!=null&&uploading.position.equals(p))return false;
        Chunk c=world.getLoadedChunks().get(p);if(c==null||!c.dirty()||Objects.equals(failed.get(p),c.meshRevision()))return false;
        worker.purge();
        long start=System.nanoTime();var snapshot=ChunkSnapshot.begin(world,p,c,true);snapshotNanos+=System.nanoTime()-start;
        var next=new Capture(p,c,c.meshRevision(),world.models().revision(),System.nanoTime(),snapshot);
        waiting.remove(p);
        if(snapshot.complete())submit(next);else capture=next;
        return true;
    }
    private boolean valid(World world,Capture c){return world.getLoadedChunks().get(c.pos)==c.chunk&&c.chunk.meshRevision()==c.revision&&world.models().revision()==c.models;}
    private void submit(Capture c) {
        var future=worker.submit(()->{long then=System.nanoTime();MeshData data=c.snapshot.isEmpty()?null:MeshDataGenerator.generate(c.snapshot);if(bytes(data)>MAX_RESULT_BYTES)throw new IllegalStateException("Chunk mesh exceeds bounded completion budget");return new Result(c.pos,c.chunk,c.revision,data,System.nanoTime()-then,c.created);});
        jobs.put(c.pos,new Job(c.pos,c.chunk,c.revision,c.models,c.created,future));
    }
    private void capture(World world,long deadline) {
        if(capture==null)return;
        if(!valid(world,capture)){capture=null;rejected++;return;}
        long start=System.nanoTime();boolean complete=capture.snapshot.pump(world,capture.pos,deadline,1536);
        snapshotNanos+=System.nanoTime()-start;
        if(complete){submit(capture);capture=null;}
    }
    /** Pure result fencing is exposed for CPU checks; no GL work here. */
    public Result ready(World world) {
        capture(world,System.nanoTime()+250_000L);return readyWithoutCapture(world);
    }
    private Result readyWithoutCapture(World world) {
        var it=jobs.values().iterator();
        while(it.hasNext()) {
            Job j=it.next();
            if(!valid(world,j)){j.future.cancel(true);it.remove();rejected++;continue;}
            if(!j.future.isDone())continue;it.remove();
            try{Result r=j.future.get();if(!valid(world,j)){rejected++;continue;}meshNanos+=r.meshNanos;return r;}
            catch(InterruptedException e){Thread.currentThread().interrupt();return null;}
            catch(ExecutionException|CancellationException e){failed.put(j.pos,j.revision);error="A chunk mesh failed; retained its previous mesh. Edit it to retry.";}
        }
        return null;
    }
    public void update(World world,float x,float z,FrustumIntersection frustum,int detailBlocks,int passBlocks,long nanosBudget,int bytesBudget) {
        long updateStart=System.nanoTime();
        snapshotNanos=meshNanos=uploadNanos=uploadBytes=0;
        long deadline=System.nanoTime()+nanosBudget;
        failed.keySet().removeIf(p->!world.getLoadedChunks().containsKey(p));
        waiting.keySet().removeIf(p->{Chunk c=world.getLoadedChunks().get(p);return c==null||!c.dirty()||(c.getMesh()==null&&(Math.abs(p.chunkX()*16+8-x)>passBlocks||Math.abs(p.chunkZ()*16+8-z)>passBlocks));});
        if(uploading!=null&&(world.getLoadedChunks().get(uploading.position)!=uploading.chunk||uploading.chunk.meshRevision()!=uploading.revision||world.models().revision()!=uploadModels)){upload.close();upload=null;uploading=null;rejected++;}
        capture(world,deadline);
        long start=System.nanoTime();
        try {
        if(upload==null) {
            Result next=readyWithoutCapture(world);
            if(next!=null){if(next.data==null)next.chunk.installMesh(null,null,next.revision);else{uploading=next;uploadModels=world.models().revision();upload=new Mesh.Upload(next.data);}}
        }
        if(upload!=null){uploadBytes=upload.pump(bytesBudget,deadline);if(upload.ready()){uploading.chunk.installMesh(upload.take(),uploading.data,uploading.revision);upload.close();upload=null;uploading=null;}}
        }catch(RuntimeException e){if(upload!=null)upload.close();if(uploading!=null)failed.put(uploading.position,uploading.revision);upload=null;uploading=null;error="A chunk upload failed; retained its previous mesh. Edit it to retry.";}
        uploadNanos=System.nanoTime()-start;
        var dirtyIterator=world.dirtyMeshes().entrySet().iterator();
        while(dirtyIterator.hasNext()){var e=dirtyIterator.next();
            if(capture!=null&&capture.pos.equals(e.getKey())||jobs.containsKey(e.getKey())||uploading!=null&&uploading.position.equals(e.getKey())||Objects.equals(failed.get(e.getKey()),e.getValue().meshRevision())){waiting.remove(e.getKey());continue;}
            if(e.getValue().isEmpty()){dirtyIterator.remove();e.getValue().installMesh(null,null,e.getValue().meshRevision());waiting.remove(e.getKey());continue;}
            if(e.getValue().getMesh()!=null||Math.abs(e.getKey().chunkX()*16+8-x)<=passBlocks&&Math.abs(e.getKey().chunkZ()*16+8-z)<=passBlocks)waiting.putIfAbsent(e.getKey(),System.nanoTime());
        }
        // Select one minimum at a time only when slots are free; no full-frame list copy/sort.
        while(capture==null&&queued()<MAX_JOBS&&System.nanoTime()<deadline) {
            ChunkPos best=null;double score=Double.POSITIVE_INFINITY;long now=System.nanoTime();
            for(var e:waiting.entrySet()){
                ChunkPos p=e.getKey();Chunk c=world.getLoadedChunks().get(p);
                if(jobs.containsKey(p)||uploading!=null&&uploading.position.equals(p)||Objects.equals(failed.get(p),c.meshRevision()))continue;
                double distance=Math.hypot(p.chunkX()*16+8-x,p.chunkZ()*16+8-z);
                boolean visible=Math.abs(p.chunkX()*16+8-x)<=detailBlocks&&Math.abs(p.chunkZ()*16+8-z)<=detailBlocks&&frustum.testAab(p.chunkX()*16,p.chunkY()*16,p.chunkZ()*16,p.chunkX()*16+16,p.chunkY()*16+16,p.chunkZ()*16+16);
                // Age eventually outranks visibility/distance, including background jobs.
                double candidate=distance+(visible?0:256)+(c.getMesh()==null?-128:0)-(now-e.getValue())/1e9*64;
                if(candidate<score){score=candidate;best=p;}
            }
            if(best==null||!request(world,best))break;
        }
        totalNanos=System.nanoTime()-updateStart;
    }
    @Override public void close(){closed=true;capture=null;for(var j:jobs.values())j.future.cancel(true);jobs.clear();waiting.clear();worker.shutdownNow();if(upload!=null)upload.close();upload=null;uploading=null;}
}
