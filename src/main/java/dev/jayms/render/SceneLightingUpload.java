package dev.jayms.render;

import dev.jayms.World;
import org.lwjgl.system.MemoryUtil;
import java.nio.*;
import static org.lwjgl.opengl.GL33.*;

/** One staged lighting replacement. GL 3.3 row transfers obey both bytes and elapsed time. */
final class SceneLightingUpload implements AutoCloseable {
    final LightVolume volume;
    SkyVisibilityScheduler.Prepared sky;
    final boolean replaceVolume,separateRoots;
    int width,height,depth,offset;
    private final World owner;
    private final LightingStores stores;
    private final WorldLighting.Capture volumeFence;
    private final long edits,models;
    private int atlas,irradiance,fineTexture,fineBuffer;
    private IntBuffer integers;
    private ByteBuffer pixels;
    private VoxelTransport.Staging transportUpload;
    private VoxelTransport transport;
    private int phase,y,z,fineOffset;
    private boolean allocated;
    private int[] skyData;
    long transportAllocationNanos,transportMipmapNanos;
    record Resources(int atlas,int irradiance,int fineTexture,int fineBuffer,VoxelTransport transport) {}
    SceneLightingUpload(World world,LightVolume volume,SkyVisibilityScheduler.Prepared sky,boolean replaceVolume) {
        this(world,volume,sky,replaceVolume,null);
    }
    SceneLightingUpload(World world,LightVolume volume,SkyVisibilityScheduler.Prepared sky,boolean replaceVolume,LightingStores stores) {
        this(world,volume,sky,replaceVolume,stores,null);
    }
    SceneLightingUpload(World world,LightVolume volume,SkyVisibilityScheduler.Prepared sky,boolean replaceVolume,LightingStores stores,WorldLighting.Capture volumeFence) {
        this(world,volume,sky,replaceVolume,stores,volumeFence,false);
    }
    SceneLightingUpload(World world,LightVolume volume,SkyVisibilityScheduler.Prepared sky,boolean replaceVolume,LightingStores stores,WorldLighting.Capture volumeFence,boolean separateRoots) {
        this.separateRoots=separateRoots;
        this.volumeFence=volumeFence;
        this.stores=stores;this.owner=world;this.volume=volume;this.sky=sky;this.replaceVolume=replaceVolume;
        edits=world.editsVersion();models=world.models().revision();skyData=sky==null?new int[0]:sky.data.data;
        int w=volume==null?64:volume.width,h=volume==null?64:volume.height,l=volume==null?0:volume.length;
        int limit=glGetInteger(GL_MAX_3D_TEXTURE_SIZE),d=l+(skyData.length+w*h-1)/(w*h);
        if(stores!=null){w=96;h=128;if(96+(WorldSkyVisibility.MAX_INTS+w*h-1)/(w*h)>limit){w=h=256;}d=l+(skyData.length+w*h-1)/(w*h);}
        if(d>limit){w=Math.max(w,256);h=Math.max(h,256);d=l+(skyData.length+w*h-1)/(w*h);}
        if(w>limit||h>limit||d>limit)throw new IllegalStateException("Sky visibility exceeds GL integer atlas capacity");
        width=w;height=h;depth=Math.max(1,d);offset=w*h*l;phase=replaceVolume?1:0;
        try {
            atlas=separateRoots&&replaceVolume?0:stores==null?glGenTextures():stores.texture("sky");integers=stores==null?MemoryUtil.memAllocInt(512*1024):stores.integers;
            if(replaceVolume){irradiance=stores==null?glGenTextures():stores.texture("irradiance");fineTexture=stores==null?glGenTextures():stores.texture("fine");fineBuffer=stores==null?glGenBuffers():stores.buffer();if(volume!=null)pixels=stores==null?MemoryUtil.memAlloc(2*1024*1024):stores.pixels;}
        }catch(RuntimeException e){close();throw e;}
    }
    boolean valid(World world){
        if(world!=owner||world.isClosed()||world.models().revision()!=models)return false;
        if(volumeFence!=null)return volumeFence.valid(world,volumeFence.x,volumeFence.z);
        return replaceVolume?world.editsVersion()==edits:sky==null||sky.valid(world);
    }
    boolean skyCurrent(World world){return sky==null||sky.valid(world);}
    void refreshSky(World world,SkyVisibilityScheduler.Prepared latest) {refreshSky(world,latest,false);}
    void refreshSky(World world,SkyVisibilityScheduler.Prepared latest,boolean coverageUpgrade) {
        if(separateRoots&&replaceVolume)return;
        if(latest==null||!latest.valid(world)||latest==sky)return;
        // Finish the independent volume first. A changed far column must not restart
        // tens of MB of already valid irradiance/transport data.
        if((phase==0||phase==4)&&skyCurrent(world)&&(!coverageUpgrade||replaceVolume))return;
        sky=latest;skyData=latest.data.data;
        int w=volume==null?64:volume.width,h=volume==null?64:volume.height,l=volume==null?0:volume.length;
        int limit=glGetInteger(GL_MAX_3D_TEXTURE_SIZE),d=l+(skyData.length+w*h-1)/(w*h);
        if(stores!=null){w=96;h=128;if(96+(WorldSkyVisibility.MAX_INTS+w*h-1)/(w*h)>limit){w=h=256;}d=l+(skyData.length+w*h-1)/(w*h);}
        if(d>limit){w=Math.max(w,256);h=Math.max(h,256);d=l+(skyData.length+w*h-1)/(w*h);}
        if(w>limit||h>limit||d>limit)throw new IllegalStateException("Sky visibility exceeds GL integer atlas capacity");
        width=w;height=h;depth=Math.max(1,d);offset=w*h*l;
        if(phase==0||phase==4){if(stores==null){glDeleteTextures(atlas);atlas=glGenTextures();}y=z=0;allocated=false;phase=0;}
    }
    private static void configure(){glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_WRAP_R,GL_CLAMP_TO_EDGE);}
    int pump(long deadline,int byteBudget) {return pump(deadline,byteBudget,64*1024);}
    int pump(long deadline,int byteBudget,int transferUnit) {
        int trace=dev.jayms.OwnerTrace.begin("lighting.pump");try{
        if(transferUnit<=0||transferUnit>LightingStores.PIXEL_UNIT_BYTES)throw new IllegalArgumentException("Lighting transfer unit");
        int bytes=0;
        while(phase<4&&System.nanoTime()<deadline) {
            if(phase==0) {
                if(separateRoots&&replaceVolume){phase=4;continue;}
                if(!skyCurrent(owner))break;
                int available=Math.min(transferUnit,byteBudget-bytes),planes=1;
                int rows=Math.min(height-y,available/(width*4));if(rows==0)break;
                if(y==0&&rows==height)planes=Math.min(depth-z,available/(width*height*4));
                int count=width*rows*planes;
                glActiveTexture(GL_TEXTURE4);glBindTexture(GL_TEXTURE_3D,atlas);
                if(!allocated){if(stores==null)glTexImage3D(GL_TEXTURE_3D,0,GL_R32I,width,height,depth,0,GL_RED_INTEGER,GL_INT,(IntBuffer)null);else stores.texture3D(atlas,0,GL_R32I,width,height,depth,GL_RED_INTEGER,true);configure();allocated=true;}
                integers.clear();integers.limit(count);MemoryUtil.memSet(MemoryUtil.memAddress(integers),0,count*4L);
                int l=volume==null?0:volume.length;
                if(volume==null||(width==volume.width&&height==volume.height)) {
                    int rootPlanes=Math.min(planes,Math.max(0,l-z)),rootCount=rootPlanes*rows*width;
                    if(rootCount>0)integers.put(volume.roots,(z*height+y)*width,rootCount);
                    int source=(z+rootPlanes-l)*width*height+y*width,n=Math.min(count-rootCount,Math.max(0,skyData.length-source));
                    if(n>0){integers.position(rootCount);integers.put(skyData,source,n);}
                } else for(int plane=0;plane<planes;plane++) {
                    if(z+plane<l){for(int row=0;row<rows&&y+row<volume.height;row++){integers.position((plane*rows+row)*width);integers.put(volume.roots,((z+plane)*volume.height+y+row)*volume.width,volume.width);}}
                    else{int source=(z+plane-l)*width*height+y*width,n=Math.min(width*rows,Math.max(0,skyData.length-source));if(n>0){integers.position(plane*rows*width);integers.put(skyData,source,n);}}
                }
                integers.position(0);integers.limit(count);if(stores==null)glTexSubImage3D(GL_TEXTURE_3D,0,0,y,z,width,rows,planes,GL_RED_INTEGER,GL_INT,integers);else if(!stores.pixels3D(0,0,y,z,width,rows,planes,GL_RED_INTEGER,GL_INT,integers,count*4))break;
                bytes+=count*4;y+=rows;if(y==height){y=0;z+=planes;if(z==depth){z=0;phase=4;allocated=false;}}
            } else if(phase==1) {
                glActiveTexture(GL_TEXTURE3);glBindTexture(GL_TEXTURE_3D,irradiance);
                if(volume==null){if(stores==null)glTexImage3D(GL_TEXTURE_3D,0,GL_RGBA8,1,1,1,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);else stores.texture3D(irradiance,0,GL_RGBA8,1,1,1,GL_RGBA,false);configure();phase=2;continue;}
                int available=Math.min(transferUnit,byteBudget-bytes),planes=1;
                int rows=Math.min(volume.height-y,available/(volume.width*4));if(rows==0)break;
                if(y==0&&rows==volume.height)planes=Math.min(volume.length-z,available/(volume.width*volume.height*4));
                int count=volume.width*rows*planes*4;
                if(!allocated){if(stores==null)glTexImage3D(GL_TEXTURE_3D,0,GL_RGBA8,volume.width,volume.height,volume.length,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);else stores.texture3D(irradiance,0,GL_RGBA8,volume.width,volume.height,volume.length,GL_RGBA,false);configure();allocated=true;}
                pixels.clear();pixels.put(volume.rgba,(z*volume.height+y)*volume.width*4,count).flip();
                if(stores==null)glTexSubImage3D(GL_TEXTURE_3D,0,0,y,z,volume.width,rows,planes,GL_RGBA,GL_UNSIGNED_BYTE,pixels);else if(!stores.pixels3D(0,0,y,z,volume.width,rows,planes,GL_RGBA,GL_UNSIGNED_BYTE,pixels,count))break;bytes+=count;y+=rows;
                if(y==volume.height){y=0;if((z+=planes)==volume.length){z=0;phase=2;allocated=false;}}
            } else if(phase==2) {
                int[] fine=volume==null?EMPTY:volume.fine;int roots=separateRoots&&volume!=null&&!volume.uniformRoots()?volume.roots.length:0,total=roots+fine.length;int count=Math.min(Math.min(transferUnit/4,total-fineOffset),(byteBudget-bytes)/4);
                if(count==0&&fineOffset<total)break;
                glActiveTexture(GL_TEXTURE5);glBindBuffer(GL_TEXTURE_BUFFER,fineBuffer);
                if(!allocated){if(stores==null)glBufferData(GL_TEXTURE_BUFFER,total*4L,GL_DYNAMIC_DRAW);else stores.bufferData(fineBuffer,total*4L);allocated=true;}
                integers.clear();int rootCount=Math.min(count,Math.max(0,roots-fineOffset));if(rootCount>0)integers.put(volume.roots,fineOffset,rootCount);if(rootCount<count)integers.put(fine,Math.max(0,fineOffset-roots),count-rootCount);integers.flip();glBufferSubData(GL_TEXTURE_BUFFER,fineOffset*4L,integers);bytes+=count*4;fineOffset+=count;
                if(fineOffset==total){glBindTexture(GL_TEXTURE_BUFFER,fineTexture);glTexBuffer(GL_TEXTURE_BUFFER,GL_R32I,fineBuffer);glBindBuffer(GL_TEXTURE_BUFFER,0);phase=3;allocated=false;}
            } else {
                if(volume==null){transport=new VoxelTransport();phase=0;continue;}
                if(transportUpload==null)transportUpload=new VoxelTransport.Staging(volume.transport,stores);
                bytes+=transportUpload.pump(deadline,byteBudget-bytes);
                transportAllocationNanos=transportUpload.allocationNanos;transportMipmapNanos=transportUpload.mipmapNanos;
                if(transportUpload.ready()){transport=transportUpload.take();transportUpload.close();transportUpload=null;phase=0;}else break;
            }
        }
        if(stores!=null)stores.flushPixels();glActiveTexture(GL_TEXTURE0);return bytes;

        }finally{dev.jayms.OwnerTrace.end(trace,byteBudget);}
    }
    private static final int[] EMPTY={0};
    boolean ready(){return phase==4;}
    Resources take(){if(!ready())throw new IllegalStateException("Lighting upload incomplete");var result=new Resources(atlas,irradiance,fineTexture,fineBuffer,transport);atlas=irradiance=fineTexture=fineBuffer=0;transport=null;return result;}
    long estimatedBytes(){return width*(long)height*depth*4+(replaceVolume&&volume!=null?volume.rgba.length+volume.fine.length*4L+VoxelTransport.bytes(volume.transport):0);}
    @Override public void close(){if(stores==null){if(atlas!=0)glDeleteTextures(atlas);if(irradiance!=0)glDeleteTextures(irradiance);if(fineTexture!=0)glDeleteTextures(fineTexture);if(fineBuffer!=0)glDeleteBuffers(fineBuffer);}else{stores.releaseTexture(atlas);stores.releaseTexture(irradiance);stores.releaseTexture(fineTexture);stores.releaseBuffer(fineBuffer);}atlas=irradiance=fineTexture=fineBuffer=0;if(transport!=null){transport.close();transport=null;}if(transportUpload!=null){transportUpload.close();transportUpload=null;}if(integers!=null){if(stores==null)MemoryUtil.memFree(integers);integers=null;}if(pixels!=null){if(stores==null)MemoryUtil.memFree(pixels);pixels=null;}}
}
