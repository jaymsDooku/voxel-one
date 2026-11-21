import dev.jayms.net.model.SparseVoxelOctree;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.util.*;

/** Rotation component diagnostic only; no city/maximum or GPU qualification. */
public final class RotationTreeProbe {
    static volatile SparseVoxelOctree consumed;
    static SparseVoxelOctree reference(SparseVoxelOctree source) {
        int size=source.size();
        if(source.uniform(0,0,0,size)!=-1)return source.copy().freeze();
        var result=new SparseVoxelOctree(size);
        for(var leaf:source.leaves()) {
            int x=size-leaf.z()-leaf.side(),z=leaf.x(),side=leaf.side();
            result.fill(x,leaf.y(),z,x+side,leaf.y()+side,z+side,leaf.color());
        }
        return result.freeze();
    }
    static byte[] bytes(SparseVoxelOctree tree)throws IOException {
        var buffer=new ByteArrayOutputStream();tree.write(new DataOutputStream(buffer));return buffer.toByteArray();
    }
    public static void main(String[] args)throws Exception {
        var tree=new SparseVoxelOctree(16);
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
            if(((x+y+z)&1)==0)tree.set(x,y,z,0xff000000|((x*17)<<16)|((y*17)<<8)|z*17);
        tree.freeze();
        if(!Arrays.equals(bytes(reference(tree)),bytes(tree.rotatedY(1))))throw new AssertionError("Rotation bytes differ");
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);
        double[][] times=new double[2][20];long[][] allocations=new long[2][20];
        for(int run=-3;run<20;run++)for(int visit=0;visit<2;visit++) {
            int mode=(visit+(run&1))&1;long before=bean.getCurrentThreadAllocatedBytes(),start=System.nanoTime();
            SparseVoxelOctree last=null;
            for(int repeat=0;repeat<64;repeat++)last=mode==0?reference(tree):tree.rotatedY(1);
            consumed=last;
            double ms=(System.nanoTime()-start)/1e6;long allocated=bean.getCurrentThreadAllocatedBytes()-before;
            if(last==null||last.occupied()!=2048||last.get(15,0,0)!=tree.get(0,0,0))throw new AssertionError("Rotation value changed");
            if(run>=0){times[mode][run]=ms;allocations[mode][run]=allocated;}
        }
        for(int mode=0;mode<2;mode++) {
            Arrays.sort(times[mode]);Arrays.sort(allocations[mode]);
            System.out.printf(Locale.ROOT,"{\"implementation\":\"%s\",\"runs\":20,\"warmups\":3,\"batch\":64,\"leaves\":2048,\"median_ms\":%.3f,\"p95_ms\":%.3f,\"worst_ms\":%.3f,\"median_thread_allocated_bytes\":%d}%n",
                    mode==0?"leaf_fill_reference":"octant_rotation",(times[mode][9]+times[mode][10])/2,times[mode][18],times[mode][19],(allocations[mode][9]+allocations[mode][10])/2);
        }
    }
}
