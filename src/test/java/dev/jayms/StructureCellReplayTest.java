package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureCellReplayTest {
    static List<Protocol.Edit> legacy(Protocol.CellState state) {
        var edits=new ArrayList<Protocol.Edit>();edits.add(new Protocol.Edit(state.x(),state.y(),state.z(),0));
        for(var leaf:state.tree().leaves()) {
            int depth=4-Integer.numberOfTrailingZeros(leaf.side()),material=WorldVoxels.decode(leaf.color());
            edits.add(new Protocol.Edit(state.x(),state.y(),state.z(),Blocks.piece(material,depth),depth,
                    leaf.x()/leaf.side(),leaf.y()/leaf.side(),leaf.z()/leaf.side(),
                    material==Blocks.LED?WorldVoxels.lightColor(leaf.color()):0));
        }
        return edits;
    }
    static byte[] bytes(Collection<Protocol.Edit> edits)throws Exception {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        for(var edit:edits)edit.write(out);return bytes.toByteArray();
    }
    static List<Protocol.CellState> fixtures() {
        var trees=new ArrayList<SparseVoxelOctree>();trees.add(new SparseVoxelOctree(16));
        for(int type:new int[]{Blocks.STONE,Blocks.LED,1024,66559}) {
            var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(type));trees.add(tree);
        }
        var tree=new SparseVoxelOctree(16);
        tree.fill(0,0,0,8,8,8,WorldVoxels.encode(Blocks.BRICKS));
        tree.fill(12,12,12,16,16,16,0xfe123456);
        tree.fill(15,1,9,16,2,10,0xfeffffff);trees.add(tree);
        var cells=new ArrayList<Protocol.CellState>();
        for(var t:trees)cells.add(new Protocol.CellState(-1_000_000,-1024,1_000_000,t.freeze()));return cells;
    }
    @Test void streamingMatchesLegacyBytesOrderAirModelsAndFractionalLedColours()throws Exception {
        for(var state:fixtures()) {
            var original=state.tree().leaves();var streamed=new ArrayList<Protocol.Edit>();state.forEachEdit(streamed::add);
            assertEquals(legacy(state),streamed);assertEquals(streamed,state.edits());
            assertArrayEquals(bytes(legacy(state)),bytes(streamed));
            assertEquals(original,state.tree().leaves());assertTrue(streamed.stream().allMatch(Protocol.Edit::valid));
            assertThrows(IllegalStateException.class,()->state.tree().set(0,0,0,0xff000000));
        }
    }
    @Test void wholeCellHistoryKeepsReplayOrderAndCopyIsolationWithOutsideEdits()throws Exception {
        for(var state:fixtures()) {
            var source=new WorldVoxels.History();
            for(var edit:List.of(new Protocol.Edit(4,128,7,Blocks.STONE),new Protocol.Edit(state.x(),state.y(),state.z(),Blocks.WOOD),
                    new Protocol.Edit(state.x(),state.y(),state.z(),Blocks.piece(Blocks.STONE,4),4,1,2,3,0),new Protocol.Edit(5,128,7,Blocks.BRICKS)))source.put(edit.key(),edit);
            var original=new ArrayList<>(source.entrySet());var actual=WorldVoxels.History.copyOf(source);
            var expected=new LinkedHashMap<String,Protocol.Edit>(source);
            for(var edit:legacy(state))WorldVoxels.remember(expected,edit);
            WorldVoxels.rememberCell(actual,state);
            assertEquals(new ArrayList<>(expected.entrySet()),new ArrayList<>(actual.entrySet()));
            assertArrayEquals(bytes(expected.values()),bytes(actual.values()));
            assertEquals(original,new ArrayList<>(source.entrySet()));
            var next=new Protocol.Edit(state.x(),state.y(),state.z(),Blocks.STONE);WorldVoxels.remember(actual,next);
            assertEquals(original,new ArrayList<>(source.entrySet()));
        }
    }
    @Test void failedVisitorLeavesSourceReusableAndWrongResolutionCannotChangeHistory() {
        var state=fixtures().get(5);var expected=legacy(state);var failure=new IllegalStateException("synthetic visitor failure");
        int[] visits={0};assertSame(failure,assertThrows(IllegalStateException.class,()->state.forEachEdit(edit->{if(++visits[0]==2)throw failure;})));
        assertEquals(expected,state.edits());assertThrows(NullPointerException.class,()->state.forEachEdit(null));
        var history=new WorldVoxels.History();var edit=new Protocol.Edit(1,128,1,Blocks.STONE);history.put(edit.key(),edit);
        assertThrows(IllegalArgumentException.class,()->WorldVoxels.rememberCell(history,new Protocol.CellState(1,128,1,new SparseVoxelOctree(32))));
        assertEquals(List.of(edit),new ArrayList<>(history.values()));
    }
}
