package dev.jayms.net;

import dev.jayms.*;
import dev.jayms.net.model.SparseVoxelOctree;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RegionReplayEditorTest {
    @Test public void editsAndCellReplacementKeepExactOrderedJournal()throws Exception {
        try(var world=new World(42)) {
            var journal=new RegionReplayJournal();var editor=new RegionReplayEditor(world,journal);var edit=new Protocol.Edit(0,160,0,Blocks.GLASS);
            editor.apply(edit,()->false);assertEquals(List.of(edit),editor.capture(()->false).overlay().rows());
            var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,8,8,8,WorldVoxels.encode(Blocks.STONE));
            var replacement=World.prepareCellReplacement(1,160,0,tree);editor.replaceCells(List.of(replacement),()->false);
            var rows=editor.capture(()->false).overlay().rows();var expected=new ArrayList<Protocol.Edit>();expected.add(edit);expected.addAll(replacement.replay().orderedEdits());assertEquals(expected,rows);assertEquals(rows.size(),world.editsVersion());
            assertThrows(UnsupportedOperationException.class,()->replacement.replay().orderedEdits().add(edit));
        }
    }
    @Test public void budgetCancellationInvalidInputAndForeignThreadRejectBeforeWorldMutation()throws Exception {
        try(var world=new World(42)) {
            var journal=new RegionReplayJournal();var editor=new RegionReplayEditor(world,journal);var edit=new Protocol.Edit(0,160,0,Blocks.STONE);
            assertThrows(CancellationException.class,()->editor.apply(edit,()->true));assertEquals(0,world.editsVersion());
            assertThrows(IllegalArgumentException.class,()->editor.apply(new Protocol.Edit(Integer.MAX_VALUE,160,0,Blocks.STONE),()->false));assertEquals(0,world.editsVersion());
            var worker=Executors.newSingleThreadExecutor();try{worker.submit(()->{assertThrows(IOException.class,()->editor.apply(edit,()->false));return null;}).get(10,TimeUnit.SECONDS);}finally{worker.shutdownNow();assertTrue(worker.awaitTermination(10,TimeUnit.SECONDS));}
            journal.append(Collections.nCopies(RegionReplayOverlay.MAX_ROWS,edit),()->false);assertThrows(IOException.class,()->editor.apply(edit,()->false));assertEquals(0,world.editsVersion());
        }
    }
    @Test public void bypassAndPartialWorldFailurePoisonJournalInsteadOfClaimingRollback()throws Exception {
        try(var world=new World(42)) {
            var journal=new RegionReplayJournal();var editor=new RegionReplayEditor(world,journal);var captured=editor.capture(()->false);world.apply(new Protocol.Edit(0,160,0,Blocks.STONE));
            assertThrows(IOException.class,()->captured.validate(()->false));
            assertThrows(IOException.class,()->editor.capture(()->false));assertThrows(IOException.class,()->journal.capture(()->false));assertEquals(1,world.editsVersion());
        }
        var failure=new IllegalStateException("Synthetic failure after World mutation");
        try(var world=new World(42){@Override public void apply(Protocol.Edit edit){super.apply(edit);throw failure;}}) {
            var journal=new RegionReplayJournal();var editor=new RegionReplayEditor(world,journal);
            assertSame(failure,assertThrows(IllegalStateException.class,()->editor.apply(new Protocol.Edit(0,160,0,Blocks.STONE),()->false)));
            assertEquals(1,world.editsVersion());assertThrows(IOException.class,()->journal.capture(()->false));assertThrows(IOException.class,()->editor.capture(()->false));
        }
    }
}
