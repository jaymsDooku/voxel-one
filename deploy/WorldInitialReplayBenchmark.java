package dev.jayms;

import dev.jayms.net.*;
import java.lang.management.ManagementFactory;
import java.util.*;

/** Small private-world replay diagnostic, never full-fixture qualification. */
public final class WorldInitialReplayBenchmark {
    private static List<Protocol.Edit> fixture(boolean coarse) {
        var edits=new ArrayList<Protocol.Edit>();
        if(coarse)for(int i=0;i<16384;i++)edits.add(new Protocol.Edit(i%256-128,180,i/256+1024,Blocks.STONE));
        else for(int cell=0;cell<2;cell++)for(int i=0;i<4096;i++)edits.add(new Protocol.Edit(cell,180,0,
                Blocks.piece(i%2==0?Blocks.BRICKS:Blocks.GLASS,4),4,i%16,i/256,i/16%16));
        return edits;
    }
    public static void main(String[] args)throws Exception {
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation measurement unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();boolean first=true;
        System.out.println("{\"fixtures\":{\"dense\":\"2 dense cells,8192 alternating finest-grid pieces\",\"coarse\":\"16384 cells,one whole-cell edit each\"},\"warmups\":1,\"runs\":[");
        for(boolean coarse:new boolean[]{false,true}) {
            var edits=fixture(coarse);
            for(int run=-1;run<5;run++)for(boolean batched:new boolean[]{false,true})try(var world=new World(42)) {
                long allocated=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
                if(batched)world.replayInitial(edits,()->false,n->{});else for(var edit:edits)world.apply(edit);
                long nanos=System.nanoTime()-start,bytes=bean.getThreadAllocatedBytes(thread)-allocated;
                for(var edit:edits)if(world.region(edit)!=edit.type())throw new AssertionError("Replay material/detail");
                if(world.editsSnapshot().size()!=edits.size())throw new AssertionError("Replay history count");
                if(run>=0){if(!first)System.out.println(",");first=false;System.out.print("{\"fixture\":\""+(coarse?"coarse":"dense")+"\",\"run\":"+run+",\"batched\":"+batched+",\"nanoseconds\":"+nanos+",\"thread_allocated_bytes\":"+bytes+"}");}
            }
        }
        System.out.println("\n]}");
    }
}
