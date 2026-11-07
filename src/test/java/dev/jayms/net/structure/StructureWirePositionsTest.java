package dev.jayms.net.structure;

import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureWirePositionsTest {
    @Test void boundaryAxesAndZeroRemainDistinctAndWireOrderIsPreserved()throws Exception {
        var cells=new ArrayList<Protocol.CellState>();
        for(int x:new int[]{-Terrain.LIMIT,0,Terrain.LIMIT})
            for(int y:new int[]{Protocol.MIN_BUILD_Y,0,Protocol.MAX_BUILD_Y})
                for(int z:new int[]{-Terrain.LIMIT,0,Terrain.LIMIT})
                    cells.add(new Protocol.CellState(x,y,z,new SparseVoxelOctree(16)));
        Collections.shuffle(cells,new Random(37));
        var wire=StructureWire.encode(new StructureWire.Delta(List.of(),cells));
        var decoded=StructureWire.decode(wire);
        for(int i=0;i<cells.size();i++) {
            var a=cells.get(i);var b=decoded.cells().get(i);
            assertEquals(List.of(a.x(),a.y(),a.z()),List.of(b.x(),b.y(),b.z()));
        }
        assertArrayEquals(wire,StructureWire.encode(decoded));
        cells.add(cells.get(0));
        assertEquals("Duplicate structure cell",assertThrows(IOException.class,()->StructureWire.decode(
                StructureWire.encode(new StructureWire.Delta(List.of(),cells)))).getMessage());
    }
    @Test void sortedIndexHandlesFullTransferCountAndRejectsLateDuplicate()throws Exception {
        var index=new StructureWirePositions(2_000_000);
        for(int i=1_999_999;i>=0;i--)index.add(i-1_000_000,128,-2);
        index.validate();
        assertThrows(IOException.class,()->index.add(0,128,-2));
        var duplicate=new StructureWirePositions(100_001);
        for(int i=99_999;i>=0;i--)duplicate.add(i-50_000,128,-2);
        duplicate.add(49_999,128,-2);
        assertEquals("Duplicate structure cell",assertThrows(IOException.class,duplicate::validate).getMessage());
    }
    @Test void invalidCoordinatesCountsAndIncompleteTransferRejectBeforePacking()throws Exception {
        assertThrows(IllegalArgumentException.class,()->new StructureWirePositions(-1));
        assertThrows(IllegalArgumentException.class,()->new StructureWirePositions(2_000_001));
        new StructureWirePositions(0).validate();
        var index=new StructureWirePositions(1);
        for(int[] p:new int[][]{{Integer.MIN_VALUE,0,0},{Integer.MAX_VALUE,0,0},{0,-1025,0},{0,1024,0},{0,0,-1_000_001},{0,0,1_000_001}})
            assertThrows(IOException.class,()->index.add(p[0],p[1],p[2]));
        assertThrows(IOException.class,index::validate);
        index.add(0,0,0);index.validate();
    }
}
