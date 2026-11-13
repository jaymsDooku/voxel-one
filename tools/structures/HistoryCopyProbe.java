import dev.jayms.net.*;
import java.util.*;
import java.lang.management.ManagementFactory;

/** Transaction history copy diagnostic. Excludes save, geometry, rendering and traffic. */
public final class HistoryCopyProbe {
    static volatile WorldVoxels.History retained;
    public static void main(String[] args)throws Exception {
        if(args.length!=2||!List.of("single","fractional").contains(args[0])||!List.of("rebuild","shared").contains(args[1]))
            throw new IllegalArgumentException("single|fractional rebuild|shared required");
        boolean single=args[0].equals("single"),shared=args[1].equals("shared");
        var source=new WorldVoxels.History();
        for(int i=0;i<100000;i++) {
            int cell=single?i:i/4,part=single?0:i%4;
            var edit=single?new Protocol.Edit(cell%500,128,cell/500,Blocks.STONE):
                    new Protocol.Edit(cell%500,128,cell/500,Blocks.piece(Blocks.STONE,4),4,part,0,0,0xffffff);
            source.put(edit.key(),edit);
        }
        var expected=new ArrayList<>(source.entrySet());
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        double[] times=new double[20];long[] allocations=new long[20],firstEdit=new long[20];
        for(int run=-3;run<20;run++) {
            long bytes=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
            WorldVoxels.History copy;
            if(shared)copy=WorldVoxels.History.copyOf(source);
            else {copy=new WorldVoxels.History();copy.putAll(source);}
            double elapsed=(System.nanoTime()-start)/1e6;bytes=bean.getThreadAllocatedBytes(thread)-bytes;
            if(!expected.equals(new ArrayList<>(copy.entrySet())))throw new AssertionError("Replay order changed");
            var key=copy.keySet().iterator().next();long beforeEdit=bean.getThreadAllocatedBytes(thread);copy.remove(key);
            long editBytes=bean.getThreadAllocatedBytes(thread)-beforeEdit;
            if(!expected.equals(new ArrayList<>(source.entrySet())))throw new AssertionError("Source mutated");
            if(run>=0){times[run]=elapsed;allocations[run]=bytes;firstEdit[run]=editBytes;}retained=copy;
        }
        Arrays.sort(times);Arrays.sort(allocations);Arrays.sort(firstEdit);
        System.out.printf(Locale.ROOT,"{\"layout\":\"%s\",\"mode\":\"%s\",\"edits\":100000,\"warmups\":3,\"runs\":20,\"median_ms\":%.6f,\"p95_ms\":%.6f,\"worst_ms\":%.6f,\"median_copy_thread_allocated_bytes\":%d,\"median_first_remove_thread_allocated_bytes\":%d}%n",args[0],args[1],(times[9]+times[10])/2,times[18],times[19],(allocations[9]+allocations[10])/2,(firstEdit[9]+firstEdit[10])/2);
    }
}
