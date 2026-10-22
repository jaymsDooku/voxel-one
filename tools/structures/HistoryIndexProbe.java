import dev.jayms.net.*;
import java.util.*;
import java.lang.management.ManagementFactory;

/** History storage diagnostic, not city/save/render/network qualification. */
public final class HistoryIndexProbe {
    static volatile WorldVoxels.History retained;
    static long heap()throws Exception{System.gc();Thread.sleep(100);System.gc();Thread.sleep(100);var r=Runtime.getRuntime();return r.totalMemory()-r.freeMemory();}
    public static void main(String[] args)throws Exception {
        if(args.length!=1||!List.of("single","fractional").contains(args[0]))throw new IllegalArgumentException("single or fractional required");
        boolean single=args[0].equals("single");long baseline=heap();var source=new WorldVoxels.History();
        for(int i=0;i<100000;i++) {
            int cell=single?i:i/4,part=single?0:i%4;
            var e=single?new Protocol.Edit(cell%500,128,cell/500,Blocks.STONE):
                    new Protocol.Edit(cell%500,128,cell/500,Blocks.piece(Blocks.STONE,4),4,part,0,0,0xffffff);
            source.put(e.key(),e);
        }
        retained=source;long sourceHeap=heap()-baseline;
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        double[] times=new double[20];long[] allocations=new long[20];
        for(int run=-3;run<20;run++) {
            long bytes=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
            var copy=new WorldVoxels.History();copy.putAll(source);
            double elapsed=(System.nanoTime()-start)/1e6;bytes=bean.getThreadAllocatedBytes(thread)-bytes;
            if(run>=0){times[run]=elapsed;allocations[run]=bytes;}
            if(!new ArrayList<>(source.entrySet()).equals(new ArrayList<>(copy.entrySet())))throw new AssertionError("Replay order changed");
            var key=copy.keySet().iterator().next();copy.remove(key);if(source.size()!=100000)throw new AssertionError("Source changed");
            retained=copy;
        }
        Arrays.sort(times);Arrays.sort(allocations);
        System.out.printf(Locale.ROOT,"{\"layout\":\"%s\",\"edits\":100000,\"warmups\":3,\"runs\":20,\"source_retained_heap_delta_bytes\":%d,\"median_copy_allocated_bytes\":%d,\"median_copy_ms\":%.6f,\"p95_copy_ms\":%.6f,\"worst_copy_ms\":%.6f}%n",args[0],sourceHeap,(allocations[9]+allocations[10])/2,(times[9]+times[10])/2,times[18],times[19]);
    }
}
