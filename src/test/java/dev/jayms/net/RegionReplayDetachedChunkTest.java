package dev.jayms.net;
import dev.jayms.*;
import java.io.IOException;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegionReplayDetachedChunkTest {
    @TempDir Path folder;
    @Test void boundWorkerTransfersIndependentChunkAndRejectsOwnerMutation()throws Exception {
        var fixture=new ReadbackMetadataTest();fixture.folder=folder;var path=fixture.fixture();var original=Files.readAllBytes(path);var worker=fixture.worker(path);
        try {
            ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
            try(var world=new World(local.seed,local.models,local.generatorVersion)) {
                var binding=worker.bindTarget(local,world);var request=binding.submitChunk(RegionReplayBindingTest.REGION,8192,world);RegionReplayWorkerTest.done(request);
                var snapshot=binding.chunkResult(request,world);var detached=snapshot.detached();assertNotNull(detached);detached.validate();
                assertTrue(snapshot.geometry().sameValues(detached.chunk().snapshot()));assertEquals(1,detached.chunk().models().size());assertNotNull(snapshot.mesh());assertNotNull(snapshot.modelDraws());
                detached.chunk().setBlock(0,0,0,Blocks.GLASS);assertEquals(Blocks.STONE,WorldVoxels.decode(snapshot.geometry().uniform(0,0,0,16)));
                assertThrows(IOException.class,detached::validate);assertThrows(IOException.class,()->binding.chunkResult(request,world));
                var retry=binding.submitChunk(RegionReplayBindingTest.REGION,8192,world);RegionReplayWorkerTest.done(retry);assertNotNull(binding.chunkResult(retry,world).detached());
                assertTrue(world.getLoadedChunks().isEmpty());assertEquals(0,world.editsVersion());assertFalse(local.replayComplete());assertThrows(IOException.class,local::save);assertArrayEquals(original,Files.readAllBytes(path));
            }
        }finally{RegionReplayWorkerTest.closed(worker);fixture.noSpools();}
    }
    @Test void attachmentAndPrematureMeshInstallationInvalidateReceipt()throws Exception {
        for(int mode=0;mode<2;mode++) {
            var root=ReplayChunkPreparationTest.fixture().snapshot();var source=new RegionReplayChunk.Snapshot(new ChunkPos(-1,10,-1),root,0,5,root.leafCount());
            var prepared=RegionReplayChunk.prepareDetached(source,RegionReplayChunk.MAX_CELL_LEAVES,()->false);prepared.detached().validate();
            try(var world=new World(42)) {
                if(mode==0)prepared.detached().chunk().attach(world,source.region());
                else assertTrue(prepared.detached().chunk().installMesh(null,null,0));
                assertThrows(IOException.class,prepared.detached()::validate);assertTrue(world.getLoadedChunks().isEmpty());assertTrue(root.sameValues(prepared.geometry()));
            }
        }
    }
}
