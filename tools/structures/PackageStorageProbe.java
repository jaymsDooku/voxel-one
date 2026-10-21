import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.util.*;
import java.security.MessageDigest;
import static dev.jayms.net.structure.StructurePackage.*;

/** Retained-heap diagnostic only; not city or full-system qualification. */
public final class PackageStorageProbe {
    private static volatile StructurePackage retained;
    private static StructurePackage fixture() throws Exception {
        var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        var cells=new ArrayList<Cell>();
        for(int i=0;i<100000;i++)cells.add(new Cell(new Position(i%512,0,i/512),tree,-1));
        return new StructurePackage("Storage probe",new Bounds(512,1,256,0,0,0),List.of(),cells);
    }
    private static long used() throws Exception {
        System.gc();Thread.sleep(100);System.gc();Thread.sleep(100);
        var runtime=Runtime.getRuntime();return runtime.totalMemory()-runtime.freeMemory();
    }
    public static void main(String[] args) throws Exception {
        retained=fixture();retained=null;long baseline=used();
        retained=fixture();long delta=used()-baseline;
        byte[] bytes=retained.encode();
        for(int i=0;i<3;i++)retained.encode();
        double[] timings=new double[20];
        for(int i=0;i<timings.length;i++){long start=System.nanoTime();retained.encode();timings[i]=(System.nanoTime()-start)/1e6;}
        Arrays.sort(timings);
        System.out.printf(Locale.ROOT,"{\"export_runs\":20,\"median_ms\":%.3f,\"p95_ms\":%.3f,\"worst_ms\":%.3f,\"qualification\":\"100000-cell CPU microbenchmark only\"}%n",(timings[9]+timings[10])/2,timings[18],timings[19]);
        if(StructurePackage.decode(bytes).cells.size()!=100000)throw new AssertionError("Roundtrip count");
        System.out.printf(Locale.ROOT,"{\"cells\":100000,\"retained_heap_delta_bytes\":%d,\"encoded_sha256\":\"%s\",\"encoded_bytes\":%d,\"qualification\":\"single synthetic retained-heap diagnostic, JVM/GC sensitive; not city qualification\"}%n",delta,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),bytes.length);
    }
}
