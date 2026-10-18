package dev.jayms;

import dev.jayms.net.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldSkyJournalTest {
    @Test void journalIsBoundedAndTracksLoadedAndUnloadedDependencies()throws Exception {
        try(var world=new World(42)){
            for(int i=0;i<513;i++)world.apply(new Protocol.Edit(i*16,140,0,Blocks.STONE));
            assertNull(world.skyChange(1),"Old cursors must reschedule instead of accepting missing history");
            assertEquals(new ChunkPos(1,0,0),world.skyChange(2));
            assertEquals(new ChunkPos(512,0,0),world.skyChange(world.skyStamp()));
            assertNull(world.skyChange(world.skyStamp()+1));
            var position=new ChunkPos(-2,4,3);world.addChunk(position,new Chunk());
            assertEquals(new ChunkPos(-2,0,3),world.skyChange(world.skyStamp()));
            world.unloadChunk(position);assertEquals(new ChunkPos(-2,0,3),world.skyChange(world.skyStamp()));
            world.close();assertNull(world.skyChange(world.skyStamp()-1),"Close releases the dependency ring");
        }
    }
}
