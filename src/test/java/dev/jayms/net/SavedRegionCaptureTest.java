package dev.jayms.net;

import dev.jayms.net.model.*;
import dev.jayms.net.structure.StructurePackage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static dev.jayms.net.structure.StructurePackage.*;

public class SavedRegionCaptureTest {
    @TempDir Path folder;
    public static Path fixture(Path directory)throws Exception {
        Files.createDirectories(directory);var file=directory.resolve("synthetic-world.dat");var models=new ModelLibrary();var a=new SparseVoxelOctree(8);a.set(1,2,3,0xff267ac9);var b=new SparseVoxelOctree(32);b.set(31,30,29,0xffabcdef);
        int first=models.stageStructureModel(new ModelDefinition("A",a)).id(),second=models.stageStructureModel(new ModelDefinition("B",b)).id();
        var rows=new WorldVoxels.History();WorldVoxels.remember(rows,new Protocol.Edit(16,160,0,first));WorldVoxels.remember(rows,new Protocol.Edit(0,161,0,second));WorldVoxels.remember(rows,new Protocol.Edit(1,160,1,Blocks.WATER));
        WorldVoxels.remember(rows,new Protocol.Edit(-17,160,-15,Blocks.LED).withColor(0xed593c));WorldVoxels.remember(rows,new Protocol.Edit(-2,161,1,Blocks.piece(Blocks.LED,1),1,1,0,0).withColor(0x26cfa8));WorldVoxels.remember(rows,new Protocol.Edit(0,10,0,Blocks.AIR));
        try(var save=StagedOfflineSave.stageRegions(file,42,Terrain.CURRENT_VERSION,models,rows,()->false,n->{})){save.publish(new Inventory(),17,List.of(new ItemDrop(3,Blocks.STONE,2,1,2,3)),1);}return file;
    }
    public static StructurePackage expected(Path file,String name,Bounds bounds,Position origin)throws Exception {
        var local=new LocalGame(file,0);var voxels=new WorldVoxels(new Terrain(local.seed,local.generatorVersion));local.edits.values().forEach(voxels::apply);
        return StructurePackage.capture(name,bounds,origin,new StructurePackage.Source(){public ModelLibrary models(){return local.models;}public SparseVoxelOctree cell(int x,int y,int z){return voxels.cell(x,y,z);}},()->false,n->{});
    }
    @Test public void savedCaptureMatchesIndependentFullReplayWithoutCreatingAWorld()throws Exception {
        var file=fixture(folder.resolve("compare"));var bytes=Files.readAllBytes(file);var scratch=Files.createDirectory(folder.resolve("scratch"));Files.writeString(scratch.resolve("keep"),"synthetic marker");
        var bounds=new Bounds(34,3,18,17,1,15);var origin=new Position(-17,160,-15);var expected=expected(file,"Saved region",bounds,origin);var phases=new HashSet<SavedRegionCapture.Phase>();
        try(var capture=new SavedRegionCapture(file,scratch,"Saved region",bounds,origin)) {
            var result=capture.capture(()->false,p->{phases.add(p.phase());assertDoesNotThrow(capture::stats);assertThrows(IOException.class,capture::close);});assertArrayEquals(expected.encode(),result.encode());assertFalse(capture.cleanupPending());
            assertEquals(6,capture.stats().checkedRows());assertTrue(capture.stats().peakCachedRegions()<=2);assertTrue(capture.stats().peakCachedBytes()<=4L*1024*1024);assertTrue(capture.stats().visitedChunks()>1);
            assertThrows(IOException.class,()->capture.capture(()->false,p->fail("Consumed capture callback")));var output=folder.resolve("captured.voxstructure");result.save(output);assertArrayEquals(result.encode(),StructurePackage.load(output).encode());
        }
        assertEquals(EnumSet.allOf(SavedRegionCapture.Phase.class),phases);assertArrayEquals(bytes,Files.readAllBytes(file));try(var files=Files.list(scratch)){assertEquals(List.of("keep"),files.map(p->p.getFileName().toString()).toList());}
    }
    @Test public void implicitTerrainAndExplicitAirArePreserved()throws Exception {
        var file=fixture(folder.resolve("terrain"));var bounds=new Bounds(3,2,3,0,0,0);var origin=new Position(0,10,0);
        try(var capture=new SavedRegionCapture(file,folder.resolve("terrain-scratch"),"Terrain",bounds,origin)) {var actual=capture.capture(()->false,p->{});assertArrayEquals(expected(file,"Terrain",bounds,origin).encode(),actual.encode());assertFalse(actual.occupiedPositions().contains(new Position(0,0,0)));}
    }
    @Test public void sourceReplacementAfterSpoolingDoesNotChangeTheCapturedSnapshot()throws Exception {
        var file=fixture(folder.resolve("snapshot"));var bytes=Files.readAllBytes(file);var bounds=new Bounds(32,2,2,0,0,0);var origin=new Position(0,160,0);var expected=expected(file,"Snapshot",bounds,origin);var changed=new AtomicBoolean();
        try(var capture=new SavedRegionCapture(file,folder.resolve("snapshot-scratch"),"Snapshot",bounds,origin)) {var actual=capture.capture(()->false,p->{if(p.phase()==SavedRegionCapture.Phase.CAPTURING&&changed.compareAndSet(false,true))try{Files.writeString(file,"synthetic replacement");}catch(IOException e){throw new UncheckedIOException(e);}});assertArrayEquals(expected.encode(),actual.encode());assertTrue(changed.get());}finally{Files.write(file,bytes);}
    }
    @Test public void everyProgressPhaseCanCancelWithoutReturningAPackageOrLeavingScratch()throws Exception {
        var file=fixture(folder.resolve("cancel"));var bytes=Files.readAllBytes(file);
        for(var phase:SavedRegionCapture.Phase.values()) {
            var cancel=new AtomicBoolean();var scratch=folder.resolve("cancel-"+phase);
            try(var capture=new SavedRegionCapture(file,scratch,"Cancel",new Bounds(32,2,2,0,0,0),new Position(0,160,0))) {
                assertThrows(CancellationException.class,()->capture.capture(cancel::get,p->{if(p.phase()==phase)cancel.set(true);}));assertFalse(capture.cleanupPending());assertArrayEquals(bytes,Files.readAllBytes(file));
            }
            if(Files.exists(scratch))try(var files=Files.list(scratch)){assertEquals(0,files.count());}
        }
    }
    @Test public void invalidSelectionMalformedSourceAndMidReadMutationReject()throws Exception {
        var file=fixture(folder.resolve("invalid"));var bytes=Files.readAllBytes(file);var scratch=folder.resolve("untouched");
        assertThrows(IOException.class,()->new SavedRegionCapture(file,scratch,"!",new Bounds(1,1,1,0,0,0),new Position(0,160,0)));assertThrows(IOException.class,()->new SavedRegionCapture(file,scratch,"Bad",new Bounds(2,1,1,0,0,0),new Position(Terrain.LIMIT,160,0)));assertFalse(Files.exists(scratch));
        Files.write(file,new byte[]{1},StandardOpenOption.APPEND);try(var capture=new SavedRegionCapture(file,scratch,"Malformed",new Bounds(1,1,1,0,0,0),new Position(0,160,0))){assertThrows(IOException.class,()->capture.capture(()->false,p->{}));assertFalse(capture.cleanupPending());}finally{Files.write(file,bytes);}
        var changed=new AtomicBoolean();try(var capture=new SavedRegionCapture(file,scratch,"Changed",new Bounds(1,1,1,0,0,0),new Position(0,160,0))){assertThrows(IOException.class,()->capture.capture(()->false,p->{if(p.stats().checkedRows()>0&&changed.compareAndSet(false,true))try{Files.writeString(file,"synthetic replacement");}catch(IOException e){throw new UncheckedIOException(e);}}));assertTrue(changed.get());assertFalse(capture.cleanupPending());}finally{Files.write(file,bytes);}
    }
    @Test public void failedCleanupRetainsOwnershipUntilExplicitCloseRetry()throws Exception {
        var file=fixture(folder.resolve("cleanup"));var bytes=Files.readAllBytes(file);var blocked=new AtomicReference<Path>();var capture=new SavedRegionCapture(file,folder.resolve("cleanup-scratch"),"Cleanup",new Bounds(32,2,2,0,0,0),new Position(0,160,0));
        try {
            assertThrows(IOException.class,()->capture.capture(()->false,p->{if(p.phase()==SavedRegionCapture.Phase.CLEANING)try{var marker=capture.ownedDirectory().resolve("synthetic-blocker");Files.writeString(marker,"synthetic");blocked.set(marker);}catch(IOException e){throw new UncheckedIOException(e);}}));
            assertTrue(capture.cleanupPending());assertNotNull(capture.ownedDirectory());assertThrows(IOException.class,capture::close);Files.delete(blocked.get());
            for(int i=0;i<5;i++){capture.stats();assertTrue(capture.cleanupPending());}capture.close();assertFalse(capture.cleanupPending());assertNull(capture.ownedDirectory());assertArrayEquals(bytes,Files.readAllBytes(file));
        }finally{if(blocked.get()!=null)Files.deleteIfExists(blocked.get());capture.close();}
    }
    @Test public void foreignOwnersAndReentryCannotCaptureOrReleaseAnotherWorkersScratch()throws Exception {
        var file=fixture(folder.resolve("owner"));var scratch=folder.resolve("owner-scratch");var executor=Executors.newSingleThreadExecutor();
        try(var capture=new SavedRegionCapture(file,scratch,"Owner",new Bounds(32,2,2,0,0,0),new Position(0,160,0))) {
            executor.submit(()->{assertThrows(IOException.class,()->capture.capture(()->false,p->{}));assertThrows(IOException.class,capture::close);assertThrows(IOException.class,capture::stats);assertThrows(IOException.class,capture::ownedDirectory);return null;}).get(10,TimeUnit.SECONDS);assertFalse(Files.exists(scratch));
            capture.capture(()->false,p->{assertThrows(IOException.class,()->capture.capture(()->false,q->fail("Reentered capture")));assertThrows(IOException.class,capture::close);});
        }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test public void longRoadSpansSixtyFourChunksWithOnlyTwoCachedRoots()throws Exception {
        var file=folder.resolve("road.dat");var rows=new WorldVoxels.History();for(int x=0;x<1024;x++)WorldVoxels.remember(rows,new Protocol.Edit(x,160,0,Blocks.ASPHALT));
        try(var save=StagedOfflineSave.stageRegions(file,42,Terrain.CURRENT_VERSION,new ModelLibrary(),rows,()->false,n->{})){save.publish(new Inventory(),20,List.of(),0);}
        var bytes=Files.readAllBytes(file);var bounds=new Bounds(1024,1,1,512,0,0);var origin=new Position(0,160,0);
        try(var capture=new SavedRegionCapture(file,folder.resolve("road-scratch"),"Long road",bounds,origin)) {
            var actual=capture.capture(()->false,p->{});assertEquals(1024,actual.cells.size());assertEquals(64,capture.stats().visitedChunks());assertEquals(1024,capture.stats().cellReads());assertEquals(2,capture.stats().peakCachedRegions());assertArrayEquals(expected(file,"Long road",bounds,origin).encode(),actual.encode());assertArrayEquals(bytes,Files.readAllBytes(file));
        }
    }
    @Test public void denseRegionalHistoryRejectsAtExistingCacheBudgetWithoutPublication()throws Exception {
        var file=folder.resolve("dense.dat");var rows=new WorldVoxels.History();for(int cell=0;cell<9;cell++)for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)WorldVoxels.remember(rows,new Protocol.Edit(cell,160,0,Blocks.piece(Blocks.LED,4),4,x,y,z).withColor((x+y+z)%2==0?0x123456:0xabcdef));
        assertTrue(rows.size()>32768);try(var save=StagedOfflineSave.stageRegions(file,42,Terrain.CURRENT_VERSION,new ModelLibrary(),rows,()->false,n->{})){save.publish(new Inventory(),20,List.of(),0);}
        var bytes=Files.readAllBytes(file);try(var capture=new SavedRegionCapture(file,folder.resolve("dense-scratch"),"Dense",new Bounds(9,1,1,0,0,0),new Position(0,160,0))){assertThrows(IOException.class,()->capture.capture(()->false,p->{}));assertFalse(capture.cleanupPending());assertArrayEquals(bytes,Files.readAllBytes(file));}
    }
}
