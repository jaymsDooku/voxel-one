package dev.jayms.render;

import dev.jayms.ShaderProgram;
import dev.jayms.net.atmosphere.AtmosphereConfig;
import dev.jayms.net.atmosphere.AtmosphereConfig.Vec;
import org.joml.Vector3f;
import org.joml.Matrix3f;
import static org.lwjgl.opengl.GL33.*;

/** GL 3.3 raster LUT prototype. No compute shaders, readbacks or timing-query nesting.
 * Rebuilds own complete textures before publishing them to scene shaders. */
public final class PlanetAtmosphere implements AutoCloseable {
    public enum Quality {
        LOW(96,32,96,48,16),MEDIUM(256,64,192,108,32),HIGH(384,96,256,144,48);
        public final int transWidth,transHeight,skyWidth,skyHeight,samples;
        Quality(int tw,int th,int sw,int sh,int n){transWidth=tw;transHeight=th;skyWidth=sw;skyHeight=sh;samples=n;}
        public long bytes(){return (long)(transWidth*transHeight+skyWidth*skyHeight)*8*2;}
    }
    private final ShaderProgram transPass=new ShaderProgram("shaders/fullscreen.vert","shaders/atmosphere-transmittance.frag");
    private final ShaderProgram skyPass=new ShaderProgram("shaders/fullscreen.vert","shaders/atmosphere-sky.frag");
    private final ShaderProgram environmentPass=new ShaderProgram("shaders/fullscreen.vert","shaders/atmosphere-environment.frag");
    private final int fbo=glGenFramebuffers(),vao=glGenVertexArrays();
    private int trans,sky;
    private AtmosphereConfig config=AtmosphereConfig.earth();
    private Quality quality=Quality.LOW;
    private Vec position=config.planetPosition(0,26,0),sun=new Vec(.45,.78,-.45).unit();
    private Matrix3f transform=new Matrix3f();
    private boolean dirty=true;
    private double lastAltitude=Double.NaN,lastSun=Double.NaN;
    private Vec lastDirection;
    public double rebuildCpuMillis;
    public long revision;
    public AtmosphereConfig config(){return config;}
    public void profile(AtmosphereConfig value) {if(!config.equals(value)){config=value;dirty=true;}}
    public void quality(Quality value) {if(quality!=value){quality=value;dirty=true;}}
    private static Vector3f floatVector(Vec v){return new Vector3f((float)v.x(),(float)v.y(),(float)v.z());}
    public void update(Vector3f camera,Vector3f worldSun) {
        position=config.planetPosition(camera.x,camera.y,camera.z);
        // Local terrain can lie below its sea reference. Its geometry, not a diagnostic sphere,
        // determines ground visibility in gameplay. Keep the sky observer just above the shell floor.
        if(position.length()<config.radius()+1)position=position.unit().mul(config.radius()+1);
        Vec up=config.up(),east=Math.abs(up.y())<.99?new Vec(0,1,0).cross(up).unit():new Vec(0,0,1).cross(up).unit().mul(-1),north=east.cross(up);
        transform.set(floatVector(east),floatVector(up),floatVector(north));
        sun=east.mul(worldSun.x).add(up.mul(worldSun.y)).add(north.mul(worldSun.z)).unit();
        double altitude=position.length()-config.radius(),sunCos=position.unit().dot(sun);
        boolean changed=dirty||lastDirection==null||sun.sub(lastDirection).length()>.002||!Double.isFinite(lastAltitude)||Math.abs(altitude-lastAltitude)>Math.max(2,altitude*.002)||Math.abs(sunCos-lastSun)>.002;
        if(!changed)return;
        long start=System.nanoTime();int oldFbo=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),oldVao=glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int[] viewport=new int[4];glGetIntegerv(GL_VIEWPORT,viewport);boolean depth=glIsEnabled(GL_DEPTH_TEST),blend=glIsEnabled(GL_BLEND),cull=glIsEnabled(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);glDisable(GL_BLEND);glDisable(GL_CULL_FACE);glBindVertexArray(vao);glBindFramebuffer(GL_FRAMEBUFFER,fbo);
        int nextTrans=dirty||trans==0?texture(quality.transWidth,quality.transHeight):trans;
        int nextSky=texture(quality.skyWidth,quality.skyHeight);
        try {
            if(nextTrans!=trans){attach(nextTrans,quality.transWidth,quality.transHeight);transPass.bind();uniforms(transPass);glDrawArrays(GL_TRIANGLES,0,3);}
            attach(nextSky,quality.skyWidth,quality.skyHeight);skyPass.bind();uniforms(skyPass);
            glActiveTexture(GL_TEXTURE15);glBindTexture(GL_TEXTURE_2D,nextTrans);skyPass.setInt("uTransmittance",15);glDrawArrays(GL_TRIANGLES,0,3);
            if(trans!=0&&nextTrans!=trans)glDeleteTextures(trans);if(sky!=0)glDeleteTextures(sky);
            trans=nextTrans;sky=nextSky;dirty=false;lastAltitude=altitude;lastSun=sunCos;lastDirection=sun;revision++;
        } catch(RuntimeException failure){if(nextTrans!=trans)glDeleteTextures(nextTrans);glDeleteTextures(nextSky);throw failure;}
        finally {glBindFramebuffer(GL_FRAMEBUFFER,oldFbo);glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);glBindVertexArray(oldVao);
            if(depth)glEnable(GL_DEPTH_TEST);if(blend)glEnable(GL_BLEND);if(cull)glEnable(GL_CULL_FACE);glActiveTexture(GL_TEXTURE0);}
        rebuildCpuMillis=(System.nanoTime()-start)/1e6;
    }
    private static int texture(int w,int h){int t=glGenTextures();glBindTexture(GL_TEXTURE_2D,t);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA16F,w,h,0,GL_RGBA,GL_FLOAT,(java.nio.ByteBuffer)null);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);return t;}
    private void attach(int t,int w,int h){glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,t,0);glDrawBuffer(GL_COLOR_ATTACHMENT0);
        if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Atmosphere LUT framebuffer incomplete");glViewport(0,0,w,h);}
    private static void vector(ShaderProgram s,String name,Vec v,double scale){s.setVector3(name,(float)(v.x()*scale),(float)(v.y()*scale),(float)(v.z()*scale));}
    private void uniforms(ShaderProgram s){
        s.setInt("uAtmosphereEnabled",config.enabled()?1:0);s.setInt("uAtmosphereSamples",quality.samples);
        s.setFloat("uPlanetRadius",(float)(config.radius()*.001));s.setFloat("uAtmosphereHeight",(float)(config.height()*.001));
        s.setFloat("uRayleighScale",(float)(config.molecularScale()*.001));s.setFloat("uMieScale",(float)(config.aerosolScale()*.001));
        s.setFloat("uOzoneCentre",(float)(config.absorptionCentre()*.001));s.setFloat("uOzoneWidth",(float)(config.absorptionWidth()*.001));
        s.setFloat("uMieG",(float)config.anisotropy());s.setFloat("uSolarRadius",(float)config.solarRadius());s.setFloat("uBlockKm",(float)(config.metresPerBlock()*.001));
        vector(s,"uRayleigh",config.molecular(),1000);vector(s,"uMieScattering",config.aerosolScattering(),1000);
        vector(s,"uMieExtinction",config.aerosolExtinction(),1000);vector(s,"uOzone",config.absorption(),1000);vector(s,"uSolar",config.solarIrradiance(),1);
        vector(s,"uPlanetCamera",position,.001);vector(s,"uAtmosphereSun",sun,1);s.setMatrix3("uWorldToPlanet",transform);
    }
    public void bind(ShaderProgram s){uniforms(s);glActiveTexture(GL_TEXTURE15);glBindTexture(GL_TEXTURE_2D,trans);s.setInt("uTransmittance",15);
        glActiveTexture(GL_TEXTURE14);glBindTexture(GL_TEXTURE_2D,sky);s.setInt("uSkyView",14);glActiveTexture(GL_TEXTURE0);}
    public void environment(int cube) {
        int oldFbo=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),oldVao=glGetInteger(GL_VERTEX_ARRAY_BINDING);int[] vp=new int[4];glGetIntegerv(GL_VIEWPORT,vp);
        boolean depth=glIsEnabled(GL_DEPTH_TEST),cull=glIsEnabled(GL_CULL_FACE);glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);
        glBindFramebuffer(GL_FRAMEBUFFER,fbo);glBindVertexArray(vao);environmentPass.bind();bind(environmentPass);
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_CUBE_MAP,cube);
        for(int face=0;face<6;face++) {
            glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X+face,0,GL_RGB16F,128,128,0,GL_RGB,GL_FLOAT,(java.nio.ByteBuffer)null);
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_CUBE_MAP_POSITIVE_X+face,cube,0);
            glViewport(0,0,128,128);environmentPass.setInt("uFace",face);glDrawArrays(GL_TRIANGLES,0,3);
        }
        glGenerateMipmap(GL_TEXTURE_CUBE_MAP);glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_MIN_FILTER,GL_LINEAR_MIPMAP_LINEAR);glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_WRAP_R,GL_CLAMP_TO_EDGE);
        glBindFramebuffer(GL_FRAMEBUFFER,oldFbo);glBindVertexArray(oldVao);glViewport(vp[0],vp[1],vp[2],vp[3]);if(depth)glEnable(GL_DEPTH_TEST);if(cull)glEnable(GL_CULL_FACE);
    }
    @Override public void close(){glDeleteTextures(trans);glDeleteTextures(sky);glDeleteFramebuffers(fbo);glDeleteVertexArrays(vao);transPass.close();skyPass.close();environmentPass.close();}
}
