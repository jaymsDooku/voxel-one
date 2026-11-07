package dev.jayms.net.structure;

import java.io.IOException;
import java.util.function.BooleanSupplier;

/** Stable ordering of bounded 28-bit spatial keys; all long passes can be cancelled. */
final class StructureOrder {
    private StructureOrder() {}
    static int[] indices(int[] keys,int count,BooleanSupplier cancel)throws IOException {
        StructurePackage.cancelled(cancel);
        if(count<0||count>keys.length||count>StructurePackage.MAX_CELLS)
            throw new IllegalArgumentException("Structure sort count");
        // Retain keys in first-pass order to avoid scattered source-key reads
        // during the second pass. Three int buffers use 12 bytes per cell;
        // the final index buffer is returned without a separate result array.
        int[] order=new int[count],scratch=new int[count],sortedKeys=new int[count];int[] offsets=new int[1<<14];
        for(int i=0;i<count;i++) {
            if((i&1023)==0)StructurePackage.cancelled(cancel);
            if(keys[i]<0||keys[i]>=1<<28)throw new IllegalArgumentException("Structure sort key");
            order[i]=i;
        }
        for(int shift=0;shift<28;shift+=14) {
            java.util.Arrays.fill(offsets,0);
            for(int i=0;i<count;i++) {
                if((i&1023)==0)StructurePackage.cancelled(cancel);
                int key=shift==0?keys[i]:sortedKeys[i];
                offsets[(key>>>shift)&16383]++;
            }
            int total=0;
            for(int i=0;i<offsets.length;i++) {
                if((i&1023)==0)StructurePackage.cancelled(cancel);
                int n=offsets[i];offsets[i]=total;total+=n;
            }
            for(int i=0;i<count;i++) {
                if((i&1023)==0)StructurePackage.cancelled(cancel);
                int key=shift==0?keys[i]:sortedKeys[i];
                int destination=offsets[(key>>>shift)&16383]++;
                scratch[destination]=order[i];
                if(shift==0)sortedKeys[destination]=key;
            }
            int[] swap=order;order=scratch;scratch=swap;
        }
        StructurePackage.cancelled(cancel);return order;
    }
}
