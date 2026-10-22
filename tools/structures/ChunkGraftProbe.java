import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.util.*;
import java.lang.management.ManagementFactory;

/** Detached chunk assembly only; not full placement, city, GPU or network qualification. */
public final class ChunkGraftProbe {
    static volatile Chunk retained;
    public static void main(String[] args) {
        if(args.length!=1||!List.of("replay","graft").contains(args[0]))throw new IllegalArgumentException("replay or graft required");
        boolean graft=args[0].equals("graft");var tree=new SparseVoxelOctree(16);
        for(int y=0;y<16;y+=2)for(int z=0;z<16;z+=2)for(int x=0;x<16;x+=2)tree.fill(x,y,z,x+1,y+1,z+1,0xfe123456);
        tree.freeze();var sourceLeaves=tree.leaves();var states=new ArrayList<Protocol.CellState>();
        for(int x=0;x<16;x++)states.add(new Protocol.CellState(x,0,0,tree));
        var reference=new Chunk();reference.setBlock(15,15,15,Blocks.BRICKS);
        for(var state:states)for(var edit:state.edits())reference.apply(edit);
        var expected=reference.snapshot().leaves();var expectedMesh=MeshDataGenerator.generate(reference);
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();double[] times=new double[20];long[] allocations=new long[20];
        for(int run=-3;run<20;run++) {
            var chunk=new Chunk();chunk.setBlock(15,15,15,Blocks.BRICKS);
            long bytes=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
            for(var state:states) {
                if(graft)chunk.replaceCell(state.x(),state.y(),state.z(),state.tree());
                else for(var edit:state.edits())chunk.apply(edit);
            }
            double elapsed=(System.nanoTime()-start)/1e6;bytes=bean.getThreadAllocatedBytes(thread)-bytes;
            if(run>=0){times[run]=elapsed;allocations[run]=bytes;}
            if(!expected.equals(chunk.snapshot().leaves())||!chunk.hasFractional()||!chunk.models().isEmpty()||!sourceLeaves.equals(tree.leaves()))throw new AssertionError("Geometry/source changed");
            if(run==0) {
                var mesh=MeshDataGenerator.generate(chunk);
                if(!Arrays.equals(expectedMesh.vertices(),mesh.vertices())||!Arrays.equals(expectedMesh.indices(),mesh.indices())||!Arrays.equals(expectedMesh.surface(),mesh.surface()))throw new AssertionError("Mesh changed");
            }
            retained=chunk;
        }
        Arrays.sort(times);Arrays.sort(allocations);
        System.out.printf(Locale.ROOT,"{\"operation\":\"%s\",\"cells\":16,\"leaves_per_cell\":512,\"warmups\":3,\"runs\":20,\"median_ms\":%.6f,\"p95_ms\":%.6f,\"worst_ms\":%.6f,\"median_thread_allocated_bytes\":%d}%n",args[0],(times[9]+times[10])/2,times[18],times[19],(allocations[9]+allocations[10])/2);
    }
}
