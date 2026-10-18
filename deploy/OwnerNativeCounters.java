package dev.jayms;

import java.nio.ByteBuffer;
import org.lwjgl.system.Library;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.SharedLibrary;
import org.lwjgl.system.JNI;

/** Linux64 diagnostic only. getrusage reads this thread's counters; no files/configuration. */
public final class OwnerNativeCounters {
    private static final SharedLibrary LIB=Library.loadNative(OwnerNativeCounters.class,"org.lwjgl","libc.so.6");
    private static final long FUNCTION=LIB.getFunctionAddress("getrusage");
    private static final ThreadLocal<ByteBuffer> BUFFER=ThreadLocal.withInitial(()->MemoryUtil.memAlloc(144));
    public static void read(long[] target,int offset){
        if(!System.getProperty("os.name").equals("Linux")||org.lwjgl.system.Pointer.POINTER_SIZE!=8||FUNCTION==0)throw new IllegalStateException("Linux64 getrusage unavailable");
        long pointer=MemoryUtil.memAddress(BUFFER.get());
        if(JNI.invokePI(1,pointer,FUNCTION)!=0)throw new IllegalStateException("getrusage failed");
        target[offset]=MemoryUtil.memGetLong(pointer+64);  // ru_minflt
        target[offset+1]=MemoryUtil.memGetLong(pointer+72); // ru_majflt
        target[offset+2]=MemoryUtil.memGetLong(pointer+128); // ru_nvcsw
        target[offset+3]=MemoryUtil.memGetLong(pointer+136); // ru_nivcsw
    }
    public static void main(String[] args)throws Exception {
        long[] before=new long[4],after=new long[4];read(before,0);Thread.sleep(10);read(after,0);
        for(int i=0;i<4;i++)if(after[i]<before[i])throw new AssertionError("Counters must be monotonic");
        if(after[2]<=before[2])throw new AssertionError("Sleep must cause a voluntary switch");
        System.out.println("PASS: own-thread Linux64 fault/context-switch counters; sleep observed");
        MemoryUtil.memFree(BUFFER.get());BUFFER.remove();
    }
    private OwnerNativeCounters(){}
}
