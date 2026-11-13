package dev.jayms.net;

import java.lang.management.ManagementFactory;
import java.util.*;

/** Small replay microbenchmark, never city or maximum-package qualification. */
public final class ValidatedReplayBenchmark {
    public static void main(String[] args) {
        var edits=new ArrayList<Protocol.Edit>();
        for(int cell=0;cell<8;cell++)for(int i=0;i<4096;i++)
            edits.add(new Protocol.Edit(cell,128,0,Blocks.piece(Blocks.STONE,4),4,i%16,i/256,i/16%16));
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation measurement unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        System.out.println("{\"fixture\":\"8 dense cells, 32768 valid finest-grid records\",\"warmups\":2,\"runs\":[");
        boolean first=true;
        for(int run=-2;run<5;run++)for(boolean fast:new boolean[]{false,true}) {
            var history=new WorldVoxels.History();long allocated=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
            for(var edit:edits)if(fast)WorldVoxels.rememberValidated(history,edit);else WorldVoxels.remember(history,edit);
            long elapsed=System.nanoTime()-start,bytes=bean.getThreadAllocatedBytes(thread)-allocated;
            if(!edits.equals(new ArrayList<>(history.values())))throw new AssertionError("Replay order/content");
            if(run>=0){if(!first)System.out.println(",");first=false;System.out.print("{\"run\":"+run+",\"validated\":"+fast+",\"nanoseconds\":"+elapsed+",\"allocated_bytes\":"+bytes+"}");}
        }
        System.out.println("\n]}");
    }
}
