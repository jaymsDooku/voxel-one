import dev.jayms.net.Blocks;
import dev.jayms.net.WorldVoxels;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.util.*;
import static dev.jayms.net.structure.StructurePackage.*;

/** Bounded CPU integration probe. Does not qualify city, GPU or network budgets. */
public final class StagingSharingProbe {
    public static void main(String[] args) throws Exception {
        var tree=new SparseVoxelOctree(16);
        tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        var cells=new ArrayList<Cell>();
        for(int z=0;z<128;z++)for(int x=0;x<128;x++)cells.add(new Cell(new Position(x,0,z),tree,-1));
        var asset=new StructurePackage("Uniform staging probe",new Bounds(128,1,128,0,0,0),List.of(),cells);
        var empty=new SparseVoxelOctree(16).freeze();var models=new ModelLibrary();
        Source source=new Source(){public SparseVoxelOctree cell(int x,int y,int z){return empty;}public ModelLibrary models(){return models;}};
        var engine=new StructureEngine();long start=System.nanoTime();
        try(var plan=engine.prepare(asset,new Position(0,128,0),1,source,()->false,p->{})) {
            Set<SparseVoxelOctree> unique=Collections.newSetFromMap(new IdentityHashMap<>());unique.addAll(plan.cells().values());
            int expected=args.length==0?1:Integer.parseInt(args[0]);
            if(plan.cells().size()!=16384||unique.size()!=expected)throw new AssertionError("Unexpected staged geometry identity count");
            System.out.printf(Locale.ROOT,"{\"cells\":16384,\"unique_staged_trees\":%d,\"prepare_ms\":%.3f,\"qualification\":\"bounded CPU integration only\"}%n",unique.size(),(System.nanoTime()-start)/1e6);
        }
    }
}
