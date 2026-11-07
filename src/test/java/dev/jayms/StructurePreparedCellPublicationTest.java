package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import dev.jayms.net.structure.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static dev.jayms.net.structure.StructurePackage.*;

class StructurePreparedCellPublicationTest {
    @TempDir Path folder;
    @Test void failureAfterFirstPreparedInstallRollsBackAndAllowsRetry()throws Exception {
        var path=folder.resolve("prepared.dat");var local=new LocalGame(path,42);
        var owner=Thread.currentThread();var installs=new AtomicInteger();var fail=new AtomicBoolean(true);
        try(var queue=new StructureOwnerQueue();var world=new World(42,local.models) {
            @Override public void replaceCell(CellReplacement replacement) {
                assertSame(owner,Thread.currentThread());
                if(installs.incrementAndGet()==2&&fail.getAndSet(false))throw new IllegalStateException("Synthetic prepared install failure");
                super.replaceCell(replacement);
            }
        }) {
            var tree=new SparseVoxelOctree(16);tree.fill(1,2,3,4,5,6,0xfe123456);
            var asset=new StructurePackage("Prepared pair",new Bounds(2,1,1,0,0,0),List.of(),List.of(new Cell(new Position(0,0,0),tree,-1),new Cell(new Position(1,0,0),tree,-1)));
            var engine=new StructureEngine();var dest=new Position(320,180,320);
            try(var prepared=engine.prepare(asset,dest,0,world.structureSnapshot(),()->false,p->{});
                var history=StructureOfflineHistoryStagingTest.worker(queue,()->local.stageStructureHistory(prepared.cells(),queue,()->false,p->assertNotSame(owner,Thread.currentThread())),()->{},new AtomicInteger())) {
                StructureOfflineHistoryStagingTest.worker(queue,()->{local.stageStructureSave(history,prepared.modelSnapshot(),()->false,p->{});return true;},()->{},new AtomicInteger());
                assertThrows(IllegalStateException.class,()->prepared.commit("fixture",local.structureAuthority(world,()->null,history)));
                assertTrue(local.edits.isEmpty());assertTrue(world.cell(320,180,320).isEmpty());assertTrue(world.cell(321,180,320).isEmpty());
                assertFalse(Files.exists(path));assertFalse(engine.busy());
                assertThrows(java.io.IOException.class,()->engine.undo("fixture",local.structureAuthority(world)));
            }
            try(var prepared=engine.prepare(asset,dest,0,world.structureSnapshot(),()->false,p->{});
                var history=StructureOfflineHistoryStagingTest.worker(queue,()->local.stageStructureHistory(prepared.cells(),queue,()->false,p->{}),()->{},new AtomicInteger())) {
                StructureOfflineHistoryStagingTest.worker(queue,()->{local.stageStructureSave(history,prepared.modelSnapshot(),()->false,p->{});return true;},()->{},new AtomicInteger());
                prepared.commit("fixture",local.structureAuthority(world,()->null,history));
            }
            var loaded=new LocalGame(path,0);assertEquals(local.edits,loaded.edits);
            assertTrue(world.cell(320,180,320).sameValues(tree));assertTrue(world.cell(321,180,320).sameValues(tree));
            engine.undo("fixture",local.structureAuthority(world));assertTrue(world.cell(320,180,320).isEmpty());assertTrue(world.cell(321,180,320).isEmpty());
        }
        try(var files=Files.list(folder)){assertFalse(files.anyMatch(p->p.getFileName().toString().startsWith(".structure-offline-")));}
    }
}
