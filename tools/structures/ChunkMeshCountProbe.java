import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.lang.management.ManagementFactory;
import java.nio.*;
import java.security.MessageDigest;
import java.util.*;

/** Count-only chunk-budget diagnostic. Not city, renderer or system qualification. */
public final class ChunkMeshCountProbe {
    static volatile int consumed;
    static Chunk fixture(int kind) {
        var chunk=new Chunk();
        if(kind==0) {
            for(int x=0;x<16;x++)for(int z=0;z<16;z++)chunk.setBlock(x,0,z,(x+z)%3==0?Blocks.WATER:Blocks.STONE);
            chunk.setBlock(1,1,1,Blocks.GLASS);chunk.setBlock(2,1,1,1024);
        } else {
            var tree=new SparseVoxelOctree(16);
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
                if(((x+y+z)&1)==0)tree.fill(x,y,z,x+1,y+1,z+1,kind==1?WorldVoxels.encode(Blocks.STONE):0xfe000000|((x*37+y*91+z*101)&0xffffff));
            for(int i=0;i<8;i++)chunk.replaceCell((i%4)*2,0,(i/4)*2,tree);
        }
        return chunk;
    }
    static String hash(MeshData mesh)throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");var bytes=ByteBuffer.allocate(4);
        for(float[] values:new float[][]{mesh.vertices(),mesh.surface()})for(float v:values) {
            bytes.clear();bytes.putInt(Float.floatToRawIntBits(v));digest.update(bytes.array());
        }
        for(int v:mesh.indices()){bytes.clear();bytes.putInt(v);digest.update(bytes.array());}
        return HexFormat.of().formatHex(digest.digest());
    }
    public static void main(String[] args)throws Exception {
        boolean count=args.length==1&&args[0].equals("count");
        if(!count&&(args.length!=1||!args[0].equals("render")))throw new IllegalArgumentException("count or render required");
        var method=count?MeshDataGenerator.class.getMethod("count",Chunk.class):null;
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);
        for(int kind=0;kind<3;kind++) {
            var chunk=fixture(kind);var mesh=MeshDataGenerator.generate(chunk);
            int quads=mesh.indices().length/6;String hash=hash(mesh);mesh=null;
            double[] times=new double[20];long[] allocations=new long[20];
            for(int run=-3;run<20;run++) {
                long before=bean.getCurrentThreadAllocatedBytes(),start=System.nanoTime();
                consumed=count?(int)method.invoke(null,chunk):MeshDataGenerator.generate(chunk).indices().length/6;
                double ms=(System.nanoTime()-start)/1e6;long bytes=bean.getCurrentThreadAllocatedBytes()-before;
                if(consumed!=quads)throw new AssertionError("Surface count changed");
                if(run>=0){times[run]=ms;allocations[run]=bytes;}
            }
            if(!hash.equals(hash(MeshDataGenerator.generate(chunk))))throw new AssertionError("Render bytes changed");
            Arrays.sort(times);Arrays.sort(allocations);
            System.out.printf(Locale.ROOT,"{\"operation\":\"%s\",\"fixture\":%d,\"quads\":%d,\"render_sha256\":\"%s\",\"runs\":20,\"warmups\":3,\"median_ms\":%.3f,\"p95_ms\":%.3f,\"worst_ms\":%.3f,\"median_thread_allocated_bytes\":%d}%n",args[0],kind,quads,hash,(times[9]+times[10])/2,times[18],times[19],(allocations[9]+allocations[10])/2);
        }
    }
}
