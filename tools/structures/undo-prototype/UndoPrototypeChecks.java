package dev.jayms.net.structure;

import dev.jayms.net.WorldVoxels;
import dev.jayms.net.model.SparseVoxelOctree;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import static dev.jayms.net.structure.StructurePackage.*;

public class UndoPrototypeChecks {
    static StructureEngine.Footprint box(){return new StructureEngine.Footprint(-1000,-256,-2000,1024,256,1024,0,new Position(-1000,-256,-2000));}
    static SparseVoxelOctree mixed(){var tree=new SparseVoxelOctree(16);tree.fill(1,2,3,2,3,4,0xfe123456);tree.fill(12,9,6,16,12,10,WorldVoxels.encode(3));return tree;}
    static SparseVoxelOctree uniform(int value){var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,value);return tree;}
    void detachedPhysicalImagePreservesExtremePositionsReferencesAirAndFrozenValues()throws Exception {
        var original=new LinkedHashMap<Position,SparseVoxelOctree>();
        original.put(new Position(23,-1,-977),mixed());original.put(new Position(-1000,-256,-2000),uniform(WorldVoxels.encode(66559)));
        original.put(new Position(-999,-256,-2000),uniform(-1));original.put(new Position(-998,-256,-2000),new SparseVoxelOctree(16));
        var expected=new HashMap<Position,SparseVoxelOctree>();original.forEach((p,t)->expected.put(p,t.copy().freeze()));
        var image=UndoPrototypeImage.capture(original,box(),()->false);
        original.values().forEach(t->t.fill(0,0,0,16,16,16,0));original.clear();
        assertEquals(expected.size(),image.size());
        for(int i=0;i<image.size();i++) {
            var p=image.position(i);var tree=image.tree(i);assertTrue(tree.sameValues(expected.get(p)));
            assertThrows(IllegalStateException.class,()->tree.set(0,0,0,0xff123456));
        }
        var empty=image.emptyCells();assertEquals(expected.keySet(),empty.keySet());
        var shared=empty.values().iterator().next();assertEquals(0,shared.occupied());
        for(var p:expected.keySet()){assertTrue(empty.containsKey(p));assertSame(shared,empty.get(p));}
        for(Object p:Arrays.asList(null,"bad",new Position(Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE),new Position(24,-1,-977),new Position(-1000,0,-2000))) {
            assertFalse(empty.containsKey(p));assertNull(empty.get(p));
        }
        assertThrows(UnsupportedOperationException.class,empty::clear);
        assertThrows(UnsupportedOperationException.class,()->empty.put(new Position(0,0,0),shared));
        assertThrows(UnsupportedOperationException.class,()->empty.entrySet().iterator().next().setValue(shared));
        assertThrows(UnsupportedOperationException.class,()->empty.keySet().remove(expected.keySet().iterator().next()));
        assertThrows(IllegalStateException.class,()->shared.set(0,0,0,0xff123456));
        assertThrows(IndexOutOfBoundsException.class,()->image.position(-1));
        assertThrows(IndexOutOfBoundsException.class,()->image.tree(image.size()));
    }
    void imageRetainsNoSourceOctreesOrPlacementMap()throws Exception {
        var source=new HashMap<Position,SparseVoxelOctree>();source.put(new Position(-1000,-256,-2000),mixed().freeze());
        var image=UndoPrototypeImage.capture(source,box(),()->false);
        assertFalse(retains(image,source,new IdentityHashMap<>()));
    }
    static boolean retains(Object object,Object source,IdentityHashMap<Object,Boolean> seen)throws Exception {
        if(object==null||seen.put(object,true)!=null)return false;
        if(object==source||object instanceof SparseVoxelOctree)return true;
        var type=object.getClass();
        if(type.isArray()) {
            if(!type.getComponentType().isPrimitive())for(Object value:(Object[])object)if(retains(value,source,seen))return true;
        } else if(type.getName().startsWith("dev.jayms.net.structure.")) {
            for(var field:type.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&!field.getType().isPrimitive()) {
                field.setAccessible(true);if(retains(field.get(object),source,seen))return true;
            }
        }
        return false;
    }
    void allRotationsPreserveAlreadyTransformedGeometryWithoutRotatingTwice()throws Exception {
        for(int rotation=0;rotation<4;rotation++) {
            var relative=StructureEngine.rotate(new Position(2,0,1),3,5,rotation);
            var p=new Position(-20+relative.x(),128,-30+relative.z());var tree=StructureEngine.rotate(mixed(),rotation);
            var box=new StructureEngine.Footprint(-20,128,-30,rotation%2==0?3:5,1,rotation%2==0?5:3,rotation,new Position(-20,128,-30));
            var image=UndoPrototypeImage.capture(Map.of(p,tree),box,()->false);
            assertEquals(p,image.position(0));assertTrue(tree.sameValues(image.tree(0)));assertEquals(Set.of(p),image.emptyCells().keySet());
        }
    }
    void cancellationAndInvalidInputLeaveSourceUntouched()throws Exception {
        var source=new LinkedHashMap<Position,SparseVoxelOctree>();
        for(int i=0;i<8;i++)source.put(new Position(-1000+i,-256,-2000),mixed().freeze());
        var calls=new AtomicInteger();assertThrows(CancellationException.class,()->UndoPrototypeImage.capture(source,box(),()->calls.incrementAndGet()>3));
        assertEquals(8,source.size());assertTrue(source.values().stream().allMatch(t->t.occupied()>0));
        assertThrows(IOException.class,()->UndoPrototypeImage.capture(Map.of(new Position(24,-1,-977),mixed()),box(),()->false));
        var invalid=new StructureEngine.Footprint(0,0,0,1025,1,1,0,new Position(0,0,0));
        assertThrows(IOException.class,()->UndoPrototypeImage.capture(source,invalid,()->false));
        assertThrows(IOException.class,()->UndoPrototypeImage.capture(Map.of(),box(),()->false));
        var oversized=new AbstractMap<Position,SparseVoxelOctree>() {
            public int size(){return MAX_CELLS+1;}
            public Set<Entry<Position,SparseVoxelOctree>> entrySet(){throw new AssertionError("Must reject before reading/allocating cells");}
        };
        assertThrows(IOException.class,()->UndoPrototypeImage.capture(oversized,box(),()->false));
        assertEquals(8,UndoPrototypeImage.capture(source,box(),()->false).size());
    }
    interface Action {void run()throws Throwable;}
    static void assertTrue(boolean value){if(!value)throw new AssertionError("Expected true");}
    static void assertFalse(boolean value){assertTrue(!value);}
    static void assertNull(Object value){assertTrue(value==null);}
    static void assertSame(Object expected,Object actual){assertTrue(expected==actual);}
    static void assertEquals(Object expected,Object actual){assertTrue(Objects.equals(expected,actual));}
    static <T extends Throwable>T assertThrows(Class<T> expected,Action action) {
        try{action.run();}catch(Throwable failure){if(expected.isInstance(failure))return expected.cast(failure);throw new AssertionError("Unexpected failure",failure);}
        throw new AssertionError("Expected rejection");
    }
    public static void main(String[] args)throws Exception {
        var checks=new UndoPrototypeChecks();
        checks.detachedPhysicalImagePreservesExtremePositionsReferencesAirAndFrozenValues();
        checks.imageRetainsNoSourceOctreesOrPlacementMap();
        checks.allRotationsPreserveAlreadyTransformedGeometryWithoutRotatingTwice();
        checks.cancellationAndInvalidInputLeaveSourceUntouched();
        System.out.println("Playtest: four packed-undo diagnostic groups passed; game undo remains unchanged; NOT QUALIFIED");
    }
}
