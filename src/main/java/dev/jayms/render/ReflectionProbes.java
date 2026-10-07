package dev.jayms.render;

import dev.jayms.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.FrustumIntersection;
import java.nio.ByteBuffer;
import static org.lwjgl.opengl.GL33.*;

/** Rolling six-face local reflection capture; sky is used until all faces are complete. */
public final class ReflectionProbes implements AutoCloseable {
    private final int cube=glGenTextures(),fbo=glGenFramebuffers(),depth=glGenRenderbuffers(),vao=glGenVertexArrays();
    private final ShaderProgram voxel=new ShaderProgram("shaders/voxel.vert","shaders/voxel.frag");
    private final ShaderProgram sky=new ShaderProgram("shaders/fullscreen.vert","shaders/probe-sky.frag");
    private final Vector3f center=new Vector3f();
    private int face=-1,frames;private long revision=-1;private boolean ready;
    public ReflectionProbes(){
        glBindTexture(GL_TEXTURE_CUBE_MAP,cube);
        for(int i=0;i<6;i++)glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X+i,0,GL_RGB16F,64,64,0,GL_RGB,GL_FLOAT,(ByteBuffer)null);
        glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_MIN_FILTER,GL_LINEAR_MIPMAP_LINEAR);glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_WRAP_R,GL_CLAMP_TO_EDGE);
        glBindRenderbuffer(GL_RENDERBUFFER,depth);glRenderbufferStorage(GL_RENDERBUFFER,GL_DEPTH_COMPONENT24,64,64);
        glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_RENDERBUFFER,depth);glBindFramebuffer(GL_FRAMEBUFFER,0);
    }
    public void capture(World world,VoxelModelRenderer models,Vector3f eye,Vector3f sun,float daylight,float ambient,int environment,int material){
        if(face<0&&(revision!=world.editsVersion()||center.distanceSquared(eye)>256||frames++%120==0)){
            if(center.distanceSquared(eye)>256)ready=false;
            center.set(eye);revision=world.editsVersion();face=0;
        }
        if(face<0)return;
        Vector3f[] direction={new Vector3f(1,0,0),new Vector3f(-1,0,0),new Vector3f(0,1,0),new Vector3f(0,-1,0),new Vector3f(0,0,1),new Vector3f(0,0,-1)};
        Vector3f[] up={new Vector3f(0,-1,0),new Vector3f(0,-1,0),new Vector3f(0,0,1),new Vector3f(0,0,-1),new Vector3f(0,-1,0),new Vector3f(0,-1,0)};
        Matrix4f projection=new Matrix4f().perspective((float)Math.PI/2,1,.1f,96),view=new Matrix4f().lookAt(center,new Vector3f(center).add(direction[face]),up[face]);
        Matrix4f vp=new Matrix4f(projection).mul(view);FrustumIntersection frustum=new FrustumIntersection(vp);
        glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_CUBE_MAP_POSITIVE_X+face,cube,0);
        glViewport(0,0,64,64);glClear(GL_DEPTH_BUFFER_BIT);glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);
        sky.bind();sky.setMatrix4("uInverseVP",new Matrix4f(vp).invert());sky.setVector3("uCenter",center.x,center.y,center.z);sky.setInt("uEnvironment",0);
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_CUBE_MAP,environment);glBindVertexArray(vao);glDrawArrays(GL_TRIANGLES,0,3);glBindVertexArray(0);
        glEnable(GL_DEPTH_TEST);glEnable(GL_CULL_FACE);voxel.bind();
        voxel.setMatrix4("uProjection",projection);voxel.setMatrix4("uView",view);
        voxel.setVector3("uCameraPosition",center.x,center.y,center.z);voxel.setVector3("uLightDirection",-sun.x,-sun.y,-sun.z);
        voxel.setFloat("uDaylight",daylight);voxel.setFloat("uAmbient",ambient);voxel.setInt("uVertexColor",1);voxel.setInt("uInstanced",0);
        for(int i=0;i<3;i++)voxel.setInt("uCascadeProbes["+i+"]",8+i);
        voxel.setInt("uShadow",1);voxel.setInt("uIrradiance",3);voxel.setInt("uFineRoots",4);voxel.setInt("uFineLight",5);
        voxel.setInt("uVoxelRadiance",6);voxel.setInt("uDistanceField",7);voxel.setInt("uClusters",11);voxel.setInt("uLightIndices",12);voxel.setInt("uLights",13);voxel.setInt("uReflectionProbe",14);
        voxel.setInt("uLightingEnabled",1);voxel.setInt("uEnvironment",0);voxel.setInt("uMaterials",2);
        glActiveTexture(GL_TEXTURE2);glBindTexture(GL_TEXTURE_2D_ARRAY,material);
        for(var entry:world.getLoadedChunks().entrySet()){
            var p=entry.getKey();if(entry.getValue().getMesh()==null||!frustum.testAab(p.chunkX()*16,p.chunkY()*16,p.chunkZ()*16,p.chunkX()*16+16,p.chunkY()*16+16,p.chunkZ()*16+16))continue;
            voxel.setMatrix4("uModel",new Matrix4f().translation(p.chunkX()*16,p.chunkY()*16,p.chunkZ()*16));entry.getValue().getMesh().render();
        }
        models.render(world,frustum,voxel);
        if(++face==6){glActiveTexture(GL_TEXTURE14);glBindTexture(GL_TEXTURE_CUBE_MAP,cube);glGenerateMipmap(GL_TEXTURE_CUBE_MAP);ready=true;face=-1;}
        glBindFramebuffer(GL_FRAMEBUFFER,0);
    }
    public void bind(ShaderProgram shader){
        glActiveTexture(GL_TEXTURE14);glBindTexture(GL_TEXTURE_CUBE_MAP,cube);shader.setInt("uReflectionProbe",14);shader.setInt("uProbeReady",ready?1:0);
        shader.setVector3("uProbeCenter",center.x,center.y,center.z);
    }
    public boolean ready(){return ready;}
    @Override public void close(){glDeleteTextures(cube);glDeleteFramebuffers(fbo);glDeleteRenderbuffers(depth);glDeleteVertexArrays(vao);voxel.close();sky.close();}
}
