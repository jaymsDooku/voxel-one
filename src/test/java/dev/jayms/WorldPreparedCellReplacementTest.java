package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldPreparedCellReplacementTest {
    @Test void workerPreparationIsDetachedReusableAndMatchesResidentReplay()throws Exception {
        var source=new SparseVoxelOctree(16);source.fill(1,2,3,4,5,6,0xfe123456);
        source.fill(14,13,12,15,14,13,WorldVoxels.encode(Blocks.GLASS));
        var expected=source.copy().freeze();var task=new FutureTask<>(()->World.prepareCellReplacement(-17,180,-1,source.packedCellCopy()));
        var worker=new Thread(task,"synthetic-cell-preparation");worker.start();var prepared=task.get(10,TimeUnit.SECONDS);worker.join();
        source.fill(0,0,0,16,16,16,0);
        assertThrows(IllegalStateException.class,()->prepared.replayState().tree().fill(0,0,0,16,16,16,0));
        try(var actual=new World(42);var replay=new World(42);var second=new World(42)) {
            var chunk=ChunkPos.fromBlock(-17,180,-1);actual.addChunk(chunk,new Chunk());replay.addChunk(chunk,new Chunk());
            actual.replaceCell(prepared);second.replaceCell(prepared);
            for(var edit:new Protocol.CellState(-17,180,-1,expected).edits())replay.apply(edit);
            assertTrue(expected.sameValues(actual.cell(-17,180,-1)));assertTrue(expected.sameValues(second.cell(-17,180,-1)));
            assertEquals(replay.editsVersion(),actual.editsVersion());
            assertEquals(new ArrayList<>(replay.editsSnapshot().entrySet()),new ArrayList<>(actual.editsSnapshot().entrySet()));
            actual.replaceCell(-17,180,-1,new SparseVoxelOctree(16));
            assertTrue(expected.sameValues(second.cell(-17,180,-1)));
            actual.replaceCell(prepared);actual.unloadChunk(chunk);actual.addChunk(chunk,new Chunk());
            assertTrue(expected.sameValues(actual.cell(-17,180,-1)));
        }
    }
    @Test void redundantBranchesKeepOriginalReplayWhileInstalledGeometryIsCanonical()throws Exception {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeByte(16);out.writeByte(2);
        for(int i=0;i<8;i++){out.writeByte(1);out.writeInt(WorldVoxels.encode(Blocks.STONE));}
        var source=SparseVoxelOctree.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        var prepared=World.prepareCellReplacement(0,180,0,source);assertEquals(8,prepared.replayState().tree().leafCount());
        try(var actual=new World(42);var replay=new World(42)) {
            actual.replaceCell(prepared);for(var edit:new Protocol.CellState(0,180,0,source).edits())replay.apply(edit);
            assertEquals(1,actual.cell(0,180,0).leafCount());assertEquals(replay.editsVersion(),actual.editsVersion());
            assertEquals(new ArrayList<>(replay.editsSnapshot().entrySet()),new ArrayList<>(actual.editsSnapshot().entrySet()));
        }
    }
    @Test void invalidFractionalModelsRejectDuringPreparationWithoutFreezingCaller()throws Exception {
        var source=new SparseVoxelOctree(16);source.fill(0,0,0,1,1,1,WorldVoxels.encode(Blocks.FLOWER_POT));
        assertThrows(IllegalArgumentException.class,()->World.prepareCellReplacement(0,180,0,source));
        assertDoesNotThrow(()->source.fill(0,0,0,16,16,16,0));
        assertThrows(IllegalArgumentException.class,()->World.prepareCellReplacement(Integer.MAX_VALUE,180,0,source));
        assertThrows(IllegalArgumentException.class,()->World.prepareCellReplacement(0,180,0,new SparseVoxelOctree(8)));
    }
}
