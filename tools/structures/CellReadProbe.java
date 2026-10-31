import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.util.*;
/** World-cell decode component diagnostic only; not full synchronization qualification. */
public final class CellReadProbe {
    static volatile long consumed;
    public static void main(String[] args)throws Exception {
        var tree=new SparseVoxelOctree(16);
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)if(((x+y+z)&1)==0)tree.set(x,y,z,WorldVoxels.encode(Blocks.BRICKS));
        var buffer=new ByteArrayOutputStream();new Protocol.CellState(20,180,20,tree).write(new DataOutputStream(buffer));byte[] input=buffer.toByteArray();
        var check=Protocol.CellState.read(new DataInputStream(new ByteArrayInputStream(input)));
        var output=new ByteArrayOutputStream();check.write(new DataOutputStream(output));if(!Arrays.equals(input,output.toByteArray())||check.tree().leafCount()!=2048)throw new AssertionError("Wire changed");
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");bean.setThreadAllocatedMemoryEnabled(true);
        double[] times=new double[20];long[] bytes=new long[20];
        for(int run=-3;run<20;run++) {
            long before=bean.getCurrentThreadAllocatedBytes(),start=System.nanoTime(),sum=0;
            for(int repeat=0;repeat<64;repeat++) {
                var cell=Protocol.CellState.read(new DataInputStream(new ByteArrayInputStream(input)));
                if(cell.x()!=20||cell.y()!=180||cell.z()!=20||cell.tree().get(0,0,0)!=WorldVoxels.encode(Blocks.BRICKS)||cell.tree().get(1,0,0)!=0)throw new AssertionError("Decoded value changed");sum+=cell.tree().occupied();
            }
            double ms=(System.nanoTime()-start)/1e6;long allocated=bean.getCurrentThreadAllocatedBytes()-before;if(sum!=64L*2048)throw new AssertionError();consumed=sum;
            if(run>=0){times[run]=ms;bytes[run]=allocated;}
        }
        Arrays.sort(times);Arrays.sort(bytes);
        System.out.printf(Locale.ROOT,"{\"implementation\":\"%s\",\"runs\":20,\"warmups\":3,\"batch\":64,\"leaves_per_cell\":2048,\"wire_bytes_per_cell\":%d,\"median_ms\":%.3f,\"p95_ms\":%.3f,\"worst_ms\":%.3f,\"median_thread_allocated_bytes\":%d}%n",args[0],input.length,(times[9]+times[10])/2,times[18],times[19],(bytes[9]+bytes[10])/2);
    }
}
