import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** History preparation only; synthetic owner frames, not actual FPS or full placement. */
public final class OfflineHistoryStagingProbe {
    static volatile Object consumed;
    static String hash(WorldVoxels.History history)throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");
        try(var out=new DataOutputStream(new BufferedOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),digest),65536))) {
            for(var e:history.values())e.write(out);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    static WorldVoxels.History synchronous(WorldVoxels.History base,Map<StructurePackage.Position,SparseVoxelOctree> cells) {
        var next=WorldVoxels.History.copyOf(base);
        for(var entry:cells.entrySet()){var p=entry.getKey();WorldVoxels.rememberCell(next,new Protocol.CellState(p.x(),p.y(),p.z(),entry.getValue()));}
        return next;
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=1||!List.of("sync","staged").contains(args[0]))throw new IllegalArgumentException("sync|staged required");
        var path=Path.of("target/structure-offline-history-probe-unused.dat");if(Files.exists(path))throw new IllegalStateException("Synthetic probe path must be unused");
        var local=new LocalGame(path,42);var base=new WorldVoxels.History();local.edits=base;
        for(int i=0;i<100000;i++){var edit=new Protocol.Edit(i%512,180,1000+i/512,Blocks.BRICKS);base.put(edit.key(),edit);}
        var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));tree.freeze();
        var mutable=new LinkedHashMap<StructurePackage.Position,SparseVoxelOctree>();
        for(int i=0;i<1000;i++)mutable.put(new StructurePackage.Position(5000+i,180,5000),tree);
        var cells=Collections.unmodifiableMap(mutable);String expected=hash(synchronous(base,cells));
        var stagedField=LocalGame.StructureHistory.class.getDeclaredField("next");stagedField.setAccessible(true);
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);
        if(!bean.isCurrentThreadCpuTimeSupported())throw new IllegalStateException("CPU counter unavailable");
        if(!bean.isThreadCpuTimeEnabled())bean.setThreadCpuTimeEnabled(true);
        double[] wall=new double[20],ownerMax=new double[20],workerCpu=new double[20];long[] ownerBytes=new long[20],workerBytes=new long[20];int[] reads=new int[20];
        for(int run=-3;run<20;run++) {
            WorldVoxels.History history;double max=0,cpu=0;long allocated=0;int requests=0;
            long before=bean.getCurrentThreadAllocatedBytes(),start=System.nanoTime();
            if(args[0].equals("sync")) {
                history=synchronous(base,cells);max=(System.nanoTime()-start)/1e6;
            } else {
                try(var queue=new StructureOwnerQueue()) {
                    var bytes=new AtomicLong();var cpuNanos=new AtomicLong();
                    var task=new FutureTask<>(()->{
                        long b=bean.getCurrentThreadAllocatedBytes(),c=bean.getCurrentThreadCpuTime();
                        var result=local.stageStructureHistory(cells,queue,()->false,p->{});
                        bytes.set(bean.getCurrentThreadAllocatedBytes()-b);cpuNanos.set(bean.getCurrentThreadCpuTime()-c);return result;
                    });
                    var thread=new Thread(task,"synthetic-history-staging-probe");thread.start();
                    try {
                        long deadline=System.nanoTime()+10_000_000_000L;
                        while(!task.isDone()&&System.nanoTime()<deadline) {
                            long readStart=System.nanoTime();boolean read=queue.pump();double ms=(System.nanoTime()-readStart)/1e6;
                            if(read){max=Math.max(max,ms);requests++;}
                            if(!task.isDone())Thread.sleep(33); // Synthetic owner cadence; no renderer.
                        }
                        if(!task.isDone())throw new AssertionError("Owner-read deadline");
                        history=(WorldVoxels.History)stagedField.get(task.get());allocated=bytes.get();cpu=cpuNanos.get()/1e6;
                    } finally {task.cancel(true);thread.join(2000);if(thread.isAlive())throw new AssertionError("Worker not released");}
                }
            }
            double elapsed=(System.nanoTime()-start)/1e6;long mainBytes=bean.getCurrentThreadAllocatedBytes()-before;
            if(history.size()!=101000||!expected.equals(hash(history))||base.size()!=100000||local.edits!=base)throw new AssertionError("History changed");
            consumed=history;
            if(run>=0){wall[run]=elapsed;ownerMax[run]=max;ownerBytes[run]=mainBytes;workerBytes[run]=allocated;workerCpu[run]=cpu;reads[run]=requests;}
        }
        Arrays.sort(wall);Arrays.sort(ownerMax);Arrays.sort(ownerBytes);Arrays.sort(workerBytes);Arrays.sort(workerCpu);Arrays.sort(reads);
        System.out.printf(Locale.ROOT,"{\"mode\":\"%s\",\"source_edits\":100000,\"placement_cells\":1000,\"result_edits\":101000,\"replay_sha256\":\"%s\",\"warmups\":3,\"runs\":20,\"synthetic_frame_sleep_ms\":%d,\"wall_median_ms\":%.3f,\"wall_p95_ms\":%.3f,\"wall_worst_ms\":%.3f,\"owner_max_per_run_median_ms\":%.3f,\"owner_max_per_run_p95_ms\":%.3f,\"owner_max_per_run_worst_ms\":%.3f,\"median_owner_thread_allocated_bytes\":%d,\"median_worker_thread_allocated_bytes\":%d,\"worker_cpu_p95_ms\":%.3f,\"owner_requests_median\":%d}%n",args[0],expected,args[0].equals("staged")?33:0,(wall[9]+wall[10])/2,wall[18],wall[19],(ownerMax[9]+ownerMax[10])/2,ownerMax[18],ownerMax[19],(ownerBytes[9]+ownerBytes[10])/2,(workerBytes[9]+workerBytes[10])/2,workerCpu[18],reads[10]);
    }
}
