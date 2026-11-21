package dev.jayms;

import dev.jayms.net.model.*;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Actual dependency staging component; no placement, GPU or network qualification. */
public final class ModelAllocationBenchmark {
    private static ModelDefinition model(String name,int color) {
        var tree=new SparseVoxelOctree(8);tree.set(1,2,3,color);
        return new ModelDefinition(name,tree.freeze());
    }
    private static String sample(List<ModelDefinition> prefix,List<ModelDefinition> batch,String mode,int run)throws Exception {
        var models=new ModelLibrary();
        for(var definition:prefix)models.stageStructureModel(definition);
        long revision=models.revision();var heap=ManagementFactory.getMemoryMXBean();long before=heap.getHeapMemoryUsage().getUsed();
        long start=System.nanoTime();
        for(int i=0;i<batch.size();i++)if(models.stageStructureModel(batch.get(i)).id()!=1024+prefix.size()+i)throw new AssertionError("Lowest-free ID changed");
        long nanos=System.nanoTime()-start,after=heap.getHeapMemoryUsage().getUsed();
        if(models.revision()!=revision+512)throw new AssertionError("One revision per new dependency");
        var encoded=new ByteArrayOutputStream();models.writeAll(new DataOutputStream(encoded));
        String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded.toByteArray()));
        for(int i=0;i<batch.size();i++)if(models.stageStructureModel(batch.get(i)).id()!=1024+prefix.size()+i)throw new AssertionError("Deduplication changed");
        if(models.revision()!=revision+512)throw new AssertionError("Duplicate must not mutate library");
        return "{\"mode\":\""+mode+"\",\"run\":"+run+",\"prefix_models\":"+prefix.size()+",\"batch_models\":512,\"nanos\":"+nanos+",\"used_heap_before\":"+before+",\"used_heap_after\":"+after+",\"world_library_sha256\":\""+digest+"\"}";
    }
    public static void main(String[] args)throws Exception {
        var prefix=new ArrayList<ModelDefinition>();var batch=new ArrayList<ModelDefinition>();
        for(int i=0;i<1536;i++)prefix.add(model("Prefix "+i,0xff000000|i+1));
        for(int i=0;i<512;i++)batch.add(model("Dependency "+i,0xff800000|i+1));
        sample(List.of(),batch,"empty",-1);sample(prefix,batch,"prefilled",-1);
        var samples=new ArrayList<String>();
        for(int i=0;i<20;i++) {
            if((i&1)==0){samples.add(sample(List.of(),batch,"empty",i));samples.add(sample(prefix,batch,"prefilled",i));}
            else{samples.add(sample(prefix,batch,"prefilled",i));samples.add(sample(List.of(),batch,"empty",i));}
        }
        String text="{\"schema\":1,\"scope\":\"512 tiny-model dependency staging, not a city or maximum structure fixture\",\"runs_per_mode\":20,\"warmups\":2,\"qualification\":false,\"heap_max_bytes\":"+Runtime.getRuntime().maxMemory()+",\"available_processors\":"+Runtime.getRuntime().availableProcessors()+",\"heap_note\":\"Used heap before/after only; shared JVM carryover, not peak memory or incremental allocation\",\"samples\":["+String.join(",",samples)+"]}\n";
        Files.writeString(Path.of(args[0]),text);System.out.println("PASS:20 runs per library occupancy; IDs, deduplication and library bytes recorded.");
    }
}
