import dev.jayms.net.model.*;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.security.MessageDigest;
import java.util.*;

/** Actual library staging diagnostic; not full package or system qualification. */
public final class ModelAdmissionProbe {
    static volatile ModelLibrary consumed;
    public static void main(String[] args)throws Exception {
        var tree=new SparseVoxelOctree(16);
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
            tree.set(x,y,z,0xff000000|((x*17)<<16)|((y*17)<<8)|z*17);
        tree.freeze();var definitions=new ArrayList<ModelDefinition>();
        for(int i=0;i<64;i++)definitions.add(new ModelDefinition("Component "+i,tree));
        if(ModelMesher.mesh(tree).quads()!=1536)throw new AssertionError("Fixture faces changed");
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);
        double[] times=new double[20];long[] allocations=new long[20];String identity=null;
        for(int run=-3;run<20;run++) {
            long before=bean.getCurrentThreadAllocatedBytes(),start=System.nanoTime();
            var library=new ModelLibrary();for(var definition:definitions)library.stageStructureModel(definition);
            consumed=library;
            double ms=(System.nanoTime()-start)/1e6;long allocated=bean.getCurrentThreadAllocatedBytes()-before;
            for(int i=0;i<64;i++)if(!definitions.get(i).fingerprint().equals(library.get(1024+i).definition().fingerprint()))throw new AssertionError("Staged identity changed");
            var buffer=new ByteArrayOutputStream();library.writeAll(new DataOutputStream(buffer));
            String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(buffer.toByteArray()));
            if(identity==null)identity=sha;else if(!identity.equals(sha))throw new AssertionError("World bytes changed between batches");
            if(run>=0){times[run]=ms;allocations[run]=allocated;}
        }
        Arrays.sort(times);Arrays.sort(allocations);
        System.out.printf(Locale.ROOT,"{\"implementation\":\"%s\",\"runs\":20,\"warmups\":3,\"models_per_batch\":64,\"quads_per_model\":1536,\"median_ms\":%.3f,\"p95_ms\":%.3f,\"worst_ms\":%.3f,\"median_thread_allocated_bytes\":%d,\"world_models_sha256\":\"%s\"}%n",
                args[0],(times[9]+times[10])/2,times[18],times[19],(allocations[9]+allocations[10])/2,identity);
    }
}
