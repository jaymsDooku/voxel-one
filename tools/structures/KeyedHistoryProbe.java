package dev.jayms.net;

import java.lang.management.ManagementFactory;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Modifier;
import java.util.*;

/** Synthetic history-intake and overlay comparison; not server/city qualification. */
public final class KeyedHistoryProbe {
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
    static List<Protocol.Edit> cell(int x,boolean clear,int leaves) {
        var rows=new ArrayList<Protocol.Edit>();rows.add(new Protocol.Edit(x,180,-17,clear?0:Blocks.STONE));
        for(int i=0;i<leaves;i++)rows.add(new Protocol.Edit(x,180,-17,Blocks.piece(Blocks.LED,4),4,i%16,i/256,i/16%16,i*37));
        return rows;
    }
    static void same(WorldVoxels.History expected,WorldVoxels.History actual) {
        if(expected.revision()!=actual.revision()||!new ArrayList<>(expected.entrySet()).equals(new ArrayList<>(actual.entrySet())))throw new AssertionError("Ordered state/revision differs");
        for(var row:expected.entrySet())if(!row.getValue().equals(actual.get(row.getKey())))throw new AssertionError("Keyed lookup differs");
    }
    public static void main(String[] args)throws Exception {
        boolean current=args[0].equals("current");MEMORY.setThreadAllocatedMemoryEnabled(true);
        System.out.print("{\"warmups\":3,\"runs\":20,\"cases\":[");boolean firstCase=true;
        for(String fixture:List.of("dense","sparse","nonclear","recurring","compact_512_overwrites")) {
            boolean overlay=fixture.startsWith("compact");var rows=new ArrayList<Protocol.Edit>();
            if(overlay)for(var row:cell(-17,true,4096).subList(1,513))rows.add(row.withColor(0xffffff-row.color()));
            else {
                int cells=fixture.equals("recurring")?2:8,leaves=fixture.equals("sparse")?1:4096;
                for(int i=0;i<cells;i++)rows.addAll(cell(i*32-128,!fixture.equals("nonclear"),leaves));
                if(fixture.equals("recurring"))for(int i=0;i<4097;i++)for(int j=0;j<2;j++) {
                    var row=rows.get(j*4097+i);
                    rows.add(row.depth()==0?row:row.withColor(0xffffff-row.color()));
                }
            }
            var expected=new WorldVoxels.History();if(overlay)for(var row:cell(-17,true,4096))expected.put(row.key(),row);
            for(var row:rows)expected.put(row.key(),row);
            if(!firstCase)System.out.print(',');firstCase=false;
            System.out.print("{\"fixture\":\""+fixture+"\",\"input_records\":"+rows.size()+",\"final_rows\":"+expected.size()+",\"samples\":[");
            boolean first=true;long retained=0,combined=0,cleanup=0;
            for(int run=-3;run<20;run++) {
                var target=new WorldVoxels.History();WorldVoxels.History snapshot=null;
                if(overlay) {if(!target.putNewCanonicalReplay(cell(-17,true,4096),()->{}))throw new AssertionError();snapshot=WorldVoxels.History.copyOf(target);}
                long id=Thread.currentThread().getId(),allocation=MEMORY.getThreadAllocatedBytes(id),start=System.nanoTime();
                if(current&&!overlay){var loader=new KeyedReplayLoader(target,()->false);for(var row:rows){if(!row.valid())throw new AssertionError();loader.accept(row);}loader.finish();}
                else for(var row:rows){if(!row.valid())throw new AssertionError();target.put(row.key(),row);}
                long nanos=System.nanoTime()-start,bytes=MEMORY.getThreadAllocatedBytes(id)-allocation;same(expected,target);
                if(snapshot!=null&&snapshot.size()!=4097)throw new AssertionError("Snapshot changed");
                if(run==19){retained=reachable(target);combined=reachable(target,snapshot);}
                if(overlay) {
                    var oracle=new WorldVoxels.History();for(var row:expected.values())oracle.put(row.key(),row);
                    var edit=new Protocol.Edit(-17,180,-17,Blocks.STONE);WorldVoxels.remember(target,edit);WorldVoxels.remember(oracle,edit);
                    if(!new ArrayList<>(oracle.entrySet()).equals(new ArrayList<>(target.entrySet())))throw new AssertionError("Coverage differs");
                    if(snapshot.size()!=4097||snapshot.get(rows.get(0).key()).equals(rows.get(0)))throw new AssertionError("Snapshot changed");
                    if(run==19)cleanup=reachable(target);
                }
                if(run>=0){if(!first)System.out.print(',');first=false;System.out.print("{\"run\":"+(run+1)+",\"nanos\":"+nanos+",\"allocated_bytes\":"+bytes+"}");}
            }
            System.out.print("],\"retained_history_bytes\":"+retained+",\"retained_with_before_snapshot_bytes\":"+combined+",\"retained_after_full_coverage_bytes\":"+cleanup+"}");
        }
        System.out.println("],\"checks\":\"exact ordered rows/revisions/keyed reads, old snapshots and later coverage passed\",\"qualification\":false}");
    }
}
