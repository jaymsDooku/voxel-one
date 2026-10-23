package dev.jayms.net.structure;

import dev.jayms.net.model.SparseVoxelOctree;
import java.io.IOException;
import java.util.*;
import static dev.jayms.net.structure.StructurePackage.*;

/** Immutable placement map. Keys are relative to one bounded footprint, never world IDs. */
final class StructurePlacementCells extends AbstractMap<Position,SparseVoxelOctree> {
    private final StructureEngine.Footprint box;
    private final int[] keys;
    private final SparseVoxelOctree[] trees;
    private StructurePlacementCells(StructureEngine.Footprint box,int[] keys,SparseVoxelOctree[] trees) {
        this.box=box;this.keys=keys;this.trees=trees;
    }
    private static int key(StructureEngine.Footprint box,Position p) {
        long x=(long)p.x()-box.minX(),y=(long)p.y()-box.minY(),z=(long)p.z()-box.minZ();
        if(x<0||x>=box.width()||y<0||y>=box.height()||z<0||z>=box.depth())return -1;
        return (int)x|((int)z<<10)|((int)y<<20);
    }
    private Position position(int key) {
        return new Position(box.minX()+(key&1023),box.minY()+(key>>>20),box.minZ()+((key>>>10)&1023));
    }
    @Override public int size(){return keys.length;}
    @Override public SparseVoxelOctree get(Object candidate) {
        if(!(candidate instanceof Position p))return null;
        int key=key(box,p);if(key<0)return null;
        int index=Arrays.binarySearch(keys,key);return index<0?null:trees[index];
    }
    @Override public boolean containsKey(Object key){return get(key)!=null;}
    @Override public Set<Entry<Position,SparseVoxelOctree>> entrySet() {
        return new AbstractSet<>() {
            public int size(){return keys.length;}
            public Iterator<Entry<Position,SparseVoxelOctree>> iterator() {
                return new Iterator<>() {
                    int index;
                    public boolean hasNext(){return index<keys.length;}
                    public Entry<Position,SparseVoxelOctree> next() {
                        if(!hasNext())throw new NoSuchElementException();
                        int i=index++;return new SimpleImmutableEntry<>(position(keys[i]),trees[i]);
                    }
                };
            }
        };
    }
    /** Internal staging only: supplied trees must already be detached and frozen. */
    static final class Builder {
        private final StructureEngine.Footprint box;
        private final int[] keys;
        private final SparseVoxelOctree[] trees;
        private int count;
        Builder(StructureEngine.Footprint box,int capacity) {
            if(capacity<1||capacity>MAX_CELLS||box.width()<1||box.width()>1024||box.height()<1||box.height()>256||box.depth()<1||box.depth()>1024)
                throw new IllegalArgumentException("Placement buffer bounds/count");
            // Published coordinates must be representable even for direct internal callers.
            if((long)box.minX()+box.width()-1>Integer.MAX_VALUE||(long)box.minY()+box.height()-1>Integer.MAX_VALUE
                    ||(long)box.minZ()+box.depth()-1>Integer.MAX_VALUE)throw new IllegalArgumentException("Placement coordinate overflow");
            this.box=box;keys=new int[capacity];trees=new SparseVoxelOctree[capacity];
        }
        void add(Position position,SparseVoxelOctree tree)throws IOException {
            if(count==keys.length)throw new IOException("Placement buffer full");
            int key=key(box,position);if(key<0||tree==null)throw new IOException("Placement cell outside footprint/missing tree");
            keys[count]=key;trees[count++]=tree;
        }
        Map<Position,SparseVoxelOctree> build()throws IOException {
            if(count!=keys.length)throw new IOException("Incomplete placement buffer");
            long[] order=new long[count];
            for(int i=0;i<count;i++)order[i]=((long)keys[i]<<22)|i;
            Arrays.sort(order);int[] sorted=new int[count];var values=new SparseVoxelOctree[count];
            for(int i=0;i<count;i++) {
                int key=(int)(order[i]>>>22);if(i>0&&key==sorted[i-1])throw new IOException("Duplicate placement cell");
                sorted[i]=key;values[i]=trees[(int)(order[i]&((1<<22)-1))];
            }
            return Collections.unmodifiableMap(new StructurePlacementCells(box,sorted,values));
        }
    }
}
