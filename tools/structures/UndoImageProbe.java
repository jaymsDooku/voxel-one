package dev.jayms.net.structure;

import dev.jayms.net.WorldVoxels;
import dev.jayms.net.model.SparseVoxelOctree;
import java.lang.management.ManagementFactory;
import java.util.*;
import static dev.jayms.net.structure.StructurePackage.*;

/** Undo-storage component only; excludes world storage, commit, GPU and network. */
public final class UndoImageProbe {
    static volatile Object retained;
    static volatile long consumed;
    static final int COUNT=50000;
    static final StructureEngine.Footprint BOX=new StructureEngine.Footprint(-256,128,-128,512,1,128,0,new Position(-256,128,-128));
    static int color(int index,boolean mixed){return mixed?0xfe000000|(index*37&0xffffff):WorldVoxels.encode(index%2==0?3:4);}
    static Map<Position,SparseVoxelOctree> fixture(boolean mixed)throws Exception {
        var builder=new StructurePlacementCells.Builder(BOX,COUNT);var uniform=new SparseVoxelOctree[2];
        for(int i=0;i<COUNT;i++) {
            SparseVoxelOctree tree;
            if(mixed) {
                tree=new SparseVoxelOctree(16);tree.fill(1,2,3,2,3,4,color(i,true));tree.fill(14,13,12,15,14,13,WorldVoxels.encode(3));tree.freeze();
            } else {
                int type=i%2;tree=uniform[type];
                if(tree==null){tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,color(i,false));uniform[type]=tree.freeze();}
            }
            builder.add(new Position(BOX.minX()+i%512,128,BOX.minZ()+i/512),tree);
        }
        return builder.build();
    }
    static long heap()throws Exception {System.gc();Thread.sleep(100);System.gc();Thread.sleep(100);return Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();}
    static Object storage(boolean packed,boolean mixed)throws Exception {
        var source=fixture(mixed);return packed?UndoPrototypeImage.capture(source,BOX,()->false):source;
    }
    static void check(Position p,SparseVoxelOctree tree,boolean mixed) {
        int index=(p.z()-BOX.minZ())*512+p.x()-BOX.minX();
        if(p.y()!=128||index<0||index>=COUNT||tree.get(1,2,3)!=color(index,mixed)||tree.occupied()!=(mixed?2:4096))throw new AssertionError("Undo physical values changed");
        consumed+=tree.get(1,2,3);
    }
    @SuppressWarnings("unchecked") static void visit(Object image,boolean packed,boolean mixed) {
        if(packed) {
            var snapshot=(UndoPrototypeImage)image;
            for(int i=0;i<snapshot.size();i++)check(snapshot.position(i),snapshot.tree(i),mixed);
        } else for(var entry:((Map<Position,SparseVoxelOctree>)image).entrySet())check(entry.getKey(),entry.getValue(),mixed);
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=1||!List.of("map","packed").contains(args[0]))throw new IllegalArgumentException("map or packed required");
        boolean packed=args[0].equals("packed");var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");bean.setThreadAllocatedMemoryEnabled(true);
        for(boolean mixed:new boolean[]{false,true}) {
            retained=storage(packed,mixed);retained=null;long baseline=heap();retained=storage(packed,mixed);long retainedBytes=heap()-baseline;
            var source=fixture(mixed);double[][] times=new double[2][20];long[][] allocations=new long[2][20];
            for(int run=-3;run<20;run++) {
                long before=bean.getCurrentThreadAllocatedBytes(),start=System.nanoTime();
                Object image=packed?UndoPrototypeImage.capture(source,BOX,()->false):source;
                double buildMs=(System.nanoTime()-start)/1e6;long buildBytes=bean.getCurrentThreadAllocatedBytes()-before;
                before=bean.getCurrentThreadAllocatedBytes();start=System.nanoTime();visit(image,packed,mixed);
                double visitMs=(System.nanoTime()-start)/1e6;long visitBytes=bean.getCurrentThreadAllocatedBytes()-before;
                if(run>=0){times[0][run]=buildMs;allocations[0][run]=buildBytes;times[1][run]=visitMs;allocations[1][run]=visitBytes;}
            }
            for(int mode=0;mode<2;mode++) {
                Arrays.sort(times[mode]);Arrays.sort(allocations[mode]);
                System.out.printf(Locale.ROOT,"{\"storage\":\"%s\",\"fixture\":\"%s\",\"cells\":50000,\"operation\":\"%s\",\"runs\":20,\"warmups\":3,\"retained_heap_delta_bytes\":%d,\"median_ms\":%.3f,\"p95_ms\":%.3f,\"worst_ms\":%.3f,\"median_thread_allocated_bytes\":%d}%n",args[0],mixed?"mixed":"shared_uniform",mode==0?"retain_or_capture":"visit",retainedBytes,(times[mode][9]+times[mode][10])/2,times[mode][18],times[mode][19],(allocations[mode][9]+allocations[mode][10])/2);
            }
            retained=null;
        }
    }
}
