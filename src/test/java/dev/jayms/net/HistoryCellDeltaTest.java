package dev.jayms.net;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HistoryCellDeltaTest {
    static Set<WorldVoxels.CellPosition> oracle(Map<String,Protocol.Edit> current,Map<String,Protocol.Edit> before) {
        var result=new HashSet<WorldVoxels.CellPosition>();
        for(var row:current.values())if(!row.equals(before.get(row.key())))result.add(new WorldVoxels.CellPosition(row.x(),row.y(),row.z()));
        for(var row:before.values())if(!row.equals(current.get(row.key())))result.add(new WorldVoxels.CellPosition(row.x(),row.y(),row.z()));
        return result;
    }
    static void same(WorldVoxels.History current,WorldVoxels.History before) {
        assertEquals(oracle(new LinkedHashMap<>(current),new LinkedHashMap<>(before)),MultiplayerServer.changedCellPositions(current,before));
        assertEquals(oracle(new LinkedHashMap<>(before),new LinkedHashMap<>(current)),MultiplayerServer.changedCellPositions(before,current));
    }
    static WorldVoxels.History compact(int cells) {
        var result=new WorldVoxels.History();var loader=new KeyedReplayLoader(result,()->false);
        for(int x=0;x<cells;x++)for(var row:RegionReplayLoaderTest.fragmented(x))loader.accept(row);
        loader.finish();return result;
    }
    @Test void bulkAndOrdinaryOverwritesDetachCellIdentityAcrossSiblingSnapshots() {
        var current=compact(2);var ordinary=new Protocol.Edit(32,180,-1,Blocks.LED,0,0,0,0,123);current.put(ordinary.key(),ordinary);
        var before=WorldVoxels.History.copyOf(current);var saved=new LinkedHashMap<>(before);
        var sibling=WorldVoxels.History.copyOf(before);
        var bulk=new Protocol.Edit(0,180,-1,Blocks.piece(Blocks.LED,4),4,1,2,3,0xabcdef);
        long revision=current.revision();current.put(bulk.key(),bulk);current.put(ordinary.key(),ordinary.withColor(234));
        assertEquals(revision+2,current.revision());same(current,before);same(sibling,before);
        sibling.put(bulk.key(),bulk.withColor(0x123456));same(current,sibling);same(sibling,before);
        assertEquals(saved,new LinkedHashMap<>(before));
        assertThrows(UnsupportedOperationException.class,()->MultiplayerServer.changedCellPositions(current,before).clear());
    }
    @Test void equalValuesAndReorderedRowsDoNotProduceFalseChanges() {
        var current=compact(1);var before=WorldVoxels.History.copyOf(current);
        var row=current.values().stream().filter(e->e.depth()==4).findFirst().orElseThrow();
        current.put(row.key(),row);same(current,before);assertTrue(MultiplayerServer.changedCellPositions(current,before).isEmpty());
        current.remove(row.key());current.put(row.key(),row);same(current,before);
        assertTrue(MultiplayerServer.changedCellPositions(current,before).isEmpty());
        var ordinary=new WorldVoxels.History();ordinary.putAll(new LinkedHashMap<>(before));same(ordinary,before);
        assertTrue(MultiplayerServer.changedCellPositions(ordinary,before).isEmpty());
    }
    @Test void randomCoverageDeletionReplacementAndClearMatchRowUnionOracle() {
        var current=compact(2);var before=WorldVoxels.History.copyOf(current);var random=new Random(390174);
        for(int step=0;step<500;step++) {
            int x=random.nextInt(6),depth=random.nextInt(5),width=1<<depth;
            var row=new Protocol.Edit(x,180,-1,Blocks.piece(Blocks.LED,depth),depth,random.nextInt(width),random.nextInt(width),random.nextInt(width),random.nextInt(0x1000000));
            switch(step%7) {
                case 0 -> current.put(row.key(),row);
                case 1 -> WorldVoxels.remember(current,row);
                case 2 -> {if(!current.isEmpty())current.remove(current.keySet().iterator().next());}
                case 3 -> {var tree=new dev.jayms.net.model.SparseVoxelOctree(16);tree.fill(1,2,3,2,3,4,0xfe123456);WorldVoxels.rememberCell(current,new Protocol.CellState(x,180,-1,tree));}
                case 4 -> {if(step%35==4)current.clear();}
                case 5 -> before=WorldVoxels.History.copyOf(current);
                case 6 -> {var row2=row.withColor(0x345678);before.put(row2.key(),row2);}
            }
            same(current,before);
        }
    }
    @Test void whollyRemovedAndAddedCellsAndEmptyHistoriesAreDetected() {
        var current=compact(2);var before=WorldVoxels.History.copyOf(current);
        for(var row:new ArrayList<>(current.values()))if(row.x()==0)current.remove(row.key());
        var added=new Protocol.Edit(-17,180,-1,Blocks.STONE);current.put(added.key(),added);same(current,before);
        assertEquals(Set.of(new WorldVoxels.CellPosition(0,180,-1),new WorldVoxels.CellPosition(-17,180,-1)),MultiplayerServer.changedCellPositions(current,before));
        current.clear();same(current,before);before.clear();same(current,before);assertTrue(MultiplayerServer.changedCellPositions(current,before).isEmpty());
    }
}
