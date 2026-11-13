package dev.jayms;
import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ReplayChunkPreparationTest {
    public static Chunk fixture() {
        var chunk=new Chunk();
        for(var edit:List.of(new Protocol.Edit(-16,160,-16,Blocks.STONE),new Protocol.Edit(-15,160,-16,Blocks.piece(Blocks.WOOD,2),2,1,2,3),
                new Protocol.Edit(-14,160,-16,Blocks.LED,0,0,0,0,0xabcdef),new Protocol.Edit(-1,175,-1,1024),new Protocol.Edit(-13,160,-16,9)))chunk.apply(edit);
        return chunk;
    }
    @Test public void geometryModelsFractionalAndLedMatchOrdinaryReplayWithoutGl()throws Exception {
        var expected=fixture();var prepared=Chunk.prepareReplayGeometry(expected.snapshot(),Chunk.MAX_REPLAY_LEAVES,()->false);
        assertTrue(prepared.snapshot().sameValues(expected.snapshot()));assertEquals(expected.models(),prepared.models());assertTrue(prepared.hasFractional());
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){assertEquals(expected.getBlock(x,y,z),prepared.getBlock(x,y,z));assertTrue(expected.cell(x,y,z).sameValues(prepared.cell(x,y,z)));}
        assertEquals(0xfeabcdef,prepared.value(32,0,0));assertNull(prepared.position());assertNull(prepared.getMesh());assertTrue(prepared.dirty());
        assertEquals(0,prepared.geometryVersion());assertEquals(0,prepared.meshRevision());assertEquals(0,prepared.attachmentRevision());assertFalse(prepared.terrainEquivalent);
        prepared.setBlock(3,0,0,Blocks.GLASS);assertEquals(1,prepared.geometryVersion());assertEquals(1,prepared.meshRevision());
        prepared.setBlock(1,0,0,0);assertFalse(prepared.hasFractional());
        prepared.setBlock(15,15,15,0);assertFalse(prepared.models().containsKey(prepared.index(15,15,15)));
    }
    @Test public void persistentCaptureKeepsInputAndPreparedEditsIndependent()throws Exception {
        var input=fixture().snapshot().copy();var before=input.copy().freeze();var calls=new AtomicInteger();
        var prepared=Chunk.prepareReplayGeometry(input,Chunk.MAX_REPLAY_LEAVES,()->{if(calls.incrementAndGet()==2)input.fill(0,0,0,256,256,256,0);return false;});
        assertTrue(input.isEmpty());assertTrue(prepared.snapshot().sameValues(before));prepared.setBlock(0,0,0,Blocks.GLASS);
        assertTrue(input.isEmpty());assertFalse(prepared.snapshot().sameValues(before));assertEquals(Blocks.STONE,WorldVoxels.decode(before.uniform(0,0,0,16)));
    }
    @Test public void cancellationBadResolutionAndLeafAdmissionRejectBeforeExposure()throws Exception {
        var input=fixture().snapshot();var before=input.copy().freeze();var calls=new AtomicInteger();
        assertThrows(CancellationException.class,()->Chunk.prepareReplayGeometry(input,Chunk.MAX_REPLAY_LEAVES,()->calls.incrementAndGet()>5));
        assertTrue(input.sameValues(before));
        assertThrows(IllegalArgumentException.class,()->Chunk.prepareReplayGeometry(new SparseVoxelOctree(16),1,()->false));
        for(int budget:new int[]{-1,Chunk.MAX_REPLAY_LEAVES+1})assertThrows(IllegalArgumentException.class,()->Chunk.prepareReplayGeometry(input,budget,()->false));
        assertThrows(IOException.class,()->Chunk.prepareReplayGeometry(input,0,()->false));
        var invalid=new SparseVoxelOctree(256);invalid.set(0,0,0,0xffffffff);assertThrows(IOException.class,()->Chunk.prepareReplayGeometry(invalid,1,()->false));
        var empty=Chunk.prepareReplayGeometry(new SparseVoxelOctree(256),0,()->false);assertTrue(empty.isEmpty());assertFalse(empty.hasFractional());assertTrue(empty.models().isEmpty());
        assertTrue(Chunk.prepareReplayGeometry(input,Chunk.MAX_REPLAY_LEAVES,()->false).snapshot().sameValues(before));
    }
}
