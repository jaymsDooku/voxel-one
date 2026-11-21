import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import static dev.jayms.net.structure.StructurePackage.*;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.security.MessageDigest;
import java.util.*;

/** Bounded rejection diagnostic, not city or placement qualification. */
public final class PlacementAdmissionProbe {
    static final class SourceProbe implements Source {
        int modelCalls,cellCalls;
        final ModelLibrary library=new ModelLibrary();
        final SparseVoxelOctree empty=new SparseVoxelOctree(16).freeze();
        public ModelLibrary models(){modelCalls++;return library;}
        public SparseVoxelOctree cell(int x,int y,int z){cellCalls++;return empty;}
        public boolean emptyCell(int x,int y,int z){return true;}
    }
    public static StructurePackage fixture()throws Exception {
        var definitions=new ArrayList<ModelDefinition>();
        for(int i=0;i<256;i++) {
            var tree=new SparseVoxelOctree(32);
            for(int k=0;k<32;k++)tree.set(k,k*7%32,k*13%32,0xff000000|((i+1)*499+k*17));
            definitions.add(new ModelDefinition("Admission dependency "+i,tree));
        }
        var geometry=new SparseVoxelOctree(16);geometry.fill(1,1,1,2,2,2,WorldVoxels.encode(Blocks.BRICKS));
        geometry.fill(14,14,14,15,15,15,WorldVoxels.encode(new Protocol.Edit(0,0,0,Blocks.LED).withColor(0x73519b)));
        var cells=new ArrayList<Cell>();
        for(int z=0;z<17&&cells.size()<257;z++)for(int x=0;x<17&&cells.size()<257;x++)cells.add(new Cell(new Position(x*16,0,z*16),geometry,-1));
        return new StructurePackage("Admission 257 chunks",new Bounds(272,1,272,0,0,0),definitions,cells);
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=1||!Set.of("before","after").contains(args[0]))throw new IllegalArgumentException("before or after required");
        var asset=fixture();String identity=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(asset.encode()));
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);double[] times=new double[20];long[] allocations=new long[20];int modelCalls=0,cellCalls=0;
        for(int run=-3;run<20;run++) {
            var source=new SourceProbe();var engine=new StructureEngine();long allocated=bean.getCurrentThreadAllocatedBytes(),start=System.nanoTime();
            try(var prepared=engine.prepare(asset,new Position(-17,160,-17),1,source,()->false,p->{})){throw new AssertionError("Oversized fixture admitted");}
            catch(IOException expected){if(!expected.getMessage().contains("256 affected chunks"))throw expected;}
            if(engine.busy()||source.library.entries().size()!=1)throw new AssertionError("Rejection changed authority or retained engine reservation");
            if(run>=0){times[run]=(System.nanoTime()-start)/1e6;allocations[run]=bean.getCurrentThreadAllocatedBytes()-allocated;modelCalls+=source.modelCalls;cellCalls+=source.cellCalls;}
        }
        Arrays.sort(times);Arrays.sort(allocations);
        System.out.printf(Locale.ROOT,"{\"implementation\":\"%s\",\"runs\":20,\"warmups\":3,\"cells\":257,\"affected_chunks\":257,\"dependencies\":256,\"geometry_leaves\":%d,\"fixture_sha256\":\"%s\",\"median_ms\":%.6f,\"p95_ms\":%.6f,\"worst_ms\":%.6f,\"median_thread_allocated_bytes\":%d,\"source_model_calls\":%d,\"source_cell_calls\":%d,\"qualification\":false}%n",args[0],asset.leafCount,identity,(times[9]+times[10])/2,times[18],times[19],(allocations[9]+allocations[10])/2,modelCalls,cellCalls);
    }
}
