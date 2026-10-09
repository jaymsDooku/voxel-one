package dev.jayms.render;

import dev.jayms.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.FrustumIntersection;
import java.nio.ByteBuffer;
import static org.lwjgl.opengl.GL33.*;

/** Per-view planar reflection and depth-aware refraction of the already rendered opaque scene. */
public final class WaterRenderer implements AutoCloseable {
    private final ShaderProgram water=new ShaderProgram("shaders/voxel.vert","shaders/water.frag");
    private final ShaderProgram capture=new ShaderProgram("shaders/voxel.vert","shaders/planar.frag");
    private final ShaderProgram sceneCapture=new ShaderProgram("shaders/voxel.vert","shaders/voxel.frag");
    private final ShaderProgram sky=new ShaderProgram("shaders/fullscreen.vert","shaders/probe-sky.frag");
    private final int fbo=glGenFramebuffers(),color=glGenTextures(),depth=glGenRenderbuffers(),vao=glGenVertexArrays();
    private int width,height;private float plane;private boolean found;
    private final Matrix4f reflected=new Matrix4f();
    public boolean found(){return found;}
    public void capture(World world,VoxelModelRenderer models,Matrix4f projection,Matrix4f view,Vector3f eye,int w,int h,int environment,Vector3f sun,float ambient,float daylight){capture(world,models,projection,view,eye,w,h,environment,sun,ambient,daylight,null);}
    public void capture(World world,VoxelModelRenderer models,Matrix4f projection,Matrix4f view,Vector3f eye,int w,int h,int environment,Vector3f sun,float ambient,float daylight,java.util.function.Consumer<ShaderProgram> lighting){
        found=false;float nearest=Float.POSITIVE_INFINITY;
        // Water-bearing chunk surfaces come from the current mesh, including generated oceans.
        for(var entry:world.getLoadedChunks().entrySet()){
            Chunk chunk=entry.getValue();if(chunk.getMesh()==null||!Float.isFinite(chunk.waterHeight()))continue;
            ChunkPos p=entry.getKey();float d=new Vector3f(p.chunkX()*16+8,p.chunkY()*16+8,p.chunkZ()*16+8).distanceSquared(eye);
            if(d<nearest){nearest=d;plane=p.chunkY()*16+chunk.waterHeight();found=true;}
        }
        if(!found||eye.y<plane)return;
        int rw=Math.max(1,w/2),rh=Math.max(1,h/2);
        glBindFramebuffer(GL_FRAMEBUFFER,fbo);
        if(rw!=width||rh!=height){
            width=rw;height=rh;glBindTexture(GL_TEXTURE_2D,color);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA16F,width,height,0,GL_RGBA,GL_FLOAT,(ByteBuffer)null);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
            glBindRenderbuffer(GL_RENDERBUFFER,depth);glRenderbufferStorage(GL_RENDERBUFFER,GL_DEPTH_COMPONENT24,width,height);
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,color,0);glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_RENDERBUFFER,depth);
            if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete planar framebuffer");
        }
        Matrix4f mirror=new Matrix4f().translation(0,plane*2,0).scale(1,-1,1);
        Matrix4f reflectedView=new Matrix4f(view).mul(mirror);reflected.set(projection).mul(reflectedView);
        Vector3f reflectedEye=new Vector3f(eye.x,plane*2-eye.y,eye.z);
        glViewport(0,0,width,height);glClear(GL_DEPTH_BUFFER_BIT);glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);
        sky.bind();sky.setMatrix4("uInverseVP",new Matrix4f(reflected).invert());sky.setVector3("uCenter",reflectedEye.x,reflectedEye.y,reflectedEye.z);sky.setInt("uEnvironment",0);
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_CUBE_MAP,environment);glBindVertexArray(vao);glDrawArrays(GL_TRIANGLES,0,3);
        glEnable(GL_DEPTH_TEST);glEnable(GL_CULL_FACE);glFrontFace(GL_CW);glEnable(GL_CLIP_DISTANCE0);
        ShaderProgram pass=lighting==null?capture:sceneCapture;
        pass.bind();pass.setMatrix4("uProjection",projection);pass.setMatrix4("uView",reflectedView);pass.setInt("uVertexColor",1);pass.setInt("uInstanced",0);
        pass.setInt("uClipEnabled",1);pass.setFloat("uClipPlaneY",plane+.02f);
        pass.setVector3("uSun",sun.x,sun.y,sun.z);pass.setFloat("uAmbient",ambient);pass.setFloat("uDaylight",daylight);
        if(lighting!=null){lighting.accept(pass);pass.setVector3("uCameraPosition",reflectedEye.x,reflectedEye.y,reflectedEye.z);pass.setInt("uProbeReady",0);pass.setInt("uFog",1);pass.setInt("uAtmosphereReflection",1);pass.setFloat("uAtmosphereReflectionPlane",plane);pass.setVector3("uAtmosphereReflectedEye",reflectedEye.x,reflectedEye.y,reflectedEye.z);}
        FrustumIntersection frustum=new FrustumIntersection(reflected);
        for(var entry:world.getLoadedChunks().entrySet()){
            ChunkPos p=entry.getKey();if(entry.getValue().getMesh()==null||!frustum.testAab(p.chunkX()*16,p.chunkY()*16,p.chunkZ()*16,p.chunkX()*16+16,p.chunkY()*16+16,p.chunkZ()*16+16))continue;
            pass.setMatrix4("uModel",new Matrix4f().translation(p.chunkX()*16,p.chunkY()*16,p.chunkZ()*16));entry.getValue().getMesh().render();
        }
        models.render(world,frustum,pass);glDisable(GL_CLIP_DISTANCE0);glFrontFace(GL_CCW);glBindFramebuffer(GL_FRAMEBUFFER,0);
    }
    public void render(World world,Matrix4f projection,Matrix4f view,Vector3f eye,int scene,int sceneDepth,int environment,int w,int h,float jitterX,float jitterY){render(world,projection,view,eye,scene,sceneDepth,environment,w,h,jitterX,jitterY,null);}
    public void render(World world,Matrix4f projection,Matrix4f view,Vector3f eye,int scene,int sceneDepth,int environment,int w,int h,float jitterX,float jitterY,java.util.function.Consumer<ShaderProgram> lighting){
        if(!found)return;Matrix4f vp=new Matrix4f().translation(jitterX,jitterY,0).mul(projection).mul(view);FrustumIntersection frustum=new FrustumIntersection(vp);
        water.bind();water.setFloat("uJitterX",jitterX);water.setFloat("uJitterY",jitterY);water.setMatrix4("uProjection",projection);water.setMatrix4("uView",view);water.setMatrix4("uInverseVP",new Matrix4f(vp).invert());water.setMatrix4("uReflectionVP",reflected);
        water.setFloat("uPlaneY",plane);water.setVector3("uCamera",eye.x,eye.y,eye.z);water.setFloat("uTime",(float)(System.nanoTime()/1e9%10000));water.setInt("uInstanced",0);water.setFloat("uWidth",w);water.setFloat("uHeight",h);water.setInt("uPlanarReady",eye.y>=plane?1:0);
        if(lighting!=null)lighting.accept(water);else water.setInt("uPlanetLighting",0);
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_CUBE_MAP,environment);water.setInt("uEnvironment",0);
        glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,scene);water.setInt("uScene",1);
        glActiveTexture(GL_TEXTURE2);glBindTexture(GL_TEXTURE_2D,sceneDepth);water.setInt("uDepth",2);
        glActiveTexture(GL_TEXTURE13);glBindTexture(GL_TEXTURE_2D,color);water.setInt("uPlanar",13);
        for(var entry:world.getLoadedChunks().entrySet()){
            ChunkPos p=entry.getKey();Chunk chunk=entry.getValue();if(chunk.getMesh()==null||!Float.isFinite(chunk.waterHeight())||!frustum.testAab(p.chunkX()*16,p.chunkY()*16,p.chunkZ()*16,p.chunkX()*16+16,p.chunkY()*16+16,p.chunkZ()*16+16))continue;
            water.setMatrix4("uModel",new Matrix4f().translation(p.chunkX()*16,p.chunkY()*16,p.chunkZ()*16));chunk.getMesh().render();
        }
        glActiveTexture(GL_TEXTURE0);
    }
    @Override public void close(){water.close();capture.close();sceneCapture.close();sky.close();glDeleteFramebuffers(fbo);glDeleteTextures(color);glDeleteRenderbuffers(depth);glDeleteVertexArrays(vao);}
}
