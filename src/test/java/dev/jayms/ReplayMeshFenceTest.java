package dev.jayms;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ReplayMeshFenceTest {
    static final ChunkPos CENTER=new ChunkPos(-2,10,-3);
    static List<ChunkPos> positions() {
        var result=new ArrayList<ChunkPos>();result.add(CENTER);
        for(var face:Face.values())result.add(new ChunkPos(CENTER.chunkX()+face.dx(),CENTER.chunkY()+face.dy(),CENTER.chunkZ()+face.dz()));
        return result;
    }
    static void natural(World world,ChunkPos position){world.addChunk(position,ChunkGenerator.generate(position,world.terrain()));}
    @Test public void absentCenterAndEachNeighborLoadingInvalidatesOnlyMeshReceipt()throws Exception {
        for(var position:positions())try(var world=new World(42)) {
            var baseline=world.beginReplayFence();var mesh=baseline.captureMeshDependencies(CENTER);
            natural(world,position);baseline.validate(world);
            assertThrows(ConcurrentModificationException.class,()->mesh.validate(world));
        }
    }
    @Test public void meshOnlyInvalidationAndResidentReplacementRejectOldReceipt()throws Exception {
        for(var position:positions())try(var world=new World(42)) {
            natural(world,position);var baseline=world.beginReplayFence();var mesh=baseline.captureMeshDependencies(CENTER);
            var chunk=world.getLoadedChunks().get(position);long geometry=chunk.geometryVersion();chunk.markDirty();
            assertEquals(geometry,chunk.geometryVersion());baseline.validate(world);
            assertThrows(ConcurrentModificationException.class,()->mesh.validate(world));
            var beforeRemoval=world.beginReplayFence();var removal=beforeRemoval.captureMeshDependencies(CENTER);
            world.unloadChunk(position);beforeRemoval.validate(world);
            assertThrows(ConcurrentModificationException.class,()->removal.validate(world));
            natural(world,position);assertNotSame(chunk,world.getLoadedChunks().get(position));
            assertThrows(ConcurrentModificationException.class,()->removal.validate(world));
            world.beginReplayFence().captureMeshDependencies(CENTER).validate(world);
            var same=world.getLoadedChunks().get(position);var beforeAba=world.beginReplayFence();var aba=beforeAba.captureMeshDependencies(CENTER);
            long attached=same.attachmentRevision();world.unloadChunk(position);world.addChunk(position,same);
            assertEquals(attached+1,same.attachmentRevision());beforeAba.validate(world);
            assertThrows(ConcurrentModificationException.class,()->aba.validate(world));
        }
    }
    @Test public void distantNaturalResidencyDoesNotInvalidateAndForeignOwnerCannotValidate()throws Exception {
        try(var world=new World(42);var other=new World(42)) {
            var mesh=world.beginReplayFence().captureMeshDependencies(CENTER);var distant=new ChunkPos(5,10,7);
            natural(world,distant);world.getLoadedChunks().get(distant).markDirty();mesh.validate(world);
            world.unloadChunk(distant);mesh.validate(world);
            assertThrows(ConcurrentModificationException.class,()->mesh.validate(other));
            var executor=Executors.newSingleThreadExecutor();
            try{assertTrue(executor.submit(()->{try{mesh.validate(world);return false;}catch(IllegalStateException expected){return true;}}).get(10,TimeUnit.SECONDS));}
            finally{executor.shutdownNow();}
            mesh.validate(world);
            assertThrows(IllegalArgumentException.class,()->world.beginReplayFence().captureMeshDependencies(new ChunkPos(Integer.MAX_VALUE,0,0)));
        }
    }
    @Test public void canonicalGeometryTerrainModelsAndClosedWorldRetainBaselineChecks()throws Exception {
        for(int mode=0;mode<4;mode++) {
            var world=new World(42);
            try {
                var mesh=world.beginReplayFence().captureMeshDependencies(CENTER);
                switch(mode) {
                    case 0->world.apply(new dev.jayms.net.Protocol.Edit(0,160,0,dev.jayms.net.Blocks.STONE));
                    case 1->world.terrain().stressGrid(null);
                    case 2->{var root=new dev.jayms.net.model.SparseVoxelOctree(8);root.set(0,0,0,0xff123456);world.models().accept(new dev.jayms.net.model.ModelLibrary.Entry(9,"test",new dev.jayms.net.model.ModelDefinition("test",root)));}
                    case 3->world.close();
                }
                assertThrows(ConcurrentModificationException.class,()->mesh.validate(world));
            }finally{world.close();}
        }
    }
}
