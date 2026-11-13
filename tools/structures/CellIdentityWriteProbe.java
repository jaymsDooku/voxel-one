package dev.jayms.net;
import java.util.*;
import java.lang.management.ManagementFactory;

/** Exact-parent write cost after a snapshot; no join/whole-server qualification. */
public final class CellIdentityWriteProbe {
    public static void main(String[] args) {
        var memory=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();memory.setThreadAllocatedMemoryEnabled(true);
        var rows=new ArrayList<Protocol.Edit>();rows.add(new Protocol.Edit(0,180,-17,0));
        for(int i=0;i<4096;i++)rows.add(new Protocol.Edit(0,180,-17,Blocks.piece(Blocks.LED,4),4,i%16,i/256,i/16%16,i*37));
        System.out.print("{\"warmups\":3,\"runs\":20,\"cases\":[");boolean firstCase=true;
        for(String name:List.of("ordinary_512_overwrites","compact_512_overwrites")) {
            if(!firstCase)System.out.print(',');firstCase=false;System.out.print("{\"fixture\":\""+name+"\",\"samples\":[");boolean first=true;
            for(int run=-3;run<20;run++) {
                var target=new WorldVoxels.History();
                if(name.startsWith("ordinary"))for(var row:rows)target.put(row.key(),row);
                else if(!target.putNewCanonicalReplay(rows,()->{}))throw new AssertionError();
                var snapshot=WorldVoxels.History.copyOf(target);long id=Thread.currentThread().getId(),allocated=memory.getThreadAllocatedBytes(id),start=System.nanoTime();
                for(int i=1;i<=512;i++){var row=rows.get(i).withColor(0xffffff-rows.get(i).color());target.put(row.key(),row);}
                long nanos=System.nanoTime()-start,bytes=memory.getThreadAllocatedBytes(id)-allocated;
                if(target.size()!=4097||snapshot.size()!=4097||target.revision()!=4609)throw new AssertionError("Count/revision differs");
                for(int i=0;i<rows.size();i++) {
                    var row=rows.get(i);if(!row.equals(snapshot.get(row.key())))throw new AssertionError("Snapshot changed");
                    var expected=i>0&&i<=512?row.withColor(0xffffff-row.color()):row;
                    if(!expected.equals(target.get(row.key())))throw new AssertionError("Overwrite differs");
                }
                if(run>=0){if(!first)System.out.print(',');first=false;System.out.print("{\"run\":"+(run+1)+",\"nanos\":"+nanos+",\"allocated_bytes\":"+bytes+"}");}
            }
            System.out.print("]}");
        }
        System.out.println("]}");
    }
}
