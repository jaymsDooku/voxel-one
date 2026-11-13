import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.lang.management.ManagementFactory;
import java.security.MessageDigest;
import java.util.*;
import static dev.jayms.net.structure.StructurePackage.*;

/** Synthetic capture/import allocation diagnostic, not a peak-memory or city benchmark. */
public final class StreamAllocationProbe {
    static volatile StructurePackage retained;
    public static void main(String[] args) throws Exception {
        var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));tree.freeze();
        var models=new ModelLibrary();
        Source source=new Source(){public SparseVoxelOctree cell(int x,int y,int z){return tree;}public ModelLibrary models(){return models;}};
        var bounds=new Bounds(500,1,200,0,0,0);var origin=new Position(0,180,0);
        retained=StructurePackage.capture("Stream probe",bounds,origin,source,()->false,p->{});byte[] bytes=retained.encode();
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        for(String operation:List.of("capture","decode")) {
            for(int i=0;i<3;i++)retained=operation.equals("capture")?StructurePackage.capture("Stream probe",bounds,origin,source,()->false,p->{}):StructurePackage.decode(bytes);
            long[] allocation=new long[20];double[] times=new double[20];
            for(int i=0;i<20;i++) {
                long before=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
                retained=operation.equals("capture")?StructurePackage.capture("Stream probe",bounds,origin,source,()->false,p->{}):StructurePackage.decode(bytes);
                times[i]=(System.nanoTime()-start)/1e6;allocation[i]=bean.getThreadAllocatedBytes(thread)-before;
            }
            Arrays.sort(allocation);Arrays.sort(times);
            System.out.printf(Locale.ROOT,"{\"operation\":\"%s\",\"cells\":100000,\"warmups\":3,\"runs\":20,\"median_thread_allocated_bytes\":%d,\"median_ms\":%.3f,\"p95_ms\":%.3f,\"worst_ms\":%.3f}%n",operation,(allocation[9]+allocation[10])/2,(times[9]+times[10])/2,times[18],times[19]);
        }
        if(!Arrays.equals(bytes,retained.encode()))throw new AssertionError("Roundtrip bytes changed");
        System.out.println("{\"encoded_sha256\":\""+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))+"\",\"qualification\":\"synthetic capture/import allocations exclude other threads; not peak memory or city/GPU/network qualification\"}");
    }
}
