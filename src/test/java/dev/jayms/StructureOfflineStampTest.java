package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import static dev.jayms.net.structure.StructurePackage.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StructureOfflineStampTest {
    @TempDir Path folder;
    private StructurePackage asset()throws Exception {
        var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        return new StructurePackage("Offline stamp",new Bounds(1,1,1,0,0,0),List.of(),List.of(new Cell(new Position(0,0,0),tree,-1)));
    }
    @Test void unloadedProofTracksEditsTerrainAndSnapshotResidency()throws Exception {
        try(var world=new World(42)) {
            var position=new ChunkPos(-2,8,-1);var source=world.structureSnapshot();var stamp=source.chunkStamp(position);
            assertNotNull(stamp);assertEquals(stamp,world.structureChunkStamp(position));
            world.apply(new Protocol.Edit(100,128,100,Blocks.BRICKS));assertEquals(stamp,world.structureChunkStamp(position));
            world.apply(new Protocol.Edit(-17,128,-1,Blocks.BRICKS));assertNotEquals(stamp,world.structureChunkStamp(position));
            var updated=world.structureSnapshot();assertEquals(updated.chunkStamp(position),world.structureChunkStamp(position));
            world.addChunk(position,new Chunk());assertNull(world.structureChunkStamp(position));
            var resident=world.structureSnapshot();assertNull(resident.chunkStamp(position));
            world.unloadChunk(position);assertNull(resident.chunkStamp(position));assertNotNull(world.structureChunkStamp(position));
            world.terrain().stressGrid(new dev.jayms.net.city.StressGrid(32));world.terrain().stressGrid(null);
            assertNull(updated.chunkStamp(position));assertNull(source.chunkStamp(position));
        }
    }
    @Test void residentCollisionCannotBeHiddenByCanonicalSnapshotProof()throws Exception {
        for(boolean initiallyResident:new boolean[]{false,true}) {
            var local=new LocalGame(folder.resolve("resident-"+initiallyResident+".dat"),42);
            try(var world=new World(42,local.models)) {
                var position=new ChunkPos(0,8,0);var resident=new Chunk();resident.setBlock(1,0,1,Blocks.BRICKS);
                if(initiallyResident)world.addChunk(position,resident);
                var snapshot=world.structureSnapshot();var engine=new StructureEngine();
                try(var plan=engine.prepare(asset(),new Position(1,128,1),0,snapshot,()->false,p->{})) {
                    if(!initiallyResident)world.addChunk(position,resident);
                    assertThrows(IOException.class,()->plan.commit("synthetic",local.structureAuthority(world)));
                }
                assertEquals(Blocks.BRICKS,world.getBlock(1,128,1));assertTrue(local.edits.isEmpty());assertFalse(engine.busy());
            }
        }
    }
    @Test void unchangedUnloadedPlacementReusesProofAndStillPersistsAndUndoes()throws Exception {
        var path=folder.resolve("unloaded.dat");var local=new LocalGame(path,42);
        try(var world=new World(42,local.models){int reads;@Override public SparseVoxelOctree cell(int x,int y,int z){reads++;return super.cell(x,y,z);}}) {
            var engine=new StructureEngine();
            try(var plan=engine.prepare(asset(),new Position(320,180,320),0,world.structureSnapshot(),()->false,p->{})) {
                plan.commit("synthetic",local.structureAuthority(world));
            }
            assertTrue(world.reads<100,"Unchanged unloaded chunk does not repeat 4096-cell mesh validation");
            assertEquals(Blocks.STONE,new LocalGame(path,0).edits.values().stream().filter(e->e.type()!=0).findFirst().orElseThrow().type());
            engine.undo("synthetic",local.structureAuthority(world));assertEquals(0,world.sample(320,180,320));
        }
    }
    @Test void residentNeighbourInstancesStillEnforceLiveChunkSurfaceLimit()throws Exception {
        var local=new LocalGame(folder.resolve("surface.dat"),42);var model=new SparseVoxelOctree(8);
        for(int y=0;y<8;y++)for(int z=0;z<8;z++)for(int x=0;x<8;x++)if(((x+y+z)&1)==0)model.set(x,y,z,0xff123456);
        int id=local.models.stageStructureModel(new ModelDefinition("Fragmented",model)).id();
        try(var world=new World(42,local.models)) {
            var resident=new Chunk();for(int i=0;i<171;i++)resident.setBlock(i%16,0,i/16,id);world.addChunk(new ChunkPos(0,8,0),resident);
            var engine=new StructureEngine();
            try(var plan=engine.prepare(asset(),new Position(15,128,15),0,world.structureSnapshot(),()->false,p->{})) {
                var failure=assertThrows(IOException.class,()->plan.commit("synthetic",local.structureAuthority(world)));
                assertTrue(failure.getMessage().contains("chunk model-instance surface cap"));
            }
            assertEquals(0,world.sample(15,128,15));assertTrue(local.edits.isEmpty());assertFalse(engine.busy());
        }
    }
}
