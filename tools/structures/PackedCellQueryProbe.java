package dev.jayms.net.model;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.util.*;

/** CPU component comparison with the previous decode-before-each-query path. */
public final class PackedCellQueryProbe {
    private static volatile int held;
    private static final com.sun.management.ThreadMXBean MEMORY=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    private static SparseVoxelOctree decode(byte[] bytes)throws IOException {
        try(var input=new DataInputStream(new ByteArrayInputStream(bytes))) {
            var tree=SparseVoxelOctree.read(input,16);
            if(input.read()!=-1)throw new IOException("Trailing probe bytes");return tree;
        }
    }
    private static String measure(SparseVoxelOctree packed,byte[] bytes,int[] points,boolean direct,boolean uniform)throws IOException {
        long[] nanos=new long[20],allocated=new long[20];long thread=Thread.currentThread().getId();int expected=0;
        for(int run=-3;run<20;run++) {
            long before=MEMORY.getThreadAllocatedBytes(thread),start=System.nanoTime();int checksum=0;
            for(int i=0;i<points.length;i++) {
                int p=points[i],x=p&15,y=p>>>8,z=(p>>>4)&15,side=1<<(i%5);
                var tree=direct?packed:decode(bytes);
                int value=uniform?tree.uniform(x&-side,y&-side,z&-side,side):tree.get(x,y,z);
                checksum=Integer.rotateLeft(checksum,1)^value;
            }
            long elapsed=System.nanoTime()-start,count=MEMORY.getThreadAllocatedBytes(thread)-before;held=checksum;
            if(run==-3)expected=checksum;else if(checksum!=expected)throw new AssertionError("Probe query differs");
            if(run>=0){nanos[run]=elapsed;allocated[run]=count;}
        }
        return "{\"checksum\":"+expected+",\"nanos\":"+Arrays.toString(nanos)+",\"allocated_bytes\":"+Arrays.toString(allocated)+"}";
    }
    public static void main(String[] args)throws Exception {
        if(!MEMORY.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");
        MEMORY.setThreadAllocatedMemoryEnabled(true);
        int[] points=new Random(20261110).ints(64,0,4096).toArray();
        System.out.print("{\"queries_per_sample\":64,\"cases\":[");boolean first=true;
        for(int dense=0;dense<2;dense++) {
            var tree=new SparseVoxelOctree(16);
            if(dense==0)tree.fill(9,10,11,10,11,12,0xfe314159);
            else for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)tree.fill(x,y,z,x+1,y+1,z+1,((x+y+z)&1)==0?0xff010203:0xfe314159);
            var packed=tree.packedCellCopy();var buffer=new ByteArrayOutputStream();tree.write(new DataOutputStream(buffer));byte[] bytes=buffer.toByteArray();
            for(boolean uniform:new boolean[]{false,true}) {
                if(!first)System.out.print(',');first=false;
                String baseline=measure(packed,bytes,points,false,uniform);int expected=held;
                String direct=measure(packed,bytes,points,true,uniform);
                if(held!=expected)throw new AssertionError("Direct query checksum differs from decoded query");
                System.out.print("{\"fixture\":\""+(dense==0?"sparse":"fragmented")+"\",\"query\":\""+(uniform?"uniform":"get")
                        +"\",\"decode_each_query\":"+baseline+",\"direct\":"+direct+"}");
            }
        }
        System.out.println("]}");
    }
}
