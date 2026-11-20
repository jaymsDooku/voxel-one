package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import static dev.jayms.net.structure.StructurePackage.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructurePackageAdmissionTest {
    static SparseVoxelOctree denseWorld() {
        var tree=new SparseVoxelOctree(16);
        for(int i=0;i<4096;i++)tree.fill(i%16,i/16%16,i/256,i%16+1,i/16%16+1,i/256+1,WorldVoxels.encode(new Protocol.Edit(0,0,0,Blocks.LED).withColor(i*3733)));
        return tree.freeze();
    }
    static StructurePackage geometry(int leaves)throws Exception {
        var dense=denseWorld();var one=new SparseVoxelOctree(16);one.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        var cells=new ArrayList<Cell>();int remaining=leaves;
        while(remaining>0){int i=cells.size();boolean full=remaining>=4096;cells.add(new Cell(new Position(i%64,0,i/64),full?dense:one,-1));remaining-=full?4096:1;}
        return new StructurePackage("Stored geometry",new Bounds(64,1,(cells.size()+63)/64,0,0,0),List.of(),cells);
    }
    static SparseVoxelOctree denseModel()throws Exception {
        var tree=new SparseVoxelOctree(32);for(int i=0;i<32768;i++)tree.set(i%32,i/32%32,i/1024,0xff000000|i);return tree.packedModelCopy();
    }
    static StructurePackage dependencies(int count)throws Exception {
        var shared=denseModel();var models=new ArrayList<ModelDefinition>();for(int i=0;i<count;i++)models.add(new ModelDefinition("Dependency "+i,shared));
        var stone=new SparseVoxelOctree(16);stone.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        return new StructurePackage("Stored dependencies",new Bounds(1,1,1,0,0,0),models,List.of(new Cell(new Position(0,0,0),stone,-1)));
    }
    static StructurePackage instances(int count,SparseVoxelOctree model)throws Exception {
        var cells=new ArrayList<Cell>();for(int i=0;i<count;i++)cells.add(new Cell(new Position(i%128,0,i/128),null,0));
        return new StructurePackage("Stored instances",new Bounds(128,1,(count+127)/128,0,0,0),List.of(new ModelDefinition("Shared",model)),cells);
    }
    static void rejectedBeforeReads(StructurePackage asset,String message)throws Exception {
        var engine=new StructureEngine();var source=new StructurePlacementAdmissionTest.Empty(true);
        for(int r=0;r<4;r++) {
            int turn=r;var anchor=new Position(0,160,0);
            assertTrue(assertThrows(IOException.class,()->StructureEngine.admittedFootprint(asset,anchor,turn,()->false)).getMessage().contains(message));
            assertTrue(assertThrows(IOException.class,()->engine.prepare(asset,anchor,turn,source,()->false,p->{})).getMessage().contains(message));
            assertFalse(engine.busy());assertEquals(0,source.calls);
        }
    }
    @Test void storageCanExceedPlacementGeometryWithoutAuthorityAccess()throws Exception {
        var asset=geometry(StructureEngine.MAX_PLACEMENT_LEAVES+1);var loaded=decode(asset.encode());
        assertEquals(131073,loaded.leafCount);assertEquals(0,loaded.dependencyQuads);assertEquals(0,loaded.instanceQuads);
        rejectedBeforeReads(loaded,"131072 geometry leaves");
        assertDoesNotThrow(()->StructureEngine.admittedFootprint(geometry(131072),new Position(0,160,0),0,()->false));
    }
    @Test void dependencyAndInstanceComplexityAreRecomputedOnDecode()throws Exception {
        var dependencies=decode(dependencies(171).encode());assertEquals(171L*6144,dependencies.dependencyQuads);assertEquals(0,dependencies.instanceQuads);
        rejectedBeforeReads(dependencies,"dependency surface cap");
        var instances=decode(instances(171,denseModel()).encode());assertEquals(6144,instances.dependencyQuads);assertEquals(171L*6144,instances.instanceQuads);
        rejectedBeforeReads(instances,"instance surface cap");
        assertDoesNotThrow(()->StructureEngine.admittedFootprint(instances(170,denseModel()),new Position(0,160,0),0,()->false));
    }
    @Test void storageLeafLimitIsExactAndRoundtripsWithoutWorldMutation()throws Exception {
        var asset=geometry(MAX_LEAVES);assertEquals(8_388_608,asset.leafCount);byte[] encoded=asset.encode();var loaded=decode(encoded);
        assertEquals(asset.leafCount,loaded.leafCount);assertEquals(2048,loaded.cells.size());assertArrayEquals(encoded,loaded.encode());
        assertTrue(assertThrows(IOException.class,()->geometry(MAX_LEAVES+1)).getMessage().contains("geometry cap"));
    }
    @Test void storageSurfaceAndInstanceCountLimitsRemainEnforced()throws Exception {
        assertEquals(341L*6144,dependencies(341).dependencyQuads);
        assertTrue(assertThrows(IOException.class,()->dependencies(342)).getMessage().contains("dependency surface cap"));
        var dense=denseModel();assertEquals(10922L*6144,instances(10922,dense).instanceQuads);
        assertTrue(assertThrows(IOException.class,()->instances(10923,dense)).getMessage().contains("instance surface cap"));
        var small=new SparseVoxelOctree(8);small.set(0,0,0,0xff123456);
        assertEquals(16384,instances(16384,small).instanceCount);
        assertThrows(IOException.class,()->instances(16385,small));
    }
    @Test void constructorSnapshotsAndCaptureDeriveTheSameComplexity()throws Exception {
        var tree=new SparseVoxelOctree(8);tree.set(1,2,3,0xff123456);var definition=new ModelDefinition("Small",tree);
        var geometry=new SparseVoxelOctree(16);geometry.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        var asset=new StructurePackage("Counts",new Bounds(2,1,1,0,0,0),List.of(definition),List.of(new Cell(new Position(0,0,0),geometry,-1),new Cell(new Position(1,0,0),null,0)));
        String fingerprint=asset.models.get(0).fingerprint();tree.set(1,2,3,0xffabcdef);geometry.fill(0,0,0,16,16,16,0);
        assertEquals(fingerprint,asset.models.get(0).fingerprint());assertEquals(1,asset.leafCount);assertEquals(6,asset.dependencyQuads);assertEquals(6,asset.instanceQuads);
        var library=new ModelLibrary();int id=library.stageStructureModel(asset.models.get(0)).id();var modelCell=new SparseVoxelOctree(16);modelCell.fill(0,0,0,16,16,16,WorldVoxels.encode(id));
        var captured=capture("Counts",asset.bounds,new Position(0,160,0),new Source(){public ModelLibrary models(){return library;}public SparseVoxelOctree cell(int x,int y,int z){return x==0?asset.cells.get(0).geometry():modelCell;}},()->false,p->{});
        assertEquals(asset.leafCount,captured.leafCount);assertEquals(asset.dependencyQuads,captured.dependencyQuads);assertEquals(asset.instanceQuads,captured.instanceQuads);
        assertEquals(fingerprint,captured.models.get(0).fingerprint());assertArrayEquals(asset.encode(),captured.encode());
    }
}
