import dev.jayms.*;
import dev.jayms.net.city.GameConfig;
import dev.jayms.player.Player;
import dev.jayms.window.Window;
import dev.jayms.render.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.concurrent.locks.LockSupport;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import org.joml.FrustumIntersection;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Matched resident/cold/transition route. Fresh travel is wholly outside the spawn preload. */
public class StreamingRoute {
    static Method traceFlush;static{try{traceFlush=Class.forName("dev.jayms.OwnerTrace").getMethod("flush",Path.class,int.class);}catch(ClassNotFoundException old){}catch(ReflectiveOperationException error){throw new ExceptionInInitializerError(error);}}
    static Main game=new Main();static int frame,first=-1;static Path out;static PerformanceRecorder recorder;
    static final int TOTAL=Integer.getInteger("voxel.route.samples",166);static boolean visual;
    static final StringBuilder coverage=new StringBuilder("frame,sample,x,has_gi,gi_x,gi_z,horizontal_coverage_9,active_age_frames,sky_known,pending_bake,capture_index,upload_phase,upload_bytes,sky_pending,source_age_ms,current_geometry,submitted_bakes,completed_bakes,rejected_bakes,pending_age_ms,initial_budget_updates,initial_budget_active,bake_done,worker_id,worker_wall_nanos,worker_cpu_nanos,stream_cpu_ms,mesh_cpu_ms,sky_cache_bytes,sky_cached_columns,sky_publications,sky_partial_publications,sky_rejected,local_sky_ready,sky_upload_preemptions,gi_ceiling_ready,obsolete_volumes,sky_gpu_pending,sky_gpu_age_ms,sky_gpu_bytes,sky_source_age_ms,sky_build_cpu_ms,scene_cpu_ms,gi_prepare_cpu_ms,lighting_upload_cpu_ms,sky_upload_owner_ms,sky_capture_chunk,sky_captured_tiles,sky_packing_columns,sky_cpu_building,sky_cpu_merging,owner_budget_ms,gpu_frame_ema_ms,reused_fields,wall_ms,sky_coverage_9\n");
    static Object activeVolume;static int activeSince;static long routeStarted;static volatile boolean firstObserved;
    static int terrainUiFrames,lightingUiFrames;static double lastInitialLightingMs=-1;static final java.util.Map<String,Integer> waits=new java.util.TreeMap<>();
    static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static double stageMillis(Object renderer,String name)throws Exception{try{return ((long)field(renderer,name))/1e6;}catch(NoSuchFieldException old){return -1;}}
    static long counter(Object renderer,String name)throws Exception{try{return (long)field(renderer,name);}catch(NoSuchFieldException old){return -1;}}
    static double gpuMillis(Object renderer)throws Exception{try{return ((Number)renderer.getClass().getMethod("gpuMillis").invoke(renderer)).doubleValue();}catch(NoSuchMethodException old){return -1;}}
    static void measureCoverage(int sample)throws Exception{
        var renderer=(RenderPipeline)get("rendering");var v=(LightVolume)field(renderer,"volume");
        if(v!=activeVolume){activeVolume=v;activeSince=frame;}
        float px=sample<0?8:x(Math.min(sample,TOTAL-1));int covered=0;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(v!=null&&px+dx*24>=v.x&&px+dx*24<v.x+v.width&&8+dz*24>=v.z&&8+dz*24<v.z+v.length)covered++;
        var sky=(WorldSkyVisibility)field(renderer,"worldSky");int skyCovered=0;if(sky!=null)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(sky.sampleKnown(px+dx*24,110,8+dz*24,-1)>=0)skyCovered++;var lighting=field(renderer,"lighting");int ci=-1,phase=-1;
        try{var c=field(lighting,"capture");if(c!=null)ci=(int)field(c,"index");}catch(NoSuchFieldException old){}
        try{var u=field(renderer,"lightingUpload");if(u!=null)phase=(int)field(u,"phase");}catch(NoSuchFieldException old){}
        int sp=-1;try{sp=(int)renderer.getClass().getMethod("skyPending").invoke(renderer);}catch(NoSuchMethodException old){}
        long bytes=0;try{bytes=(long)field(renderer,"lightingUploadBytes");}catch(NoSuchFieldException old){}
        String extra="-1,false,-1,-1,-1,-1,-1,false,false,0,0,0";
        try{var status=renderer.getClass().getMethod("lightingCoverage",World.class,float.class,float.class).invoke(renderer,get("world"),px,8f);
            String[] names={"sourceAgeMillis","current","submitted","completed","rejected","pendingAgeMillis","initialBudgetUpdates","initialBudgetActive","bakeDone","workerId","workerNanos","workerCpuNanos"};var values=new java.util.ArrayList<String>();for(String name:names)values.add(String.valueOf(status.getClass().getMethod(name).invoke(status)));extra=String.join(",",values);if(sample>=0&&(Boolean.parseBoolean(values.get(7))||Integer.parseInt(values.get(6))>64))throw new AssertionError("Initial allowance leaked into profiled streaming");
        }catch(NoSuchMethodException old){}
        long skyBytes=-1;try{skyBytes=(long)renderer.getClass().getMethod("skyCacheBytes").invoke(renderer);}catch(NoSuchMethodException old){}
        String skyExtra="-1,-1,-1,-1,false,-1,false,0,false,-1,0,-1";
        try{var progress=renderer.getClass().getMethod("skyProgress",World.class,float.class,float.class).invoke(renderer,get("world"),px,8f);var values=new java.util.ArrayList<String>();for(String name:new String[]{"cached","published","partial","rejected","localReady","preemptions","ceilingReady","obsoleteVolumes","gpuPending","gpuAgeMillis","gpuBytes","sourceAgeMillis"})values.add(String.valueOf(progress.getClass().getMethod(name).invoke(progress)));skyExtra=String.join(",",values);}catch(NoSuchMethodException old){}
        long skyNanos=-1;try{skyNanos=(long)field(renderer,"skyBuildNanos");}catch(NoSuchFieldException old){}
        long meshNanos=-1;try{meshNanos=(long)field(get("detailedMeshes"),"totalNanos");}catch(NoSuchFieldException old){}
        String skyWork="-1,-1,-1,false,false";
        try{var scheduler=field(renderer,"skyVisibility");var cap=field(scheduler,"capture");var parts=field(scheduler,"packingParts");skyWork=(cap==null?-1:(int)field(cap,"chunk"))+","+((java.util.List<?>)field(scheduler,"tickets")).size()+","+(parts==null?-1:((java.util.List<?>)parts).size())+","+(field(scheduler,"pending")!=null)+","+(field(scheduler,"packed")!=null);}catch(NoSuchFieldException old){}
        coverage.append(frame+","+sample+","+px+","+(v!=null)+","+(v==null?0:v.x)+","+(v==null?0:v.z)+","+covered+","+(v==null?-1:frame-activeSince)+","+(sky!=null&&sky.sampleKnown(px,110,8,-1)>=0)+","+(field(lighting,"pending")!=null)+","+ci+","+phase+","+bytes+","+sp+","+extra+","+((long)get("streamNanos"))/1e6+","+meshNanos/1e6+","+skyBytes+","+skyExtra+","+skyNanos/1e6+","+stageMillis(renderer,"sceneNanos")+","+stageMillis(renderer,"lightingNanos")+","+stageMillis(renderer,"lightingUploadNanos")+","+stageMillis(renderer,"skyGpuNanos")+","+skyWork+","+stageMillis(renderer,"ownerBudgetNanos")+","+gpuMillis(renderer)+","+counter(renderer,"reusedCurrentVolumes")+","+(System.nanoTime()-routeStarted)/1e6+","+skyCovered+"\n");
        Files.writeString(out.resolve("coverage.csv"),coverage);
    }
    static void capture(int sample)throws Exception{
        int w=(int)get("framebufferWidth"),h=(int)get("framebufferHeight");var pixels=java.nio.ByteBuffer.allocateDirect(w*h*3);glReadPixels(0,0,w,h,GL_RGB,GL_UNSIGNED_BYTE,pixels);
        var image=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=(x+y*w)*3;image.setRGB(x,h-y-1,(pixels.get(i)&255)<<16|(pixels.get(i+1)&255)<<8|(pixels.get(i+2)&255));}
        javax.imageio.ImageIO.write(image,"png",out.resolve("visual-"+sample+".png").toFile());
    }
    static final StringBuilder residency=new StringBuilder("sample,segment,x,z,center_resident,nearby_resident,pending,loaded\n");
    static Object get(String name)throws Exception{var f=Main.class.getDeclaredField(name);f.setAccessible(true);return f.get(game);}
    static void set(String name,Object value)throws Exception{var f=Main.class.getDeclaredField(name);f.setAccessible(true);f.set(game,value);}
    static boolean resident(World w,float x,float z,int radius){int cx=Math.floorDiv((int)Math.floor(x),16),cz=Math.floorDiv((int)Math.floor(z),16);for(int a=-radius;a<=radius;a++)for(int b=-radius;b<=radius;b++)for(int y=-2;y<=7;y++)if(!w.getLoadedChunks().containsKey(new ChunkPos(cx+a,y,cz+b)))return false;return true;}
    static boolean waiting(String reason)throws Exception{
        waits.merge(reason,1,Integer::sum);var text=new StringBuilder("reason,frames\n");for(var entry:waits.entrySet())text.append(entry.getKey()+","+entry.getValue()+"\n");Files.writeString(out.resolve("readiness-waits.csv"),text);return false;
    }
    static boolean graphicsReady()throws Exception{
        Object renderer=get("rendering");var f=RenderPipeline.class.getDeclaredField("hasIrradiance");f.setAccessible(true);if(!f.getBoolean(renderer))return waiting("first GI");
        try{f=RenderPipeline.class.getDeclaredField("lightingUpload");f.setAccessible(true);if(f.get(renderer)!=null)return waiting("GI GPU upload");}catch(NoSuchFieldException baseline){}
        try{if((int)RenderPipeline.class.getMethod("skyPending").invoke(renderer)!=0)return waiting("sky CPU work");}catch(NoSuchMethodException baseline){}
        try{var requested=RenderPipeline.class.getDeclaredField("desiredVolume");requested.setAccessible(true);var active=RenderPipeline.class.getDeclaredField("volume");active.setAccessible(true);if(requested.get(renderer)!=active.get(renderer))return waiting("GI replacement");}catch(NoSuchFieldException baseline){}
        try{var requested=RenderPipeline.class.getDeclaredField("desiredSky");requested.setAccessible(true);var active=RenderPipeline.class.getDeclaredField("worldSky");active.setAccessible(true);var prepared=requested.get(renderer);if(prepared!=null&&prepared.getClass().getField("data").get(prepared)!=active.get(renderer))return waiting("sky replacement");}catch(NoSuchFieldException baseline){}

        try{var progress=renderer.getClass().getMethod("skyProgress",World.class,float.class,float.class).invoke(renderer,get("world"),8f,8f);if((boolean)progress.getClass().getMethod("gpuPending").invoke(progress))return waiting("sky GPU upload");}catch(NoSuchMethodException old){}
        var lightingField=RenderPipeline.class.getDeclaredField("lighting");lightingField.setAccessible(true);var lighting=lightingField.get(renderer);var pendingField=WorldLighting.class.getDeclaredField("pending");pendingField.setAccessible(true);if(pendingField.get(lighting)!=null)return waiting("GI worker");
        try{if(field(lighting,"capture")!=null)return waiting("GI capture");}catch(NoSuchFieldException old){}
        try{Object expected;try{expected=renderer.getClass().getMethod("lightingSkyKey",World.class,float.class,float.class).invoke(renderer,get("world"),8f,8f);}catch(NoSuchMethodException old){var prepared=field(renderer,"desiredSky");expected=prepared==null?null:prepared.getClass().getMethod("localKey",float.class,float.class).invoke(prepared,8f,8f);}if(!java.util.Objects.equals(field(lighting,"skyKey"),expected))return waiting("GI ceiling dependency");}catch(NoSuchFieldException old){}
        return true;
    }
    static String segment(int sample){if(sample<12)return "resident";if(sample<16)return "coldEntry";if(sample<32)return "coldWalk";if(sample<112)return "coldFlight";if(sample<128)return "warmReversal";if(sample<134)return "teleport";if(sample<158)return "catchup";return "transitionTail";}
    static float x(int sample){if(sample<12)return 8;if(sample<16)return 128;if(sample<32)return 128+(sample-16)*.5f;if(sample<112)return 136+(sample-32)*1.25f;if(sample<128)return 236-(sample-112)*1.25f;return 2048;}
    public static void main(String[] args)throws Exception{
        out=Path.of(args[0]);Files.createDirectories(out);visual=args.length>2&&args[2].equals("visual");long startup=System.nanoTime();routeStarted=startup;
        GraphicsProfile.preset(GraphicsProfile.Preset.BALANCED).with(GraphicsProfile.Key.RESOLUTION,"640x480@60").save(dev.jayms.ui.Controls.directory().resolve("graphics.properties"));
        set("offlineSave",out.resolve("synthetic.dat"));set("seed",748291L);set("gameConfig",new GameConfig(false,false,1200,10));
        game.run(new Main.FrameObserver(){
            public void loading(Main main,String stage){if(stage.equals("terrain"))terrainUiFrames++;else if(stage.equals("lighting")){lightingUiFrames++;lastInitialLightingMs=(System.nanoTime()-startup)/1e6;}}
            public void started(Main main)throws Exception{
                Files.writeString(out.resolve("initial-lighting.json"),"{\"terrainUiFrames\":"+terrainUiFrames+",\"lightingUiFrames\":"+lightingUiFrames+",\"lastInitialLightingMs\":"+lastInitialLightingMs+"}\n");
                glfwSwapInterval(0);Files.writeString(out.resolve("route-progress.txt"),"preload\n");recorder=(PerformanceRecorder)get("performance");World world=(World)get("world");
                Method async=null;try{async=World.class.getMethod("streamAsync",float.class,float.class,float.class,long.class);}catch(NoSuchMethodException baseline){}
                if(async!=null)async.invoke(world,8f,110f,8f,2_000_000L);
                while(!resident(world,8,8,4)){if(async==null)world.stream(8,8,9);else async.invoke(world,8f,110f,8f,2_000_000L);glfwSwapBuffers(((Window)get("window")).getHandle());glfwPollEvents();LockSupport.parkNanos(1_000_000L);}
                for(var position:new java.util.ArrayList<>(world.getLoadedChunks().keySet()))if(Math.abs(position.chunkX())>4||Math.abs(position.chunkZ())>4)world.unloadChunk(position);
                if(world.getLoadedChunks().size()!=810)throw new AssertionError("Fixture must preload exactly the same810 target chunks");
                // Finish identical resident meshes before graphics warmup. No recorder is active.
                var scheduler=(DetailedMeshScheduler)get("detailedMeshes");var visible=new FrustumIntersection(new Matrix4f().ortho(-10000,10000,-10000,10000,-10000,10000));long limit=System.nanoTime()+120_000_000_000L;
                while(world.getLoadedChunks().values().stream().anyMatch(Chunk::dirty)||scheduler.queued()!=0){
                    if(System.nanoTime()>limit)throw new AssertionError("Resident mesh warmup did not catch up");scheduler.update(world,8,8,visible,112,112,2_000_000,2*1024*1024);glfwPollEvents();LockSupport.parkNanos(1_000_000L);
                }
                Files.writeString(out.resolve("startup.json"),"{\"startupAndResidentPreparationMs\":"+(System.nanoTime()-startup)/1e6+",\"residentChunks\":"+world.getLoadedChunks().size()+"}\n");
                Thread owner=Thread.currentThread();var monitor=new Thread(()->{try{Thread.sleep(30_000);if(!firstObserved){var lines=java.util.Arrays.stream(owner.getStackTrace()).limit(8).map(StackTraceElement::toString).toList();Files.writeString(out.resolve("first-frame-callsite.txt"),String.join("\n",lines)+"\n");}}catch(Exception ignored){}},"first-frame-diagnostic");monitor.setDaemon(true);monitor.start();
            }
            public void beforeFrame(Main main)throws Exception{
                if(Files.exists(Path.of("target/streaming-matrix-stop"))){if(recorder.active())recorder.stop().get();Files.writeString(out.resolve("interrupted.txt"),"Controller stop marker; partial data only\n");glfwSetWindowShouldClose(((Window)get("window")).getHandle(),true);return;}
                ((dev.jayms.ui.ControlsMenu)get("menu")).open=false;
                if(first<0&&frame>=10&&graphicsReady()){
                    if(args.length>2&&args[2].equals("warmup")){Files.writeString(out.resolve("warmup.json"),"{\"graphicsWarmupFrames\":"+frame+",\"warmupWallMs\":"+(System.nanoTime()-startup)/1e6+"}\n");glfwSetWindowShouldClose(((Window)get("window")).getHandle(),true);}
                    first=frame;var graphics=(GraphicsController)get("graphics");recorder.start(out,new PerformanceRecorder.Metadata(System.getProperty("os.name"),System.getProperty("os.arch"),glGetString(GL_RENDERER),glGetString(GL_VENDOR),glGetString(GL_VERSION),System.getProperty("java.version"),args[1],Runtime.getRuntime().availableProcessors(),Runtime.getRuntime().maxMemory(),(int)get("framebufferWidth"),(int)get("framebufferHeight"),graphics.transaction.effective(),PerformanceRecorder.Route.FLIGHT,"810 resident chunks and completed resident meshes; graphics warmup until latest requested irradiance/sky accepted and no pending bake/sky/upload; video inactive"+(visual?"; VISUAL capture run excluded from performance comparison":"")),0);
                    Files.writeString(out.resolve("warmup.json"),"{\"graphicsWarmupFrames\":"+frame+",\"warmupWallMs\":"+(System.nanoTime()-startup)/1e6+"}\n");
                }
                if(first<0&&frame>240)throw new AssertionError("Graphics warmup did not become ready");
                int sample=first<0?-1:frame-first;
                if(sample==TOTAL){recorder.stop().get();Files.writeString(out.resolve("residency.csv"),residency);glfwSetWindowShouldClose(((Window)get("window")).getHandle(),true);}
                ((Player)get("player")).driveSeat(new Vector3f(sample<0?8:x(Math.min(sample,TOTAL-1)),110,8),0);
            }
            public void afterFrame(Main main)throws Exception{
                firstObserved=true;
                int sample=first<0?-1:frame-first;
                measureCoverage(sample);
                if(traceFlush!=null)traceFlush.invoke(null,out.resolve("owner-operations.csv"),sample);
                if(visual&&(sample==11||sample==64||sample==111||sample==127||sample==133||sample==165))capture(sample);
                if(sample>=0&&sample<TOTAL){World world=(World)get("world");float px=x(sample);int pending=-1;try{pending=(int)World.class.getMethod("terrainPending").invoke(world);}catch(NoSuchMethodException baseline){}
                    residency.append(sample+","+segment(sample)+","+px+",8,"+resident(world,px,8,0)+","+resident(world,px,8,1)+","+pending+","+world.getLoadedChunks().size()+"\n");
                    if(sample%10==0)Files.writeString(out.resolve("residency-partial.csv"),residency);
                }
                if(++frame%10==0)Files.writeString(out.resolve("route-progress.txt"),"frame="+frame+"; sample="+sample+"\n");
            }
        });
        boolean warmupOnly=args.length>2&&args[2].equals("warmup");
        if(first<0||(!warmupOnly&&frame-first<TOTAL))throw new AssertionError("Route interrupted; retained partial data is not a completed trial");
        System.out.println("Playtest: resident then sustained fresh terrain x128..236, warm reversal and teleport; "+TOTAL+" profiled frames exported");
    }
}
