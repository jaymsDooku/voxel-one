package dev.jayms.net.structure;

import dev.jayms.net.Terrain;
import dev.jayms.net.WorldVoxels;
import java.util.*;

/** Real WorldVoxels retained with candidate undo storage; no frame/network qualification. */
public final class UndoLiveRetentionProbe {
    static volatile Object retained;
    static long heap()throws Exception {System.gc();Thread.sleep(100);System.gc();Thread.sleep(100);return Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();}
    static Object live(String mode,boolean mixed)throws Exception {
        var source=UndoImageProbe.fixture(mixed);var world=new WorldVoxels(new Terrain(42));
        for(var e:source.entrySet()){var p=e.getKey();world.replaceCell(p.x(),p.y(),p.z(),e.getValue());}
        Object undo=mode.equals("map")?source:mode.equals("packed")?UndoPrototypeImage.capture(source,UndoImageProbe.BOX,()->false):null;
        return new Object[]{world,undo};
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=1||!List.of("world","map","packed").contains(args[0]))throw new IllegalArgumentException("world, map or packed required");
        for(boolean mixed:new boolean[]{false,true}) {
            retained=live(args[0],mixed);retained=null;
            for(int run=1;run<=3;run++) {
                long baseline=heap();retained=live(args[0],mixed);long delta=heap()-baseline;
                var world=(WorldVoxels)((Object[])retained)[0];
                if(world.cell(-256,128,-128).get(1,2,3)!=UndoImageProbe.color(0,mixed))throw new AssertionError("World changed");
                System.out.printf(Locale.ROOT,"{\"storage\":\"%s\",\"fixture\":\"%s\",\"cells\":50000,\"run\":%d,\"retained_heap_delta_bytes\":%d}%n",args[0],mixed?"mixed":"shared_uniform",run,delta);
                retained=null;
            }
        }
    }
}
