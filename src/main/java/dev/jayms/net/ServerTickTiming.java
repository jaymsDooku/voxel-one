package dev.jayms.net;

import java.util.Arrays;

/** Bounded diagnostics only. No world, player or account data is retained. */
final class ServerTickTiming {
    static final int CAPACITY=4096;
    private final long[] starts=new long[CAPACITY],waits=new long[CAPACITY],work=new long[CAPACITY];
    private int next,size;private long total,failures;
    synchronized void record(long requested,long started,long finished,boolean succeeded) {
        starts[next]=started;waits[next]=Math.max(0,started-requested);work[next]=Math.max(0,finished-started);
        next=(next+1)%CAPACITY;size=Math.min(CAPACITY,size+1);total++;if(!succeeded)failures++;
    }
    MultiplayerServer.TickTiming snapshot() {
        long[] start,wait,duration;long count,failed;
        synchronized(this) {
            start=new long[size];wait=new long[size];duration=new long[size];count=total;failed=failures;
            for(int i=0;i<size;i++){int at=(next-size+i+CAPACITY)%CAPACITY;start[i]=starts[at];wait[i]=waits[at];duration[i]=work[at];}
        }
        var intervals=new long[Math.max(0,start.length-1)];
        for(int i=1;i<start.length;i++)intervals[i-1]=Math.max(0,start[i]-start[i-1]);
        long span=start.length<2?0:Math.max(0,start[start.length-1]-start[0]);
        return new MultiplayerServer.TickTiming(count,failed,start.length,span/1e6,
                span==0?0:(start.length-1)*1e9/span,distribution(intervals),distribution(wait),distribution(duration));
    }
    private static MultiplayerServer.TickDistribution distribution(long[] values) {
        Arrays.sort(values);return new MultiplayerServer.TickDistribution(values.length,percentile(values,.5),percentile(values,.95),values.length==0?0:values[values.length-1]/1e6);
    }
    // Nearest-rank percentiles; empty windows report zero and zero samples.
    private static double percentile(long[] sorted,double fraction){return sorted.length==0?0:sorted[(int)Math.ceil(sorted.length*fraction)-1]/1e6;}
}
