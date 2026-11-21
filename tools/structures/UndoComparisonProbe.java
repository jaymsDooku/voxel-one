import dev.jayms.net.model.SparseVoxelOctree;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;

/** Isolated undo-comparison diagnostic; excludes placement, persistence and rendering. */
public final class UndoComparisonProbe {
    static volatile int sink;
    public static void main(String[] args)throws Exception {
        if(args.length!=1||Files.exists(Path.of(args[0])))throw new IllegalArgumentException("Fresh result path required");
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Thread allocation counters unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        var rows=new ArrayList<String>();
        for(boolean dense:new boolean[]{false,true}) {
            var a=new SparseVoxelOctree(16);
            if(dense)for(int i=0;i<4096;i++)a.fill(i%16,i/256,i/16%16,i%16+1,i/256+1,i/16%16+1,0xfe000000|i);
            else a.fill(0,0,0,16,16,16,0xff123456);
            var bytes=new ByteArrayOutputStream();a.write(new DataOutputStream(bytes));
            var b=SparseVoxelOctree.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
            int cells=dense?256:131072;double[][] times=new double[2][20],allocations=new double[2][20];
            for(int run=-3;run<20;run++)for(int mode=0;mode<2;mode++) {
                long allocated=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();int matched=0;
                for(int i=0;i<cells;i++)if(mode==0?a.leaves().equals(b.leaves()):a.sameValues(b))matched++;
                long elapsed=System.nanoTime()-start,used=bean.getThreadAllocatedBytes(thread)-allocated;
                if(matched!=cells)throw new AssertionError("Comparison mismatch");sink=matched;
                if(run>=0){times[mode][run]=elapsed/1e6;allocations[mode][run]=used;}
            }
            rows.add("{\"dense\":"+dense+",\"cells\":"+cells+",\"legacy_ms\":"+stats(times[0])+",\"semantic_ms\":"+stats(times[1])+",\"legacy_allocated_bytes\":"+stats(allocations[0])+",\"semantic_allocated_bytes\":"+stats(allocations[1])+"}");
        }
        Files.writeString(Path.of(args[0]),"{\"scope\":\"Shared VPS isolated comparison loop, independently decoded equal cells, legacy first, three warmups and 20 measured runs. ThreadMXBean thread allocation bytes, not peak memory. Excludes cell lookup, locks, save, network, GL and city/max qualification.\",\"results\":["+String.join(",",rows)+"]}\n");
    }
    static String stats(double[] values){Arrays.sort(values);return String.format(Locale.ROOT,"{\"median\":%.6f,\"p95\":%.6f,\"worst\":%.6f}",(values[9]+values[10])/2,values[18],values[19]);}
}
