package dev.jayms.render;

import dev.jayms.ShaderProgram;
import static org.lwjgl.opengl.GL33.*;

/** GPU views of worker-built radiance, SDF and cascade probes; updates only on accepted bakes. */
public final class VoxelTransport implements AutoCloseable {
    private final int radiance=glGenTextures(),distance=glGenTextures();
    private final int[] probes={glGenTextures(),glGenTextures(),glGenTextures()};
    private boolean ready;
    private final java.util.Map<Integer,int[]> allocated=new java.util.HashMap<>();
    public void upload(TransportField field){
        upload(radiance,GL_RGBA16F,GL_RGBA,field.width,field.height,field.length,field.radiance);
        glGenerateMipmap(GL_TEXTURE_3D);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MIN_FILTER,GL_LINEAR_MIPMAP_LINEAR);
        upload(distance,GL_R16F,GL_RED,field.width,field.height,field.length,field.distance);
        for(int i=0;i<3;i++){int[] d=field.probeSize[i];upload(probes[i],GL_RGBA16F,GL_RGBA,d[0],d[1],d[2],field.probes[i]);}
        ready=true;
    }
    private void upload(int id,int internal,int format,int w,int h,int l,float[] data){
        glActiveTexture(GL_TEXTURE6);glBindTexture(GL_TEXTURE_3D,id);
        int[] previous=allocated.get(id);
        if(previous!=null&&previous[0]==w&&previous[1]==h&&previous[2]==l)glTexSubImage3D(GL_TEXTURE_3D,0,0,0,0,w,h,l,format,GL_FLOAT,data);
        else{glTexImage3D(GL_TEXTURE_3D,0,internal,w,h,l,0,format,GL_FLOAT,data);allocated.put(id,new int[]{w,h,l});}
        glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_WRAP_R,GL_CLAMP_TO_EDGE);
    }
    public void bind(ShaderProgram shader){
        shader.setInt("uTransportReady",ready?1:0);
        glActiveTexture(GL_TEXTURE6);glBindTexture(GL_TEXTURE_3D,radiance);shader.setInt("uVoxelRadiance",6);
        glActiveTexture(GL_TEXTURE7);glBindTexture(GL_TEXTURE_3D,distance);shader.setInt("uDistanceField",7);
        for(int i=0;i<3;i++){glActiveTexture(GL_TEXTURE8+i);glBindTexture(GL_TEXTURE_3D,probes[i]);shader.setInt("uCascadeProbes["+i+"]",8+i);}
    }
    @Override public void close(){glDeleteTextures(radiance);glDeleteTextures(distance);for(int p:probes)glDeleteTextures(p);}
}
