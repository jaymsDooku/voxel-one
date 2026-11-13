package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreparedCellReplayTest {
    private static SparseVoxelOctree fractional(){var t=new SparseVoxelOctree(16);t.fill(1,2,3,4,5,6,0xfe123456);t.fill(14,13,12,15,14,13,WorldVoxels.encode(Blocks.GLASS));return t;}
    private static WorldVoxels.History base(){var h=new WorldVoxels.History();for(var e:List.of(new Protocol.Edit(0,180,0,Blocks.BRICKS),new Protocol.Edit(1,180,0,Blocks.STONE))){h.put(e.key(),e);}return h;}
    private static void same(Map<String,Protocol.Edit> a,Map<String,Protocol.Edit> b){assertEquals(new ArrayList<>(a.entrySet()),new ArrayList<>(b.entrySet()));}
    @Test void everyCellKindMatchesIndexedAndLegacyReplayIncludingRawRevisionCounts()throws Exception {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeByte(16);out.writeByte(2);
        for(int i=0;i<8;i++){out.writeByte(1);out.writeInt(WorldVoxels.encode(Blocks.STONE));}
        var redundant=SparseVoxelOctree.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        for(var tree:List.of(new SparseVoxelOctree(16),WorldCellReplacementTest.uniform(Blocks.STONE),WorldCellReplacementTest.uniform(Blocks.FLOWER_POT),fractional(),fractional().packedCellCopy(),redundant)) {
            var state=new Protocol.CellState(0,180,0,tree);var replay=WorldVoxels.prepareCellReplay(state);
            assertEquals(state.replayEditCount(),replay.editCount());assertEquals(state.edits().get(0).cellKey(),replay.cellKey());
            var expected=base();var actual=base();long before=actual.revision();
            WorldVoxels.rememberCell(expected,state);WorldVoxels.rememberCell(actual,replay);same(expected,actual);
            assertEquals(expected.revision(),actual.revision());assertEquals(replay.editCount(),actual.revision()-before);
            var legacy=new LinkedHashMap<String,Protocol.Edit>(base());WorldVoxels.rememberCell(legacy,replay);same(expected,legacy);
            WorldVoxels.rememberCell(actual,replay);same(expected,actual);
        }
    }
    @Test void replayIndexRemainsImmutableAcrossHistoriesSnapshotsAndLaterEdits() {
        var source=fractional();var state=new Protocol.CellState(0,180,0,source);var replay=WorldVoxels.prepareCellReplay(state);
        var a=base();var b=base();WorldVoxels.rememberCell(a,replay);WorldVoxels.rememberCell(b,replay);
        var expected=new ArrayList<>(b.entrySet());var snapshot=WorldVoxels.History.copyOf(a);
        source.fill(0,0,0,16,16,16,0);assertEquals(expected,new ArrayList<>(b.entrySet()));
        WorldVoxels.remember(a,new Protocol.Edit(0,180,0,Blocks.WOOD));assertEquals(expected,new ArrayList<>(b.entrySet()));assertEquals(expected,new ArrayList<>(snapshot.entrySet()));
        b.remove(b.values().stream().filter(e->e.depth()>0).findFirst().orElseThrow().key());assertEquals(expected,new ArrayList<>(snapshot.entrySet()));
        WorldVoxels.rememberCell(a,replay);assertEquals(expected,new ArrayList<>(a.entrySet()));
        WorldVoxels.remember(a,new Protocol.Edit(0,180,0,Blocks.WOOD));
        var overwritten=base();WorldVoxels.rememberCell(overwritten,new Protocol.CellState(0,180,0,fractional()));WorldVoxels.remember(overwritten,new Protocol.Edit(0,180,0,Blocks.WOOD));same(overwritten,a);
        WorldVoxels.rememberCell(a,replay);assertEquals(expected,new ArrayList<>(a.entrySet()));
        snapshot.clear();WorldVoxels.rememberCell(snapshot,replay);
        var expectedCell=new LinkedHashMap<String,Protocol.Edit>();WorldVoxels.rememberCell(expectedCell,replay);same(expectedCell,snapshot);
        var copied=new WorldVoxels.History();var cursor=a.copyCursor();for(var batch=cursor.nextBatch();!batch.isEmpty();batch=cursor.nextBatch())batch.copyTo(copied,()->{});same(a,copied);
    }
    @Test void failedPreparationDoesNotChangeExistingHistoryOrFreezeCaller() {
        var history=base();var before=new ArrayList<>(history.entrySet());long revision=history.revision();
        var invalid=new SparseVoxelOctree(16);invalid.fill(0,0,0,1,1,1,WorldVoxels.encode(Blocks.FLOWER_POT));
        assertThrows(IllegalArgumentException.class,()->WorldVoxels.prepareCellReplay(new Protocol.CellState(0,180,0,invalid)));
        assertEquals(before,new ArrayList<>(history.entrySet()));assertEquals(revision,history.revision());
        assertDoesNotThrow(()->invalid.fill(0,0,0,16,16,16,0));
        assertThrows(IllegalArgumentException.class,()->WorldVoxels.prepareCellReplay(new Protocol.CellState(0,180,0,new SparseVoxelOctree(8))));
    }
}
