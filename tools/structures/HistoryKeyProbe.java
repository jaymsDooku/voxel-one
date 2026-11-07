import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.lang.management.ManagementFactory;
import java.security.*;
import java.io.*;
import java.util.*;

/** History key CPU/allocation and live-world heap estimates; not full qualification. */
public final class HistoryKeyProbe {
    static final int CELLS=50_000;
    static volatile Object retained;
    static int x(int i){return i%512-256;}
    static int z(int i){return i/512-128;}
    static int color(int i){return 0xfe000000|((i*1664525+1013904223)&0xffffff);}
    static SparseVoxelOctree tree(int i,boolean mixed) {
        var tree=new SparseVoxelOctree(16);
        if(mixed){tree.fill(1,2,3,2,3,4,color(i));tree.fill(14,13,12,15,14,13,WorldVoxels.encode(Blocks.STONE));}
        else tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        return tree.freeze();
    }
    static String hash(WorldVoxels.History history)throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");
        try(var out=new DataOutputStream(new BufferedOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),digest),65536))) {
            for(var edit:history.values())edit.write(out);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    static WorldVoxels.History build(List<Protocol.CellState> cells) {
        var history=new WorldVoxels.History();for(var cell:cells)WorldVoxels.rememberCell(history,cell);return history;
    }
    static void cpu(String implementation,boolean mixed)throws Exception {
        var cells=new ArrayList<Protocol.CellState>();var uniform=tree(0,false);
        for(int i=0;i<CELLS;i++)cells.add(new Protocol.CellState(x(i),128,z(i),mixed?tree(i,true):uniform));
        var reference=build(cells);String expected=hash(reference);int edits=reference.size();reference=null;
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);double[] times=new double[20];long[] allocated=new long[20];
        for(int run=-3;run<20;run++) {
            long before=bean.getCurrentThreadAllocatedBytes(),start=System.nanoTime();var history=build(cells);
            double ms=(System.nanoTime()-start)/1e6;long bytes=bean.getCurrentThreadAllocatedBytes()-before;
            if(history.size()!=edits||!expected.equals(hash(history)))throw new AssertionError("Replay changed");
            retained=history;if(run>=0){times[run]=ms;allocated[run]=bytes;}
        }
        Arrays.sort(times);Arrays.sort(allocated);retained=null;
        System.out.printf(Locale.ROOT,"{\"implementation\":\"%s\",\"fixture\":\"%s\",\"cells\":50000,\"edits\":%d,\"replay_sha256\":\"%s\",\"warmups\":3,\"runs\":20,\"median_ms\":%.3f,\"p95_ms\":%.3f,\"worst_ms\":%.3f,\"median_thread_allocated_bytes\":%d}%n",implementation,mixed?"mixed":"uniform",edits,expected,(times[9]+times[10])/2,times[18],times[19],(allocated[9]+allocated[10])/2);
    }
    static Object live(boolean mixed,boolean copy) {
        var world=new WorldVoxels(new Terrain(42));var history=new WorldVoxels.History();var uniform=tree(0,false);
        for(int i=0;i<CELLS;i++) {
            var tree=mixed?tree(i,true):uniform;
            world.replaceCell(x(i),128,z(i),tree);WorldVoxels.rememberCell(history,new Protocol.CellState(x(i),128,z(i),tree));
        }
        return new Object[]{world,history,copy?WorldVoxels.History.copyOf(history):null};
    }
    static long heap()throws Exception {
        System.gc();Thread.sleep(100);System.gc();Thread.sleep(100);
        return Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
    }
    static long sample(boolean mixed,boolean copy)throws Exception {
        long baseline=heap();retained=live(mixed,copy);long bytes=heap()-baseline;
        var values=(Object[])retained;var world=(WorldVoxels)values[0];var history=(WorldVoxels.History)values[1];
        if(world.cell(x(0),128,z(0)).get(mixed?1:0,mixed?2:0,mixed?3:0)!=(mixed?color(0):WorldVoxels.encode(Blocks.STONE))
                ||history.size()!=CELLS*(mixed?3:1))throw new AssertionError("Retained world/history changed");
        if(copy&&!hash(history).equals(hash((WorldVoxels.History)values[2])))throw new AssertionError("Copy replay changed");
        retained=null;return bytes;
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=2||!List.of("cpu","heap","heap-copy").contains(args[0]))throw new IllegalArgumentException("cpu|heap|heap-copy label required");
        for(boolean mixed:new boolean[]{false,true}) {
            if(args[0].equals("cpu")){cpu(args[1],mixed);continue;}
            boolean copy=args[0].equals("heap-copy");sample(mixed,copy);
            for(int run=1;run<=3;run++)System.out.printf(Locale.ROOT,"{\"implementation\":\"%s\",\"fixture\":\"%s\",\"retained\":\"%s\",\"cells\":50000,\"run\":%d,\"heap_delta_bytes\":%d}%n",args[1],mixed?"mixed":"uniform",copy?"world_history_and_copy":"world_and_history",run,sample(mixed,copy));
        }
    }
}
