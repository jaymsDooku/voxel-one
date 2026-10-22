package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureValidationStampTest {
    static final StructurePackage.Position DEST=new StructurePackage.Position(-17,128,-1);
    static class Authority implements StructureEngine.Authority {
        final Terrain terrain=new Terrain(42);final WorldVoxels geometry=new WorldVoxels(terrain);final ModelLibrary models=new ModelLibrary();
        final boolean stamps;int reads,commits;
        Authority(boolean stamps){this.stamps=stamps;}
        public Object lock(){return this;}public ModelLibrary models(){return models;}
        public SparseVoxelOctree cell(int x,int y,int z){reads++;return geometry.cell(x,y,z);}
        public Object chunkStamp(ChunkPos p){return stamps?geometry.chunkStamp(p):null;}
        StructurePackage.Source snapshot() {
            var copy=geometry.snapshot();var definitions=models.copy();long generation=copy.terrainRevision();
            return new StructurePackage.Source() {
                public ModelLibrary models(){return definitions;}
                public SparseVoxelOctree cell(int x,int y,int z){return copy.cell(x,y,z);}
                public Object chunkStamp(ChunkPos p){return stamps&&copy.terrainRevision()==generation?copy.chunkStamp(p):null;}
            };
        }
        public void commit(ModelLibrary staged,Map<StructurePackage.Position,SparseVoxelOctree> cells) {
            for(var e:cells.entrySet()){var p=e.getKey();geometry.replaceCell(p.x(),p.y(),p.z(),e.getValue());}commits++;
        }
    }
    static StructurePackage asset()throws Exception {
        var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        return new StructurePackage("Stamp",new StructurePackage.Bounds(1,1,1,0,0,0),List.of(),List.of(new StructurePackage.Cell(new StructurePackage.Position(0,0,0),tree,-1)));
    }
    @Test void untouchedChunkReusesValidationButUnknownSourcesRecheck()throws Exception {
        for(boolean trusted:new boolean[]{false,true}) {
            var authority=new Authority(trusted);var engine=new StructureEngine();
            try(var plan=engine.prepare(asset(),DEST,0,authority.snapshot(),()->false,p->{})){plan.commit("test",authority);}
            assertEquals(1,authority.commits);assertEquals(Blocks.STONE,authority.geometry.type(DEST.x(),DEST.y(),DEST.z()));
            if(trusted)assertEquals(1,authority.reads);else assertTrue(authority.reads>=4096);
        }
    }
    @Test void sameChunkEditsRevalidateWhileOutsideEditsRemainInteractive()throws Exception {
        for(boolean sameChunk:new boolean[]{false,true}) {
            var authority=new Authority(true);var engine=new StructureEngine();
            try(var plan=engine.prepare(asset(),DEST,0,authority.snapshot(),()->false,p->{})) {
                var edit=new Protocol.Edit(sameChunk?-18:0,128,sameChunk?-1:0,Blocks.BRICKS);authority.geometry.apply(edit);plan.commit("test",authority);
                assertEquals(Blocks.BRICKS,authority.geometry.type(edit.x(),edit.y(),edit.z()));
            }
            if(sameChunk)assertTrue(authority.reads>=4096);else assertEquals(1,authority.reads);
        }
    }
    @Test void terrainAbaAndRepeatedOwnedBucketEditsInvalidateStamps()throws Exception {
        var authority=new Authority(true);var engine=new StructureEngine();var snapshot=authority.snapshot();var chunk=ChunkPos.fromBlock(DEST.x(),DEST.y(),DEST.z());
        try(var plan=engine.prepare(asset(),DEST,0,snapshot,()->false,p->{})) {
            authority.terrain.stressGrid(new dev.jayms.net.city.StressGrid(32));authority.terrain.stressGrid(null);
            assertNull(snapshot.chunkStamp(chunk));plan.commit("test",authority);assertTrue(authority.reads>=4096);
        }
        var frozen=authority.geometry.snapshot();var stamp=frozen.chunkStamp(chunk);
        authority.geometry.apply(new Protocol.Edit(-18,128,-1,Blocks.STONE));var first=authority.geometry.chunkStamp(chunk);
        authority.geometry.apply(new Protocol.Edit(-19,128,-1,Blocks.BRICKS));assertNotEquals(first,authority.geometry.chunkStamp(chunk));assertEquals(stamp,frozen.chunkStamp(chunk));
    }
    @Test void newNeighbourInstancesCannotBypassChunkSurfaceBudget()throws Exception {
        var authority=new Authority(true);var engine=new StructureEngine();var tree=new SparseVoxelOctree(8);
        for(int y=0;y<8;y++)for(int z=0;z<8;z++)for(int x=0;x<8;x++)if(((x+y+z)&1)==0)tree.set(x,y,z,0xff123456);
        int model=authority.models.stageStructureModel(new ModelDefinition("Fragmented component",tree)).id();
        try(var plan=engine.prepare(asset(),DEST,0,authority.snapshot(),()->false,p->{})) {
            for(int i=0;i<171;i++)authority.geometry.apply(new Protocol.Edit(-32+i%16,128,-16+i/16,model));
            var failure=assertThrows(java.io.IOException.class,()->plan.commit("test",authority));
            assertTrue(failure.getMessage().contains("chunk model-instance surface cap"));
            assertEquals(0,authority.commits);assertEquals(0,authority.geometry.type(DEST.x(),DEST.y(),DEST.z()));
            assertEquals(model,authority.geometry.type(-32,128,-16));
        }
    }
    @Test void cachedValidationStillRejectsChangedDestinationModelsAndCancellation()throws Exception {
        for(int mode=0;mode<3;mode++) {
            var authority=new Authority(true);var engine=new StructureEngine();var cancel=new AtomicBoolean();
            try(var plan=engine.prepare(asset(),DEST,0,authority.snapshot(),cancel::get,p->{})) {
                if(mode==0){authority.geometry.apply(new Protocol.Edit(DEST.x(),DEST.y(),DEST.z(),Blocks.BRICKS));assertThrows(java.io.IOException.class,()->plan.commit("test",authority));}
                if(mode==1){var tree=new SparseVoxelOctree(8);tree.set(0,0,0,0xff123456);authority.models.register(new ModelDefinition("New component",tree),"test");assertThrows(java.io.IOException.class,()->plan.commit("test",authority));}
                if(mode==2){cancel.set(true);assertThrows(java.util.concurrent.CancellationException.class,()->plan.commit("test",authority));}
            }
            assertEquals(0,authority.commits);assertFalse(engine.busy());
        }
    }
}
