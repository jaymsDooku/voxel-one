package dev.jayms;

import dev.jayms.net.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldInitialReplayHistoryTest {
    static List<Protocol.Edit> fixture() {
        var rows=new ArrayList<Protocol.Edit>();
        for(int x:new int[]{-17,32,-17}) {
            rows.add(new Protocol.Edit(x,180,-1,0));
            var leaves=new ArrayList<Protocol.Edit>();
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int ix=0;ix<16;ix++)
                leaves.add(new Protocol.Edit(x,180,-1,Blocks.piece(Blocks.LED,4),4,ix,y,z,ix+16*z+256*y));
            Collections.shuffle(leaves,new Random(x));rows.addAll(leaves);
        }
        rows.add(new Protocol.Edit(33,180,-1,Blocks.STONE));
        rows.add(new Protocol.Edit(33,180,-1,Blocks.piece(Blocks.GLASS,1),1,0,0,0));
        rows.add(new Protocol.Edit(32,180,-1,0,2,0,0,0));
        return rows;
    }
    @SuppressWarnings("unchecked")
    static Map<ChunkPos,Map<String,Protocol.Edit>> histories(World world)throws Exception {
        var field=World.class.getDeclaredField("edits");field.setAccessible(true);
        return (Map<ChunkPos,Map<String,Protocol.Edit>>)field.get(world);
    }
    static void same(World expected,World actual)throws Exception {
        assertEquals(expected.editsVersion(),actual.editsVersion());
        var a=histories(expected);var b=histories(actual);
        assertEquals(new ArrayList<>(a.keySet()),new ArrayList<>(b.keySet()));
        for(var key:a.keySet()) {
            assertEquals(new ArrayList<>(a.get(key).entrySet()),new ArrayList<>(b.get(key).entrySet()));
            assertEquals(((WorldVoxels.History)a.get(key)).revision(),((WorldVoxels.History)b.get(key)).revision());
            for(var row:a.get(key).entrySet())assertEquals(row.getValue(),b.get(key).get(row.getKey()));
        }
        for(int x:new int[]{-17,32,33})assertTrue(expected.cell(x,180,-1).sameValues(actual.cell(x,180,-1)));
        assertEquals(expected.requiresSkyCeiling(32,-1),actual.requiresSkyCeiling(32,-1));
    }
    @Test void compactAndLegacyRunsMatchOwnerReplayThroughLaterEditsAndSnapshots()throws Exception {
        var rows=fixture();var progress=new AtomicInteger();
        try(var expected=new World(42);var actual=new World(42)) {
            rows.forEach(expected::apply);actual.replayInitial(rows,()->false,progress::set);
            assertEquals(rows.size(),progress.get());same(expected,actual);
            var before=actual.editsSnapshot();var saved=new LinkedHashMap<>(before);
            for(var edit:List.of(new Protocol.Edit(-17,180,-1,0,1,0,0,0),new Protocol.Edit(32,180,-1,Blocks.BRICKS))) {
                expected.apply(edit);actual.apply(edit);
            }
            same(expected,actual);assertEquals(saved,before);
            assertThrows(IllegalStateException.class,()->actual.replayInitial(List.of(),()->false,i->{}));
        }
    }
    @Test void cancellationAtFinalFlushAndInvalidRowsCannotCompleteAWorld()throws Exception {
        var rows=fixture().subList(0,4097);var progress=new AtomicInteger();
        try(var world=new World(42)) {
            assertThrows(CancellationException.class,()->world.replayInitial(rows,
                    ()->progress.get()==4096,progress::set));
        }
        try(var world=new World(42)) {
            assertThrows(IllegalArgumentException.class,()->world.replayInitial(
                    List.of(new Protocol.Edit(0,180,0,0,0,0,0,0,1)),()->false,i->{}));
        }
    }
    @Test void publicWriterValidatesLifecycleAndMatchesGenericFallback() {
        var rows=fixture().subList(0,4097);var expected=new WorldVoxels.History();rows.forEach(e->WorldVoxels.remember(expected,e));
        for(Map<String,Protocol.Edit> target:List.<Map<String,Protocol.Edit>>of(new WorldVoxels.History(),new LinkedHashMap<>())) {
            var writer=new WorldVoxels.ReplayWriter(target,()->false);rows.forEach(writer::accept);writer.finish();
            assertEquals(new ArrayList<>(expected.entrySet()),new ArrayList<>(target.entrySet()));
            assertThrows(IllegalStateException.class,writer::finish);
            assertThrows(IllegalStateException.class,()->writer.accept(rows.get(0)));
        }
        var target=new WorldVoxels.History();var writer=new WorldVoxels.ReplayWriter(target,()->false);
        assertThrows(IllegalArgumentException.class,()->writer.accept(new Protocol.Edit(0,180,0,0,0,0,0,0,1)));
        assertTrue(target.isEmpty());
    }
}
