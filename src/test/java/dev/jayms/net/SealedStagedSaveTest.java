package dev.jayms.net;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class SealedStagedSaveTest {
 @TempDir Path folder;
 Path staged(Path directory)throws Exception{try(var files=Files.list(directory)){var matches=files.filter(p->p.getFileName().toString().startsWith(".structure-offline-")).toList();assertEquals(1,matches.size());return matches.get(0);}}
 @Test public void workerCompletesTailBeforeOwnerCommitAndOwnerChangeStillRejects()throws Exception {
  var tests=new ReadbackSaveSnapshotTest();tests.folder=Files.createDirectories(folder.resolve("readback"));
  try(var c=tests.new Context("complete")) {
   try(var prepared=c.stage(c.snapshot())) {
    var complete=new LocalGame(staged(c.fixture.folder),0);assertEquals(c.local.health,complete.health);assertEquals(c.local.drops,complete.drops);for(int i=0;i<Inventory.SIZE;i++){assertEquals(c.local.inventory.type(i),complete.inventory.type(i));assertEquals(c.local.inventory.count(i),complete.inventory.count(i));}assertArrayEquals(c.original,Files.readAllBytes(c.path));prepared.commit(()->false);
   }
   c.noPrefixes();assertFalse(c.local.replayComplete());assertThrows(IOException.class,c.local::save);
  }
  try(var c=tests.new Context("stale")) {
   try(var prepared=c.stage(c.snapshot())){assertNotNull(new LocalGame(staged(c.fixture.folder),0));c.local.health--;assertThrows(IOException.class,()->prepared.commit(()->false));assertArrayEquals(c.original,Files.readAllBytes(c.path));}c.noPrefixes();
  }
 }
 @Test public void sealingIsOneTimeAndGuardedPublicationRequiresCompleteStage()throws Exception {
  var f=new ReadbackMetadataTest();f.folder=Files.createDirectories(folder.resolve("once"));var path=f.fixture();var original=Files.readAllBytes(path);var local=new LocalGame(path,0);var generation=WorldSavePublication.capture(path,WorldSavePublication.MAX_GENERATION_BYTES,()->false);var rows=new WorldVoxels.History();WorldVoxels.remember(rows,new Protocol.Edit(3,160,0,Blocks.STONE));
  try(var stage=StagedOfflineSave.stageRegions(path,local.seed,local.generatorVersion,local.models,rows,()->false,n->{})) {
   assertThrows(IOException.class,()->stage.publishSealed(generation,()->false));assertArrayEquals(original,Files.readAllBytes(path));stage.sealTail(local.inventory,local.health,local.drops.values(),local.drops.size(),()->false);var file=staged(f.folder);var sealedBytes=Files.readAllBytes(file);assertNotNull(new LocalGame(file,0));assertThrows(IOException.class,()->stage.sealTail(local.inventory,local.health,List.of(),0,()->false));assertArrayEquals(sealedBytes,Files.readAllBytes(file));stage.publishSealed(generation,()->false);assertThrows(IOException.class,()->stage.publishSealed(generation,()->false));
  }
  var saved=new LocalGame(path,0);assertEquals(1,saved.edits.size());assertEquals(local.drops,saved.drops);assertEquals(local.health,saved.health);
 }
 @Test public void cancellationAndDestinationChangesPreservePriorBytes()throws Exception {
  var f=new ReadbackMetadataTest();f.folder=Files.createDirectories(folder.resolve("failure"));var path=f.fixture();var original=Files.readAllBytes(path);var local=new LocalGame(path,0);var rows=new WorldVoxels.History();var unrelated=Files.write(f.folder.resolve("retained.tmp"),new byte[]{7,8,9});
  try(var stage=StagedOfflineSave.stageRegions(path,local.seed,local.generatorVersion,local.models,rows,()->false,n->{})){assertThrows(CancellationException.class,()->stage.sealTail(local.inventory,local.health,local.drops.values(),local.drops.size(),()->true));assertArrayEquals(original,Files.readAllBytes(path));}assertArrayEquals(new byte[]{7,8,9},Files.readAllBytes(unrelated));
  var generation=WorldSavePublication.capture(path,WorldSavePublication.MAX_GENERATION_BYTES,()->false);
  try(var stage=StagedOfflineSave.stageRegions(path,local.seed,local.generatorVersion,local.models,rows,()->false,n->{})) {
   stage.sealTail(local.inventory,local.health,local.drops.values(),local.drops.size(),()->false);byte[] changed=original.clone();changed[changed.length-1]^=1;Files.write(path,changed);assertThrows(IOException.class,()->stage.publishSealed(generation,()->false));assertArrayEquals(changed,Files.readAllBytes(path));
  }
  Files.write(path,original);final var current=WorldSavePublication.capture(path,WorldSavePublication.MAX_GENERATION_BYTES,()->false);
  try(var stage=StagedOfflineSave.stageRegions(path,local.seed,local.generatorVersion,local.models,rows,()->false,n->{})){stage.sealTail(local.inventory,local.health,local.drops.values(),local.drops.size(),()->false);assertThrows(CancellationException.class,()->stage.publishSealed(current,()->true));assertArrayEquals(original,Files.readAllBytes(path));}
  try(var files=Files.list(f.folder)){assertFalse(files.anyMatch(p->p.getFileName().toString().startsWith(".structure-offline-")));}assertArrayEquals(new byte[]{7,8,9},Files.readAllBytes(unrelated));
 }
}
