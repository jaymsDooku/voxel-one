package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import static dev.jayms.net.structure.StructurePackage.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureUniformCacheTest {
    static final Position TARGET=new Position(320,180,320);
    static final SparseVoxelOctree EMPTY=new SparseVoxelOctree(16).freeze();
    static final Source SOURCE=new Source(){final ModelLibrary models=new ModelLibrary();public ModelLibrary models(){return models;}public SparseVoxelOctree cell(int x,int y,int z){return EMPTY;}};
    static SparseVoxelOctree uniform(int color){var t=new SparseVoxelOctree(16);t.fill(0,0,0,16,16,16,color);return t;}
    static StructurePackage fixture()throws Exception {
        var cells=new ArrayList<Cell>();
        for(int z=0;z<16;z++)for(int x=0;x<128;x++) {
            int color=x==127&&z==15?0xfe000001:0xfe000000|(1+z*128+x);
            if(x==127&&(z==13||z==14))color=0xfefdcba9;
            var tree=uniform(color);int model=-1;
            if(x==126&&z==15){tree=new SparseVoxelOctree(16);tree.fill(1,2,3,5,6,7,WorldVoxels.encode(Blocks.BRICKS));tree.fill(12,13,14,13,14,15,0xfe123456);}
            if((x==124||x==125)&&z==15){tree=null;model=0;}
            cells.add(new Cell(new Position(x,0,z),tree,model));
        }
        var model=new SparseVoxelOctree(8);model.set(1,2,3,0xffabcdef);
        return new StructurePackage("Cache edges",new Bounds(128,1,16,0,0,0),List.of(new ModelDefinition("Small component",model)),cells);
    }
    static Position destination(Position p,int r){return switch(r){case 0->new Position(320+p.x(),180,320+p.z());case 1->new Position(320-p.z(),180,320+p.x());case 2->new Position(320-p.x(),180,320-p.z());default->new Position(320+p.z(),180,320-p.x());};}
    static SparseVoxelOctree reference(SparseVoxelOctree tree,int r) {
        int n=tree.size();var result=new SparseVoxelOctree(n);
        for(var leaf:tree.leaves()) {
            int x=leaf.x(),z=leaf.z(),s=leaf.side();int rx=switch(r){case 1->n-z-s;case 2->n-x-s;case 3->z;default->x;};int rz=switch(r){case 1->x;case 2->n-z-s;case 3->n-x-s;default->z;};
            result.fill(rx,leaf.y(),rz,rx+s,leaf.y()+s,rz+s,leaf.color());
        }
        return result.freeze();
    }
    @Test void cacheOverflowPreservesAllRotationsFractionalGeometryModelsAndWireBytes()throws Exception {
        var asset=fixture();byte[] sourceBytes=asset.encode();
        for(int rotation=0;rotation<4;rotation++) {
            var engine=new StructureEngine();
            try(var prepared=engine.prepare(asset,TARGET,rotation,SOURCE,()->false,p->{})) {
                var expected=new TreeMap<Position,SparseVoxelOctree>(Comparator.comparingInt(Position::y).thenComparingInt(Position::z).thenComparingInt(Position::x));
                int modelId=WorldVoxels.decode(prepared.cells().get(destination(new Position(124,0,15),rotation)).get(0,0,0));
                assertTrue(prepared.modelSnapshot().get(modelId).definition().voxels().sameValues(reference(asset.models.get(0).voxels(),rotation)));
                for(var cell:asset.cells)expected.put(destination(cell.position(),rotation),cell.model()>=0?uniform(WorldVoxels.encode(modelId)).freeze():reference(cell.geometry(),rotation));
                var actualCells=new ArrayList<Protocol.CellState>();var expectedCells=new ArrayList<Protocol.CellState>();
                for(var entry:prepared.cells().entrySet()){var p=entry.getKey();assertTrue(entry.getValue().sameValues(expected.get(p)));actualCells.add(new Protocol.CellState(p.x(),p.y(),p.z(),entry.getValue()));}
                for(var entry:expected.entrySet()){var p=entry.getKey();expectedCells.add(new Protocol.CellState(p.x(),p.y(),p.z(),entry.getValue()));}
                assertArrayEquals(StructureWire.encode(new StructureWire.Delta(List.of(),expectedCells)),StructureWire.encode(new StructureWire.Delta(List.of(),actualCells)));
                var first=prepared.cells().get(destination(new Position(0,0,0),rotation));
                assertSame(first,prepared.cells().get(destination(new Position(127,0,15),rotation)),"Early cached material still shares after overflow");
                assertThrows(IllegalStateException.class,()->first.set(0,0,0,0));
                assertSame(prepared.cells().get(destination(new Position(127,0,13),rotation)),prepared.cells().get(destination(new Position(127,0,14),rotation)),"Late repeated material must keep sharing beyond 1024 entries");
                assertSame(prepared.cells().get(destination(new Position(124,0,15),rotation)),prepared.cells().get(destination(new Position(125,0,15),rotation)));
                assertTrue(SOURCE.cell(320,180,320).isEmpty());
            }
            assertFalse(engine.busy());
        }
        assertArrayEquals(sourceBytes,asset.encode());
    }
    @Test void cachesDoNotSurvivePreparationsAndCancellationReleasesReservation()throws Exception {
        var asset=fixture();var engine=new StructureEngine();SparseVoxelOctree before;
        try(var prepared=engine.prepare(asset,TARGET,0,SOURCE,()->false,p->{})){before=prepared.cells().get(TARGET);}
        var cancel=new AtomicBoolean();assertThrows(CancellationException.class,()->engine.prepare(asset,TARGET,0,SOURCE,cancel::get,p->{if(p>60)cancel.set(true);}));
        assertFalse(engine.busy());
        try(var prepared=engine.prepare(asset,TARGET,0,SOURCE,()->false,p->{})){assertNotSame(before,prepared.cells().get(TARGET));assertTrue(before.sameValues(prepared.cells().get(TARGET)));}
        assertFalse(engine.busy());assertTrue(EMPTY.isEmpty());
    }
}
