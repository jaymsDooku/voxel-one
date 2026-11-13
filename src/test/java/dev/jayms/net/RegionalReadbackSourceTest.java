package dev.jayms.net;

import dev.jayms.ChunkPos;
import dev.jayms.net.model.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegionalReadbackSourceTest {
    @TempDir Path folder;
    static final RegionReplayWorker.Revision REV=new RegionReplayWorker.Revision(1,2,3);
    Path spoolFolder()throws Exception{return Files.createDirectories(folder.resolve("spool"));}
    static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    Path fixture(String name,ModelLibrary models,Inventory inventory,int health,Map<String,Protocol.Edit> rows,List<ItemDrop> drops)throws Exception {
        var path=folder.resolve(name);
        try(var out=new DataOutputStream(Files.newOutputStream(path))) {
            out.writeInt(10);out.writeLong(42);out.writeInt(Terrain.CURRENT_VERSION);models.writeAll(out);inventory.write(out);out.writeByte(health);
            RegionReplay.write(out,rows,()->false,n->{});out.writeInt(drops.size());for(var drop:drops)drop.write(out);
        }return path;
    }
    static WorldVoxels.History rows(int type){var rows=new WorldVoxels.History();WorldVoxels.remember(rows,new Protocol.Edit(0,160,0,type));return rows;}
    RegionReplayWorker.Source open(Path path)throws Exception{return LocalGame.openRegionalReadback(path,spoolFolder(),REV,()->false,n->{});}
    void noSpools()throws Exception{try(var files=Files.list(spoolFolder())){assertEquals(0,files.count());}}
    void rejected(byte[] bytes)throws Exception {
        var path=folder.resolve("invalid.dat");Files.write(path,bytes);
        assertThrows(IOException.class,()->open(path));assertArrayEquals(bytes,Files.readAllBytes(path));noSpools();
    }
    @Test void actualRegionalWriterRestoresModelsFractionalGeometryAndFullSourceHash()throws Exception {
        var models=new ModelLibrary();var voxels=new SparseVoxelOctree(32);voxels.set(31,30,29,0xff123456);
        int model=models.stageStructureModel(new ModelDefinition("Source component",voxels)).id();
        var rows=rows(0);WorldVoxels.remember(rows,new Protocol.Edit(0,160,0,Blocks.piece(Blocks.LED,4),4,15,14,13,0x123456));
        WorldVoxels.remember(rows,new Protocol.Edit(1,160,0,model));var path=folder.resolve("actual.dat");
        try(var staged=StagedOfflineSave.stageRegions(path,42,Terrain.CURRENT_VERSION,models,rows,()->false,n->{})) {
            staged.publish(new Inventory(),17,List.of(new ItemDrop(3,Blocks.STONE,2,1,2,3)),1);
        }
        byte[] before=Files.readAllBytes(path);var source=open(path);
        try(var spool=source.spool();var cache=new RegionReplayCache(spoolFolder(),spool,1,rows.size()*28L,rows.size(),source.allowed())) {
            assertEquals(hash(before),source.fingerprint());assertEquals(REV,source.revision());
            assertTrue(source.allowed().test(new Protocol.Edit(2,160,0,model)));
            assertFalse(source.allowed().test(new Protocol.Edit(2,160,0,model+1)));
            var page=cache.load(new ChunkPos(0,10,0),new RegionReplayPage.CellRange(0,64),source.terrain(),rows.size(),()->false);
            assertEquals(new ArrayList<>(rows.values()),new ArrayList<>(page.history().values()));
            var expected=new WorldVoxels(new Terrain(42));rows.values().forEach(expected::apply);
            for(var entry:page.cells().entrySet()){var p=entry.getKey();assertTrue(entry.getValue().tree().sameValues(expected.cell(p.x(),p.y(),p.z())));}
        }
        assertArrayEquals(before,Files.readAllBytes(path));noSpools();
    }
    @Test void workerUsesValidatedSnapshotAfterSourcePathChangesAndReopenGetsNewIdentity()throws Exception {
        var path=fixture("snapshot.dat",new ModelLibrary(),new Inventory(),20,rows(Blocks.STONE),List.of());
        String original=hash(Files.readAllBytes(path));var worker=new RegionReplayWorker(spoolFolder(),(cancel,progress)->
                LocalGame.openRegionalReadback(path,folder.resolve("spool"),REV,cancel,progress),2,1,28,1);
        try {
            RegionReplayWorkerTest.await(()->worker.status().state()==RegionReplayWorker.State.RUNNING);assertEquals(original,worker.fingerprint());
            fixture("snapshot.dat",new ModelLibrary(),new Inventory(),13,rows(Blocks.GLASS),List.of());assertNotEquals(original,hash(Files.readAllBytes(path)));
            var request=worker.submit(new ChunkPos(0,10,0),new RegionReplayPage.CellRange(0,1),1,REV);RegionReplayWorkerTest.done(request);
            assertEquals(Blocks.STONE,request.result(REV).history().values().iterator().next().type());assertEquals(original,worker.fingerprint());
        }finally{RegionReplayWorkerTest.closed(worker);noSpools();}
        var reopened=open(path);try(var spool=reopened.spool()){assertEquals(hash(Files.readAllBytes(path)),reopened.fingerprint());assertNotEquals(original,reopened.fingerprint());}noSpools();
    }
    @Test void invalidPrefixesAndLegacyReadbackNeverCreateAResidentPrefixOrChangeSource()throws Exception {
        var good=Files.readAllBytes(fixture("base.dat",new ModelLibrary(),new Inventory(),20,rows(Blocks.STONE),List.of()));
        var version=good.clone();ByteBuffer.wrap(version).putInt(0,11);rejected(version);
        var generator=good.clone();ByteBuffer.wrap(generator).putInt(12,Integer.MAX_VALUE);rejected(generator);
        var models=good.clone();ByteBuffer.wrap(models).putInt(16,ModelLibrary.MAX_CUSTOM+2049);rejected(models);
        var inventory=good.clone();inventory[20]=0;inventory[21]=1;rejected(inventory);
        var health=good.clone();health[20+Inventory.SIZE*2]=21;rejected(health);rejected(Arrays.copyOf(good,20));
        var legacyPath=folder.resolve("legacy.dat");var legacy=new LocalGame(legacyPath,42);legacy.health=13;legacy.inventory.add(Blocks.STONE,3);legacy.save();
        byte[] bytes=Files.readAllBytes(legacyPath);assertThrows(IOException.class,()->open(legacyPath));
        var restored=new LocalGame(legacyPath,0);assertEquals(13,restored.health);assertEquals(3,restored.inventory.countType(Blocks.STONE));assertArrayEquals(bytes,Files.readAllBytes(legacyPath));noSpools();
        var missing=folder.resolve("absent/world.dat");assertThrows(IOException.class,()->open(missing));assertFalse(Files.exists(missing.getParent()));
    }
    @Test void missingInventoryReplayAndEverySerializedDropDependencyRejectBeforeSourceEscapes()throws Exception {
        var inventory=new Inventory();inventory.add(9,1);
        rejected(Files.readAllBytes(fixture("inventory.dat",new ModelLibrary(),inventory,20,rows(Blocks.STONE),List.of())));
        rejected(Files.readAllBytes(fixture("replay.dat",new ModelLibrary(),new Inventory(),20,rows(1024),List.of())));
        // The missing model must reject even though the next drop overwrites its ID.
        rejected(Files.readAllBytes(fixture("drops.dat",new ModelLibrary(),new Inventory(),20,rows(Blocks.STONE),
                List.of(new ItemDrop(3,9,1,1,2,3),new ItemDrop(3,Blocks.STONE,1,1,2,3)))));
        var models=new ModelLibrary();var tree=new SparseVoxelOctree(8);tree.set(0,0,0,0xff123456);models.accept(new ModelLibrary.Entry(9,"fixture",new ModelDefinition("Custom",tree)));
        var valid=fixture("valid-custom.dat",models,inventory,20,rows(9),List.of(new ItemDrop(3,9,1,1,2,3)));
        var source=open(valid);try(var spool=source.spool()){assertTrue(source.allowed().test(new Protocol.Edit(0,0,0,9)));}noSpools();
    }
    @Test void corruptTailReplayAndDeclaredBudgetsCleanSpoolsAndKeepSourceBytes()throws Exception {
        var good=Files.readAllBytes(fixture("base.dat",new ModelLibrary(),new Inventory(),20,rows(Blocks.STONE),List.of()));
        rejected(Arrays.copyOf(good,good.length-1));var trailing=Arrays.copyOf(good,good.length+1);trailing[trailing.length-1]=1;rejected(trailing);
        var drops=good.clone();ByteBuffer.wrap(drops).putInt(drops.length-4,100001);rejected(drops);
        var replay=good.clone();replay[replay.length-5]^=1;rejected(replay);
        var count=good.clone();ByteBuffer.wrap(count).putInt(21+Inventory.SIZE*2,LocalGame.MAX_SAVED_EDITS+1);rejected(count);
        var path=folder.resolve("empty-model.dat");
        try(var out=new DataOutputStream(Files.newOutputStream(path))) {
            out.writeInt(10);out.writeLong(42);out.writeInt(Terrain.CURRENT_VERSION);out.writeInt(1);out.writeInt(9);out.writeUTF("fixture");
            new ModelDefinition("Empty",new SparseVoxelOctree(8)).write(out);new Inventory().write(out);out.writeByte(20);RegionReplay.write(out,rows(Blocks.STONE),()->false,n->{});out.writeInt(0);
        }
        byte[] before=Files.readAllBytes(path);assertThrows(IOException.class,()->open(path));assertArrayEquals(before,Files.readAllBytes(path));noSpools();
    }
    @Test void cancellationAndProgressFailureCannotReturnOrRetainAPartialSource()throws Exception {
        var path=fixture("cancel.dat",new ModelLibrary(),new Inventory(),20,rows(Blocks.STONE),List.of());byte[] before=Files.readAllBytes(path);
        var cancel=new AtomicBoolean();assertThrows(CancellationException.class,()->LocalGame.openRegionalReadback(path,spoolFolder(),REV,cancel::get,n->{if(n>0)cancel.set(true);}));noSpools();
        assertThrows(CancellationException.class,()->LocalGame.openRegionalReadback(path,spoolFolder(),REV,()->true,n->fail()));noSpools();
        var failure=new IllegalStateException("Synthetic progress failure");
        assertSame(failure,assertThrows(IllegalStateException.class,()->LocalGame.openRegionalReadback(path,spoolFolder(),REV,()->false,n->{if(n>0)throw failure;})));noSpools();assertArrayEquals(before,Files.readAllBytes(path));
        var valid=open(path);try(var spool=valid.spool()) {
            assertThrows(IllegalArgumentException.class,()->new RegionReplayWorker.Source(spool,valid.terrain(),valid.allowed(),REV,"not a hash"));
        }noSpools();
    }
}
