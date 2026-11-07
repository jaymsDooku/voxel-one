package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static dev.jayms.net.structure.StructurePackage.*;

class StructureRollbackImagesTest {
    @TempDir Path folder;
    static final Position DEST=new Position(320,180,320);
    static class CountingWorld extends World {
        int reads;
        CountingWorld(ModelLibrary models){super(42,models);}
        @Override public SparseVoxelOctree cell(int x,int y,int z){reads++;return super.cell(x,y,z);}
    }
    @Test void verifiedPlacementAndPreparedUndoDoNotRebuildLiveRollbackImages()throws Exception {
        var local=new LocalGame(folder.resolve("verified.dat"),42);
        try(var world=new CountingWorld(local.models)) {
            var engine=new StructureEngine();var authority=local.structureAuthority(world);
            try(var plan=engine.prepare(StructureOfflineHistoryStagingTest.asset(),DEST,0,world.structureSnapshot(),()->false,p->{})) {
                world.reads=0;plan.commit("fixture",authority);assertEquals(0,world.reads,"Verified empty before-image needs no duplicate live snapshot");
            }
            try(var undo=engine.beginUndo("fixture",()->false)) {
                undo.validate(world.structureSnapshot(),p->{});world.reads=0;undo.commit(authority);assertEquals(0,world.reads,"Trusted undo before-image needs no duplicate live snapshot");
            }
            assertTrue(world.cell(DEST.x(),DEST.y(),DEST.z()).isEmpty());
            try(var plan=engine.prepare(StructureOfflineHistoryStagingTest.asset(),DEST,0,world.structureSnapshot(),()->false,p->{})){plan.commit("fixture",authority);}
            world.reads=0;engine.undo("fixture",authority);assertEquals(1,world.reads,"Synchronous undo keeps its required equality check, without another rollback read");
        }
    }
    @Test void directLegacyPublicationStillCapturesCurrentRollbackImages()throws Exception {
        var local=new LocalGame(folder.resolve("direct.dat"),42);
        try(var world=new CountingWorld(local.models)) {
            var cells=Map.of(DEST,WorldCellReplacementTest.uniform(Blocks.STONE).freeze());
            world.reads=0;local.structureAuthority(world).commit(local.models.copy(),cells);assertEquals(1,world.reads);
            assertEquals(Blocks.STONE,world.sample(DEST.x(),DEST.y(),DEST.z()));
        }
    }
}
