package dev.jayms;

import dev.jayms.net.*;
import java.lang.management.ManagementFactory;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Modifier;
import java.util.*;

/** Private startup component measurement; not city or frame qualification. */
public final class WorldStartupImageProbe {
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
    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception {
        MEMORY.setThreadAllocatedMemoryEnabled(true);
        System.out.print("{\"warmups\":3,\"runs\":20,\"cases\":[");boolean firstCase=true;
        for(String fixture:List.of("dense","sparse","legacy")) {
            var rows=new ArrayList<Protocol.Edit>();int cells=8;
            for(int i=0;i<cells;i++) {
                int x=i*32-128;
                rows.add(new Protocol.Edit(x,180,-17,fixture.equals("legacy")?Blocks.STONE:0));
                int leaves=fixture.equals("sparse")?1:4096;
                for(int leaf=0;leaf<leaves;leaf++)rows.add(new Protocol.Edit(x,180,-17,
                        Blocks.piece(Blocks.LED,4),4,leaf%16,leaf/256,leaf/16%16,leaf*37));
            }
            var source=new WorldVoxels.History();var writer=new WorldVoxels.ReplayWriter(source,()->false);
            rows.forEach(writer::accept);writer.finish();var captured=WorldVoxels.History.copyOf(source);
            var field=World.class.getDeclaredField("edits");field.setAccessible(true);
            var editedField=World.class.getDeclaredField("editedCells");editedField.setAccessible(true);
            var expected=new World(42);captured.values().forEach(expected::apply);
            var expectedRows=expected.editsSnapshot();long check=checksum(expectedRows);
            if(!firstCase)System.out.print(',');firstCase=false;
            System.out.print("{\"fixture\":\""+fixture+"\",\"rows\":"+rows.size()+",\"samples\":[");
            boolean first=true;long retained=0,combined=0;
            long expectedQuery=0;
            for(int i=0;i<256;i++)expectedQuery+=expected.cell((i%cells)*32-128,180,-17).get(i%16,(i/16)%16,(i*7)%16);
            for(int run=-3;run<20;run++) {
                try(var world=new World(42)) {
                    long id=Thread.currentThread().getId(),allocation=MEMORY.getThreadAllocatedBytes(id),start=System.nanoTime();
                    world.replayInitial(captured.values(),()->false,n->{});
                    long nanos=System.nanoTime()-start,bytes=MEMORY.getThreadAllocatedBytes(id)-allocation;
                    var snapshot=world.editsSnapshot();
                    if(checksum(snapshot)!=check||!new ArrayList<>(snapshot.entrySet()).equals(new ArrayList<>(expectedRows.entrySet()))
                            ||world.editsVersion()!=expected.editsVersion())throw new AssertionError("Replay differs");
                    var histories=(Map<ChunkPos,Map<String,Protocol.Edit>>)field.get(world);
                    var expectedHistories=(Map<ChunkPos,Map<String,Protocol.Edit>>)field.get(expected);
                    for(var entry:histories.entrySet())if(((WorldVoxels.History)entry.getValue()).revision()!=
                            ((WorldVoxels.History)expectedHistories.get(entry.getKey())).revision())throw new AssertionError("Revision differs");
                    for(int i=0;i<cells;i++)if(!world.cell(i*32-128,180,-17).sameValues(expected.cell(i*32-128,180,-17)))
                        throw new AssertionError("Geometry differs");
                    long queryAllocation=MEMORY.getThreadAllocatedBytes(id),queryStart=System.nanoTime(),query=0;
                    for(int i=0;i<256;i++)query+=world.cell((i%cells)*32-128,180,-17).get(i%16,(i/16)%16,(i*7)%16);
                    long queryNanos=System.nanoTime()-queryStart,queryBytes=MEMORY.getThreadAllocatedBytes(id)-queryAllocation;
                    if(query!=expectedQuery)throw new AssertionError("Query differs");
                    if(run>=0){if(!first)System.out.print(',');first=false;System.out.print("{\"run\":"+(run+1)+",\"nanos\":"+nanos+",\"allocated_bytes\":"+bytes+",\"query_nanos\":"+queryNanos+",\"query_allocated_bytes\":"+queryBytes+"}");}
                    if(run==19) {
                        var canonical=new ArrayList<Object>();
                        for(int i=0;i<cells;i++)canonical.add(world.cell(i*32-128,180,-17));
                        Object edited=editedField.get(world);
                        retained=reachable(histories,edited,canonical);
                        combined=reachable(source,captured,histories,edited,canonical);
                    }
                }
            }
            expected.close();System.out.print("],\"retained_world_cell_owners_bytes\":"+retained+",\"retained_combined_cell_owners_bytes\":"+combined+"}");
        }
        System.out.println("],\"checks\":\"exact ordered rows, history revisions, world revision and complete cell geometry match ordinary replay\",\"qualification\":false}");
    }
}
