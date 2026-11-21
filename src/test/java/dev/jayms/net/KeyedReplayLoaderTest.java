package dev.jayms.net;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KeyedReplayLoaderTest {
    static WorldVoxels.History puts(List<Protocol.Edit> rows) {
        var result=new WorldVoxels.History();for(var row:rows)result.put(row.key(),row);return result;
    }
    static void same(WorldVoxels.History expected,WorldVoxels.History actual) {
        assertEquals(new ArrayList<>(expected.entrySet()),new ArrayList<>(actual.entrySet()));
        assertEquals(expected.revision(),actual.revision());
        for(var row:expected.entrySet())assertEquals(row.getValue(),actual.get(row.getKey()));
    }
    @Test void firstAndReturningCellsKeepExactPutOrderRevisionAndSnapshotIndependence() {
        var rows=new ArrayList<>(RegionReplayLoaderTest.fragmented(-17));
        rows.add(new Protocol.Edit(64,180,0,Blocks.STONE));rows.addAll(RegionReplayLoaderTest.fragmented(32));
        rows.add(new Protocol.Edit(-17,180,-1,0)); // Pure put must retain every earlier fractional row.
        rows.add(new Protocol.Edit(-17,180,-1,0,2,0,0,0));
        rows.add(new Protocol.Edit(-17,180,-1,Blocks.piece(Blocks.GLASS,4),4,0,0,0));
        var actual=new WorldVoxels.History();var loader=new KeyedReplayLoader(actual,()->false);
        rows.forEach(loader::accept);loader.finish();var expected=puts(rows);same(expected,actual);
        assertEquals(8196,actual.size());
        var before=WorldVoxels.History.copyOf(actual);var saved=new ArrayList<>(before.entrySet());
        var next=new Protocol.Edit(-17,180,-1,0,1,0,0,0);
        WorldVoxels.remember(actual,next);WorldVoxels.remember(expected,next);same(expected,actual);
        assertEquals(saved,new ArrayList<>(before.entrySet()));rows.clear();assertEquals(saved,new ArrayList<>(before.entrySet()));
    }
    @Test void overlapsDuplicatesNonclearAndLongSequencesMatchIndependentLinkedMap()throws Exception {
        var random=new Random(60711);var rows=new ArrayList<Protocol.Edit>();
        for(int i=0;i<12000;i++) {
            int depth=i%97==0?0:1+random.nextInt(4),n=1<<depth;
            int type=i%97==0?0:Blocks.piece(Blocks.LED,depth);
            rows.add(new Protocol.Edit(i%7==0?32:-17,180,-1,type,depth,random.nextInt(n),random.nextInt(n),random.nextInt(n),type==0?0:random.nextInt(1<<24)));
        }
        var expected=new LinkedHashMap<String,Protocol.Edit>();var actual=new WorldVoxels.History();var loader=new KeyedReplayLoader(actual,()->false);
        var pending=KeyedReplayLoader.class.getDeclaredField("pending");pending.setAccessible(true);
        for(var row:rows){expected.put(row.key(),row);loader.accept(row);assertTrue(((List<?>)pending.get(loader)).size()<=4097);}
        loader.finish();assertEquals(new ArrayList<>(expected.entrySet()),new ArrayList<>(actual.entrySet()));assertEquals(rows.size(),actual.revision());
        var sameCell=new ArrayList<Protocol.Edit>();sameCell.add(new Protocol.Edit(0,180,0,0));
        for(int i=0;i<9000;i++)sameCell.add(new Protocol.Edit(0,180,0,Blocks.piece(Blocks.LED,4),4,i%16,0,0,i));
        var target=new WorldVoxels.History();var longLoader=new KeyedReplayLoader(target,()->false);
        for(var row:sameCell){longLoader.accept(row);assertTrue(((List<?>)pending.get(longLoader)).size()<=4097);}
        longLoader.finish();same(puts(sameCell),target);
    }
    @Test void existingCellCannotBeReplacedAndCancelledEligibilityLeavesPrivateTargetUnchanged() {
        var rows=RegionReplayLoaderTest.fragmented(-17);var target=new WorldVoxels.History();
        var prefix=new Protocol.Edit(-17,180,-1,Blocks.piece(Blocks.GLASS,1),1,0,0,0);
        target.put(prefix.key(),prefix);assertFalse(target.putNewCanonicalReplay(rows,()->{}));assertEquals(1,target.size());
        var loader=new KeyedReplayLoader(target,()->false);rows.forEach(loader::accept);loader.finish();
        var expected=puts(List.of(prefix));for(var row:rows)expected.put(row.key(),row);same(expected,target);
        var fresh=new WorldVoxels.History();var cancelled=new AtomicBoolean();var pending=new KeyedReplayLoader(fresh,cancelled::get);
        rows.forEach(pending::accept);cancelled.set(true);assertThrows(CancellationException.class,pending::finish);assertTrue(fresh.isEmpty());
        var invalid=new KeyedReplayLoader(fresh,()->false);
        assertThrows(IllegalArgumentException.class,()->invalid.accept(new Protocol.Edit(0,180,0,0,0,0,0,0,1)));assertTrue(fresh.isEmpty());
    }
    @Test void fullRecurringGroupsAndAlternatingOverwritesRemainIndependentThroughCoverageRemoval() {
        var first=RegionReplayLoaderTest.fragmented(-17);var second=RegionReplayLoaderTest.fragmented(32);
        var actual=new WorldVoxels.History();var loader=new KeyedReplayLoader(actual,()->false);
        var expected=puts(List.of());
        for(var group:List.of(first,second))for(var row:group){loader.accept(row);expected.put(row.key(),row);}
        loader.finish();var snapshot=WorldVoxels.History.copyOf(actual);var before=new ArrayList<>(snapshot.entrySet());
        var updates=new KeyedReplayLoader(actual,()->false);
        for(int i=0;i<4097;i++)for(var group:List.of(first,second)) {
            var row=group.get(i);if(row.depth()!=0)row=row.withColor(0xffffff-row.color());
            updates.accept(row);expected.put(row.key(),row);
        }
        updates.finish();same(expected,actual);assertEquals(before,new ArrayList<>(snapshot.entrySet()));
        var overwritten=WorldVoxels.History.copyOf(actual);var overwrittenRows=new ArrayList<>(overwritten.entrySet());
        for(var edit:List.of(new Protocol.Edit(-17,180,-1,0,1,0,0,0),new Protocol.Edit(32,180,-1,Blocks.STONE))) {
            WorldVoxels.remember(expected,edit);WorldVoxels.remember(actual,edit);
        }
        same(expected,actual);assertEquals(overwrittenRows,new ArrayList<>(overwritten.entrySet()));
        assertEquals(before,new ArrayList<>(snapshot.entrySet()));
    }

}
