import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.render.*;
import org.lwjgl.opengl.GL;
import java.nio.file.*;
import java.util.*;
import java.lang.reflect.*;
import java.lang.management.ManagementFactory;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Matched standalone regression; excludes fixture setup and recording from pump timings. */
public class LocalLightingSmoke {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static final java.lang.management.ThreadMXBean cpu=ManagementFactory.getThreadMXBean();
 static final java.util.List<java.lang.management.GarbageCollectorMXBean> collectors=ManagementFactory.getGarbageCollectorMXBeans();
 static final ArrayList<double[]> details=new ArrayList<>();
 static long gc(boolean time){long n=0;for(var c:collectors)n+=Math.max(0,time?c.getCollectionTime():c.getCollectionCount());return n;}
 static Method update;static boolean sliced;static long window;
 static boolean pump(ClusteredLights c,World w,float x)throws Exception {
  return (boolean)(sliced?update.invoke(c,w,x,8f,System.nanoTime()+250_000):update.invoke(c,w,x,8f));
 }
 static int drain(ClusteredLights c,World w,float x,List<Double> times)throws Exception {
  for(int n=1;n<=200;n++) {long g=gc(false),gt=gc(true),ct=cpu.getCurrentThreadCpuTime(),start=System.nanoTime();boolean done=pump(c,w,x);double ms=(System.nanoTime()-start)/1e6;if(times!=null){times.add(ms);details.add(new double[]{(cpu.getCurrentThreadCpuTime()-ct)/1e6,gc(false)-g,gc(true)-gt});}glfwSwapBuffers(window);glfwPollEvents();if(done)return n;}
  throw new AssertionError("Cluster update did not catch up in200pumps");
 }
 static int texture(ClusteredLights c)throws Exception {
  try{var f=c.getClass().getDeclaredField("cluster");f.setAccessible(true);return f.getInt(c);}catch(NoSuchFieldException staged){
   var f=c.getClass().getDeclaredField("stores");f.setAccessible(true);Object[] a=(Object[])f.get(c);f=c.getClass().getDeclaredField("active");f.setAccessible(true);Object s=a[f.getInt(c)];f=s.getClass().getDeclaredField("cluster");f.setAccessible(true);return f.getInt(s);
  }
 }
 static double percentile(List<Double> v,double q){var s=new ArrayList<>(v);Collections.sort(s);return s.get(Math.max(0,(int)Math.ceil(q*s.size())-1));}
 public static void main(String[] args)throws Exception {
  var out=Path.of(args[0]);Files.createDirectories(out);
  check(glfwInit(),"GLFW");glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
  window=glfwCreateWindow(320,240,"Local edited-world lighting regression",0,0);check(window!=0,"GL3.3 context");glfwMakeContextCurrent(window);GL.createCapabilities();glfwSwapInterval(0);
  try {
   try{update=ClusteredLights.class.getMethod("update",World.class,float.class,float.class,long.class);sliced=true;}catch(NoSuchMethodException old){update=ClusteredLights.class.getMethod("update",World.class,float.class,float.class);}
   try(var w=new World(748291);var c=new ClusteredLights()) {
    for(int i=0;i<100_000;i++)w.apply(new Protocol.Edit(100_000+i%1000,1000,100_000+i/1000,Blocks.STONE));
    w.apply(new Protocol.Edit(1,1,1,Blocks.LED).withColor(0xff3040));
    for(int i=0;i<20;i++)drain(c,w,i%2*16,null);
    var pumps=new ArrayList<Double>();var totals=new ArrayList<Double>();var counts=new ArrayList<Integer>();var csv=new StringBuilder("boundary,pump,pump_ms,owner_thread_cpu_ms,gc_count,gc_ms\n");
    for(int i=0;i<100;i++) {var times=new ArrayList<Double>();int n=drain(c,w,i%2*16,times);double sum=0;for(int j=0;j<times.size();j++){double ms=times.get(j);pumps.add(ms);sum+=ms;var d=details.get(details.size()-times.size()+j);csv.append(i+","+j+","+ms+","+d[0]+","+d[1]+","+d[2]+"\n");}totals.add(sum);counts.add(n);}
    check(c.emitters().size()==1,"Nearby LED retained despite100000distant non-light edits");
    int[] grid=new int[12*16*12*2];glBindTexture(GL_TEXTURE_3D,texture(c));glGetTexImage(GL_TEXTURE_3D,0,GL_RG_INTEGER,GL_INT,grid);int count=0;for(int i=1;i<grid.length;i+=2)count+=grid[i];check(count>0,"Installed GPU cluster grid has light references");
    if(sliced){
     check(!(boolean)update.invoke(c,w,32f,8f,0L),"Expired deadline does not start preparation");
     pump(c,w,32);w.apply(new Protocol.Edit(1,1,1,Blocks.LED).withColor(0x1234ff));drain(c,w,32,null);
     check(c.emitters().size()==1&&c.emitters().getFirst().color().z==1,"Local edit race replaces stale captured color");
     w.apply(new Protocol.Edit(200_000,1000,200_000,Blocks.STONE));check(!pump(c,w,32),"Distant edit does not invalidate accepted local clusters");
     w.apply(new Protocol.Edit(1,1,1,0));drain(c,w,32,null);check(c.emitters().isEmpty(),"Removed LED leaves no phantom emitter");
     glBindTexture(GL_TEXTURE_3D,texture(c));glGetTexImage(GL_TEXTURE_3D,0,GL_RG_INTEGER,GL_INT,grid);for(int i=1;i<grid.length;i+=2)check(grid[i]==0,"GPU grid removed old light references");
     try(var other=new World(999)){other.apply(new Protocol.Edit(1,1,1,Blocks.LED).withColor(0x00ff00));drain(c,other,0,null);check(c.emitters().size()==1&&c.emitters().getFirst().color().y==1,"World switch fences old clusters");}
    }
    check(glGetError()==GL_NO_ERROR,"No GL error");int id=texture(c);c.close();c.close();check(!glIsTexture(id),"Cluster GPU resources released");
    Files.writeString(out.resolve("pumps.csv"),csv);Files.writeString(out.resolve("result.json"),"{\"status\":\"passed\",\"distantEdits\":100000,\"boundaries\":100,\"warmupBoundaries\":20,\"sliced\":"+sliced+",\"pumpP50Ms\":"+percentile(pumps,.5)+",\"pumpP95Ms\":"+percentile(pumps,.95)+",\"pumpP99Ms\":"+percentile(pumps,.99)+",\"worstPumpMs\":"+Collections.max(pumps)+",\"boundaryCpuP50Ms\":"+percentile(totals,.5)+",\"boundaryCpuP99Ms\":"+percentile(totals,.99)+",\"maxPumpsPerBoundary\":"+Collections.max(counts)+"}\n");
   }
  }finally{glfwDestroyWindow(window);glfwTerminate();}
 }
}
