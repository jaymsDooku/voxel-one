import dev.jayms.net.model.*;
import java.lang.management.ManagementFactory;
import java.security.MessageDigest;
import java.util.*;
/** Greedy face-count/render compatibility and component allocation diagnostic only. */
public final class ModelFaceCountProbe {
    private static void integer(MessageDigest hash,int value){for(int shift=24;shift>=0;shift-=8)hash.update((byte)(value>>>shift));}
    private static SparseVoxelOctree tree(int size,int mode,Random random) {
        var tree=new SparseVoxelOctree(size);
        if(mode==1)tree.fill(0,0,0,size,size,size,0xff123456);
        if(mode==2)for(int y=0;y<size;y++)for(int z=0;z<size;z++)for(int x=0;x<size;x++)tree.set(x,y,z,0xff000000|random.nextInt(1<<24));
        if(mode==3)for(int i=0;i<size*2;i++)tree.set(random.nextInt(size),random.nextInt(size),random.nextInt(size),0xff000000|random.nextInt(1<<24));
        return tree.freeze();
    }
    public static void main(String[] args)throws Exception {
        var random=new Random(91823);var digest=MessageDigest.getInstance("SHA-256");
        for(int size:new int[]{8,16,32})for(int mode=0;mode<4;mode++) {
            var geometry=ModelMesher.mesh(tree(size,mode,random));integer(digest,geometry.quads());
            for(float f:geometry.vertices())integer(digest,Float.floatToRawIntBits(f));for(int i:geometry.indices())integer(digest,i);
        }
        System.out.println("{\"implementation\":\""+args[0]+"\",\"render_array_sha256\":\""+HexFormat.of().formatHex(digest.digest())+"\",\"fixtures\":12}");
        if(args[0].equals("parent"))return;
        var fixture=tree(16,2,random);int expected=ModelMesher.mesh(fixture).quads();
        var memory=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!memory.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counters unavailable");memory.setThreadAllocatedMemoryEnabled(true);
        double[][] times=new double[2][20];long[][] allocated=new long[2][20];
        for(int trial=-3;trial<20;trial++)for(int mode=0;mode<2;mode++) {
            long before=memory.getCurrentThreadAllocatedBytes(),start=System.nanoTime();int sum=0;
            for(int repeat=0;repeat<64;repeat++)sum+=mode==0?ModelMesher.mesh(fixture).quads():ModelMesher.count(fixture);
            double ms=(System.nanoTime()-start)/1e6;long bytes=memory.getCurrentThreadAllocatedBytes()-before;
            if(sum!=expected*64)throw new AssertionError("Quad count changed");if(trial>=0){times[mode][trial]=ms;allocated[mode][trial]=bytes;}
        }
        for(int mode=0;mode<2;mode++) {
            Arrays.sort(times[mode]);Arrays.sort(allocated[mode]);var t=times[mode];var a=allocated[mode];
            System.out.printf(Locale.ROOT,"{\"operation\":\"%s\",\"batch\":64,\"resolution\":16,\"quads_per_model\":%d,\"runs\":20,\"median_ms\":%.3f,\"p95_ms\":%.3f,\"worst_ms\":%.3f,\"median_thread_allocated_bytes\":%d}%n",mode==0?"full-render-output":"count-only",expected,(t[9]+t[10])/2,t[18],t[19],(a[9]+a[10])/2);
        }
    }
}
