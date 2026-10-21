import dev.jayms.net.*;
import java.lang.management.ManagementFactory;
import java.util.*;

/** Synthetic snapshot/edit diagnostic; not city, GPU or network qualification. */
public final class WorldSnapshotProbe {
    static volatile WorldVoxels retained;
    static volatile long consumed;
    static long heap()throws Exception{System.gc();Thread.sleep(100);System.gc();Thread.sleep(100);var r=Runtime.getRuntime();return r.totalMemory()-r.freeMemory();}
    static void print(String layout,String operation,long[] allocations,double[] times) {
        Arrays.sort(allocations);Arrays.sort(times);
        System.out.printf(Locale.ROOT,"{\"layout\":\"%s\",\"operation\":\"%s\",\"cells\":100000,\"warmups\":3,\"runs\":20,\"median_thread_allocated_bytes\":%d,\"median_ms\":%.6f,\"p95_ms\":%.6f,\"worst_ms\":%.6f}%n",layout,operation,(allocations[9]+allocations[10])/2,(times[9]+times[10])/2,times[18],times[19]);
    }
    public static void main(String[] args)throws Exception {
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        if(args.length!=1||!List.of("compact","scattered").contains(args[0]))throw new IllegalArgumentException("compact or scattered required");
        for(String layout:List.of(args[0])) {
            retained=null;long baseline=heap();var world=new WorldVoxels(new Terrain(42));int step=layout.equals("compact")?1:16;
            for(int z=0;z<200;z++)for(int x=0;x<500;x++)world.apply(new Protocol.Edit(x*step,128,z*step,Blocks.STONE));
            retained=world;long initial=heap()-baseline;
            System.out.printf("{\"layout\":\"%s\",\"cells\":100000,\"source_retained_heap_delta_bytes\":%d,\"scope\":\"single GC-sensitive sample, not peak memory\"}%n",layout,initial);
            long[][] allocations=new long[3][20];double[][] times=new double[3][20];
            for(int i=-3;i<20;i++) {
                long before=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();var snapshot=world.snapshot();
                double elapsed=(System.nanoTime()-start)/1e6;long bytes=bean.getThreadAllocatedBytes(thread)-before;
                if(i>=0){times[0][i]=elapsed;allocations[0][i]=bytes;}
                int old=snapshot.type(250*step,128,100*step);
                for(int edit=0;edit<2;edit++) {
                    before=bean.getThreadAllocatedBytes(thread);start=System.nanoTime();
                    world.apply(new Protocol.Edit(250*step,128,100*step,edit==0?Blocks.BRICKS:Blocks.STONE));
                    elapsed=(System.nanoTime()-start)/1e6;bytes=bean.getThreadAllocatedBytes(thread)-before;
                    if(i>=0){times[edit+1][i]=elapsed;allocations[edit+1][i]=bytes;}
                    if(snapshot.type(250*step,128,100*step)!=old)throw new AssertionError("Snapshot changed after edit");
                }
                retained=snapshot;consumed+=snapshot.type(499*step,128,199*step);
            }
            print(layout,"snapshot",allocations[0],times[0]);print(layout,"first_edit_after_snapshot",allocations[1],times[1]);print(layout,"owned_bucket_edit",allocations[2],times[2]);
        }
        if(consumed==0)throw new AssertionError("Unused snapshot");
    }
}
