package dev.jayms.net.structure;

import dev.jayms.net.model.SparseVoxelOctree;
import java.io.IOException;
import java.util.*;
import java.util.function.BooleanSupplier;
import static dev.jayms.net.structure.StructurePackage.*;

/** Diagnostic candidate only. The live game retains its existing undo maps. */
final class UndoPrototypeImage {
    private final StructureEngine.Footprint box;
    private final UndoPrototypeCells cells;
    private UndoPrototypeImage(StructureEngine.Footprint box,UndoPrototypeCells cells){this.box=box;this.cells=cells;}
    static UndoPrototypeImage capture(Map<Position,SparseVoxelOctree> after,StructureEngine.Footprint box,BooleanSupplier cancel)throws IOException {
        if(box.width()<1||box.width()>1024||box.height()<1||box.height()>256||box.depth()<1||box.depth()>1024)
            throw new IOException("Undo footprint outside limits");
        int count=after.size();if(count<1||count>MAX_CELLS)throw new IOException("Undo cell count outside limits");
        var packed=new UndoPrototypeCells.Builder(count);
        for(var entry:after.entrySet()) {
            cancelled(cancel);var relative=relative(box,entry.getKey());
            if(relative==null)throw new IOException("Undo cell outside footprint");
            // Model references are physical uniform values here, not package dependencies.
            packed.add(relative,entry.getValue(),-1);
        }
        cancelled(cancel);return new UndoPrototypeImage(box,packed.build(cancel));
    }
    private static Position relative(StructureEngine.Footprint box,Position p) {
        long x=(long)p.x()-box.minX(),y=(long)p.y()-box.minY(),z=(long)p.z()-box.minZ();
        if(x<0||x>=box.width()||y<0||y>=box.height()||z<0||z>=box.depth())return null;
        return new Position((int)x,(int)y,(int)z);
    }
    int size(){return cells.size();}
    Position position(int index) {
        var p=cells.positionAt(index);return new Position(box.minX()+p.x(),box.minY()+p.y(),box.minZ()+p.z());
    }
    SparseVoxelOctree tree(int index){return cells.get(index).geometry();}
    /** Every admitted destination was empty. This view holds no original map/tree references. */
    Map<Position,SparseVoxelOctree> emptyCells(){return Collections.unmodifiableMap(new EmptyCells(this));}
    private static final class EmptyCells extends AbstractMap<Position,SparseVoxelOctree> {
        private final UndoPrototypeImage image;
        private final SparseVoxelOctree empty=new SparseVoxelOctree(16).freeze();
        EmptyCells(UndoPrototypeImage image){this.image=image;}
        @Override public int size(){return image.size();}
        @Override public boolean containsKey(Object candidate) {
            if(!(candidate instanceof Position p))return false;
            var relative=relative(image.box,p);return relative!=null&&image.cells.indexOfPosition(relative)>=0;
        }
        @Override public SparseVoxelOctree get(Object key){return containsKey(key)?empty:null;}
        @Override public Set<Entry<Position,SparseVoxelOctree>> entrySet() {
            return new AbstractSet<>() {
                @Override public int size(){return image.size();}
                @Override public Iterator<Entry<Position,SparseVoxelOctree>> iterator() {
                    return new Iterator<>() {
                        int index;
                        public boolean hasNext(){return index<image.size();}
                        public Entry<Position,SparseVoxelOctree> next() {
                            if(!hasNext())throw new NoSuchElementException();
                            return new SimpleImmutableEntry<>(image.position(index++),empty);
                        }
                    };
                }
            };
        }
    }
}
