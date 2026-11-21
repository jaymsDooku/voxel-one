package dev.jayms.net;

import java.lang.management.ManagementFactory;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Modifier;
import java.util.*;

/** Warm synthetic geometry replay only; no file/network/render qualification. */
public final class InitialGeometryProbe {
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
    static final com.sun.management.ThreadMXBean MEMORY=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    static List<Protocol.Edit> fixture(String name) {
        var rows=new ArrayList<Protocol.Edit>();
        if(name.equals("dense")) {
            for(int cell=0;cell<2;cell++) {
                rows.add(new Protocol.Edit(cell,180,-17,0));
                for(int i=0;i<4096;i++)rows.add(new Protocol.Edit(cell,180,-17,Blocks.piece(Blocks.LED,4),4,i%16,i/256,i/16%16,i*37));
            }
        } else if(name.equals("sparse")) {
            for(int cell=0;cell<64;cell++)rows.add(new Protocol.Edit(cell,0,-17,Blocks.STONE));
        } else {
            for(int i=0;i<512;i++)rows.add(new Protocol.Edit(i%2,180,-17,Blocks.piece(Blocks.GLASS,4),4,i%16,i/256,i/16%16));
        }
        return rows;
    }
    public static void main(String[] args)throws Exception {
        MEMORY.setThreadAllocatedMemoryEnabled(true);System.out.print("{\"warmups\":3,\"runs\":20,\"cases\":[");boolean firstCase=true;
        for(String name:List.of("dense","sparse","alternating")) {
            var rows=fixture(name);var expected=new WorldVoxels(new Terrain(42));rows.forEach(expected::apply);
            var positions=new HashSet<WorldVoxels.CellPosition>();for(var row:rows)positions.add(new WorldVoxels.CellPosition(row.x(),row.y(),row.z()));
            if(!firstCase)System.out.print(',');firstCase=false;
            System.out.print("{\"fixture\":\""+name+"\",\"records\":"+rows.size()+",\"samples\":[");boolean first=true;
            for(int run=-3;run<20;run++)for(int order=0;order<2;order++) {
                boolean current=((run+order)&1)==0;var actual=new WorldVoxels(new Terrain(42));
                long tid=Thread.currentThread().getId(),allocated=MEMORY.getThreadAllocatedBytes(tid),start=System.nanoTime();
                if(current)actual.replayInitial(rows,()->false);else rows.forEach(actual::apply);
                long nanos=System.nanoTime()-start,bytes=MEMORY.getThreadAllocatedBytes(tid)-allocated;
                var chunks=WorldVoxels.class.getDeclaredField("chunks");chunks.setAccessible(true);long retained=reachable(chunks.get(actual));
                for(var p:positions)if(!expected.cell(p.x(),p.y(),p.z()).sameValues(actual.cell(p.x(),p.y(),p.z())))throw new AssertionError("Replay differs");
                if(run>=0){if(!first)System.out.print(',');first=false;System.out.print("{\"run\":"+(run+1)+",\"mode\":\""+(current?"current":"per-row")+"\",\"nanos\":"+nanos+",\"allocated_bytes\":"+bytes+",\"retained_geometry_bytes\":"+retained+"}");}
            }
            System.out.print("]}");
        }
        System.out.println("]}");
    }
}
