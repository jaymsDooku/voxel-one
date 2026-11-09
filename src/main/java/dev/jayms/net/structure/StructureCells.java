package dev.jayms.net.structure;

import dev.jayms.net.model.SparseVoxelOctree;
import java.io.DataOutputStream;
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
    private final int[] values;
    private final StructureGeometry geometry;
    private static final short UNIFORM=-2;

    private StructureCells(int[] positions,short[] models,int[] values,StructureGeometry geometry) {
        this.positions=positions;this.models=models;this.values=values;this.geometry=geometry;
    }
    @Override public int size(){return positions.length;}
    /** Immutable position-only view: footprint queries never decode geometry. */
    List<Position> occupiedPositions() {
        return new AbstractList<>() {
            @Override public int size(){return positions.length;}
            @Override public Position get(int index){Objects.checkIndex(index,positions.length);return position(positions[index]);}
        };
    }
    @Override public Cell get(int index) {
        Objects.checkIndex(index,positions.length);
        short model=models[index];
        return cell(positions[index],model>=0?null:model==UNIFORM?uniform(values[index]):geometry.read(values[index]),model>=0?model:-1);
    }
    private static SparseVoxelOctree uniform(int color) {
        var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,color);return tree.freeze();
    }
    /** Copy original geometry bytes directly; export need not reconstruct node graphs. */
    void writeCell(int index,DataOutputStream out)throws IOException {
        Objects.checkIndex(index,positions.length);int p=positions[index];short model=models[index];
        out.writeShort((p>>>12)&63);out.writeByte(p>>>24);out.writeShort((p>>>18)&63);
        out.writeShort(p&4095);out.writeShort(model>=0?model:-1);
        if(model==UNIFORM) {
            out.writeByte(16);out.writeByte(values[index]==0?0:1);if(values[index]!=0)out.writeInt(values[index]);
        } else if(model<0)geometry.write(values[index],out);
    }
    private static Cell cell(int p,SparseVoxelOctree tree,int model) {
        return new Cell(position(p),tree,model);
    }
    private static Position position(int p) {
        return new Position(((p>>>12)&63)*16+(p&15),
                (p>>>24)*16+((p>>>8)&15),((p>>>18)&63)*16+((p>>>4)&15));
    }

    /** Owns unsorted values; only validated bounds/model IDs and frozen trees may be added. */
    static final class Builder {
        // MAX_CELLS is below 2^22; the canonical spatial key uses at most 28 bits.
        private static final int INDEX_MASK=(1<<22)-1;
        private int[] positions;
        private short[] models;
        private int[] values;
        private final StructureGeometry.Builder geometry=new StructureGeometry.Builder();
        private int count;
        private final int expectedCount;
        Builder(){this(256,-1);}
        Builder(int capacity){this(capacity,capacity);}
        private Builder(int capacity,int expectedCount) {
            if(capacity<1||capacity>MAX_CELLS||capacity>INDEX_MASK)throw new IllegalArgumentException("Structure cell capacity");
            this.expectedCount=expectedCount;
            positions=new int[capacity];models=new short[capacity];values=new int[capacity];
        }
        int size(){return count;}
        /** Borrowed read-only view for immediate constructor validation; never publish it. */
        List<Cell> view() {
            return new AbstractList<>() {
                @Override public int size(){return count;}
                @Override public Cell get(int index) {
                    Objects.checkIndex(index,count);
                    short model=models[index];
                    return cell(positions[index],model>=0?null:model==UNIFORM?uniform(values[index]):geometry.read(values[index]),model>=0?model:-1);
                }
            };
        }
        void add(Position p,SparseVoxelOctree tree,int model) throws IOException {
            if(count>=MAX_CELLS)throw new IOException("Occupied cell limit");
            if(count==positions.length) {
                if(expectedCount>=0)throw new IllegalStateException("Too many structure cells");
                int capacity=Math.min(MAX_CELLS,positions.length+positions.length/2);
                positions=Arrays.copyOf(positions,capacity);models=Arrays.copyOf(models,capacity);values=Arrays.copyOf(values,capacity);
            }
            if(model< -1||model>=MAX_MODELS||model>=0&&tree!=null||model<0&&(tree==null||tree.size()!=16))throw new IOException("Invalid structure cell storage");
            int value=0;short code=(short)model;
            if(model<0) {
                int color=tree.uniform(0,0,0,16);
                if(color!=-1){code=UNIFORM;value=color;}else value=geometry.add(tree);
            }
            positions[count]=(int)StructurePackage.order(p);models[count]=code;values[count]=value;count++;
        }
        StructureCells build() throws IOException {return build(()->false);}
        StructureCells build(java.util.function.BooleanSupplier cancel) throws IOException {return build(cancel,false);}
        /** Decoder-only ordered input. Still rejects duplicates/backtracking and copies
         * every published array, but needs no sort or temporary permutation array. */
        StructureCells buildOrdered(java.util.function.BooleanSupplier cancel)throws IOException {return build(cancel,true);}
        private StructureCells build(java.util.function.BooleanSupplier cancel,boolean ordered) throws IOException {
            if(expectedCount>=0&&count!=expectedCount)throw new IllegalStateException("Incomplete structure cells");
            int[] order=ordered?null:StructureOrder.indices(positions,count,cancel);
            int[] sortedPositions=new int[count];short[] sortedModels=new short[count];
            int[] sortedValues=new int[count];int previous=-1;
            for(int i=0;i<count;i++) {
                if((i&1023)==0)cancelled(cancel);
                int source=ordered?i:order[i],key=positions[source];
                if(key<=previous)throw new IOException("Invalid or duplicate structure cell");
                previous=key;sortedPositions[i]=key;sortedModels[i]=models[source];sortedValues[i]=values[source];
            }
            // New arrays prevent this builder or its input from changing the published list.
            return new StructureCells(sortedPositions,sortedModels,sortedValues,geometry.snapshot());
        }
    }
}
