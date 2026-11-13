import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.util.*;
import java.lang.management.ManagementFactory;
import static dev.jayms.net.structure.StructurePackage.*;

/** Counts allocations on the construction thread, not peak memory or city qualification. */
public final class ConstructorAllocationProbe {
    private static volatile StructurePackage retained;
    public static void main(String[] args) throws Exception {
        var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        var input=new ArrayList<Cell>();
        for(int i=0;i<100000;i++)input.add(new Cell(new Position(i%512,0,i/512),tree,-1));
        Collections.shuffle(input,new Random(20261021));
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Thread allocation counter unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        for(int i=0;i<3;i++)retained=new StructurePackage("Allocation probe",new Bounds(512,1,256,0,0,0),List.of(),input);
        long[] allocated=new long[20];double[] milliseconds=new double[20];
        for(int i=0;i<20;i++) {
            long before=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
            retained=new StructurePackage("Allocation probe",new Bounds(512,1,256,0,0,0),List.of(),input);
            milliseconds[i]=(System.nanoTime()-start)/1e6;allocated[i]=bean.getThreadAllocatedBytes(thread)-before;
        }
        Arrays.sort(allocated);Arrays.sort(milliseconds);
        System.out.printf(Locale.ROOT,"{\"cells\":100000,\"warmups\":3,\"runs\":20,\"median_thread_allocated_bytes\":%d,\"median_ms\":%.3f,\"p95_ms\":%.3f,\"worst_ms\":%.3f,\"qualification\":\"synthetic constructor diagnostic only; allocations exclude prepared input and other threads, not peak memory or city qualification\"}%n",(allocated[9]+allocated[10])/2,(milliseconds[9]+milliseconds[10])/2,milliseconds[18],milliseconds[19]);
    }
}
