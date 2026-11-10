package dev.jayms.net;

import dev.jayms.ChunkPos;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Warm file/CPU lookup diagnostic. Does not qualify world/client performance. */
public final class ReplayLookupDiagnostic {
    static final int REGIONS=96,CYCLES=64,ROWS=8;
    static ChunkPos region(int r){return new ChunkPos(r-48,10,(r%7)-3);}
    static byte[] fixture(List<List<Protocol.Edit>> expected)throws IOException {
        for(int r=0;r<REGIONS;r++)expected.add(new ArrayList<>());
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeInt(REGIONS*CYCLES*ROWS);
        for(int cycle=0;cycle<CYCLES;cycle++)for(int r=0;r<REGIONS;r++) {
            var p=region(r);var payload=new ByteArrayOutputStream();var rows=new DataOutputStream(payload);
            for(int j=0;j<ROWS;j++) {
                var edit=new Protocol.Edit(p.chunkX()*16+j,160+(cycle%16),p.chunkZ()*16,((cycle+j)&1)==0?Blocks.STONE:Blocks.GLASS);
                expected.get(r).add(edit);edit.write(rows);
            }
            RegionReplay.writeSection(out,p,ROWS,payload);
        }
        out.flush();return bytes.toByteArray();
    }
    static void lookup(RegionReplaySpool source,ChunkPos p,List<Protocol.Edit> expected,boolean indexed)throws IOException {
        var actual=new ArrayList<Protocol.Edit>();var calls=new AtomicInteger();
        java.util.function.Predicate<Protocol.Edit> allowed=e->{calls.incrementAndGet();return true;};
        RegionReplay.SectionSink sink=(region,rows)->{if(!region.equals(p))throw new AssertionError("Wrong region");actual.addAll(rows);};
        if(indexed)source.visitIndexedRegion(p,expected.size(),allowed,()->false,sink);
        else source.visitRegion(p,expected.size(),allowed,()->false,sink);
        if(!actual.equals(expected)||calls.get()!=expected.size())throw new AssertionError("Order/policy calls");
    }
    static long pass(RegionReplaySpool source,List<List<Protocol.Edit>> expected,boolean indexed,int shift)throws IOException {
        long start=System.nanoTime();
        for(int i=0;i<REGIONS;i++){int r=(i+shift)%REGIONS;lookup(source,region(r),expected.get(r),indexed);}
        lookup(source,new ChunkPos(-10000,10,10000),List.of(),indexed);
        return System.nanoTime()-start;
    }
    public static void main(String[] args)throws Exception {
        Path folder=Path.of(args[0]),output=Path.of(args[1]);
        if(Files.exists(folder)||Files.exists(output))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(folder);
        var expected=new ArrayList<List<Protocol.Edit>>();byte[] encoded=fixture(expected);
        var samples=new StringJoiner(",");long initial;int buckets;long bucketBytes;
        try(var source=RegionReplaySpool.stage(folder,new DataInputStream(new ByteArrayInputStream(encoded)),e->true,()->false)) {
            long start=System.nanoTime();lookup(source,region(0),expected.get(0),true);initial=System.nanoTime()-start;
            try(var files=Files.list(folder)) {
                var paths=files.filter(p->p.getFileName().toString().startsWith(".replay-bucket-")).toList();buckets=paths.size();bucketBytes=0;
                for(var p:paths)bucketBytes+=Files.size(p);
            }
            if(buckets>32||bucketBytes!=(long)REGIONS*CYCLES*RegionReplaySpool.INDEX_RECORD_BYTES)throw new AssertionError("Metadata bounds");
            Set<Path> owned;try(var paths=Files.list(folder)){owned=new HashSet<>(paths.toList());}
            for(int run=-2;run<20;run++) {
                long direct,indexed;
                if((run&1)==0){direct=pass(source,expected,false,Math.floorMod(run,REGIONS));indexed=pass(source,expected,true,Math.floorMod(run,REGIONS));}
                else{indexed=pass(source,expected,true,Math.floorMod(run,REGIONS));direct=pass(source,expected,false,Math.floorMod(run,REGIONS));}
                try(var paths=Files.list(folder)){if(!new HashSet<>(paths.toList()).equals(owned))throw new AssertionError("Lookup changed ownership");}
                if(run>=0)samples.add("{\"run\":"+run+",\"first\":\""+((run&1)==0?"direct":"indexed")+"\",\"direct_pass_ns\":"+direct+",\"indexed_pass_ns\":"+indexed+"}");
            }
        }
        try(var paths=Files.list(folder)){if(paths.findAny().isPresent())throw new AssertionError("Source cleanup");}Files.delete(folder);
        var hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded));
        Files.writeString(output,"{\"status\":\"passed\",\"qualification\":false,\"regions\":"+REGIONS+",\"rows\":"+(REGIONS*CYCLES*ROWS)+",\"sections\":"+(REGIONS*CYCLES)+",\"rows_per_lookup\":"+(CYCLES*ROWS)+",\"lookups_per_pass\":97,\"warmups\":2,\"measured_runs\":20,\"fixture_bytes\":"+encoded.length+",\"fixture_sha256\":\""+hash+"\",\"initial_bucket_build_and_one_lookup_ns\":"+initial+",\"bucket_files\":"+buckets+",\"bucket_bytes\":"+bucketBytes+",\"runs\":["+samples+"]}\n");
        System.out.println("Lookup diagnostic passed: 20 alternating-order runs; order/policy/empty-region/cleanup checked. qualification=false");
    }
}
