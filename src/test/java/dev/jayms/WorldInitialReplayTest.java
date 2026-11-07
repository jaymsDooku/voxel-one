package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldInitialReplayTest {
    private static void same(World expected,World actual,List<Protocol.Edit> records) {
        assertEquals(new ArrayList<>(expected.editsSnapshot().entrySet()),new ArrayList<>(actual.editsSnapshot().entrySet()));
        assertEquals(expected.editsVersion(),actual.editsVersion());assertEquals(expected.skyStamp(),actual.skyStamp());
        var cells=new HashSet<WorldVoxels.CellPosition>();var chunks=new HashSet<ChunkPos>();
        for(var edit:records){cells.add(new WorldVoxels.CellPosition(edit.x(),edit.y(),edit.z()));chunks.add(ChunkPos.fromBlock(edit.x(),edit.y(),edit.z()));}
        for(var p:cells) {
            assertTrue(expected.cell(p.x(),p.y(),p.z()).sameValues(actual.cell(p.x(),p.y(),p.z())),p.toString());
            assertEquals(expected.requiresSkyCeiling(Math.floorDiv(p.x(),16),Math.floorDiv(p.z(),16)),actual.requiresSkyCeiling(Math.floorDiv(p.x(),16),Math.floorDiv(p.z(),16)));
        }
        for(var p:chunks) {
            assertEquals(new ArrayList<>(expected.editsInChunk(p)),new ArrayList<>(actual.editsInChunk(p)));
            var before=new ArrayList<>(expected.editedCellsInChunk(p));var after=new ArrayList<>(actual.editedCellsInChunk(p));assertEquals(before.size(),after.size());
            for(int i=0;i<before.size();i++){var a=before.get(i);var b=after.get(i);assertEquals(List.of(a.x(),a.y(),a.z()),List.of(b.x(),b.y(),b.z()));assertTrue(a.voxels().sameValues(b.voxels()));}
            assertEquals(expected.localRevision(p.chunkX()*16,p.chunkX()*16+15,p.chunkZ()*16,p.chunkZ()*16+15),actual.localRevision(p.chunkX()*16,p.chunkX()*16+15,p.chunkZ()*16,p.chunkZ()*16+15));
        }
        for(long i=Math.max(1,expected.skyStamp()-511);i<=expected.skyStamp();i++)assertEquals(expected.skyChange(i),actual.skyChange(i));
        assertTrue(actual.getLoadedChunks().isEmpty());assertEquals(0,actual.terrainPending());
    }
    @Test void interleavedCoarseFineAirAndModelsPreserveGeometryOrderAndMetadata()throws Exception {
        var models=new ModelLibrary();var tree=new SparseVoxelOctree(32);tree.set(31,30,29,0xffabcdef);
        int model=models.stageStructureModel(new ModelDefinition("Replay component",tree)).id();
        var records=new ArrayList<Protocol.Edit>();var random=new Random(6325);
        for(int i=0;i<1000;i++) {
            int depth=random.nextInt(5),side=1<<depth;
            records.add(new Protocol.Edit(random.nextInt(3)-17,180,random.nextInt(3)-1,
                    i%7==0?0:Blocks.piece(i%2==0?Blocks.BRICKS:Blocks.GLASS,depth),depth,random.nextInt(side),random.nextInt(side),random.nextInt(side)));
        }
        records.add(new Protocol.Edit(Terrain.LIMIT,180,-Terrain.LIMIT,model));
        records.add(new Protocol.Edit(-17,180,-1,Blocks.LED).withColor(0x123456));
        try(var expected=new World(42,models);var actual=new World(42,models)) {
            records.forEach(e->{assertTrue(e.valid());expected.apply(e);});var progress=new ArrayList<Integer>();
            actual.replayInitial(records,()->false,progress::add);same(expected,actual,records);
            assertEquals(0,progress.get(0));assertEquals(records.size(),progress.get(progress.size()-1));
            assertSame(models,actual.models());assertThrows(IllegalStateException.class,()->actual.replayInitial(List.of(),()->false,n->{}));
            var next=new Protocol.Edit(-17,180,-1,Blocks.WOOD);expected.apply(next);actual.apply(next);records.add(next);same(expected,actual,records);
        }
    }
    @Test void denseCellAndReturningVisitsFlushFinalValuesWithoutAliasing()throws Exception {
        var records=new ArrayList<Protocol.Edit>();
        for(int i=0;i<4096;i++)records.add(new Protocol.Edit(-Terrain.LIMIT,180,Terrain.LIMIT,
                Blocks.piece(i%2==0?Blocks.BRICKS:Blocks.GLASS,4),4,i%16,i/256,i/16%16));
        records.add(new Protocol.Edit(0,180,0,Blocks.STONE));records.add(records.get(0).withType(Blocks.piece(Blocks.WOOD,4)));
        try(var expected=new World();var actual=new World()) {
            records.forEach(expected::apply);actual.replayInitial(records,()->false,n->{});same(expected,actual,records);
            var saved=actual.cell(-Terrain.LIMIT,180,Terrain.LIMIT).copy().freeze();
            actual.apply(new Protocol.Edit(-Terrain.LIMIT,180,Terrain.LIMIT,Blocks.STONE));assertFalse(saved.sameValues(actual.cell(-Terrain.LIMIT,180,Terrain.LIMIT)));
        }
    }
    @Test void rejectsExistingLoadedClosedAndReentrantWorldsBeforeReadingMoreSource()throws Exception {
        Iterable<Protocol.Edit> unread=()->{fail("Existing world must reject before iterator access");return List.<Protocol.Edit>of().iterator();};
        try(var edited=new World();var loaded=new World();var fresh=new World()) {
            edited.apply(new Protocol.Edit(0,180,0,Blocks.STONE));assertThrows(IllegalStateException.class,()->edited.replayInitial(unread,()->false,n->{}));
            loaded.addChunk(new ChunkPos(0,0,0),new Chunk());assertThrows(IllegalStateException.class,()->loaded.replayInitial(unread,()->false,n->{}));
            var records=List.of(new Protocol.Edit(0,180,0,Blocks.STONE));var callbacks=new AtomicInteger();
            fresh.replayInitial(records,()->false,n->{callbacks.incrementAndGet();assertThrows(IllegalStateException.class,()->fresh.replayInitial(unread,()->false,m->{}));});
            assertEquals(2,callbacks.get());
        }
        var closed=new World();closed.close();assertThrows(IllegalStateException.class,()->closed.replayInitial(unread,()->false,n->{}));
    }
    @Test void cancellationInvalidInputAndCallbackFailureKeepPrivateStateUnpublished()throws Exception {
        var records=new ArrayList<Protocol.Edit>();for(int i=0;i<300;i++)records.add(new Protocol.Edit(0,180,0,Blocks.piece(Blocks.STONE,4),4,i%16,i/256,i/16%16));
        var cancel=new AtomicBoolean();
        try(var world=new World()) {
            assertThrows(CancellationException.class,()->world.replayInitial(records,cancel::get,n->{if(n>=128)cancel.set(true);}));
            assertTrue(world.getLoadedChunks().isEmpty());assertEquals(0,world.terrainPending());
        }
        try(var world=new World()) {
            Iterable<Protocol.Edit> unread=()->{fail("Already cancelled");return records.iterator();};
            assertThrows(CancellationException.class,()->world.replayInitial(unread,()->true,n->fail()));
        }
        try(var world=new World()) {
            assertThrows(IllegalArgumentException.class,()->world.replayInitial(List.of(new Protocol.Edit(0,180,0,Blocks.STONE,5,0,0,0)),()->false,n->{}));
            assertTrue(world.editsSnapshot().isEmpty());
        }
        try(var world=new World()) {
            var failure=new IllegalStateException("Synthetic progress failure");
            assertSame(failure,assertThrows(IllegalStateException.class,()->world.replayInitial(records,()->false,n->{if(n>=128)throw failure;})));
            // The private caller must close/discard this world, as OfflineWorldReplay does.
            assertTrue(world.getLoadedChunks().isEmpty());
        }
    }
}
