package dev.jayms.net;

import java.io.*;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Modifier;
import java.util.*;

/** Synthetic replay-only diagnostic. No live world, GPU, accounts or network. */
public final class RegionalReplayLoadProbe {
    private static Instrumentation sizes;
    private static volatile Object held;
    private static final com.sun.management.ThreadMXBean MEMORY=
            (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    public static void premain(String ignored,Instrumentation value){sizes=value;}
    private static long reachable(Object root)throws Exception {
        var seen=new IdentityHashMap<Object,Boolean>();var queue=new ArrayDeque<Object>();queue.add(root);long bytes=0;
        while(!queue.isEmpty()) {
            var value=queue.remove();if(seen.put(value,Boolean.TRUE)!=null)continue;bytes+=sizes.getObjectSize(value);
            if(value instanceof Object[] array){for(var item:array)if(item!=null)queue.add(item);continue;}
            if(value.getClass().isArray())continue;
            for(Class<?> type=value.getClass();type!=null&&type!=Object.class;type=type.getSuperclass())
                for(var field:type.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&!field.getType().isPrimitive()) {
                    field.setAccessible(true);var child=field.get(value);if(child!=null)queue.add(child);
                }
        }
        return bytes;
    }
    private static byte[] encode(Map<String,Protocol.Edit> history)throws Exception {
        var bytes=new ByteArrayOutputStream();RegionReplay.write(new DataOutputStream(bytes),history,()->false,p->{});return bytes.toByteArray();
    }
    private static WorldVoxels.History fixture(int fixture) {
        var history=new WorldVoxels.History();
        for(int cell=0;cell<8;cell++) {
            int x=cell*32-128;WorldVoxels.rememberValidated(history,new Protocol.Edit(x,180,-17,fixture==1?Blocks.STONE:0));
            var leaves=new ArrayList<Protocol.Edit>();
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int ix=0;ix<16;ix++)
                if(ix+16*z+256*y<(fixture==2?1:fixture==3?32:4096))leaves.add(new Protocol.Edit(x,180,-17,Blocks.piece(Blocks.LED,4),4,ix,y,z,(ix+16*z+256*y)*37));
            Collections.shuffle(leaves,new Random(60701+cell));
            for(var edit:leaves)WorldVoxels.rememberValidated(history,edit);
        }
        return history;
    }
    public static void main(String[] args)throws Exception {
        if(sizes==null||!MEMORY.isThreadAllocatedMemorySupported())throw new IllegalStateException("Instrumentation unavailable");
        MEMORY.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        System.out.print("{\"warmups\":3,\"runs\":20,\"heap_mib\":512,\"cases\":[");
        for(int fixture=0;fixture<4;fixture++) {
            var expected=fixture(fixture);var entries=new ArrayList<>(expected.entrySet());byte[] bytes=encode(expected);
            long[] nanos=new long[20],allocation=new long[20],writeNanos=new long[20],writeAllocation=new long[20],lookupNanos=new long[20],lookupAllocation=new long[20];WorldVoxels.History last=null;
            for(int run=-3;run<20;run++) {
                held=null;System.gc();Thread.sleep(10);
                long allocated=MEMORY.getThreadAllocatedBytes(thread),start=System.nanoTime();
                var history=new WorldVoxels.History();
                try(var in=new DataInputStream(new ByteArrayInputStream(bytes))) {
                    RegionReplay.read(in,history);if(in.read()!=-1)throw new AssertionError("Trailing source");
                }
                long elapsed=System.nanoTime()-start,count=MEMORY.getThreadAllocatedBytes(thread)-allocated;
                held=history;last=history;
                if(!entries.equals(new ArrayList<>(history.entrySet()))||history.revision()!=expected.revision())
                    throw new AssertionError("Ordered replay/revision differs");
                long writeAllocated=MEMORY.getThreadAllocatedBytes(thread),writeStart=System.nanoTime();
                byte[] encoded=encode(history);
                long writeElapsed=System.nanoTime()-writeStart,writeBytes=MEMORY.getThreadAllocatedBytes(thread)-writeAllocated;
                if(!Arrays.equals(bytes,encoded))throw new AssertionError("Re-encoded replay differs");
                long lookupAllocated=MEMORY.getThreadAllocatedBytes(thread),lookupStart=System.nanoTime(),sum=0;
                for(var entry:entries)sum+=history.get(entry.getKey()).hashCode();
                long lookupElapsed=System.nanoTime()-lookupStart,lookupBytes=MEMORY.getThreadAllocatedBytes(thread)-lookupAllocated;
                held=sum;
                if(run>=0){nanos[run]=elapsed;allocation[run]=count;writeNanos[run]=writeElapsed;writeAllocation[run]=writeBytes;lookupNanos[run]=lookupElapsed;lookupAllocation[run]=lookupBytes;}
            }
            if(fixture>0)System.out.print(',');
            System.out.print("{\"fixture\":\""+(fixture==0?"canonical":fixture==1?"legacy_nonclear":fixture==2?"canonical_sparse":"canonical_threshold")+"\",\"cells\":8,\"rows\":"+expected.size()+",\"encoded_bytes\":"+bytes.length+",\"nanos\":"+Arrays.toString(nanos)+",\"allocated_bytes\":"+Arrays.toString(allocation)+",\"write_nanos\":"+Arrays.toString(writeNanos)+",\"write_allocated_bytes\":"+Arrays.toString(writeAllocation)+",\"lookup_nanos\":"+Arrays.toString(lookupNanos)+",\"lookup_allocated_bytes\":"+Arrays.toString(lookupAllocation)+",\"retained_history_bytes\":"+reachable(last)+"}");
        }
        System.out.println("],\"checks\":\"Exact ordered rows, revision and complete re-encoded bytes verified after every load\",\"qualification\":false}");
    }
}
