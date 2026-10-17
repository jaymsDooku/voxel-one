package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.physics.VoxelQueries;
import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class WorldStreamingTest {
    static void resident(World w,ChunkPos p,float x,float y,float z)throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        while(!w.getLoadedChunks().containsKey(p)&&System.nanoTime()<end){w.streamAsync(x,y,z,2_000_000);assertTrue(w.terrainPending()<=9);Thread.sleep(1);}
        assertTrue(w.getLoadedChunks().containsKey(p),"Near playable chunk becomes resident");
    }
    @Test void currentEditsWinGenerationAndReloadIncludingFractionalColor()throws Exception {
        var p=new ChunkPos(0,4,0);
        try(var w=new World(748291)) {
            w.streamAsync(8,65,8,2_000_000);
            w.apply(new Protocol.Edit(8,65,8,Blocks.BRICKS));
            w.apply(new Protocol.Edit(9,65,8,Blocks.LED,1,1,0,0).withColor(0x123456));
            resident(w,p,8,65,8);
            assertEquals(Blocks.BRICKS,w.getBlock(8,65,8));assertEquals(Blocks.PARTIAL,w.getBlock(9,65,8));
            int expected=w.cell(9,65,8).get(8,0,0);assertEquals(0xfe123456,expected);
            w.unloadChunk(p);w.apply(new Protocol.Edit(8,65,8,0));resident(w,p,8,65,8);
            assertEquals(0,w.getBlock(8,65,8));assertEquals(expected,w.cell(9,65,8).get(8,0,0));
        }
    }
    @Test void reversalTeleportWorldSwitchAndSolidLoadingBoundary()throws Exception {
        try(var w=new World(42)) {
            var box=new VoxelQueries.Box(8,65,8,8.5f,66,8.5f);
            assertTrue(VoxelQueries.collides(w,box),"Not-yet-resident space is an intentional solid loading boundary");
            w.streamAsync(8,65,8,2_000_000);
            resident(w,new ChunkPos(200,4,0),3208,65,8);
            assertTrue(w.getLoadedChunks().keySet().stream().noneMatch(p->p.chunkX()==0),"Old generation cannot attach after teleport");
            resident(w,new ChunkPos(0,4,0),8,65,8);
            assertTrue(w.getLoadedChunks().keySet().stream().noneMatch(p->p.chunkX()==200));
        }
        try(var next=new World(99)) {
            resident(next,new ChunkPos(0,4,0),8,65,8);
            var expected=ChunkGenerator.generate(new ChunkPos(0,4,0),new Terrain(99));
            for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++)assertEquals(expected.getBlock(x,y,z),next.getBlock(x,64+y,z));
        }
    }
    @Test void partialColumnDoesNotHideDistantCoverageAndClosedWorldCannotRestart()throws Exception {
        var w=new World(42);
        try {
            resident(w,new ChunkPos(0,4,0),8,65,8);
            assertFalse(w.renderedColumns().contains(new ChunkPos(0,0,0)),"Partial streaming column retains distant parent");
        }finally{w.close();}
        assertEquals(0,w.terrainPending());assertTrue(w.editsSnapshot().isEmpty(),"Closed world releases its own edit index");assertThrows(IllegalStateException.class,()->w.streamAsync(8,65,8,2_000_000));
    }
}
