package dev.jayms.net.structure;

import java.util.*;
/** Sorting component only. Does not create or qualify populated structure fixtures. */
public final class SpatialOrderProbe {
    public static void main(String[] args)throws Exception {
        for(int count:new int[]{1000000,4000000}) {
            int[] keys=new int[count];var random=new Random(12491);
            for(int i=0;i<count;i++)keys[i]=random.nextInt(1<<28);
            long[] reference=new long[count];for(int i=0;i<count;i++)reference[i]=((long)keys[i]<<22)|i;
            Arrays.sort(reference);int[] actual=StructureOrder.indices(keys,count,()->false);
            for(int i=0;i<count;i++)if((reference[i]&((1<<22)-1))!=actual[i])throw new AssertionError("Order mismatch");
            double[][] times=new double[2][20];
            for(int trial=-2;trial<20;trial++)for(int mode=0;mode<2;mode++) {
                long start=System.nanoTime(),checksum=0;
                if(mode==0){long[] order=new long[count];for(int i=0;i<count;i++)order[i]=((long)keys[i]<<22)|i;Arrays.sort(order);checksum=order[count-1]&((1<<22)-1);}
                else {int[] order=StructureOrder.indices(keys,count,()->false);checksum=order[count-1];}
                if(checksum!=actual[count-1])throw new AssertionError("Trial mismatch");
                if(trial>=0)times[mode][trial]=(System.nanoTime()-start)/1e6;
            }
            for(int mode=0;mode<2;mode++){
                Arrays.sort(times[mode]);var t=times[mode];
                System.out.printf(Locale.ROOT,"{\"cells\":%d,\"mode\":\"%s\",\"runs\":20,\"median_ms\":%.3f,\"p95_ms\":%.3f,\"worst_ms\":%.3f,\"qualification\":\"sorting component only; not populated city/max qualification\"}%n",count,mode==0?"packed-long-control":"cancellable-radix",(t[9]+t[10])/2,t[18],t[19]);
            }
        }
    }
}
