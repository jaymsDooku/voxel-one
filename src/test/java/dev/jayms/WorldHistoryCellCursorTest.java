package dev.jayms;

import dev.jayms.net.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldHistoryCellCursorTest {
    static Protocol.Edit whole(int x){return new Protocol.Edit(x,128,0,Blocks.STONE);}
    static Set<WorldVoxels.CellPosition> all(WorldVoxels.History history){
        var result=new HashSet<WorldVoxels.CellPosition>();var cursor=history.cellCopyCursor();
        for(var batch=cursor.nextBatch();!batch.isEmpty();batch=cursor.nextBatch()) {assertTrue(batch.size()<=1024);for(var p:batch)assertTrue(result.add(p));}
        return result;
    }
    @Test void batchesDistinctCellsIncludingAirAndFractionalHistory() {
        var history=new WorldVoxels.History();for(int i=0;i<2051;i++)WorldVoxels.remember(history,whole(i));
        WorldVoxels.remember(history,new Protocol.Edit(-1,128,0,0));
        WorldVoxels.remember(history,new Protocol.Edit(-2,128,0,Blocks.piece(Blocks.GLASS,1),1,0,0,0));
        WorldVoxels.remember(history,new Protocol.Edit(-2,128,0,Blocks.piece(Blocks.STONE,1),1,1,0,0));
        var cursor=history.cellCopyCursor();var first=cursor.nextBatch();assertEquals(1024,first.size());assertThrows(UnsupportedOperationException.class,first::clear);
        var found=all(history);assertEquals(2053,found.size());assertTrue(found.contains(new WorldVoxels.CellPosition(-1,128,0)));assertTrue(found.contains(new WorldVoxels.CellPosition(-2,128,0)));
        assertEquals(1024,cursor.nextBatch().size());assertEquals(5,cursor.nextBatch().size());assertTrue(cursor.nextBatch().isEmpty());assertTrue(cursor.nextBatch().isEmpty());
    }
    @Test void everyMutationInvalidatesIncludingExhaustedAndValueAba() {
        for(int mutation=0;mutation<4;mutation++) {
            var history=new WorldVoxels.History();history.put(whole(0).key(),whole(0));var cursor=history.cellCopyCursor();var retained=cursor.nextBatch();
            switch(mutation){case 0->history.put(whole(0).key(),whole(0));case 1->{history.remove(whole(0).key());history.put(whole(0).key(),whole(0));}case 2->history.clear();default->history.put(whole(1).key(),whole(1));}
            assertThrows(ConcurrentModificationException.class,cursor::nextBatch);assertEquals(List.of(new WorldVoxels.CellPosition(0,128,0)),retained);
        }
    }
    @Test void partialRemovalCopyOnWriteAndCanonicalReplacementKeepCellIndex() {
        var history=new WorldVoxels.History();var left=new Protocol.Edit(3,128,0,Blocks.piece(Blocks.STONE,1),1,0,0,0);var right=new Protocol.Edit(3,128,0,Blocks.piece(Blocks.GLASS,1),1,1,0,0);WorldVoxels.remember(history,left);WorldVoxels.remember(history,right);
        var cursor=history.cellCopyCursor();var copy=WorldVoxels.History.copyOf(history);copy.remove(left.key());assertEquals(1,cursor.nextBatch().size());assertEquals(1,all(copy).size());copy.remove(right.key());assertTrue(all(copy).isEmpty());assertEquals(1,all(history).size());
        var empty=new dev.jayms.net.model.SparseVoxelOctree(16);WorldVoxels.rememberCell(history,new Protocol.CellState(3,128,0,empty));assertEquals(1,all(history).size());assertEquals(1,history.size());history.remove(history.keySet().iterator().next());assertTrue(all(history).isEmpty());
    }
}
