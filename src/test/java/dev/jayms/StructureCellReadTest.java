package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureCellReadTest {
    private byte[] bytes(Protocol.CellState cell)throws IOException {
        var bytes=new ByteArrayOutputStream();cell.write(new DataOutputStream(bytes));return bytes.toByteArray();
    }
    private Protocol.CellState read(byte[] bytes)throws IOException {
        return Protocol.CellState.read(new DataInputStream(new ByteArrayInputStream(bytes)));
    }
    @Test void validAirUniformModelFractionalAndColouredCellsRetainWireBytes()throws Exception {
        var trees=new ArrayList<SparseVoxelOctree>();trees.add(new SparseVoxelOctree(16));
        for(int type:new int[]{Blocks.STONE,Blocks.BRICKS,Blocks.FLOWER_POT,1024}) {
            var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(type));trees.add(tree);
        }
        var mixed=new SparseVoxelOctree(16);mixed.fill(0,0,0,8,8,8,WorldVoxels.encode(Blocks.BRICKS));
        mixed.fill(15,15,15,16,16,16,0xfe123456);trees.add(mixed);
        for(var tree:trees) {
            byte[] expected=bytes(new Protocol.CellState(-20,180,26,tree));var decoded=read(expected);
            assertEquals(-20,decoded.x());assertEquals(180,decoded.y());assertEquals(26,decoded.z());
            assertArrayEquals(expected,bytes(decoded));assertEquals(tree.leaves(),decoded.tree().leaves());
            assertThrows(IllegalStateException.class,()->decoded.tree().set(0,0,0,0));
        }
    }
    @Test void malformedMaterialsPositionsResolutionAndTruncationRemainRejected()throws Exception {
        for(int color:new int[]{WorldVoxels.encode(190),WorldVoxels.encode(Blocks.piece(Blocks.STONE,1)),0xff000000}) {
            var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,color);
            assertEquals("Invalid world material",assertThrows(IOException.class,()->read(bytes(new Protocol.CellState(0,180,0,tree)))).getMessage());
        }
        var partialModel=new SparseVoxelOctree(16);partialModel.fill(0,0,0,8,8,8,WorldVoxels.encode(1024));
        assertEquals("Invalid world material",assertThrows(IOException.class,()->read(bytes(new Protocol.CellState(0,180,0,partialModel)))).getMessage());
        assertEquals("Invalid world cell position",assertThrows(IOException.class,()->read(bytes(new Protocol.CellState(0,Protocol.MAX_BUILD_Y+1,0,new SparseVoxelOctree(16))))).getMessage());
        assertThrows(IOException.class,()->read(bytes(new Protocol.CellState(0,180,0,new SparseVoxelOctree(32)))));
        byte[] valid=bytes(new Protocol.CellState(0,180,0,partialModel));assertThrows(IOException.class,()->read(Arrays.copyOf(valid,valid.length-1)));
    }
}
