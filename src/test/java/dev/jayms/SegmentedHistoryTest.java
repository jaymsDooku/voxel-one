package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SegmentedHistoryTest {
    private static void equal(Map<String,Protocol.Edit> reference,WorldVoxels.History actual) {
        assertEquals(new ArrayList<>(reference.entrySet()),new ArrayList<>(actual.entrySet()));assertEquals(reference.size(),actual.size());
        for(var entry:reference.entrySet()){assertEquals(entry.getValue(),actual.get(entry.getKey()));assertTrue(actual.containsKey(entry.getKey()));}
    }
    private static Protocol.CellState state(int x,int z,Random random) {
        var tree=new SparseVoxelOctree(16);
        for(int i=0;i<24;i++){int a=random.nextInt(16),b=random.nextInt(16),c=random.nextInt(16);tree.fill(a,b,c,a+1,b+1,c+1,0xfe000000|random.nextInt(1<<24));}
        return new Protocol.CellState(x,180,z,tree);
    }
    @Test void mixedBulkAndOrdinaryMapOperationsMatchInsertionOrderedReferenceAcrossForks() {
        var random=new Random(96031);var actual=new ArrayList<WorldVoxels.History>();var expected=new ArrayList<LinkedHashMap<String,Protocol.Edit>>();
        actual.add(new WorldVoxels.History());expected.add(new LinkedHashMap<>());
        for(int step=0;step<350;step++) {
            int branch=random.nextInt(actual.size());var a=actual.get(branch);var e=expected.get(branch);
            int x=random.nextInt(4)-2,z=random.nextInt(3)-1;
            if(step%19==0&&actual.size()<6){actual.add(WorldVoxels.History.copyOf(a));expected.add(new LinkedHashMap<>(e));}
            else if(step%7==0){var s=state(x,z,random);WorldVoxels.rememberCell(e,s);WorldVoxels.rememberCell(a,WorldVoxels.prepareCellReplay(s));}
            else if(step%13==0){a.clear();e.clear();}
            else {
                int d=random.nextInt(5),side=1<<d;var edit=new Protocol.Edit(x,180,z,Blocks.piece(Blocks.LED,d),d,random.nextInt(side),random.nextInt(side),random.nextInt(side),random.nextInt(1<<24));
                if(step%3==0&&!e.isEmpty()) {
                    var old=new ArrayList<>(e.values()).get(random.nextInt(e.size()));
                    assertEquals(e.remove(old.key()),a.remove(old.key()));
                }else if(step%3==1){assertEquals(e.put(edit.key(),edit),a.put(edit.key(),edit));}
                else {WorldVoxels.remember(e,edit);WorldVoxels.remember(a,edit);}
            }
            for(int i=0;i<actual.size();i++)equal(expected.get(i),actual.get(i));
        }
    }
    @Test void groupOverridesTombstonesAndReinsertionPreserveGlobalOrder() {
        var tree=new SparseVoxelOctree(16);var random=new Random(509);
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)tree.fill(x,y,z,x+1,y+1,z+1,0xfe000000|random.nextInt(1<<24));
        var state=new Protocol.CellState(-17,180,-33,tree);var expected=new LinkedHashMap<String,Protocol.Edit>();state.forEachEdit(edit->expected.put(edit.key(),edit));
        var actual=new WorldVoxels.History();var replay=WorldVoxels.prepareCellReplay(state);WorldVoxels.rememberCell(actual,replay);
        var fork=WorldVoxels.History.copyOf(actual);var before=new LinkedHashMap<>(expected);var rows=state.edits();
        for(int i:new int[]{1,63,64,65,127,128,4095,4096}) {
            var original=rows.get(i);var changed=original.withColor(original.color()^0xffffff);
            assertEquals(expected.put(changed.key(),changed),actual.put(changed.key(),changed));
        }
        var other=new Protocol.Edit(8,180,8,Blocks.STONE);expected.put(other.key(),other);actual.put(other.key(),other);
        for(int i:new int[]{0,1,64,128,4096}){var edit=rows.get(i);assertEquals(expected.remove(edit.key()),actual.remove(edit.key()));expected.put(edit.key(),edit);actual.put(edit.key(),edit);}
        equal(expected,actual);equal(before,fork);
        WorldVoxels.rememberCell(actual,replay);WorldVoxels.rememberCell(expected,state);equal(expected,actual);equal(before,fork);
        assertNull(actual.get("-17,180,-33/04/00/00/00"));assertNull(actual.get("\u0000-17,180,-33"));
        assertFalse(actual.containsKey("-17,180,-33/4/16/0/0"));
    }
    @Test void directMapPutKeepsLegacyPayloadAndImmutableViewsWithoutChangingRowPosition() {
        var actual=new WorldVoxels.History();var expected=new LinkedHashMap<String,Protocol.Edit>();var s=state(0,0,new Random(54));
        WorldVoxels.rememberCell(actual,s);WorldVoxels.rememberCell(expected,s);
        // Map.put historically accepts payloads that protocol validation would reject.
        var invalid=new Protocol.Edit(0,180,0,Blocks.STONE,0,100,200,300,-1);
        assertEquals(expected.put(invalid.key(),invalid),actual.put(invalid.key(),invalid));equal(expected,actual);
        var iterator=actual.entrySet().iterator();var cursor=actual.copyCursor();actual.put(invalid.key(),invalid);
        assertThrows(ConcurrentModificationException.class,iterator::hasNext);assertThrows(ConcurrentModificationException.class,cursor::nextBatch);
        assertThrows(UnsupportedOperationException.class,()->actual.entrySet().clear());
        assertThrows(UnsupportedOperationException.class,()->actual.keySet().remove(invalid.key()));
        assertThrows(UnsupportedOperationException.class,()->actual.values().clear());
    }
}
