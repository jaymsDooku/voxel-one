package dev.jayms.net;

import dev.jayms.*;
import dev.jayms.net.model.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class ReadOnlyWorldSessionTest {
    @TempDir Path folder;
    static final ChunkPos CHUNK=new ChunkPos(0,10,0);
    Path fixture(String name)throws Exception {var tests=new ReadbackMetadataTest();tests.folder=Files.createDirectories(folder.resolve(name));return tests.fixture();}
    static void ready(ReadOnlyWorldSession session)throws Exception {
        long end=System.nanoTime()+20_000_000_000L;
        while(session.poll(()->false).state()!=ReadOnlyWorldSession.State.READY&&System.nanoTime()<end)Thread.sleep(5);
        assertEquals(ReadOnlyWorldSession.State.READY,session.status().state());
    }
    static void cpu(ReadOnlyWorldSession session)throws Exception {
        long end=System.nanoTime()+20_000_000_000L;
        while(session.status().readyCpuChunks()!=1&&System.nanoTime()<end){session.tick();Thread.sleep(5);}
        assertEquals(1,session.status().readyCpuChunks());
    }
    static void closed(ReadOnlyWorldSession session)throws Exception {
        session.close();long end=System.nanoTime()+20_000_000_000L;
        while(session.status().state()!=ReadOnlyWorldSession.State.CLOSED&&System.nanoTime()<end)Thread.sleep(5);
        assertEquals(ReadOnlyWorldSession.State.CLOSED,session.status().state());
    }
    @Test public void opensPartialWorldAndPublishesOnlyRequestedCpuGeometry()throws Exception {
        var file=fixture("geometry");var bytes=Files.readAllBytes(file);var scratch=folder.resolve("scratch");
        try(var session=new ReadOnlyWorldSession(file,scratch)) {
            assertThrows(IOException.class,session::world);assertThrows(IOException.class,session::snapshotFingerprint);ready(session);assertEquals(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)),session.snapshotFingerprint());var world=session.world();assertEquals(2,session.status().checkedRows());assertTrue(world.hasReplayReadView());assertEquals(0,session.status().readyCpuChunks());assertEquals(0,session.status().readyGpuChunks());assertTrue(world.getLoadedChunks().isEmpty());
            assertThrows(IllegalStateException.class,()->world.apply(new Protocol.Edit(3,160,0,Blocks.STONE)));assertThrows(IllegalStateException.class,world::beginStructureSnapshot);
            session.desired(List.of(CHUNK));cpu(session);assertTrue(world.replayDataReady(0,160,0));assertFalse(world.isLoaded(0,160,0));assertEquals(WorldVoxels.encode(Blocks.STONE),world.cell(0,160,0).uniform(0,0,0,16));assertTrue(Blocks.structureModel(WorldVoxels.decode(world.cell(1,160,0).uniform(0,0,0,16))));
            assertFalse(world.replayDataReady(64,160,0));assertThrows(World.UnloadedReplayCellException.class,()->world.cell(64,160,0));assertArrayEquals(bytes,Files.readAllBytes(file));closed(session);assertTrue(world.isClosed());assertThrows(IOException.class,session::world);
        }
        try(var files=Files.list(scratch)){assertEquals(0,files.count());}
    }
    @Test public void badDemandKeepsValidSelectionAndEmptyDemandEvictsIt()throws Exception {
        var file=fixture("demand");try(var session=new ReadOnlyWorldSession(file,folder.resolve("demand-scratch"))) {
            ready(session);session.desired(List.of(CHUNK));cpu(session);var world=session.world();
            assertThrows(IllegalArgumentException.class,()->session.desired(List.of(CHUNK,new ChunkPos(1,10,0))));assertThrows(NullPointerException.class,()->session.desired(null));assertEquals(1,session.status().readyCpuChunks());assertTrue(world.replayDataReady(0,160,0));
            session.desired(List.of());assertEquals(0,session.status().readyCpuChunks());assertFalse(world.replayDataReady(0,160,0));assertThrows(World.UnloadedReplayCellException.class,()->world.cell(0,160,0));closed(session);
        }
    }
    @Test public void ownerReentryAndCancellationDoNotConsumeColdMetadata()throws Exception {
        var file=fixture("cancel");try(var session=new ReadOnlyWorldSession(file,folder.resolve("cancel-scratch"))) {
            assertThrows(CancellationException.class,()->session.poll(()->true));assertThrows(IOException.class,session::world);var calls=new AtomicInteger();
            long end=System.nanoTime()+20_000_000_000L;
            while(session.poll(()->{
                calls.incrementAndGet();assertTrue(assertDoesNotThrow(session::status).ownerStepActive());assertThrows(IOException.class,session::world);assertThrows(IOException.class,session::snapshotFingerprint);assertThrows(IOException.class,()->session.poll(()->false));assertThrows(IOException.class,session::close);return false;
            }).state()!=ReadOnlyWorldSession.State.READY&&System.nanoTime()<end)Thread.sleep(5);
            assertTrue(calls.get()>0);assertEquals(ReadOnlyWorldSession.State.READY,session.status().state());assertFalse(session.status().ownerStepActive());session.desired(List.of(CHUNK));cpu(session);
            assertThrows(CancellationException.class,()->session.pumpGpu(0,System.nanoTime()+1_000_000,()->true));assertEquals(ReadOnlyWorldSession.State.READY,session.status().state());assertEquals(0,session.status().readyGpuChunks());closed(session);
        }
    }
    @Test public void foreignThreadCannotPollReadDemandUploadOrClose()throws Exception {
        var file=fixture("owner");var executor=Executors.newSingleThreadExecutor();try(var session=new ReadOnlyWorldSession(file,folder.resolve("owner-scratch"))) {
            executor.submit(()->{assertThrows(IOException.class,()->session.poll(()->false));assertThrows(IOException.class,session::status);assertThrows(IOException.class,session::snapshotFingerprint);assertThrows(IOException.class,session::world);assertThrows(IOException.class,()->session.desired(List.of(CHUNK)));assertThrows(IOException.class,session::tick);assertThrows(IOException.class,()->session.pumpGpu(0,0,()->false));assertThrows(IOException.class,session::close);return null;}).get(10,TimeUnit.SECONDS);
            ready(session);closed(session);
        }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test public void invalidFilesAndBudgetsNeverPublishWorld()throws Exception {
        var file=fixture("invalid");var scratch=folder.resolve("not-created");assertThrows(IllegalArgumentException.class,()->new ReadOnlyWorldSession(file,scratch,3,8192));assertThrows(IllegalArgumentException.class,()->new ReadOnlyWorldSession(file,scratch,1,ReadOnlyWorldSession.MAX_CELL_LEAVES+1));assertFalse(Files.exists(scratch));
        Files.write(file,new byte[]{1},StandardOpenOption.APPEND);
        try(var session=new ReadOnlyWorldSession(file,folder.resolve("bad-scratch"))) {
            long end=System.nanoTime()+20_000_000_000L;while(session.status().state()==ReadOnlyWorldSession.State.OPENING&&System.nanoTime()<end)Thread.sleep(5);
            assertEquals(ReadOnlyWorldSession.State.FAILED,session.status().state());assertNotNull(session.status().failure());assertThrows(IOException.class,session::world);assertThrows(IOException.class,session::snapshotFingerprint);assertThrows(IOException.class,()->session.poll(()->false));closed(session);
        }
    }
    @Test public void independentSessionsStayIsolatedWhenOneModelMapOrWorldChanges()throws Exception {
        for(boolean closeWorld:new boolean[]{false,true}) {
            var file=fixture("isolation-"+closeWorld);var bytes=Files.readAllBytes(file);
            try(var first=new ReadOnlyWorldSession(file,folder.resolve("first-"+closeWorld));var second=new ReadOnlyWorldSession(file,folder.resolve("second-"+closeWorld))) {
                ready(first);ready(second);var world=first.world();assertNotSame(world.models(),second.world().models());
                if(closeWorld)world.close();else {var tree=new SparseVoxelOctree(8);tree.set(0,0,0,0xff112233);int id=world.models().stageStructureModel(new ModelDefinition("Only first",tree)).id();assertFalse(second.world().models().has(id));}
                assertEquals(ReadOnlyWorldSession.State.UNAVAILABLE,first.status().state());assertNotNull(first.status().failure());assertThrows(IOException.class,first::world);assertEquals(ReadOnlyWorldSession.State.READY,second.status().state());assertArrayEquals(bytes,Files.readAllBytes(file));closed(first);closed(second);
            }
        }
    }
    @Test public void fileReplacementDoesNotSilentlyChangeCapturedReadOnlyGeometry()throws Exception {
        var file=fixture("snapshot");var bytes=Files.readAllBytes(file);try(var session=new ReadOnlyWorldSession(file,folder.resolve("snapshot-scratch"))) {
            ready(session);Files.writeString(file,"synthetic replacement");session.desired(List.of(CHUNK));cpu(session);assertEquals(WorldVoxels.encode(Blocks.STONE),session.world().cell(0,160,0).uniform(0,0,0,16));closed(session);
        }finally{Files.write(file,bytes);}
    }
    @Test public void chunkBudgetFailureIsVisibleAndDoesNotInventReadyCells()throws Exception {
        var file=fixture("budget");try(var session=new ReadOnlyWorldSession(file,folder.resolve("budget-scratch"),1,0)) {
            ready(session);session.desired(List.of(CHUNK));long end=System.nanoTime()+20_000_000_000L;
            while(session.status().failedChunks()==0&&System.nanoTime()<end){session.tick();Thread.sleep(5);}
            assertEquals(1,session.status().failedChunks());assertNotNull(session.failure(CHUNK));assertEquals(0,session.status().readyCpuChunks());assertEquals(0,session.status().readyGpuChunks());assertFalse(session.world().replayDataReady(0,160,0));closed(session);
        }
    }
    @Test public void cleanupFailureNeedsExplicitCloseRetryAndNeverPollsFilesystem()throws Exception {
        var file=fixture("cleanup");var bytes=Files.readAllBytes(file);var scratch=folder.resolve("cleanup-scratch");try(var session=new ReadOnlyWorldSession(file,scratch)) {
            ready(session);Path blocked;try(var files=Files.list(scratch)){blocked=files.filter(p->p.getFileName().toString().startsWith(".replay-spool-")).findFirst().orElseThrow();}
            Files.delete(blocked);Files.createDirectory(blocked);Files.writeString(blocked.resolve("hold"),"synthetic");
            try {
                session.close();long end=System.nanoTime()+20_000_000_000L;while(session.status().state()!=ReadOnlyWorldSession.State.CLEANUP_FAILED&&System.nanoTime()<end)Thread.sleep(5);
                assertEquals(ReadOnlyWorldSession.State.CLEANUP_FAILED,session.status().state());assertNotNull(session.status().failure());Files.delete(blocked.resolve("hold"));Files.delete(blocked);
                for(int i=0;i<5;i++){assertEquals(ReadOnlyWorldSession.State.CLEANUP_FAILED,session.status().state());assertThrows(IOException.class,()->session.poll(()->false));}closed(session);
            }finally{Files.deleteIfExists(blocked.resolve("hold"));Files.deleteIfExists(blocked);closed(session);}
            assertArrayEquals(bytes,Files.readAllBytes(file));
        }
    }
}
