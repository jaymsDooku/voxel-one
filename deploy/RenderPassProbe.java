package dev.jayms.render;
import java.nio.file.*;
import java.lang.management.*;
import java.util.*;
import static org.lwjgl.opengl.GL33.*;
/** Diagnostic overlay only. Frame-correlated queries; slow-draw replay clears depth and changes output. Never use for visual or performance qualification. */
public final class RenderPassProbe implements AutoCloseable {
 static final ThreadMXBean CPU=ManagementFactory.getThreadMXBean();
 static String armed;static boolean described;static final List<String> descriptors=new ArrayList<>();
 public static void arm(String tag){armed=tag;described=false;}
 public static void descriptor(){
  if(armed==null||described)return;described=true;StringBuilder d=new StringBuilder();int program=glGetInteger(GL_CURRENT_PROGRAM);d.append("program="+program);
  for(int flag:new int[]{GL_BLEND,GL_DEPTH_TEST,GL_CULL_FACE,GL_SCISSOR_TEST,GL_FRAMEBUFFER_SRGB,GL_POLYGON_OFFSET_FILL,GL_RASTERIZER_DISCARD,GL_CLIP_DISTANCE0})d.append(";enable"+flag+"="+glIsEnabled(flag));
  for(int flag:new int[]{GL_DEPTH_FUNC,GL_DEPTH_WRITEMASK,GL_CULL_FACE_MODE,GL_FRONT_FACE,GL_BLEND_SRC_RGB,GL_BLEND_DST_RGB,GL_BLEND_SRC_ALPHA,GL_BLEND_DST_ALPHA,GL_SAMPLES})d.append(";state"+flag+"="+glGetInteger(flag));
  int[] box=new int[4];glGetIntegerv(GL_VIEWPORT,box);d.append(";viewport="+Arrays.toString(box).replace(',',':'));glGetIntegerv(GL_COLOR_WRITEMASK,box);d.append(";colorMask="+Arrays.toString(box).replace(',',':'));
  for(String name:new String[]{"uHasIrradiance","uRootBuffer","uFineOffset","uTransportReady","uClusterReady","uProbeReady","uVertexColor","uInstanced","uHeld","uFog","uLightingEnabled","uVoxelAO","uVoxelGI","uContactTransport","uLocalReflection","uShadowEnabled","uShadowCascades","uAtmosphereReflection","uPlanetLighting","uOutputTone","uClipEnabled"}){int location=glGetUniformLocation(program,name);if(location>=0)d.append(";"+name+"="+glGetUniformi(program,location));}
  for(int a=0;a<5;a++)for(int q:new int[]{GL_VERTEX_ATTRIB_ARRAY_ENABLED,GL_VERTEX_ATTRIB_ARRAY_SIZE,GL_VERTEX_ATTRIB_ARRAY_TYPE,GL_VERTEX_ATTRIB_ARRAY_STRIDE,GL_VERTEX_ATTRIB_ARRAY_NORMALIZED,GL_VERTEX_ATTRIB_ARRAY_INTEGER,GL_VERTEX_ATTRIB_ARRAY_DIVISOR})d.append(";a"+a+"p"+q+"="+glGetVertexAttribi(a,q));
  int old=glGetInteger(GL_ACTIVE_TEXTURE);int[] targets={GL_TEXTURE_CUBE_MAP,GL_TEXTURE_2D_ARRAY,GL_TEXTURE_2D_ARRAY,GL_TEXTURE_3D,GL_TEXTURE_3D,GL_TEXTURE_BUFFER,GL_TEXTURE_3D,GL_TEXTURE_3D,GL_TEXTURE_3D,GL_TEXTURE_3D,GL_TEXTURE_3D,GL_TEXTURE_3D,GL_TEXTURE_BUFFER,GL_TEXTURE_BUFFER,GL_TEXTURE_CUBE_MAP,GL_TEXTURE_2D};
  for(int unit=0;unit<16;unit++){glActiveTexture(GL_TEXTURE0+unit);int target=targets[unit],levelTarget=target==GL_TEXTURE_CUBE_MAP?GL_TEXTURE_CUBE_MAP_POSITIVE_X:target;String key=";t"+unit;
   for(int q:new int[]{GL_TEXTURE_INTERNAL_FORMAT,GL_TEXTURE_WIDTH,GL_TEXTURE_HEIGHT,GL_TEXTURE_DEPTH}){if(target==GL_TEXTURE_BUFFER&&q!=GL_TEXTURE_INTERNAL_FORMAT&&q!=GL_TEXTURE_WIDTH)continue;d.append(key+"l"+q+"="+glGetTexLevelParameteri(levelTarget,0,q));}
   if(target!=GL_TEXTURE_BUFFER)for(int q:new int[]{GL_TEXTURE_MIN_FILTER,GL_TEXTURE_MAG_FILTER,GL_TEXTURE_WRAP_S,GL_TEXTURE_WRAP_T,GL_TEXTURE_WRAP_R,GL_TEXTURE_BASE_LEVEL,GL_TEXTURE_MAX_LEVEL,GL_TEXTURE_COMPARE_MODE,GL_TEXTURE_COMPARE_FUNC})d.append(key+"p"+q+"="+glGetTexParameteri(target,q));
  }glActiveTexture(old);descriptors.add(sample+","+armed+",\""+d+"\"\n");
 }
 static final List<String> states=new ArrayList<>();
 public static void lighting(int mode,int sky){int unit=glGetInteger(GL_ACTIVE_TEXTURE);StringBuilder row=new StringBuilder(sample+","+mode+","+sky);for(int u:new int[]{3,4,5}){glActiveTexture(GL_TEXTURE0+u);int target=u==5?GL_TEXTURE_BUFFER:GL_TEXTURE_3D;row.append(","+glGetInteger(u==5?GL_TEXTURE_BINDING_BUFFER:GL_TEXTURE_BINDING_3D));row.append(","+glGetTexLevelParameteri(target,0,GL_TEXTURE_INTERNAL_FORMAT));row.append(","+glGetTexLevelParameteri(target,0,GL_TEXTURE_WIDTH));if(u!=5)row.append(","+glGetTexLevelParameteri(target,0,GL_TEXTURE_HEIGHT)+","+glGetTexLevelParameteri(target,0,GL_TEXTURE_DEPTH));}glActiveTexture(GL_TEXTURE14);row.append(","+glGetInteger(GL_TEXTURE_BINDING_CUBE_MAP)+","+glGetTexLevelParameteri(GL_TEXTURE_CUBE_MAP_POSITIVE_X,0,GL_TEXTURE_WIDTH)+","+glGetTexLevelParameteri(GL_TEXTURE_CUBE_MAP_POSITIVE_X,1,GL_TEXTURE_WIDTH));glActiveTexture(unit);states.add(row.toString());}
 static final List<RenderPassProbe> rows=new ArrayList<>();
 public static volatile int sample=-1;
 static volatile Thread owner;static volatile boolean rendering;static final Map<String,Integer> calls=new java.util.concurrent.ConcurrentHashMap<>();
 static{Thread monitor=new Thread(()->{for(;;){try{Thread.sleep(10);}catch(InterruptedException done){return;}Thread t=owner;if(t!=null&&rendering){String stack=java.util.Arrays.stream(t.getStackTrace()).filter(e->e.getClassName().startsWith("dev.jayms.")||e.getClassName().startsWith("org.lwjgl.")).limit(6).map(Object::toString).collect(java.util.stream.Collectors.joining(";"));calls.merge(sample+",\""+stack+"\"",1,Integer::sum);}}},"render-callsite-probe");monitor.setDaemon(true);monitor.start();}
 int frame=sample;final String name;final long start=System.nanoTime(),cpu=CPU.getCurrentThreadCpuTime();
 final int a=glGenQueries(),b=glGenQueries();long wall,thread;int rootMode,skyColumns;
 public RenderPassProbe(String name){this.name=name;if(name.equals("main.render")){owner=Thread.currentThread();rendering=true;}glQueryCounter(a,GL_TIMESTAMP);}
 public void close(){glQueryCounter(b,GL_TIMESTAMP);thread=CPU.getCurrentThreadCpuTime()-cpu;wall=System.nanoTime()-start;rows.add(this);if(name.equals("reflection.capture"))armed=null;if(name.equals("main.render"))rendering=false;}
 public static void nativeDraw(String label,Runnable work){
  long start=System.nanoTime(),cpu=CPU.getCurrentThreadCpuTime();work.run();long elapsed=CPU.getCurrentThreadCpuTime()-cpu;
  if(armed!=null&&elapsed>100_000_000L){String old=armed;armed=old+".slow."+label+".cpu"+elapsed+".wall"+(System.nanoTime()-start);described=false;descriptor();
   glFinish();glClear(GL_DEPTH_BUFFER_BIT);long replayCpu=CPU.getCurrentThreadCpuTime(),replayWall=System.nanoTime();work.run();glFinish();
   armed=old+".replay."+label+".cpu"+(CPU.getCurrentThreadCpuTime()-replayCpu)+".wall"+(System.nanoTime()-replayWall);described=false;descriptor();armed=old;}
 }
 public static void draw(String name,Runnable work){try(var span=new RenderPassProbe(name)){work.run();}}
 public static void flush(Path file,int frame)throws Exception {
  boolean header=!Files.exists(file);var out=new StringBuilder(header?"sample,operation,wall_ns,thread_cpu_ns,gpu_ns\n":"");
  // Never drain unfinished queries. Preserve their originating frame IDs.
  for(var iterator=rows.iterator();iterator.hasNext();){var r=iterator.next();if(glGetQueryObjecti(r.b,GL_QUERY_RESULT_AVAILABLE)==0)continue;long begin=glGetQueryObjectui64(r.a,GL_QUERY_RESULT),end=glGetQueryObjectui64(r.b,GL_QUERY_RESULT);out.append(r.frame+","+r.name+","+r.wall+","+r.thread+","+(end-begin)+"\n");glDeleteQueries(r.a);glDeleteQueries(r.b);iterator.remove();}Files.writeString(file,out,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
  Path descriptorFile=file.resolveSibling("render-descriptors.csv");Files.writeString(descriptorFile,(Files.exists(descriptorFile)?"":"sample,scope,descriptor\n")+String.join("",descriptors),StandardOpenOption.CREATE,StandardOpenOption.APPEND);descriptors.clear();
  Path stateFile=file.resolveSibling("render-lighting-state.csv");var stateText=new StringBuilder(Files.exists(stateFile)?"":"sample,rootMode,skyColumns,irradianceId,irradianceFormat,irradianceWidth,irradianceHeight,irradianceDepth,rootsId,rootsFormat,rootsWidth,rootsHeight,rootsDepth,fineId,fineFormat,fineWidth,probeId,probeLevel0Width,probeLevel1Width\n");for(String row:states)stateText.append(row+"\n");states.clear();Files.writeString(stateFile,stateText,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
  Path sites=file.resolveSibling("render-callsites.csv");var text=new StringBuilder(Files.exists(sites)?"":"sample,stack,samples\n");for(var e:calls.entrySet()){text.append(e.getKey()+","+e.getValue()+"\n");calls.remove(e.getKey(),e.getValue());}Files.writeString(sites,text,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
 }
}
