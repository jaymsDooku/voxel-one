package dev.jayms.render;

import dev.jayms.ShaderProgram;
import dev.jayms.net.atmosphere.AtmosphereConfig;
import dev.jayms.net.atmosphere.AtmosphereConfig.Vec;
import org.joml.Vector3f;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import static org.lwjgl.opengl.GL33.*;

/** GL 3.3 raster LUT prototype. No compute shaders, readbacks or timing-query nesting.
 * Rebuilds own complete textures before publishing them to scene shaders. */
public final class PlanetAtmosphere implements AutoCloseable {
    public enum Quality {
        LOW(96,32,96,48,16,8,4,2),MEDIUM(256,64,192,108,32,16,8,1),HIGH(384,96,256,144,48,32,16,1);
        public final int transWidth,transHeight,skyWidth,skyHeight,samples,multipleSize,rows,interval;
        Quality(int tw,int th,int sw,int sh,int n,int m,int r,int i){transWidth=tw;transHeight=th;skyWidth=sw;skyHeight=sh;samples=n;multipleSize=m;rows=r;interval=i;}
        public long bytes(){return (long)(transWidth*(transHeight+multipleSize)+skyWidth*skyHeight)*8*2+(long)multipleSize*multipleSize*8;}
    }
    private final ShaderProgram transPass=new ShaderProgram("shaders/fullscreen.vert","shaders/atmosphere-transmittance.frag");
    private final ShaderProgram multiplePass=new ShaderProgram("shaders/fullscreen.vert","shaders/atmosphere-multiple.frag");
    private final ShaderProgram skyPass=new ShaderProgram("shaders/fullscreen.vert","shaders/atmosphere-sky.frag");
    private final ShaderProgram environmentPass=new ShaderProgram("shaders/fullscreen.vert","shaders/atmosphere-environment.frag");
    private final int fbo=glGenFramebuffers(),vao=glGenVertexArrays();
    private int trans,sky;
    private AtmosphereConfig config=AtmosphereConfig.earth();
    private Quality quality=Quality.LOW;
    private AtmosphereConfig requestedConfig=config;
    private Quality requestedQuality=quality;
    private Job job;private int frame;
    public int lastRowsSubmitted;public long staticRevision;public boolean initialized(){return trans!=0&&sky!=0;}
    public boolean rebuilding(){return job!=null;}
    private static java.util.List<Object> physics(AtmosphereConfig c){return java.util.List.of(c.enabled(),c.radius(),c.height(),c.molecular(),c.aerosolScattering(),c.aerosolExtinction(),c.absorption(),c.molecularScale(),c.aerosolScale(),c.absorptionCentre(),c.absorptionWidth(),c.anisotropy(),c.groundAlbedo(),c.solarIrradiance());}
    private final class Job {
        final AtmosphereConfig profile;final Quality q;final Vec p,s;final Matrix3f matrix;final int atlas,image,multiple;final boolean ownsAtlas;int stage,row;
        Job(AtmosphereConfig c,Quality quality,Vec position,Vec sun,Matrix3f transform){
            profile=c;q=quality;p=position;s=sun;matrix=new Matrix3f(transform);
            ownsAtlas=trans==0||q!=PlanetAtmosphere.this.quality||!physics(c).equals(physics(config));
            atlas=ownsAtlas?texture(q.transWidth,q.transHeight+q.multipleSize):trans;image=texture(q.skyWidth,q.skyHeight);
            multiple=ownsAtlas?texture(q.multipleSize,q.multipleSize):0;stage=ownsAtlas?0:2;
        }
        void discard(){if(ownsAtlas)glDeleteTextures(atlas);glDeleteTextures(image);glDeleteTextures(multiple);}
    }
    private final Vector3f worldCamera=new Vector3f(),viewDirection=new Vector3f(0,0,-1);
    private boolean orthographic;private float overviewHaze=1;
    public void view(Matrix4f view,boolean ortho){view(view,ortho,1);}
    public void view(Matrix4f view,boolean ortho,float haze){overviewHaze=Float.isFinite(haze)?Math.max(0,Math.min(1,haze)):1;orthographic=ortho;new Matrix4f(view).invert().transformDirection(new Vector3f(0,0,-1),viewDirection).normalize();}
    private Vec position=config.planetPosition(0,26,0),sun=new Vec(.45,.78,-.45).unit();
    private Matrix3f transform=new Matrix3f();
    private boolean dirty=true;
    private double lastAltitude=Double.NaN,lastSun=Double.NaN;
    private Vec lastDirection;
    public double rebuildCpuMillis;
    public long revision;
    public AtmosphereConfig config(){return config;}
    public void profile(AtmosphereConfig value) {if(!requestedConfig.equals(value)){requestedConfig=value;dirty=true;}}
    public void quality(Quality value) {if(requestedQuality!=value){requestedQuality=value;dirty=true;}}
    private static Vector3f floatVector(Vec v){return new Vector3f((float)v.x(),(float)v.y(),(float)v.z());}
    private Vec planetPosition(AtmosphereConfig c,Vector3f camera){Vec p=c.planetPosition(camera.x,camera.y,camera.z);return p.length()<c.radius()+1?p.unit().mul(c.radius()+1):p;}
    private static Matrix3f matrix(AtmosphereConfig c){Vec up=c.up(),east=Math.abs(up.y())<.99?new Vec(0,1,0).cross(up).unit():new Vec(0,0,1).cross(up).unit().mul(-1);return new Matrix3f().set(floatVector(east),floatVector(up),floatVector(east.cross(up)));}
    private static Vec planetSun(Matrix3f m,Vector3f sun){return new Vec(m.m00()*sun.x+m.m10()*sun.y+m.m20()*sun.z,m.m01()*sun.x+m.m11()*sun.y+m.m21()*sun.z,m.m02()*sun.x+m.m12()*sun.y+m.m22()*sun.z).unit();}
    public void update(Vector3f camera,Vector3f worldSun) {
        worldCamera.set(camera);
        position=planetPosition(config,camera);transform=matrix(config);sun=planetSun(transform,worldSun);
        double altitude=position.length()-config.radius(),sunCos=position.unit().dot(sun);
        boolean changed=dirty||lastDirection==null||sun.sub(lastDirection).length()>.002||!Double.isFinite(lastAltitude)||Math.abs(altitude-lastAltitude)>Math.max(2,altitude*.002)||Math.abs(sunCos-lastSun)>.002;
        lastRowsSubmitted=0;frame++;
        if(job!=null){
            Matrix3f requestedMatrix=matrix(requestedConfig);Vec nextPosition=planetPosition(requestedConfig,camera),nextSun=planetSun(requestedMatrix,worldSun);
            double previousHeight=job.p.length()-job.profile.radius(),nextHeight=nextPosition.length()-requestedConfig.radius();
            if(!job.profile.equals(requestedConfig)||job.q!=requestedQuality||Math.abs(nextHeight-previousHeight)>Math.max(100,Math.abs(previousHeight)*.1)||nextSun.sub(job.s).length()>.1){job.discard();job=null;dirty=true;}
        }
        if(job==null&&changed){Matrix3f m=matrix(requestedConfig);job=new Job(requestedConfig,requestedQuality,planetPosition(requestedConfig,camera),planetSun(m,worldSun),m);}
        if(job==null||initialized()&&frame%requestedQuality.interval!=0)return;
        long start=System.nanoTime();int oldFbo=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),oldRead=glGetInteger(GL_READ_FRAMEBUFFER_BINDING),oldVao=glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int[] viewport=new int[4];glGetIntegerv(GL_VIEWPORT,viewport);boolean depth=glIsEnabled(GL_DEPTH_TEST),blend=glIsEnabled(GL_BLEND),cull=glIsEnabled(GL_CULL_FACE),scissor=glIsEnabled(GL_SCISSOR_TEST);
        int[] box=new int[4];glGetIntegerv(GL_SCISSOR_BOX,box);
        glDisable(GL_DEPTH_TEST);glDisable(GL_BLEND);glDisable(GL_CULL_FACE);glEnable(GL_SCISSOR_TEST);glBindVertexArray(vao);glBindFramebuffer(GL_FRAMEBUFFER,fbo);
        try {
            int budget=initialized()?job.q.rows:Integer.MAX_VALUE;
            while(job!=null&&budget>0){Job j=job;int total=j.stage==0?j.q.transHeight:j.stage==1?j.q.multipleSize:j.q.skyHeight;
                int rows=Math.min(total-j.row,budget),w=j.stage==0?j.q.transWidth:j.stage==1?j.q.multipleSize:j.q.skyWidth;
                ShaderProgram pass=j.stage==0?transPass:j.stage==1?multiplePass:skyPass;
                attach(j.stage==0?j.atlas:j.stage==1?j.multiple:j.image,w,j.stage==0?j.q.transHeight+ j.q.multipleSize:total);
                glViewport(0,0,w,total);glScissor(0,j.row,w,rows);pass.bind();uniforms(pass,j.profile,j.q,j.p,j.s,j.matrix);
                pass.setInt("uMultipleScatteringEnabled",j.stage==2?1:0);
                glActiveTexture(GL_TEXTURE15);glBindTexture(GL_TEXTURE_2D,j.stage==0?0:j.atlas);pass.setInt("uTransmittance",15);
                glDrawArrays(GL_TRIANGLES,0,3);j.row+=rows;budget-=rows;lastRowsSubmitted+=rows;
                if(j.row==total){
                    if(j.stage==1){glDisable(GL_SCISSOR_TEST);glActiveTexture(GL_TEXTURE15);glBindTexture(GL_TEXTURE_2D,j.atlas);glCopyTexSubImage2D(GL_TEXTURE_2D,0,0,j.q.transHeight,0,0,j.q.multipleSize,j.q.multipleSize);glEnable(GL_SCISSOR_TEST);}
                    if(j.stage==2){if(trans!=0&&j.ownsAtlas)glDeleteTextures(trans);if(sky!=0)glDeleteTextures(sky);glDeleteTextures(j.multiple);
                        trans=j.atlas;sky=j.image;config=j.profile;quality=j.q;position=planetPosition(config,camera);transform=matrix(config);sun=planetSun(transform,worldSun);
                        lastAltitude=j.p.length()-config.radius();lastSun=j.p.unit().dot(j.s);lastDirection=j.s;dirty=false;revision++;if(j.ownsAtlas)staticRevision++;job=null;
                    }else{j.stage++;j.row=0;}
                }
            }
        }catch(RuntimeException failure){if(job!=null){job.discard();job=null;}throw failure;}
        finally{glBindFramebuffer(GL_DRAW_FRAMEBUFFER,oldFbo);glBindFramebuffer(GL_READ_FRAMEBUFFER,oldRead);glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);glBindVertexArray(oldVao);glScissor(box[0],box[1],box[2],box[3]);if(!scissor)glDisable(GL_SCISSOR_TEST);if(depth)glEnable(GL_DEPTH_TEST);if(blend)glEnable(GL_BLEND);if(cull)glEnable(GL_CULL_FACE);glActiveTexture(GL_TEXTURE0);}
        rebuildCpuMillis=(System.nanoTime()-start)/1e6;
    }
    private static int texture(int w,int h){int t=glGenTextures();glBindTexture(GL_TEXTURE_2D,t);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA16F,w,h,0,GL_RGBA,GL_FLOAT,(java.nio.ByteBuffer)null);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);return t;}
    private void attach(int t,int w,int h){glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,t,0);glDrawBuffer(GL_COLOR_ATTACHMENT0);
        if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Atmosphere LUT framebuffer incomplete");glViewport(0,0,w,h);}
    private static void vector(ShaderProgram s,String name,Vec v,double scale){s.setVector3(name,(float)(v.x()*scale),(float)(v.y()*scale),(float)(v.z()*scale));}
    private void uniforms(ShaderProgram s){uniforms(s,config,quality,position,sun,transform);}
    private void uniforms(ShaderProgram s,AtmosphereConfig config,Quality quality,Vec position,Vec sun,Matrix3f transform){
        s.setVector3("uAtmosphereLutSize",quality.transWidth,quality.transHeight,quality.transHeight+quality.multipleSize);
        vector(s,"uGroundAlbedo",config.groundAlbedo(),1);s.setInt("uMultipleScatteringEnabled",1);
        s.setInt("uAtmosphereEnabled",config.enabled()?1:0);s.setInt("uAtmosphereSamples",quality.samples);
        s.setFloat("uPlanetRadius",(float)(config.radius()*.001));s.setFloat("uAtmosphereHeight",(float)(config.height()*.001));
        s.setFloat("uRayleighScale",(float)(config.molecularScale()*.001));s.setFloat("uMieScale",(float)(config.aerosolScale()*.001));
        s.setFloat("uOzoneCentre",(float)(config.absorptionCentre()*.001));s.setFloat("uOzoneWidth",(float)(config.absorptionWidth()*.001));
        s.setFloat("uMieG",(float)config.anisotropy());s.setFloat("uSolarRadius",(float)config.solarRadius());s.setFloat("uBlockKm",(float)(config.metresPerBlock()*.001));
        vector(s,"uRayleigh",config.molecular(),1000);vector(s,"uMieScattering",config.aerosolScattering(),1000);
        vector(s,"uMieExtinction",config.aerosolExtinction(),1000);vector(s,"uOzone",config.absorption(),1000);vector(s,"uSolar",config.solarIrradiance(),1);
        vector(s,"uPlanetCamera",position,.001);vector(s,"uAtmosphereSun",sun,1);s.setMatrix3("uWorldToPlanet",transform);
    }
    public void bind(ShaderProgram s){uniforms(s);s.setInt("uPlanetLighting",1);s.setInt("uAtmosphereOrtho",orthographic?1:0);s.setFloat("uOverviewHaze",overviewHaze);s.setVector3("uAtmosphereViewDirection",viewDirection.x,viewDirection.y,viewDirection.z);s.setVector3("uAtmosphereWorldCamera",worldCamera.x,worldCamera.y,worldCamera.z);glActiveTexture(GL_TEXTURE15);glBindTexture(GL_TEXTURE_2D,trans);s.setInt("uTransmittance",15);
        glActiveTexture(GL_TEXTURE14);glBindTexture(GL_TEXTURE_2D,sky);s.setInt("uSkyView",14);glActiveTexture(GL_TEXTURE0);}
    public void environment(int cube) {
        int oldFbo=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),oldRead=glGetInteger(GL_READ_FRAMEBUFFER_BINDING),oldVao=glGetInteger(GL_VERTEX_ARRAY_BINDING);int[] vp=new int[4];glGetIntegerv(GL_VIEWPORT,vp);
        boolean depth=glIsEnabled(GL_DEPTH_TEST),cull=glIsEnabled(GL_CULL_FACE),blend=glIsEnabled(GL_BLEND),scissor=glIsEnabled(GL_SCISSOR_TEST);glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);glDisable(GL_BLEND);glDisable(GL_SCISSOR_TEST);
        glBindFramebuffer(GL_FRAMEBUFFER,fbo);glBindVertexArray(vao);environmentPass.bind();bind(environmentPass);
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_CUBE_MAP,cube);
        for(int face=0;face<6;face++) {
            glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X+face,0,GL_RGB16F,128,128,0,GL_RGB,GL_FLOAT,(java.nio.ByteBuffer)null);
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_CUBE_MAP_POSITIVE_X+face,cube,0);
            glViewport(0,0,128,128);environmentPass.setInt("uFace",face);glDrawArrays(GL_TRIANGLES,0,3);
        }
        glGenerateMipmap(GL_TEXTURE_CUBE_MAP);glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_MIN_FILTER,GL_LINEAR_MIPMAP_LINEAR);glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_WRAP_R,GL_CLAMP_TO_EDGE);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER,oldFbo);glBindFramebuffer(GL_READ_FRAMEBUFFER,oldRead);glBindVertexArray(oldVao);glViewport(vp[0],vp[1],vp[2],vp[3]);if(depth)glEnable(GL_DEPTH_TEST);if(cull)glEnable(GL_CULL_FACE);if(blend)glEnable(GL_BLEND);if(scissor)glEnable(GL_SCISSOR_TEST);
    }
    @Override public void close(){if(job!=null){job.discard();job=null;}multiplePass.close();glDeleteTextures(trans);glDeleteTextures(sky);glDeleteFramebuffers(fbo);glDeleteVertexArrays(vao);transPass.close();skyPass.close();environmentPass.close();}
}
