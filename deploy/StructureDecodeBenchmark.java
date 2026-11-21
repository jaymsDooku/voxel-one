import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.StructurePackage;
import static dev.jayms.net.structure.StructurePackage.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Public decoder component diagnostic. Does not admit city/max fixtures or qualify placement. */
public final class StructureDecodeBenchmark {
    private static final int RUNS=20;
    private static StructurePackage fixture(boolean dense)throws Exception {
        var tree=new SparseVoxelOctree(16);
        if(dense)for(int i=0;i<2048;i++){int x=i%16,y=i/256,z=i/16%16;tree.fill(x,y,z,x+1,y+1,z+1,0xfe000000|((i*1664525)&0xffffff));}
        else tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        int count=dense?64:100000;var cells=new ArrayList<Cell>(count);
        for(int i=0;i<count;i++)cells.add(new Cell(new Position(dense?i:i%128,dense?0:i/16384,dense?0:i/128%128),tree,-1));
        return new StructurePackage(dense?"Dense decode":"Uniform decode",dense?new Bounds(64,1,1,0,0,0):new Bounds(128,16,128,0,0,0),List.of(),cells);
    }
    private static String stats(double[] values) {
        var sorted=values.clone();Arrays.sort(sorted);
        return "{\"median\":"+((sorted[9]+sorted[10])/2)+",\"p95\":"+sorted[18]+",\"worst\":"+sorted[19]+"}";
    }
    public static void main(String[] args)throws Exception {
        if(args.length==2&&args[0].equals("prepare")) {
            var directory=Path.of(args[1]);
            for(boolean dense:new boolean[]{false,true})Files.write(directory.resolve(dense?"dense.vstr":"uniform.vstr"),fixture(dense).encode(),StandardOpenOption.CREATE_NEW);
            return;
        }
        if(args.length!=3)throw new IllegalArgumentException("fixture.vstr result.json label");
        var output=Path.of(args[1]);if(Files.exists(output))throw new IllegalArgumentException("Fresh result required");
        byte[] bytes=Files.readAllBytes(Path.of(args[0]));boolean dense=Path.of(args[0]).getFileName().toString().equals("dense.vstr");
        if(bytes.length>COMPRESSED)throw new IllegalArgumentException("Fixture budget");
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        double[] ms=new double[RUNS],allocated=new double[RUNS];
        for(int run=-3;run<RUNS;run++) {
            long before=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
            var asset=StructurePackage.decode(bytes);
            double elapsed=(System.nanoTime()-start)/1e6;long allocation=bean.getThreadAllocatedBytes(thread)-before;
            if(asset.cells.size()!=(dense?64:100000)||asset.leafCount!=(dense?131072:100000)||asset.instanceCount!=0)
                throw new AssertionError("Fixture counts");
            if(!Arrays.equals(bytes,asset.encode()))throw new AssertionError("Exact full package roundtrip");
            if(run>=0){ms[run]=elapsed;allocated[run]=allocation;}
        }
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        String json="{\"label\":\""+args[2]+"\",\"fixture\":\""+(dense?"dense":"uniform")+"\",\"runs\":20,\"warmups\":3,\"fixtureSha256\":\""+hash+"\",\"compressedBytes\":"+bytes.length
                +",\"decodeMs\":"+stats(ms)+",\"threadAllocatedBytes\":"+stats(allocated)
                +",\"samplesMs\":"+Arrays.toString(ms)+",\"samplesAllocatedBytes\":"+Arrays.toString(allocated)
                +",\"qualification\":false,\"verification\":\"Counts and exact encoded bytes checked after every decode outside timings\"}\n";
        Files.writeString(output,json,StandardOpenOption.CREATE_NEW);
        System.out.println("Playtest: "+args[2]+" "+(dense?"dense":"uniform")+" decoder diagnostic passed 20 measured runs and 3 warmups.");
    }
}
