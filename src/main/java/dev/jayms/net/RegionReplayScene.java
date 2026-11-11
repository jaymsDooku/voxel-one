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
    record Status(RegionReplayWorker.Binding.ChunkPumpStatus data,int gpuEntries,int cleanupPending) {}
    private final Thread owner=Thread.currentThread();private final RegionReplayWorker.Binding binding;
    private final RegionReplayWorker.Binding.ChunkPump pump;private final World world;private final int capacity;
    private Set<ChunkPos> desired=Set.of();private Map<ChunkPos,RegionReplayChunkUpload.Resources> gpu=Map.of();
    private final List<RegionReplayChunkUpload.Resources> retired=new ArrayList<>();private boolean closed;
    RegionReplayScene(RegionReplayWorker.Binding binding,RegionReplayWorker.Binding.ChunkPump pump,World world,int capacity){this.binding=binding;this.pump=pump;this.world=world;this.capacity=capacity;}
    private void owner()throws IOException{if(Thread.currentThread()!=owner)throw new IOException("Replay scene owner required");}
    private void check()throws IOException{owner();if(closed)throw new IOException("Replay scene closed");if(!retired.isEmpty())throw new IOException("Replay scene cleanup pending");pump.status();}
    World.ReplayReadView readView() {
        return new World.ReplayReadView() {
            private RegionReplayChunk.Snapshot data(int x,int y,int z) {
                try{check();return new Protocol.Edit(x,y,z,0).valid()?pump.result(ChunkPos.fromBlock(x,y,z)):null;}
                catch(IOException failure){throw new IllegalStateException("Replay scene unavailable",failure);}
            }
            @Override public boolean ready(int x,int y,int z){return data(x,y,z)!=null;}
            @Override public boolean meshReady(int x,int y,int z) {
                if(data(x,y,z)==null)return false;
                try{return resources(ChunkPos.fromBlock(x,y,z))!=null;}
                catch(IOException failure){throw new IllegalStateException("Replay scene GPU unavailable",failure);}
            }
            @Override public SparseVoxelOctree cell(int x,int y,int z) {
                var chunk=data(x,y,z);if(chunk==null)throw new World.UnloadedReplayCellException(x,y,z);
                return chunk.geometry().region(Math.floorMod(x,16)*16,Math.floorMod(y,16)*16,Math.floorMod(z,16)*16,16).freeze();
            }
            @Override public void close(){try{RegionReplayScene.this.close();}catch(IOException failure){throw new IllegalStateException("Replay scene cleanup failed",failure);}}
        };
    }
    void desired(List<ChunkPos> regions)throws IOException {
        check();Objects.requireNonNull(regions);int count=regions.size();if(count<0||count>capacity)throw new IllegalArgumentException("Replay scene desired budget");
        var next=new LinkedHashSet<ChunkPos>();for(int i=0;i<count;i++)next.add(Objects.requireNonNull(regions.get(i)));
        if(regions.size()!=count)throw new ConcurrentModificationException("Replay scene desired changed");if(next.size()!=count)throw new IllegalArgumentException("Replay scene duplicate desired chunk");
        var nextGpu=new LinkedHashMap<ChunkPos,RegionReplayChunkUpload.Resources>();var removed=new ArrayList<RegionReplayChunkUpload.Resources>();
        for(var entry:gpu.entrySet())if(next.contains(entry.getKey()))nextGpu.put(entry.getKey(),entry.getValue());else removed.add(entry.getValue());
        var immutable=Set.copyOf(next);var immutableGpu=Collections.unmodifiableMap(nextGpu);
        pump.desired(List.copyOf(next));desired=immutable;gpu=immutableGpu;retired.addAll(removed);retryCleanup();
    }
    void tick()throws IOException{check();pump.tick();}
    IOException failure(ChunkPos region)throws IOException{check();return pump.failure(region);}
    Status status()throws IOException{check();return new Status(pump.status(),gpu.size(),retired.size());}
    RegionReplayChunkUpload beginUpload(ChunkPos region)throws IOException {
        check();if(!desired.contains(region)||gpu.containsKey(region))throw new IOException("Replay scene chunk not awaiting GPU");
        var request=pump.request(region);if(request==null)throw new IOException("Replay scene CPU data pending");return new RegionReplayChunkUpload(binding,request,world);
    }
    private void admissible(RegionReplayChunkUpload.Resources resources,RegionReplayChunk.Snapshot snapshot)throws IOException {
        check();resources.validate();if(!desired.contains(snapshot.region())||pump.result(snapshot.region())!=snapshot||gpu.containsKey(snapshot.region()))throw new IOException("Replay scene resource mismatch");
    }
    /** Ownership transfers only at the final map swap. Rejection/cancellation
     * leaves the complete resource owner with the caller, for retry or close. */
    void publish(RegionReplayChunkUpload.Resources resources,BooleanSupplier cancel)throws IOException {
        owner();Objects.requireNonNull(resources);Objects.requireNonNull(cancel);var snapshot=resources.snapshot();admissible(resources,snapshot);StructurePackage.cancelled(cancel);
        var prior=gpu;var next=new LinkedHashMap<>(prior);next.put(snapshot.region(),resources);var committed=Collections.unmodifiableMap(next);
        StructurePackage.cancelled(cancel);admissible(resources,snapshot);if(gpu!=prior)throw new IOException("Replay scene changed during publication");gpu=committed;
    }
    /** Borrowed owner. Never close/mutate its handles; Scene owns retirement. */
    RegionReplayChunkUpload.Resources resources(ChunkPos region)throws IOException {
        check();var result=gpu.get(region);if(result==null)return null;var snapshot=result.snapshot();
        if(pump.result(region)!=snapshot)throw new IOException("Replay scene data/GPU mismatch");return result;
    }
    void retryCleanup()throws IOException {
        owner();Throwable failure=null;
        for(var iterator=retired.iterator();iterator.hasNext();)try{iterator.next().close();iterator.remove();}catch(IOException|RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else failure.addSuppressed(cleanup);}
        if(failure instanceof IOException io)throw io;if(failure instanceof RuntimeException runtime)throw runtime;if(failure instanceof Error error)throw error;
    }
    @Override public void close()throws IOException {
        owner();closed=true;pump.close();desired=Set.of();retired.addAll(gpu.values());gpu=Map.of();retryCleanup();
    }
}
