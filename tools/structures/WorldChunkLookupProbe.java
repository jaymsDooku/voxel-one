import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;

/** Actual WorldVoxels lookup component. Not placement, meshing, GPU or network qualification. */
public final class WorldChunkLookupProbe {
    private static volatile long consumed;
    private static String stats(double[] v){var s=v.clone();Arrays.sort(s);return String.format(Locale.ROOT,"{\"median\":%.6f,\"p95\":%.6f,\"worst\":%.6f,\"samples\":%s}",(s[9]+s[10])/2,s[18],s[19],Arrays.toString(v));}
    public static void main(String[] args)throws Exception {
        if(args.length!=1||Files.exists(Path.of(args[0])))throw new IllegalArgumentException("Fresh result path required");
        var world=new WorldVoxels(new Terrain(42));var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));var image=WorldVoxels.prepareCellImage(tree);
        int[] x=new int[8192],z=new int[8192];for(int i=0;i<8192;i++){x[i]=(i%128-64)*16;z[i]=(i/128-32)*16;world.installCellImage(x[i],128,z[i],image);}
        int[][] xs=new int[2][65536],zs=new int[2][65536];long[] expected=new long[2];var random=new Random(2971);
        for(int i=0;i<65536;i++) {
            if(i%16==0){int k=random.nextInt(8192);for(int j=0;j<16;j++){xs[0][i+j]=x[k];zs[0][i+j]=z[k];expected[0]+=Blocks.STONE;}}
            if(i%4==0){xs[1][i]=16000+i%1024;zs[1][i]=16000;}else{int k=random.nextInt(8192);xs[1][i]=x[k];zs[1][i]=z[k];expected[1]+=Blocks.STONE;}
        }
        for(int mode=0;mode<2;mode++){long sum=0;for(int i=0;i<65536;i++)sum+=world.type(xs[mode][i],128,zs[mode][i]);if(sum!=expected[mode])throw new AssertionError("Lookup fixture");}
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();double[][] times=new double[2][20],bytes=new double[2][20];
        for(int run=-3;run<20;run++)for(int order=0;order<2;order++) {
            int mode=(order+run)&1;long before=bean.getThreadAllocatedBytes(thread),begin=System.nanoTime(),sum=0;
            for(int i=0;i<65536;i++)sum+=world.type(xs[mode][i],128,zs[mode][i]);double ms=(System.nanoTime()-begin)/1e6;long allocated=bean.getThreadAllocatedBytes(thread)-before;
            if(sum!=expected[mode])throw new AssertionError("Lookup result");consumed^=sum;if(run>=0){times[mode][run]=ms;bytes[mode][run]=allocated;}
        }
        // The cached root/bucket must not hide replacement, an owned edit or clear.
        var held=world.snapshot();world.apply(new Protocol.Edit(x[0],128,z[0],Blocks.BRICKS));if(world.type(x[0],128,z[0])!=Blocks.BRICKS||held.type(x[0],128,z[0])!=Blocks.STONE)throw new AssertionError("Root cache");world.clear();if(world.type(x[0],128,z[0])!=0||held.type(x[0],128,z[0])!=Blocks.STONE)throw new AssertionError("Clear cache");
        String result="{\"status\":\"passed\",\"chunks\":8192,\"cells\":8192,\"queries_per_run\":65536,\"warmups\":3,\"runs\":20,\"coherent_repeat\":16,\"mixed_hit_fraction\":0.75,\"coherent_ms\":"+stats(times[0])+",\"mixed_ms\":"+stats(times[1])+",\"coherent_allocated_bytes\":"+stats(bytes[0])+",\"mixed_allocated_bytes\":"+stats(bytes[1])+",\"snapshot_edit_clear\":true,\"qualification\":false}";Files.writeString(Path.of(args[0]),result+"\n");System.out.println(result);
    }
}
