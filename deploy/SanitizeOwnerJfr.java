import jdk.jfr.consumer.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
/** Export only owner timing/worker allocation summaries. Raw JFR stays under ignored target. */
public class SanitizeOwnerJfr {
 public static void main(String[] args)throws Exception {
  Set<String> allowed=Set.of("jdk.GCPhasePause","jdk.SafepointBegin","jdk.SafepointEnd","jdk.JavaMonitorEnter","jdk.ThreadPark","jdk.ExecutionSample","jdk.NativeMethodSample","jdk.ObjectAllocationSample");
  try(var recording=new RecordingFile(Path.of(args[0]));var out=Files.newBufferedWriter(Path.of(args[1]))){
   out.write("event,start_epoch_ns,duration_ns,thread,thread_id,safepoint_id,allocation_bytes,site\n");
   while(recording.hasMoreEvents()){
    var e=recording.readEvent();String name=e.getEventType().getName();if(!allowed.contains(name))continue;
    RecordedThread thread=e.hasField("sampledThread")?e.getThread("sampledThread"):e.getThread();String t=thread==null?"jvm":thread.getJavaName();if(t==null)t="jvm";
    if(!Set.of("main","terrain-generation","detailed-mesh","voxel-lighting","jvm").contains(t))t="other-jvm";
    if((name.contains("Sample")||name.equals("jdk.JavaMonitorEnter")||name.equals("jdk.ThreadPark"))&&t.equals("other-jvm"))continue;
    long id=thread==null?0:thread.getJavaThreadId(),sid=e.hasField("safepointId")?e.getLong("safepointId"):0,weight=name.equals("jdk.ObjectAllocationSample")?e.getLong("weight"):0;
    StringBuilder site=new StringBuilder();var stack=e.getStackTrace();if(stack!=null)for(var frame:stack.getFrames()){
     var method=frame.getMethod();String type=method.getType().getName();int lambda=type.indexOf("$$Lambda");if(lambda>=0)type=type.substring(0,lambda)+"$$Lambda";
     if(type.startsWith("dev.jayms.")||type.startsWith("org.lwjgl.")||type.equals("java.util.TimSort")){
      if(site.length()>0)site.append(';');site.append(type).append('.').append(method.getName()).append(':').append(frame.getLineNumber());if(site.length()>700)break;
     }
    }
    var start=e.getStartTime();long epoch=Math.addExact(Math.multiplyExact(start.getEpochSecond(),1000000000L),start.getNano());
    out.write(name+","+epoch+","+e.getDuration().toNanos()+","+t+","+id+","+sid+","+weight+","+site+"\n");
   }
  }
 }
}
