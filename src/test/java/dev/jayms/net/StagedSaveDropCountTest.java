package dev.jayms.net;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StagedSaveDropCountTest {
    @TempDir Path folder;
    @Test void invalidDeclaredCountsAndChangingRowsLeaveOldSaveIntact()throws Exception {
        var path=folder.resolve("world.dat");var local=new LocalGame(path,123);local.save();
        var original=Files.readAllBytes(path);var drop=new ItemDrop(1,Blocks.STONE,1,1,2,3);
        int[] counts={-1,Crafting.MAX_DROPS+1,0,2,1,1};
        List<Iterable<ItemDrop>> rows=List.of(List.of(),List.of(),List.of(drop),List.of(drop),List.of(drop,drop),Arrays.asList((ItemDrop)null));
        for(int i=0;i<counts.length;i++) {
            int count=counts[i];var values=rows.get(i);
            try(var staged=StagedOfflineSave.stageRegions(path,local.seed,local.generatorVersion,local.models,new WorldVoxels.History(),()->false,n->{})) {
                assertThrows(IOException.class,()->staged.publish(local.inventory,local.health,values,count));
            }
            assertArrayEquals(original,Files.readAllBytes(path));
            try(var files=Files.list(folder)){assertFalse(files.anyMatch(p->p.getFileName().toString().startsWith(".structure-offline-")));}
        }
        try(var staged=StagedOfflineSave.stageRegions(path,local.seed,local.generatorVersion,local.models,new WorldVoxels.History(),()->false,n->{})) {
            staged.publish(local.inventory,local.health,List.of(drop),1);
        }
        var loaded=new LocalGame(path,0);assertEquals(Map.of(1,drop),loaded.drops);
        assertEquals(local.seed,loaded.seed);assertEquals(local.health,loaded.health);
    }
}
