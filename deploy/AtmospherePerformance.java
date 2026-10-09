import dev.jayms.*;
import dev.jayms.render.*;
import dev.jayms.net.*;
import dev.jayms.net.city.GameConfig;
import dev.jayms.net.atmosphere.AtmosphereConfig;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import java.nio.file.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Recording/readback-free software benchmark. Timestamp queries are read only when available. */
public class AtmospherePerformance {
    static final int W=1920,H=1080;
    static void frame(RenderPipeline r,ShaderProgram s,World w,Vector3f eye){
        Matrix4f p=new Matrix4f().ortho(-10,10,-6,6,.1f,1000),v=new Matrix4f().lookAt(eye,new Vector3f(8,24,8),new Vector3f(0,1,0));
        r.begin(W,H,p,v,eye,true,s);s.setMatrix4("uProjection",p);s.setMatrix4("uView",v);s.setInt("uVertexColor",1);s.setInt("uInstanced",0);s.setInt("uFog",1);
        for(var e:w.getLoadedChunks().entrySet()){var c=e.getKey();s.setMatrix4("uModel",new Matrix4f().translation(c.chunkX()*16,c.chunkY()*16,c.chunkZ()*16));r.chunk(e.getValue(),c);}
        r.finish();glFlush();if(glGetError()!=GL_NO_ERROR)throw new AssertionError("Benchmark GL error");
    }
    public static void main(String[] args)throws Exception {
        if(!glfwInit())throw new AssertionError("GLFW");glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window=glfwCreateWindow(W,H,"Voxel One atmosphere benchmark",0,0);glfwMakeContextCurrent(window);GL.createCapabilities();
        StringBuilder report=new StringBuilder("Performance: recording disabled; no framebuffer/texture readbacks or glFinish. 1920x1080, GL3.3, orthographic synthetic stone patch; one sample, fixed exposure=1; TAA/AO/contact/SSR/screen GI/bloom/clouds disabled. Nonblocking frame elapsed and atmosphere timestamp queries. Driver: "+glGetString(GL_RENDERER)+"\n");
        try(var r=new RenderPipeline();var s=new ShaderProgram("shaders/voxel.vert","shaders/voxel.frag");var w=new World()){
            r.settings.autoExposure=false;r.settings.taa=false;r.settings.ao=false;r.settings.contactShadows=false;r.settings.reflections=false;r.settings.screenGi=false;r.settings.volumetrics=false;r.settings.bloom=false;r.settings.clouds=false;
            var chunk=new Chunk();for(int x=0;x<16;x++)for(int z=0;z<16;z++)chunk.setBlock(x,8,z,Blocks.STONE);w.addChunk(new ChunkPos(0,1,0),chunk);chunk.checkMesh();long deadline=System.nanoTime()+30_000_000_000L;do{r.update(w,8,8);if(r.lightingReady())break;Thread.sleep(10);}while(System.nanoTime()<deadline);if(!r.lightingReady())throw new AssertionError("Accepted lighting");
            Vector3f eye=new Vector3f(8,35,24);r.time(new GameConfig(false,false,1200,12),0);
            for(var q:PlanetAtmosphere.Quality.values()){
                r.settings.atmosphereQuality=q;float baseline=0;
                for(boolean enabled:new boolean[]{false,true}){
                    r.atmosphere(enabled?AtmosphereConfig.earth():AtmosphereConfig.airless());double peakCpu=0;float peakTable=0,peakFrame=0;long before=r.atmosphereGpuSamples();
                    for(int i=0;i<30;i++){frame(r,s,w,eye);peakCpu=Math.max(peakCpu,r.atmosphereRebuildMillis());peakTable=Math.max(peakTable,r.atmosphereTableGpuLast());peakFrame=Math.max(peakFrame,r.atmosphereGpuLast());}
                    if(r.atmosphereRebuilding()||r.atmosphereGpuSamples()-before<10)throw new AssertionError("Completed tables and available timing samples");
                    long start=System.nanoTime();for(int i=0;i<15;i++)frame(r,s,w,eye);double cpu=(System.nanoTime()-start)/15e6;float full=r.gpuMillis();if(!enabled)baseline=full;
                    report.append(q+" enabled="+enabled+": full GPU EMA "+full+" ms; CPU submission/frame "+cpu+" ms; atmosphere tables+sky GPU EMA "+r.atmosphereGpuMillis()+" ms; tables GPU EMA "+r.atmosphereTableGpuMillis()+" ms; rebuild max CPU submission "+peakCpu+" ms; max tables GPU sample "+peakTable+" ms; max tables+sky GPU sample "+peakFrame+" ms; peak LUT allocation "+q.bytes()+" bytes; enabled-minus-airless full-frame GPU delta "+(enabled?full-baseline:0)+" ms.\n");
                    Files.writeString(Path.of(args[0]).resolve("atmosphere-performance.txt"),report+"Partial run: completed modes above; 30 warmup and 15 steady frames/mode.\n");
                }
            }
        }finally{glfwDestroyWindow(window);glfwTerminate();}
        report.append("Software correctness/performance only. EMAs and sequential profile delta include queue/driver noise; not a hardware <=2 ms claim. A declared desktop reference GPU and physical iPhone budget remain unavailable.\n");
        Files.writeString(Path.of(args[0]).resolve("atmosphere-performance.txt"),report.toString());System.out.println("Performance benchmark passed");
    }
}
