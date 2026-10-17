package dev.jayms;

import java.lang.management.*;
import java.nio.file.*;
import java.io.*;

/** Opt-in primitive owner-operation trace. No world data, logs or stack arguments are captured. */
public final class OwnerTrace {
    public static final boolean ENABLED=Boolean.getBoolean("voxel.streaming.trace");
    private static final boolean HOST=Boolean.getBoolean("voxel.streaming.hosttrace");
    private static final ThreadMXBean CPU=ManagementFactory.getThreadMXBean();
    private static final com.sun.management.ThreadMXBean ALLOC=CPU instanceof com.sun.management.ThreadMXBean b?b:null;
    private static final java.util.List<GarbageCollectorMXBean> GC=ManagementFactory.getGarbageCollectorMXBeans();
    private static final ThreadLocal<State> STATE=ThreadLocal.withInitial(State::new);
    private static final class State {
        int depth,count,dropped;final String[] stack=new String[32],names=new String[8192];
        final long[][] starts=new long[32][8],rows=new long[8192][11];
    }
    private static long cpu(){return CPU.isCurrentThreadCpuTimeSupported()?CPU.getCurrentThreadCpuTime():-1;}
    private static long allocation(){return ALLOC==null?-1:ALLOC.getThreadAllocatedBytes(Thread.currentThread().threadId());}
    private static long gc(boolean time){long n=0;for(var bean:GC)n+=Math.max(0,time?bean.getCollectionTime():bean.getCollectionCount());return n;}
    public static int begin(String operation){
        if(!ENABLED)return -1;State s=STATE.get();if(s.depth==32)throw new IllegalStateException("Trace nesting");int i=s.depth++;
        s.stack[i]=operation;long[] a=s.starts[i];a[0]=System.nanoTime();a[1]=cpu();a[2]=allocation();a[3]=gc(true);a[4]=gc(false);a[5]=schedulerWait();throttle(a,6);return i;
    }
    /** Own-thread kernel accounting only. No process command lines, cgroup config or runtime logs. */
    private static long schedulerWait(){
        if(!HOST)return -1;
        try{String value=Files.readString(Path.of("/proc/thread-self/schedstat"));int first=value.indexOf(' '),last=value.indexOf(' ',first+1);return Long.parseLong(value.substring(first+1,last));}
        catch(IOException|RuntimeException unavailable){return -1;}
    }
    /** Read-only CPU usage counters in this process namespace; never read/change quota configuration. */
    private static void throttle(long[] target,int offset){
        target[offset]=target[offset+1]=-1;if(!HOST)return;
        try{
            String text=Files.readString(Path.of("/sys/fs/cgroup/cpu.stat"));
            for(String line:text.split("\n")){
                if(line.startsWith("nr_throttled "))target[offset]=Long.parseLong(line.substring(13).trim());
                else if(line.startsWith("throttled_usec "))target[offset+1]=Long.parseLong(line.substring(15).trim());
            }
        }catch(IOException|RuntimeException unavailable){target[offset]=target[offset+1]=-1;}
    }
    public static void end(int token,long units){
        if(token<0)return;State s=STATE.get();if(token!=--s.depth)throw new IllegalStateException("Trace scope order");
        if(s.count==s.rows.length){s.dropped++;return;}
        long allocated=allocation(),gcTime=gc(true),gcCount=gc(false),wait=schedulerWait();long[] r=s.rows[Math.min(s.count,s.rows.length-1)];throttle(r,9);long cpu=cpu(),now=System.nanoTime();
        long[] a=s.starts[token];s.names[s.count++]=s.stack[token];
        r[0]=a[0];r[1]=now-a[0];r[2]=a[1]<0?-1:cpu-a[1];r[3]=a[2]<0?-1:allocated-a[2];r[4]=gcTime-a[3];r[5]=gcCount-a[4];r[6]=units;r[7]=token;r[8]=wait<0||a[5]<0?-1:wait-a[5];r[9]=r[9]<0||a[6]<0?-1:r[9]-a[6];r[10]=r[10]<0||a[7]<0?-1:r[10]-a[7];
    }

    /** Observer calls after the measured owner work; profiling trials leave this disabled. */
    public static void flush(Path file,int sample)throws IOException{
        if(!ENABLED)return;State s=STATE.get();if(s.depth!=0)throw new IllegalStateException("Unclosed trace scope");boolean header=!Files.exists(file);
        try(var out=Files.newBufferedWriter(file,StandardOpenOption.CREATE,StandardOpenOption.APPEND)){
            if(header)out.write("sample,operation,start_ns,wall_ns,thread_cpu_ns,allocated_bytes,gc_ms,gc_count,units,depth,scheduler_wait_ns,cpu_throttled_count,cpu_throttled_us,dropped\n");
            for(int i=0;i<s.count;i++){out.write(sample+","+s.names[i]);for(long n:s.rows[i])out.write(","+n);out.write(","+s.dropped+"\n");s.names[i]=null;}
        }s.count=s.dropped=0;
    }
    private OwnerTrace(){}
}
