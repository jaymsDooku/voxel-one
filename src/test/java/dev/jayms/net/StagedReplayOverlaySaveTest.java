package dev.jayms.net;

import dev.jayms.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StagedReplayOverlaySaveTest {
    @TempDir Path folder;
    void noPrefixes()throws Exception{try(var paths=Files.list(folder)){assertFalse(paths.anyMatch(p->p.getFileName().toString().startsWith(".structure-offline-")));}}
    @Test void appendedOverlayPreservesRawOrderAndLoadedFractionalModelGeometry()throws Exception {
        var base=RegionReplayTest.fixture();var models=new ModelLibrary();var tree=new SparseVoxelOctree(32);tree.set(31,30,29,0xffabcdef);
        int model=models.stageStructureModel(new ModelDefinition("Overlay component",tree)).id();
        var edits=List.of(new Protocol.Edit(0,128,0,0),new Protocol.Edit(-1,128,-1,model),new Protocol.Edit(0,128,0,Blocks.piece(Blocks.GLASS,4),4,1,2,3),new Protocol.Edit(1,128,0,Blocks.piece(Blocks.LED,4),4,2,3,4,0x123456),new Protocol.Edit(0,128,0,Blocks.piece(Blocks.LED,4),4,1,2,3,0x654321));
        var overlay=RegionReplayOverlay.capture(edits,()->false);var path=folder.resolve("overlay.dat");
        try(var spool=RegionReplaySpool.stage(folder,StagedReplaySourceSaveTest.input(RegionReplayTest.encode(base)),e->true,()->false);
                var save=StagedOfflineSave.stageRegions(path,42,Terrain.CURRENT_VERSION,models,spool,overlay,e->true,()->false,p->{})){save.publish(new Inventory(),17,List.of(),0);}
        var expectedRows=new ArrayList<>(base.values());expectedRows.addAll(edits);var actualRows=new ArrayList<Protocol.Edit>();
        try(var in=new DataInputStream(Files.newInputStream(path))) {
            assertEquals(10,in.readInt());in.readLong();in.readInt();ModelLibrary.readAll(in);Inventory.read(in);in.readUnsignedByte();
            RegionReplay.visit(in,e->true,()->false,(region,section)->actualRows.addAll(section));assertEquals(expectedRows,actualRows);assertEquals(0,in.readInt());assertEquals(-1,in.read());
        }
        var reference=new WorldVoxels(new Terrain(42));expectedRows.forEach(reference::apply);var restored=new LocalGame(path,0);var actual=new WorldVoxels(new Terrain(42));restored.edits.values().forEach(actual::apply);
        for(var e:expectedRows)assertTrue(reference.cell(e.x(),e.y(),e.z()).sameValues(actual.cell(e.x(),e.y(),e.z())));
        assertEquals(model,actual.type(-1,128,-1));assertEquals(32,restored.models.get(model).definition().voxels().size());assertEquals(17,restored.health);noPrefixes();
    }
    @Test void captureIsImmutableBoundedValidatedAndCancellable() {
        var edit=new Protocol.Edit(0,160,0,Blocks.STONE);var input=new ArrayList<>(List.of(edit));var snapshot=RegionReplayOverlay.capture(input,()->false);input.clear();
        assertEquals(List.of(edit),snapshot.rows());assertThrows(UnsupportedOperationException.class,()->snapshot.rows().clear());
        assertEquals(RegionReplayOverlay.MAX_ROWS,RegionReplayOverlay.capture(Collections.nCopies(RegionReplayOverlay.MAX_ROWS,edit),()->false).rows().size());
        var oversized=new AbstractList<Protocol.Edit>(){public int size(){return RegionReplayOverlay.MAX_ROWS+1;}public Protocol.Edit get(int n){throw new AssertionError("Must reject before reading");}};
        assertThrows(IllegalArgumentException.class,()->RegionReplayOverlay.capture(oversized,()->false));
        assertThrows(IllegalArgumentException.class,()->RegionReplayOverlay.capture(List.of(new Protocol.Edit(Integer.MAX_VALUE,0,0,0)),()->false));
        assertThrows(NullPointerException.class,()->RegionReplayOverlay.capture(Arrays.asList((Protocol.Edit)null),()->false));
        var checks=new AtomicInteger();assertThrows(CancellationException.class,()->RegionReplayOverlay.capture(Collections.nCopies(1000,edit),()->checks.incrementAndGet()>128));
        var changed=new AbstractList<Protocol.Edit>(){int size=1;public int size(){return size;}public Protocol.Edit get(int n){size=2;return edit;}};
        assertThrows(ConcurrentModificationException.class,()->RegionReplayOverlay.capture(changed,()->false));assertTrue(RegionReplayOverlay.empty().rows().isEmpty());
    }
    @Test void overlayCancellationAndPolicyFailureLeaveOldDestinationIntactThenRetry()throws Exception {
        var path=folder.resolve("prior.dat");new LocalGame(path,42).save();var prior=Files.readAllBytes(path);var base=RegionReplayTest.fixture();var rows=new ArrayList<Protocol.Edit>();
        for(int i=0;i<130;i++)rows.add(new Protocol.Edit(900+i,180,900,Blocks.STONE));var overlay=RegionReplayOverlay.capture(rows,()->false);
        try(var spool=RegionReplaySpool.stage(folder,StagedReplaySourceSaveTest.input(RegionReplayTest.encode(base)),e->true,()->false)) {
            var cancel=new AtomicBoolean();var overlayChecked=new AtomicInteger();
            assertThrows(CancellationException.class,()->StagedOfflineSave.stageRegions(path,42,Terrain.CURRENT_VERSION,new ModelLibrary(),spool,overlay,e->{if(e.y()==180)overlayChecked.incrementAndGet();return true;},cancel::get,p->{if(p>=90)cancel.set(true);}));
            assertTrue(overlayChecked.get()>0);assertArrayEquals(prior,Files.readAllBytes(path));noPrefixes();
            assertThrows(IOException.class,()->StagedOfflineSave.stageRegions(path,42,Terrain.CURRENT_VERSION,new ModelLibrary(),spool,overlay,e->e.y()!=180,()->false,p->{}));
            assertArrayEquals(prior,Files.readAllBytes(path));noPrefixes();
            try(var save=StagedOfflineSave.stageRegions(path,42,Terrain.CURRENT_VERSION,new ModelLibrary(),spool,overlay,e->true,()->false,p->{})){save.publish(new Inventory(),18,List.of(),0);}
            assertEquals(18,new LocalGame(path,0).health);noPrefixes();
        }
    }
    @Test void combinedExistingPersistenceLimitRejectsBeforeCreatingDestinationPrefix()throws Exception {
        var edit=new Protocol.Edit(0,160,0,Blocks.STONE);var section=new ByteArrayOutputStream();var body=new ByteArrayOutputStream();var data=new DataOutputStream(body);
        for(int i=0;i<RegionReplay.MAX_RECORDS;i++)edit.write(data);
        RegionReplay.writeSection(new DataOutputStream(section),new ChunkPos(0,10,0),RegionReplay.MAX_RECORDS,body);var full=section.toByteArray();
        int count=LocalGame.MAX_SAVED_EDITS;var header=new ByteArrayOutputStream();new DataOutputStream(header).writeInt(count);
        int complete=count/RegionReplay.MAX_RECORDS,tail=count%RegionReplay.MAX_RECORDS;
        var last=new ByteArrayOutputStream();var tailBody=new ByteArrayOutputStream();tailBody.write(body.toByteArray(),0,tail*RegionReplay.EDIT_BYTES);
        if(tail>0)RegionReplay.writeSection(new DataOutputStream(last),new ChunkPos(0,10,0),tail,tailBody);
        var streams=new AbstractList<InputStream>() {
            public int size(){return 1+complete+(tail>0?1:0);}
            public InputStream get(int i){return new ByteArrayInputStream(i==0?header.toByteArray():i<=complete?full:last.toByteArray());}
        };
        try(var input=new DataInputStream(new SequenceInputStream(Collections.enumeration(streams)));
                var spool=RegionReplaySpool.stage(folder,input,e->true,()->false)) {
            assertEquals(count,spool.size());var target=folder.resolve("must-not-create").resolve("world.dat");
            assertThrows(IOException.class,()->StagedOfflineSave.stageRegions(target,42,Terrain.CURRENT_VERSION,new ModelLibrary(),spool,RegionReplayOverlay.capture(List.of(edit),()->false),e->true,()->false,p->fail()));
            assertFalse(Files.exists(target.getParent()));noPrefixes();
        }
    }
}
