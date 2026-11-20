package dev.jayms;

import dev.jayms.net.structure.*;
import java.nio.file.*;
import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.LockSupport;
import static dev.jayms.net.structure.StructurePackage.*;

/** Headless component comparison, not city/maximum or frame-time qualification. */
public final class SparseSnapshotBenchmark {
    record Sample(String mode,int run,long nanos,int ownerCalls,long maxOwnerNanos,long peakSampledHeapBytes) {}
    static Sample run(World world,StructureOwnerQueue queue,ExecutorService worker,StructurePackage asset,boolean sparse,int run)throws Exception {
        var heap=ManagementFactory.getMemoryMXBean();var peak=new AtomicLong(heap.getHeapMemoryUsage().getUsed());var sampling=new AtomicBoolean(true);
        var sampler=new Thread(()->{while(sampling.get()){peak.accumulateAndGet(heap.getHeapMemoryUsage().getUsed(),Math::max);LockSupport.parkNanos(2_000_000);}},"snapshot-heap-sampler");sampler.setDaemon(true);sampler.start();long start=System.nanoTime();
        var task=worker.submit(()->{var origin=new Position(0,160,0);var snapshot=sparse?StructureRegionSnapshot.captureOccupied(world,asset,origin,0,queue,()->false,p->{}):StructureRegionSnapshot.capture(world,origin,1024,256,1024,queue,()->false,p->{});for(var p:asset.occupiedPositions())if(!snapshot.emptyCell(p.x(),160+p.y(),p.z()))throw new AssertionError("Synthetic destination must stay empty");return null;});
        int calls=0;long max=0,deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
        try {
            while(!task.isDone()) {if(System.nanoTime()>deadline)throw new AssertionError("Component deadline");long before=System.nanoTime();if(queue.pump()){max=Math.max(max,System.nanoTime()-before);calls++;}else LockSupport.parkNanos(100_000);}
            task.get();long nanos=System.nanoTime()-start;peak.accumulateAndGet(heap.getHeapMemoryUsage().getUsed(),Math::max);return new Sample(sparse?"occupied":"bounding",run,nanos,calls,max,peak.get());
        }finally{sampling.set(false);sampler.join(5000);if(sampler.isAlive())throw new AssertionError("Heap sampler must stop");}
    }
    public static void main(String[] args)throws Exception {
        var asset=StructureSparseSnapshotTest.sparseAsset();var samples=new ArrayList<Sample>();
        try(var world=new World(42);var queue=new StructureOwnerQueue()) {
            var worker=Executors.newSingleThreadExecutor();try {
                run(world,queue,worker,asset,false,-1);run(world,queue,worker,asset,true,-1);
                for(int i=0;i<20;i++) {boolean first=i%2==0;samples.add(run(world,queue,worker,asset,first,i));samples.add(run(world,queue,worker,asset,!first,i));}
            }finally{worker.shutdownNow();if(!worker.awaitTermination(10,TimeUnit.SECONDS))throw new AssertionError("Worker must stop");}
            if(world.editsVersion()!=0||!world.getLoadedChunks().isEmpty())throw new AssertionError("Benchmark must not mutate/render the World");
        }
        for(var sample:samples)if(sample.ownerCalls()!=(sample.mode().equals("occupied")?3:514))throw new AssertionError("Expected exact owner request count");
        var text=new StringBuilder("{\"schema\":1,\"scope\":\"Headless snapshot component only: maximum bounds with four occupied cells, not the required maximum-limit fragmented fixture\",\"bounds\":[1024,256,1024],\"occupied_cells\":4,\"bounding_chunks\":65536,\"occupied_chunks\":4,\"runs_per_mode\":20,\"warmup_operations\":2,\"alternating_order\":true,\"heap_sample_period_ms\":2,\"heap_max_bytes\":").append(Runtime.getRuntime().maxMemory()).append(",\"available_processors\":").append(Runtime.getRuntime().availableProcessors()).append(",\"qualification\":false,\"samples\":[");
        for(int i=0;i<samples.size();i++){var s=samples.get(i);if(i>0)text.append(',');text.append("{\"mode\":\"").append(s.mode()).append("\",\"run\":").append(s.run()).append(",\"nanos\":").append(s.nanos()).append(",\"owner_calls\":").append(s.ownerCalls()).append(",\"max_owner_nanos\":").append(s.maxOwnerNanos()).append(",\"peak_sampled_heap_bytes\":").append(s.peakSampledHeapBytes()).append('}');}
        Files.writeString(Path.of(args[0]),text.append("]}\n").toString());System.out.println("PASS:20 runs per snapshot mode; source unchanged; no GL qualification.");
    }
}
