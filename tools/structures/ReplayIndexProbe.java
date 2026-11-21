package dev.jayms.net;
import dev.jayms.ChunkPos;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.lang.management.ManagementFactory;

/** Actual private spool I/O, not durable/live paging or city qualification. */
public final class ReplayIndexProbe {
    static final com.sun.management.ThreadMXBean MEMORY=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    static LinkedHashMap<String,Protocol.Edit> fixture(String name) {
        var rows=new LinkedHashMap<String,Protocol.Edit>();
        for(int i=0;i<(name.equals("empty")?0:8192);i++) {
            int region=name.equals("clustered")?i/1024:i%32,local=name.equals("clustered")?i%1024:i/32;
            var row=new Protocol.Edit(region*32+local%16,128+local/256,local/16%16,Blocks.STONE);rows.put(row.key(),row);
        }
        return rows;
    }
    static long hash(Iterable<Protocol.Edit> rows,ChunkPos selected) {
        long result=0;for(var row:rows)if(selected==null||ChunkPos.fromBlock(row.x(),row.y(),row.z()).equals(selected))result=result*31+row.hashCode();return result;
    }
    public static void main(String[] args)throws Exception {
        boolean indexed=args[0].equals("current");var directory=Path.of(args[1]);Files.createDirectories(directory);MEMORY.setThreadAllocatedMemoryEnabled(true);
        System.out.print("{\"warmups\":3,\"runs\":20,\"cases\":[");boolean firstCase=true;
        for(String name:List.of("clustered","fragmented","empty")) {
            var rows=fixture(name);var bytes=new ByteArrayOutputStream();RegionReplay.write(new DataOutputStream(bytes),rows,()->false,p->{});var encoded=bytes.toByteArray();
            var selected=new ChunkPos(0,8,0);long allHash=hash(rows.values(),null),selectedHash=hash(rows.values(),selected);int selectedCount=(int)rows.values().stream().filter(e->ChunkPos.fromBlock(e.x(),e.y(),e.z()).equals(selected)).count();
            if(!firstCase)System.out.print(',');firstCase=false;System.out.print("{\"fixture\":\""+name+"\",\"records\":"+rows.size()+",\"selected_records\":"+selectedCount+",\"samples\":[");boolean first=true;
            for(int run=-3;run<20;run++) {
                long id=Thread.currentThread().getId(),allocated=MEMORY.getThreadAllocatedBytes(id),start=System.nanoTime();
                try(var spool=RegionReplaySpool.stage(directory,new DataInputStream(new ByteArrayInputStream(encoded)),e->true,()->false)) {
                    long stageNanos=System.nanoTime()-start,stageBytes=MEMORY.getThreadAllocatedBytes(id)-allocated;
                    if(spool.size()!=rows.size()||spool.encodedBytes()!=encoded.length)throw new AssertionError("Stage count/bytes differ");
                    long stagingBytes;try(var paths=Files.list(directory)){stagingBytes=paths.mapToLong(p->{try{return Files.size(p);}catch(IOException e){throw new UncheckedIOException(e);}}).sum();}
                    long[] state={0,0};allocated=MEMORY.getThreadAllocatedBytes(id);start=System.nanoTime();
                    spool.visit(e->true,()->false,(r,s)->{for(var row:s){state[0]++;state[1]=state[1]*31+row.hashCode();}});
                    long fullNanos=System.nanoTime()-start,fullBytes=MEMORY.getThreadAllocatedBytes(id)-allocated;
                    if(state[0]!=rows.size()||state[1]!=allHash)throw new AssertionError("Full replay differs");
                    state[0]=0;state[1]=0;allocated=MEMORY.getThreadAllocatedBytes(id);start=System.nanoTime();
                    RegionReplay.SectionSink sink=(r,s)->{if(r.equals(selected))for(var row:s){state[0]++;state[1]=state[1]*31+row.hashCode();}};
                    if(indexed)spool.visitRegion(selected,e->true,()->false,sink);else spool.visit(e->true,()->false,sink);
                    long selectedNanos=System.nanoTime()-start,selectedBytes=MEMORY.getThreadAllocatedBytes(id)-allocated;
                    if(state[0]!=selectedCount||state[1]!=selectedHash)throw new AssertionError("Selective replay differs");
                    if(run>=0){if(!first)System.out.print(',');first=false;System.out.print("{\"run\":"+(run+1)+",\"stage_nanos\":"+stageNanos+",\"stage_allocated_bytes\":"+stageBytes+",\"full_nanos\":"+fullNanos+",\"full_allocated_bytes\":"+fullBytes+",\"selected_nanos\":"+selectedNanos+",\"selected_allocated_bytes\":"+selectedBytes+",\"staging_bytes\":"+stagingBytes+"}");}
                }
                try(var paths=Files.list(directory)){if(paths.findAny().isPresent())throw new AssertionError("Spool/index leaked");}
            }
            System.out.print("]}");
        }
        System.out.println("]}");
    }
}
