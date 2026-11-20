import dev.jayms.net.model.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;

/** Validation/visitor component diagnostic; no package admission or world mutation. */
public final class ModelValidationProbe {
    static volatile Object retained;
    static volatile long consumed;
    static double rank(double[] values,int rank){var copy=values.clone();Arrays.sort(copy);return copy[rank];}
    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("fixture scratch-directory implementation-label");
        var models=new ArrayList<ModelDefinition>();var identities=new ArrayList<String>();long leaves=0;
        try(var index=DiskPackageIndexPrototype.open(Path.of(args[0]),Path.of(args[1]),()->false)) {
            for(int i=0;i<index.models.size();i++) {
                var source=index.model(i,()->false);var model=new ModelDefinition(source.name(),source.voxels().packedModelCopy());
                if(!source.fingerprint().equals(model.fingerprint()))throw new AssertionError("Fixture identity mismatch");
                models.add(model);identities.add(model.fingerprint());leaves+=model.voxels().leafCount();
            }
        }
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");bean.setThreadAllocatedMemoryEnabled(true);
        long[] digest={0,0};SparseVoxelOctree.LeafVisitor visitor=(x,y,z,side,color)->{digest[0]=digest[0]*31+x+17L*y+257L*z+4099L*side+color;digest[1]++;};
        for(var model:models)model.voxels().forEachLeaf(visitor);long expected=digest[0];
        for(String operation:List.of("validation","visitor")) {
            double[] times=new double[20];long[] allocations=new long[20];
            for(int run=-3;run<20;run++) {
                digest[0]=digest[1]=0;long before=bean.getCurrentThreadAllocatedBytes(),start=System.nanoTime(),count=0;
                for(var model:models) {
                    if(operation.equals("validation")){var result=new ModelDefinition(model.name(),model.voxels());retained=result;count+=result.voxels().leafCount();}
                    else model.voxels().forEachLeaf(visitor);
                }
                long elapsed=System.nanoTime()-start,allocated=bean.getCurrentThreadAllocatedBytes()-before;
                if(operation.equals("validation")){if(count!=leaves)throw new AssertionError("Validation count mismatch");consumed=count;}
                else {if(digest[0]!=expected||digest[1]!=leaves)throw new AssertionError("Visitor mismatch");consumed=digest[0];}
                if(run>=0){times[run]=elapsed/1e6;allocations[run]=allocated;}
            }
            var sorted=allocations.clone();Arrays.sort(sorted);
            System.out.printf(Locale.ROOT,"{\"implementation\":\"%s\",\"operation\":\"%s\",\"models_per_batch\":%d,\"stored_leaves_per_batch\":%d,\"runs\":20,\"warmups\":3,\"median_ms\":%.6f,\"p95_ms\":%.6f,\"worst_ms\":%.6f,\"median_thread_allocated_bytes\":%d,\"visitor_checksum\":%d,\"samples_ms\":%s,\"allocated_samples_bytes\":%s,\"qualification\":false}%n",args[2],operation,models.size(),leaves,(rank(times,9)+rank(times,10))/2,rank(times,18),rank(times,19),(sorted[9]+sorted[10])/2,expected,Arrays.toString(times),Arrays.toString(allocations));
        }
        for(int i=0;i<models.size();i++)if(!identities.get(i).equals(models.get(i).fingerprint()))throw new AssertionError("Source mutated");
    }
}
