import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.lang.management.ManagementFactory;
import java.security.MessageDigest;
import java.util.*;
import static dev.jayms.net.structure.StructurePackage.*;

/** Admitted small-package storage/export diagnostic, not city/system qualification. */
public final class PackedGeometryProbe {
    static volatile StructurePackage retained;
    static volatile long consumed;
    static int color(int index,boolean mixed) {
        return mixed?0xfe000000|(index*37&0xffffff):WorldVoxels.encode(index%2==0?Blocks.STONE:Blocks.BRICKS);
    }
    static StructurePackage fixture(boolean mixed)throws Exception {
        int count=mixed?50000:100000;var cells=new ArrayList<Cell>();
        for(int i=0;i<count;i++) {
            var tree=new SparseVoxelOctree(16);
            if(mixed) {
                tree.fill(1,2,3,2,3,4,color(i,true));
                tree.fill(14,13,12,15,14,13,WorldVoxels.encode(Blocks.STONE));
            } else tree.fill(0,0,0,16,16,16,color(i,false));
            cells.add(new Cell(new Position(i%512,0,i/512),tree,-1));
        }
        return new StructurePackage(mixed?"Mixed storage":"Uniform storage",new Bounds(512,1,256,0,0,0),List.of(),cells);
    }
    static long heap()throws Exception {
        System.gc();Thread.sleep(100);System.gc();Thread.sleep(100);
        return Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
    }
    static long visit(StructurePackage asset,boolean mixed) {
        long sum=0;
        for(var cell:asset.cells) {
            int index=cell.position().z()*512+cell.position().x();var tree=cell.geometry();
            int value=tree.get(1,2,3);if(value!=color(index,mixed)||tree.occupied()!=(mixed?2:4096))throw new AssertionError("Geometry value changed");
            sum+=value;
        }
        return sum;
    }
    static String sha(byte[] data)throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
    public static void main(String[] args)throws Exception {
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);
        for(boolean mixed:new boolean[]{false,true}) {
            retained=fixture(mixed);retained=null;long baseline=heap();retained=fixture(mixed);long retainedBytes=heap()-baseline;
            byte[] encoded=retained.encode();String expected=sha(encoded);int encodedBytes=encoded.length;encoded=null;
            double[][] times=new double[2][20];long[][] allocated=new long[2][20];
            for(int run=-3;run<20;run++) {
                long before=bean.getCurrentThreadAllocatedBytes(),start=System.nanoTime();
                byte[] result=retained.encode();
                double exportMs=(System.nanoTime()-start)/1e6;long exportAllocated=bean.getCurrentThreadAllocatedBytes()-before;
                if(!expected.equals(sha(result)))throw new AssertionError("Export changed");
                before=bean.getCurrentThreadAllocatedBytes();start=System.nanoTime();
                consumed=visit(retained,mixed);
                double visitMs=(System.nanoTime()-start)/1e6;long visitAllocated=bean.getCurrentThreadAllocatedBytes()-before;
                if(run>=0){times[0][run]=exportMs;allocated[0][run]=exportAllocated;times[1][run]=visitMs;allocated[1][run]=visitAllocated;}
            }
            if(!expected.equals(sha(StructurePackage.decode(retained.encode()).encode())))throw new AssertionError("Package roundtrip changed");
            for(int mode=0;mode<2;mode++) {
                Arrays.sort(times[mode]);Arrays.sort(allocated[mode]);
                System.out.printf(Locale.ROOT,"{\"implementation\":\"%s\",\"fixture\":\"%s\",\"cells\":%d,\"operation\":\"%s\",\"runs\":20,\"warmups\":3,\"median_ms\":%.3f,\"p95_ms\":%.3f,\"worst_ms\":%.3f,\"median_thread_allocated_bytes\":%d,\"retained_heap_delta_bytes\":%d,\"compressed_bytes\":%d,\"package_sha256\":\"%s\"}%n",
                        args[0],mixed?"mixed":"uniform",retained.cells.size(),mode==0?"export":"visit",(times[mode][9]+times[mode][10])/2,times[mode][18],times[mode][19],(allocated[mode][9]+allocated[mode][10])/2,retainedBytes,encodedBytes,expected);
            }
            retained=null;
        }
    }
}
