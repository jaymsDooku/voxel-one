package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChunkCellReplacementTest {
    private static SparseVoxelOctree uniform(int value) {var t=new SparseVoxelOctree(16);t.fill(0,0,0,16,16,16,value);return t;}
    @Test void cellReplacementMatchesReplayMetadataAndMeshes() {
        var actual=new Chunk();var replay=new Chunk();var random=new Random(37);
        for(int step=0;step<100;step++) {
            int x=random.nextInt(4),y=random.nextInt(2),z=random.nextInt(4);var tree=new SparseVoxelOctree(16);
            switch(step%4) {
                case 0 -> tree=uniform(WorldVoxels.encode(Blocks.STONE));
                case 1 -> tree=uniform(WorldVoxels.encode(1024));
                case 2 -> {for(int i=0;i<40;i++){int a=random.nextInt(16),b=random.nextInt(16),c=random.nextInt(16);tree.fill(a,b,c,a+1,b+1,c+1,0xfe123456);}}
                default -> {}
            }
            actual.replaceCell(x,y,z,tree);
            for(var edit:new Protocol.CellState(x,y,z,tree).edits())replay.apply(edit);
            assertEquals(replay.snapshot().leaves(),actual.snapshot().leaves());assertEquals(replay.models(),actual.models());assertEquals(replay.hasFractional(),actual.hasFractional());
            tree.fill(0,0,0,16,16,16,0);assertEquals(replay.snapshot().leaves(),actual.snapshot().leaves());
        }
        var a=MeshDataGenerator.generate(actual);var b=MeshDataGenerator.generate(replay);
        assertArrayEquals(b.vertices(),a.vertices());assertArrayEquals(b.indices(),a.indices());assertArrayEquals(b.surface(),a.surface());
        assertThrows(IndexOutOfBoundsException.class,()->actual.replaceCell(16,0,0,uniform(0)));
        assertThrows(IllegalArgumentException.class,()->actual.replaceCell(0,0,0,new SparseVoxelOctree(8)));
    }
    @Test void legacyRedundantBranchesNormalizeWithoutChangingSourceBytes()throws Exception {
        var bytes=new java.io.ByteArrayOutputStream();var out=new java.io.DataOutputStream(bytes);out.writeByte(16);out.writeByte(2);
        for(int i=0;i<8;i++){out.writeByte(1);out.writeInt(WorldVoxels.encode(Blocks.STONE));}
        var source=SparseVoxelOctree.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(8,source.leaves().size());var chunk=new Chunk();chunk.replaceCell(0,0,0,source);
        assertFalse(chunk.hasFractional());assertEquals(Blocks.STONE,chunk.getBlock(0,0,0));assertEquals(1,chunk.cell(0,0,0).leaves().size());
        var after=new java.io.ByteArrayOutputStream();source.write(new java.io.DataOutputStream(after));assertArrayEquals(bytes.toByteArray(),after.toByteArray());
    }
    @Test void alignedGraftPreservesSnapshotsNeighboursAndCollapsesUniformBranches() {
        var target=new SparseVoxelOctree(32);target.fill(0,0,0,32,32,32,0xff000002);var before=target.copy().freeze();
        var source=new SparseVoxelOctree(16);source.fill(1,2,3,3,4,5,0xfe123456);var saved=source.copy().freeze();
        target.replaceRegion(16,0,16,source);source.fill(0,0,0,16,16,16,0);
        for(int y=0;y<32;y++)for(int z=0;z<32;z++)for(int x=0;x<32;x++) {
            int expected=x>=16&&y<16&&z>=16?saved.get(x-16,y,z-16):0xff000002;
            assertEquals(expected,target.get(x,y,z));assertEquals(0xff000002,before.get(x,y,z));
        }
        var detached=target.copy().freeze();target.fill(16,0,16,32,16,32,0);assertEquals(saved.leaves(),detached.region(16,0,16,16).leaves());
        for(int y=0;y<32;y+=16)for(int z=0;z<32;z+=16)for(int x=0;x<32;x+=16)target.replaceRegion(x,y,z,uniform(0xff000001));
        assertEquals(1,target.leaves().size());assertEquals(0xff000001,target.uniform(0,0,0,32));
        assertThrows(IllegalArgumentException.class,()->target.replaceRegion(1,0,0,source));
        assertThrows(IllegalArgumentException.class,()->target.replaceRegion(Integer.MAX_VALUE,0,0,source));
        assertThrows(IllegalArgumentException.class,()->target.replaceRegion(0,0,0,new SparseVoxelOctree(64)));
        assertThrows(IllegalStateException.class,()->before.replaceRegion(0,0,0,source));
    }
}
