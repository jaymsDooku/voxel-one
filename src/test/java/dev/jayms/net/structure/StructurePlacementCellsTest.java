package dev.jayms.net.structure;

import dev.jayms.net.model.SparseVoxelOctree;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.jayms.net.structure.StructurePackage.*;

class StructurePlacementCellsTest {
    StructureEngine.Footprint box(int x,int y,int z){return new StructureEngine.Footprint(x,y,z,1024,256,1024,0,new Position(x,y,z));}
    SparseVoxelOctree tree(int value){var tree=new SparseVoxelOctree(16);tree.set(1,2,3,0xff000000|value);return tree.freeze();}
    @Test void shuffledCoordinatesAndValuesMatchReferenceMap()throws Exception {
        var box=box(-1500,-512,-2000);var random=new Random(20261021);var expected=new HashMap<Position,SparseVoxelOctree>();
        expected.put(new Position(-1500,-512,-2000),tree(1));expected.put(new Position(-477,-257,-977),tree(2));
        while(expected.size()<2000)expected.put(new Position(box.minX()+random.nextInt(1024),box.minY()+random.nextInt(256),box.minZ()+random.nextInt(1024)),tree(expected.size()+3));
        var entries=new ArrayList<>(expected.entrySet());Collections.shuffle(entries,random);
        var builder=new StructurePlacementCells.Builder(box,entries.size());for(var entry:entries)builder.add(entry.getKey(),entry.getValue());
        var actual=builder.build();assertEquals(expected,actual);assertEquals(actual,expected);assertEquals(expected.keySet(),actual.keySet());
        for(var entry:expected.entrySet())assertSame(entry.getValue(),actual.get(entry.getKey()));
        assertNull(actual.get(new Position(box.minX()-1,box.minY(),box.minZ())));
        assertNull(actual.get(new Position(box.minX()+1024,box.minY(),box.minZ())));
        assertNull(actual.get(new Position(box.minX(),box.minY()+256,box.minZ())));
        assertNull(actual.get(new Position(box.minX(),box.minY(),box.minZ()+1024)));
        assertNull(actual.get(new Position(Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE)));
        assertNull(actual.get(null));assertNull(actual.get("invalid"));
        var entry=actual.entrySet().iterator().next();
        assertThrows(UnsupportedOperationException.class,()->actual.put(entry.getKey(),entry.getValue()));
        assertThrows(UnsupportedOperationException.class,()->actual.remove(entry.getKey()));
        assertThrows(UnsupportedOperationException.class,actual::clear);
        assertThrows(UnsupportedOperationException.class,()->entry.setValue(tree(4)));
        assertThrows(UnsupportedOperationException.class,()->actual.keySet().remove(entry.getKey()));
        assertThrows(IllegalStateException.class,()->entry.getValue().set(1,2,3,0xff000004));
    }
    @Test void rejectsDuplicateIncompleteOverflowAndMissingCells()throws Exception {
        var box=box(0,0,0);var builder=new StructurePlacementCells.Builder(box,2);var tree=tree(1);
        assertThrows(IOException.class,builder::build);builder.add(new Position(0,0,0),tree);builder.add(new Position(0,0,0),tree);
        assertThrows(IOException.class,builder::build);assertThrows(IOException.class,()->builder.add(new Position(1,0,0),tree));
        var one=new StructurePlacementCells.Builder(box,1);
        assertThrows(IOException.class,()->one.add(new Position(-1,0,0),tree));assertThrows(IOException.class,()->one.add(new Position(0,0,0),null));
        one.add(new Position(0,0,0),tree);assertEquals(1,one.build().size());
        assertThrows(IllegalArgumentException.class,()->new StructurePlacementCells.Builder(box(Integer.MAX_VALUE,0,0),1));
        var minimum=new StructurePlacementCells.Builder(box(Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE),1);
        var p=new Position(Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE);minimum.add(p,tree);var map=minimum.build();
        assertSame(tree,map.get(p));assertNull(map.get(new Position(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE)));
    }
}
