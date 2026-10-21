import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import java.lang.management.ManagementFactory;
import java.util.*;

/** Geometry-only staging diagnostic. Excludes history, save, network and rendering. */
public final class WholeCellProbe {
    static volatile WorldVoxels retained;
    public static void main(String[] args) {
        var tree=new SparseVoxelOctree(16);
        for(int y=0;y<16;y+=2)for(int z=0;z<16;z+=2)for(int x=0;x<16;x+=2)
            tree.set(x,y,z,WorldVoxels.encode(Blocks.STONE));
        tree.freeze();
        var states=new ArrayList<Protocol.CellState>();
        for(int x=-32;x<32;x++)states.add(new Protocol.CellState(x,128,-1,tree));
        var edits=states.stream().flatMap(s->s.edits().stream()).toList();
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        for(boolean direct:new boolean[]{false,true}) {
            double[] times=new double[20];long[] allocations=new long[20];
            for(int run=-3;run<20;run++) {
                var world=new WorldVoxels(new Terrain(42));
                world.apply(new Protocol.Edit(-33,128,-1,Blocks.BRICKS));var before=world.snapshot();
                long bytes=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
                if(direct)for(var state:states)world.replaceCell(state.x(),state.y(),state.z(),state.tree());
                else for(var edit:edits)world.apply(edit);
                double elapsed=(System.nanoTime()-start)/1e6;bytes=bean.getThreadAllocatedBytes(thread)-bytes;
                if(run>=0){times[run]=elapsed;allocations[run]=bytes;}
                for(var state:states) {
                    if(before.type(state.x(),128,-1)!=0)throw new AssertionError("Snapshot changed");
                    var actual=world.cell(state.x(),128,-1);
                    for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
                        if(tree.get(x,y,z)!=actual.get(x,y,z))throw new AssertionError("Cell differs");
                }
                if(world.type(-33,128,-1)!=Blocks.BRICKS)throw new AssertionError("Unrelated edit lost");
                retained=world;
            }
            Arrays.sort(times);Arrays.sort(allocations);
            System.out.printf(Locale.ROOT,"{\"operation\":\"%s\",\"cells\":64,\"leaves_per_cell\":512,\"warmups\":3,\"runs\":20,\"median_ms\":%.6f,\"p95_ms\":%.6f,\"worst_ms\":%.6f,\"median_thread_allocated_bytes\":%d}%n",direct?"replace":"replay",(times[9]+times[10])/2,times[18],times[19],(allocations[9]+allocations[10])/2);
        }
    }
}
