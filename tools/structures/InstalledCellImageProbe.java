package dev.jayms.net;

import dev.jayms.net.model.SparseVoxelOctree;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.util.*;

/** Isolated backing-tree footprint and preparation/edit allocation; not world peak memory. */
public final class InstalledCellImageProbe {
    private static Instrumentation sizes;
    private static volatile Object held;
    private static final com.sun.management.ThreadMXBean MEMORY=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    public static void premain(String ignored,Instrumentation instrumentation){sizes=instrumentation;}
    private static long footprint(Object root)throws Exception {
        var seen=new IdentityHashMap<Object,Boolean>();var queue=new ArrayDeque<Object>();queue.add(root);long bytes=0;
        while(!queue.isEmpty()) {
            Object value=queue.remove();if(seen.put(value,Boolean.TRUE)!=null)continue;bytes+=sizes.getObjectSize(value);
            if(value instanceof Object[] array){for(var item:array)if(item!=null)queue.add(item);continue;}
            if(value.getClass().isArray())continue;
            for(Class<?> type=value.getClass();type!=null&&type!=Object.class;type=type.getSuperclass())
                for(var field:type.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&!field.getType().isPrimitive()) {
                    field.setAccessible(true);var child=field.get(value);if(child!=null)queue.add(child);
                }
        }
        return bytes;
    }
    private static SparseVoxelOctree prepare(SparseVoxelOctree source,boolean packed){return packed?WorldVoxels.prepareCellImage(source).tree():source.copy().freeze();}
    private static String measure(SparseVoxelOctree source,boolean packed,boolean edit)throws Exception {
        long[] nanos=new long[20],alloc=new long[20];long thread=Thread.currentThread().getId(),retained=-1;
        for(int run=-3;run<20;run++) {
            var image=edit?prepare(source,packed):null;
            long before=MEMORY.getThreadAllocatedBytes(thread),start=System.nanoTime();SparseVoxelOctree result;
            if(edit){result=image.copy();result.fill(0,0,0,1,1,1,WorldVoxels.encode(Blocks.STONE));}
            else result=prepare(source,packed);
            long elapsed=System.nanoTime()-start,allocated=MEMORY.getThreadAllocatedBytes(thread)-before;held=result;
            if(edit){if(result.get(0,0,0)!=WorldVoxels.encode(Blocks.STONE))throw new AssertionError("Edit failed");}
            else if(!source.sameValues(result))throw new AssertionError("Prepared geometry differs");
            if(retained<0)retained=footprint(result);
            if(run>=0){nanos[run]=elapsed;alloc[run]=allocated;}
        }
        return "{\"nanos\":"+Arrays.toString(nanos)+",\"allocated_bytes\":"+Arrays.toString(alloc)+",\"reachable_tree_bytes\":"+retained+"}";
    }
    public static void main(String[] args)throws Exception {
        if(sizes==null||!MEMORY.isThreadAllocatedMemorySupported())throw new IllegalStateException("Probe instrumentation missing");
        MEMORY.setThreadAllocatedMemoryEnabled(true);boolean first=true;System.out.print("{\"warmups\":3,\"samples\":20,\"cases\":[");
        for(boolean fragmented:new boolean[]{false,true}) {
            var tree=new SparseVoxelOctree(16);
            if(fragmented)for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)tree.fill(x,y,z,x+1,y+1,z+1,((x+y+z)&1)==0?0xff123456:0xfe345678);
            else tree.fill(9,10,11,10,11,12,0xfe345678);
            for(boolean packedInput:new boolean[]{false,true})for(boolean edit:new boolean[]{false,true}) {
                var source=packedInput?tree.packedCellCopy():tree;
                if(!first)System.out.print(',');first=false;
                System.out.print("{\"fragmented\":"+fragmented+",\"packed_input\":"+packedInput+",\"edit\":"+edit+",\"node_image\":"+measure(source,false,edit)+",\"installed_image\":"+measure(source,true,edit)+"}");
            }
        }
        System.out.println("]}");
    }
}
