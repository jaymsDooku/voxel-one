package dev.jayms.net.structure;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.jayms.net.structure.StructurePackage.*;

class StructurePreviewTest {
    private static SparseVoxelOctree stone() {
        var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));return tree.freeze();
    }
    private static StructurePackage asset()throws IOException {
        var fraction=new SparseVoxelOctree(16);fraction.fill(1,2,3,2,4,6,WorldVoxels.encode(Blocks.STONE));
        var model=new SparseVoxelOctree(8);model.set(1,2,3,0xff123456);
        return new StructurePackage("Preview",new Bounds(3,1,5,1,0,2),List.of(new ModelDefinition("Component",model)),
                List.of(new Cell(new Position(2,0,4),stone(),-1),new Cell(new Position(0,0,0),fraction,-1),new Cell(new Position(1,0,3),null,0)));
    }
    private static Source source(Predicate<Position> empty,Function<Position,SparseVoxelOctree> read) {
        return new Source() {
            public boolean emptyCell(int x,int y,int z){return empty.test(new Position(x,y,z));}
            public SparseVoxelOctree cell(int x,int y,int z){return read.apply(new Position(x,y,z));}
            public ModelLibrary models(){throw new AssertionError("Preview must not read dependency definitions");}
        };
    }
    @Test void fourRotationsRespectAnchorHolesAndEmptyHints()throws Exception {
        var asset=asset();var anchor=new Position(10,80,-20);
        int[][][] expected={{{9,-22},{11,-18},{10,-19}},{{12,-21},{8,-19},{9,-20}},{{11,-18},{9,-22},{10,-21}},{{8,-19},{12,-21},{11,-20}}};
        for(int rotation=0;rotation<4;rotation++) {
            var seen=new HashSet<Position>();var progress=new ArrayList<Integer>();
            // Terrain at the empty source anchor must remain irrelevant to overlap.
            var snapshot=source(p->{seen.add(p);return !p.equals(anchor);},p->{throw new AssertionError("Empty hints must avoid geometry reads");});
            assertFalse(StructurePreview.blocked(asset,anchor,rotation,snapshot,()->false,progress::add));
            var wanted=new HashSet<Position>();for(var p:expected[rotation])wanted.add(new Position(p[0],80,p[1]));
            assertEquals(wanted,seen);assertEquals(List.of(0,100),progress);
        }
    }
    @Test void firstConflictStopsAndUnknownHintsStillReadOccupancy()throws Exception {
        var asset=asset();var reads=new AtomicInteger();var progress=new ArrayList<Integer>();
        var blocked=source(p->false,p->{assertEquals(1,reads.incrementAndGet());return stone();});
        assertTrue(StructurePreview.blocked(asset,new Position(10,80,-20),0,blocked,()->false,progress::add));
        assertEquals(1,reads.get());assertEquals(List.of(0,100),progress);
        reads.set(0);var unknown=source(p->false,p->{reads.incrementAndGet();return new SparseVoxelOctree(16);});
        assertFalse(StructurePreview.blocked(asset,new Position(10,80,-20),0,unknown,()->false,p->{}));assertEquals(3,reads.get());
    }
    @Test void cancellationDuringHintOrCompletionNeverReturnsAResult()throws Exception {
        var asset=asset();var cancel=new AtomicBoolean();
        var snapshot=source(p->{cancel.set(true);return false;},p->{throw new AssertionError("Cancelled before occupancy read");});
        assertThrows(CancellationException.class,()->StructurePreview.blocked(asset,new Position(10,80,-20),0,snapshot,cancel::get,p->{}));
        cancel.set(false);var empty=source(p->true,p->{throw new AssertionError();});
        assertThrows(CancellationException.class,()->StructurePreview.blocked(asset,new Position(10,80,-20),0,empty,cancel::get,p->{if(p==100)cancel.set(true);}));
        assertFalse(StructurePreview.blocked(asset,new Position(10,80,-20),0,empty,()->false,p->{}));
        assertThrows(IOException.class,()->StructurePreview.blocked(asset,new Position(Integer.MAX_VALUE,80,0),0,empty,()->false,p->{}));
    }
    @Test void positionIndexSurvivesRoundTripAndIsImmutable()throws Exception {
        var asset=asset();var positions=asset.occupiedPositions();
        assertEquals(asset.cells.stream().map(Cell::position).toList(),positions);
        assertThrows(UnsupportedOperationException.class,()->positions.set(0,new Position(0,0,0)));
        assertThrows(UnsupportedOperationException.class,()->positions.clear());
        assertThrows(IndexOutOfBoundsException.class,()->positions.get(-1));
        assertEquals(positions,StructurePackage.decode(asset.encode()).occupiedPositions());
        assertArrayEquals(asset.encode(),StructurePackage.decode(asset.encode()).encode());
    }
}
