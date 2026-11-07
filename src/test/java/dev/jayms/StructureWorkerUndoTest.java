package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.structure.*;
import dev.jayms.ui.StructureMenu;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static dev.jayms.net.structure.StructurePackage.*;

class StructureWorkerUndoTest {
    @TempDir Path folder;
    static void pump(StructureMenu menu)throws Exception {
        long deadline=System.nanoTime()+30_000_000_000L;
        while(menu.busy&&System.nanoTime()<deadline){menu.pump();Thread.sleep(1);}assertFalse(menu.busy,"Menu operation deadline");
    }
    @Test void menuAcknowledgesUndoBeforeOwnerReadsAndCancellationPreservesFileAndRetry()throws Exception {
        var path=folder.resolve("menu-world.dat");var local=new LocalGame(path,42);var snapshots=new AtomicInteger();
        try(var world=new World(42,local.models){@Override public StructureSnapshotPlan beginStructureSnapshot(){snapshots.incrementAndGet();return super.beginStructureSnapshot();}};var menu=new StructureMenu(folder.resolve("library"))) {
            menu.selected=StructureOfflineHistoryStagingTest.asset();menu.target=new Position(320,180,320);
            menu.execute("place",world,local,null);pump(menu);assertTrue(menu.message.startsWith("Placed and saved"),menu.message);byte[] placed=Files.readAllBytes(path);
            int before=snapshots.get();menu.execute("undo",world,local,null);assertTrue(menu.busy);assertEquals(before,snapshots.get(),"Undo returned before queued owner reads");
            menu.cancel(null);pump(menu);assertTrue(menu.message.contains("Cancelled"),menu.message);assertArrayEquals(placed,Files.readAllBytes(path));assertEquals(Blocks.STONE,world.sample(320,180,320));
            menu.execute("undo",world,local,null);pump(menu);assertTrue(menu.message.contains("undone"),menu.message);assertTrue(world.cell(320,180,320).isEmpty());
            var loaded=new LocalGame(path,0);assertEquals(local.edits,loaded.edits);
        }
    }
    @Test void undoStagesHistoryAndSaveOffOwnerThenPreservesCurrentSurvivalAtPublication()throws Exception {
        var path=folder.resolve("worker-world.dat");var local=new LocalGame(path,42);StructureOfflineHistoryStagingTest.populate(local,2051);var owner=Thread.currentThread();
        try(var world=new World(42,local.models);var queue=new StructureOwnerQueue()) {
            var engine=new StructureEngine();try(var placement=engine.prepare(StructureOfflineHistoryStagingTest.asset(),new Position(320,180,320),0,world.structureSnapshot(),()->false,p->{})){placement.commit("fixture",local.structureAuthority(world));}
            byte[] previous=Files.readAllBytes(path);
            try(var undo=engine.beginUndo("fixture",()->false)) {
                var history=StructureOfflineHistoryStagingTest.worker(queue,()->{
                    var box=undo.footprint();var source=StructureRegionSnapshot.capture(world,new Position(box.minX(),box.minY(),box.minZ()),box.width(),box.height(),box.depth(),queue,()->false,p->assertNotSame(owner,Thread.currentThread()));
                    undo.validate(source,p->assertNotSame(owner,Thread.currentThread()));
                    var staged=local.stageStructureHistory(undo.cells(),queue,()->false,p->assertNotSame(owner,Thread.currentThread()));
                    try{local.stageStructureSave(staged,undo.modelSnapshot(),()->false,p->assertNotSame(owner,Thread.currentThread()));return staged;}catch(Exception failure){if(staged!=null)staged.close();throw failure;}
                },()->{},new AtomicInteger());
                try(history) {
                    assertArrayEquals(previous,Files.readAllBytes(path));assertEquals(Blocks.STONE,world.sample(320,180,320));
                    local.health=17;local.inventory.add(Blocks.STONE,3);local.drops.put(1,new ItemDrop(1,Blocks.STONE,2,1,180,1));
                    undo.commit(local.structureAuthority(world,()->null,history));
                }
            }
            assertFalse(engine.busy());assertTrue(world.cell(320,180,320).isEmpty());
            var loaded=new LocalGame(path,0);assertEquals(local.edits,loaded.edits);assertEquals(17,loaded.health);assertEquals(3,loaded.inventory.countType(Blocks.STONE));assertEquals(local.drops,loaded.drops);
            assertThrows(java.io.IOException.class,()->engine.beginUndo("fixture",()->false));
        }
    }
    @Test void failedUndoPublicationRestoresLiveStateAndRetainsRecordForRetry()throws Exception {
        var path=folder.resolve("blocked-world.dat");var local=new LocalGame(path,42);
        try(var world=new World(42,local.models);var queue=new StructureOwnerQueue()) {
            var engine=new StructureEngine();try(var placement=engine.prepare(StructureOfflineHistoryStagingTest.asset(),new Position(320,180,320),0,world.structureSnapshot(),()->false,p->{})){placement.commit("fixture",local.structureAuthority(world));}
            byte[] previous=Files.readAllBytes(path);var edits=new LinkedHashMap<>(local.edits);var retained=folder.resolve("prior-world.dat");
            try(var undo=engine.beginUndo("fixture",()->false)) {
                undo.validate(world.structureSnapshot(),p->{});
                var history=StructureOfflineHistoryStagingTest.worker(queue,()->{
                    var staged=local.stageStructureHistory(undo.cells(),queue,()->false,p->{});
                    try{local.stageStructureSave(staged,undo.modelSnapshot(),()->false,p->{});return staged;}catch(Exception failure){if(staged!=null)staged.close();throw failure;}
                },()->{},new AtomicInteger());
                try(history) {
                    Files.move(path,retained);Files.createDirectory(path);
                    assertThrows(java.io.IOException.class,()->undo.commit(local.structureAuthority(world,()->null,history)));
                    assertEquals(Blocks.STONE,world.sample(320,180,320));assertEquals(edits,local.edits);assertArrayEquals(previous,Files.readAllBytes(retained));assertTrue(Files.isDirectory(path));
                    Files.delete(path);Files.move(retained,path);
                }
            }
            assertFalse(engine.busy());engine.undo("fixture",local.structureAuthority(world));assertTrue(world.cell(320,180,320).isEmpty());
        }
    }

}
