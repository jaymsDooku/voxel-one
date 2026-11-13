import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.lang.management.ManagementFactory;
import java.security.MessageDigest;
import java.util.*;

/** CPU/allocation codec diagnostic. Not network or full-fixture qualification. */
public final class WireBufferProbe {
    static volatile Object consumed;
    static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    static StructureWire.Delta fixture() {
        var tree=new SparseVoxelOctree(32);tree.set(31,2,7,0xff123456);
        var models=List.of(new ModelLibrary.Entry(1024,"fixture",new ModelDefinition("Wire component",tree)));
        var cells=new ArrayList<Protocol.CellState>();
        for(int i=0;i<64;i++) {
            tree=new SparseVoxelOctree(16);
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
                if(((x+y+z)&1)==0)tree.fill(x,y,z,x+1,y+1,z+1,0xfe000000|((i*171+x*37+y*91+z*101)&0xffffff));
            cells.add(new Protocol.CellState(i-32,128,-2,tree));
        }
        return new StructureWire.Delta(models,cells);
    }
    static void validate(StructureWire.Delta delta,StructureWire.Delta original) {
        if(delta.cells().size()!=original.cells().size()||delta.models().size()!=1||!delta.models().get(0).definition().fingerprint().equals(original.models().get(0).definition().fingerprint()))throw new AssertionError("Dependencies changed");
        for(int i=0;i<delta.cells().size();i++) {
            var a=original.cells().get(i);var b=delta.cells().get(i);
            if(a.x()!=b.x()||a.y()!=b.y()||a.z()!=b.z()||!a.tree().sameValues(b.tree()))throw new AssertionError("World cell changed");
        }
    }
    public static void main(String[] args)throws Exception {
        var delta=fixture();byte[] wire=StructureWire.encode(delta);String hash=sha(wire);
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);
        for(int mode=0;mode<2;mode++) {
            double[] times=new double[20];long[] allocations=new long[20];
            for(int run=-3;run<20;run++) {
                long before=bean.getCurrentThreadAllocatedBytes(),start=System.nanoTime();
                Object value=mode==0?StructureWire.encode(delta):StructureWire.decode(wire);
                double ms=(System.nanoTime()-start)/1e6;long bytes=bean.getCurrentThreadAllocatedBytes()-before;
                consumed=value;
                if(mode==0){if(!hash.equals(sha((byte[])value)))throw new AssertionError("Wire bytes changed");}
                else validate((StructureWire.Delta)value,delta);
                if(run>=0){times[run]=ms;allocations[run]=bytes;}
            }
            Arrays.sort(times);Arrays.sort(allocations);
            System.out.printf(Locale.ROOT,"{\"implementation\":\"%s\",\"operation\":\"%s\",\"cells\":64,\"geometry_leaves\":131072,\"models\":1,\"compressed_bytes\":%d,\"wire_sha256\":\"%s\",\"warmups\":3,\"runs\":20,\"median_ms\":%.3f,\"p95_ms\":%.3f,\"worst_ms\":%.3f,\"median_thread_allocated_bytes\":%d}%n",args[0],mode==0?"encode":"decode",wire.length,hash,(times[9]+times[10])/2,times[18],times[19],(allocations[9]+allocations[10])/2);
        }
        if(!hash.equals(sha(StructureWire.encode(StructureWire.decode(wire)))))throw new AssertionError("Roundtrip changed");
    }
}
