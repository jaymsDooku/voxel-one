package dev.jayms.net;

import dev.jayms.net.model.SparseVoxelOctree;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.util.*;

/** Component probe of shared bulk rows, legacy rows, iteration and snapshot costs. */
public final class SharedHistoryProbe {
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
    private static long checksum(Map<String,Protocol.Edit> map){long sum=0;for(var e:map.entrySet())sum=Long.rotateLeft(sum,1)^e.hashCode();return sum;}
    private static String measure(Protocol.CellState state,String mode)throws Exception {
        long[] nanos=new long[20],allocation=new long[20];long id=Thread.currentThread().getId();
        var replay=WorldVoxels.prepareCellReplay(state);var reference=new LinkedHashMap<String,Protocol.Edit>();state.forEachEdit(e->reference.put(e.key(),e));
        var entries=new ArrayList<>(reference.entrySet());long expected=checksum(reference);
        var source=new WorldVoxels.History();WorldVoxels.rememberCell(source,replay);
        for(int run=-3;run<20;run++) {
            var history=mode.equals("install")?new WorldVoxels.History():null;
            long before=MEMORY.getThreadAllocatedBytes(id),start=System.nanoTime();Object result;
            switch(mode) {
                case "prepare" -> result=WorldVoxels.prepareCellReplay(state);
                case "install" -> {WorldVoxels.rememberCell(history,replay);result=history;}
                case "scan" -> result=checksum(source);
                case "snapshot" -> result=WorldVoxels.History.copyOf(source);
                default -> throw new AssertionError("Unknown probe mode");
            }
            long elapsed=System.nanoTime()-start,count=MEMORY.getThreadAllocatedBytes(id)-before;held=result;
            if(history!=null&&!entries.equals(new ArrayList<>(history.entrySet())))throw new AssertionError("Replay differs");
            if(mode.equals("scan")&&((Long)result)!=expected)throw new AssertionError("Scan differs");
            if(mode.equals("snapshot")&&!entries.equals(new ArrayList<>(((WorldVoxels.History)result).entrySet())))throw new AssertionError("Snapshot differs");
            if(run>=0){nanos[run]=elapsed;allocation[run]=count;}
        }
        return "{\"nanos\":"+Arrays.toString(nanos)+",\"allocated_bytes\":"+Arrays.toString(allocation)+"}";
    }
    public static void main(String[] args)throws Exception {
        if(sizes==null||!MEMORY.isThreadAllocatedMemorySupported())throw new IllegalStateException("Probe instrumentation missing");
        MEMORY.setThreadAllocatedMemoryEnabled(true);System.out.print("{\"warmups\":3,\"samples\":20,\"cases\":[");
        for(int fixture=0;fixture<3;fixture++) {
            var tree=new SparseVoxelOctree(16);
            if(fixture==0)tree.fill(9,10,11,10,11,12,0xfeabcdef);
            else if(fixture==1){var random=new Random(60317);for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)tree.fill(x,y,z,x+1,y+1,z+1,0xfe000000|random.nextInt(1<<24));}
            else tree.fill(0,0,0,16,16,16,WorldVoxels.encode(66559));
            var state=new Protocol.CellState(-17,180,-33,tree.freeze());var replay=WorldVoxels.prepareCellReplay(state);
            var staged=new WorldVoxels.History();var live=new WorldVoxels.History();
            WorldVoxels.rememberCell(staged,replay);WorldVoxels.rememberCell(live,replay);
            var legacy=new WorldVoxels.History();state.forEachEdit(e->legacy.put(e.key(),e));
            if(fixture>0)System.out.print(',');
            System.out.print("{\"fixture\":\""+new String[]{"sparse","fragmented","model"}[fixture]+"\",\"records\":"+state.replayEditCount()
                    +",\"replay_and_two_histories_reachable_bytes\":"+footprint(new Object[]{replay,staged,live})+",\"legacy_history_reachable_bytes\":"+footprint(legacy));
            for(var mode:new String[]{"prepare","install","scan","snapshot"})System.out.print(",\""+mode+"\":"+measure(state,mode));
            System.out.print('}');
        }
        var ordinary=new WorldVoxels.History();for(int i=0;i<512;i++){var edit=new Protocol.Edit(i,180,0,Blocks.STONE);ordinary.put(edit.key(),edit);}
        System.out.println("],\"ordinary_512_cell_history_reachable_bytes\":"+footprint(ordinary)+"}");
    }
}
