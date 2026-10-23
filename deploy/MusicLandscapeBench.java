import dev.jayms.music.*;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
/** Matched real renderer benchmark. Uses the same synthetic fixture and isolated profile. */
public class MusicLandscapeBench {
 public static void main(String[] args)throws Exception {
  Path out=Path.of(args[0]);double seconds=Double.parseDouble(args[1]);
  var times=new ArrayList<Double>();
  long[] resident={0},heap={0};String[] rss={""};long[] start={0},last={0};
  boolean[] shot={false};var app=new MusicVisualizer();app.run(a->{
   glFinish();long now=System.nanoTime();if(start[0]==0){start[0]=now;a.requestLoad(Path.of(args[2]));glfwSwapInterval(0);}
   double elapsed=(now-start[0])/1e9;
   if(elapsed>5&&last[0]!=0)times.add((now-last[0])/1e6);last[0]=now;
   if(elapsed>3&&!shot[0]){
    shot[0]=true;var pixels=java.nio.ByteBuffer.allocateDirect(a.width*a.height*3);glReadPixels(0,0,a.width,a.height,GL_RGB,GL_UNSIGNED_BYTE,pixels);
    var image=new java.awt.image.BufferedImage(a.width,a.height,java.awt.image.BufferedImage.TYPE_INT_RGB);
    for(int y=0;y<a.height;y++)for(int x=0;x<a.width;x++){int i=(y*a.width+x)*3;image.setRGB(x,a.height-1-y,(pixels.get(i)&255)<<16|(pixels.get(i+1)&255)<<8|(pixels.get(i+2)&255));}
    javax.imageio.ImageIO.write(image,"png",Path.of(args[0]+".png").toFile());
   }
   if(a.track.ended()){a.seek(0);a.track.toggle();}
   if(elapsed>seconds){
    resident[0]=dev.jayms.Mesh.estimatedResidentBytes();heap[0]=java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    rss[0]=Files.readAllLines(Path.of("/proc/self/status")).stream().filter(v->v.startsWith("VmRSS:")).findFirst().orElse("unavailable").replace("\t"," ");
    glfwSetWindowShouldClose(a.window,true);
   }
  });
  Collections.sort(times);String report="{\"renderer\":\""+app.renderer+"\",\"resolution\":\"1100x720\",\"warmupSeconds\":5,\"seconds\":"+seconds+",\"frames\":"+times.size()+",\"medianMs\":"+times.get(times.size()/2)+",\"p95Ms\":"+times.get((int)(times.size()*.95))+",\"trackedMeshBytes\":"+resident[0]+",\"heapUsedBytes\":"+heap[0]+",\"rss\":\""+rss[0]+"\"}";
  Files.writeString(out,report+"\n");System.out.println(report);System.exit(0);
 }
}
