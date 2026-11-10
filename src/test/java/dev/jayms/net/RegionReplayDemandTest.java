package dev.jayms.net;

import dev.jayms.*;
import dev.jayms.physics.VoxelQueries;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegionReplayDemandTest {
    @TempDir Path folder;
    RegionReplayReadViewTest fixture(){var f=new RegionReplayReadViewTest();f.folder=folder;return f;}
    static BlockRaycaster.Trace trace(World world,float x,float z){return BlockRaycaster.trace(world,new Vector3f(x,160.5f,z),new Vector3f(1,0,0),6);}
    @Test void actualRayDemandLoadsDataWithoutMeshOrAuthorityPromotion()throws Exception {
        var f=fixture();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var c=f.new Context("ray",0,entered,release);var pump=c.binding.openPump(c.world,1)) {
            try {
                var stopped=trace(c.world,.5f,.5f);assertNull(stopped.hit());assertFalse(stopped.unavailable().replayDataReady());
                pump.desiredBoundaries(Collections.nCopies(64,stopped.unavailable()));
                assertEquals(1,pump.status().desired());assertEquals(0,pump.status().pending(),"Demand preflight must not submit work");
                pump.tick();assertTrue(entered.await(10,TimeUnit.SECONDS));assertEquals(1,pump.status().pending());
                pump.desiredBoundaries(List.of(stopped.unavailable()));assertFalse(c.world.replayDataReady(0,160,0));
                release.countDown();RegionReplayPumpTest.ready(pump,1);
                assertEquals(Blocks.STONE,c.world.sample(0,160,0));var after=trace(c.world,.5f,.5f);
                assertNull(after.hit());assertTrue(after.unavailable().replayDataReady());assertFalse(c.world.isLoaded(0,160,0));
                assertTrue(VoxelQueries.collides(c.world,new VoxelQueries.Box(.1f,160.1f,.1f,.9f,160.9f,.9f)));
                assertEquals(0,c.world.editsVersion());assertTrue(c.world.getLoadedChunks().isEmpty());assertFalse(c.local.replayComplete());
                assertThrows(IOException.class,c.local::save);assertArrayEquals(c.original,Files.readAllBytes(c.path));
            }finally{release.countDown();}
        }
    }
    @Test void negativeCellsMapToTheCorrectRegionAndAlignedWindow()throws Exception {
        assertEquals(new RegionReplayWorker.Binding.Window(new ChunkPos(-1,10,-1),192),RegionReplayWorker.Binding.Window.atCell(-1,160,-1));
        assertEquals(new RegionReplayWorker.Binding.Window(new ChunkPos(-1,10,-1),0),RegionReplayWorker.Binding.Window.atCell(-16,160,-16));
        assertEquals(new RegionReplayWorker.Binding.Window(new ChunkPos(-2,10,-2),192),RegionReplayWorker.Binding.Window.atCell(-17,160,-17));
        var f=fixture();try(var c=f.context("negative",0);var pump=c.binding.openPump(c.world,2)) {
            var stopped=trace(c.world,-.25f,-.25f);pump.desiredBoundaries(List.of(stopped.unavailable()));RegionReplayPumpTest.ready(pump,1);
            assertTrue(c.world.replayDataReady(-1,160,-1));assertFalse(c.world.replayDataReady(-1,160,-5));assertEquals(Blocks.AIR,c.world.sample(-1,160,-1));
            pump.desiredBoundaries(List.of(new BlockRaycaster.UnavailableCell(-16,160,-16,false)));RegionReplayPumpTest.ready(pump,1);
            assertTrue(c.world.replayDataReady(-16,160,-16));
        }
    }
    @Test void invalidAndOverBudgetHintsDoNotCancelExistingWork()throws Exception {
        var f=fixture();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var c=f.new Context("preflight",0,entered,release);var pump=c.binding.openPump(c.world,1)) {
            try {
                var source=trace(c.world,.5f,.5f).unavailable();pump.desiredBoundaries(List.of(source));pump.tick();assertTrue(entered.await(10,TimeUnit.SECONDS));
                assertThrows(IllegalArgumentException.class,()->pump.desiredBoundaries(Collections.nCopies(65,source)));
                assertThrows(NullPointerException.class,()->pump.desiredBoundaries(Arrays.asList(source,null)));
                assertThrows(IllegalArgumentException.class,()->pump.desiredBoundaries(List.of(source,new BlockRaycaster.UnavailableCell(Integer.MIN_VALUE,160,0,false))));
                assertThrows(IllegalArgumentException.class,()->pump.desiredBoundaries(List.of(source,new BlockRaycaster.UnavailableCell(0,160,4,false))));
                assertEquals(1,pump.status().desired());assertEquals(1,pump.status().pending());assertEquals(0,pump.status().failed());
                var failure=new AtomicReference<Throwable>();var thread=new Thread(()->{try{pump.desiredBoundaries(List.of(source));}catch(Throwable error){failure.set(error);}});
                thread.start();thread.join(5000);assertFalse(thread.isAlive());assertInstanceOf(IOException.class,failure.get());
                release.countDown();RegionReplayPumpTest.ready(pump,1);assertEquals(Blocks.STONE,c.world.sample(0,160,0));
                pump.desiredBoundaries(List.of());pump.tick();assertEquals(0,pump.status().desired());assertEquals(0,pump.status().pending());
            }finally{release.countDown();}
        }
    }
    @Test void staleReadyHintReloadsAfterEvictionInsteadOfTrustingItsFlag()throws Exception {
        var f=fixture();try(var c=f.context("stale",0);var pump=c.binding.openPump(c.world,1)) {
            pump.desiredBoundaries(List.of(trace(c.world,.5f,.5f).unavailable()));RegionReplayPumpTest.ready(pump,1);
            var old=trace(c.world,.5f,.5f).unavailable();assertTrue(old.replayDataReady());
            pump.desiredBoundaries(List.of(new BlockRaycaster.UnavailableCell(-16,160,-16,true)));RegionReplayPumpTest.ready(pump,1);
            assertFalse(c.world.replayDataReady(0,160,0));assertTrue(c.world.replayDataReady(-16,160,-16));
            pump.desiredBoundaries(List.of(old));RegionReplayPumpTest.ready(pump,1);
            assertTrue(c.world.replayDataReady(0,160,0));assertFalse(c.world.replayDataReady(-16,160,-16));assertEquals(Blocks.STONE,c.world.sample(0,160,0));
        }
    }
}
