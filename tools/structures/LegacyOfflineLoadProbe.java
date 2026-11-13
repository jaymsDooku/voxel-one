package dev.jayms.net;

import dev.jayms.net.model.*;
import java.io.*;
import java.nio.file.*;
import java.lang.management.ManagementFactory;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Modifier;
import java.util.*;

/** Actual legacy private load component probe; not cold-cache/city qualification. */
public final class LegacyOfflineLoadProbe {
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
    public static void main(String[] args)throws Exception {
        Path directory=Path.of(args[0]);Files.createDirectories(directory);MEMORY.setThreadAllocatedMemoryEnabled(true);
        System.out.print("{\"warmups\":3,\"runs\":20,\"cases\":[");boolean firstCase=true;
        for(String fixture:List.of("v8_dense","v9_dense","v8_sparse","v8_nonclear")) {
            int version=fixture.startsWith("v9")?9:8;var models=new ModelLibrary();var rows=new ArrayList<Protocol.Edit>();
            for(int i=0;i<8;i++) {
                int x=i*32-128;rows.add(new Protocol.Edit(x,180,-17,fixture.endsWith("nonclear")?Blocks.STONE:0));
                int leaves=fixture.endsWith("sparse")?1:4096;
                for(int leaf=0;leaf<leaves;leaf++)rows.add(new Protocol.Edit(x,180,-17,Blocks.piece(Blocks.LED,4),4,leaf%16,leaf/256,leaf/16%16,leaf*37));
            }
            if(version==9) {
                var tree=new SparseVoxelOctree(32);tree.set(31,30,29,0xff123456);
                rows.add(new Protocol.Edit(256,180,-17,models.stageStructureModel(new ModelDefinition("Synthetic legacy dependency",tree)).id()));
            }
            var expected=new WorldVoxels.History();rows.forEach(e->WorldVoxels.rememberValidated(expected,e));
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            out.writeInt(version);out.writeLong(123);out.writeInt(Terrain.CURRENT_VERSION);
            if(version==9)models.writeAll(out);else models.write(out);
            new Inventory().write(out);out.writeByte(17);out.writeInt(rows.size());for(var row:rows)row.write(out);out.writeInt(0);out.flush();
            var encoded=bytes.toByteArray();Path path=directory.resolve(fixture+".dat");Files.write(path,encoded);
            if(!firstCase)System.out.print(',');firstCase=false;
            System.out.print("{\"fixture\":\""+fixture+"\",\"records\":"+rows.size()+",\"file_bytes\":"+encoded.length+",\"samples\":[");
            boolean first=true;long retained=0,combined=0;
            for(int run=-3;run<20;run++) {
                long id=Thread.currentThread().getId(),allocation=MEMORY.getThreadAllocatedBytes(id),start=System.nanoTime();
                var game=new LocalGame(path,0);
                long nanos=System.nanoTime()-start,allocated=MEMORY.getThreadAllocatedBytes(id)-allocation;
                if(game.seed!=123||game.health!=17||game.generatorVersion!=Terrain.CURRENT_VERSION||game.edits.size()!=expected.size()
                        ||!new ArrayList<>(game.edits.entrySet()).equals(new ArrayList<>(expected.entrySet()))
                        ||((WorldVoxels.History)game.edits).revision()!=expected.revision())throw new AssertionError("State differs");
                for(var entry:expected.entrySet())if(!entry.getValue().equals(game.edits.get(entry.getKey())))throw new AssertionError("Lookup differs");
                if(!models.entries().stream().map(e->e.definition().fingerprint()).toList().equals(
                        game.models.entries().stream().map(e->e.definition().fingerprint()).toList()))throw new AssertionError("Dependency differs");
                game.save();if(!Arrays.equals(encoded,Files.readAllBytes(path)))throw new AssertionError("Saved bytes differ");
                if(run>=0){if(!first)System.out.print(',');first=false;System.out.print("{\"run\":"+(run+1)+",\"nanos\":"+nanos+",\"allocated_bytes\":"+allocated+"}");}
                if(run==19) {
                    retained=reachable(game.edits,game.models,game.inventory,game.drops);
                    combined=reachable(game.edits,WorldVoxels.History.copyOf(game.edits),game.models,game.inventory,game.drops);
                }
            }
            System.out.print("],\"retained_loaded_state_bytes\":"+retained+",\"retained_with_history_snapshot_bytes\":"+combined+"}");
        }
        System.out.println("],\"checks\":\"ordered history, revisions, keyed reads, dependency fingerprints and complete saved bytes match after every load\",\"qualification\":false}");
    }
}
