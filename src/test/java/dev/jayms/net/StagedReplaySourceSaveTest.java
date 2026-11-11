package dev.jayms.net;

import dev.jayms.net.model.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StagedReplaySourceSaveTest {
    @TempDir Path folder;
    static DataInputStream input(byte[] bytes){return new DataInputStream(new ByteArrayInputStream(bytes));}
    void noStagedFiles()throws Exception {try(var paths=Files.list(folder)){assertFalse(paths.anyMatch(p->p.getFileName().toString().startsWith(".structure-offline-")));}}
    @Test void streamedSourceMatchesResidentWriterBytesModelsAndSurvival()throws Exception {
        var history=RegionReplayTest.fixture();var models=new ModelLibrary();var tree=new SparseVoxelOctree(32);tree.set(31,30,29,0xffabcdef);
        int model=models.stageStructureModel(new ModelDefinition("Component",tree)).id();WorldVoxels.remember(history,new Protocol.Edit(20,160,20,model));
        var inventory=new Inventory();inventory.add(Blocks.STONE,3);var drops=List.of(new ItemDrop(3,Blocks.STONE,2,1,2,3));
        var resident=folder.resolve("resident.dat");var streamed=folder.resolve("streamed.dat");
        try(var save=StagedOfflineSave.stageRegions(resident,42,Terrain.CURRENT_VERSION,models,history,()->false,p->{})){save.publish(inventory,17,drops,1);}
        try(var spool=RegionReplaySpool.stage(folder,input(RegionReplayTest.encode(history)),e->true,()->false)) {
            var progress=new ArrayList<Integer>();
            try(var save=StagedOfflineSave.stageRegions(streamed,42,Terrain.CURRENT_VERSION,models,spool,e->true,()->false,progress::add)) {
                assertTrue(save.regional());assertFalse(Files.exists(streamed));save.publish(inventory,17,drops,1);
            }
            assertEquals(0,progress.get(0));assertEquals(100,progress.get(progress.size()-1));for(int i=1;i<progress.size();i++)assertTrue(progress.get(i)>=progress.get(i-1));
            assertArrayEquals(Files.readAllBytes(resident),Files.readAllBytes(streamed));
            var loaded=new LocalGame(streamed,0);assertEquals(17,loaded.health);assertEquals(new ArrayList<>(history.values()),new ArrayList<>(loaded.edits.values()));
            assertEquals(32,loaded.models.get(model).definition().voxels().size());assertEquals(0xffabcdef,loaded.models.get(model).definition().voxels().get(31,30,29));
        }
        noStagedFiles();
    }
    @Test void recurringRegionOverwritesAndExplicitAirRetainExactSourceOrder()throws Exception {
        var rows=List.of(new Protocol.Edit(0,160,0,Blocks.STONE),new Protocol.Edit(-1,160,-1,Blocks.GLASS),new Protocol.Edit(0,160,0,0),new Protocol.Edit(-1,160,-1,Blocks.STONE));
        var raw=new AbstractMap<String,Protocol.Edit>() {
            public int size(){return rows.size();}public Set<Entry<String,Protocol.Edit>> entrySet(){throw new AssertionError();}public Collection<Protocol.Edit> values(){return rows;}
        };
        var encoded=RegionReplayTest.encode(raw);var path=folder.resolve("order.dat");
        try(var spool=RegionReplaySpool.stage(folder,input(encoded),e->true,()->false);
                var save=StagedOfflineSave.stageRegions(path,42,Terrain.CURRENT_VERSION,new ModelLibrary(),spool,e->true,()->false,p->{})) {save.publish(new Inventory(),20,List.of(),0);}
        try(var in=new DataInputStream(Files.newInputStream(path))) {
            assertEquals(10,in.readInt());in.readLong();in.readInt();ModelLibrary.readAll(in);Inventory.read(in);in.readUnsignedByte();
            assertArrayEquals(encoded,in.readNBytes(encoded.length));assertEquals(0,in.readInt());assertEquals(-1,in.read());
        }
        var expected=new WorldVoxels.History();rows.forEach(e->WorldVoxels.remember(expected,e));assertEquals(new ArrayList<>(expected.values()),new ArrayList<>(new LocalGame(path,0).edits.values()));
        noStagedFiles();
    }
    @Test void cancellationPolicyAndProgressFailurePreserveOldDestinationAndAllowRetry()throws Exception {
        var path=folder.resolve("old.dat");new LocalGame(path,42).save();var prior=Files.readAllBytes(path);var encoded=RegionReplayTest.encode(RegionReplayTest.fixture());
        try(var spool=RegionReplaySpool.stage(folder,input(encoded),e->true,()->false)) {
            var cancel=new AtomicBoolean();
            assertThrows(CancellationException.class,()->StagedOfflineSave.stageRegions(path,42,Terrain.CURRENT_VERSION,new ModelLibrary(),spool,e->true,cancel::get,p->{if(p>0)cancel.set(true);}));
            assertArrayEquals(prior,Files.readAllBytes(path));noStagedFiles();
            assertThrows(IOException.class,()->StagedOfflineSave.stageRegions(path,42,Terrain.CURRENT_VERSION,new ModelLibrary(),spool,e->false,()->false,p->{}));
            assertArrayEquals(prior,Files.readAllBytes(path));noStagedFiles();
            assertThrows(IllegalStateException.class,()->StagedOfflineSave.stageRegions(path,42,Terrain.CURRENT_VERSION,new ModelLibrary(),spool,e->true,()->false,p->{if(p>0)throw new IllegalStateException("Synthetic progress failure");}));
            assertArrayEquals(prior,Files.readAllBytes(path));noStagedFiles();
            try(var save=StagedOfflineSave.stageRegions(path,42,Terrain.CURRENT_VERSION,new ModelLibrary(),spool,e->true,()->false,p->{})){save.publish(new Inventory(),19,List.of(),0);}
            assertEquals(19,new LocalGame(path,0).health);
        }
        noStagedFiles();
    }
    @Test void lateCorruptionRejectsPrivatePrefixAndEmptySourceStillRoundTrips()throws Exception {
        var path=folder.resolve("safe.dat");new LocalGame(path,42).save();var prior=Files.readAllBytes(path);var encoded=RegionReplayTest.encode(RegionReplayTest.fixture());
        try(var spool=RegionReplaySpool.stage(folder,input(encoded),e->true,()->false)) {
            Path source;try(var paths=Files.list(folder)){source=paths.filter(p->p.getFileName().toString().startsWith(".replay-spool-")).findFirst().orElseThrow();}
            var bad=encoded.clone();bad[24+1024*24+16]^=1;Files.write(source,bad);
            assertThrows(IOException.class,()->StagedOfflineSave.stageRegions(path,42,Terrain.CURRENT_VERSION,new ModelLibrary(),spool,e->true,()->false,p->{}));
            assertArrayEquals(prior,Files.readAllBytes(path));noStagedFiles();Files.write(source,encoded);
            try(var save=StagedOfflineSave.stageRegions(path,42,Terrain.CURRENT_VERSION,new ModelLibrary(),spool,e->true,()->false,p->{})) {}
            assertArrayEquals(prior,Files.readAllBytes(path));noStagedFiles();
        }
        try(var spool=RegionReplaySpool.stage(folder,input(new byte[4]),e->true,()->false);
                var save=StagedOfflineSave.stageRegions(path,42,Terrain.CURRENT_VERSION,new ModelLibrary(),spool,e->true,()->false,p->{})){save.publish(new Inventory(),18,List.of(),0);}
        var loaded=new LocalGame(path,0);assertTrue(loaded.edits.isEmpty());assertEquals(18,loaded.health);noStagedFiles();
    }
}
