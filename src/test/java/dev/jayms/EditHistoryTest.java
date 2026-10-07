package dev.jayms;

import dev.jayms.net.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EditHistoryTest {
    @Test void indexedHistoryMatchesLegacyReplayForNestedEditsAndRemoval() {
        var indexed = new WorldVoxels.History();
        Map<String,Protocol.Edit> legacy = new LinkedHashMap<>();
        var random = new Random(42);
        for(int i=0;i<1000;i++) {
            int depth=random.nextInt(5), side=1<<depth;
            var edit=new Protocol.Edit(random.nextInt(4),33,0,
                    i%5==0?0:Blocks.piece(Blocks.WOOD,depth),depth,
                    random.nextInt(side),random.nextInt(side),random.nextInt(side));
            WorldVoxels.remember(indexed,edit); WorldVoxels.remember(legacy,edit);
            assertEquals(new ArrayList<>(legacy.values()),new ArrayList<>(indexed.values()));
        }
        var key=legacy.keySet().iterator().next(); indexed.remove(key); legacy.remove(key);
        var root=new Protocol.Edit(0,33,0,Blocks.GLASS);
        WorldVoxels.remember(indexed,root); WorldVoxels.remember(legacy,root);
        assertEquals(new ArrayList<>(legacy.values()),new ArrayList<>(indexed.values()));
        indexed.clear(); assertTrue(indexed.isEmpty());
        WorldVoxels.remember(indexed,root); assertEquals(List.of(root),new ArrayList<>(indexed.values()));
    }
}
