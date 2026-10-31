package dev.jayms;

import dev.jayms.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldCellSnapshotTest {
    static Protocol.Edit stone(){return new Protocol.Edit(0,128,0,Blocks.STONE);}
    static WorldVoxels world(){return new WorldVoxels(new Terrain(126));}
    @Test void workerReadsDetachedFrozenGeometryAndOwnerMustRevalidate()throws Exception {
        var world=world();var history=new WorldVoxels.History();world.apply(stone());WorldVoxels.remember(history,stone());var snapshot=world.cellSnapshot(history);var positions=snapshot.nextBatch(world,history);assertEquals(1,positions.size());
        var worker=new FutureTask<>(()->snapshot.cell(positions.get(0)));var thread=new Thread(worker);thread.start();var cell=worker.get(5,TimeUnit.SECONDS);thread.join();assertEquals(WorldVoxels.encode(Blocks.STONE),cell.tree().uniform(0,0,0,16));assertThrows(IllegalStateException.class,()->cell.tree().set(0,0,0,0));assertTrue(snapshot.unchanged(world,history));
        world.apply(new Protocol.Edit(0,128,0,Blocks.GLASS));assertFalse(snapshot.unchanged(world,history));assertEquals(WorldVoxels.encode(Blocks.STONE),snapshot.cell(positions.get(0)).tree().uniform(0,0,0,16));assertThrows(ConcurrentModificationException.class,()->snapshot.nextBatch(world,history));
    }
    @Test void worldOnlyHistoryOnlyIdentityClearAndAbaChangesReject() {
        for(int mutation=0;mutation<6;mutation++) {
            var world=world();var history=new WorldVoxels.History();world.apply(stone());WorldVoxels.remember(history,stone());var snapshot=world.cellSnapshot(history);
            switch(mutation){case 0->world.apply(stone());case 1->history.put(stone().key(),stone());case 2->{world.clear();world.apply(stone());}case 3->{assertFalse(snapshot.unchanged(world.snapshot(),history));continue;}case 4->{assertFalse(snapshot.unchanged(world,WorldVoxels.History.copyOf(history)));continue;}default->world.clear();}
            assertFalse(snapshot.unchanged(world,history));assertThrows(ConcurrentModificationException.class,()->snapshot.nextBatch(world,history));
        }
    }
    @Test void terrainAbaInvalidatesOwnerAndWorkerReads() {
        var terrain=new Terrain(126);var world=new WorldVoxels(terrain);var history=new WorldVoxels.History();WorldVoxels.remember(history,new Protocol.Edit(0,128,0,0));var snapshot=world.cellSnapshot(history);terrain.stressGrid(null);
        assertFalse(snapshot.unchanged(world,history));assertThrows(ConcurrentModificationException.class,()->snapshot.cell(new WorldVoxels.CellPosition(0,128,0)));
    }
    @Test void validationAndCursorAreOwnerOnlyButCopyMutationIsIndependent()throws Exception {
        var world=world();var history=new WorldVoxels.History();WorldVoxels.remember(history,stone());world.apply(stone());var snapshot=world.cellSnapshot(history);var copy=world.snapshot();copy.apply(new Protocol.Edit(0,128,0,Blocks.GLASS));assertTrue(snapshot.unchanged(world,history));
        var worker=new FutureTask<>(()->{assertThrows(IllegalStateException.class,()->snapshot.unchanged(world,history));assertThrows(IllegalStateException.class,()->snapshot.nextBatch(world,history));return true;});var thread=new Thread(worker);thread.start();assertTrue(worker.get(5,TimeUnit.SECONDS));thread.join();
        assertThrows(IllegalArgumentException.class,()->snapshot.cell(new WorldVoxels.CellPosition(Integer.MAX_VALUE,0,0)));assertEquals(1,snapshot.nextBatch(world,history).size());assertTrue(snapshot.nextBatch(world,history).isEmpty());
    }
    @Test void patchCandidateChangesIdentityAndKeepsPriorSnapshot() {
        var world=world();var history=new WorldVoxels.History();world.apply(stone());WorldVoxels.remember(history,stone());var snapshot=world.cellSnapshot(history);var chunk=ChunkPos.fromBlock(0,128,0);var patch=world.captureCellPatch(List.of(chunk));var tree=world.cell(0,128,0).copy();tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.GLASS));patch.replaceCell(0,128,0,tree);var candidate=patch.merge(world);assertNotNull(candidate);assertTrue(snapshot.unchanged(world,history));assertFalse(snapshot.unchanged(candidate,history));assertEquals(WorldVoxels.encode(Blocks.STONE),snapshot.cell(new WorldVoxels.CellPosition(0,128,0)).tree().uniform(0,0,0,16));
    }
    @Test void callerBudgetsRejectBeforeCaptureAndExactBoundsPass() {
        var world=world();var history=new WorldVoxels.History();assertTrue(world.cellSnapshot(history,0,0).unchanged(world,history));
        WorldVoxels.remember(history,stone());world.apply(stone());
        assertThrows(IllegalArgumentException.class,()->world.cellSnapshot(history,0,1));
        assertThrows(IllegalArgumentException.class,()->world.cellSnapshot(history,1,0));
        assertThrows(IllegalArgumentException.class,()->world.cellSnapshot(history,-1,1));
        assertThrows(IllegalArgumentException.class,()->world.cellSnapshot(history,1,-1));
        assertEquals(1,world.cellSnapshot(history,1,1).nextBatch(world,history).size());
    }

    @Test void incrementalCopyYieldsAndSharesFrozenBuckets() {
        var world=world();var history=new WorldVoxels.History();
        for(int i=0;i<257;i++){var edit=new Protocol.Edit(i*16,128,0,Blocks.STONE);world.apply(edit);WorldVoxels.remember(history,edit);}
        var builder=world.beginCellSnapshot(history,257,257);
        assertNull(builder.advance(world,history));assertNull(builder.advance(world,history));
        var snapshot=builder.advance(world,history);assertNotNull(snapshot);assertSame(snapshot,builder.advance(world,history));
        assertEquals(257,snapshot.nextBatch(world,history).size());
        world.apply(new Protocol.Edit(0,128,0,Blocks.GLASS));
        assertEquals(WorldVoxels.encode(Blocks.STONE),snapshot.cell(new WorldVoxels.CellPosition(0,128,0)).tree().uniform(0,0,0,16));
        assertThrows(ConcurrentModificationException.class,()->builder.advance(world,history));
    }
    @Test void incrementalCopyRejectsStaleIdentityRevisionTerrainAndWrongThread()throws Exception {
        for(int change=0;change<5;change++) {
            var terrain=new Terrain(126);var world=new WorldVoxels(terrain);var history=new WorldVoxels.History();
            for(int i=0;i<129;i++)world.apply(new Protocol.Edit(i*16,128,0,Blocks.STONE));
            var builder=world.beginCellSnapshot(history,0,129);assertNull(builder.advance(world,history));
            switch(change){case 0->world.apply(stone());case 1->WorldVoxels.remember(history,stone());case 2->terrain.stressGrid(null);
                case 3->{assertThrows(ConcurrentModificationException.class,()->builder.advance(world.snapshot(),history));continue;}
                default->{assertThrows(ConcurrentModificationException.class,()->builder.advance(world,WorldVoxels.History.copyOf(history)));continue;}}
            assertThrows(ConcurrentModificationException.class,()->builder.advance(world,history));
        }
        var world=world();var history=new WorldVoxels.History();var builder=world.beginCellSnapshot(history,0,0);
        var task=new FutureTask<>(()->{assertThrows(IllegalStateException.class,()->builder.advance(world,history));return true;});
        var thread=new Thread(task);thread.start();assertTrue(task.get(5,TimeUnit.SECONDS));thread.join();assertNotNull(builder.advance(world,history));
        assertThrows(IllegalArgumentException.class,()->world.beginCellSnapshot(history,-1,0));
        world.apply(stone());assertThrows(IllegalArgumentException.class,()->world.beginCellSnapshot(history,0,0));
    }

}
