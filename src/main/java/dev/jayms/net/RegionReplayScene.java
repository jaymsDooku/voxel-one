package dev.jayms.net;
import dev.jayms.*;
import dev.jayms.net.model.SparseVoxelOctree;
import dev.jayms.net.structure.StructurePackage;
import java.io.IOException;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Private owner/GL scene over the existing <=2-chunk pump. Complete immutable
 * entry-map publication gates World.isLoaded; no resident World/history/save
 * promotion, Main startup or general rendering integration. */
final class RegionReplayScene implements AutoCloseable {
    record Status(RegionReplayWorker.Binding.ChunkPumpStatus data,int gpuEntries,int cleanupPending,ChunkPos uploading) {}
    private final Thread owner=Thread.currentThread();private final RegionReplayWorker.Binding binding;
    private final RegionReplayWorker.Binding.ChunkPump pump;private final World world;private final int capacity;
    private Set<ChunkPos> desired=Set.of();private Map<ChunkPos,RegionReplayChunkUpload.Resources> gpu=Map.of();
    private final List<RegionReplayChunkUpload.Resources> retired=new ArrayList<>();private boolean closed;private long renderRevision;
    private RegionReplayChunkUpload upload;private ChunkPos uploading;private boolean pumping,uploadCleanupPending;
    RegionReplayScene(RegionReplayWorker.Binding binding,RegionReplayWorker.Binding.ChunkPump pump,World world,int capacity){this.binding=binding;this.pump=pump;this.world=world;this.capacity=capacity;}
    private void owner()throws IOException{if(Thread.currentThread()!=owner)throw new IOException("Replay scene owner required");}
    private void check()throws IOException{owner();if(closed)throw new IOException("Replay scene closed");if(uploadCleanupPending||!retired.isEmpty())throw new IOException("Replay scene cleanup pending");pump.status();}
    private void idle()throws IOException{owner();if(pumping)throw new IOException("Replay scene pump reentry");}
    World.ReplayReadView readView() {
        return new World.ReplayReadView() {
            private RegionReplayChunk.Snapshot data(int x,int y,int z) {
                try{check();return new Protocol.Edit(x,y,z,0).valid()?pump.result(ChunkPos.fromBlock(x,y,z)):null;}
                catch(IOException failure){throw new IllegalStateException("Replay scene unavailable",failure);}
            }
            @Override public boolean ready(int x,int y,int z){return data(x,y,z)!=null;}
            @Override public long renderRevision(){try{check();return RegionReplayScene.this.renderRevision;}catch(IOException failure){throw new IllegalStateException("Replay scene unavailable",failure);}}
            @Override public int render(org.joml.FrustumIntersection frustum,ShaderProgram shader,boolean models){try{return draw(frustum,shader,models,false);}catch(IOException failure){throw new IllegalStateException("Replay scene draw unavailable",failure);}}
            @Override public World.ReplayWaterSurface nearestWater(float x,float y,float z){try{return water(x,y,z);}catch(IOException failure){throw new IllegalStateException("Replay water unavailable",failure);}}
            @Override public int renderWater(org.joml.FrustumIntersection frustum,ShaderProgram shader){try{return draw(frustum,shader,false,true);}catch(IOException failure){throw new IllegalStateException("Replay water draw unavailable",failure);}}
            @Override public boolean meshReady(int x,int y,int z) {
                if(data(x,y,z)==null)return false;
                try{return resources(ChunkPos.fromBlock(x,y,z))!=null;}
                catch(IOException failure){throw new IllegalStateException("Replay scene GPU unavailable",failure);}
            }
            @Override public SparseVoxelOctree cell(int x,int y,int z) {
                var chunk=data(x,y,z);if(chunk==null)throw new World.UnloadedReplayCellException(x,y,z);
                return chunk.geometry().region(Math.floorMod(x,16)*16,Math.floorMod(y,16)*16,Math.floorMod(z,16)*16,16).freeze();
            }
            @Override public void checkClose(){try{idle();}catch(IOException failure){throw new IllegalStateException("Replay scene close unavailable",failure);}}
            @Override public void close(){try{RegionReplayScene.this.close();}catch(IOException failure){throw new IllegalStateException("Replay scene cleanup failed",failure);}}
        };
    }
    void desired(List<ChunkPos> regions)throws IOException {
        idle();check();Objects.requireNonNull(regions);int count=regions.size();if(count<0||count>capacity)throw new IllegalArgumentException("Replay scene desired budget");
        var next=new LinkedHashSet<ChunkPos>();for(int i=0;i<count;i++)next.add(Objects.requireNonNull(regions.get(i)));
        if(regions.size()!=count)throw new ConcurrentModificationException("Replay scene desired changed");if(next.size()!=count)throw new IllegalArgumentException("Replay scene duplicate desired chunk");
        var nextGpu=new LinkedHashMap<ChunkPos,RegionReplayChunkUpload.Resources>();var removed=new ArrayList<RegionReplayChunkUpload.Resources>();
        for(var entry:gpu.entrySet())if(next.contains(entry.getKey()))nextGpu.put(entry.getKey(),entry.getValue());else removed.add(entry.getValue());
        var immutable=Collections.unmodifiableSet(new LinkedHashSet<>(next));var immutableGpu=Collections.unmodifiableMap(nextGpu);
        if(uploading!=null&&!next.contains(uploading))abortUpload();
        pump.desired(List.copyOf(next));desired=immutable;gpu=immutableGpu;if(!removed.isEmpty())renderRevision++;retired.addAll(removed);retryCleanup();
    }
    void tick()throws IOException{idle();check();pump.tick();}
    IOException failure(ChunkPos region)throws IOException{check();return pump.failure(region);}
    Status status()throws IOException{check();return new Status(pump.status(),gpu.size(),retired.size()+(uploadCleanupPending?1:0),uploading);}
    RegionReplayChunkUpload beginUpload(ChunkPos region)throws IOException {
        idle();check();if(upload!=null)throw new IOException("Replay scene automatic upload active");if(!desired.contains(region)||gpu.containsKey(region))throw new IOException("Replay scene chunk not awaiting GPU");
        var request=pump.request(region);if(request==null)throw new IOException("Replay scene CPU data pending");return new RegionReplayChunkUpload(binding,request,world);
    }
    private void admissible(RegionReplayChunkUpload.Resources resources,RegionReplayChunk.Snapshot snapshot)throws IOException {
        check();resources.validate();if(!desired.contains(snapshot.region())||pump.result(snapshot.region())!=snapshot||gpu.containsKey(snapshot.region()))throw new IOException("Replay scene resource mismatch");
    }
    /** Ownership transfers only at the final map swap. Rejection/cancellation
     * leaves the complete resource owner with the caller, for retry or close. */
    void publish(RegionReplayChunkUpload.Resources resources,BooleanSupplier cancel)throws IOException {
        idle();publishResource(resources,cancel);
    }
    private void publishResource(RegionReplayChunkUpload.Resources resources,BooleanSupplier cancel)throws IOException {
        owner();Objects.requireNonNull(resources);Objects.requireNonNull(cancel);var snapshot=resources.snapshot();admissible(resources,snapshot);StructurePackage.cancelled(cancel);
        var prior=gpu;var next=new LinkedHashMap<>(prior);next.put(snapshot.region(),resources);var committed=Collections.unmodifiableMap(next);
        StructurePackage.cancelled(cancel);admissible(resources,snapshot);if(gpu!=prior)throw new IOException("Replay scene changed during publication");gpu=committed;renderRevision++;
    }
    /** One private chunk upload, one lane and one shared byte/deadline budget
     * per call. Existing published entries are never rolled back on failure. */
    int pumpGpu(int bytes,long deadline,BooleanSupplier cancel)throws IOException {
        idle();Objects.requireNonNull(cancel);if(bytes<0||bytes>RegionReplayMeshUpload.MAX_PUMP_BYTES)throw new IllegalArgumentException("Replay scene upload byte budget");
        pumping=true;RegionReplayChunkUpload.Resources completed=null;
        try {
            check();StructurePackage.cancelled(cancel);
            if(bytes<4||System.nanoTime()>=deadline)return 0;
            if(upload==null)for(var region:desired)if(!gpu.containsKey(region)) {
                var request=pump.request(region);if(request!=null){upload=new RegionReplayChunkUpload(binding,request,world);uploading=region;break;}
            }
            if(upload==null)return 0;
            int written=upload.pump(bytes,deadline,cancel);
            if(upload.ready()) {
                completed=upload.take(cancel);upload=null;uploading=null;
                publishResource(completed,cancel);completed=null;
            }
            return written;
        }catch(IOException|RuntimeException|Error failure) {
            if(completed!=null)try{completed.close();}catch(IOException|RuntimeException|Error cleanup){retired.add(completed);failure.addSuppressed(cleanup);}
            try{abortUpload();}catch(IOException|RuntimeException|Error cleanup){failure.addSuppressed(cleanup);}
            throw failure;
        }finally{pumping=false;}
    }
    private void abortUpload()throws IOException {
        if(upload!=null){uploadCleanupPending=true;upload.close();upload=null;uploading=null;uploadCleanupPending=false;}
    }
    /** Borrowed owner. Never close/mutate its handles; Scene owns retirement. */
    RegionReplayChunkUpload.Resources resources(ChunkPos region)throws IOException {
        check();var result=gpu.get(region);if(result==null)return null;var snapshot=result.snapshot();
        if(pump.result(region)!=snapshot)throw new IOException("Replay scene data/GPU mismatch");return result;
    }
    /** Validate the entire bounded scene before issuing any draw. The caller
     * owns the bound program/pass state; this method always leaves instancing off. */
    private int draw(org.joml.FrustumIntersection frustum,ShaderProgram shader,boolean models,boolean waterOnly)throws IOException {
        check();Objects.requireNonNull(frustum);Objects.requireNonNull(shader);
        for(var region:gpu.keySet())resources(region);
        int draws=0;
        shader.setInt("uVertexColor",1);shader.setInt("uInstanced",models?1:0);
        try {
            for(var entry:gpu.entrySet()) {
                if(waterOnly&&!Float.isFinite(entry.getValue().snapshot().waterHeight()))continue;
                var p=entry.getKey();float x=p.chunkX()*16f,y=p.chunkY()*16f,z=p.chunkZ()*16f;
                if(!frustum.testAab(x,y,z,x+16,y+16,z+16))continue;
                var value=entry.getValue();shader.setMatrix4("uModel",new org.joml.Matrix4f().translation(x,y,z));
                if(models){for(var batch:value.models().batches())if(batch.mesh()!=null){batch.mesh().renderPreparedInstances();draws++;}}
                else {var mesh=value.ordinary();if(mesh!=null){mesh.render();draws++;}}
            }
            return draws;
        }finally{shader.setInt("uInstanced",0);}
    }
    private World.ReplayWaterSurface water(float x,float y,float z)throws IOException {
        check();World.ReplayWaterSurface nearest=null;double distance=Double.POSITIVE_INFINITY;
        for(var region:gpu.keySet()) {
            var value=resources(region);var snapshot=value.snapshot();if(value.ordinary()==null||!Float.isFinite(snapshot.waterHeight()))continue;
            double dx=region.chunkX()*16.0+8-x,dy=region.chunkY()*16.0+8-y,dz=region.chunkZ()*16.0+8-z,d=dx*dx+dy*dy+dz*dz;
            if(d<distance){distance=d;nearest=new World.ReplayWaterSurface(region,region.chunkY()*16f+snapshot.waterHeight());}
        }
        return nearest;
    }
    void retryCleanup()throws IOException {
        idle();cleanup();
    }
    private void cleanup()throws IOException {
        Throwable failure=null;
        if(uploadCleanupPending)try{abortUpload();}catch(IOException|RuntimeException|Error problem){failure=problem;}
        for(var iterator=retired.iterator();iterator.hasNext();)try{iterator.next().close();iterator.remove();}catch(IOException|RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else failure.addSuppressed(cleanup);}
        if(failure instanceof IOException io)throw io;if(failure instanceof RuntimeException runtime)throw runtime;if(failure instanceof Error error)throw error;
    }
    @Override public void close()throws IOException {
        idle();closed=true;pump.close();desired=Set.of();retired.addAll(gpu.values());gpu=Map.of();if(upload!=null)uploadCleanupPending=true;cleanup();
    }
}
