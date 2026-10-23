import dev.jayms.music.*;
import dev.jayms.Mesh;
import java.nio.file.*;
import java.lang.management.ManagementFactory;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
/** Wall-clock native soak. Never substitutes a simulated scene test for playback time. */
public class MusicLandscapeSoak {
 public static void main(String[] args)throws Exception {
  Path report=Path.of(args[0]);
  Thread.setDefaultUncaughtExceptionHandler((thread,error)->{try{Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();Files.writeString(Path.of(args[0]+".failure"),cause.getClass().getSimpleName()+": "+cause.getMessage());}catch(Exception ignored){}System.exit(1);});
  double duration=Double.parseDouble(args[1]);
  var app=new MusicVisualizer();long[] start={0};boolean[] requested={false};int[] maximum={0,0,0};
  StringBuilder samples=new StringBuilder();double[] next={60};long[] meshBytes={-1};
  app.run(a->{
   if(!requested[0]){a.requestLoad(Path.of(args[2]));requested[0]=true;}
   if(a.status.startsWith("Cannot"))throw new AssertionError("Soak audio load failed");
   if(start[0]==0){if(a.track.data()==null)return;if(!a.track.playing())throw new AssertionError("Soak did not start playback");start[0]=System.nanoTime();}
   double elapsed=(System.nanoTime()-start[0])/1e9;
   int[] counts={a.landscape.ripples.count(),a.landscape.particles.count(),a.landscape.memory.count()};
   int[] caps={8,256,128};for(int i=0;i<3;i++){if(counts[i]>caps[i])throw new AssertionError("Pool cap");maximum[i]=Math.max(maximum[i],counts[i]);}
   if(Math.abs(a.landscape.displacement(3,4,a.motion))>MusicLandscape.MAX_DISPLACEMENT)throw new AssertionError("Wave bound");
   if(glGetError()!=GL_NO_ERROR)throw new AssertionError("Soak GL error");
   if(elapsed>=next[0]){
    long resident=Mesh.estimatedResidentBytes();if(meshBytes[0]<0)meshBytes[0]=resident;else if(resident!=meshBytes[0])throw new AssertionError("Continuing mesh growth");
    long heap=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    String rss=Files.readAllLines(Path.of("/proc/self/status")).stream().filter(s->s.startsWith("VmRSS:")).findFirst().orElse("VmRSS unavailable").replace("\t"," ");
    if(samples.length()>0)samples.append(",\n");samples.append("{\"seconds\":").append(elapsed).append(",\"heapBytes\":").append(heap).append(",\"meshBytes\":").append(resident).append(",\"rss\":\"").append(rss).append("\"}");
    Files.writeString(Path.of(args[0]+".partial"),"{\"status\":\"running, not a pass\",\"samples\":["+samples+"]}\n");next[0]+=60;
   }
   if(elapsed>=duration)glfwSetWindowShouldClose(a.window,true);
  });
  if(duration<1800)throw new AssertionError("Required soak is 1800 seconds");
  if(app.window!=0||app.track.data()!=null||Mesh.estimatedResidentBytes()!=0)throw new AssertionError("Exit resource release");
  Files.writeString(report,"{\"Playtest\":\"Linux assigned X11; isolated synthetic profile; production OpenAL null clock and landscape; 1100x720, sensitivity 1, motion 1, palette cyan, drift off; real 1800-second 8 kHz mono synthetic playback with 100/1000/3000 Hz, pulses, quiet and silence\",\"seconds\":"+(System.nanoTime()-start[0])/1e9+",\"frames\":"+app.frames+",\"renderer\":\""+app.renderer+"\",\"maxRipples\":"+maximum[0]+",\"maxParticles\":"+maximum[1]+",\"maxMemory\":"+maximum[2]+",\"boundedPoolsAndMesh\":true,\"samples\":["+samples+"]}\n");
  System.exit(0);
 }
}
