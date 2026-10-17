package dev.jayms.render;

import dev.jayms.ShaderProgram;
import static org.lwjgl.opengl.GL33.*;

/** GPU views of worker-built radiance, SDF and cascade probes; updates only on accepted bakes. */
public final class VoxelTransport implements AutoCloseable {
    private final int radiance=glGenTextures(),distance=glGenTextures();
    private final int[] probes={glGenTextures(),glGenTextures(),glGenTextures()};
    private boolean ready;private long estimatedBytes;
    static long bytes(TransportField f){long n=(long)f.width*f.height*f.length,total=n*10+n*8/7;for(var p:f.probes)total+=p.length*2L;return total;}
    public long estimatedBytes(){return estimatedBytes;}
    private final java.util.Map<Integer,int[]> allocated=new java.util.HashMap<>();
    public void upload(TransportField field){
        estimatedBytes=bytes(field);
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
    /** Detached GPU replacement: rows are uploaded under the shared byte/time allowance. */
    static final class Staging implements AutoCloseable {
        private record Part(int id,int internal,int format,int w,int h,int l,int channels,float[] data) {}
        private VoxelTransport target=new VoxelTransport();
        private final Part[] parts;
        private int part,y,z;private boolean allocated;
        private java.nio.FloatBuffer scratch;
        Staging(TransportField f) {
            target.estimatedBytes=bytes(f);
            parts=new Part[5];
            parts[0]=new Part(target.radiance,GL_RGBA16F,GL_RGBA,f.width,f.height,f.length,4,f.radiance);
            parts[1]=new Part(target.distance,GL_R16F,GL_RED,f.width,f.height,f.length,1,f.distance);
            for(int i=0;i<3;i++){int[] d=f.probeSize[i];parts[i+2]=new Part(target.probes[i],GL_RGBA16F,GL_RGBA,d[0],d[1],d[2],4,f.probes[i]);}
            int max=0;for(var p:parts)max=Math.max(max,p.w*Math.min(32,p.h)*p.channels);
            scratch=org.lwjgl.system.MemoryUtil.memAllocFloat(Math.max(max,512*1024));
        }
        int pump(long deadline,int byteBudget) {
            int bytes=0;
            while(part<parts.length&&System.nanoTime()<deadline) {
                Part p=parts[part];int available=Math.min(2*1024*1024,byteBudget-bytes),planes=1;
                int rows=Math.min(p.h-y,available/(p.w*p.channels*4));if(rows==0)break;
                if(y==0&&rows==p.h)planes=Math.min(p.l-z,available/(p.w*p.h*p.channels*4));
                int count=p.w*rows*p.channels*planes;
                if(count*4>byteBudget-bytes)break;
                glActiveTexture(GL_TEXTURE6);glBindTexture(GL_TEXTURE_3D,p.id);
                if(!allocated){glTexImage3D(GL_TEXTURE_3D,0,p.internal,p.w,p.h,p.l,0,p.format,GL_FLOAT,(java.nio.FloatBuffer)null);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_WRAP_R,GL_CLAMP_TO_EDGE);allocated=true;}
                scratch.clear();scratch.put(p.data,(z*p.h+y)*p.w*p.channels,count).flip();
                glTexSubImage3D(GL_TEXTURE_3D,0,0,y,z,p.w,rows,planes,p.format,GL_FLOAT,scratch);bytes+=count*4;y+=rows;
                if(y==p.h){y=0;z+=planes;if(z==p.l){if(part==0){glGenerateMipmap(GL_TEXTURE_3D);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MIN_FILTER,GL_LINEAR_MIPMAP_LINEAR);}part++;z=0;allocated=false;}}
            }
            return bytes;
        }
        boolean ready(){return part==parts.length;}
        VoxelTransport take(){if(!ready())throw new IllegalStateException("Transport upload incomplete");var value=target;target=null;value.ready=true;return value;}
        @Override public void close(){if(target!=null){target.close();target=null;}if(scratch!=null){org.lwjgl.system.MemoryUtil.memFree(scratch);scratch=null;}}
    }
    public void bind(ShaderProgram shader){
        shader.setInt("uTransportReady",ready?1:0);
        glActiveTexture(GL_TEXTURE6);glBindTexture(GL_TEXTURE_3D,radiance);shader.setInt("uVoxelRadiance",6);
        glActiveTexture(GL_TEXTURE7);glBindTexture(GL_TEXTURE_3D,distance);shader.setInt("uDistanceField",7);
        for(int i=0;i<3;i++){glActiveTexture(GL_TEXTURE8+i);glBindTexture(GL_TEXTURE_3D,probes[i]);shader.setInt("uCascadeProbes["+i+"]",8+i);}
    }
    @Override public void close(){glDeleteTextures(radiance);glDeleteTextures(distance);for(int p:probes)glDeleteTextures(p);}
}
