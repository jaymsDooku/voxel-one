package dev.jayms.net.structure;

import dev.jayms.net.model.SparseVoxelOctree;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Per-preparation primitive material cache. Values must already be detached and frozen. */
final class StructureUniformCells {
    private static final int[] NO_KEYS=new int[0];
    private static final SparseVoxelOctree[] NO_VALUES=new SparseVoxelOctree[0];
    private final int maximum;
    private final BooleanSupplier cancel;
    private int[] keys=NO_KEYS;
    private SparseVoxelOctree[] values=NO_VALUES;
    private int size;
    StructureUniformCells(int maximum,BooleanSupplier cancel) {
        // Every occupied geometry cell has at least one leaf; models use their separate cache.
        if(maximum<0||maximum>StructurePackage.MAX_LEAVES)throw new IllegalArgumentException("Uniform material cache budget");
        this.maximum=maximum;this.cancel=Objects.requireNonNull(cancel);
    }
    private static int hash(int key) {
        key^=key>>>16;key*=0x7feb352d;key^=key>>>15;key*=0x846ca68b;return key^(key>>>16);
    }
    private int slot(int key,int[] searchKeys,SparseVoxelOctree[] searchValues) {
        int mask=searchValues.length-1,index=hash(key)&mask,probes=0;
        while(searchValues[index]!=null&&searchKeys[index]!=key) {
            if((++probes&1023)==0)StructurePackage.cancelled(cancel);
            if(probes>=searchValues.length)throw new IllegalStateException("Uniform material cache full");
            index=(index+1)&mask;
        }
        return index;
    }
    SparseVoxelOctree get(int key) {
        if(values.length==0)return null;
        return values[slot(key,keys,values)];
    }
    void put(int key,SparseVoxelOctree tree) {
        Objects.requireNonNull(tree);
        int index=-1;
        if(values.length!=0) {
            index=slot(key,keys,values);
            if(values[index]!=null){values[index]=tree;return;}
        }
        if(size>=maximum)throw new IllegalStateException("Uniform material cache budget exceeded");
        if(values.length==0||size==values.length/2){grow();index=slot(key,keys,values);}
        keys[index]=key;values[index]=tree;size++;
    }
    private void grow() {
        StructurePackage.cancelled(cancel);
        int limit=2;while(limit<maximum*2)limit<<=1;
        int capacity=values.length==0?Math.min(16,limit):Math.min(values.length*2,limit);
        if(capacity<=values.length)throw new IllegalStateException("Uniform material cache capacity");
        var nextKeys=new int[capacity];var nextValues=new SparseVoxelOctree[capacity];
        for(int i=0;i<values.length;i++) {
            if((i&1023)==0)StructurePackage.cancelled(cancel);
            if(values[i]!=null){int index=slot(keys[i],nextKeys,nextValues);nextKeys[index]=keys[i];nextValues[index]=values[i];}
        }
        keys=nextKeys;values=nextValues;
    }
}
