package dev.jayms.net;

import java.lang.management.ManagementFactory;
import java.util.*;

/** Warm history comparison only; no whole-server, network or city qualification. */
public final class HistoryDeltaProbe {
    static final com.sun.management.ThreadMXBean MEMORY=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    static WorldVoxels.History dense(int cells) {
        var history=new WorldVoxels.History();var loader=new KeyedReplayLoader(history,()->false);
        for(int x=0;x<cells;x++) {
            loader.accept(new Protocol.Edit(x,180,-17,0));
            for(int i=0;i<4096;i++)loader.accept(new Protocol.Edit(x,180,-17,Blocks.piece(Blocks.LED,4),4,i%16,i/256,i/16%16,i*37));
        }
        loader.finish();return history;
    }
    static Set<WorldVoxels.CellPosition> rowUnion(WorldVoxels.History current,WorldVoxels.History before) {
        var changed=new LinkedHashSet<WorldVoxels.CellPosition>();
        for(var edit:current.values())if(!edit.equals(before.get(edit.key())))changed.add(new WorldVoxels.CellPosition(edit.x(),edit.y(),edit.z()));
        for(var edit:before.values())if(!edit.equals(current.get(edit.key())))changed.add(new WorldVoxels.CellPosition(edit.x(),edit.y(),edit.z()));
        return Collections.unmodifiableSet(changed);
    }
    public static void main(String[] args) {
        MEMORY.setThreadAllocatedMemoryEnabled(true);System.out.print("{\"warmups\":3,\"runs\":20,\"cases\":[");boolean firstCase=true;
        for(String name:List.of("dense-shared","dense-one-changed","dense-independent","sparse-shared","removed-cell")) {
            var current=name.equals("sparse-shared")?new WorldVoxels.History():dense(8);
            if(name.equals("sparse-shared"))for(int x=0;x<4096;x++){var row=new Protocol.Edit(x,180,-17,Blocks.STONE);current.put(row.key(),row);}
            var before=WorldVoxels.History.copyOf(current);
            if(name.equals("dense-one-changed")) {var row=new Protocol.Edit(0,180,-17,Blocks.piece(Blocks.LED,4),4,1,2,3,0xabcdef);current.put(row.key(),row);}
            if(name.equals("dense-independent"))current=dense(8);
            if(name.equals("removed-cell"))for(var row:new ArrayList<>(current.values()))if(row.x()==0)current.remove(row.key());
            var expected=rowUnion(current,before);
            if(!firstCase)System.out.print(',');firstCase=false;
            System.out.print("{\"fixture\":\""+name+"\",\"current_records\":"+current.size()+",\"before_records\":"+before.size()+",\"changed_cells\":"+expected.size()+",\"samples\":[");boolean first=true;
            for(int run=-3;run<20;run++)for(int order=0;order<2;order++) {
                boolean indexed=((run+order)&1)==0;long tid=Thread.currentThread().getId(),allocated=MEMORY.getThreadAllocatedBytes(tid),start=System.nanoTime();
                var actual=indexed?MultiplayerServer.changedCellPositions(current,before):rowUnion(current,before);
                long nanos=System.nanoTime()-start,bytes=MEMORY.getThreadAllocatedBytes(tid)-allocated;
                if(!expected.equals(actual))throw new AssertionError("Join delta differs");
                if(run>=0){if(!first)System.out.print(',');first=false;System.out.print("{\"run\":"+(run+1)+",\"mode\":\""+(indexed?"indexed":"row-union")+"\",\"nanos\":"+nanos+",\"allocated_bytes\":"+bytes+"}");}
            }
            System.out.print("]}");
        }
        System.out.println("]}");
    }
}
