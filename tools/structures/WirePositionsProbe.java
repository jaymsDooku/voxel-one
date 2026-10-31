import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.lang.management.ManagementFactory;
import java.security.MessageDigest;
import java.util.*;

/** Decode CPU/allocation diagnostic, not full-fixture or network qualification. */
public final class WirePositionsProbe {
    static volatile Object consumed;
    public static void main(String[] args)throws Exception {
        var cells=new ArrayList<Protocol.CellState>();var tree=new SparseVoxelOctree(16);
        tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));tree.freeze();
        for(int i=0;i<100_000;i++)cells.add(new Protocol.CellState(i-50_000,128,-2,tree));
        Collections.shuffle(cells,new Random(371));
        byte[] wire=StructureWire.encode(new StructureWire.Delta(List.of(),cells));
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(wire));
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);
        double[] times=new double[20];long[] allocations=new long[20];
        for(int run=-3;run<20;run++) {
            long before=bean.getCurrentThreadAllocatedBytes(),start=System.nanoTime();
            var decoded=StructureWire.decode(wire);
            double ms=(System.nanoTime()-start)/1e6;long bytes=bean.getCurrentThreadAllocatedBytes()-before;
            consumed=decoded;
            if(decoded.cells().size()!=cells.size())throw new AssertionError("Cell count changed");
            for(int i=0;i<cells.size();i++) {
                var a=cells.get(i);var b=decoded.cells().get(i);
                if(a.x()!=b.x()||a.y()!=b.y()||a.z()!=b.z()||!a.tree().sameValues(b.tree()))throw new AssertionError("Cell/order changed");
            }
            if(run>=0){times[run]=ms;allocations[run]=bytes;}
        }
        if(!Arrays.equals(wire,StructureWire.encode((StructureWire.Delta)consumed)))throw new AssertionError("Wire roundtrip changed");
        Arrays.sort(times);Arrays.sort(allocations);
        System.out.printf(Locale.ROOT,"{\"implementation\":\"%s\",\"operation\":\"decode\",\"cells\":100000,\"compressed_bytes\":%d,\"wire_sha256\":\"%s\",\"warmups\":3,\"runs\":20,\"median_ms\":%.3f,\"p95_ms\":%.3f,\"worst_ms\":%.3f,\"median_thread_allocated_bytes\":%d}%n",args[0],wire.length,hash,(times[9]+times[10])/2,times[18],times[19],(allocations[9]+allocations[10])/2);
    }
}
