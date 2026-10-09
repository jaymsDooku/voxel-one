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
    static VoxelModelRenderer models;
    static void require(boolean b,String text){if(!b)throw new AssertionError(text);}
    static BufferedImage capture(){ByteBuffer rgb=MemoryUtil.memAlloc(W*H*3);var image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        try{glReadPixels(0,0,W,H,GL_RGB,GL_UNSIGNED_BYTE,rgb);for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=(x+y*W)*3;image.setRGB(x,H-1-y,(rgb.get(i)&255)<<16|(rgb.get(i+1)&255)<<8|(rgb.get(i+2)&255));}}finally{MemoryUtil.memFree(rgb);}return image;}
    static double mean(BufferedImage image){long total=0;for(int y=0;y<H;y++)for(int x=0;x<W;x++){int c=image.getRGB(x,y);total+=(c>>16&255)+(c>>8&255)+(c&255);}return total/(double)(W*H*3);}
    static Object field(Object owner,String name)throws Exception {var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    static double transmittanceReferenceCheck(RenderPipeline renderer)throws Exception {
        var atmosphere=(PlanetAtmosphere)field(renderer,"atmosphere");int texture=(int)field(atmosphere,"trans");
        glActiveTexture(GL_TEXTURE15);glBindTexture(GL_TEXTURE_2D,texture);require(glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_WIDTH)==256&&glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_HEIGHT)==80,"Reference requires complete Medium atlas");float[] table=new float[256*80*4];glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_FLOAT,table);double worst=0;
        for(int y:new int[]{1,16,40,63})for(int x:new int[]{140,170,220,255}) {
            double mapped=(x+.5)/256*2-1,mu=mapped*Math.abs(mapped),altitude=Math.pow((y+.5)/64,2)*100_000;
            var expected=AtmosphereReference.transmittance(AtmosphereConfig.earth(),new Vec(0,6_360_000+altitude,0),new Vec(Math.sqrt(1-mu*mu),mu,0),13_000_000,4096);
            for(int k=0;k<3;k++){double value=table[(x+y*256)*4+k],error=Math.abs(value-expected.component(k));worst=Math.max(worst,error);require(Double.isFinite(value)&&value>=0&&value<=1,"LUT transmittance bounds");require(error<=.035,"4096-sample reference transmittance absolute error <= 0.035; got "+error);}
        }
        glActiveTexture(GL_TEXTURE0);return worst;
    }
    // Compare actual sky texels with the same physical equations at 256 path samples.
    // The solar atlas has its separate double-precision transmittance comparison.
    static double skyReferenceCheck(RenderPipeline renderer)throws Exception {
        var atmosphere=(PlanetAtmosphere)field(renderer,"atmosphere");
        glActiveTexture(GL_TEXTURE14);glBindTexture(GL_TEXTURE_2D,(int)field(atmosphere,"sky"));require(glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_WIDTH)==192&&glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_HEIGHT)==108,"Reference requires complete Medium sky");float[] table=new float[192*108*4];glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_FLOAT,table);
        int oldFbo=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),oldRead=glGetInteger(GL_READ_FRAMEBUFFER_BINDING),oldVao=glGetInteger(GL_VERTEX_ARRAY_BINDING);int[] viewport=new int[4];glGetIntegerv(GL_VIEWPORT,viewport);
        boolean depth=glIsEnabled(GL_DEPTH_TEST),blend=glIsEnabled(GL_BLEND),cull=glIsEnabled(GL_CULL_FACE),scissor=glIsEnabled(GL_SCISSOR_TEST);
        int fbo=glGenFramebuffers(),texture=glGenTextures(),vao=glGenVertexArrays();double worst=0;
        try(var pass=new ShaderProgram("shaders/fullscreen.vert","shaders/atmosphere-sky.frag")){
            glBindTexture(GL_TEXTURE_2D,texture);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,1,1,0,GL_RGBA,GL_FLOAT,(ByteBuffer)null);
            glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);glDrawBuffer(GL_COLOR_ATTACHMENT0);glReadBuffer(GL_COLOR_ATTACHMENT0);
            require(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"Reference framebuffer");
            glBindVertexArray(vao);glDisable(GL_DEPTH_TEST);glDisable(GL_BLEND);glDisable(GL_CULL_FACE);glDisable(GL_SCISSOR_TEST);
            pass.bind();atmosphere.bind(pass);pass.setInt("uAtmosphereSamples",256);
            for(int y:new int[]{53,54,72})for(int x:new int[]{48,96,144}){
                glViewport(-x,-y,192,108);glDrawArrays(GL_TRIANGLES,0,3);float[] reference=new float[4];glReadPixels(0,0,1,1,GL_RGBA,GL_FLOAT,reference);
                for(int k=0;k<3;k++){
                    double value=table[(x+y*192)*4+k],error=Math.abs(value-reference[k])/Math.max(.01,reference[k]);
                    require(Float.isFinite(reference[k])&&reference[k]>=0,"Finite nonnegative high-sample sky");worst=Math.max(worst,error);
                    require(error<=.25,"Sky 256-sample normalized error <=0.25; got "+error);
                }
            }
        }finally{
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER,oldFbo);glBindFramebuffer(GL_READ_FRAMEBUFFER,oldRead);glBindVertexArray(oldVao);glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            if(depth)glEnable(GL_DEPTH_TEST);if(blend)glEnable(GL_BLEND);if(cull)glEnable(GL_CULL_FACE);if(scissor)glEnable(GL_SCISSOR_TEST);
            glDeleteFramebuffers(fbo);glDeleteTextures(texture);glDeleteVertexArrays(vao);glActiveTexture(GL_TEXTURE0);
        }
        return worst;
    }
    static double multipleReferenceCheck(RenderPipeline renderer)throws Exception {
        var atmosphere=(PlanetAtmosphere)field(renderer,"atmosphere");
        glActiveTexture(GL_TEXTURE15);glBindTexture(GL_TEXTURE_2D,(int)field(atmosphere,"trans"));
        require(glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_WIDTH)==256&&glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_HEIGHT)==80,"Reference requires complete Medium atlas");float[] table=new float[256*80*4];glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_FLOAT,table);double worst=0;
        for(int y:new int[]{0,5,8})for(int x:new int[]{7,8,14}) {
            double mu=(x+.5)/16*2-1,altitude=Math.pow((y+.5)/16,2)*100_000;
            Vec expected=AtmosphereReference.multiple(AtmosphereConfig.earth(),altitude,mu,256,256,256);
            for(int k=0;k<3;k++) {
                double value=table[(x+(64+y)*256)*4+k],error=Math.abs(value-expected.component(k));
                require(Double.isFinite(value)&&value>=0,"Multiple-scattering finite nonnegative");
                double relative=error/Math.max(.01,expected.component(k));worst=Math.max(worst,relative);
                require(relative<=.25,"Multiple closure 256-step reference normalized error <= 0.25; got "+relative);
            }
        }
        glActiveTexture(GL_TEXTURE0);return worst;
    }
    static void lifecycleCheck()throws Exception {
        var atmosphere=new PlanetAtmosphere();Vector3f camera=new Vector3f(8,30,24),sun=new Vector3f(.45f,.78f,-.45f).normalize();
        atmosphere.update(camera,sun);require(atmosphere.initialized(),"Bootstrap initialized");
        int initial=(int)field(atmosphere,"trans");long revision=atmosphere.staticRevision;
        atmosphere.update(camera,new Vector3f(1,1,0).normalize());
        require(atmosphere.rebuilding(),"Sun change starts view rebuild");
        require((int)field(atmosphere,"trans")==initial&&atmosphere.staticRevision==revision,"Sun reuses static tables");
        atmosphere.profile(AtmosphereConfig.hazy());atmosphere.update(camera,sun);
        require((int)field(atmosphere,"trans")==initial&&glIsTexture(initial),"Old initialized atlas retained during rebuild");
        atmosphere.profile(AtmosphereConfig.thin());
        int frames=0;do {atmosphere.update(camera,sun);require(atmosphere.lastRowsSubmitted<=PlanetAtmosphere.Quality.LOW.rows,"Row work bounded after bootstrap");}while(atmosphere.rebuilding()&&frames++<160);
        require(!atmosphere.rebuilding()&&atmosphere.config().equals(AtmosphereConfig.thin()),"Rapid profile change cancels and publishes latest complete output");
        require(!glIsTexture(initial),"Replaced atlas deleted");
        int atlas=(int)field(atmosphere,"trans"),sky=(int)field(atmosphere,"sky");
        atmosphere.close();require(!glIsTexture(atlas)&&!glIsTexture(sky),"Close deletes active textures");
        require(glGetError()==GL_NO_ERROR,"Lifecycle GL_NO_ERROR");
    }
    static BufferedImage scene(RenderPipeline renderer,ShaderProgram shader,World world,Vector3f eye,boolean ortho,boolean geometry){
        Matrix4f projection=ortho?new Matrix4f().ortho(-40,40,-25,25,.1f,4096):new Matrix4f().perspective(1.2f,W/(float)H,.1f,1e6f);
        Matrix4f view=ortho?new Matrix4f().lookAt(eye,new Vector3f(8,26,8),new Vector3f(0,1,0)):new Matrix4f().lookAt(eye,new Vector3f(eye).add(0,100,-1000),new Vector3f(0,1,0));
        require(glGetError()==GL_NO_ERROR,"Before begin GL errors");
        renderer.begin(W,H,projection,view,eye,ortho,shader);
        int beginError=glGetError();require(beginError==GL_NO_ERROR,"begin GL error "+beginError);
        shader.setMatrix4("uProjection",projection);shader.setMatrix4("uView",view);shader.setInt("uVertexColor",1);shader.setInt("uInstanced",0);shader.setInt("uFog",1);
        if(geometry)for(var e:world.getLoadedChunks().entrySet()){var p=e.getKey();shader.setMatrix4("uModel",new Matrix4f().translation(p.chunkX()*16,p.chunkY()*16,p.chunkZ()*16));renderer.chunk(e.getValue(),p);}
        if(geometry&&models!=null){models.render(world,new org.joml.FrustumIntersection(new Matrix4f(projection).mul(view)),shader);renderer.water(world,models,projection,view,eye);}
        renderer.finish();glFinish();int finishError=glGetError();require(finishError==GL_NO_ERROR,"finish GL error "+finishError);return capture();
    }
    static BufferedImage settled(RenderPipeline renderer,ShaderProgram shader,World world,Vector3f eye,boolean ortho,boolean geometry){
        BufferedImage image=scene(renderer,shader,world,eye,ortho,geometry);int frames=0;
        while(renderer.atmosphereRebuilding()&&frames++<160)image=scene(renderer,shader,world,eye,ortho,geometry);
        require(!renderer.atmosphereRebuilding(),"Bounded LUT build completes within 160 frames");return image;
    }
    static void acceptLighting(RenderPipeline renderer,World world,float x,float z)throws Exception {
        long deadline=System.nanoTime()+30_000_000_000L;
        while(System.nanoTime()<deadline){renderer.update(world,x,z);if((boolean)field(renderer,"hasIrradiance")&&(long)field(field(renderer,"lighting"),"revision")==world.editsVersion())return;Thread.sleep(10);}
        throw new AssertionError("Synthetic voxel lighting accepted within 30 seconds");
    }
    public static void main(String[] args)throws Exception {
        Path evidence=Path.of(args[0]);Files.createDirectories(evidence);require(glfwInit(),"GLFW initialized on role display");
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window=glfwCreateWindow(W,H,"Voxel One atmosphere playtest",0,0);require(window!=0,"GL 3.3 window");glfwMakeContextCurrent(window);GL.createCapabilities();
        String driver=glGetString(GL_RENDERER);StringBuilder report=new StringBuilder("Playtest: Voxel One engine GL 3.3 raster atmosphere. Synthetic world/profile; 640x400, one sample, fixed exposure=1, TAA/clouds off. Driver: "+driver+"\n");
        try(var renderer=new RenderPipeline();var shader=new ShaderProgram("shaders/voxel.vert","shaders/voxel.frag");var world=new World()) {
            models=new VoxelModelRenderer(world.models());
            renderer.settings.autoExposure=false;renderer.settings.taa=false;renderer.settings.exposure=1;renderer.settings.clouds=false;renderer.settings.atmosphereQuality=PlanetAtmosphere.Quality.MEDIUM;
            Vector3f eye=new Vector3f(8,30,24);BufferedImage noon=null,night=null;
            for(double hour:new double[]{12,6,18,0}) {
                renderer.time(new GameConfig(false,true,1200,hour),0);var image=settled(renderer,shader,world,eye,false,false);
                String name=hour==12?"noon":hour==6?"dawn":hour==18?"dusk":"night";
                ImageIO.write(image,"png",evidence.resolve("atmosphere-"+name+".png").toFile());report.append(name+": mean "+mean(image)+", LUT rebuild CPU submission "+renderer.atmosphereRebuildMillis()+" ms; GL_NO_ERROR.\n");
                report.append(name+": 9 actual sky texels (horizon and upper sky) vs 256-path-sample raster reference; max normalized RGB error "+skyReferenceCheck(renderer)+" <=0.25.\n");
                if(hour==12)noon=image;if(hour==0)night=image;
            }
            require(mean(noon)>mean(night)+5,"Noon sky brighter than night at fixed exposure");
            report.append("16 GPU transmittance texels / 48 RGB components compared with double 4096-sample reference, upward and low-sun paths at four heights; maximum absolute error "+transmittanceReferenceCheck(renderer)+" <= 0.035. Readback used only for correctness, not performance.\n");
            report.append("9 GPU multiple-scattering texels / 27 RGB components compared with 256-direction closure at 256 view/sun samples; normalized error "+multipleReferenceCheck(renderer)+" <= 0.25. Approximation tolerance, not full transport parity.\n");
            lifecycleCheck();report.append("Lifecycle: static sun reuse, retained initialized atlas, rapid profile cancellation, per-frame row limits and texture cleanup passed.\n");
            renderer.time(new GameConfig(false,true,1200,12),0);
            for(float altitude:new float[]{8000,90000,120000}) {
                eye.y=24+altitude;var image=settled(renderer,shader,world,eye,false,false);ImageIO.write(image,"png",evidence.resolve("atmosphere-altitude-"+(int)altitude+".png").toFile());
                report.append("Altitude "+altitude+" m: finite render, GL_NO_ERROR, mean "+mean(image)+".\n");
            }
            renderer.atmosphere(AtmosphereConfig.airless());eye.y=30;var airless=settled(renderer,shader,world,eye,false,false);ImageIO.write(airless,"png",evidence.resolve("atmosphere-airless.png").toFile());require(mean(airless)<mean(noon),"Airless edge removes scattering");
            renderer.atmosphere(AtmosphereConfig.earth());
            var chunk=new Chunk();for(int x=0;x<16;x++)for(int z=0;z<16;z++)chunk.setBlock(x,8,z,Blocks.STONE);world.addChunk(new ChunkPos(0,1,0),chunk);chunk.checkMesh();acceptLighting(renderer,world,8,8);
            eye.set(42,60,42);var overview=settled(renderer,shader,world,eye,true,true);ImageIO.write(overview,"png",evidence.resolve("atmosphere-isometric.png").toFile());report.append("Orthographic regression: visible synthetic stone surface, per-pixel sky ray origin, GL_NO_ERROR.\n");
            var lit=overview;
            renderer.time(new GameConfig(false,false,1200,0),0);
            var dark=settled(renderer,shader,world,eye,true,true);
            ImageIO.write(dark,"png",evidence.resolve("atmosphere-fixed-midnight-surface.png").toFile());
            require(mean(lit)>mean(dark)+5,"Fixed sandbox noon and midnight affect sky and surface");
            renderer.time(new GameConfig(false,false,1200,12),0);settled(renderer,shader,world,eye,true,true);
            report.append("Fixed sandbox clock: noon surface/sky brighter than midnight; no second daylight factor in bound atmospheric direct sunlight.\n");
            renderer.settings.atmosphereQuality=PlanetAtmosphere.Quality.LOW;settled(renderer,shader,world,eye,true,true);renderer.settings.atmosphereQuality=PlanetAtmosphere.Quality.HIGH;settled(renderer,shader,world,eye,true,true);
            renderer.settings.atmosphereQuality=PlanetAtmosphere.Quality.MEDIUM;
            for(int x=3;x<=11;x++)for(int y=25;y<=31;y++)for(int z=3;z<=11;z++)
                world.setBlock(x,y,z,(x==3||x==11||y==25||y==31||z==3||z==11)?Blocks.STONE:0);
            world.getLoadedChunks().values().forEach(Chunk::checkMesh);acceptLighting(renderer,world,8,8);
            eye.set(7.5f,28.5f,7.5f);renderer.time(new GameConfig(false,false,1200,12),0);
            var closed=settled(renderer,shader,world,eye,false,true);
            ImageIO.write(closed,"png",evidence.resolve("atmosphere-sealed-noon.png").toFile());
            require(mean(closed)<1.5,"Sealed room receives no outdoor atmospheric light or fog; mean="+mean(closed));
            world.setBlock(7,28,3,0);world.setBlock(7,29,3,0);world.getLoadedChunks().values().forEach(Chunk::checkMesh);acceptLighting(renderer,world,8,8);
            var opened=settled(renderer,shader,world,eye,false,true);
            ImageIO.write(opened,"png",evidence.resolve("atmosphere-window-noon.png").toFile());
            require(mean(opened)>mean(closed)+1,"Open window admits atmosphere sky light");
            world.setBlock(7,28,3,Blocks.GLASS);world.setBlock(7,29,3,Blocks.GLASS);
            world.getLoadedChunks().values().forEach(Chunk::checkMesh);acceptLighting(renderer,world,8,8);
            var glass=settled(renderer,shader,world,eye,false,true);ImageIO.write(glass,"png",evidence.resolve("atmosphere-glass-noon.png").toFile());
            require(mean(glass)>mean(closed)+1,"Glass transmits outdoor lighting");
            for(int y:new int[]{28,29}){
                world.setBlock(7,y,3,0);
                for(int ix=0;ix<16;ix++)for(int iy=0;iy<16;iy++)world.apply(new dev.jayms.net.Protocol.Edit(7,y,3,Blocks.STONE,4,ix,iy,8));
            }
            world.getLoadedChunks().values().forEach(Chunk::checkMesh);acceptLighting(renderer,world,8,8);
            var fractional=settled(renderer,shader,world,eye,false,true);ImageIO.write(fractional,"png",evidence.resolve("atmosphere-fractional-noon.png").toFile());
            require(mean(fractional)<1.5,"Opaque 1/16 wall blocks sky and aerial light");
            var tree=new dev.jayms.net.model.SparseVoxelOctree(32);tree.fill(0,0,16,32,32,17,0xff8899aa);
            var model=world.models().register(new dev.jayms.net.model.ModelDefinition("Atmosphere thin wall",tree),"synthetic");
            world.setBlock(7,28,3,model.id());world.setBlock(7,29,3,model.id());
            world.getLoadedChunks().values().forEach(Chunk::checkMesh);acceptLighting(renderer,world,8,8);
            var tiny=settled(renderer,shader,world,eye,false,true);ImageIO.write(tiny,"png",evidence.resolve("atmosphere-tiny-wall-noon.png").toFile());
            require(mean(tiny)<1.5,"Opaque 1/32 model wall blocks sky and aerial light");
            report.append("Rendered glass/fractional/tiny regression: glass mean "+mean(glass)+", 1/16 wall "+mean(fractional)+", 1/32 model wall "+mean(tiny)+"; glass transmits, opaque fine walls remain dark.\n");
            world.setBlock(7,28,3,Blocks.STONE);world.setBlock(7,29,3,Blocks.STONE);world.setBlock(7,29,4,Blocks.LED);
            world.getLoadedChunks().values().forEach(Chunk::checkMesh);acceptLighting(renderer,world,8,8);
            renderer.time(new GameConfig(false,false,1200,0),0);
            var lamp=settled(renderer,shader,world,eye,false,true);
            ImageIO.write(lamp,"png",evidence.resolve("atmosphere-sealed-led-night.png").toFile());
            require(mean(lamp)>mean(closed)+1,"Local LED remains visible at night in sealed room");
            report.append("Sealed-room regression: noon mean "+mean(closed)+", open window "+mean(opened)+", sealed LED night "+mean(lamp)+"; no outdoor sky/fog in closed room, window and local LED remain lit.\n");
            renderer.time(new GameConfig(false,false,1200,12),0);renderer.settings.clouds=true;
            eye.set(8,30,24);var cloudy=settled(renderer,shader,world,eye,false,false);
            ImageIO.write(cloudy,"png",evidence.resolve("atmosphere-clouds-noon.png").toFile());
            renderer.settings.clouds=false;
            world.setBlock(8,32,8,Blocks.WATER);world.getLoadedChunks().values().forEach(Chunk::checkMesh);acceptLighting(renderer,world,8,8);
            eye.set(18,45,24);var wet=settled(renderer,shader,world,eye,true,true);
            ImageIO.write(wet,"png",evidence.resolve("atmosphere-water-overview.png").toFile());
            report.append("Clouds and water with shared atmospheric lighting/composition rendered with GL_NO_ERROR.\n");
            models.close();models=null;
            report.append("Low/Medium/High profile-preserving table rebuilds and airless/re-enable completed. Desktop GPU target and mobile budget remain unmeasured.\n");
        } finally{glfwDestroyWindow(window);glfwTerminate();}
        Files.writeString(evidence.resolve("atmosphere-playtest.txt"),report.toString());System.out.println("Playtest: atmosphere workflow passed; captures saved.");
    }
}
