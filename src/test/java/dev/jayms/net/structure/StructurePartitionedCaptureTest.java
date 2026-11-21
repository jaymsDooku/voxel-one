package dev.jayms.net.structure;

import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.jayms.net.structure.StructurePackage.*;

class StructurePartitionedCaptureTest {
    static SparseVoxelOctree tree(int type){var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(type));return tree.freeze();}
    static final class Source implements StructurePackage.Source {
        final ModelLibrary library=new ModelLibrary();final Map<Position,SparseVoxelOctree> cells=new HashMap<>();final Set<ChunkPos> visited=new HashSet<>();final SparseVoxelOctree empty=new SparseVoxelOctree(16).freeze();int chunkCalls;
        public ModelLibrary models(){return library;}
        public SparseVoxelOctree cell(int x,int y,int z){return cells.getOrDefault(new Position(x,y,z),empty);}
        public boolean emptyCell(int x,int y,int z){return cell(x,y,z).occupied()==0;}
        public boolean emptyChunk(ChunkPos chunk){chunkCalls++;assertTrue(visited.add(chunk),"Visit each clipped world chunk once");return false;}
        int model(String name,int color)throws Exception {var voxels=new SparseVoxelOctree(8);voxels.set(1,2,3,color);return library.stageStructureModel(new ModelDefinition(name,voxels)).id();}
    }
    static void same(Source source,Bounds bounds,Position origin)throws Exception {
        var expected=StructurePackage.capture("Physical capture",bounds,origin,source,()->false,n->{});source.visited.clear();source.chunkCalls=0;
        var actual=StructurePackage.capturePartitioned("Physical capture",bounds,origin,source,()->false,n->{});assertArrayEquals(expected.encode(),actual.encode());assertEquals(expected.cells,actual.cells);
    }
    @Test void preservesLogicalDependencyOrderWhenChunkOrderEncountersModelsBackwards()throws Exception {
        var source=new Source();int a=source.model("A",0xff123456),b=source.model("B",0xffabcdef);
        source.cells.put(new Position(16,160,0),tree(a));source.cells.put(new Position(0,161,0),tree(b));source.cells.put(new Position(31,161,0),tree(a));
        same(source,new Bounds(32,2,1,16,1,0),new Position(0,160,0));assertEquals(2,source.chunkCalls);
    }
    @Test void negativeUnalignedChunksPreserveFractionalColoursMaterialsHolesAndAliases()throws Exception {
        var source=new Source();int a=source.model("Shared",0xff13579b);source.library.acceptWorldEntry(new ModelLibrary.Entry(1200,"structure",source.library.get(a).definition()));
        source.cells.put(new Position(-17,160,-15),tree(a));source.cells.put(new Position(15,161,19),tree(1200));source.cells.put(new Position(0,160,0),tree(Blocks.WATER));
        var detail=new SparseVoxelOctree(16);detail.fill(8,0,0,16,8,8,WorldVoxels.encode(new Protocol.Edit(0,0,0,Blocks.LED).withColor(0x26cfa8)));source.cells.put(new Position(-2,161,1),detail.freeze());
        same(source,new Bounds(33,3,35,17,1,15),new Position(-17,160,-15));
        var actual=StructurePackage.capturePartitioned("Physical capture",new Bounds(33,3,35,17,1,15),new Position(-17,160,-15),new StructurePackage.Source(){public ModelLibrary models(){return source.library;}public SparseVoxelOctree cell(int x,int y,int z){return source.cell(x,y,z);}},()->false,n->{});
        assertEquals(1,actual.models.size());assertEquals(2,actual.instanceCount);assertEquals(4,actual.cells.size());
    }
    @Test void emptyChunkHintsSkipOnlyTheirClippedVolumeAndProgressIsMonotone()throws Exception {
        var source=new Source();source.cells.put(new Position(16,160,16),tree(Blocks.BRICKS));var progress=new ArrayList<Integer>();
        var hinted=new StructurePackage.Source(){public ModelLibrary models(){return source.library;}public SparseVoxelOctree cell(int x,int y,int z){assertTrue(x>=16&&z>=16);return source.cell(x,y,z);}public boolean emptyChunk(ChunkPos p){return p.chunkX()!=1||p.chunkZ()!=1;}};
        var bounds=new Bounds(33,2,33,0,0,0);var expected=StructurePackage.capture("Hints",bounds,new Position(0,160,0),hinted,()->false,n->{});
        var actual=StructurePackage.capturePartitioned("Hints",bounds,new Position(0,160,0),hinted,()->false,progress::add);assertArrayEquals(expected.encode(),actual.encode());assertEquals(100,progress.get(progress.size()-1));
        for(int i=1;i<progress.size();i++)assertTrue(progress.get(i)>=progress.get(i-1));
    }
    @Test void malformedInputsAndMissingModelsRejectWithoutSourceQueriesOrPublication()throws Exception {
        var source=new Source();assertThrows(IOException.class,()->StructurePackage.capturePartitioned("!",new Bounds(1,1,1,0,0,0),new Position(0,160,0),source,()->false,n->{}));
        assertThrows(IOException.class,()->StructurePackage.capturePartitioned("Bounds",new Bounds(2,1,1,0,0,0),new Position(Terrain.LIMIT,160,0),source,()->false,n->{}));assertEquals(0,source.chunkCalls);
        source.cells.put(new Position(0,160,0),tree(1024));assertThrows(IOException.class,()->StructurePackage.capturePartitioned("Missing",new Bounds(1,1,1,0,0,0),new Position(0,160,0),source,()->false,n->{}));
        source.cells.put(new Position(0,160,0),tree(190));source.visited.clear();assertThrows(IOException.class,()->StructurePackage.capturePartitioned("Material",new Bounds(1,1,1,0,0,0),new Position(0,160,0),source,()->false,n->{}));
    }
    @Test void cancellationIncludingFinalProgressNeverPublishesAndSourceStaysIndependent()throws Exception {
        var source=new Source();source.cells.put(new Position(0,160,0),tree(Blocks.STONE));var before=source.cells.get(new Position(0,160,0));
        assertThrows(CancellationException.class,()->StructurePackage.capturePartitioned("Cancel",new Bounds(32,2,32,0,0,0),new Position(0,160,0),source,()->true,n->{}));assertEquals(0,source.chunkCalls);
        var cancel=new AtomicBoolean();assertThrows(CancellationException.class,()->StructurePackage.capturePartitioned("Cancel",new Bounds(32,2,32,0,0,0),new Position(0,160,0),source,cancel::get,n->{if(n==100)cancel.set(true);}));assertSame(before,source.cells.get(new Position(0,160,0)));
        source.visited.clear();var captured=StructurePackage.capturePartitioned("Independent",new Bounds(1,1,1,0,0,0),new Position(0,160,0),source,()->false,n->{});var bytes=captured.encode();source.cells.put(new Position(0,160,0),tree(Blocks.WOOD));assertArrayEquals(bytes,captured.encode());
    }
    @Test void privateModelPermutationValidatesBeforeMutationAndPublishedArraysAreIndependent()throws Exception {
        var source=new Source();int a=source.model("A",0xff123456),b=source.model("B",0xffabcdef);var models=List.of(source.library.get(a).definition(),source.library.get(b).definition());
        var builder=new StructureCells.Builder();builder.add(new Position(0,0,0),null,1);builder.add(new Position(1,0,0),null,0);builder.add(new Position(2,0,0),tree(Blocks.STONE),-1);
        var old=new StructurePackage("Private",new Bounds(3,1,1,0,0,0),models,builder.view());var bytes=old.encode();assertThrows(IOException.class,()->builder.remapModels(new int[]{0,0},()->false));assertEquals(1,builder.view().get(0).model());
        builder.remapModels(new int[]{1,0},()->false);assertEquals(0,builder.view().get(0).model());assertEquals(-1,builder.view().get(2).model());assertArrayEquals(bytes,old.encode());
    }
}
