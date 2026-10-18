import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Diagnostic only. Samples this process's render owner; emits no stacks, arguments or logs. */
public class OwnerNativeSample {
    public static void main(String[] args)throws Exception {
        Thread owner=Thread.currentThread();
        Path wait=Path.of("/proc",Files.readSymbolicLink(Path.of("/proc/thread-self")).toString(),"wchan");
        var running=new AtomicBoolean(true);var counts=new TreeMap<String,Long>();
        Thread sampler=new Thread(()->{
            while(running.get()){
                String operation=null;
                for(var frame:owner.getStackTrace()){
                    if(!frame.getClassName().startsWith("org.lwjgl.opengl."))continue;
                    String method=frame.getMethodName();
                    if(method.contains("nglTexSubImage3D")){operation="pixels3D";break;}
                    if(method.contains("nglBufferSubData")){operation="bufferSubData";break;}
                    if(method.contains("nglTexImage3D")){operation="textureAllocation";break;}
                }
                if(operation!=null){
                    String state="unavailable";
                    try{String value=Files.readString(wait).trim();if(value.matches("[A-Za-z0-9_]+"))state=value;}catch(Exception ignored){}
                    String phase=StreamingRoute.first<0?"warmup":"route";
                    counts.merge(phase+"/"+operation+"/"+state,1L,Long::sum);
                }
                try{Thread.sleep(2);}catch(InterruptedException stop){return;}
            }
        },"owner-native-diagnostic");
        sampler.setDaemon(true);sampler.start();
        try{StreamingRoute.main(args);}finally{
            running.set(false);sampler.interrupt();sampler.join(1000);
            var lines=new ArrayList<String>();lines.add("phase,operation,owner_kernel_wait,samples");
            for(var entry:counts.entrySet())lines.add(entry.getKey().replace('/',',')+","+entry.getValue());
            Files.write(Path.of(args[0],"native-wait-samples.csv"),lines);
        }
    }
}
