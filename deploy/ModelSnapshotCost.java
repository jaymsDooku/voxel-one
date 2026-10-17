import dev.jayms.net.model.*;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
public class ModelSnapshotCost {
 public static void main(String[] args)throws Exception{
  var library=new ModelLibrary();var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long id=Thread.currentThread().getId();
  for(int i=0;i<100;i++)library.copy();long[] times=new long[100];long before=bean.getThreadAllocatedBytes(id);
  for(int i=0;i<times.length;i++){long start=System.nanoTime();var copy=library.copy();times[i]=System.nanoTime()-start;if(copy.revision()!=library.revision())throw new AssertionError("Snapshot revision");}
  long bytes=bean.getThreadAllocatedBytes(id)-before;Arrays.sort(times);
  System.out.println("Model snapshot: p50_ms="+times[49]/1e6+", p99_ms="+times[98]/1e6+", allocated_bytes_per_copy="+bytes/100);
 }
}
