package dev.jayms.net;

import dev.jayms.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegionReplayReadViewTest {
    @TempDir Path folder;
    static final RegionReplayWorker.Revision REV=ReadbackMetadataTest.REV;
    static final ChunkPos SOURCE=new ChunkPos(0,10,0);
    static final Protocol.Edit DETAIL=new Protocol.Edit(2,160,0,Blocks.piece(Blocks.LED,4),4,1,2,3,0x123456);
    final class Context implements AutoCloseable {
        final ReadbackMetadataTest fixture;final Path path;final byte[] original;
        final RegionReplayWorker worker;final LocalGame local;final World world;
        final RegionReplayWorker.Binding binding;
        RegionReplayWorker.Binding.ReadView view;
        Context(String name,int windows,CountDownLatch entered,CountDownLatch release)throws Exception {
            fixture=new ReadbackMetadataTest();fixture.folder=Files.createDirectories(folder.resolve(name));path=fixture.fixture();
            var complete=new LocalGame(path,0);WorldVoxels.remember(complete.edits,DETAIL);complete.save();original=Files.readAllBytes(path);var spool=fixture.spool();
            worker=new RegionReplayWorker(spool,(cancel,progress)->{
                var source=LocalGame.openRegionalReadback(path,spool,REV,cancel,progress);
                if(entered==null)return source;
                return new RegionReplayWorker.Source(source.spool(),source.terrain(),edit->{entered.countDown();try{RegionReplayWorkerTest.release(release);}catch(IOException error){throw new UncheckedIOException(error);}return source.allowed().test(edit);},REV,source.fingerprint(),source.metadata());
            },2,1,112,4);
            ReadbackMetadataTest.ready(worker);local=worker.metadataResult(REV);world=new World(local.seed,local.models,local.generatorVersion);binding=worker.bindTarget(local,world);
            if(windows>0)view=binding.openReadView(world,windows);
        }
        RegionReplayWorker.Request request(ChunkPos region,int first,int size)throws Exception {
            var request=binding.submit(region,new RegionReplayPage.CellRange(first,first+size),4,world);RegionReplayWorkerTest.done(request);return request;
        }
        void publish(ChunkPos region,int first)throws Exception{binding.publish(request(region,first,64),world);}
        @Override public void close()throws Exception{RegionReplayWorkerTest.closed(worker);world.close();fixture.noSpools();}
    }
    Context context(String name,int windows)throws Exception{return new Context(name,windows,null,null);}
    @Test void unknownIsNotImplicitTerrainAndKnownWindowsPreserveGeometryModelsAndColour()throws Exception {
        try(var c=context("values",2)) {
            assertFalse(c.world.replayDataReady(0,160,0));
            assertThrows(World.UnloadedReplayCellException.class,()->c.world.sample(0,160,0));
            assertThrows(World.UnloadedReplayCellException.class,()->c.world.getBlock(0,160,0));
            assertThrows(World.UnloadedReplayCellException.class,()->c.world.cell(0,160,0));
            assertThrows(World.UnloadedReplayCellException.class,()->c.world.value(0,2560,0));
            assertThrows(World.UnloadedReplayCellException.class,()->c.world.material(0,2560,0));
            assertThrows(World.UnloadedReplayCellException.class,()->c.world.regionValue(DETAIL));
            assertThrows(World.UnloadedReplayCellException.class,()->c.world.region(DETAIL));
            c.publish(SOURCE,0);var expected=new LocalGame(c.path,0);var geometry=new WorldVoxels(new Terrain(42));expected.edits.values().forEach(geometry::apply);
            for(int x=0;x<4;x++){assertTrue(c.world.replayDataReady(x,160,0));assertTrue(c.world.cell(x,160,0).sameValues(geometry.cell(x,160,0)));}
            assertEquals(Blocks.STONE,c.world.sample(0,160,0));assertEquals(Blocks.PARTIAL,c.world.getBlock(2,160,0));assertEquals(0,c.world.sample(3,160,0));
            assertEquals(WorldVoxels.encode(DETAIL),c.world.value(33,2562,3));assertEquals(Blocks.LED,c.world.material(33,2562,3));assertEquals(WorldVoxels.encode(DETAIL),c.world.regionValue(DETAIL));
            assertTrue(c.world.models().entries().stream().anyMatch(e->e.definition().voxels().size()==32));
            assertThrows(IllegalStateException.class,()->c.world.cell(0,160,0).set(0,0,0,0xffabcdef));
            var unknown=assertThrows(World.UnloadedReplayCellException.class,()->c.world.sample(0,160,4));assertEquals(0,unknown.x);assertEquals(160,unknown.y);assertEquals(4,unknown.z);
            assertFalse(c.world.isLoaded(0,160,0));assertFalse(c.world.nearbyReady(0,0,1));assertEquals(0,c.world.editsVersion());assertTrue(c.world.getLoadedChunks().isEmpty());
            assertThrows(IOException.class,c.local::save);assertArrayEquals(c.original,Files.readAllBytes(c.path));assertFalse(c.local.replayComplete());
        }
    }
    @Test void emptyNaturalWindowsNegativeCoordinatesAndFifoEvictionNeverFallBackForUnknown()throws Exception {
        try(var c=context("coverage",2)) {
            c.publish(SOURCE,0);var retained=c.world.cell(0,160,0);
            c.publish(new ChunkPos(0,0,0),0);assertEquals(new Terrain(42).block(0,0,0),c.world.sample(0,0,0));
            c.publish(new ChunkPos(-1,10,-1),0);assertEquals(2,c.view.retainedWindows());
            assertFalse(c.world.replayDataReady(0,160,0));assertThrows(World.UnloadedReplayCellException.class,()->c.world.sample(0,160,0));assertEquals(WorldVoxels.encode(Blocks.STONE),retained.get(0,0,0));
            assertTrue(c.world.replayDataReady(-16,160,-16));assertEquals(0,c.world.value(-256,2560,-256));
            assertFalse(c.world.replayDataReady(-17,160,-16));assertFalse(c.world.replayDataReady(Integer.MIN_VALUE,160,0));
            assertThrows(World.UnloadedReplayCellException.class,()->c.world.cell(Integer.MIN_VALUE,160,0));
            c.publish(new ChunkPos(-1,10,-1),0);assertEquals(2,c.view.retainedWindows());
        }
    }
    @Test void partialOrForeignPublicationsLeaveCoverageUnchanged()throws Exception {
        try(var c=context("publication",2)) {
            var partial=c.request(SOURCE,0,2);assertThrows(IOException.class,()->c.binding.publish(partial,c.world));assertEquals(0,c.view.retainedWindows());assertFalse(c.world.replayDataReady(0,160,0));
            c.publish(SOURCE,0);var foreign=c.worker.submit(SOURCE,new RegionReplayPage.CellRange(64,128),4,REV);RegionReplayWorkerTest.done(foreign);
            assertThrows(IOException.class,()->c.binding.publish(foreign,c.world));assertEquals(1,c.view.retainedWindows());assertTrue(c.world.replayDataReady(0,160,0));assertFalse(c.world.replayDataReady(0,160,4));
        }
    }
    @Test void readonlyWorldRejectsMutationCaptureHistoryAndStreamingBeforeCallbacks()throws Exception {
        try(var c=context("guards",1)) {
            var world=c.world;var called=new AtomicBoolean();
            assertThrows(IllegalStateException.class,()->world.apply(null));assertThrows(IllegalStateException.class,()->world.setBlock(0,160,0,Blocks.STONE));
            assertThrows(IllegalStateException.class,()->world.replaceCell(0,160,0,null));assertThrows(IllegalStateException.class,()->world.replaceCells(null));assertThrows(IllegalStateException.class,()->world.replaceCell((World.CellReplacement)null));
            assertThrows(IllegalStateException.class,()->world.addChunk(SOURCE,null));assertThrows(IllegalStateException.class,()->world.unloadChunk(SOURCE));
            assertThrows(IllegalStateException.class,world::editsSnapshot);assertThrows(IllegalStateException.class,()->world.editsInChunk(SOURCE));assertThrows(IllegalStateException.class,()->world.editedCellsInChunk(SOURCE));
            assertThrows(IllegalStateException.class,world::structureSnapshot);assertThrows(IllegalStateException.class,world::beginStructureSnapshot);assertThrows(IllegalStateException.class,()->world.structureChunkStamp(SOURCE));
            assertThrows(IllegalStateException.class,()->world.renderedColumns());assertThrows(IllegalStateException.class,()->world.renderedColumns(0,0,16));
            assertThrows(IllegalStateException.class,()->world.stream(0,0,1));assertThrows(IllegalStateException.class,()->world.streamAsync(0,160,0,1));
            assertThrows(IllegalStateException.class,()->world.prepareTerrainTask(()->{called.set(true);return 1;}));assertFalse(called.get());
            assertEquals(0,world.editsVersion());assertTrue(world.getLoadedChunks().isEmpty());
        }
    }
    @Test void openAdmissionAndViewIdentityFencesCannotBeBypassed()throws Exception {
        try(var c=context("admission",0)) {
            var before=c.world.beginReplayFence();var priorPlan=c.world.beginStructureSnapshot();
            assertThrows(IllegalArgumentException.class,()->c.binding.openReadView(c.world,0));assertThrows(IllegalArgumentException.class,()->c.binding.openReadView(c.world,5));before.validate(c.world);
            c.view=c.binding.openReadView(c.world,1);assertThrows(ConcurrentModificationException.class,()->before.validate(c.world));
            assertThrows(IllegalStateException.class,priorPlan::validate);assertThrows(IllegalStateException.class,()->priorPlan.batch(null));
            assertThrows(IOException.class,()->c.binding.openReadView(c.world,1));assertThrows(IllegalStateException.class,()->c.world.attachReplayReadView(c.view));
        }
        try(var c=context("late",0)) {
            c.request(SOURCE,0,64);assertThrows(IOException.class,()->c.binding.openReadView(c.world,1));assertEquals(0,c.world.sample(0,160,0));
        }
    }
    @Test void ownerSourceLifetimeAndModelInvalidationNeverExposeFallbackData()throws Exception {
        try(var c=context("lifetime",1)) {
            c.publish(SOURCE,0);Files.delete(c.path);assertEquals(Blocks.STONE,c.world.sample(0,160,0));assertThrows(IOException.class,c.local::save);assertFalse(Files.exists(c.path));
            var error=new AtomicReference<Throwable>();var thread=new Thread(()->{try{c.world.sample(0,160,0);}catch(Throwable failure){error.set(failure);}});thread.start();thread.join(5000);assertFalse(thread.isAlive());assertInstanceOf(IllegalStateException.class,error.get());
            c.local.models=c.local.models.copy();assertThrows(IllegalStateException.class,()->c.world.sample(0,160,0));assertThrows(IllegalStateException.class,()->c.world.replayDataReady(0,160,0));
            RegionReplayWorkerTest.closed(c.worker);assertEquals(0,c.view.retainedWindows());assertThrows(IllegalStateException.class,()->c.world.sample(0,160,0));
        }
    }
    @Test void cancelledDecodeDoesNotPublishReadiness()throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var c=new Context("cancel",1,entered,release)) {
            try {
                var request=c.binding.submit(SOURCE,new RegionReplayPage.CellRange(0,64),4,c.world);assertTrue(entered.await(10,TimeUnit.SECONDS));assertTrue(request.cancel());release.countDown();
                assertThrows(CancellationException.class,()->c.binding.publish(request,c.world));assertEquals(0,c.view.retainedWindows());assertFalse(c.world.replayDataReady(0,160,0));
            }finally{release.countDown();}
        }
    }
}
