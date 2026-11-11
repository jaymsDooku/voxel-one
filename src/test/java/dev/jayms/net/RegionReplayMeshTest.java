package dev.jayms.net;

import dev.jayms.*;
import dev.jayms.net.model.SparseVoxelOctree;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegionReplayMeshTest {
    @TempDir Path folder;
    static final ChunkPos REGION=new ChunkPos(0,10,0);
    static Chunk independent(List<Protocol.Edit> rows) {
        var world=new WorldVoxels(new Terrain(42));rows.forEach(world::apply);var chunk=new Chunk();
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)chunk.replaceCell(x,y,z,world.cell(x,160+y,z));
        return chunk;
    }
    static void same(MeshData expected,MeshData actual) {
        assertNotNull(actual);assertArrayEquals(expected.vertices(),actual.vertices());
        assertArrayEquals(expected.indices(),actual.indices());assertArrayEquals(expected.surface(),actual.surface());
    }
    @Test void diskReplayMeshMatchesIndependentMaterialsColoursWaterAndModelExclusion()throws Exception {
        var rows=List.of(new Protocol.Edit(0,160,0,Blocks.STONE),
                new Protocol.Edit(1,160,0,Blocks.piece(Blocks.GLASS,1),1,1,0,1),
                new Protocol.Edit(2,160,0,Blocks.piece(Blocks.LED,4),4,1,2,3,0x123456),
                new Protocol.Edit(3,160,0,Blocks.WATER),new Protocol.Edit(4,160,0,1031));
        RegionReplayChunk.Snapshot result;
        try(var source=RegionReplaySpool.stage(folder,new DataInputStream(new ByteArrayInputStream(RegionReplayLoaderTest.encodeRows(rows))),e->true,()->false);
                var cache=new RegionReplayCache(folder,source,1,rows.size()*28L,rows.size(),e->true)) {
            var geometry=RegionReplayChunk.load(cache,REGION,new Terrain(42),8192,()->false);
            assertNull(geometry.mesh());result=RegionReplayMesh.prepare(geometry,()->false);
            same(MeshDataGenerator.generate(independent(rows)),result.mesh());
            assertEquals(1031,WorldVoxels.decode(result.geometry().uniform(64,0,0,16)));
        }
        assertTrue(result.mesh().indices().length>0);try(var files=Files.list(folder)){assertEquals(0,files.count());}
    }
    @Test void cancellationAndPersistentCaptureDoNotMutateOrFreezeCaller()throws Exception {
        var mutable=new SparseVoxelOctree(256);mutable.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        var source=new RegionReplayChunk.Snapshot(REGION,mutable,0,1,1);var calls=new AtomicInteger();
        assertThrows(CancellationException.class,()->RegionReplayMesh.prepare(source,()->calls.incrementAndGet()>128));
        var result=RegionReplayMesh.prepare(source,()->false);mutable.fill(0,0,0,16,16,16,0);
        assertTrue(mutable.isEmpty());assertFalse(result.geometry().isEmpty());
        assertThrows(IllegalStateException.class,()->result.geometry().fill(0,0,0,1,1,1,0));
        same(MeshDataGenerator.generate(independent(List.of(new Protocol.Edit(0,160,0,Blocks.STONE)))),result.mesh());
    }
    @Test void existingMeshBudgetRejectsFragmentationThenAllowsCleanRetry()throws Exception {
        var checker=new SparseVoxelOctree(16);
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)if(((x+y+z)&1)==0)checker.set(x,y,z,WorldVoxels.encode(Blocks.STONE));
        var chunk=new Chunk();for(int i=0;i<14;i++)chunk.replaceCell((i%4)*2,0,(i/4)*2,checker);
        var source=new RegionReplayChunk.Snapshot(REGION,chunk.snapshot(),0,14,14*2048);
        var failure=assertThrows(IOException.class,()->RegionReplayMesh.prepare(source,()->false));assertInstanceOf(IllegalStateException.class,failure.getCause());
        var clean=independent(List.of(new Protocol.Edit(0,160,0,Blocks.STONE)));
        same(MeshDataGenerator.generate(clean),RegionReplayMesh.prepare(new RegionReplayChunk.Snapshot(REGION,clean.snapshot(),0,1,1),()->false).mesh());
    }
    @Test void boundWorkerReturnsCpuMeshWithoutLiveInstallationOrSavePromotion()throws Exception {
        var fixture=new RegionReplayReadViewTest();fixture.folder=folder;RegionReplayChunk.Snapshot retained;
        try(var c=fixture.context("worker",0)) {
            var complete=new LocalGame(c.path,0);var expected=MeshDataGenerator.generate(independent(new ArrayList<>(complete.edits.values())));
            var request=c.binding.submitChunk(REGION,8192,c.world);RegionReplayWorkerTest.done(request);
            assertEquals(RegionReplayWorker.Phase.READY,request.outcome().phase());retained=c.binding.chunkResult(request,c.world);
            same(expected,retained.mesh());assertEquals(4096,request.completedCells());
            assertTrue(c.world.getLoadedChunks().isEmpty());assertEquals(0,c.world.editsVersion());assertFalse(c.world.isLoaded(0,160,0));
            assertFalse(c.local.replayComplete());assertThrows(IOException.class,c.local::save);assertArrayEquals(c.original,Files.readAllBytes(c.path));
        }
        assertTrue(retained.mesh().indices().length>0);
    }
}
