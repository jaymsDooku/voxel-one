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
    @Test void cancellationDuringModelBytesStopsBeforeHistoryAndKeepsPriorSave()throws Exception {
        Path path=folder.resolve("cancel-models.dat");var local=new LocalGame(path,42);local.save();byte[] before=Files.readAllBytes(path);
        var models=local.models.copy();
        for(int index=0;index<8;index++) {
            var voxels=new SparseVoxelOctree(16);
            for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++)
                voxels.set(x,y,z,0xff000000|((x+y+z)%2==0?0x123400+index:0xabcdef-index));
            models.stageStructureModel(new ModelDefinition("Cancel component "+index,voxels));
        }
        var encoded=new java.io.ByteArrayOutputStream();models.writeAll(new java.io.DataOutputStream(encoded));
        assertTrue(encoded.size()>128*1024,"Fixture must reach multiple buffered model writes");
        var checks=new java.util.concurrent.atomic.AtomicInteger();var privateBytes=new java.util.concurrent.atomic.AtomicLong();
        var historyProgress=new java.util.concurrent.atomic.AtomicBoolean();
        assertThrows(CancellationException.class,()->StagedOfflineSave.stage(path,42,local.generatorVersion,models,new WorldVoxels.History(),()->{
            if(checks.incrementAndGet()<3)return false;
            try(var files=Files.list(folder)) {
                var temp=files.filter(p->p.getFileName().toString().startsWith(".structure-offline-")).findFirst().orElseThrow();
                privateBytes.set(Files.size(temp));
            }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
            return true;
        },p->historyProgress.set(true)));
        assertTrue(privateBytes.get()>0&&privateBytes.get()<=64*1024,"Only one bounded file write may precede cancellation");assertFalse(historyProgress.get());
        assertArrayEquals(before,Files.readAllBytes(path));assertTrue(local.edits.isEmpty());assertNoPrivateTemps();
    }
    @Test void interruptionSplitsOneLargeModelWriteAndRemovesPrivateSave()throws Exception {
        Path path=folder.resolve("interrupt-model.dat");var local=new LocalGame(path,42);local.save();byte[] before=Files.readAllBytes(path);
        var models=local.models.copy();var voxels=new SparseVoxelOctree(32);
        for(int x=0;x<32;x++)for(int y=0;y<32;y++)for(int z=0;z<32;z++)
            voxels.set(x,y,z,(x+y+z)%2==0?0xff123456:0xffabcdef);
        var definition=new ModelDefinition("Large component",voxels);models.stageStructureModel(definition);
        var encoded=new java.io.ByteArrayOutputStream();definition.write(new java.io.DataOutputStream(encoded));
        assertTrue(encoded.size()>128*1024,"One model must exceed the delegated write bound");
        var privateBytes=new java.util.concurrent.atomic.AtomicLong();var historyProgress=new java.util.concurrent.atomic.AtomicBoolean();
        try {
            assertThrows(CancellationException.class,()->StagedOfflineSave.stage(path,42,local.generatorVersion,models,new WorldVoxels.History(),()->{
                try(var files=Files.list(folder)) {
                    var temp=files.filter(p->p.getFileName().toString().startsWith(".structure-offline-")).findFirst().orElseThrow();
                    long bytes=Files.size(temp);if(bytes>=64*1024){privateBytes.set(bytes);Thread.currentThread().interrupt();}
                }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
                return false;
            },p->historyProgress.set(true)));
            assertTrue(Thread.currentThread().isInterrupted());
        }finally{Thread.interrupted();}
        assertTrue(privateBytes.get()>=64*1024&&privateBytes.get()<128*1024);
        assertFalse(historyProgress.get());assertArrayEquals(before,Files.readAllBytes(path));assertNoPrivateTemps();
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
