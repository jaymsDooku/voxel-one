package dev.jayms.render;

import dev.jayms.*;
import dev.jayms.net.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** GL 3.3 byte-exact checks of staged sky/fine/irradiance transfers and cancellation. */
public class StreamingUploadSmoke {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    static SkyVisibilityScheduler.Prepared sky(SkyVisibilityScheduler scheduler,World world)throws Exception {
        long end=System.nanoTime()+10_000_000_000L;
        while(System.nanoTime()<end){var result=scheduler.update(world,1_000_000);if(result!=null&&result.valid(world))return result;Thread.sleep(1);}
        throw new AssertionError("Sky preparation timeout");
    }
    static int id(VoxelTransport transport,String field)throws Exception {var f=VoxelTransport.class.getDeclaredField(field);f.setAccessible(true);return f.getInt(transport);}
    static void floats(int id,int format,float[] expected)throws Exception {floats(id,0,format,expected);}
    static void floats(int id,int level,int format,float[] expected)throws Exception {
        var data=MemoryUtil.memAllocFloat(expected.length);
        try{glBindTexture(GL_TEXTURE_3D,id);glGetTexImage(GL_TEXTURE_3D,level,format,GL_FLOAT,data);for(int i=0;i<expected.length;i++)check(Math.abs(data.get(i)-expected[i])<=.02f,"Transport half-float data matches worker output");}finally{MemoryUtil.memFree(data);}
    }
    public static void main(String[] args)throws Exception {
        Path out=Path.of(args[0]);Files.createDirectories(out);long handle=0;
        try {
            check(glfwInit(),"GLFW init");glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
            handle=glfwCreateWindow(320,240,"Voxel One staged upload integration",0,0);check(handle!=0,"GL 3.3 window");glfwMakeContextCurrent(handle);GL.createCapabilities();glfwSwapInterval(0);
            try(var world=new World(42);var scheduler=new SkyVisibilityScheduler()) {
                world.addChunk(new ChunkPos(0,0,0),new Chunk());
                for(int x=0;x<8;x++)for(int z=0;z<8;z++)world.setBlock(x,0,z,Blocks.STONE);
                world.apply(new Protocol.Edit(1,1,1,Blocks.LED,4,0,0,0).withColor(0x20a0ff));
                var prepared=sky(scheduler,world);
                var volume=LightVolume.bake(0,0,0,8,8,8,1,new LightVolume.Sampler(){
                    public int value(int x,int y,int z){return world.cell(x,y,z).uniform(0,0,0,16);}
                    public dev.jayms.net.model.SparseVoxelOctree detail(int x,int y,int z){return world.cell(x,y,z);}
                    public float skyVisibility(float x,float y,float z){return prepared.data.sampleKnown(x,y,z,1);}
                },true);
                int pumps=0,maxBytes=0;long maxNanos=0,totalBytes=0;
                SceneLightingUpload.Resources resources;
                long transportAllocationNanos=0,transportMipmapNanos=0;
                try(var upload=new SceneLightingUpload(world,volume,prepared,true)) {
                    while(!upload.ready()&&pumps<2048){long start=System.nanoTime();int bytes=upload.pump(start+500_000L,4096);maxNanos=Math.max(maxNanos,System.nanoTime()-start);check(bytes<=4096,"Per-pump byte allowance");maxBytes=Math.max(maxBytes,bytes);totalBytes+=bytes;pumps++;glfwPollEvents();check(glGetError()==GL_NO_ERROR,"No GL upload error");}
                    check(upload.ready(),"Small byte budgets still make progress");check(pumps>1,"Upload is split across owner ticks");check(upload.valid(world),"Current result fence");transportAllocationNanos=upload.transportAllocationNanos;transportMipmapNanos=upload.transportMipmapNanos;resources=upload.take();
                    int total=upload.width*upload.height*upload.depth;var ints=MemoryUtil.memAllocInt(total);
                    try{glBindTexture(GL_TEXTURE_3D,resources.atlas());glGetTexImage(GL_TEXTURE_3D,0,GL_RED_INTEGER,GL_INT,ints);for(int i=0;i<volume.roots.length;i++)check(ints.get(i)==volume.roots[i],"Exact fine root atlas");for(int i=0;i<prepared.data.data.length;i++)check(ints.get(upload.offset+i)==prepared.data.data[i],"Exact packed sky atlas");}finally{MemoryUtil.memFree(ints);}
                    var rgba=MemoryUtil.memAlloc(volume.rgba.length);
                    try{glBindTexture(GL_TEXTURE_3D,resources.irradiance());glGetTexImage(GL_TEXTURE_3D,0,GL_RGBA,GL_UNSIGNED_BYTE,rgba);byte[] actual=new byte[volume.rgba.length];rgba.get(actual);check(Arrays.equals(actual,volume.rgba),"Exact irradiance bytes");}finally{MemoryUtil.memFree(rgba);}
                    ints=MemoryUtil.memAllocInt(volume.fine.length);
                    try{glBindBuffer(GL_TEXTURE_BUFFER,resources.fineBuffer());glGetBufferSubData(GL_TEXTURE_BUFFER,0,ints);for(int i=0;i<volume.fine.length;i++)check(ints.get(i)==volume.fine[i],"Exact packed fine nodes");glBindBuffer(GL_TEXTURE_BUFFER,0);}finally{MemoryUtil.memFree(ints);}
                }
                for(int level=0;level<volume.transport.radianceMips.length;level++)floats(id(resources.transport(),"radiance"),level,GL_RGBA,volume.transport.radianceMips[level]);
                floats(id(resources.transport(),"distance"),GL_RED,volume.transport.distance);
                var probeField=VoxelTransport.class.getDeclaredField("probes");probeField.setAccessible(true);int[] probeIds=(int[])probeField.get(resources.transport());
                for(int i=0;i<3;i++)floats(probeIds[i],GL_RGBA,volume.transport.probes[i]);
                // Membership may change while a large valid volume is still transferring.
                SkyVisibilityScheduler.Prepared latest;
                try(var refreshed=new SceneLightingUpload(world,volume,prepared,true)) {
                    refreshed.pump(System.nanoTime()+500_000L,4096);world.unloadChunk(new ChunkPos(0,0,0));
                    check(refreshed.valid(world)&&!refreshed.skyCurrent(world),"Unloading invalidates sky without restarting unchanged authoritative volume");
                    latest=sky(scheduler,world);refreshed.refreshSky(world,latest);
                    int n=0;while(!refreshed.ready()&&n++<2048)refreshed.pump(System.nanoTime()+500_000L,4096);
                    check(refreshed.ready()&&refreshed.skyCurrent(world),"Refresh completes after membership change");
                    var data=refreshed.take();var packed=MemoryUtil.memAllocInt(refreshed.width*refreshed.height*refreshed.depth);
                    try{glBindTexture(GL_TEXTURE_3D,data.atlas());glGetTexImage(GL_TEXTURE_3D,0,GL_RED_INTEGER,GL_INT,packed);for(int i=0;i<latest.data.data.length;i++)check(packed.get(refreshed.offset+i)==latest.data.data[i],"Refreshed sky is exact");}finally{MemoryUtil.memFree(packed);}
                    glDeleteTextures(data.atlas());glDeleteTextures(data.irradiance());glDeleteTextures(data.fineTexture());glDeleteBuffers(data.fineBuffer());data.transport().close();
                }
                try(var skyOnly=new SceneLightingUpload(world,volume,latest,false)) {
                    int n=0;while(!skyOnly.ready()&&n++<2048)skyOnly.pump(System.nanoTime()+500_000L,4096);
                    check(skyOnly.ready(),"Sky-only replacement completes");var data=skyOnly.take();check(data.irradiance()==0&&data.transport()==null,"Sky-only update keeps existing volume resources");glDeleteTextures(data.atlas());check(glIsTexture(resources.irradiance()),"Existing irradiance retained");
                }
                try(var cancelled=new SceneLightingUpload(world,volume,latest,true)) {
                    cancelled.pump(System.nanoTime()+500_000L,4096);world.setBlock(2,1,1,Blocks.BRICKS);check(!cancelled.valid(world),"Edit invalidates in-progress lighting upload");
                }
                glDeleteTextures(resources.atlas());glDeleteTextures(resources.irradiance());glDeleteTextures(resources.fineTexture());glDeleteBuffers(resources.fineBuffer());resources.transport().close();
                long pooledMaxNanos=0,pooledWarmMaxNanos=0,pooledAllocations=0;
                try(var stores=new LightingStores()){
                    SceneLightingUpload.Resources active=null;long warmAllocations=0;
                    for(int generation=0;generation<5;generation++){
                        glFlush();long readyLimit=System.nanoTime()+5_000_000_000L;while(!stores.available(true)&&System.nanoTime()<readyLimit)Thread.sleep(1);check(stores.available(true),"Retired GPU stores become reusable without blocking frame pumps");
                        try(var replacement=new SceneLightingUpload(world,volume,null,true,stores)){
                            int steps=0;while(!replacement.ready()&&steps++<4096){long start=System.nanoTime();int bytes=replacement.pump(start+500_000L,4096);long elapsed=System.nanoTime()-start;pooledMaxNanos=Math.max(pooledMaxNanos,elapsed);if(generation>=2)pooledWarmMaxNanos=Math.max(pooledWarmMaxNanos,elapsed);check(bytes<=4096,"Pooled transfer byte cap");}
                            check(replacement.ready(),"Pooled replacement completes");var next=replacement.take();
                            for(int level=0;level<volume.transport.radianceMips.length;level++)floats(id(next.transport(),"radiance"),level,GL_RGBA,volume.transport.radianceMips[level]);
                            glBindTexture(GL_TEXTURE_3D,next.atlas());int aw=glGetTexLevelParameteri(GL_TEXTURE_3D,0,GL_TEXTURE_WIDTH),ah=glGetTexLevelParameteri(GL_TEXTURE_3D,0,GL_TEXTURE_HEIGHT),ad=glGetTexLevelParameteri(GL_TEXTURE_3D,0,GL_TEXTURE_DEPTH);var roots=MemoryUtil.memAllocInt(aw*ah*ad);
                            try{glGetTexImage(GL_TEXTURE_3D,0,GL_RED_INTEGER,GL_INT,roots);for(int z=0;z<volume.length;z++)for(int y=0;y<volume.height;y++)for(int x=0;x<volume.width;x++)check(roots.get((z*ah+y)*aw+x)==volume.roots[(z*volume.height+y)*volume.width+x],"Pooled padded fine roots exact");}finally{MemoryUtil.memFree(roots);}
                            if(active!=null){stores.releaseTexture(active.atlas());stores.releaseTexture(active.irradiance());stores.releaseTexture(active.fineTexture());stores.releaseBuffer(active.fineBuffer());active.transport().close();}
                            active=next;
                        }
                        if(generation==1)warmAllocations=stores.allocations;
                        if(generation>1)check(stores.allocations==warmAllocations,"Repeated swaps reuse both generations without GPU store allocation");
                    }
                    stores.releaseTexture(active.atlas());stores.releaseTexture(active.irradiance());stores.releaseTexture(active.fineTexture());stores.releaseBuffer(active.fineBuffer());active.transport().close();pooledAllocations=stores.allocations;
                }
                check(!glIsTexture(resources.atlas())&&!glIsTexture(resources.irradiance())&&!glIsBuffer(resources.fineBuffer()),"Resources released on context thread");check(glGetError()==GL_NO_ERROR,"Final GL error absent");
                Files.writeString(out.resolve("result.txt"),"Playtest: PASS. GL 3.3 staged upload integration on inherited role display; Mesa llvmpipe; synthetic world. Exact GPU readback of sky/fine roots, fine nodes and irradiance; byte-cap progress, sky-only reuse, edit cancellation and context-thread cleanup. Pumps="+pumps+", max bytes="+maxBytes+", total bytes="+totalBytes+", max owner pump ms="+maxNanos/1e6+"; pooled max pump ms="+pooledMaxNanos/1e6+", pooled warm max pump ms="+pooledWarmMaxNanos/1e6+", two-generation allocations="+pooledAllocations+"; transport allocation ms="+transportAllocationNanos/1e6+", mipmap ms="+transportMipmapNanos/1e6+". Standalone integration, not a game frame-time claim.\n");
            }
        }catch(Throwable e){Files.writeString(out.resolve("failure.txt"),e.getClass().getSimpleName()+": "+e.getMessage()+"\n");throw e;}
        finally{if(handle!=0)glfwDestroyWindow(handle);glfwTerminate();}
    }
}
