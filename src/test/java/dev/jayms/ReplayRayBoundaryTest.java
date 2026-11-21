package dev.jayms;

import dev.jayms.net.Blocks;
import dev.jayms.net.model.SparseVoxelOctree;
import dev.jayms.physics.VoxelQueries;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplayRayBoundaryTest {
    static final class View implements World.ReplayReadView {
        boolean ready;int calls;boolean closed;
        public boolean ready(int x,int y,int z){calls++;return ready;}
        public SparseVoxelOctree cell(int x,int y,int z){throw new AssertionError("Unmeshed boundary must not sample replay geometry");}
        public void close(){closed=true;}
    }
    @Test void unknownReplayStopsAtFlooredCellWithoutSamplingAndCollisionStaysSolid()throws Exception {
        var view=new View();
        try(var world=new World()) {
            world.attachReplayReadView(view);
            var origin=new Vector3f(-.25f,160.5f,-16.25f);var direction=new Vector3f(1,0,0);
            var trace=BlockRaycaster.trace(world,origin,direction,100);
            assertNull(trace.hit());assertEquals(new BlockRaycaster.UnavailableCell(-1,160,-17,false),trace.unavailable());assertEquals(1,view.calls);
            assertNull(BlockRaycaster.cast(world,origin,direction,100));assertEquals(1,view.calls,"Legacy cast does not ask the data view");
            assertTrue(VoxelQueries.collides(world,new VoxelQueries.Box(-.4f,160.2f,-16.4f,-.1f,160.8f,-16.1f)));
            assertEquals(0,world.editsVersion());assertFalse(world.nearbyReady(-.25f,-16.25f,0));
        }
        assertTrue(view.closed);
    }
    @Test void knownReplayDataStillStopsUntilResidentGeometryExists()throws Exception {
        var view=new View();view.ready=true;
        try(var world=new World()) {
            world.attachReplayReadView(view);
            var trace=BlockRaycaster.trace(world,new Vector3f(.5f,160.5f,.5f),new Vector3f(0,0,-1),4);
            assertNull(trace.hit());assertEquals(new BlockRaycaster.UnavailableCell(0,160,0,true),trace.unavailable());
            assertFalse(world.isLoaded(0,160,0));assertEquals(1,view.calls);
        }
    }
    @Test void residentHitAndMissKeepLegacyResultsAndStopAtFirstMissingChunk()throws Exception {
        try(var world=new World()) {
            world.addChunk(new ChunkPos(0,0,0),new Chunk());world.setBlock(3,1,1,Blocks.STONE);
            var origin=new Vector3f(.5f,1.5f,1.5f);var direction=new Vector3f(1,0,0);
            var hit=BlockRaycaster.trace(world,origin,direction,30);
            assertNotNull(hit.hit());assertEquals(BlockRaycaster.cast(world,origin,direction,30),hit.hit());assertNull(hit.unavailable());
            var miss=BlockRaycaster.trace(world,origin,direction,1);assertNull(miss.hit());assertNull(miss.unavailable());
            var boundary=BlockRaycaster.trace(world,new Vector3f(14.5f,2.5f,1.5f),direction,30);
            assertNull(boundary.hit());assertEquals(new BlockRaycaster.UnavailableCell(16,2,1,true),boundary.unavailable());
            assertNull(BlockRaycaster.cast(world,new Vector3f(14.5f,2.5f,1.5f),direction,30));
        }
    }
    @Test void invalidTraceInputsReturnBeforeWorldAccess() {
        for(float reach:new float[]{-1,Float.NaN,Float.POSITIVE_INFINITY}) {
            var trace=BlockRaycaster.trace(null,new Vector3f(),new Vector3f(1,0,0),reach);assertNull(trace.hit());assertNull(trace.unavailable());
        }
        var zero=BlockRaycaster.trace(null,new Vector3f(),new Vector3f(),4);assertNull(zero.hit());assertNull(zero.unavailable());
    }
}
