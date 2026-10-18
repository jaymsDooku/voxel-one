package dev.jayms;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class OwnerTraceTest {
 @TempDir Path temp;
 public static class Probe {
  public static void main(String[] args)throws Exception{
   int parent=OwnerTrace.begin("test.parent"),child=OwnerTrace.begin("test.child");
   Thread.sleep(5);OwnerTrace.end(child,256);OwnerTrace.end(parent,512);OwnerTrace.flush(Path.of(args[0]),32);
  }
 }
 @Test void optInProbeExportsConsistentCpuAndOwnThreadAccounting()throws Exception{
  Path output=temp.resolve("owner-trace-test.csv");
  try{
   var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Dvoxel.streaming.trace=true","-Dvoxel.streaming.hosttrace=true","-cp","target/test-classes"+java.io.File.pathSeparator+"target/classes",Probe.class.getName(),output.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();assertEquals(0,process.waitFor());
   var lines=Files.readAllLines(output);assertEquals(3,lines.size());assertTrue(lines.get(0).contains("scheduler_wait_ns"));assertTrue(lines.get(0).contains("cpu_steal_ticks,processor"));
   String[] child=lines.get(1).split(","),parent=lines.get(2).split(",");assertEquals("test.child",child[1]);assertEquals("test.parent",parent[1]);
   assertTrue(Long.parseLong(parent[3])>=Long.parseLong(child[3]));assertTrue(Long.parseLong(child[3])>=1_000_000);assertTrue(Long.parseLong(child[4])>=0);assertEquals("256",child[8]);assertEquals("1",child[9]);assertEquals("0",parent[9]);
   assertTrue(Long.parseLong(child[13])>=-1,"Steal delta is nonnegative or unavailable after migration");
   if(Files.isReadable(Path.of("/proc/thread-self/stat"))&&Files.isReadable(Path.of("/proc/stat")))assertTrue(Long.parseLong(child[14])>=0,"Own executing CPU is available on Linux");
   if(Files.exists(Path.of("/proc/thread-self/schedstat")))assertTrue(Long.parseLong(child[10])>=0,"Linux own-thread scheduler metric available");
  }finally{Files.deleteIfExists(output);}
 }
}
