package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldReplayReadGateTest {
    @Test void partialViewRejectsInitialReplayBeforeAnySourceOrProgressCallback()throws Exception {
        try(var world=new World(42,new ModelLibrary())) {
            var called=new AtomicBoolean();world.attachReplayReadView(new World.ReplayReadView(){
                public boolean ready(int x,int y,int z){return false;}
                public SparseVoxelOctree cell(int x,int y,int z){throw new World.UnloadedReplayCellException(x,y,z);}
                public void close(){}
            });
            Iterable<Protocol.Edit> source=()->{called.set(true);return List.<Protocol.Edit>of().iterator();};
            assertThrows(IllegalStateException.class,()->world.replayInitial(source,()->{called.set(true);return false;},n->called.set(true)));
            assertFalse(called.get());assertEquals(0,world.editsVersion());
        }
    }
    @Test void ordinaryResidentWorldKeepsNaturalReadsEditsAndSnapshots()throws Exception {
        try(var world=new World(42,new ModelLibrary())) {
            assertTrue(world.replayDataReady(0,160,0));assertEquals(0,world.sample(0,160,0));world.apply(new Protocol.Edit(0,160,0,Blocks.STONE));
            assertEquals(Blocks.STONE,world.sample(0,160,0));assertEquals(1,world.editsSnapshot().size());assertNotNull(world.beginStructureSnapshot());
            assertThrows(IllegalStateException.class,()->world.attachReplayReadView(new World.ReplayReadView(){
                public boolean ready(int x,int y,int z){return false;}
                public SparseVoxelOctree cell(int x,int y,int z){throw new AssertionError();}
                public void close(){}
            }));assertEquals(Blocks.STONE,world.sample(0,160,0));
        }
    }
}
