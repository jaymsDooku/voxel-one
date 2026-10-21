package dev.jayms.net.structure;

import dev.jayms.net.model.SparseVoxelOctree;
import java.io.IOException;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Objects;
import java.util.List;
import java.util.RandomAccess;
import static dev.jayms.net.structure.StructurePackage.*;

/** Compact immutable storage for validated, frozen, spatially sorted cells. */
final class StructureCells extends AbstractList<Cell> implements RandomAccess {
    private final int[] positions;
    private final short[] models;
    private final SparseVoxelOctree[] geometry;

    private StructureCells(int[] positions,short[] models,SparseVoxelOctree[] geometry) {
        this.positions=positions;this.models=models;this.geometry=geometry;
    }
    @Override public int size(){return positions.length;}
    @Override public Cell get(int index) {
        Objects.checkIndex(index,positions.length);
        return cell(positions[index],geometry[index],models[index]);
    }
    private static Cell cell(int p,SparseVoxelOctree tree,int model) {
        return new Cell(new Position(((p>>>12)&63)*16+(p&15),
                (p>>>24)*16+((p>>>8)&15),((p>>>18)&63)*16+((p>>>4)&15)),tree,model);
    }

    /** Owns unsorted values; only validated bounds/model IDs and frozen trees may be added. */
    static final class Builder {
        // MAX_CELLS is below 2^22; the canonical spatial key uses at most 28 bits.
        private static final int INDEX_BITS=22,INDEX_MASK=(1<<INDEX_BITS)-1;
        private int[] positions;
        private short[] models;
        private SparseVoxelOctree[] geometry;
        private int count;
        private final int expectedCount;
        Builder(){this(256,-1);}
        Builder(int capacity){this(capacity,capacity);}
        private Builder(int capacity,int expectedCount) {
            if(capacity<1||capacity>MAX_CELLS||capacity>INDEX_MASK)throw new IllegalArgumentException("Structure cell capacity");
            this.expectedCount=expectedCount;
            positions=new int[capacity];models=new short[capacity];geometry=new SparseVoxelOctree[capacity];
        }
        int size(){return count;}
        /** Borrowed read-only view for immediate constructor validation; never publish it. */
        List<Cell> view() {
            return new AbstractList<>() {
                @Override public int size(){return count;}
                @Override public Cell get(int index) {
                    Objects.checkIndex(index,count);
                    return cell(positions[index],geometry[index],models[index]);
                }
            };
        }
        void add(Position p,SparseVoxelOctree tree,int model) throws IOException {
            if(count>=MAX_CELLS)throw new IOException("Occupied cell limit");
            if(count==positions.length) {
                if(expectedCount>=0)throw new IllegalStateException("Too many structure cells");
                int capacity=Math.min(MAX_CELLS,positions.length+positions.length/2);
                positions=Arrays.copyOf(positions,capacity);models=Arrays.copyOf(models,capacity);geometry=Arrays.copyOf(geometry,capacity);
            }
            positions[count]=(int)StructurePackage.order(p);models[count]=(short)model;geometry[count]=tree;count++;
        }
        StructureCells build() throws IOException {
            if(expectedCount>=0&&count!=expectedCount)throw new IllegalStateException("Incomplete structure cells");
            long[] order=new long[count];
            for(int i=0;i<count;i++) {
                cancelled(()->false);order[i]=((long)positions[i]<<INDEX_BITS)|i;
            }
            Arrays.sort(order);
            int[] sortedPositions=new int[count];short[] sortedModels=new short[count];
            var sortedGeometry=new SparseVoxelOctree[count];int previous=-1;
            for(int i=0;i<count;i++) {
                cancelled(()->false);
                int key=(int)(order[i]>>>INDEX_BITS),source=(int)(order[i]&INDEX_MASK);
                if(key==previous)throw new IOException("Invalid or duplicate structure cell");
                previous=key;sortedPositions[i]=key;sortedModels[i]=models[source];sortedGeometry[i]=geometry[source];
            }
            // New arrays prevent this builder or its input from changing the published list.
            return new StructureCells(sortedPositions,sortedModels,sortedGeometry);
        }
    }
}
