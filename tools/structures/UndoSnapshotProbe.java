import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.util.*;
import static dev.jayms.net.structure.StructurePackage.*;

/** Single GC-sensitive retained staging sample, not city or process peak-memory qualification. */
public final class UndoSnapshotProbe {
    static volatile StructureEngine.Prepared retained;
    static long used()throws Exception {System.gc();Thread.sleep(100);System.gc();Thread.sleep(100);var r=Runtime.getRuntime();return r.totalMemory()-r.freeMemory();}
    static void warm(StructureEngine engine,StructurePackage asset,Source source)throws Exception {
        try(var plan=engine.prepare(asset,new Position(-128,180,-128),0,source,()->false,p->{})){}
    }
    public static void main(String[] args)throws Exception {
        var stone=new SparseVoxelOctree(16);stone.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        var cells=new ArrayList<Cell>();for(int z=0;z<128;z++)for(int x=0;x<128;x++)cells.add(new Cell(new Position(x,0,z),stone,-1));
        var asset=new StructurePackage("Undo snapshot probe",new Bounds(128,1,128,0,0,0),List.of(),cells);cells=null;
        var empty=new SparseVoxelOctree(16).freeze();var library=new ModelLibrary();
        Source source=new Source(){public SparseVoxelOctree cell(int x,int y,int z){return empty;}public ModelLibrary models(){return library;}};
        var engine=new StructureEngine();warm(engine,asset,source);long baseline=used();
        retained=engine.prepare(asset,new Position(-128,180,-128),0,source,()->false,p->{});long delta=used()-baseline;
        if(retained.cells().size()!=16384)throw new AssertionError("Prepared count");
        for(var tree:retained.cells().values())if(tree.occupied()!=4096)throw new AssertionError("Geometry changed");
        System.out.println("{\"cells\":16384,\"prepared_heap_delta_bytes\":"+delta+",\"qualification\":\"single GC-sensitive retained staging sample, not peak memory or city/GPU/network qualification\"}");
        retained.close();
    }
}
