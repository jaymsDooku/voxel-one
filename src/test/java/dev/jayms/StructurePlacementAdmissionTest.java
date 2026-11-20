package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import static dev.jayms.net.structure.StructurePackage.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructurePlacementAdmissionTest {
    static final class Empty implements Source {
        final ModelLibrary library=new ModelLibrary();final SparseVoxelOctree air=new SparseVoxelOctree(16).freeze();
        final boolean forbidden;int calls;
        Empty(boolean forbidden){this.forbidden=forbidden;}
        void read(){calls++;if(forbidden)throw new AssertionError("Authority accessed before admission");}
        public ModelLibrary models(){read();return library;}
        public SparseVoxelOctree cell(int x,int y,int z){read();return air;}
        public boolean emptyCell(int x,int y,int z){read();return true;}
    }
    static StructurePackage asset(Bounds bounds,List<Position> positions)throws Exception {
        var stone=new SparseVoxelOctree(16);stone.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        return new StructurePackage("Admission",bounds,List.of(),positions.stream().map(p->new Cell(p,stone,-1)).toList());
    }
    static StructurePackage spread(int count)throws Exception {
        var positions=new ArrayList<Position>();
        for(int z=0;z<17&&positions.size()<count;z++)for(int x=0;x<17&&positions.size()<count;x++)positions.add(new Position(x*16,0,z*16));
        return asset(new Bounds(272,1,272,0,0,0),positions);
    }
    @Test void oversizedChunksRejectBeforeAnyAuthorityAccessAndReleaseReservation()throws Exception {
        var engine=new StructureEngine();var source=new Empty(true);
        for(int rotation=0;rotation<4;rotation++) {
            final int r=rotation;
            var failure=assertThrows(IOException.class,()->engine.prepare(spread(257),new Position(-17,160,-17),r,source,()->false,p->{}));
            assertTrue(failure.getMessage().contains("256 affected chunks"));assertFalse(engine.busy());assertEquals(0,source.calls);
        }
        try(var prepared=engine.prepare(spread(256),new Position(-17,160,-17),3,new Empty(false),()->false,p->{})) {
            assertEquals(256,prepared.cells().size());assertTrue(engine.busy());
        }
        assertFalse(engine.busy());
    }
    @Test void unalignedAnchorsCountExactRotatedDestinationChunks()throws Exception {
        var positions=new ArrayList<Position>();
        for(int z=0;z<16;z++)for(int x=0;x<16;x++)for(int dz:new int[]{0,15})for(int dx:new int[]{0,15})positions.add(new Position(x*16+dx,0,z*16+dz));
        var asset=asset(new Bounds(256,1,256,0,0,0),positions);var engine=new StructureEngine();
        try(var prepared=engine.prepare(asset,new Position(0,160,0),0,new Empty(false),()->false,p->{})){assertEquals(1024,prepared.cells().size());}
        for(int rotation=0;rotation<4;rotation++)for(int offset:new int[]{-1,1}) {
            int r=rotation;var source=new Empty(true);
            // A 180-degree turn moves the zero anchor to (255,255):
            // target (-1,-1) therefore aligns both minima at -256.
            if(rotation==2&&offset==-1) {
                try(var prepared=engine.prepare(asset,new Position(offset,160,offset),r,new Empty(false),()->false,p->{})){assertEquals(1024,prepared.cells().size());}
                continue;
            }
            assertThrows(IOException.class,()->engine.prepare(asset,new Position(offset,160,offset),r,source,()->false,p->{}));
            assertEquals(0,source.calls);assertFalse(engine.busy());
        }
    }
    @Test void emptyBoundingSpaceDoesNotConsumeChunkBudget()throws Exception {
        var asset=asset(new Bounds(1024,256,1024,0,0,0),List.of(new Position(0,0,0),new Position(1023,255,1023)));
        var engine=new StructureEngine();
        for(int r=0;r<4;r++)try(var prepared=engine.prepare(asset,new Position(-17,160,-17),r,new Empty(false),()->false,p->{})) {
            assertEquals(2,prepared.cells().size());
        }
        assertFalse(engine.busy());
    }
    @Test void cancellationAndInvalidCoordinatesRejectWithoutReadingAuthority()throws Exception {
        var source=new Empty(true);var engine=new StructureEngine();var checks=new AtomicInteger();
        assertThrows(CancellationException.class,()->engine.prepare(spread(257),new Position(0,160,0),0,source,()->checks.incrementAndGet()>3,p->{}));
        assertEquals(0,source.calls);assertFalse(engine.busy());
        var small=spread(1);
        assertThrows(IOException.class,()->engine.prepare(small,new Position(Integer.MAX_VALUE,160,0),0,source,()->false,p->{}));
        assertEquals(0,source.calls);assertFalse(engine.busy());
    }
}
