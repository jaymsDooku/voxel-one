package dev.jayms.net.structure;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.jayms.net.structure.StructurePackage.*;

class StructureDecodedContentsTest {
    private SparseVoxelOctree stone(){var t=new SparseVoxelOctree(16);t.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));return t;}
    private byte[] wire(String name,Bounds bounds,List<ModelDefinition> models,List<Cell> cells,int version)throws Exception {
        var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeInt(MAGIC);out.writeInt(version);out.writeUTF(name);
            for(int v:new int[]{bounds.x(),bounds.y(),bounds.z(),bounds.ax(),bounds.ay(),bounds.az()})out.writeInt(v);
            out.writeInt(models.size());for(var m:models)if(version==1)m.write(out);else StructureModelCodec.write(out,m);
            out.writeInt(cells.size());
            for(var c:cells){var p=c.position();out.writeShort(p.x()/16);out.writeByte(p.y()/16);out.writeShort(p.z()/16);out.writeShort((p.y()%16*16+p.z()%16)*16+p.x()%16);out.writeShort(c.model());if(c.model()<0)c.geometry().write(out);}
        }
        return bytes.toByteArray();
    }
    @Test void directDecoderRetainsMaterialBoundsNameAndOrderingRejections()throws Exception {
        var bounds=new Bounds(2,1,1,0,0,0);var first=new Cell(new Position(0,0,0),stone(),-1);var second=new Cell(new Position(1,0,0),stone(),-1);
        for(var cells:List.of(List.of(first,first),List.of(second,first),List.of(new Cell(new Position(2,0,0),stone(),-1))))
            assertThrows(IOException.class,()->StructurePackage.decode(wire("Invalid",bounds,List.of(),cells,2)));
        assertThrows(IOException.class,()->StructurePackage.decode(wire("!",bounds,List.of(),List.of(first),2)));
        for(int type:new int[]{Blocks.FLOWER_POT,Blocks.piece(Blocks.STONE,1)}) {
            var invalid=new SparseVoxelOctree(16);invalid.fill(0,0,0,16,16,16,WorldVoxels.encode(type));
            assertThrows(IOException.class,()->StructurePackage.decode(wire("Invalid",bounds,List.of(),List.of(new Cell(new Position(0,0,0),invalid,-1)),2)));
        }
        var fractionalModel=new SparseVoxelOctree(16);fractionalModel.set(1,2,3,WorldVoxels.encode(Blocks.FLOWER_POT));
        assertThrows(IOException.class,()->StructurePackage.decode(wire("Invalid",bounds,List.of(),List.of(new Cell(new Position(0,0,0),fractionalModel,-1)),2)));
    }
    @Test void decodedDefinitionsAndGeometryAreFrozenAndMatchUntrustedConstruction()throws Exception {
        var model=new SparseVoxelOctree(32);model.set(31,30,29,0xff123456);
        var models=List.of(new ModelDefinition("Part",model));
        var mixed=new SparseVoxelOctree(16);mixed.fill(1,2,3,2,3,4,0xfe123456);mixed.set(14,13,12,WorldVoxels.encode(Blocks.GLASS));
        var bounds=new Bounds(1024,256,1024,1,2,3);
        var cells=List.of(new Cell(new Position(0,0,0),stone(),-1),new Cell(new Position(1,0,0),mixed,-1),new Cell(new Position(1023,255,1023),null,0));
        var expected=new StructurePackage("Decode receipt",bounds,models,cells);
        for(int version:List.of(1,2)) {
            var decoded=StructurePackage.decode(wire("Decode receipt",bounds,models,cells,version));
            assertEquals(expected.cells,decoded.cells);assertEquals(expected.leafCount,decoded.leafCount);assertEquals(1,decoded.instanceCount);
            assertArrayEquals(expected.encode(),decoded.encode());
            assertThrows(IllegalStateException.class,()->decoded.models.get(0).voxels().set(0,0,0,0xffabcdef));
            assertThrows(IllegalStateException.class,()->decoded.cells.get(1).geometry().set(0,0,0,0xffabcdef));
        }
    }
    @Test void directDecoderStillChecksDependencyAndAggregateInstanceComplexity()throws Exception {
        var tree=new SparseVoxelOctree(8);
        for(int y=0;y<8;y++)for(int z=0;z<8;z++)for(int x=0;x<8;x++)tree.set(x,y,z,0xff000000|((x+y+z)%2==0?0x123456:0xabcdef));
        var model=new ModelDefinition("Surface",tree);var bounds=new Bounds(1024,1,3,0,0,0);
        var instances=new ArrayList<Cell>();for(int i=0;i<3000;i++)instances.add(new Cell(new Position(i%1024,0,i/1024),null,0));
        instances.sort(Comparator.comparingLong(c->StructurePackage.order(c.position())));
        assertEquals("Experimental instance surface cap",assertThrows(IOException.class,()->StructurePackage.decode(wire("Surface",bounds,List.of(model),instances,2))).getMessage());
        assertThrows(IOException.class,()->StructurePackage.decode(wire("Duplicates",bounds,List.of(model,model),List.of(instances.get(0)),2)));
        assertThrows(IOException.class,()->StructurePackage.decode(wire("Empty dependency",bounds,List.of(new ModelDefinition("Empty",new SparseVoxelOctree(8))),List.of(instances.get(0)),2)));
    }
    @Test void orderedFinalizationRejectsBacktrackingAndPreservesPublishedSnapshots()throws Exception {
        var good=new StructureCells.Builder();good.add(new Position(0,0,0),stone().freeze(),-1);
        var published=good.buildOrdered(()->false);good.add(new Position(1,0,0),stone().freeze(),-1);
        assertEquals(1,published.size());assertEquals(new Position(0,0,0),published.get(0).position());
        assertThrows(CancellationException.class,()->good.buildOrdered(()->true));
        var duplicate=new StructureCells.Builder();duplicate.add(new Position(0,0,0),stone().freeze(),-1);duplicate.add(new Position(0,0,0),stone().freeze(),-1);
        assertThrows(IOException.class,()->duplicate.buildOrdered(()->false));
        var backwards=new StructureCells.Builder();backwards.add(new Position(1,0,0),stone().freeze(),-1);backwards.add(new Position(0,0,0),stone().freeze(),-1);
        assertThrows(IOException.class,()->backwards.buildOrdered(()->false));
    }
}
