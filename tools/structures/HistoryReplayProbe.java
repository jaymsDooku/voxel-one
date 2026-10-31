import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.lang.management.ManagementFactory;
import java.security.MessageDigest;
import java.io.*;
import java.util.*;

/** Canonical replay construction only; not full placement/save or city qualification. */
public final class HistoryReplayProbe {
    static volatile Object consumed;
    static List<Protocol.CellState> fixture() {
        var cells=new ArrayList<Protocol.CellState>();
        for(int i=0;i<64;i++) {
            var tree=new SparseVoxelOctree(16);
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
                if(((x+y+z)&1)==0)tree.fill(x,y,z,x+1,y+1,z+1,0xfe000000|((i*171+x*37+y*91+z*101)&0xffffff));
            cells.add(new Protocol.CellState(i-32,128,-2,tree.freeze()));
        }
        return cells;
    }
    static String hash(WorldVoxels.History history)throws Exception {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        for(var edit:history.values())edit.write(out);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
    }
    public static void main(String[] args)throws Exception {
        var cells=fixture();var reference=new WorldVoxels.History();
        for(var cell:cells)WorldVoxels.rememberCell(reference,cell);
        String expected=hash(reference);
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);double[] times=new double[20];long[] allocations=new long[20];
        for(int run=-3;run<20;run++) {
            var history=new WorldVoxels.History();
            long before=bean.getCurrentThreadAllocatedBytes(),start=System.nanoTime();
            for(var cell:cells)WorldVoxels.rememberCell(history,cell);
            double ms=(System.nanoTime()-start)/1e6;long bytes=bean.getCurrentThreadAllocatedBytes()-before;
            consumed=history;
            if(history.size()!=131136||!expected.equals(hash(history)))throw new AssertionError("Replay changed");
            for(var cell:cells)if(cell.tree().leafCount()!=2048)throw new AssertionError("Source changed");
            if(run>=0){times[run]=ms;allocations[run]=bytes;}
        }
        Arrays.sort(times);Arrays.sort(allocations);
        System.out.printf(Locale.ROOT,"{\"implementation\":\"%s\",\"operation\":\"canonical_history\",\"cells\":64,\"leaves\":131072,\"edits\":131136,\"replay_sha256\":\"%s\",\"warmups\":3,\"runs\":20,\"median_ms\":%.3f,\"p95_ms\":%.3f,\"worst_ms\":%.3f,\"median_thread_allocated_bytes\":%d}%n",args[0],expected,(times[9]+times[10])/2,times[18],times[19],(allocations[9]+allocations[10])/2);
    }
}
