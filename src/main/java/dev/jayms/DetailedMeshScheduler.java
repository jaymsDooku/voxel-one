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
    private Mesh.Upload upload;private Result uploading;
    private long uploadModels;
    public long snapshotNanos,meshNanos,uploadNanos,uploadBytes,rejected;
    public String error="";
    private boolean closed;
    public int queued(){return jobs.size()+(upload==null?0:1);}
    public int waiting(){return waiting.size();}
    public long oldestMillis(){long now=System.nanoTime(),oldest=now;for(var j:jobs.values())oldest=Math.min(oldest,j.created);for(long t:waiting.values())oldest=Math.min(oldest,t);if(uploading!=null)oldest=Math.min(oldest,uploading.created);return (now-oldest)/1_000_000;}
    public static long bytes(MeshData d){return d==null?0:4L*(d.vertices().length+d.indices().length+(d.surface()==null?0:d.surface().length));}
    private boolean valid(World world,Job j){return world.getLoadedChunks().get(j.pos)==j.chunk&&j.chunk.meshRevision()==j.revision&&world.models().revision()==j.modelRevision;}
    public boolean request(World world,ChunkPos p) {
        if(closed||jobs.size()+(upload==null?0:1)>=MAX_JOBS||jobs.containsKey(p)||uploading!=null&&uploading.position.equals(p))return false;
        Chunk c=world.getLoadedChunks().get(p);if(c==null||!c.dirty()||Objects.equals(failed.get(p),c.meshRevision()))return false;
        worker.purge();
        long start=System.nanoTime();var snapshot=new ChunkSnapshot(world,p,c,true);snapshotNanos+=System.nanoTime()-start;
        long rev=c.meshRevision(),models=world.models().revision(),created=System.nanoTime();
        var future=worker.submit(()->{long then=System.nanoTime();MeshData data=snapshot.isEmpty()?null:MeshDataGenerator.generate(snapshot);if(bytes(data)>MAX_RESULT_BYTES)throw new IllegalStateException("Chunk mesh exceeds bounded completion budget");return new Result(p,c,rev,data,System.nanoTime()-then,created);});
        jobs.put(p,new Job(p,c,rev,models,created,future));waiting.remove(p);return true;
    }
    /** Pure result fencing is exposed for CPU checks; no GL work here. */
    public Result ready(World world) {
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
        snapshotNanos=meshNanos=uploadNanos=uploadBytes=0;
        long deadline=System.nanoTime()+nanosBudget;
        failed.keySet().removeIf(p->!world.getLoadedChunks().containsKey(p));
        waiting.keySet().removeIf(p->{Chunk c=world.getLoadedChunks().get(p);return c==null||!c.dirty()||(c.getMesh()==null&&(Math.abs(p.chunkX()*16+8-x)>passBlocks||Math.abs(p.chunkZ()*16+8-z)>passBlocks));});
        if(uploading!=null&&(world.getLoadedChunks().get(uploading.position)!=uploading.chunk||uploading.chunk.meshRevision()!=uploading.revision||world.models().revision()!=uploadModels)){upload.close();upload=null;uploading=null;rejected++;}
        long start=System.nanoTime();
        try {
        if(upload==null) {
            Result next=ready(world);
            if(next!=null){if(next.data==null)next.chunk.installMesh(null,null,next.revision);else{uploading=next;uploadModels=world.models().revision();upload=new Mesh.Upload(next.data);}}
        }
        if(upload!=null){uploadBytes=upload.pump(bytesBudget,deadline);if(upload.ready()){uploading.chunk.installMesh(upload.take(),uploading.data,uploading.revision);upload.close();upload=null;uploading=null;}}
        }catch(RuntimeException e){if(upload!=null)upload.close();if(uploading!=null)failed.put(uploading.position,uploading.revision);upload=null;uploading=null;error="A chunk upload failed; retained its previous mesh. Edit it to retry.";}
        uploadNanos=System.nanoTime()-start;
        var dirtyIterator=world.dirtyMeshes().entrySet().iterator();
        while(dirtyIterator.hasNext()){var e=dirtyIterator.next();
            if(jobs.containsKey(e.getKey())||uploading!=null&&uploading.position.equals(e.getKey())||Objects.equals(failed.get(e.getKey()),e.getValue().meshRevision())){waiting.remove(e.getKey());continue;}
            if(e.getValue().isEmpty()){dirtyIterator.remove();e.getValue().installMesh(null,null,e.getValue().meshRevision());waiting.remove(e.getKey());continue;}
            if(e.getValue().getMesh()!=null||Math.abs(e.getKey().chunkX()*16+8-x)<=passBlocks&&Math.abs(e.getKey().chunkZ()*16+8-z)<=passBlocks)waiting.putIfAbsent(e.getKey(),System.nanoTime());
        }
        // Select one minimum at a time only when slots are free; no full-frame list copy/sort.
        while(queued()<MAX_JOBS&&System.nanoTime()<deadline) {
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
    }
    @Override public void close(){closed=true;for(var j:jobs.values())j.future.cancel(true);jobs.clear();waiting.clear();worker.shutdownNow();if(upload!=null)upload.close();upload=null;uploading=null;}
}
