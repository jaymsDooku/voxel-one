package dev.jayms.render;

import dev.jayms.ShaderProgram;
import static org.lwjgl.opengl.GL33.*;

/** GPU views of worker-built radiance, SDF and cascade probes; updates only on accepted bakes. */
public final class VoxelTransport implements AutoCloseable {
    private final int radiance,distance;
    private final int[] probes=new int[3];
    private final LightingStores stores;
    public VoxelTransport(){this(null);}
    private VoxelTransport(LightingStores stores){this.stores=stores;radiance=stores==null?glGenTextures():stores.texture("radiance");distance=stores==null?glGenTextures():stores.texture("distance");for(int i=0;i<3;i++)probes[i]=stores==null?glGenTextures():stores.texture("probe"+i);}

    private boolean ready,closed;private long estimatedBytes;
    static long bytes(TransportField f){long n=(long)f.width*f.height*f.length,total=n*10+n*8/7;for(var p:f.probes)total+=p.length*2L;return total;}
    public long estimatedBytes(){return estimatedBytes;}
    private final java.util.Map<Long,int[]> allocated=new java.util.HashMap<>();
    public void upload(TransportField field){
        estimatedBytes=bytes(field);
        int w=field.width,h=field.height,l=field.length;
        for(int level=0;level<field.radianceMips.length;level++){upload(radiance,level,GL_RGBA16F,GL_RGBA,w,h,l,field.radianceMips[level]);w=Math.max(1,w/2);h=Math.max(1,h/2);l=Math.max(1,l/2);}
        glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MIN_FILTER,GL_LINEAR_MIPMAP_LINEAR);
        upload(distance,GL_R16F,GL_RED,field.width,field.height,field.length,field.distance);
        for(int i=0;i<3;i++){int[] d=field.probeSize[i];upload(probes[i],GL_RGBA16F,GL_RGBA,d[0],d[1],d[2],field.probes[i]);}
        ready=true;
    }
    private void upload(int id,int internal,int format,int w,int h,int l,float[] data){upload(id,0,internal,format,w,h,l,data);}
    private void upload(int id,int level,int internal,int format,int w,int h,int l,float[] data){
        glActiveTexture(GL_TEXTURE6);glBindTexture(GL_TEXTURE_3D,id);
        long key=((long)id<<32)|level;int[] previous=allocated.get(key);
        if(stores!=null){stores.texture3D(id,level,internal,w,h,l,format,false);glTexSubImage3D(GL_TEXTURE_3D,level,0,0,0,w,h,l,format,GL_FLOAT,data);}
        else
        if(previous!=null&&previous[0]==w&&previous[1]==h&&previous[2]==l)glTexSubImage3D(GL_TEXTURE_3D,level,0,0,0,w,h,l,format,GL_FLOAT,data);
        else{glTexImage3D(GL_TEXTURE_3D,level,internal,w,h,l,0,format,GL_FLOAT,data);allocated.put(key,new int[]{w,h,l});}
        glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_WRAP_R,GL_CLAMP_TO_EDGE);
    }
    /** Detached GPU replacement: rows are uploaded under the shared byte/time allowance. */
    static final class Staging implements AutoCloseable {
        private record Part(int id,int level,int internal,int format,int w,int h,int l,int channels,short[] data) {}
        private VoxelTransport target;
        private final LightingStores stores;
        private final Part[] parts;
        private int part,y,z;private boolean allocated;
        long allocationNanos,mipmapNanos;
        private java.nio.ShortBuffer scratch;
        Staging(TransportField f){this(f,null);}
        Staging(TransportField f,LightingStores stores) {
            this.stores=stores;target=new VoxelTransport(stores);
            target.estimatedBytes=bytes(f);
            var transfers=new java.util.ArrayList<Part>();
            int w=f.width,h=f.height,l=f.length;
            for(int level=0;level<f.radianceMips.length;level++){
                transfers.add(new Part(target.radiance,level,GL_RGBA16F,GL_RGBA,w,h,l,4,f.halfRadianceMips[level]));
                w=Math.max(1,w/2);h=Math.max(1,h/2);l=Math.max(1,l/2);
            }
            transfers.add(new Part(target.distance,0,GL_R16F,GL_RED,f.width,f.height,f.length,1,f.halfDistance));
            for(int i=0;i<3;i++){int[] d=f.probeSize[i];transfers.add(new Part(target.probes[i],0,GL_RGBA16F,GL_RGBA,d[0],d[1],d[2],4,f.halfProbes[i]));}
            parts=transfers.toArray(Part[]::new);
            int max=0;for(var p:parts)max=Math.max(max,p.w*Math.min(32,p.h)*p.channels);
            scratch=stores==null?org.lwjgl.system.MemoryUtil.memAllocShort(Math.max(max,LightingStores.PIXEL_UNIT_BYTES/2)):stores.pixels.duplicate().clear().order(java.nio.ByteOrder.nativeOrder()).asShortBuffer();
        }
        int pump(long deadline,int byteBudget) {
            int bytes=0;
            while(part<parts.length&&System.nanoTime()<deadline) {
                Part p=parts[part];int available=Math.min(64*1024,byteBudget-bytes),planes=1;
                int rows=Math.min(p.h-y,available/(p.w*p.channels*2));if(rows==0)break;
                if(y==0&&rows==p.h)planes=Math.min(p.l-z,available/(p.w*p.h*p.channels*2));
                int count=p.w*rows*p.channels*planes;
                if(count*2>byteBudget-bytes)break;
                glActiveTexture(GL_TEXTURE6);glBindTexture(GL_TEXTURE_3D,p.id);
                if(!allocated){long started=System.nanoTime();if(stores==null)glTexImage3D(GL_TEXTURE_3D,p.level,p.internal,p.w,p.h,p.l,0,p.format,GL_FLOAT,(java.nio.FloatBuffer)null);else stores.texture3D(p.id,p.level,p.internal,p.w,p.h,p.l,p.format,false);allocationNanos+=System.nanoTime()-started;glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_WRAP_R,GL_CLAMP_TO_EDGE);allocated=true;}
                scratch.clear();scratch.put(p.data,(z*p.h+y)*p.w*p.channels,count).flip();
                if(stores==null)glTexSubImage3D(GL_TEXTURE_3D,p.level,0,y,z,p.w,rows,planes,p.format,GL_HALF_FLOAT,scratch);else if(!stores.pixels3D(p.level,0,y,z,p.w,rows,planes,p.format,GL_HALF_FLOAT,scratch,count*2))break;bytes+=count*2;y+=rows;
                if(y==p.h){y=0;z+=planes;if(z==p.l){if(p.id==target.radiance&&p.level==fLevels()-1)glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MIN_FILTER,GL_LINEAR_MIPMAP_LINEAR);part++;z=0;allocated=false;}}
            }
            if(stores!=null)stores.flushPixels();return bytes;
        }
        private int fLevels(){int n=0;for(var p:parts)if(p.id==target.radiance)n++;return n;}
        boolean ready(){return part==parts.length;}
        VoxelTransport take(){if(!ready())throw new IllegalStateException("Transport upload incomplete");var value=target;target=null;value.ready=true;return value;}
        @Override public void close(){if(target!=null){target.close();target=null;}if(scratch!=null){if(stores==null)org.lwjgl.system.MemoryUtil.memFree(scratch);scratch=null;}}
    }
    public void bind(ShaderProgram shader){
        shader.setInt("uTransportReady",ready?1:0);
        glActiveTexture(GL_TEXTURE6);glBindTexture(GL_TEXTURE_3D,radiance);shader.setInt("uVoxelRadiance",6);
        glActiveTexture(GL_TEXTURE7);glBindTexture(GL_TEXTURE_3D,distance);shader.setInt("uDistanceField",7);
        for(int i=0;i<3;i++){glActiveTexture(GL_TEXTURE8+i);glBindTexture(GL_TEXTURE_3D,probes[i]);shader.setInt("uCascadeProbes["+i+"]",8+i);}
    }
    @Override public void close(){if(closed)return;closed=true;if(stores==null){glDeleteTextures(radiance);glDeleteTextures(distance);for(int p:probes)glDeleteTextures(p);}else{stores.releaseTexture(radiance);stores.releaseTexture(distance);for(int p:probes)stores.releaseTexture(p);}}
}
