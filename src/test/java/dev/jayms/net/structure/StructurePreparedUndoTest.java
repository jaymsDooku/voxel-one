package dev.jayms.net.structure;

import dev.jayms.ChunkPos;
import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.jayms.net.structure.StructurePackage.*;

class StructurePreparedUndoTest {
    static SparseVoxelOctree stone(){var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));return tree.freeze();}
    static StructurePackage asset()throws IOException {return new StructurePackage("Undo",new Bounds(3,1,1,0,0,0),List.of(),List.of(new Cell(new Position(0,0,0),stone(),-1),new Cell(new Position(2,0,0),stone(),-1)));}
    static final class Authority implements StructureEngine.Authority {
        final Map<Position,SparseVoxelOctree> state=new HashMap<>();ModelLibrary library=new ModelLibrary();long generation;int reads,commits;boolean fail,stamps=true;
        public Object lock(){return this;}
        public ModelLibrary models(){return library;}
        public SparseVoxelOctree cell(int x,int y,int z){reads++;return state.getOrDefault(new Position(x,y,z),new SparseVoxelOctree(16));}
        public Object chunkStamp(ChunkPos p){return stamps?generation:null;}
        public void commit(ModelLibrary prepared,Map<Position,SparseVoxelOctree> cells)throws IOException {if(fail)throw new IOException("Synthetic publication failure");state.putAll(cells);library=prepared;generation++;commits++;}
        Source snapshot(){var cells=Map.copyOf(state);var definitions=library.copy();long stamp=generation;boolean known=stamps;return new Source(){public SparseVoxelOctree cell(int x,int y,int z){return cells.getOrDefault(new Position(x,y,z),new SparseVoxelOctree(16));}public Object chunkStamp(ChunkPos p){return known?stamp:null;}public ModelLibrary models(){return definitions;}};}
    }
    static StructureEngine placed(Authority authority)throws Exception {
        var engine=new StructureEngine();try(var prepared=engine.prepare(asset(),new Position(-17,180,-17),0,authority,()->false,p->{})){prepared.commit("fixture",authority);}return engine;
    }
    @Test void reservedUndoValidatesDetachedStateAndUsesOnlyUnchangedTrustedProofs()throws Exception {
        var authority=new Authority();var engine=placed(authority);var untouched=new Position(1000,180,1000);authority.state.put(untouched,stone());
        try(var undo=engine.beginUndo("fixture",()->false)) {
            var box=undo.footprint();assertEquals(-17,box.minX());assertEquals(3,box.width());assertEquals(1,box.height());assertEquals(1,box.depth());
            assertTrue(engine.busy());assertThrows(IOException.class,()->engine.beginUndo("fixture",()->false));
            var progress=new ArrayList<Integer>();undo.validate(authority.snapshot(),progress::add);assertEquals(List.of(0,100),progress);
            assertTrue(undo.cells().values().stream().allMatch(SparseVoxelOctree::isEmpty));assertThrows(UnsupportedOperationException.class,()->undo.cells().clear());
            authority.reads=0;undo.commit(authority);assertEquals(0,authority.reads,"Unchanged trusted stamps reuse detached equality proof");
        }
        assertFalse(engine.busy());assertTrue(authority.state.get(new Position(-17,180,-17)).isEmpty());assertEquals(4096,authority.state.get(untouched).occupied());
        assertThrows(IOException.class,()->engine.beginUndo("fixture",()->false));assertFalse(engine.busy());
    }
    @Test void changedDestinationsPublicationFailureAndUnknownProofsRetainUndoUntilSuccess()throws Exception {
        var authority=new Authority();var engine=placed(authority);var p=new Position(-17,180,-17);
        try(var undo=engine.beginUndo("fixture",()->false)) {
            undo.validate(authority.snapshot(),v->{});authority.state.put(p,new SparseVoxelOctree(16).freeze());authority.generation++;
            assertThrows(IOException.class,()->undo.commit(authority));assertEquals(1,authority.commits);
        }
        authority.state.put(p,stone());authority.generation++;
        try(var undo=engine.beginUndo("fixture",()->false)) {undo.validate(authority.snapshot(),v->{});authority.fail=true;assertThrows(IOException.class,()->undo.commit(authority));}
        assertEquals(4096,authority.state.get(p).occupied());assertFalse(engine.busy());authority.fail=false;authority.stamps=false;
        try(var undo=engine.beginUndo("fixture",()->false)) {undo.validate(authority.snapshot(),v->{});authority.reads=0;undo.commit(authority);assertEquals(2,authority.reads,"Unknown proofs require authoritative equality reads");}
        assertEquals(2,authority.commits);assertTrue(authority.state.get(p).isEmpty());
    }
    @Test void cancellationAndModelChangesKeepWorldAndRecordForRetry()throws Exception {
        var authority=new Authority();var engine=placed(authority);var cancel=new AtomicBoolean();
        try(var undo=engine.beginUndo("fixture",cancel::get)) {assertThrows(CancellationException.class,()->undo.validate(authority.snapshot(),p->{if(p==100)cancel.set(true);}));}
        assertFalse(engine.busy());cancel.set(false);
        try(var undo=engine.beginUndo("fixture",cancel::get)) {undo.validate(authority.snapshot(),p->{});cancel.set(true);assertThrows(CancellationException.class,()->undo.commit(authority));}
        assertEquals(1,authority.commits);cancel.set(false);
        try(var undo=engine.beginUndo("fixture",()->false)) {
            undo.validate(authority.snapshot(),v->{});var tree=new SparseVoxelOctree(8);tree.set(0,0,0,0xff123456);authority.library.stageStructureModel(new ModelDefinition("New component",tree));
            assertThrows(IOException.class,()->undo.commit(authority));
        }
        try(var undo=engine.beginUndo("fixture",()->false)) {undo.validate(authority.snapshot(),v->{});undo.commit(authority);}
        assertEquals(2,authority.commits);assertFalse(engine.busy());
    }
}
