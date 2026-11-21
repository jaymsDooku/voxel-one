package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import dev.jayms.net.structure.StructurePackage.Position;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldHistoryReplacementSizeTest {
    static SparseVoxelOctree tree(int kind) {
        var t=new SparseVoxelOctree(16);
        if(kind==1)t.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        if(kind==2)for(int i=0;i<16;i++)t.set(i,i,i,WorldVoxels.encode(Blocks.GLASS));
        return t.freeze();
    }
    static void install(WorldVoxels.History h,Position p,SparseVoxelOctree t) {
        WorldVoxels.rememberCell(h,new Protocol.CellState(p.x(),p.y(),p.z(),t));
    }
    @Test void projectedCountMatchesCanonicalHistoryAcrossReplacementOrders()throws Exception {
        var random=new Random(20261109);
        for(int trial=0;trial<100;trial++) {
            var history=new WorldVoxels.History();var replacements=new LinkedHashMap<Position,SparseVoxelOctree>();
            for(int i=0;i<30;i++) {
                var p=new Position(i-15,180,i%5-2);install(history,p,tree(random.nextInt(3)));
                if(random.nextBoolean())replacements.put(p,tree(random.nextInt(3)));
            }
            replacements.put(new Position(60,180,-60),tree(trial%3));
            var before=new ArrayList<>(history.entrySet());long revision=history.revision();
            var expected=WorldVoxels.History.copyOf(history);
            for(var e:replacements.entrySet())install(expected,e.getKey(),e.getValue());
            assertEquals(expected.size(),history.replacementSize(replacements,()->false));
            var reversed=new ArrayList<>(replacements.entrySet());Collections.reverse(reversed);
            var reverse=new LinkedHashMap<Position,SparseVoxelOctree>();reversed.forEach(e->reverse.put(e.getKey(),e.getValue()));
            assertEquals(expected.size(),history.replacementSize(reverse,()->false));
            assertEquals(before,new ArrayList<>(history.entrySet()));assertEquals(revision,history.revision());
        }
    }
    @Test void fullCellClearSharesKeyAndLaterShrinkingCellFreesCapacity()throws Exception {
        var h=new WorldVoxels.History();var a=new Position(0,180,0);var b=new Position(1,180,0);
        install(h,a,tree(1));install(h,b,tree(2));assertEquals(18,h.size());
        var replacements=new LinkedHashMap<Position,SparseVoxelOctree>();
        replacements.put(a,tree(2));replacements.put(b,tree(0));
        assertEquals(18,h.replacementSize(replacements,()->false));
        assertEquals(2,h.replacementSize(Map.of(b,tree(1)),()->false));
    }
    @Test void cancellationAndInvalidInputNeverChangeSource()throws Exception {
        var h=new WorldVoxels.History();var p=new Position(0,180,0);install(h,p,tree(1));
        var before=new ArrayList<>(h.entrySet());var calls=new AtomicInteger();
        assertThrows(CancellationException.class,()->h.replacementSize(Map.of(p,tree(2)),()->calls.incrementAndGet()>1));
        assertThrows(IllegalArgumentException.class,()->h.replacementSize(Map.of(p,new SparseVoxelOctree(8)),()->false));
        assertEquals(before,new ArrayList<>(h.entrySet()));
    }
}
