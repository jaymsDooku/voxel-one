import dev.jayms.net.model.SparseVoxelOctree;
import java.lang.management.ManagementFactory;
import java.util.*;
/** Stored-leaf traversal diagnostic only; not full structure validation qualification. */
public final class LeafVisitorProbe {
    static volatile long consumed;
    static void accept(long[] state,int x,int y,int z,int side,int color) {
        state[0]++;state[1]=state[1]*31+x;state[1]=state[1]*31+y;state[1]=state[1]*31+z;
        state[1]=state[1]*31+side;state[1]=state[1]*31+color;
    }
    public static void main(String[] args)throws Exception {
        var tree=new SparseVoxelOctree(16);
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
            if(((x+y+z)&1)==0)tree.set(x,y,z,0xff123456);
        tree.freeze();long[] state={0,0};
        SparseVoxelOctree.LeafVisitor visitor=(x,y,z,side,color)->accept(state,x,y,z,side,color);
        for(var leaf:tree.leaves())accept(state,leaf.x(),leaf.y(),leaf.z(),leaf.side(),leaf.color());
        long expected=state[1];state[0]=state[1]=0;tree.forEachLeaf(visitor);
        if(state[0]!=2048||state[1]!=expected)throw new AssertionError("Leaf sequence changed");
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);double[][] times=new double[2][20];long[][] bytes=new long[2][20];
        for(int trial=-3;trial<20;trial++)for(int mode=0;mode<2;mode++) {
            long before=bean.getCurrentThreadAllocatedBytes(),start=System.nanoTime(),sum=0;
            for(int repeat=0;repeat<128;repeat++) {
                state[0]=state[1]=0;
                if(mode==0)for(var leaf:tree.leaves())accept(state,leaf.x(),leaf.y(),leaf.z(),leaf.side(),leaf.color());
                else tree.forEachLeaf(visitor);
                if(state[0]!=2048||state[1]!=expected)throw new AssertionError("Leaf sequence changed");sum+=state[1];
            }
            double elapsed=(System.nanoTime()-start)/1e6;long allocated=bean.getCurrentThreadAllocatedBytes()-before;consumed=sum;
            if(trial>=0){times[mode][trial]=elapsed;bytes[mode][trial]=allocated;}
        }
        for(int mode=0;mode<2;mode++) {
            Arrays.sort(times[mode]);Arrays.sort(bytes[mode]);var t=times[mode];var b=bytes[mode];
            System.out.printf(Locale.ROOT,"{\"operation\":\"%s\",\"runs\":20,\"warmups\":3,\"batch\":128,\"leaves_per_tree\":2048,\"median_ms\":%.3f,\"p95_ms\":%.3f,\"worst_ms\":%.3f,\"median_thread_allocated_bytes\":%d}%n",mode==0?"leaf-list-traversal":"leaf-visitor",(t[9]+t[10])/2,t[18],t[19],(b[9]+b[10])/2);
        }
    }
}
