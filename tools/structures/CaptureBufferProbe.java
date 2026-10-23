import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import static dev.jayms.net.structure.StructurePackage.*;

/** One GC-sensitive live-heap sample at capture progress100, before package validation. */
public final class CaptureBufferProbe {
    static volatile StructurePackage retained;
    static long used() {
        try{System.gc();Thread.sleep(100);System.gc();Thread.sleep(100);}catch(InterruptedException e){throw new RuntimeException(e);}
        var r=Runtime.getRuntime();return r.totalMemory()-r.freeMemory();
    }
    public static void main(String[] args)throws Exception {
        var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));tree.freeze();
        var models=new ModelLibrary();
        Source source=new Source(){public SparseVoxelOctree cell(int x,int y,int z){return tree.copy();}public ModelLibrary models(){return models;}};
        retained=StructurePackage.capture("Buffer warmup",new Bounds(10,1,10,0,0,0),new Position(0,180,0),source,()->false,p->{});retained=null;
        long baseline=used();long[] buffer={0};
        retained=StructurePackage.capture("Buffer probe",new Bounds(500,1,200,0,0,0),new Position(0,180,0),source,()->false,p->{if(p==100)buffer[0]=used()-baseline;});
        if(retained.cells.size()!=100000)throw new AssertionError("Capture count");
        System.out.println("{\"cells\":100000,\"capture_buffer_heap_delta_bytes\":"+buffer[0]+",\"qualification\":\"one GC-sensitive sample before constructor, fresh snapshot wrapper per cell; not peak memory or city qualification\"}");
    }
}
