import dev.jayms.net.Terrain;
import java.nio.file.*;
/** Matched hot public terrain access: exact output digest, allocation counters and CPU/wall cost. No GL. */
public class TerrainAllocationProbe {
 static long run(Terrain terrain,int n){long digest=1;for(int i=0;i<n;i++){int column=i&1023;digest=digest*31+terrain.block(128+(column&31),(i/1024)%128-32,8+(column>>>5));}return digest;}
 public static void main(String[] args)throws Exception{
  var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
  if(!bean.isThreadAllocatedMemorySupported())throw new AssertionError("Allocation counters unavailable");
  if(!bean.isThreadAllocatedMemoryEnabled())bean.setThreadAllocatedMemoryEnabled(true);
  var terrain=new Terrain(748291,3);run(terrain,262144);int count=1048576;long thread=Thread.currentThread().threadId();
  long allocated=bean.getThreadAllocatedBytes(thread),cpu=bean.getCurrentThreadCpuTime(),start=System.nanoTime();long digest=run(terrain,count);long elapsed=System.nanoTime()-start,usedCpu=bean.getCurrentThreadCpuTime()-cpu,used=bean.getThreadAllocatedBytes(thread)-allocated;
  Files.writeString(Path.of(args[0]),"{\"iterations\":"+count+",\"digest\":\""+digest+"\",\"allocatedBytes\":"+used+",\"threadCpuNs\":"+usedCpu+",\"wallNs\":"+elapsed+",\"note\":\"Public Terrain.block fixed-seed warm access; standalone allocation regression, not game FPS\"}\n");
 }
}
