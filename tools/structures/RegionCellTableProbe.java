package dev.jayms.net;

import java.lang.management.ManagementFactory;
import java.util.*;

/** Component-only allocation/copy probe. No world, accounts, display or package mutation. */
public final class RegionCellTableProbe {
    private static volatile Object held;
    private static final com.sun.management.ThreadMXBean MEMORY=
            (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    private static String measure(Map<Integer,Object> source,RegionCellTable<Object> region,boolean primitive) {
        long[] nanos=new long[20],bytes=new long[20];long thread=Thread.currentThread().getId();
        for(int i=-3;i<20;i++) {
            long before=MEMORY.getThreadAllocatedBytes(thread),start=System.nanoTime();
            Map<Integer,Object> copy=primitive?new RegionCellTable<>(region):new HashMap<>(source);
            long elapsed=System.nanoTime()-start,allocated=MEMORY.getThreadAllocatedBytes(thread)-before;
            held=copy;
            if(!copy.equals(source))throw new AssertionError("Copy contents differ");
            if(i>=0){nanos[i]=elapsed;bytes[i]=allocated;}
        }
        return "{\"nanos\":"+Arrays.toString(nanos)+",\"allocated_bytes\":"+Arrays.toString(bytes)+"}";
    }
    public static void main(String[] args) {
        if(!MEMORY.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");
        MEMORY.setThreadAllocatedMemoryEnabled(true);
        System.out.print("{\"cases\":[");boolean first=true;
        for(int count:new int[]{2,127,512,1024,1025,4096}) {
            var source=new HashMap<Integer,Object>();var region=new RegionCellTable<Object>();
            for(int i=0;i<count;i++){int key=i*17&4095;var value=new Object();source.put(key,value);region.put(key,value);}
            if(!first)System.out.print(',');first=false;
            System.out.print("{\"cells\":"+count+",\"hash_map\":"+measure(source,region,false)
                    +",\"region_table\":"+measure(source,region,true)+"}");
        }
        System.out.println("]}");
    }
}
