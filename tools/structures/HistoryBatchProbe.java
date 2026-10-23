import dev.jayms.net.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;

/** Isolated history-copy lock-work diagnostic; not a server or structure benchmark. */
public final class HistoryBatchProbe {
    static volatile WorldVoxels.History retained;
    public static void main(String[] args)throws Exception {
        if(args.length!=1||Files.exists(Path.of(args[0])))throw new IllegalArgumentException("Fresh result path required");
        var source=new WorldVoxels.History();var lock=new Object();
        for(int i=0;i<262144;i++){var e=new Protocol.Edit(i%512,180,i/512,Blocks.STONE);source.put(e.key(),e);}
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counters unavailable");bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        double[] clone=new double[20],batchTotal=new double[20],batchMax=new double[20],batchSum=new double[20],cloneBytes=new double[20],batchBytes=new double[20];
        for(int run=-3;run<20;run++) {
            long bytes=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();WorldVoxels.History direct;
            synchronized(lock){direct=WorldVoxels.History.copyOf(source);}long directTime=System.nanoTime()-start,directBytes=bean.getThreadAllocatedBytes(thread)-bytes;
            check(source,direct);retained=direct;
            bytes=bean.getThreadAllocatedBytes(thread);start=System.nanoTime();WorldVoxels.History.CopyCursor cursor;long readStart=System.nanoTime();synchronized(lock){cursor=source.copyCursor();}long max=System.nanoTime()-readStart,sum=max;
            var copy=new WorldVoxels.History();
            while(true) {
                List<Protocol.Edit> batch;readStart=System.nanoTime();synchronized(lock){batch=cursor.nextBatch();}long elapsed=System.nanoTime()-readStart;max=Math.max(max,elapsed);sum+=elapsed;
                if(batch.isEmpty())break;
                if(batch.size()>1024)throw new AssertionError("Batch cap");for(var edit:batch)copy.put(edit.key(),edit);
            }
            long total=System.nanoTime()-start,used=bean.getThreadAllocatedBytes(thread)-bytes;check(source,copy);retained=copy;
            var key=copy.keySet().iterator().next();copy.remove(key);if(!source.containsKey(key))throw new AssertionError("Source mutated");
            if(run>=0){clone[run]=directTime/1e6;batchTotal[run]=total/1e6;batchMax[run]=max/1e6;batchSum[run]=sum/1e6;cloneBytes[run]=directBytes;batchBytes[run]=used;}
        }
        Files.writeString(Path.of(args[0]),"{\"scope\":\"262144 whole-cell history entries; no competing threads, server, geometry, save, network, rendering or system qualification. Three warmups, 20 measured runs, clone first. Guarded timings include monitor entry and timer overhead; batch totals include rebuilding index outside monitor. Exact replay order and source independence checked outside timing. Thread allocation is not peak memory.\",\"clone_guarded_ms\":"+stats(clone)+",\"batch_total_ms\":"+stats(batchTotal)+",\"maximum_batch_guarded_ms\":"+stats(batchMax)+",\"sum_batch_guarded_ms\":"+stats(batchSum)+",\"clone_thread_allocated_bytes\":"+stats(cloneBytes)+",\"batch_thread_allocated_bytes\":"+stats(batchBytes)+"}\n");
    }
    static void check(WorldVoxels.History a,WorldVoxels.History b){if(a.size()!=b.size())throw new AssertionError("Count mismatch");var x=a.entrySet().iterator();var y=b.entrySet().iterator();while(x.hasNext())if(!x.next().equals(y.next()))throw new AssertionError("Order mismatch");}
    static String stats(double[] values){Arrays.sort(values);return String.format(Locale.ROOT,"{\"median\":%.6f,\"p95\":%.6f,\"worst\":%.6f}",(values[9]+values[10])/2,values[18],values[19]);}
}
