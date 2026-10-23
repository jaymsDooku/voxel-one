package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.GameConfig;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StructureSaveStagingTest {
    @TempDir Path folder;
    @SuppressWarnings("unchecked") static <T>T field(MultiplayerServer server,String name)throws Exception {
        var f=MultiplayerServer.class.getDeclaredField(name);f.setAccessible(true);return (T)f.get(server);
    }
    static StructureEngine.Authority authority(MultiplayerServer server)throws Exception {
        var method=MultiplayerServer.class.getDeclaredMethod("structureAuthority");method.setAccessible(true);return (StructureEngine.Authority)method.invoke(server);
    }
    long temporaryCount()throws IOException {try(var files=Files.list(folder)){return files.filter(p->p.getFileName().toString().startsWith(".structure-save-")).count();}}
    Map<StructurePackage.Position,SparseVoxelOctree> cells(){var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));return Map.of(new StructurePackage.Position(320,180,320),tree.freeze());}
    @Test void productionPrefixRunsOutsideLockAndPublicationKeepsCurrentTransientTailAcrossRestart()throws Exception {
        var tls=SecureTransport.server(folder.resolve("synthetic-tls"));var accounts=new AccountStore(folder.resolve("synthetic-accounts"));var save=folder.resolve("world.dat");
        try(var server=new MultiplayerServer("127.0.0.1",0,save,accounts,tls.context(),42,GameConfig.sandbox())) {
            var authority=authority(server);var edits=StructureSaveStagingTest.<WorldVoxels.History>field(server,"edits");
            var seenPrefix=new java.util.concurrent.atomic.AtomicBoolean();
            try(var staged=authority.stageCommit(authority.models().copy(),cells(),authority.models().revision(),()->{
                try{if(temporaryCount()>0){assertFalse(Thread.holdsLock(server));seenPrefix.set(true);}}catch(IOException e){throw new UncheckedIOException(e);}return false;
            })) {
                assertTrue(seenPrefix.get());assertEquals(1,temporaryCount());assertFalse(Files.exists(save));assertEquals(0,edits.size());
                synchronized(server) {
                    var inventory=new Inventory();inventory.add(Blocks.BRICKS,3);
                    StructureSaveStagingTest.<Map<String,Inventory>>field(server,"inventories").put("Synthetic",inventory);
                    StructureSaveStagingTest.<Map<String,Integer>>field(server,"health").put("Synthetic",17);
                    StructureSaveStagingTest.<Map<Integer,ItemDrop>>field(server,"drops").put(1,new ItemDrop(1,Blocks.STONE,2,0,180,0));
                    staged.apply();
                }
                assertEquals(0,temporaryCount());
                // Open the durable result before source.close can perform an ordinary save.
                try(var restored=new MultiplayerServer("127.0.0.1",0,save,accounts,tls.context(),0,GameConfig.sandbox())) {
                    assertEquals(Blocks.STONE,StructureSaveStagingTest.<WorldVoxels>field(restored,"voxels").type(320,180,320));
                    assertEquals(3,StructureSaveStagingTest.<Map<String,Inventory>>field(restored,"inventories").get("Synthetic").count(0));
                    assertEquals(17,StructureSaveStagingTest.<Map<String,Integer>>field(restored,"health").get("Synthetic"));
                    assertEquals(2,StructureSaveStagingTest.<Map<Integer,ItemDrop>>field(restored,"drops").get(1).count());
                }
            }
        }
    }
    @Test void cancellationDuringPrefixAndAfterStageRemovesAllTemporaryFiles()throws Exception {
        var tls=SecureTransport.server(folder.resolve("synthetic-tls"));var accounts=new AccountStore(folder.resolve("synthetic-accounts"));var save=folder.resolve("world.dat");
        try(var server=new MultiplayerServer("127.0.0.1",0,save,accounts,tls.context(),42,GameConfig.sandbox())) {
            var authority=authority(server);
            assertThrows(java.util.concurrent.CancellationException.class,()->authority.stageCommit(authority.models().copy(),cells(),authority.models().revision(),()->{
                try{return temporaryCount()>0;}catch(IOException e){throw new UncheckedIOException(e);}
            }));
            assertEquals(0,temporaryCount());assertFalse(Files.exists(save));
            var cancel=new java.util.concurrent.atomic.AtomicBoolean();
            var wrapper=new StructureEngine.Authority(){
                public Object lock(){return server;}public ModelLibrary models(){return authority.models();}
                public SparseVoxelOctree cell(int x,int y,int z){return authority.cell(x,y,z);}
                public void commit(ModelLibrary models,Map<StructurePackage.Position,SparseVoxelOctree> cells)throws IOException {authority.commit(models,cells);}
                public StructureEngine.Commit stageCommit(ModelLibrary models,Map<StructurePackage.Position,SparseVoxelOctree> cells,long revision,java.util.function.BooleanSupplier flag)throws IOException {
                    var result=authority.stageCommit(models,cells,revision,flag);assertEquals(1,temporaryCount());cancel.set(true);return result;
                }
            };
            var tree=cells().values().iterator().next();var asset=new StructurePackage("Synthetic cancellation",new StructurePackage.Bounds(1,1,1,0,0,0),List.of(),List.of(new StructurePackage.Cell(new StructurePackage.Position(0,0,0),tree,-1)));
            var engine=new StructureEngine();
            try(var prepared=engine.prepare(asset,new StructurePackage.Position(320,180,320),0,wrapper,cancel::get,p->{})) {
                assertThrows(java.util.concurrent.CancellationException.class,()->prepared.commit("fixture",wrapper));
            }
            assertEquals(0,temporaryCount());assertFalse(Files.exists(save));assertFalse(engine.busy());assertEquals(0,authority.cell(320,180,320).occupied());
        }
    }
    @Test void concurrentOutsideEditRejectsStaleSaveAndLeavesDiskAndGeometryUnchanged()throws Exception {
        var tls=SecureTransport.server(folder.resolve("synthetic-tls"));var accounts=new AccountStore(folder.resolve("synthetic-accounts"));var save=folder.resolve("world.dat");
        try(var server=new MultiplayerServer("127.0.0.1",0,save,accounts,tls.context(),42,GameConfig.sandbox())) {
            var authority=authority(server);var history=StructureSaveStagingTest.<WorldVoxels.History>field(server,"edits");var geometry=StructureSaveStagingTest.<WorldVoxels>field(server,"voxels");
            var persist=MultiplayerServer.class.getDeclaredMethod("persist");persist.setAccessible(true);persist.invoke(server);byte[] before=Files.readAllBytes(save);
            try(var staged=authority.stageCommit(authority.models().copy(),cells(),authority.models().revision(),()->false)) {
                synchronized(server) {
                    var outside=new Protocol.Edit(900,180,900,Blocks.BRICKS);geometry.apply(outside);WorldVoxels.remember(history,outside);
                    assertThrows(IOException.class,staged::apply);assertArrayEquals(before,Files.readAllBytes(save));assertEquals(0,geometry.cell(320,180,320).occupied());assertEquals(Blocks.BRICKS,geometry.type(900,180,900));
                }
            }
            assertEquals(0,temporaryCount());
        }
    }
}
