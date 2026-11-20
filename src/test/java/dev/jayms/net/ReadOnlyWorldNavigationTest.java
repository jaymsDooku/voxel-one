package dev.jayms.net;

import dev.jayms.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class ReadOnlyWorldNavigationTest {
    @TempDir Path folder;
    static final ChunkPos HERE=new ChunkPos(0,10,0),LEFT=new ChunkPos(-1,10,0);
    Path fixture(String name)throws Exception {var f=new ReadOnlyWorldSessionTest();f.folder=folder;return f.fixture(name);}
    static BlockRaycaster.Trace trace(ReadOnlyWorldSession session)throws Exception {
        return session.trace(new Vector3f(.5f,160.5f,.5f),new Vector3f(1,0,0),6);
    }
    static void cpu(ReadOnlyWorldSession session,int count)throws Exception {
        long end=System.nanoTime()+20_000_000_000L;
        while(session.status().readyCpuChunks()!=count&&System.nanoTime()<end){session.tick();Thread.sleep(5);}
        assertEquals(count,session.status().readyCpuChunks());
    }
    @Test public void queryAndDemandDoNotSubmitUntilTickOrInventMeshReadiness()throws Exception {
        var file=fixture("query");var bytes=Files.readAllBytes(file);
        try(var session=new ReadOnlyWorldSession(file,folder.resolve("query-spool"))) {
            assertThrows(IOException.class,()->trace(session));assertThrows(IOException.class,()->session.demand(List.of(HERE),List.of()));
            ReadOnlyWorldSessionTest.ready(session);var stopped=trace(session);assertNull(stopped.hit());assertFalse(stopped.unavailable().replayDataReady());
            session.demand(List.of(),Collections.nCopies(64,stopped.unavailable()));
            for(int i=0;i<5;i++){assertEquals(stopped,trace(session));assertEquals(0,session.status().readyCpuChunks());}
            cpu(session,1);var after=trace(session);assertNull(after.hit());assertTrue(after.unavailable().replayDataReady());assertEquals(0,session.status().readyGpuChunks());assertFalse(session.world().isLoaded(0,160,0));
            assertThrows(IllegalStateException.class,()->session.world().apply(new Protocol.Edit(0,160,0,Blocks.AIR)));assertArrayEquals(bytes,Files.readAllBytes(file));ReadOnlyWorldSessionTest.closed(session);
        }
    }
    @Test public void visibleAndBoundaryUnionLoadsTwoChunksAndDeduplicatesNegativeCells()throws Exception {
        var file=fixture("union");try(var session=new ReadOnlyWorldSession(file,folder.resolve("union-spool"),2,8192)) {
            ReadOnlyWorldSessionTest.ready(session);
            var first=new BlockRaycaster.UnavailableCell(-1,160,0,true);var second=new BlockRaycaster.UnavailableCell(-16,160,15,false);
            session.demand(List.of(HERE,HERE),List.of(first,second));cpu(session,2);
            assertTrue(session.world().replayDataReady(-1,160,0));assertTrue(session.world().replayDataReady(0,160,0));assertFalse(session.world().replayDataReady(-17,160,0));
            session.demand(List.of(),List.of());assertEquals(0,session.status().readyCpuChunks());assertFalse(session.world().replayDataReady(-1,160,0));assertFalse(session.world().replayDataReady(0,160,0));ReadOnlyWorldSessionTest.closed(session);
        }
    }
    @Test public void invalidHintsPreservePreviouslyReadySelection()throws Exception {
        var file=fixture("invalid");try(var session=new ReadOnlyWorldSession(file,folder.resolve("invalid-spool"))) {
            ReadOnlyWorldSessionTest.ready(session);session.demand(List.of(HERE),List.of());cpu(session,1);var hint=trace(session).unavailable();
            assertThrows(IllegalArgumentException.class,()->session.demand(Collections.nCopies(65,HERE),List.of()));
            assertThrows(IllegalArgumentException.class,()->session.demand(Collections.nCopies(64,HERE),List.of(hint)));
            assertThrows(NullPointerException.class,()->session.demand(List.of(),Arrays.asList(hint,null)));
            assertThrows(NullPointerException.class,()->session.demand(null,List.of()));
            assertThrows(NullPointerException.class,()->session.demand(Arrays.asList(HERE,null),List.of()));
            assertThrows(IOException.class,()->session.demand(List.of(new ChunkPos(Integer.MAX_VALUE,10,0)),List.of()));
            assertThrows(IllegalArgumentException.class,()->session.demand(List.of(),List.of(new BlockRaycaster.UnavailableCell(Integer.MIN_VALUE,160,0,false))));
            assertThrows(IllegalArgumentException.class,()->session.demand(List.of(HERE),List.of(new BlockRaycaster.UnavailableCell(-1,160,0,false))));
            assertEquals(1,session.status().readyCpuChunks());assertTrue(session.world().replayDataReady(0,160,0));assertFalse(session.world().replayDataReady(-1,160,0));ReadOnlyWorldSessionTest.closed(session);
        }
    }
    @Test public void staleReadyFlagsReloadEvictedChunksInsteadOfGrantingReadiness()throws Exception {
        var file=fixture("stale");try(var session=new ReadOnlyWorldSession(file,folder.resolve("stale-spool"))) {
            ReadOnlyWorldSessionTest.ready(session);session.demand(List.of(HERE),List.of());cpu(session,1);var old=trace(session).unavailable();assertTrue(old.replayDataReady());
            session.demand(List.of(LEFT),List.of());cpu(session,1);assertFalse(session.world().replayDataReady(0,160,0));
            session.demand(List.of(),List.of(old));assertFalse(trace(session).unavailable().replayDataReady());cpu(session,1);assertTrue(trace(session).unavailable().replayDataReady());assertFalse(session.world().replayDataReady(-1,160,0));ReadOnlyWorldSessionTest.closed(session);
        }
    }
    @Test public void malformedAndLongRaysRejectWithoutChangingReadyData()throws Exception {
        var file=fixture("rays");try(var session=new ReadOnlyWorldSession(file,folder.resolve("rays-spool"))) {
            ReadOnlyWorldSessionTest.ready(session);session.demand(List.of(HERE),List.of());cpu(session,1);var origin=new Vector3f(.5f,160.5f,.5f);var direction=new Vector3f(1,0,0);
            for(float reach:new float[]{-1,Float.NaN,Float.POSITIVE_INFINITY,65})assertThrows(IllegalArgumentException.class,()->session.trace(origin,direction,reach));
            for(var point:List.of(new Vector3f(Float.NaN,160,0),new Vector3f(Float.MAX_VALUE,160,0),new Vector3f(0,Float.NEGATIVE_INFINITY,0)))assertThrows(IllegalArgumentException.class,()->session.trace(point,direction,6));
            for(var ray:List.of(new Vector3f(),new Vector3f(Float.NaN,0,0),new Vector3f(1,Float.POSITIVE_INFINITY,0)))assertThrows(IllegalArgumentException.class,()->session.trace(origin,ray,6));
            assertThrows(NullPointerException.class,()->session.trace(null,direction,6));assertThrows(NullPointerException.class,()->session.trace(origin,null,6));
            assertNotNull(session.trace(origin,direction,0).unavailable());assertNotNull(session.trace(origin,new Vector3f(Float.MIN_VALUE,0,0),64).unavailable());assertNotNull(session.trace(origin,new Vector3f(Float.MAX_VALUE,0,0),64).unavailable());
            assertEquals(1,session.status().readyCpuChunks());ReadOnlyWorldSessionTest.closed(session);
        }
    }
    @Test public void inputCallbacksCannotReenterAndChangingListsRejectBeforeEviction()throws Exception {
        var file=fixture("callbacks");try(var session=new ReadOnlyWorldSession(file,folder.resolve("callbacks-spool"))) {
            ReadOnlyWorldSessionTest.ready(session);session.demand(List.of(HERE),List.of());cpu(session,1);
            var reentry=new AbstractList<ChunkPos>() {
                public int size(){return 1;}
                public ChunkPos get(int i){assertTrue(assertDoesNotThrow(session::status).ownerStepActive());assertThrows(IOException.class,()->trace(session));assertThrows(IOException.class,()->session.demand(List.of(),List.of()));assertThrows(IOException.class,session::close);return HERE;}
            };
            session.demand(reentry,List.of());var changed=new AbstractList<ChunkPos>() {int sizes;public int size(){return ++sizes==1?1:0;}public ChunkPos get(int i){return LEFT;}};
            assertThrows(ConcurrentModificationException.class,()->session.demand(changed,List.of()));assertTrue(session.world().replayDataReady(0,160,0));ReadOnlyWorldSessionTest.closed(session);
        }
    }
    @Test public void foreignOwnersAndClosedSessionsCannotNavigate()throws Exception {
        var file=fixture("fences");var executor=Executors.newSingleThreadExecutor();
        try(var session=new ReadOnlyWorldSession(file,folder.resolve("fences-spool"))) {
            ReadOnlyWorldSessionTest.ready(session);executor.submit(()->{assertThrows(IOException.class,()->trace(session));assertThrows(IOException.class,()->session.demand(List.of(HERE),List.of()));return null;}).get(10,TimeUnit.SECONDS);
            session.world().close();assertThrows(IOException.class,()->trace(session));assertThrows(IOException.class,()->session.demand(List.of(HERE),List.of()));ReadOnlyWorldSessionTest.closed(session);assertThrows(IOException.class,()->trace(session));
        }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test public void changedModelsRejectBeforeInspectingCallerDemand()throws Exception {
        var file=fixture("models");var bytes=Files.readAllBytes(file);
        try(var session=new ReadOnlyWorldSession(file,folder.resolve("models-spool"))) {
            ReadOnlyWorldSessionTest.ready(session);session.demand(List.of(HERE),List.of());cpu(session,1);
            var tree=new dev.jayms.net.model.SparseVoxelOctree(8);tree.set(0,0,0,0xff112233);
            session.world().models().stageStructureModel(new dev.jayms.net.model.ModelDefinition("Changed",tree));
            var forbidden=new AbstractList<ChunkPos>() {public int size(){fail("Invalid source must reject before caller input");return 0;}public ChunkPos get(int i){throw new AssertionError();}};
            assertThrows(IOException.class,()->session.demand(forbidden,List.of()));assertThrows(IOException.class,()->trace(session));assertEquals(ReadOnlyWorldSession.State.UNAVAILABLE,session.status().state());
            assertArrayEquals(bytes,Files.readAllBytes(file));ReadOnlyWorldSessionTest.closed(session);
        }
    }
}
