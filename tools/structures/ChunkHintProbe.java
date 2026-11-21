import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.util.*;
import static dev.jayms.net.structure.StructurePackage.*;

/** Bounded CPU staging diagnostic. Does not qualify city/max, rendering or networking. */
public final class ChunkHintProbe {
    public static void main(String[] args)throws Exception {
        var stone=new SparseVoxelOctree(16);stone.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        var cells=new ArrayList<Cell>();for(int z=0;z<128;z++)for(int x=0;x<128;x++)cells.add(new Cell(new Position(x,0,z),stone,-1));
        var asset=new StructurePackage("Chunk hint probe",new Bounds(128,1,128,0,0,0),List.of(),cells);
        var empty=new SparseVoxelOctree(16).freeze();var library=new ModelLibrary();int[] reads={0};
        Source source=new Source(){public boolean emptyCell(int x,int y,int z){return true;}public SparseVoxelOctree cell(int x,int y,int z){reads[0]++;return empty;}public ModelLibrary models(){return library;}};
        var engine=new StructureEngine();double[] timings=new double[20];int expected=Integer.parseInt(args[0]);
        for(int i=-3;i<20;i++) {
            reads[0]=0;long start=System.nanoTime();
            try(var plan=engine.prepare(asset,new Position(-128,180,-128),0,source,()->false,p->{})) {
                if(reads[0]!=expected||plan.cells().size()!=16384)throw new AssertionError("Staging count/read mismatch: "+reads[0]);
                for(var tree:plan.cells().values())if(tree.occupied()!=4096)throw new AssertionError("Geometry changed");
            }
            if(i>=0)timings[i]=(System.nanoTime()-start)/1e6;
        }
        Arrays.sort(timings);
        System.out.printf(Locale.ROOT,"{\"cells\":16384,\"chunks\":64,\"snapshot_cell_reads\":%d,\"warmups\":3,\"runs\":20,\"median_ms\":%.3f,\"p95_ms\":%.3f,\"worst_ms\":%.3f,\"qualification\":\"bounded CPU staging only; not city/max or GPU/network qualification\"}%n",reads[0],(timings[9]+timings[10])/2,timings[18],timings[19]);
    }
}
