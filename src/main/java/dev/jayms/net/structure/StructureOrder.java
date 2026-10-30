package dev.jayms.net.structure;

import java.io.IOException;
import java.util.function.BooleanSupplier;

/** Stable ordering of bounded 28-bit spatial keys; all long passes can be cancelled. */
final class StructureOrder {
    private static final int INDEX_BITS=22,INDEX_MASK=(1<<INDEX_BITS)-1;
    private StructureOrder() {}
    static int[] indices(int[] keys,int count,BooleanSupplier cancel)throws IOException {
        StructurePackage.cancelled(cancel);
        if(count<0||count>keys.length||count>StructurePackage.MAX_CELLS)
            throw new IllegalArgumentException("Structure sort count");
        // Carry keys beside source indices to keep both radix passes sequential.
        // Two packed buffers plus the returned indices use 20 bytes per cell.
        long[] order=new long[count],scratch=new long[count];int[] offsets=new int[1<<14];
        for(int i=0;i<count;i++) {
            if((i&1023)==0)StructurePackage.cancelled(cancel);
            if(keys[i]<0||keys[i]>=1<<28)throw new IllegalArgumentException("Structure sort key");
            order[i]=((long)keys[i]<<INDEX_BITS)|i;
        }
        for(int shift=INDEX_BITS;shift<INDEX_BITS+28;shift+=14) {
            java.util.Arrays.fill(offsets,0);
            for(int i=0;i<count;i++) {
                if((i&1023)==0)StructurePackage.cancelled(cancel);
                offsets[(int)(order[i]>>>shift)&16383]++;
            }
            int total=0;
            for(int i=0;i<offsets.length;i++) {
                if((i&1023)==0)StructurePackage.cancelled(cancel);
                int n=offsets[i];offsets[i]=total;total+=n;
            }
            for(int i=0;i<count;i++) {
                if((i&1023)==0)StructurePackage.cancelled(cancel);
                long entry=order[i];scratch[offsets[(int)(entry>>>shift)&16383]++]=entry;
            }
            long[] swap=order;order=scratch;scratch=swap;
        }
        int[] result=new int[count];
        for(int i=0;i<count;i++) {
            if((i&1023)==0)StructurePackage.cancelled(cancel);
            result[i]=(int)(order[i]&INDEX_MASK);
        }
        StructurePackage.cancelled(cancel);return result;
    }
}
