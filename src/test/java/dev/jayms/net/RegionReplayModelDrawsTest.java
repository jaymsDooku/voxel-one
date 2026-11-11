package dev.jayms.net;

import dev.jayms.*;
import dev.jayms.net.model.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegionReplayModelDrawsTest {
    @TempDir Path folder;
    static RegionReplayChunk.Snapshot source(int... cells) {
        var root=new SparseVoxelOctree(256);var cell=new SparseVoxelOctree(16);cell.fill(0,0,0,16,16,16,WorldVoxels.encode(1024));
        for(int key:cells)root.replaceRegion((key&15)*16,(key>>>8)*16,((key>>>4)&15)*16,cell);
        return new RegionReplayChunk.Snapshot(new ChunkPos(-2,10,-1),root.freeze(),0,cells.length,cells.length);
    }
    static ModelDefinition model() {
        var tree=new SparseVoxelOctree(8);tree.set(1,2,3,0xff123456);return new ModelDefinition("Draw fixture",tree);
    }
    static void same(ModelMesher.Geometry expected,ModelMesher.Geometry actual) {
        assertEquals(expected.quads(),actual.quads());assertArrayEquals(expected.vertices(),actual.vertices());assertArrayEquals(expected.indices(),actual.indices());
    }
    @Test void sharedBatchPreservesDefinitionAndLocalPlacementKeysAcrossCells()throws Exception {
        var definition=model();var geometry=source(0,17,4095);
        var result=RegionReplayModelDraws.prepare(geometry,id->id==1024?definition:null,()->false);
        assertEquals(1,result.modelDraws().batches().size());var batch=result.modelDraws().batches().get(0);
        assertEquals(1024,batch.type());assertArrayEquals(new int[]{0,17,4095},batch.cells());assertEquals(8,batch.definition().voxels().size());
        same(ModelMesher.mesh(definition.voxels()),batch.geometry());assertEquals(3L*batch.geometry().quads(),result.modelDraws().instanceQuads());
        assertTrue(geometry.geometry().sameValues(result.geometry()));assertThrows(UnsupportedOperationException.class,()->result.modelDraws().batches().clear());
        var ordinary=RegionReplayMesh.prepare(result,()->false);assertSame(result.modelDraws(),ordinary.modelDraws());assertEquals(0,ordinary.mesh().indices().length);
    }
    @Test void aggregateInstanceSurfaceBudgetRejectsBeforePublishingAndAllowsBoundedRetry()throws Exception {
        var tree=new SparseVoxelOctree(8);
        for(int y=0;y<8;y++)for(int z=0;z<8;z++)for(int x=0;x<8;x++)if(((x+y+z)&1)==0)tree.set(x,y,z,0xffabcdef);
        var definition=new ModelDefinition("Checker",tree);assertEquals(1536,ModelMesher.count(tree));
        int[] rejected=new int[171];for(int i=0;i<rejected.length;i++)rejected[i]=i;
        assertThrows(IOException.class,()->RegionReplayModelDraws.prepare(source(rejected),id->definition,()->false));
        var result=RegionReplayModelDraws.prepare(source(Arrays.copyOf(rejected,170)),id->definition,()->false);
        assertEquals(261120,result.modelDraws().instanceQuads());assertEquals(170,result.modelDraws().batches().get(0).cells().length);
        assertThrows(IOException.class,()->RegionReplayModelDraws.prepare(source(0),id->null,()->false));
    }
    @Test void cancellationAndMutableDefinitionCaptureDoNotPublishOrFreezeCaller()throws Exception {
        var definition=model();var calls=new AtomicInteger();var resolved=new AtomicInteger();
        assertThrows(CancellationException.class,()->RegionReplayModelDraws.prepare(source(0),id->{resolved.incrementAndGet();return definition;},()->calls.incrementAndGet()>4500));
        assertEquals(1,resolved.get());var result=RegionReplayModelDraws.prepare(source(0),id->definition,()->false);
        definition.voxels().set(0,0,0,0xffaabbcc);var captured=result.modelDraws().batches().get(0).definition().voxels();
        assertEquals(0,captured.get(0,0,0));assertEquals(0xff123456,captured.get(1,2,3));
        assertThrows(IllegalStateException.class,()->captured.set(0,0,0,0xff000000));
    }
    @Test void boundWorkerRestoresCustomDrawDataAfterMetadataTransferWithoutPromotion()throws Exception {
        var fixture=new RegionReplayReadViewTest();fixture.folder=folder;RegionReplayChunk.Snapshot retained;
        try(var c=fixture.context("worker",0)) {
            var request=c.binding.submitChunk(RegionReplayReadViewTest.SOURCE,8192,c.world);RegionReplayWorkerTest.done(request);
            assertEquals(RegionReplayWorker.Phase.READY,request.outcome().phase());retained=c.binding.chunkResult(request,c.world);
            assertNotNull(retained.mesh());assertEquals(1,retained.modelDraws().batches().size());var batch=retained.modelDraws().batches().get(0);
            assertArrayEquals(new int[]{1},batch.cells());assertEquals(32,batch.definition().voxels().size());
            same(ModelMesher.mesh(c.local.models.get(batch.type()).definition().voxels()),batch.geometry());
            assertTrue(c.world.getLoadedChunks().isEmpty());assertEquals(0,c.world.editsVersion());assertFalse(c.local.replayComplete());
            assertThrows(IOException.class,c.local::save);assertArrayEquals(c.original,Files.readAllBytes(c.path));
            c.local.models=c.local.models.copy();assertThrows(IOException.class,()->c.binding.chunkResult(request,c.world));
        }
        assertTrue(retained.modelDraws().batches().get(0).geometry().indices().length>0);
    }
}
