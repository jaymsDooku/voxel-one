package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldEditSnapshotTest {
    @Test void sparseAndDenseCaptureBoundaryMatchesFlatMapAndSurvivesMutation() {
        var history=new WorldVoxels.History();
        var source=new LinkedHashMap<ChunkPos,WorldVoxels.History>();
        source.put(ChunkPos.fromBlock(0,180,0),history);
        assertEquals(Map.of(),WorldEditSnapshot.capture(source));
        for(int i=0;i<9;i++) {
            WorldVoxels.remember(history,new Protocol.Edit(i,180,0,Blocks.STONE));
            var expected=new LinkedHashMap<>(history);
            var snapshot=WorldEditSnapshot.capture(source);
            assertEquals(new ArrayList<>(expected.entrySet()),new ArrayList<>(snapshot.entrySet()));
            history.put(i+",180,0",new Protocol.Edit(i,180,0,Blocks.GLASS));
            assertEquals(expected,snapshot);
            assertThrows(UnsupportedOperationException.class,()->snapshot.remove("absent"));
        }
    }

    @Test void rootsKeepOrderLookupAndDetachedRowsAcrossOwnerChanges() throws Exception {
        var source = new LinkedHashMap<ChunkPos, WorldVoxels.History>();
        source.put(new ChunkPos(9,9,9), new WorldVoxels.History()); // Empty chunks may remain indexed.
        for (int x : new int[]{-17, 0, 32}) {
            var history = new WorldVoxels.History();
            var tree = new SparseVoxelOctree(16);
            for (int ix=0;ix<16;ix++) for(int iy=0;iy<16;iy++) for(int iz=0;iz<16;iz++)
                tree.fill(ix,iy,iz,ix+1,iy+1,iz+1,WorldVoxels.encode((ix+iy+iz)%2==0?Blocks.STONE:Blocks.GLASS));
            WorldVoxels.rememberCell(history, WorldVoxels.prepareCellReplay(new Protocol.CellState(x,180,-1,tree)));
            source.put(ChunkPos.fromBlock(x,180,-1), history);
        }
        var expected = new LinkedHashMap<String,Protocol.Edit>();
        source.values().forEach(expected::putAll);
        Map<String,Protocol.Edit> snapshot = Collections.unmodifiableMap(new WorldEditSnapshot(source));
        assertEquals(new ArrayList<>(expected.entrySet()), new ArrayList<>(snapshot.entrySet()));
        var task = new FutureTask<>(() -> {
            for(int i=0;i<3;i++) {
                assertEquals(expected,snapshot); assertEquals(snapshot,expected);
                for(var row:expected.entrySet()) assertEquals(row.getValue(),snapshot.get(row.getKey()));
            }
            return null;
        });
        var worker = new Thread(task,"synthetic-snapshot-reader"); worker.start();
        for(var history:source.values()) {
            history.clear();
            var edit = new Protocol.Edit(100,180,100,Blocks.STONE);
            WorldVoxels.remember(history,edit);
        }
        source.clear(); task.get(20,TimeUnit.SECONDS); worker.join();
        assertEquals(expected.size(),snapshot.size());
        assertThrows(UnsupportedOperationException.class,()->snapshot.clear());
        assertThrows(UnsupportedOperationException.class,()->snapshot.values().clear());
        assertThrows(UnsupportedOperationException.class,()->snapshot.entrySet().iterator().next().setValue(null));
        for(Object key:new Object[]{42,"","1,2","1,2,3/","999999999999,0,0","-017,180,-1","0,180,-1/04/0/0/0",",0,0","1,/2,3"}) {
            assertNull(snapshot.get(key)); assertFalse(snapshot.containsKey(key));
        }
        assertNull(snapshot.get(null));
    }

    @Test void worldSnapshotsSurviveFineEditsReplacementAndClose() throws Exception {
        Map<String,Protocol.Edit> before,after;
        try(var world=new World(42)) {
            world.apply(new Protocol.Edit(-17,180,-1,Blocks.STONE,4,2,3,4));
            world.apply(new Protocol.Edit(32,180,32,Blocks.GLASS));
            before=world.editsSnapshot(); var expected=new LinkedHashMap<>(before);
            var iterator=before.entrySet().iterator();
            world.replaceCell(-17,180,-1,new SparseVoxelOctree(16));
            after=world.editsSnapshot();
            assertNotEquals(before,after); assertEquals(expected,before);
            while(iterator.hasNext()) { var row=iterator.next(); assertEquals(expected.get(row.getKey()),row.getValue()); }
            assertEquals(expected,before); assertFalse(after.isEmpty());
        }
        assertFalse(before.isEmpty()); assertFalse(after.isEmpty());
    }
}
