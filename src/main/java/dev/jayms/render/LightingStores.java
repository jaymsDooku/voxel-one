package dev.jayms.render;

import java.nio.*;
import java.util.*;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.opengl.GL33.*;

/** Context-owned stores. Two generations per role suffice for active and pending lighting.
 * Released stores and scratch memory are reused; sky depth/buffer capacity grow geometrically.
 */
final class LightingStores implements AutoCloseable {
    private record Shape(int internal,int width,int height,int depth) {}
    private static final class Texture {
        final int id=glGenTextures();final String role;final Map<Integer,Shape> levels=new HashMap<>();boolean borrowed;long fence;
        Texture(String role){this.role=role;}
    }
    private static final class Buffer {final int id=glGenBuffers();long capacity;boolean borrowed;long fence;}
    private static final String[] VOLUME_ROLES={"sky","irradiance","fine","radiance","distance","probe0","probe1","probe2"},SKY_ROLES={"sky"};
    private final Map<Integer,Texture> textures=new HashMap<>();
    private final Map<Integer,Buffer> buffers=new HashMap<>();
    final IntBuffer integers=MemoryUtil.memAllocInt(512*1024);
    final ByteBuffer pixels=MemoryUtil.memAlloc(2*1024*1024);
    long allocations,allocationNanos;
    private boolean closed;
    final boolean directPixels;
    long directCopies,mappedCopies;int largestPixelCopy;
    static final int PIXEL_UNIT_BYTES=256*1024,PIXEL_SLOTS=64;
    final int[] pixelBuffers=new int[PIXEL_SLOTS];
    private final long[] pixelFences=new long[PIXEL_SLOTS];
    private final ByteBuffer[] pixelMappings=new ByteBuffer[PIXEL_SLOTS];
    private int pixelCursor;private boolean pixelFlush;
    LightingStores(){this(softwareRenderer());}
    private static boolean softwareRenderer(){String renderer=glGetString(GL_RENDERER);return renderer!=null&&(renderer.toLowerCase(java.util.Locale.ROOT).contains("llvmpipe")||renderer.toLowerCase(java.util.Locale.ROOT).contains("softpipe"));}
    LightingStores(boolean directPixels){
        this.directPixels=directPixels;
        int prior=glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        try{for(int i=0;i<PIXEL_SLOTS;i++){
            pixelBuffers[i]=glGenBuffers();if(pixelBuffers[i]==0)throw new IllegalStateException("Pixel staging buffer unavailable");
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER,pixelBuffers[i]);long start=System.nanoTime();glBufferData(GL_PIXEL_UNPACK_BUFFER,PIXEL_UNIT_BYTES,GL_STREAM_DRAW);allocationNanos+=System.nanoTime()-start;allocations++;
        }}catch(RuntimeException failure){close();throw failure;}finally{glBindBuffer(GL_PIXEL_UNPACK_BUFFER,prior);}
    }
    /** Nonblocking context-owned upload lane. A buffer cannot be rewritten until its copy fence signals. */
    boolean pixels3D(int level,int x,int y,int z,int w,int h,int d,int format,int type,java.nio.Buffer data,int bytes){
        int trace=dev.jayms.OwnerTrace.begin("gl.pixels3D");try{
        if(closed)throw new IllegalStateException("Lighting stores closed");
        int stride=data instanceof ByteBuffer?1:data instanceof ShortBuffer?2:4;
        if(!data.isDirect()||bytes<0||bytes>PIXEL_UNIT_BYTES||data.remaining()*(long)stride!=bytes)throw new IllegalArgumentException("Pixel staging byte range");
        if(directPixels){
            // Software renderers copy client data before this call returns. Avoid an
            // extra PBO copy/fence lane; the same bounded transfer and formats apply.
            int prior=glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);
            try{
                if(data instanceof ByteBuffer b)glTexSubImage3D(GL_TEXTURE_3D,level,x,y,z,w,h,d,format,type,b);
                else if(data instanceof ShortBuffer b)glTexSubImage3D(GL_TEXTURE_3D,level,x,y,z,w,h,d,format,type,b);
                else if(data instanceof IntBuffer b)glTexSubImage3D(GL_TEXTURE_3D,level,x,y,z,w,h,d,format,type,b);
                else if(data instanceof FloatBuffer b)glTexSubImage3D(GL_TEXTURE_3D,level,x,y,z,w,h,d,format,type,b);
                else throw new IllegalArgumentException("Pixel staging buffer type");
                directCopies++;largestPixelCopy=Math.max(largestPixelCopy,bytes);return true;
            }finally{glBindBuffer(GL_PIXEL_UNPACK_BUFFER,prior);}
        }
        int slot=-1;
        for(int i=0;i<PIXEL_SLOTS;i++){
            int candidate=(pixelCursor+i)%PIXEL_SLOTS;
            if(pixelFences[candidate]!=0){int state=glClientWaitSync(pixelFences[candidate],0,0);if(state==GL_WAIT_FAILED)throw new IllegalStateException("Pixel staging fence failed");if(state!=GL_ALREADY_SIGNALED&&state!=GL_CONDITION_SATISFIED)continue;glDeleteSync(pixelFences[candidate]);pixelFences[candidate]=0;}
            slot=candidate;break;
        }
        if(slot<0)return false;pixelCursor=(slot+1)%PIXEL_SLOTS;
        int prior=glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);glBindBuffer(GL_PIXEL_UNPACK_BUFFER,pixelBuffers[slot]);
        try{
            // The slot fence has signaled. Mapping this bounded range cannot race an earlier copy.
            if(!data.isDirect())throw new IllegalArgumentException("Pixel staging requires direct memory");
            long address;
            if(data instanceof ByteBuffer b)address=MemoryUtil.memAddress(b);
            else if(data instanceof ShortBuffer b)address=MemoryUtil.memAddress(b);
            else if(data instanceof IntBuffer b)address=MemoryUtil.memAddress(b);
            else if(data instanceof FloatBuffer b)address=MemoryUtil.memAddress(b);
            else throw new IllegalArgumentException("Pixel staging buffer type");
            ByteBuffer mapped=glMapBufferRange(GL_PIXEL_UNPACK_BUFFER,0,PIXEL_UNIT_BYTES,
                    GL_MAP_WRITE_BIT|GL_MAP_UNSYNCHRONIZED_BIT,pixelMappings[slot]);
            if(mapped==null)throw new IllegalStateException("Pixel staging mapping unavailable");
            pixelMappings[slot]=mapped;
            try{MemoryUtil.memCopy(address,MemoryUtil.memAddress(mapped),bytes);}
            finally{if(!glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER))throw new IllegalStateException("Pixel staging mapping invalidated");}
            glTexSubImage3D(GL_TEXTURE_3D,level,x,y,z,w,h,d,format,type,0L);
            pixelFences[slot]=glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE,0);if(pixelFences[slot]==0)throw new IllegalStateException("Pixel staging fence unavailable");pixelFlush=true;mappedCopies++;largestPixelCopy=Math.max(largestPixelCopy,bytes);return true;
        }finally{glBindBuffer(GL_PIXEL_UNPACK_BUFFER,prior);}

        }finally{dev.jayms.OwnerTrace.end(trace,bytes);}
    }
    void flushPixels(){
        int trace=dev.jayms.OwnerTrace.begin("gl.flush");try{if(pixelFlush){glFlush();pixelFlush=false;}
        }finally{dev.jayms.OwnerTrace.end(trace,0);}
    }
    int texture(String role){
        if(closed)throw new IllegalStateException("Lighting stores closed");
        for(var t:textures.values())if(t.role.equals(role)&&!t.borrowed&&ready(t)){t.borrowed=true;return t.id;}
        long count=textures.values().stream().filter(t->t.role.equals(role)).count();
        if(count>=2)throw new IllegalStateException("Lighting texture generations exceeded: "+role);
        var t=new Texture(role);t.borrowed=true;textures.put(t.id,t);return t.id;
    }
    int buffer(){if(closed)throw new IllegalStateException("Lighting stores closed");for(var b:buffers.values())if(!b.borrowed&&ready(b)){b.borrowed=true;return b.id;}if(buffers.size()>=2)throw new IllegalStateException("Lighting buffer generations exceeded");var b=new Buffer();b.borrowed=true;buffers.put(b.id,b);return b.id;}
    private boolean signaled(long fence){if(fence==0)return true;int status=glClientWaitSync(fence,0,0);if(status==GL_WAIT_FAILED)throw new IllegalStateException("Lighting retirement fence failed");return status==GL_ALREADY_SIGNALED||status==GL_CONDITION_SATISFIED;}
    private boolean ready(Texture t){if(!signaled(t.fence))return false;if(t.fence!=0){glDeleteSync(t.fence);t.fence=0;}return true;}
    private boolean ready(Buffer b){if(!signaled(b.fence))return false;if(b.fence!=0){glDeleteSync(b.fence);b.fence=0;}return true;}
    /** Poll only. Never wait on a store still referenced by queued GPU reads. */
    boolean available(boolean volume){return available(volume,true);}
    boolean available(boolean volume,boolean atlas){
        String[] roles=volume?VOLUME_ROLES:SKY_ROLES;
        for(String role:roles){if(!atlas&&role.equals("sky"))continue;int count=0;boolean free=false;for(var t:textures.values())if(t.role.equals(role)){count++;if(!t.borrowed&&ready(t))free=true;}if(!free&&count>=2)return false;}
        if(volume){boolean free=false;for(var b:buffers.values())if(!b.borrowed&&ready(b))free=true;if(!free&&buffers.size()>=2)return false;}
        return true;
    }
    void texture3D(int id,int level,int internal,int w,int h,int d,int format,boolean growDepth){
        var texture=textures.get(id);var old=texture.levels.get(level);
        if(old!=null&&old.internal==internal&&old.width==w&&old.height==h&&(growDepth?old.depth>=d:old.depth==d))return;
        int capacity=d;
        if(growDepth){int limit=glGetInteger(GL_MAX_3D_TEXTURE_SIZE);capacity=Math.min(limit,Integer.highestOneBit(Math.max(1,d-1))<<1);capacity=Math.max(d,capacity);}
        long start=System.nanoTime();glTexImage3D(GL_TEXTURE_3D,level,internal,w,h,capacity,0,format,internal==GL_R32I?GL_INT:GL_FLOAT,(FloatBuffer)null);
        allocationNanos+=System.nanoTime()-start;allocations++;
        texture.levels.put(level,new Shape(internal,w,h,capacity));
    }
    void bufferData(int id,long bytes){
        var b=buffers.get(id);if(b.capacity>=bytes)return;
        long capacity=Math.max(65536,Long.highestOneBit(Math.max(1,bytes-1))<<1);
        long start=System.nanoTime();glBufferData(GL_TEXTURE_BUFFER,capacity,GL_DYNAMIC_DRAW);allocationNanos+=System.nanoTime()-start;allocations++;b.capacity=capacity;
    }
    void releaseTexture(int id){if(id==0)return;var t=textures.get(id);if(t==null)glDeleteTextures(id);else{if(!t.borrowed)throw new IllegalStateException("Lighting texture released twice");t.borrowed=false;t.fence=glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE,0);}}
    void releaseBuffer(int id){if(id==0)return;var b=buffers.get(id);if(b==null)glDeleteBuffers(id);else{if(!b.borrowed)throw new IllegalStateException("Lighting buffer released twice");b.borrowed=false;b.fence=glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE,0);}}
    /** Explicit startup preparation: no full store allocations for normal terrain arrivals. */
    void warmDefaults(){
        var borrowed=new ArrayList<Integer>();var borrowedBuffers=new ArrayList<Integer>();
        for(int generation=0;generation<2;generation++){
            int atlas=texture("sky");borrowed.add(atlas);glBindTexture(GL_TEXTURE_3D,atlas);
            int aw=96,ah=128;if(96+(WorldSkyVisibility.MAX_INTS+aw*ah-1)/(aw*ah)>glGetInteger(GL_MAX_3D_TEXTURE_SIZE)){aw=ah=256;}texture3D(atlas,0,GL_R32I,aw,ah,96+(WorldSkyVisibility.MAX_INTS+aw*ah-1)/(aw*ah),GL_RED_INTEGER,true);
            int irradiance=texture("irradiance");borrowed.add(irradiance);glBindTexture(GL_TEXTURE_3D,irradiance);texture3D(irradiance,0,GL_RGBA8,96,128,96,GL_RGBA,false);
            int fine=texture("fine");borrowed.add(fine);int buffer=buffer();borrowedBuffers.add(buffer);glBindBuffer(GL_TEXTURE_BUFFER,buffer);bufferData(buffer,4L*Math.min(1_048_576+96*128*96,glGetInteger(GL_MAX_TEXTURE_BUFFER_SIZE)));glBindTexture(GL_TEXTURE_BUFFER,fine);glTexBuffer(GL_TEXTURE_BUFFER,GL_R32I,buffer);
            int radiance=texture("radiance");borrowed.add(radiance);glBindTexture(GL_TEXTURE_3D,radiance);
            int w=96,h=128,l=96,level=0;while(true){texture3D(radiance,level++,GL_RGBA16F,w,h,l,GL_RGBA,false);if(w==1&&h==1&&l==1)break;w=Math.max(1,w/2);h=Math.max(1,h/2);l=Math.max(1,l/2);}
            int distance=texture("distance");borrowed.add(distance);glBindTexture(GL_TEXTURE_3D,distance);texture3D(distance,0,GL_R16F,96,128,96,GL_RED,false);
            for(int i=0;i<3;i++){int spacing=8<<i,angles=16<<(2*i);int probe=texture("probe"+i);borrowed.add(probe);glBindTexture(GL_TEXTURE_3D,probe);texture3D(probe,0,GL_RGBA16F,(96+spacing-1)/spacing,(128+spacing-1)/spacing,((96+spacing-1)/spacing)*angles,GL_RGBA,false);}
        }
        for(int id:borrowed)releaseTexture(id);for(int id:borrowedBuffers)releaseBuffer(id);
        glBindBuffer(GL_TEXTURE_BUFFER,0);glActiveTexture(GL_TEXTURE0);
    }
    long retainedBytes(){long bytes=(long)PIXEL_SLOTS*PIXEL_UNIT_BYTES;for(var t:textures.values())for(var s:t.levels.values()){int stride=s.internal==GL_RGBA16F?8:s.internal==GL_R16F?2:4;bytes+=(long)s.width*s.height*s.depth*stride;}for(var b:buffers.values())bytes+=b.capacity;return bytes;}
    @Override public void close(){if(closed)return;closed=true;for(int i=0;i<PIXEL_SLOTS;i++){if(pixelFences[i]!=0){glDeleteSync(pixelFences[i]);pixelFences[i]=0;}if(pixelBuffers[i]!=0){glDeleteBuffers(pixelBuffers[i]);pixelBuffers[i]=0;}}for(var t:textures.values()){if(t.fence!=0)glDeleteSync(t.fence);glDeleteTextures(t.id);}for(var b:buffers.values()){if(b.fence!=0)glDeleteSync(b.fence);glDeleteBuffers(b.id);}textures.clear();buffers.clear();MemoryUtil.memFree(integers);MemoryUtil.memFree(pixels);}
}
