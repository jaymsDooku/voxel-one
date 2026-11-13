package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import java.lang.management.ManagementFactory;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Modifier;
import java.util.*;

/** Synthetic component comparison, not city or renderer qualification. */
public final class WorldEditSnapshotProbe {
    private static volatile Object held;
    private static Instrumentation sizes;
    public static void premain(String ignored, Instrumentation value) { sizes=value; }
    private static long reachable(Object... roots) throws Exception {
        var seen=new IdentityHashMap<Object,Boolean>(); var queue=new ArrayDeque<Object>();
        for(var root:roots)if(root!=null)queue.add(root);
        long bytes=0;
        while(!queue.isEmpty()) {
            var value=queue.remove();if(seen.put(value,Boolean.TRUE)!=null)continue;
            bytes+=sizes.getObjectSize(value);
            if(value instanceof Object[] array){for(var item:array)if(item!=null)queue.add(item);continue;}
            if(value.getClass().isArray())continue;
            for(Class<?> type=value.getClass();type!=null&&type!=Object.class;type=type.getSuperclass())
                for(var field:type.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&!field.getType().isPrimitive()) {
                    field.setAccessible(true);var child=field.get(value);if(child!=null)queue.add(child);
                }
        }
        return bytes;
    }
    private static final com.sun.management.ThreadMXBean MEMORY =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static long checksum(Map<String,Protocol.Edit> rows) {
        long result=0; for(var row:rows.entrySet()) result=Long.rotateLeft(result,1)^row.hashCode();
        return result;
    }
    public static void main(String[] args) throws Exception {
        if(!MEMORY.isThreadAllocatedMemorySupported()) throw new IllegalStateException("Allocation counter unavailable");
        MEMORY.setThreadAllocatedMemoryEnabled(true);
        Map<String,Protocol.Edit> retained; long finalChecksum;
        try(var world=new World(42)) {
            boolean ordinary=args.length!=0 && args[0].equals("ordinary");
            int cells=ordinary?512:8;
            var random=new Random(60621); var tree=new SparseVoxelOctree(16);
            if(!ordinary)for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++)
                tree.fill(x,y,z,x+1,y+1,z+1,0xfe000000|random.nextInt(1<<24));
            for(int i=0;i<cells;i++) {
                if(ordinary)world.apply(new Protocol.Edit(i*32-128,180,-17,Blocks.STONE));
                else world.replaceCell(i*32-128,180,-17,tree);
            }
            var field=World.class.getDeclaredField("edits"); field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var source=(Map<ChunkPos,Map<String,Protocol.Edit>>)field.get(world);
            var expected=new LinkedHashMap<String,Protocol.Edit>(); source.values().forEach(expected::putAll);
            long expectedChecksum=checksum(expected),id=Thread.currentThread().getId();
            System.out.print("{\"warmups\":3,\"runs\":20,\"cells\":"+cells+",\"rows\":"+expected.size()+",\"samples\":[");
            boolean first=true;
            for(int run=-3;run<20;run++)for(int turn=0;turn<2;turn++) {
                boolean current=((run+turn)&1)==0;
                long allocation=MEMORY.getThreadAllocatedBytes(id),start=System.nanoTime();
                Map<String,Protocol.Edit> snapshot;
                if(current) snapshot=world.editsSnapshot();
                else {var flat=new LinkedHashMap<String,Protocol.Edit>();source.values().forEach(flat::putAll);snapshot=Collections.unmodifiableMap(flat);}
                long nanos=System.nanoTime()-start,bytes=MEMORY.getThreadAllocatedBytes(id)-allocation;
                held=snapshot;
                long scanAllocation=MEMORY.getThreadAllocatedBytes(id),scanStart=System.nanoTime();
                long scanned=checksum(snapshot);
                long scanNanos=System.nanoTime()-scanStart,scanBytes=MEMORY.getThreadAllocatedBytes(id)-scanAllocation;
                long lookupAllocation=MEMORY.getThreadAllocatedBytes(id),lookupStart=System.nanoTime();
                long found=0;for(var key:expected.keySet())found+=snapshot.get(key).hashCode();
                long lookupNanos=System.nanoTime()-lookupStart,lookupBytes=MEMORY.getThreadAllocatedBytes(id)-lookupAllocation;
                held=found;
                if(snapshot.size()!=expected.size()||scanned!=expectedChecksum||!snapshot.equals(expected)||!expected.equals(snapshot))
                    throw new AssertionError("Snapshot differs");
                if(run>=0){if(!first)System.out.print(',');first=false;System.out.print("{\"mode\":\""+(current?"current":"flat")+"\",\"run\":"+(run+1)+",\"nanos\":"+nanos+",\"allocated_bytes\":"+bytes+",\"scan_nanos\":"+scanNanos+",\"scan_allocated_bytes\":"+scanBytes+",\"lookup_nanos\":"+lookupNanos+",\"lookup_allocated_bytes\":"+lookupBytes+"}");}
            }
            retained=world.editsSnapshot(); finalChecksum=expectedChecksum;
            var flat=new LinkedHashMap<String,Protocol.Edit>();source.values().forEach(flat::putAll);
            var old=Collections.unmodifiableMap(flat);
            if(sizes==null)throw new IllegalStateException("Instrumentation missing");
            long sourceBytes=reachable(source),flatBytes=reachable(source,old),rootBytes=reachable(source,retained);
            System.out.print("],\"reachable_history_graph_bytes\":{\"source\":"+sourceBytes+",\"source_plus_flat_snapshot\":"+flatBytes+",\"source_plus_current_snapshot\":"+rootBytes+"}");
            System.out.println(",\"checks\":\"ordered rows, lookup, size, equality and close isolation passed\",\"qualification\":false}");
        }
        if(checksum(retained)!=finalChecksum)throw new AssertionError("Close changed snapshot");
    }
}
