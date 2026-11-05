package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import static dev.jayms.net.structure.StructurePackage.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StructureOfflineHistoryStagingTest {
    @TempDir Path folder;
    static StructurePackage asset()throws Exception {
        var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        return new StructurePackage("Worker history",new Bounds(1,1,1,0,0,0),List.of(),List.of(new Cell(new Position(0,0,0),tree,-1)));
    }
    static void populate(LocalGame local,int count) {
        for(int i=0;i<count;i++){var e=new Protocol.Edit(i%512,180,500+i/512,Blocks.BRICKS);local.edits.put(e.key(),e);}
    }
    static <T> T worker(StructureOwnerQueue queue,Callable<T> job,Runnable beforePump,AtomicInteger reads)throws Exception {
        var task=new FutureTask<T>(job);var thread=new Thread(task,"synthetic-history-worker");thread.start();
        try {
            long deadline=System.nanoTime()+10_000_000_000L;
            while(!task.isDone()&&System.nanoTime()<deadline){beforePump.run();if(queue.pump())reads.incrementAndGet();Thread.sleep(1);}
            assertTrue(task.isDone(),"Worker owner-read deadline");return task.get();
        } finally {task.cancel(true);thread.join(2000);assertFalse(thread.isAlive(),"Worker released");}
    }
    @Test void boundedOwnerBatchesBuildOffThreadAndCommitCurrentSurvivalState()throws Exception {
        var path=folder.resolve("world.dat");var local=new LocalGame(path,42);populate(local,2051);
        var before=new LinkedHashMap<>(local.edits);var owner=Thread.currentThread();var reads=new AtomicInteger();var progress=new AtomicInteger();
        try(var queue=new StructureOwnerQueue();var world=new World(42,local.models)) {
            var engine=new StructureEngine();
            try(var prepared=engine.prepare(asset(),new Position(320,180,320),0,world.structureSnapshot(),()->false,p->{})) {
                var history=worker(queue,()->local.stageStructureHistory(prepared.cells(),queue,()->false,p->{assertNotSame(owner,Thread.currentThread());progress.set(p);}),()->{},reads);
                assertNotNull(history);assertEquals(2,reads.get()); // Capture plus one bounded group.
                assertEquals(100,progress.get());assertEquals(before,local.edits);assertTrue(world.cell(320,180,320).isEmpty());
                worker(queue,()->{local.stageStructureSave(history,prepared.modelSnapshot(),()->false,p->assertNotSame(owner,Thread.currentThread()));return true;},()->{},new AtomicInteger());
                assertFalse(Files.exists(path));
                local.health=17;local.inventory.add(Blocks.STONE,3);
                local.drops.put(1,new ItemDrop(1,Blocks.STONE,2,1,180,1));
                prepared.commit("fixture",local.structureAuthority(world,()->null,history));
            }
            assertFalse(engine.busy());assertEquals(Blocks.STONE,world.sample(320,180,320));
            try(var in=new java.io.DataInputStream(Files.newInputStream(path))){assertEquals(10,in.readInt(),"Production staged placement uses regional replay");}
            var loaded=new LocalGame(path,0);assertEquals(local.drops,loaded.drops);assertEquals(17,loaded.health);assertEquals(3,loaded.inventory.countType(Blocks.STONE));
            var expected=new WorldVoxels.History();expected.putAll(before);
            WorldVoxels.rememberCell(expected,new Protocol.CellState(320,180,320,world.cell(320,180,320)));
            assertEquals(new ArrayList<>(expected.entrySet()),new ArrayList<>(loaded.edits.entrySet()));
            engine.undo("fixture",local.structureAuthority(world));assertTrue(world.cell(320,180,320).isEmpty());
        }
    }
    @Test void historyChangesAfterStagingRejectBeforeAnyPublication()throws Exception {
        for(int action=0;action<3;action++) {
            var path=folder.resolve("stale-"+action+".dat");var local=new LocalGame(path,42);populate(local,1);int models=local.models.entries().size();
            try(var queue=new StructureOwnerQueue();var world=new World(42,local.models)) {
                var engine=new StructureEngine();
                try(var prepared=engine.prepare(asset(),new Position(320,180,320),0,world.structureSnapshot(),()->false,p->{})) {
                    var history=worker(queue,()->local.stageStructureHistory(prepared.cells(),queue,()->false,p->{}),()->{},new AtomicInteger());
                    var first=local.edits.values().iterator().next();
                    switch(action) {
                        case 0 -> {var edit=new Protocol.Edit(700,180,700,Blocks.WOOD);local.edits.put(edit.key(),edit);}
                        case 1 -> local.edits.put(first.key(),first); // Equal replacement still changes revision.
                        default -> local.edits=WorldVoxels.History.copyOf(local.edits);
                    }
                    var expected=new ArrayList<>(local.edits.entrySet());
                    assertThrows(IOException.class,()->prepared.commit("fixture",local.structureAuthority(world,()->null,history)));
                    assertEquals(expected,new ArrayList<>(local.edits.entrySet()));assertTrue(world.cell(320,180,320).isEmpty());
                    assertFalse(Files.exists(path));assertFalse(engine.busy());assertEquals(models,local.models.entries().size());
                }
            }
        }
    }
    @Test void stagedSavePublicationFailureRollsBackWorldAndHistory()throws Exception {
        var path=folder.resolve("publication-failure.dat");var local=new LocalGame(path,42);populate(local,1);
        var before=new ArrayList<>(local.edits.entrySet());int models=local.models.entries().size();
        try(var queue=new StructureOwnerQueue();var world=new World(42,local.models)) {
            var engine=new StructureEngine();
            try(var prepared=engine.prepare(asset(),new Position(320,180,320),0,world.structureSnapshot(),()->false,p->{});
                var history=worker(queue,()->local.stageStructureHistory(prepared.cells(),queue,()->false,p->{}),()->{},new AtomicInteger())) {
                worker(queue,()->{local.stageStructureSave(history,prepared.modelSnapshot(),()->false,p->{});return true;},()->{},new AtomicInteger());
                Files.createDirectory(path);Files.writeString(path.resolve("keep"),"keep");
                assertThrows(IOException.class,()->prepared.commit("fixture",local.structureAuthority(world,()->null,history)));
                assertEquals(before,new ArrayList<>(local.edits.entrySet()));assertEquals(models,local.models.entries().size());
                assertTrue(world.cell(320,180,320).isEmpty());assertFalse(engine.busy());assertEquals("keep",Files.readString(path.resolve("keep")));
                assertThrows(IOException.class,()->engine.undo("fixture",local.structureAuthority(world)));
            }
            try(var files=Files.list(folder)){assertFalse(files.anyMatch(p->p.getFileName().toString().startsWith(".structure-offline-")));}
        }
    }
    @Test void stagedModelMismatchRejectsBeforeWorldOrSavePublication()throws Exception {
        var path=folder.resolve("model-mismatch.dat");var local=new LocalGame(path,42);
        try(var queue=new StructureOwnerQueue();var world=new World(42,local.models)) {
            var engine=new StructureEngine();
            try(var prepared=engine.prepare(asset(),new Position(320,180,320),0,world.structureSnapshot(),()->false,p->{});
                var history=worker(queue,()->local.stageStructureHistory(prepared.cells(),queue,()->false,p->{}),()->{},new AtomicInteger())) {
                var wrong=prepared.modelSnapshot();var voxels=new SparseVoxelOctree(8);voxels.set(1,2,3,0xffaabbcc);
                wrong.stageStructureModel(new ModelDefinition("Wrong model",voxels));
                worker(queue,()->{local.stageStructureSave(history,wrong,()->false,p->{});return true;},()->{},new AtomicInteger());
                assertThrows(IOException.class,()->prepared.commit("fixture",local.structureAuthority(world,()->null,history)));
                assertTrue(local.edits.isEmpty());assertTrue(world.cell(320,180,320).isEmpty());assertFalse(Files.exists(path));assertFalse(engine.busy());
            }
            try(var files=Files.list(folder)){assertFalse(files.anyMatch(p->p.getFileName().toString().startsWith(".structure-offline-")));}
        }
    }
    @Test void mutationBetweenOwnerBatchesRejectsBeforeIteratorResume()throws Exception {
        var local=new LocalGame(folder.resolve("mid-copy.dat"),42);populate(local,33000);
        var copied=new AtomicBoolean();var changed=new AtomicBoolean();var reads=new AtomicInteger();
        try(var queue=new StructureOwnerQueue()) {
            var failure=assertThrows(ExecutionException.class,()->worker(queue,
                    ()->local.stageStructureHistory(Map.of(),queue,()->false,p->copied.set(true)),
                    ()->{if(copied.get()&&changed.compareAndSet(false,true)){var e=local.edits.values().iterator().next();local.edits.put(e.key(),e);}},reads));
            assertInstanceOf(IOException.class,failure.getCause());assertTrue(failure.getCause().getMessage().contains("changed"));
            assertTrue(changed.get());assertEquals(33000,local.edits.size());assertEquals(3,reads.get());
        }
    }
    @Test void cancellationLeavesQueueReusableAndSourceUntouched()throws Exception {
        var local=new LocalGame(folder.resolve("cancel.dat"),42);populate(local,2051);var before=new LinkedHashMap<>(local.edits);
        var cancel=new AtomicBoolean();var copied=new AtomicBoolean();
        try(var queue=new StructureOwnerQueue()) {
            var failure=assertThrows(ExecutionException.class,()->worker(queue,
                    ()->local.stageStructureHistory(Map.of(),queue,cancel::get,p->{copied.set(true);cancel.set(true);}),()->{},new AtomicInteger()));
            assertInstanceOf(CancellationException.class,failure.getCause());assertTrue(copied.get());assertEquals(before,local.edits);
            assertNotNull(worker(queue,()->local.stageStructureHistory(Map.of(),queue,()->false,p->{}),()->{},new AtomicInteger()));
            assertEquals(before,local.edits);assertFalse(Files.exists(folder.resolve("cancel.dat")));
        }
    }
    @Test void historyCannotBeAppliedToAnotherCellMapOrWorldAndLegacyMapsStillWork()throws Exception {
        var local=new LocalGame(folder.resolve("binding.dat"),42);var other=new LocalGame(folder.resolve("other.dat"),42);
        var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));tree.freeze();
        var cells=Map.of(new Position(320,180,320),tree);
        try(var queue=new StructureOwnerQueue();var world=new World(42,local.models)) {
            var history=worker(queue,()->local.stageStructureHistory(cells,queue,()->false,p->{}),()->{},new AtomicInteger());
            assertThrows(IOException.class,()->local.structureAuthority(world,()->null,history).commit(local.models.copy(),new LinkedHashMap<>(cells)));
            assertThrows(IOException.class,()->other.structureAuthority(world,()->null,history).commit(other.models.copy(),cells));
            assertTrue(local.edits.isEmpty());assertTrue(other.edits.isEmpty());assertTrue(world.cell(320,180,320).isEmpty());
            local.edits=new LinkedHashMap<>();
            assertNull(worker(queue,()->local.stageStructureHistory(cells,queue,()->false,p->{}),()->{},new AtomicInteger()));
            local.structureAuthority(world).commit(local.models.copy(),cells);
            assertEquals(Blocks.STONE,world.sample(320,180,320));assertEquals(1,new LocalGame(folder.resolve("binding.dat"),0).edits.size());
        }
    }
    @Test void closingQueueReleasesWorkerAndNeverRunsPendingRead()throws Exception {
        var queue=new StructureOwnerQueue();var started=new CountDownLatch(1);var executed=new AtomicBoolean();
        var task=new FutureTask<>(()->{started.countDown();return queue.call(()->{executed.set(true);return 7;},()->false);});
        var thread=new Thread(task,"synthetic-owner-close");thread.start();assertTrue(started.await(2,TimeUnit.SECONDS));queue.close();
        try {assertThrows(ExecutionException.class,()->task.get(2,TimeUnit.SECONDS));assertFalse(executed.get());assertFalse(queue.pump());}
        finally {thread.interrupt();thread.join(2000);assertFalse(thread.isAlive());}
        assertThrows(IllegalStateException.class,()->queue.call(()->7,()->false));
    }
    @Test void menuHistoryFailureReleasesPlacementReservationForRetry()throws Exception {
        var path=folder.resolve("menu.dat");var local=new LocalGame(path,42);populate(local,33000);
        try(var world=new World(42,local.models);var menu=new dev.jayms.ui.StructureMenu(folder.resolve("library"))) {
            menu.selected=asset();menu.target=new Position(320,180,320);menu.execute("place",world,local,null);
            boolean changed=false;long deadline=System.nanoTime()+20_000_000_000L;
            while(menu.busy&&System.nanoTime()<deadline) {
                if(!changed&&menu.progress>50) {
                    var edit=local.edits.values().iterator().next();local.edits.put(edit.key(),edit);changed=true;
                }
                menu.pump();Thread.sleep(1);
            }
            assertTrue(changed);assertFalse(menu.busy);assertTrue(menu.message.contains("changed"),menu.message);
            assertTrue(world.cell(320,180,320).isEmpty());assertFalse(Files.exists(path));
            menu.execute("place",world,local,null);deadline=System.nanoTime()+20_000_000_000L;
            while(menu.busy&&System.nanoTime()<deadline){menu.pump();Thread.sleep(1);}
            assertFalse(menu.busy);assertTrue(menu.message.startsWith("Placed and saved"),menu.message);
            assertEquals(Blocks.STONE,world.sample(320,180,320));assertEquals(33001,new LocalGame(path,0).edits.size());
        }
    }
}
