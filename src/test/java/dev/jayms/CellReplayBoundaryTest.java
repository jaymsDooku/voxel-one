package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CellReplayBoundaryTest {
    @Test void fragmentedRowsPreserveMaximumLocalAddressesLedColorsAndExactOrder() throws Exception {
        var tree=new SparseVoxelOctree(16);var random=new Random(60317);
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
            tree.fill(x,y,z,x+1,y+1,z+1,0xfe000000|random.nextInt(1<<24));
        var state=new Protocol.CellState(-17,180,-33,tree);var expected=new WorldVoxels.History();
        WorldVoxels.rememberCell(expected,state);var replay=WorldVoxels.prepareCellReplay(state);
        assertEquals(4097,replay.editCount());
        tree.fill(0,0,0,16,16,16,0);var actual=new WorldVoxels.History();WorldVoxels.rememberCell(actual,replay);
        assertEquals(new ArrayList<>(expected.entrySet()),new ArrayList<>(actual.entrySet()));
        assertEquals(expected.revision(),actual.revision());
        var legacy=new LinkedHashMap<String,Protocol.Edit>();WorldVoxels.rememberCell(legacy,replay);
        assertEquals(new ArrayList<>(expected.entrySet()),new ArrayList<>(legacy.entrySet()));
    }
    @Test void fullRangeModelIdsAndLedColorsRoundtripWithoutTruncation() {
        for(int type:new int[]{0,Blocks.STONE,Blocks.FLOWER_POT,1024,66559}) {
            var tree=new SparseVoxelOctree(16);if(type!=0)tree.fill(0,0,0,16,16,16,WorldVoxels.encode(type));
            compare(new Protocol.CellState(-16,180,15,tree));
        }
        for(int rgb:new int[]{0,0xffffff,0x800000,0xabcdef}) {
            var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,0xfe000000|rgb);
            tree.fill(15,15,15,16,16,16,0xfe000000|(rgb^0x111111));compare(new Protocol.CellState(-1,180,-1,tree));
        }
    }
    private static void compare(Protocol.CellState state) {
        var expected=new WorldVoxels.History();WorldVoxels.rememberCell(expected,state);
        var actual=new WorldVoxels.History();WorldVoxels.rememberCell(actual,WorldVoxels.prepareCellReplay(state));
        assertEquals(new ArrayList<>(expected.entrySet()),new ArrayList<>(actual.entrySet()));assertEquals(expected.revision(),actual.revision());
    }
}
