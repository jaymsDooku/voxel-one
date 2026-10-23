package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import static dev.jayms.net.structure.StructurePackage.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureCaptureChunkTest {
    @Test void hintsStayConservativeAcrossTerrainLimitsAirOverridesAndSnapshots() {
        var geometry=new WorldVoxels(new Terrain(42));
        assertFalse(geometry.emptyChunk(new ChunkPos(0,5,0))); // Includes terrain y=95.
        assertTrue(geometry.emptyChunk(new ChunkPos(0,6,0))); // Starts at y=96.
        assertFalse(geometry.emptyChunk(new ChunkPos(0,-2,0))); // Includes terrain y=-32.
        assertTrue(geometry.emptyChunk(new ChunkPos(0,-3,0)));
        var before=geometry.snapshot();geometry.apply(new Protocol.Edit(-17,180,-1,Blocks.STONE));
        var position=ChunkPos.fromBlock(-17,180,-1);assertTrue(before.emptyChunk(position));assertFalse(geometry.emptyChunk(position));
        geometry.apply(new Protocol.Edit(-18,180,-1,0));assertFalse(geometry.emptyChunk(position));
        var occupied=geometry.snapshot();geometry.apply(new Protocol.Edit(-17,180,-1,0));assertTrue(geometry.emptyChunk(position));assertFalse(occupied.emptyChunk(position));
        geometry.apply(new Protocol.Edit(0,-40,0,Blocks.piece(Blocks.LED,4),4,1,2,3,0x123456));assertFalse(geometry.emptyChunk(new ChunkPos(0,-3,0)));
    }
    @Test void negativeUnalignedCapturePreservesBytesDependenciesFractionalDetailsAndSourceIndependence()throws Exception {
        try(var world=new World(42)) {
            var a=new SparseVoxelOctree(8);a.set(1,2,3,0xff123456);var b=new SparseVoxelOctree(16);b.set(3,2,1,0xffabcdef);
            int idA=world.models().stageStructureModel(new ModelDefinition("A",a)).id(),idB=world.models().stageStructureModel(new ModelDefinition("B",b)).id();
            for(var edit:List.of(new Protocol.Edit(-17,180,-17,Blocks.STONE),new Protocol.Edit(22,212,15,idB),
                new Protocol.Edit(15,180,-16,idA),new Protocol.Edit(-16,181,-1,idB),new Protocol.Edit(-1,196,0,Blocks.piece(Blocks.LED,4),4,1,2,3,0x987654),new Protocol.Edit(23,180,0,Blocks.BRICKS)))world.apply(edit);
            var snapshot=world.structureSnapshot();world.apply(new Protocol.Edit(-17,180,-17,Blocks.BRICKS));world.apply(new Protocol.Edit(0,200,0,Blocks.STONE));
            var bounds=new Bounds(40,33,33,1,2,3);var origin=new Position(-17,180,-17);byte[] reference=null;int[] baselineReads={0};
            for(boolean fast:new boolean[]{false,true}) {
                int[] cells={0},hints={0};var progress=new ArrayList<Integer>();
                Source source=new Source(){
                    public SparseVoxelOctree cell(int x,int y,int z){return snapshot.cell(x,y,z);}
                    public boolean emptyCell(int x,int y,int z){cells[0]++;return snapshot.emptyCell(x,y,z);}
                    public boolean emptyChunk(ChunkPos p){hints[0]++;return fast&&snapshot.emptyChunk(p);}
                    public ModelLibrary models(){return snapshot.models();}
                };
                var captured=StructurePackage.capture("Chunk hints",bounds,origin,source,()->false,progress::add);
                assertEquals(5,captured.cells.size());assertEquals(2,captured.models.size());assertEquals(100,progress.get(progress.size()-1));
                for(int i=1;i<progress.size();i++)assertTrue(progress.get(i)>=progress.get(i-1));
                if(fast){assertArrayEquals(reference,captured.encode());assertTrue(cells[0]<baselineReads[0]);}
                else{reference=captured.encode();baselineReads[0]=cells[0];assertEquals(40*33*33,cells[0]);}
                assertTrue(hints[0]<=4*3*4);
            }
            assertEquals(Blocks.STONE,WorldVoxels.decode(snapshot.cell(-17,180,-17).uniform(0,0,0,16)));
        }
    }
    @Test void skippedAirCannotBypassWorldBoundsAndCancellationRemainsAvailable()throws Exception {
        var calls=new AtomicInteger();var library=new ModelLibrary();
        Source empty=new Source(){
            public SparseVoxelOctree cell(int x,int y,int z){fail("Empty chunk materialized");return null;}
            public boolean emptyChunk(ChunkPos p){calls.incrementAndGet();return true;}
            public ModelLibrary models(){return library;}
        };
        for(var origin:List.of(new Position(Integer.MAX_VALUE,180,0),new Position(Integer.MIN_VALUE,180,0),new Position(Terrain.LIMIT,180,0),new Position(0,1023,0),new Position(0,-1025,0),new Position(0,180,Integer.MAX_VALUE))) {
            assertThrows(IOException.class,()->StructurePackage.capture("Invalid bounds",new Bounds(2,2,2,0,0,0),origin,empty,()->false,p->{}));
        }
        assertEquals(0,calls.get());
        var checks=new AtomicInteger();assertThrows(java.util.concurrent.CancellationException.class,()->StructurePackage.capture("Cancelled",new Bounds(1024,256,1024,0,0,0),new Position(-17,128,-17),empty,()->checks.incrementAndGet()>2000,p->{}));
        assertTrue(calls.get()>0);
    }
    @Test void belowTerrainCaptureRetainsTheLowestAllowedWorldCell()throws Exception {
        try(var world=new World(42)) {
            world.apply(new Protocol.Edit(0,-1024,0,Blocks.STONE));var snapshot=world.structureSnapshot();
            var captured=StructurePackage.capture("Lowest cell",new Bounds(2,2,2,0,0,0),new Position(-1,-1024,-1),snapshot,()->false,p->{});
            assertEquals(1,captured.cells.size());assertEquals(new Position(1,0,1),captured.cells.get(0).position());
            assertEquals(WorldVoxels.encode(Blocks.STONE),captured.cells.get(0).geometry().uniform(0,0,0,16));
        }
    }
    @Test void terrainIntersectingAndUnknownSourcesKeepFullCaptureBehavior()throws Exception {
        try(var world=new World(42)) {
            var snapshot=world.structureSnapshot();
            var origin=new Position(-2,12,-2);var bounds=new Bounds(5,4,5,0,0,0);
            Source fallback=new Source(){public SparseVoxelOctree cell(int x,int y,int z){return snapshot.cell(x,y,z);}public ModelLibrary models(){return snapshot.models();}};
            assertArrayEquals(StructurePackage.capture("Terrain",bounds,origin,fallback,()->false,p->{}).encode(),StructurePackage.capture("Terrain",bounds,origin,snapshot,()->false,p->{}).encode());
        }
    }
}
