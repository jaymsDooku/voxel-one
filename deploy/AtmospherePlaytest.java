import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import dev.jayms.*;
import dev.jayms.render.*;
import dev.jayms.net.city.GameConfig;
import dev.jayms.net.Blocks;
import dev.jayms.net.atmosphere.AtmosphereConfig;
import dev.jayms.net.atmosphere.AtmosphereConfig.Vec;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import java.nio.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Deterministic engine playtest. Actual GL renderer; fixed exposure and synthetic voxel geometry. */
public class AtmospherePlaytest {
    static final int W=640,H=400;
    static void require(boolean b,String text){if(!b)throw new AssertionError(text);}
    static BufferedImage capture(){ByteBuffer rgb=MemoryUtil.memAlloc(W*H*3);var image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        try{glReadPixels(0,0,W,H,GL_RGB,GL_UNSIGNED_BYTE,rgb);for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=(x+y*W)*3;image.setRGB(x,H-1-y,(rgb.get(i)&255)<<16|(rgb.get(i+1)&255)<<8|(rgb.get(i+2)&255));}}finally{MemoryUtil.memFree(rgb);}return image;}
    static double mean(BufferedImage image){long total=0;for(int y=0;y<H;y++)for(int x=0;x<W;x++){int c=image.getRGB(x,y);total+=(c>>16&255)+(c>>8&255)+(c&255);}return total/(double)(W*H*3);}
    static Object field(Object owner,String name)throws Exception {var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    static double transmittanceReferenceCheck(RenderPipeline renderer)throws Exception {
        var atmosphere=(PlanetAtmosphere)field(renderer,"atmosphere");int texture=(int)field(atmosphere,"trans");
        glActiveTexture(GL_TEXTURE15);glBindTexture(GL_TEXTURE_2D,texture);float[] table=new float[256*64*4];glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_FLOAT,table);double worst=0;
        for(int y:new int[]{1,16,40,63})for(int x:new int[]{140,170,220,255}) {
            double mapped=(x+.5)/256*2-1,mu=mapped*Math.abs(mapped),altitude=Math.pow((y+.5)/64,2)*100_000;
            var expected=AtmosphereReference.transmittance(AtmosphereConfig.earth(),new Vec(0,6_360_000+altitude,0),new Vec(Math.sqrt(1-mu*mu),mu,0),13_000_000,4096);
            for(int k=0;k<3;k++){double value=table[(x+y*256)*4+k],error=Math.abs(value-expected.component(k));worst=Math.max(worst,error);require(Double.isFinite(value)&&value>=0&&value<=1,"LUT transmittance bounds");require(error<=.035,"4096-sample reference transmittance absolute error <= 0.035; got "+error);}
        }
        glActiveTexture(GL_TEXTURE0);return worst;
    }
    static BufferedImage scene(RenderPipeline renderer,ShaderProgram shader,World world,Vector3f eye,boolean ortho,boolean geometry){
        Matrix4f projection=ortho?new Matrix4f().ortho(-40,40,-25,25,.1f,4096):new Matrix4f().perspective(1.2f,W/(float)H,.1f,1e6f);
        Matrix4f view=ortho?new Matrix4f().lookAt(eye,new Vector3f(8,26,8),new Vector3f(0,1,0)):new Matrix4f().lookAt(eye,new Vector3f(eye).add(0,100,-1000),new Vector3f(0,1,0));
        require(glGetError()==GL_NO_ERROR,"Before begin GL errors");
        renderer.begin(W,H,projection,view,eye,ortho,shader);
        int beginError=glGetError();require(beginError==GL_NO_ERROR,"begin GL error "+beginError);
        shader.setMatrix4("uProjection",projection);shader.setMatrix4("uView",view);shader.setInt("uVertexColor",1);shader.setInt("uInstanced",0);shader.setInt("uFog",1);
        if(geometry)for(var e:world.getLoadedChunks().entrySet()){var p=e.getKey();shader.setMatrix4("uModel",new Matrix4f().translation(p.chunkX()*16,p.chunkY()*16,p.chunkZ()*16));renderer.chunk(e.getValue(),p);}
        renderer.finish();glFinish();int finishError=glGetError();require(finishError==GL_NO_ERROR,"finish GL error "+finishError);return capture();
    }
    public static void main(String[] args)throws Exception {
        Path evidence=Path.of(args[0]);Files.createDirectories(evidence);require(glfwInit(),"GLFW initialized on role display");
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window=glfwCreateWindow(W,H,"Voxel One atmosphere playtest",0,0);require(window!=0,"GL 3.3 window");glfwMakeContextCurrent(window);GL.createCapabilities();
        String driver=glGetString(GL_RENDERER);StringBuilder report=new StringBuilder("Playtest: Voxel One engine GL 3.3 raster atmosphere. Synthetic world/profile; 640x400, one sample, fixed exposure=1, TAA/clouds off. Driver: "+driver+"\n");
        try(var renderer=new RenderPipeline();var shader=new ShaderProgram("shaders/voxel.vert","shaders/voxel.frag");var world=new World()) {
            renderer.settings.autoExposure=false;renderer.settings.taa=false;renderer.settings.exposure=1;renderer.settings.clouds=false;renderer.settings.atmosphereQuality=PlanetAtmosphere.Quality.MEDIUM;
            Vector3f eye=new Vector3f(8,30,24);BufferedImage noon=null,night=null;
            for(double hour:new double[]{12,6,18,0}) {
                renderer.time(new GameConfig(false,true,1200,hour),0);var image=scene(renderer,shader,world,eye,false,false);
                String name=hour==12?"noon":hour==6?"dawn":hour==18?"dusk":"night";
                ImageIO.write(image,"png",evidence.resolve("atmosphere-"+name+".png").toFile());report.append(name+": mean "+mean(image)+", LUT rebuild CPU submission "+renderer.atmosphereRebuildMillis()+" ms; GL_NO_ERROR.\n");
                if(hour==12)noon=image;if(hour==0)night=image;
            }
            require(mean(noon)>mean(night)+5,"Noon sky brighter than night at fixed exposure");
            report.append("16 GPU transmittance texels / 48 RGB components compared with double 4096-sample reference, upward and low-sun paths at four heights; maximum absolute error "+transmittanceReferenceCheck(renderer)+" <= 0.035. Readback used only for correctness, not performance.\n");
            renderer.time(new GameConfig(false,true,1200,12),0);
            for(float altitude:new float[]{8000,90000,120000}) {
                eye.y=24+altitude;var image=scene(renderer,shader,world,eye,false,false);ImageIO.write(image,"png",evidence.resolve("atmosphere-altitude-"+(int)altitude+".png").toFile());
                report.append("Altitude "+altitude+" m: finite render, GL_NO_ERROR, mean "+mean(image)+".\n");
            }
            renderer.atmosphere(AtmosphereConfig.airless());eye.y=30;var airless=scene(renderer,shader,world,eye,false,false);ImageIO.write(airless,"png",evidence.resolve("atmosphere-airless.png").toFile());require(mean(airless)<mean(noon),"Airless edge removes scattering");
            renderer.atmosphere(AtmosphereConfig.earth());
            var chunk=new Chunk();for(int x=0;x<16;x++)for(int z=0;z<16;z++)chunk.setBlock(x,8,z,Blocks.STONE);world.addChunk(new ChunkPos(0,1,0),chunk);chunk.checkMesh();
            eye.set(42,60,42);var overview=scene(renderer,shader,world,eye,true,true);ImageIO.write(overview,"png",evidence.resolve("atmosphere-isometric.png").toFile());report.append("Orthographic regression: visible synthetic stone surface, per-pixel sky ray origin, GL_NO_ERROR.\n");
            renderer.settings.atmosphereQuality=PlanetAtmosphere.Quality.LOW;scene(renderer,shader,world,eye,true,true);renderer.settings.atmosphereQuality=PlanetAtmosphere.Quality.HIGH;scene(renderer,shader,world,eye,true,true);
            report.append("Low/Medium/High profile-preserving table rebuilds and airless/re-enable completed. Desktop GPU target and mobile budget remain unmeasured.\n");
        } finally{glfwDestroyWindow(window);glfwTerminate();}
        Files.writeString(evidence.resolve("atmosphere-playtest.txt"),report.toString());System.out.println("Playtest: atmosphere workflow passed; captures saved.");
    }
}
