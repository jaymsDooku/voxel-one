package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.StressGrid;
import dev.jayms.net.model.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class OfflineWorldReplayTest {
    @TempDir Path directory;
    private LocalGame fixture()throws Exception {
        var local=new LocalGame(directory.resolve("world.dat"),781);
        WorldVoxels.remember(local.edits,new Protocol.Edit(-17,32,-1,Blocks.STONE));
        for(int i=0;i<128;i++)WorldVoxels.remember(local.edits,new Protocol.Edit(-17,32,-1,
                Blocks.piece(i%2==0?Blocks.BRICKS:Blocks.GLASS,4),4,i%16,i/256,i/16%16));
        WorldVoxels.remember(local.edits,new Protocol.Edit(-17,32,-1,0,2,0,0,0));
        var tree=new SparseVoxelOctree(32);tree.set(31,30,29,0xff123456);
        int model=local.models.stageStructureModel(new ModelDefinition("Private replay",tree)).id();
        WorldVoxels.remember(local.edits,new Protocol.Edit(2,180,3,model));
        local.save();return local;
    }
    private static void complete(OfflineWorldReplay replay)throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(!replay.done()&&System.nanoTime()<end)Thread.sleep(1);
        assertTrue(replay.done(),"Replay worker timed out");
    }
    @Test void completedPrivateWorldMatchesOwnerReplayAndTransfersOnce()throws Exception {
        var local=fixture();byte[] before=Files.readAllBytes(directory.resolve("world.dat"));
        var source=new ArrayList<>(local.edits.entrySet());var grid=new StressGrid(32);
        World actual;
        try(var replay=new OfflineWorldReplay(local,grid);var expected=new World(local.seed,local.models,local.generatorVersion)) {
            expected.terrain().stressGrid(grid);for(var edit:local.edits.values())expected.apply(edit);
            complete(replay);assertEquals(local.edits.size(),replay.applied());actual=replay.result();
            assertSame(local.models,actual.models());assertEquals(grid,actual.terrain().stressGrid());
            assertEquals(expected.editsSnapshot(),actual.editsSnapshot());assertEquals(expected.editsVersion(),actual.editsVersion());
            for(int x=-17;x<=2;x++)for(int z=-1;z<=3;z++)for(int y:new int[]{32,180})
                assertTrue(expected.cell(x,y,z).sameValues(actual.cell(x,y,z)));
            assertEquals(expected.requiresSkyCeiling(2,3),actual.requiresSkyCeiling(2,3));
            assertTrue(actual.getLoadedChunks().isEmpty());assertEquals(0,actual.terrainPending());
            assertThrows(IllegalStateException.class,replay::result);
        }
        try(actual){assertFalse(actual.isClosed());actual.apply(new Protocol.Edit(50,180,50,Blocks.STONE));}
        assertEquals(source,new ArrayList<>(local.edits.entrySet()));assertArrayEquals(before,Files.readAllBytes(directory.resolve("world.dat")));
    }
    @Test void changedHistoryAndModelsRejectCompletedPrivateWorld()throws Exception {
        for(int change=0;change<4;change++) {
            var local=fixture();
            try(var replay=new OfflineWorldReplay(local,null)) {
                complete(replay);
                switch(change) {
                    case 0->WorldVoxels.remember(local.edits,new Protocol.Edit(999,180,999,Blocks.STONE));
                    case 1->local.edits=WorldVoxels.History.copyOf(local.edits);
                    case 2->{var tree=new SparseVoxelOctree(8);tree.set(0,0,0,0xffabcdef);local.models.stageStructureModel(new ModelDefinition("Changed",tree));}
                    case 3->local.models=local.models.copy();
                }
                assertThrows(IOException.class,replay::result);
            }
        }
    }
    @Test void cancelDoesNotJoinOrInterruptAndWorkerClosesPrivateWorld()throws Exception {
        var local=fixture();byte[] before=Files.readAllBytes(directory.resolve("world.dat"));
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var stopped=new CountDownLatch(1);
        var thread=new AtomicReference<Thread>();var prepared=new AtomicReference<World>();var interrupted=new AtomicBoolean();
        var replay=new OfflineWorldReplay(local,null,(seed,models,generator)->new World(seed,models,generator) {
            @Override public void apply(Protocol.Edit edit) {
                thread.set(Thread.currentThread());prepared.set(this);entered.countDown();
                try{release.await();}catch(InterruptedException failure){interrupted.set(true);Thread.currentThread().interrupt();}
                super.apply(edit);
            }
            @Override public void close()throws Exception {try{super.close();}finally{stopped.countDown();}}
        });
        try {
            assertTrue(entered.await(5,TimeUnit.SECONDS));assertTrue(thread.get().isDaemon());assertNotSame(Thread.currentThread(),thread.get());
            assertThrows(IllegalStateException.class,replay::result);replay.close();replay.close();
            assertEquals(1,release.getCount());assertTrue(thread.get().isAlive());
            assertThrows(CancellationException.class,replay::result);
        }finally{replay.close();release.countDown();}
        assertTrue(stopped.await(5,TimeUnit.SECONDS));thread.get().join(5000);assertFalse(thread.get().isAlive());
        assertFalse(interrupted.get());assertTrue(prepared.get().isClosed());assertArrayEquals(before,Files.readAllBytes(directory.resolve("world.dat")));
    }
    @Test void mutationDuringReplayCannotEnterCapturedGeometryOrPublishIt()throws Exception {
        var local=fixture();int count=local.edits.size();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var first=new AtomicBoolean(true);var prepared=new AtomicReference<World>();
        var extra=new Protocol.Edit(900,180,900,Blocks.GLASS);
        try(var replay=new OfflineWorldReplay(local,null,(seed,models,generator)->new World(seed,models,generator) {
            @Override public void apply(Protocol.Edit edit) {
                prepared.set(this);
                if(first.getAndSet(false)) {
                    entered.countDown();
                    try{release.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new CancellationException();}
                }
                super.apply(edit);
            }
        })) {
            try {
                assertTrue(entered.await(5,TimeUnit.SECONDS));WorldVoxels.remember(local.edits,extra);
            }finally{release.countDown();}
            complete(replay);assertEquals(count,replay.applied());
            assertFalse(prepared.get().editsSnapshot().containsKey(extra.key()));assertTrue(local.edits.containsKey(extra.key()));
            assertThrows(IOException.class,replay::result);
        }finally{release.countDown();if(prepared.get()!=null)prepared.get().close();}
    }
    @Test void failedWorkerNeverPublishesAndPreservesSource()throws Exception {
        var local=fixture();byte[] before=Files.readAllBytes(directory.resolve("world.dat"));var privateWorld=new AtomicReference<World>();
        var failure=new IllegalStateException("Synthetic replay failure");
        try(var replay=new OfflineWorldReplay(local,null,(seed,models,generator)->new World(seed,models,generator) {
            @Override public void apply(Protocol.Edit edit){privateWorld.set(this);throw failure;}
        })) {
            complete(replay);assertSame(failure,assertThrows(IllegalStateException.class,replay::result));assertTrue(privateWorld.get().isClosed());
        }
        assertArrayEquals(before,Files.readAllBytes(directory.resolve("world.dat")));
    }
    @Test void emptyWorldKeepsDefaultTerrainAndGenericHistoryFailsBeforeWorkerStart()throws Exception {
        var local=new LocalGame(directory.resolve("absent.dat"),52);
        try(var replay=new OfflineWorldReplay(local,null)) {
            complete(replay);try(var world=replay.result()){assertEquals(0,replay.applied());assertEquals(0,world.terrain().geometryRevision());assertEquals(52,world.terrain().seed);}
        }
        assertFalse(Files.exists(directory.resolve("absent.dat")));local.edits=new LinkedHashMap<>();
        assertThrows(IOException.class,()->new OfflineWorldReplay(local,null));
    }
}
