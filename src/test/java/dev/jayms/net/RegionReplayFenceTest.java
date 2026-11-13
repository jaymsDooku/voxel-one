package dev.jayms.net;

import dev.jayms.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegionReplayFenceTest {
    @TempDir Path folder;
    static final RegionReplayWorker.Revision REV=new RegionReplayWorker.Revision(1,2,3);
    static final ChunkPos REGION=new ChunkPos(0,10,0);
    static final RegionReplayPage.CellRange RANGE=new RegionReplayPage.CellRange(0,1);
    RegionReplayWorker worker()throws Exception {
        var encoded=RegionReplayLoaderTest.encodeRows(List.of(new Protocol.Edit(0,160,0,Blocks.STONE)));
        return new RegionReplayWorker(folder,(cancel,progress)->{
            var spool=RegionReplaySpool.stage(folder,new DataInputStream(new ByteArrayInputStream(encoded)),e->true,cancel);
            return new RegionReplayWorker.Source(spool,new Terrain(42),e->true,REV);
        },2,1,28,1);
    }
    RegionReplayWorker.Request request(RegionReplayWorker worker,World world)throws Exception {
        var request=worker.submitFenced(REGION,RANGE,1,REV,world);RegionReplayWorkerTest.done(request);return request;
    }
    void clean()throws Exception{try(var files=Files.list(folder)){assertEquals(0,files.count());}}
    @Test void sameWorldReturnsDetachedPageWithoutMutationAndCannotBypassFence()throws Exception {
        var models=new ModelLibrary();
        try(var world=new World(42,models);var replacement=new World(42,models)) {
            var worker=worker();
            try {
                var request=request(worker,world);assertThrows(IOException.class,()->request.result(REV));
                assertThrows(IOException.class,()->request.result(REV,replacement));
                assertThrows(IOException.class,()->request.result(new RegionReplayWorker.Revision(9,2,3),world));
                var page=request.result(REV,world);assertEquals(WorldVoxels.encode(Blocks.STONE),page.cells().values().iterator().next().tree().get(0,0,0));
                assertEquals(0,world.editsVersion());assertEquals(0,world.sample(0,160,0));assertTrue(world.getLoadedChunks().isEmpty());
                assertSame(page,request.result(REV,world));
                RegionReplayWorkerTest.closed(worker);assertThrows(IOException.class,()->request.result(REV,world));
                assertEquals(WorldVoxels.encode(Blocks.STONE),page.cells().values().iterator().next().tree().get(0,0,0));
            }finally{RegionReplayWorkerTest.closed(worker);}
        }clean();
    }
    @Test void geometryModelTerrainAndCloseInvalidateReadyResults()throws Exception {
        for(int change=0;change<4;change++) {
            var world=new World(42,new ModelLibrary());var worker=worker();
            try {
                var request=request(worker,world);
                switch(change) {
                    case 0->world.apply(new Protocol.Edit(9,160,0,Blocks.GLASS));
                    case 1->{var tree=new SparseVoxelOctree(8);tree.set(0,0,0,0xff123456);world.models().stageStructureModel(new ModelDefinition("Changed",tree));}
                    case 2->world.terrain().stressGrid(new dev.jayms.net.city.StressGrid(1));
                    case 3->world.close();
                }
                assertThrows(IOException.class,()->request.result(REV,world));
            }finally{RegionReplayWorkerTest.closed(worker);world.close();}clean();
        }
    }
    @Test void replacingModelOrTerrainIdentityWithMatchingNumbersInvalidates()throws Exception {
        class Target extends World {
            ModelLibrary library=new ModelLibrary();Terrain land=new Terrain(42);
            Target(){super(42,new ModelLibrary());}
            @Override public ModelLibrary models(){return library;}
            @Override public Terrain terrain(){return land;}
        }
        try(var world=new Target()) {
            var fence=world.beginReplayFence();long revision=world.library.revision();world.library=new ModelLibrary();assertEquals(revision,world.library.revision());assertThrows(ConcurrentModificationException.class,()->fence.validate(world));
            var next=world.beginReplayFence();world.land=new Terrain(42);assertThrows(ConcurrentModificationException.class,()->next.validate(world));
        }
    }
    @Test void residentGeometryChangesAndWrongThreadInvalidate()throws Exception {
        try(var world=new World(42,new ModelLibrary())) {
            var chunk=new Chunk();world.addChunk(REGION,chunk);var fence=world.beginReplayFence();chunk.setBlock(0,0,0,Blocks.GLASS);
            assertThrows(ConcurrentModificationException.class,()->fence.validate(world));
            var current=world.beginReplayFence();var observed=new AtomicReference<Throwable>();var thread=new Thread(()->{try{current.validate(world);}catch(Throwable error){observed.set(error);}});thread.start();thread.join(5000);assertFalse(thread.isAlive());assertInstanceOf(IllegalStateException.class,observed.get());
            var worker=worker();try {
                var request=request(worker,world);var result=new AtomicReference<Throwable>();var reader=new Thread(()->{try{request.result(REV,world);}catch(Throwable error){result.set(error);}});reader.start();reader.join(5000);assertFalse(reader.isAlive());assertInstanceOf(IOException.class,result.get());
                assertNotNull(request.result(REV,world));
            }finally{RegionReplayWorkerTest.closed(worker);}
        }clean();
    }
    @Test void unfencedRequestsDoNotClaimWorldValidationAndClosedTargetsCannotSubmit()throws Exception {
        var world=new World(42,new ModelLibrary());var worker=worker();
        try {
            var request=worker.submit(REGION,RANGE,1,REV);RegionReplayWorkerTest.done(request);
            assertThrows(IOException.class,()->request.result(REV,world));assertNotNull(request.result(REV));
            world.close();assertThrows(ConcurrentModificationException.class,()->worker.submitFenced(REGION,RANGE,1,REV,world));
        }finally{RegionReplayWorkerTest.closed(worker);world.close();}clean();
    }
}
