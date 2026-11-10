package dev.jayms.net;

import dev.jayms.ChunkPos;
import java.io.*;
import java.lang.management.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Warm-cache CPU/file diagnostic, never city qualification. */
public final class ReplayBuildComparison {
    static final ChunkPos REGION=new ChunkPos(0,10,0);
    static int key(Protocol.Edit e){return (e.x()&15)|((e.z()&15)<<4)|((e.y()&15)<<8);}
    static byte[] fixture(List<Protocol.Edit> selected)throws IOException {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeInt(65536);
        for(int batch=0;batch<4096;batch++) {
            var body=new ByteArrayOutputStream();var rows=new DataOutputStream(body);
            for(int j=0;j<8;j++) {
                int cell=(batch*8+j)%4096;
                var e=new Protocol.Edit(cell&15,160+(cell>>>8),(cell>>>4)&15,((batch+j)&1)==0?Blocks.STONE:Blocks.GLASS);
                selected.add(e);e.write(rows);
            }
            RegionReplay.writeSection(out,REGION,8,body);
            body.reset();for(int j=0;j<8;j++)new Protocol.Edit(144+j,160,144,Blocks.STONE).write(rows);
            RegionReplay.writeSection(out,new ChunkPos(9,10,9),8,body);
        }
        out.flush();return bytes.toByteArray();
    }
    static Set<Path> files(Path folder)throws IOException{try(var ps=Files.list(folder)){return new HashSet<>(ps.toList());}}
    public static void main(String[] args)throws Exception {
        Path folder=Path.of(args[0]),output=Path.of(args[1]);int sourcePasses=Integer.parseInt(args[2]);
        if(Files.exists(folder)||Files.exists(output)||(sourcePasses!=1&&sourcePasses!=2))throw new IllegalArgumentException("Fresh outputs and 1/2 passes required");
        Files.createDirectory(folder);var selected=new ArrayList<Protocol.Edit>();var encoded=fixture(selected);
        var first=selected.stream().filter(e->key(e)<64).toList();var last=selected.stream().filter(e->key(e)>=4032).toList();
        var heapPools=ManagementFactory.getMemoryPoolMXBeans().stream().filter(p->p.getType()==MemoryType.HEAP).toList();
        var results=new StringJoiner(",");
        try(var source=RegionReplaySpool.stage(folder,new DataInputStream(new ByteArrayInputStream(encoded)),e->true,()->false)) {
            var original=files(folder);
            for(int run=-2;run<20;run++) {
                for(var pool:heapPools)pool.resetPeakUsage();var policy=new AtomicInteger();
                long start=System.nanoTime();var cells=RegionReplayCells.build(folder,source,REGION,32768,e->{policy.incrementAndGet();return true;},()->false);
                long build=System.nanoTime()-start;long read;
                try(cells) {
                    if(cells.size()!=32768||cells.encodedBytes()!=917504)throw new AssertionError("Copied counts/size");
                    start=System.nanoTime();var actual=cells.readWindow(new RegionReplayPage.CellRange(0,64),4097,e->{policy.incrementAndGet();return true;},()->false);read=System.nanoTime()-start;
                    if(!actual.equals(first))throw new AssertionError("First window source order");
                    if(!cells.readWindow(new RegionReplayPage.CellRange(4032,4096),4097,e->{policy.incrementAndGet();return true;},()->false).equals(last))throw new AssertionError("Last window source order");
                    if(policy.get()!=32768*sourcePasses+first.size()+last.size())throw new AssertionError("Source pass/policy count");
                }
                if(!files(folder).equals(original))throw new AssertionError("Leaked build scratch/root");
                long heapPeakSum=0;for(var pool:heapPools)heapPeakSum+=pool.getPeakUsage().getUsed();
                if(run>=0)results.add("{\"run\":"+run+",\"build_ns\":"+build+",\"first_window_ns\":"+read+",\"heap_pool_peak_sum_bytes\":"+heapPeakSum+",\"policy_calls\":"+policy.get()+"}");
            }
        }
        if(!files(folder).isEmpty())throw new AssertionError("Spool cleanup");Files.delete(folder);
        var hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded));
        Files.writeString(output,"{\"status\":\"passed\",\"qualification\":false,\"source_passes\":"+sourcePasses+",\"warmups\":2,\"measured_runs\":20,\"fixture_rows\":65536,\"selected_rows\":32768,\"selected_cells\":4096,\"sections\":8192,\"fixture_bytes\":"+encoded.length+",\"fixture_sha256\":\""+hash+"\",\"runs\":["+results+"]}\n");
        System.out.println("Replay build diagnostic: 20 measured runs, correctness and cleanup passed; qualification=false");
    }
}
