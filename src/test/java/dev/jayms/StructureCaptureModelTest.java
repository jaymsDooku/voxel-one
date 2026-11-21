package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import static dev.jayms.net.structure.StructurePackage.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureCaptureModelTest {
    private ModelDefinition model(String name,int color) {
        var tree=new SparseVoxelOctree(8);tree.fill(0,0,0,8,8,8,color);
        return new ModelDefinition(name,tree);
    }
    private SparseVoxelOctree instance(int id) {
        var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(id));return tree.freeze();
    }
    @Test void repeatedAndAliasedDefinitionsKeepReferenceBytesAndFirstEncounterOrder()throws Exception {
        var library=new ModelLibrary();var a=model("Shared",0xff123456);var b=model("Other",0xffabcdef);
        library.accept(new ModelLibrary.Entry(9,"synthetic",a));library.accept(new ModelLibrary.Entry(10,"synthetic",a));
        library.accept(new ModelLibrary.Entry(11,"synthetic",b));
        assertNotSame(library.get(9).definition(),library.get(10).definition());
        var trees=List.of(instance(9),instance(10),instance(11));
        Source source=new Source(){public SparseVoxelOctree cell(int x,int y,int z){return trees.get(Math.floorMod(x+17,3));}public ModelLibrary models(){return library;}};
        var bounds=new Bounds(33,1,128,2,0,3);var expected=new ArrayList<Cell>();
        for(int z=0;z<128;z++)for(int x=0;x<33;x++)expected.add(new Cell(new Position(x,0,z),null,x%3==2?1:0));
        var reference=new StructurePackage("Repeated",bounds,List.of(library.get(9).definition(),library.get(11).definition()),expected);
        var captured=StructurePackage.capture("Repeated",bounds,new Position(-17,180,-17),source,()->false,p->{});
        assertEquals(2,captured.models.size());assertEquals(4224,captured.instanceCount);
        assertArrayEquals(reference.encode(),captured.encode());
        a.voxels().set(0,0,0,0xff000001);
        assertArrayEquals(reference.encode(),captured.encode());
    }
    @Test void sameWorldIdWithDifferentDefinitionIdentityIsNotMistakenForCachedModel()throws Exception {
        var first=new ModelLibrary();first.accept(new ModelLibrary.Entry(9,"synthetic",model("First",0xff123456)));
        var second=new ModelLibrary();second.accept(new ModelLibrary.Entry(9,"synthetic",model("Second",0xffabcdef)));
        var current=new AtomicInteger();var tree=instance(9);
        Source changing=new Source(){public SparseVoxelOctree cell(int x,int y,int z){current.set(x);return tree;}public ModelLibrary models(){return current.get()%2==0?first:second;}};
        var captured=StructurePackage.capture("Identity",new Bounds(4,1,1,0,0,0),new Position(0,180,0),changing,()->false,p->{});
        assertEquals(List.of("First","Second"),captured.models.stream().map(ModelDefinition::name).toList());
        assertEquals(List.of(0,1,0,1),captured.cells.stream().map(Cell::model).toList());
    }
    @Test void repeatedInstancesStillCancelWithoutPoisoningTheNextCapture()throws Exception {
        var library=new ModelLibrary();library.accept(new ModelLibrary.Entry(9,"synthetic",model("Repeat",0xff123456)));
        var tree=instance(9);Source source=new Source(){public SparseVoxelOctree cell(int x,int y,int z){return tree;}public ModelLibrary models(){return library;}};
        var checks=new AtomicInteger();var bounds=new Bounds(256,1,16,0,0,0);long revision=library.revision();
        assertThrows(CancellationException.class,()->StructurePackage.capture("Cancelled",bounds,new Position(0,180,0),source,()->checks.incrementAndGet()>1000,p->{}));
        assertEquals(revision,library.revision());
        assertEquals(4096,StructurePackage.capture("Retry",bounds,new Position(0,180,0),source,()->false,p->{}).instanceCount);
    }
}
