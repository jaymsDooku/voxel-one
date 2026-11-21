package dev.jayms.net;

import dev.jayms.net.model.SparseVoxelOctree;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.util.*;

/** Synthetic staged-replay graph and CPU probe; no save, accounts, display or network. */
public final class PrimitiveCellReplayProbe {
    private static Instrumentation sizes;
    private static volatile Object held;
    private static final com.sun.management.ThreadMXBean MEMORY=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    public static void premain(String ignored,Instrumentation instrumentation){sizes=instrumentation;}
    private static long footprint(Object root)throws Exception {
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
    private static String measure(Protocol.CellState state,boolean install)throws Exception {
        long[] nanos=new long[20],allocation=new long[20];long id=Thread.currentThread().getId(),retained=0;
        var replay=WorldVoxels.prepareCellReplay(state);var expected=new WorldVoxels.History();WorldVoxels.rememberCell(expected,state);
        var entries=new ArrayList<>(expected.entrySet());
        for(int run=-3;run<20;run++) {
            var history=install?new WorldVoxels.History():null;
            long before=MEMORY.getThreadAllocatedBytes(id),start=System.nanoTime();
            Object result;
            if(install){WorldVoxels.rememberCell(history,replay);result=history;}
            else result=WorldVoxels.prepareCellReplay(state);
            long elapsed=System.nanoTime()-start,count=MEMORY.getThreadAllocatedBytes(id)-before;held=result;
            if(install&&!entries.equals(new ArrayList<>(history.entrySet())))throw new AssertionError("Replay differs");
            if(!install&&((WorldVoxels.CellReplay)result).editCount()!=replay.editCount())throw new AssertionError("Count differs");
            if(run==-3)retained=footprint(result);
            if(run>=0){nanos[run]=elapsed;allocation[run]=count;}
        }
        return "{\"nanos\":"+Arrays.toString(nanos)+",\"allocated_bytes\":"+Arrays.toString(allocation)+",\"reachable_bytes\":"+retained+"}";
    }
    public static void main(String[] args)throws Exception {
        if(sizes==null||!MEMORY.isThreadAllocatedMemorySupported())throw new IllegalStateException("Probe instrumentation missing");
        MEMORY.setThreadAllocatedMemoryEnabled(true);System.out.print("{\"warmups\":3,\"samples\":20,\"cases\":[");
        for(int fixture=0;fixture<3;fixture++) {
            var tree=new SparseVoxelOctree(16);
            if(fixture==0)tree.fill(9,10,11,10,11,12,0xfeabcdef);
            else if(fixture==1){var random=new Random(60317);for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)tree.fill(x,y,z,x+1,y+1,z+1,0xfe000000|random.nextInt(1<<24));}
            else tree.fill(0,0,0,16,16,16,WorldVoxels.encode(66559));
            var state=new Protocol.CellState(-17,180,-33,tree.freeze());
            var retainedReplay=WorldVoxels.prepareCellReplay(state);
            var stagedHistory=new WorldVoxels.History();var liveHistory=new WorldVoxels.History();
            WorldVoxels.rememberCell(stagedHistory,retainedReplay);WorldVoxels.rememberCell(liveHistory,retainedReplay);
            long combined=footprint(new Object[]{retainedReplay,stagedHistory,liveHistory});
            if(fixture>0)System.out.print(',');
            System.out.print("{\"fixture\":\""+new String[]{"sparse","fragmented","model"}[fixture]+"\",\"records\":"+state.replayEditCount()+",\"replay_and_two_histories_reachable_bytes\":"+combined+",\"prepare\":"+measure(state,false)+",\"install\":"+measure(state,true)+"}");
        }
        System.out.println("]}");
    }
}
