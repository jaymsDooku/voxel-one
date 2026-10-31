package dev.jayms.net;

import dev.jayms.net.model.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StructureOfflineSaveTest {
    @TempDir Path folder;
    @Test void versionEightAndNineMatchExistingWriterAndPatchCurrentSurvivalState()throws Exception {
        for(boolean component:List.of(false,true)) {
            Path path=folder.resolve("world-"+component+".dat");var local=new LocalGame(path,42);
            if(component){var voxels=new SparseVoxelOctree(8);voxels.set(1,2,3,0xffaabbcc);local.models.stageStructureModel(new ModelDefinition("Component",voxels));}
            for(int i=0;i<2051;i++){var edit=new Protocol.Edit(i,180,0,Blocks.STONE);local.edits.put(edit.key(),edit);}
            try(var staged=StagedOfflineSave.stage(path,local.seed,local.generatorVersion,local.models,(WorldVoxels.History)local.edits,()->false,p->{})) {
                assertFalse(Files.exists(path));
                local.inventory.add(Blocks.STONE,4);local.health=13;
                local.drops.put(1,new ItemDrop(1,Blocks.STONE,2,1,180,2));
                staged.publish(local.inventory,local.health,local.drops.values(),local.drops.size());
            }
            byte[] actual=Files.readAllBytes(path);var loaded=new LocalGame(path,0);
            assertEquals(13,loaded.health);assertEquals(4,loaded.inventory.countType(Blocks.STONE));assertEquals(local.drops,loaded.drops);
            assertEquals(new ArrayList<>(local.edits.values()),new ArrayList<>(loaded.edits.values()));
            local.save();assertArrayEquals(Files.readAllBytes(path),actual,"Existing save codec byte compatibility");
            assertNoPrivateTemps();
        }
    }
    @Test void cancellationAndCloseLeaveExistingSaveIntactAndRemovePrivateFiles()throws Exception {
        Path path=folder.resolve("world.dat");var local=new LocalGame(path,42);local.save();byte[] before=Files.readAllBytes(path);
        assertThrows(CancellationException.class,()->StagedOfflineSave.stage(path,42,local.generatorVersion,local.models,new WorldVoxels.History(),()->true,p->{}));
        try(var staged=StagedOfflineSave.stage(path,42,local.generatorVersion,local.models,new WorldVoxels.History(),()->false,p->{})){}
        assertArrayEquals(before,Files.readAllBytes(path));assertNoPrivateTemps();
    }
    @Test void cancellationDuringHistoryWriteRemovesPartialPrivateSave()throws Exception {
        Path path=folder.resolve("cancel-mid.dat");var local=new LocalGame(path,42);var edits=new WorldVoxels.History();
        for(int i=0;i<3000;i++){var edit=new Protocol.Edit(i,180,0,Blocks.STONE);edits.put(edit.key(),edit);}
        var cancelled=new java.util.concurrent.atomic.AtomicBoolean();
        assertThrows(CancellationException.class,()->StagedOfflineSave.stage(path,42,local.generatorVersion,local.models,edits,cancelled::get,p->cancelled.set(true)));
        assertTrue(cancelled.get());assertFalse(Files.exists(path));assertNoPrivateTemps();
    }
    @Test void failedAtomicPublicationRemovesPrivateFileAndKeepsTarget()throws Exception {
        Path path=folder.resolve("directory");Files.createDirectory(path);Files.writeString(path.resolve("keep"),"keep");var local=new LocalGame(folder.resolve("unused"),42);
        try(var staged=StagedOfflineSave.stage(path,42,local.generatorVersion,local.models,new WorldVoxels.History(),()->false,p->{})) {
            assertThrows(java.io.IOException.class,()->staged.publish(local.inventory,20,List.of(),0));
        }
        assertEquals("keep",Files.readString(path.resolve("keep")));assertNoPrivateTemps();
    }
    private void assertNoPrivateTemps()throws Exception {try(var paths=Files.list(folder)){assertFalse(paths.anyMatch(p->p.getFileName().toString().startsWith(".structure-offline-")));}}
}
