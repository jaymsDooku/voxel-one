package dev.jayms;

import dev.jayms.net.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldVoxelSnapshotTest {
    @Test void wholeCellReplacementMatchesReplayWithoutAliasingCallerOrSnapshots() {
        var world=new WorldVoxels(new Terrain(42));
        var replay=new WorldVoxels(new Terrain(42));
        var tree=new dev.jayms.net.model.SparseVoxelOctree(16);
        var random=new Random(44);
        for(int i=0;i<1500;i++) {
            int x=random.nextInt(16),y=random.nextInt(16),z=random.nextInt(16);
            tree.fill(x,y,z,x+1,y+1,z+1,WorldVoxels.encode(new Protocol.Edit(0,0,0,Blocks.LED).withColor(random.nextInt(0x1000000))));
        }
        tree.fill(0,0,0,1,1,1,WorldVoxels.encode(new Protocol.Edit(0,0,0,Blocks.LED).withColor(0x123456)));
        var before=world.snapshot();
        world.replaceCell(-17,128,-1,tree);
        for(var edit:new Protocol.CellState(-17,128,-1,tree).edits())replay.apply(edit);
        var after=world.snapshot();
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
            assertEquals(replay.cell(-17,128,-1).get(x,y,z),world.cell(-17,128,-1).get(x,y,z));
        assertEquals(0,before.type(-17,128,-1));
        int saved=after.cell(-17,128,-1).get(0,0,0);assertEquals(0xfe123456,saved);
        tree.set(0,0,0,WorldVoxels.encode(Blocks.STONE));
        assertEquals(saved,world.cell(-17,128,-1).get(0,0,0));
        world.replaceCell(-17,128,-1,new dev.jayms.net.model.SparseVoxelOctree(16));
        assertEquals(0,world.type(-17,128,-1));
        assertEquals(saved,after.cell(-17,128,-1).get(0,0,0));
        assertThrows(IllegalStateException.class,()->after.cell(-17,128,-1).set(0,0,0,0));
        assertThrows(IllegalArgumentException.class,()->world.replaceCell(0,128,0,new dev.jayms.net.model.SparseVoxelOctree(8)));
        assertThrows(NullPointerException.class,()->world.replaceCell(0,128,0,null));
        assertEquals(0,world.type(-17,128,-1));
    }
    @Test void everyLocalCoordinateSurvivesFullBucketCopyOnWrite() {
        var world=new WorldVoxels(new Terrain(42));
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
            world.apply(new Protocol.Edit(x-16,y+112,z+16,Blocks.LED).withColor(x+16*z+256*y));
        var snapshot=world.snapshot();world.apply(new Protocol.Edit(-1,127,31,Blocks.STONE));
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
            assertEquals(0xfe000000|(x+16*z+256*y),snapshot.cell(x-16,y+112,z+16).uniform(0,0,0,16));
        assertEquals(Blocks.STONE,world.type(-1,127,31));
        snapshot.apply(new Protocol.Edit(-16,112,16,0));assertEquals(Blocks.LED,world.type(-16,112,16));
    }
    @Test void nestedSnapshotsShareFrozenCellsAndDetachEditsAcrossChunkEdges() {
        var original=new WorldVoxels(new Terrain(42));
        for(int x:new int[]{-17,-16,-1,0,15,16,32})original.apply(new Protocol.Edit(x,128,-1,Blocks.STONE));
        var first=original.snapshot();var second=original.snapshot();var nested=first.snapshot();
        for(int x:new int[]{-17,-16,-1,0,15,16}) {
            assertSame(original.cell(x,128,-1),first.cell(x,128,-1));
            assertSame(first.cell(x,128,-1),nested.cell(x,128,-1));
        }
        assertThrows(IllegalStateException.class,()->first.cell(-1,128,-1).set(0,0,0,0));
        assertThrows(IllegalStateException.class,()->first.cell(1000,128,1000).set(0,0,0,0));
        original.apply(new Protocol.Edit(-1,128,-1,Blocks.BRICKS));first.apply(new Protocol.Edit(0,128,-1,0));
        nested.apply(new Protocol.Edit(15,128,-1,Blocks.piece(Blocks.LED,4),4,1,2,3,0x123456));
        original.apply(new Protocol.Edit(32,128,-1,Blocks.BRICKS));original.apply(new Protocol.Edit(33,128,-1,Blocks.STONE));
        first.apply(new Protocol.Edit(34,128,-1,Blocks.BRICKS));
        assertEquals(Blocks.STONE,first.type(32,128,-1));assertEquals(0,first.type(33,128,-1));
        assertEquals(0,original.type(34,128,-1));assertEquals(0,second.type(34,128,-1));
        assertEquals(Blocks.STONE,first.type(-1,128,-1));assertEquals(Blocks.STONE,second.type(-1,128,-1));
        assertEquals(Blocks.BRICKS,original.type(-1,128,-1));assertEquals(0,first.type(0,128,-1));assertEquals(Blocks.STONE,nested.type(0,128,-1));
        assertEquals(0xfe123456,nested.cell(15,128,-1).get(1,2,3));assertEquals(Blocks.STONE,second.type(15,128,-1));
        first.clear();original.clear();assertEquals(Blocks.STONE,second.type(0,128,-1));assertEquals(Blocks.STONE,nested.type(-17,128,-1));
        original.apply(new Protocol.Edit(-17,128,-1,Blocks.BRICKS));assertEquals(Blocks.STONE,nested.type(-17,128,-1));
    }
    @Test void randomizedForkEditAndClearMatchesIndependentOverrideMaps() {
        var terrain=new Terrain(42);var worlds=new ArrayList<WorldVoxels>();var expected=new ArrayList<Map<String,Integer>>();
        worlds.add(new WorldVoxels(terrain));expected.add(new HashMap<>());var random=new Random(20261022);
        int[] coordinates={-33,-32,-17,-16,-1,0,15,16,31,32};
        for(int step=0;step<1000;step++) {
            int world=random.nextInt(worlds.size());
            if(step%11==0&&worlds.size()<16){worlds.add(worlds.get(world).snapshot());expected.add(new HashMap<>(expected.get(world)));}
            else if(step%29==0){worlds.get(world).clear();expected.get(world).clear();}
            else {
                int x=coordinates[random.nextInt(coordinates.length)],z=coordinates[random.nextInt(coordinates.length)];
                int type=switch(random.nextInt(3)){case 0->0;case 1->Blocks.STONE;default->Blocks.BRICKS;};
                worlds.get(world).apply(new Protocol.Edit(x,128,z,type));expected.get(world).put(x+",128,"+z,type);
            }
            for(int i=0;i<worlds.size();i++)for(int check=0;check<4;check++) {
                int x=coordinates[random.nextInt(coordinates.length)],z=coordinates[random.nextInt(coordinates.length)];
                assertEquals(expected.get(i).getOrDefault(x+",128,"+z,terrain.block(x,128,z)).intValue(),worlds.get(i).type(x,128,z));
            }
        }
    }
}
