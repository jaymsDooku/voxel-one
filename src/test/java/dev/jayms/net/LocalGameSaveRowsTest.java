package dev.jayms.net;

import dev.jayms.net.model.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LocalGameSaveRowsTest {
    @TempDir Path folder;
    private static Map<String,Protocol.Edit> rows(int declared,List<Protocol.Edit> actual) {
        return new AbstractMap<>() {
            public int size(){return declared;}
            public Set<Entry<String,Protocol.Edit>> entrySet(){throw new AssertionError("Use serialization values");}
            public Collection<Protocol.Edit> values(){return actual;}
        };
    }
    @Test void inconsistentOrInvalidRowsPreservePublishedWorldAndAllowRetry()throws Exception {
        for(boolean component:List.of(false,true)) {
            Path path=folder.resolve("rows-"+component+".dat"),legacy=path.resolveSibling(path.getFileName()+".tmp");
            var game=new LocalGame(path,42);game.health=17;
            if(component){var voxels=new SparseVoxelOctree(8);voxels.set(1,2,3,0xff123456);game.models.stageStructureModel(new ModelDefinition("Row fixture",voxels));}
            var valid=new Protocol.Edit(1,160,2,Blocks.STONE);
            game.edits=rows(1,List.of(valid));game.save();byte[] prior=Files.readAllBytes(path);Files.writeString(legacy,"Unowned scratch");
            var cases=List.of(rows(2,List.of(valid)),rows(0,List.of(valid)),
                    rows(1,Arrays.asList((Protocol.Edit)null)),rows(1,List.of(new Protocol.Edit(Integer.MAX_VALUE,160,0,Blocks.STONE))));
            for(var malformed:cases) {
                game.edits=malformed;assertThrows(IOException.class,game::save);
                assertArrayEquals(prior,Files.readAllBytes(path));assertEquals("Unowned scratch",Files.readString(legacy));
                assertEquals(17,new LocalGame(path,0).health);
                try(var files=Files.list(folder)){assertFalse(files.anyMatch(p->p.getFileName().toString().startsWith(".world-save-")));}
            }
            game.edits=rows(1,List.of(valid));game.save();
            assertEquals(List.of(valid),new ArrayList<>(new LocalGame(path,0).edits.values()));
            assertEquals("Unowned scratch",Files.readString(legacy));
        }
    }
}
