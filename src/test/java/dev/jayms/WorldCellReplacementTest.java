package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldCellReplacementTest {
    static SparseVoxelOctree uniform(int type){var t=new SparseVoxelOctree(16);t.fill(0,0,0,16,16,16,WorldVoxels.encode(type));return t;}
    static void equalCell(SparseVoxelOctree a,SparseVoxelOctree b){for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)assertEquals(a.get(x,y,z),b.get(x,y,z));}
    @Test void wholeCellMatchesReplayHistoryRevisionsAndResidentMetadata()throws Exception {
        for(boolean resident:new boolean[]{false,true})try(var actual=new World(42);var replay=new World(42)) {
            var component=new SparseVoxelOctree(8);component.set(1,2,3,0xff123456);var definition=new ModelDefinition("Replacement component",component);
            int model=actual.models().stageStructureModel(definition).id();assertEquals(model,replay.models().stageStructureModel(definition).id());
            var position=ChunkPos.fromBlock(-17,128,-1);var a=new Chunk();var b=new Chunk();a.terrainEquivalent=true;b.terrainEquivalent=true;if(resident){actual.addChunk(position,a);replay.addChunk(position,b);}
            var random=new Random(71);
            for(int step=0;step<24;step++) {
                int x=-17-step%2;var tree=new SparseVoxelOctree(16);
                switch(step%4) {
                    case 0 -> tree=uniform(Blocks.STONE);
                    case 1 -> tree=uniform(model);
                    case 2 -> {for(int i=0;i<64;i++){int a1=random.nextInt(16),b1=random.nextInt(16),c1=random.nextInt(16);tree.fill(a1,b1,c1,a1+1,b1+1,c1+1,0xfe000000|random.nextInt(0x1000000));}}
                    default -> {}
                }
                long sky=actual.skyStamp();actual.replaceCell(x,128,-1,tree);for(var edit:new Protocol.CellState(x,128,-1,tree).edits())replay.apply(edit);
                equalCell(replay.cell(x,128,-1),actual.cell(x,128,-1));assertEquals(replay.editsVersion(),actual.editsVersion());assertTrue(actual.skyStamp()>sky);
                assertEquals(new ArrayList<>(replay.editsSnapshot().entrySet()),new ArrayList<>(actual.editsSnapshot().entrySet()));
                assertEquals(replay.requiresSkyCeiling(position.chunkX(),position.chunkZ()),actual.requiresSkyCeiling(position.chunkX(),position.chunkZ()));
                var column=new ChunkPos(position.chunkX(),0,position.chunkZ());assertEquals(replay.skyEditRevision(column),actual.skyEditRevision(column));assertEquals(replay.skyGeometryRevision(),actual.skyGeometryRevision());
                assertEquals(replay.cell(x,128,-1).leaves(),actual.cell(x,128,-1).leaves());
                if(resident){assertEquals(b.snapshot().leaves(),a.snapshot().leaves());assertEquals(b.models(),a.models());assertEquals(b.hasFractional(),a.hasFractional());assertEquals(b.terrainEquivalent,a.terrainEquivalent);}
            }
            var saved=actual.structureSnapshot();var expected=saved.cell(-17,128,-1);actual.replaceCell(-17,128,-1,uniform(Blocks.BRICKS));equalCell(expected,saved.cell(-17,128,-1));
            if(resident){actual.unloadChunk(position);replay.unloadChunk(position);}
        }
    }
    @Test void coldChunkReplayAndCallerIsolationPreserveFractionalLedCells()throws Exception {
        try(var world=new World(42)) {
            var tree=new SparseVoxelOctree(16);tree.fill(1,2,3,4,5,6,0xfe123456);var expected=tree.copy().freeze();var position=ChunkPos.fromBlock(-17,128,-1);
            world.replaceCell(-17,128,-1,tree);tree.fill(0,0,0,16,16,16,0);equalCell(expected,world.cell(-17,128,-1));
            world.addChunk(position,new Chunk());equalCell(expected,world.cell(-17,128,-1));assertEquals(1,world.editedCellsInChunk(position).size());
            var frozen=world.editedCellsInChunk(position).iterator().next().voxels();assertThrows(IllegalStateException.class,()->frozen.set(0,0,0,0));
            world.unloadChunk(position);equalCell(expected,world.cell(-17,128,-1));world.addChunk(position,new Chunk());equalCell(expected,world.cell(-17,128,-1));
            world.replaceCell(-17,128,-1,new SparseVoxelOctree(16));assertEquals(0,world.cell(-17,128,-1).occupied());
            world.unloadChunk(position);world.addChunk(position,new Chunk());assertEquals(0,world.cell(-17,128,-1).occupied());
        }
    }
    @Test void invalidInputDoesNotChangeHistoryOrRevisions()throws Exception {
        try(var world=new World(42)) {
            world.replaceCell(0,128,0,uniform(Blocks.STONE));var before=world.editsSnapshot();long version=world.editsVersion(),sky=world.skyStamp();
            assertThrows(IllegalArgumentException.class,()->world.replaceCell(0,128,0,new SparseVoxelOctree(8)));
            assertThrows(IllegalArgumentException.class,()->world.replaceCell(Integer.MAX_VALUE,128,0,uniform(Blocks.BRICKS)));
            assertThrows(NullPointerException.class,()->world.replaceCell(0,128,0,null));
            assertEquals(before,world.editsSnapshot());assertEquals(version,world.editsVersion());assertEquals(sky,world.skyStamp());assertEquals(WorldVoxels.encode(Blocks.STONE),world.cell(0,128,0).uniform(0,0,0,16));
        }
    }
    @Test void legacyRedundantBranchesMatchCanonicalReplayAndLeaveSourceBytesUntouched()throws Exception {
        var bytes=new java.io.ByteArrayOutputStream();var out=new java.io.DataOutputStream(bytes);out.writeByte(16);out.writeByte(2);
        for(int i=0;i<8;i++){out.writeByte(1);out.writeInt(WorldVoxels.encode(Blocks.STONE));}
        var source=SparseVoxelOctree.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));assertEquals(8,source.leaves().size());
        try(var actual=new World(42);var replay=new World(42)) {
            actual.replaceCell(-17,128,-1,source);for(var edit:new Protocol.CellState(-17,128,-1,source).edits())replay.apply(edit);
            assertEquals(replay.structureSnapshot().cell(-17,128,-1).leaves(),actual.structureSnapshot().cell(-17,128,-1).leaves());
            assertEquals(new ArrayList<>(replay.editsSnapshot().entrySet()),new ArrayList<>(actual.editsSnapshot().entrySet()));
            actual.addChunk(ChunkPos.fromBlock(-17,128,-1),new Chunk());assertEquals(1,actual.cell(-17,128,-1).leaves().size());
        }
        var after=new java.io.ByteArrayOutputStream();source.write(new java.io.DataOutputStream(after));assertArrayEquals(bytes.toByteArray(),after.toByteArray());
    }
}
