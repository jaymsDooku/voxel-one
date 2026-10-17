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
    static int shader(int type,String source){int id=glCreateShader(type);glShaderSource(id,source);glCompileShader(id);check(glGetShaderi(id,GL_COMPILE_STATUS)==GL_TRUE,"Light lookup shader compile: "+glGetShaderInfoLog(id));return id;}
    static void lookup(LightVolume volume,SceneLightingUpload.Resources data,int sky,boolean prefix)throws Exception {
        String shared;try(var input=StreamingUploadSmoke.class.getClassLoader().getResourceAsStream("shaders/irradiance.glsl")){shared=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        int vs=shader(GL_VERTEX_SHADER,"#version330 core\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.-1.,0.,1.);}".replace("version330","version 330"));
        int fs=shader(GL_FRAGMENT_SHADER,"#version 330 core\nuniform int uPlanetLighting;\n"+shared+"\nuniform vec3 uTest;out vec4 color;void main(){color=irradiance(uTest);}");
        int program=glCreateProgram(),fbo=glGenFramebuffers(),texture=glGenTextures(),vao=glGenVertexArrays();
        try {
            glAttachShader(program,vs);glAttachShader(program,fs);glLinkProgram(program);check(glGetProgrami(program,GL_LINK_STATUS)==GL_TRUE,"Lookup shader linked");glUseProgram(program);
            glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,1,1,0,GL_RGBA,GL_FLOAT,(ByteBuffer)null);glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"Lookup framebuffer");glViewport(0,0,1,1);glBindVertexArray(vao);
            glActiveTexture(GL_TEXTURE3);glBindTexture(GL_TEXTURE_3D,data.irradiance());glActiveTexture(GL_TEXTURE4);glBindTexture(GL_TEXTURE_3D,sky);glActiveTexture(GL_TEXTURE5);glBindTexture(GL_TEXTURE_BUFFER,data.fineTexture());
            for(var entry:java.util.Map.of("uIrradiance",3,"uFineRoots",4,"uFineLight",5,"uHasIrradiance",1,"uRootBuffer",prefix?(volume.uniformRoots()?2:1):0,"uFineOffset",prefix&&!volume.uniformRoots()?volume.roots.length:0).entrySet())glUniform1i(glGetUniformLocation(program,entry.getKey()),entry.getValue());
            glUniform3f(glGetUniformLocation(program,"uVolumeOrigin"),volume.x,volume.y,volume.z);glUniform3f(glGetUniformLocation(program,"uVolumeSize"),volume.width,volume.height,volume.length);
            for(float[] point:new float[][]{{1.01f,1.01f,1.01f},{1.24f,1.01f,1.01f},{1.6f,1.1f,1.1f},{2.5f,2.5f,2.5f}}){
                glUniform3f(glGetUniformLocation(program,"uTest"),point[0],point[1],point[2]);glDrawArrays(GL_TRIANGLES,0,3);float[] actual=new float[4];glReadPixels(0,0,1,1,GL_RGBA,GL_FLOAT,actual);var expected=volume.sampleLighting(point[0],point[1],point[2]);for(int c=0;c<4;c++)check(Math.abs(actual[c]-expected[c])<.00002f,"Production lookup exact fractional RGB/sky; prefix="+prefix+" channel="+c+" actual="+actual[c]+" expected="+expected[c]+" size="+volume.width+","+volume.height+","+volume.length+" uniform="+volume.uniformRoots()+" rootMode="+glGetUniformi(program,glGetUniformLocation(program,"uRootBuffer"))+" glError="+glGetError());
            }
            check(glGetError()==GL_NO_ERROR,"Lookup GL error absent");
        }finally{glUseProgram(0);glBindFramebuffer(GL_FRAMEBUFFER,0);glBindVertexArray(0);glViewport(0,0,320,240);glActiveTexture(GL_TEXTURE0);glDeleteProgram(program);glDeleteShader(vs);glDeleteShader(fs);glDeleteFramebuffers(fbo);glDeleteTextures(texture);glDeleteVertexArrays(vao);}
    }
    static void atlas(int id,int offset,int[] expected){
        glBindTexture(GL_TEXTURE_3D,id);int w=glGetTexLevelParameteri(GL_TEXTURE_3D,0,GL_TEXTURE_WIDTH),h=glGetTexLevelParameteri(GL_TEXTURE_3D,0,GL_TEXTURE_HEIGHT),d=glGetTexLevelParameteri(GL_TEXTURE_3D,0,GL_TEXTURE_DEPTH);var data=MemoryUtil.memAllocInt(w*h*d);
        try{glGetTexImage(GL_TEXTURE_3D,0,GL_RED_INTEGER,GL_INT,data);for(int i=0;i<expected.length;i++)check(data.get(offset+i)==expected[i],"Concurrent sky atlas exact readback");}finally{MemoryUtil.memFree(data);}
    }
    static void concurrent(World world,LightVolume volume,SkyVisibilityScheduler.Prepared prepared)throws Exception {
        try(var stores=new LightingStores()){
            stores.warmDefaults();glFlush();long end=System.nanoTime()+5_000_000_000L;while(!stores.available(false)&&System.nanoTime()<end){glFlush();Thread.sleep(1);}check(stores.available(false),"Concurrent sky startup stores ready");
            SceneLightingUpload.Resources old;
            try(var initial=new SceneLightingUpload(world,null,prepared,false,stores)){int n=0;while(!initial.ready()&&n++<4096)initial.pump(System.nanoTime()+500_000,4096,1024);check(initial.ready(),"Initial independent sky completed");old=initial.take();}
            end=System.nanoTime()+5_000_000_000L;while(!stores.available(false)&&System.nanoTime()<end){glFlush();Thread.sleep(1);}long allocations=stores.allocations;
            try(var sky=new SceneLightingUpload(world,null,prepared,false,stores)){
                check(!stores.available(true),"Both sky generations are owned during independent staging");check(stores.available(true,false),"GI does not wait for the independent atlas role");
                try(var gi=new SceneLightingUpload(world,volume,null,true,stores,null,true)){
                    int n=0,skyProgress=0,giProgress=0;
                    while((!sky.ready()||!gi.ready())&&n++<4096){
                        int a=sky.ready()?0:sky.pump(System.nanoTime()+100_000,4096,1024),b=gi.ready()?0:gi.pump(System.nanoTime()+100_000,8192-a);check(a+b<=8192,"Concurrent lanes share one byte budget");if(a>0)skyProgress++;if(b>0)giProgress++;glfwPollEvents();
                    }
                    check(sky.ready()&&gi.ready()&&skyProgress>0&&giProgress>0,"Both bounded lanes finish without starving");/* This8-cube fixture resizes defaults during setup; the production-sized test below requires zero allocations. */var light=gi.take();var next=sky.take();
                    atlas(old.atlas(),0,prepared.data.data);atlas(next.atlas(),0,prepared.data.data);lookup(volume,light,next.atlas(),true);
                    check(glIsTexture(old.atlas()),"Old sky remains live until complete replacement is usable");stores.releaseTexture(old.atlas());stores.releaseTexture(next.atlas());stores.releaseTexture(light.irradiance());stores.releaseTexture(light.fineTexture());stores.releaseBuffer(light.fineBuffer());light.transport().close();
                }
            }
        }
        check(glGetError()==GL_NO_ERROR,"Concurrent lane cleanup GL error absent");
    }
    static void fullVolume(World world,Path out)throws Exception {
        var volume=LightVolume.bake(-48,-32,-48,96,128,96,1,(x,y,z)->WorldVoxels.encode(y==0?Blocks.STONE:x==1&&y==1&&z==1?Blocks.LED:Blocks.AIR),true);
        var times=new ArrayList<Long>();var initialTimes=new ArrayList<Long>();var csv=new StringBuilder("generation,pump,elapsed_ms,bytes,phase\n");long total=0;int maximum=0,initialMaximum=0;int[] pixelIds;
        try(var stores=new LightingStores()){
            pixelIds=stores.pixelBuffers.clone();stores.warmDefaults();glFlush();long allocations=stores.allocations;SceneLightingUpload.Resources active=null;
            for(int generation=0;generation<3;generation++){
                long limit=System.nanoTime()+5_000_000_000L;while(!stores.available(true)&&System.nanoTime()<limit){glFlush();Thread.sleep(1);}check(stores.available(true),"Full-volume retired stores available");
                boolean prefix=generation==1;
                try(var upload=new SceneLightingUpload(world,volume,null,true,stores,null,prefix)){
                    int pump=0,cap=(generation==0?8:4)*1024*1024;long allowance=generation==0?8_000_000L:1_000_000L;while(!upload.ready()&&pump<1024){long begin=System.nanoTime();int bytes=upload.pump(begin+allowance,cap);long elapsed=System.nanoTime()-begin;if(generation==0){initialTimes.add(elapsed);initialMaximum=Math.max(initialMaximum,bytes);}else{times.add(elapsed);maximum=Math.max(maximum,bytes);}csv.append(generation+","+pump+","+elapsed/1e6+","+bytes+","+(generation==0?"initial":"normal")+"\n");total+=bytes;check(bytes<=cap,"Full-volume byte cap");pump++;glfwPollEvents();}
                    check(upload.ready()&&pump>1,"Full-sized volume completes across bounded pumps");check(stores.allocations==allocations,"Full-sized volume uses preallocated stores");var next=upload.take();
                    var rgba=MemoryUtil.memAlloc(volume.rgba.length);try{glBindTexture(GL_TEXTURE_3D,next.irradiance());glGetTexImage(GL_TEXTURE_3D,0,GL_RGBA,GL_UNSIGNED_BYTE,rgba);for(int j=0;j<volume.rgba.length;j++)check(rgba.get(j)==volume.rgba[j],"Full-size irradiance readback");}finally{MemoryUtil.memFree(rgba);}
                    if(prefix){
                        int rootCount=volume.uniformRoots()?0:volume.roots.length;
                        var roots=MemoryUtil.memAllocInt(rootCount+volume.fine.length);try{glBindBuffer(GL_TEXTURE_BUFFER,next.fineBuffer());glGetBufferSubData(GL_TEXTURE_BUFFER,0,roots);for(int j=0;j<rootCount;j++)check(roots.get(j)==volume.roots[j],"Full-size prefix root readback");for(int j=0;j<volume.fine.length;j++)check(roots.get(rootCount+j)==volume.fine[j],"Full-size prefix fine readback");}finally{glBindBuffer(GL_TEXTURE_BUFFER,0);MemoryUtil.memFree(roots);}
                        if(volume.uniformRoots()){
                            glBindBuffer(GL_TEXTURE_BUFFER,next.fineBuffer());
                            for(int worldCell:new int[]{1,2}){int cell=worldCell-volume.x+volume.width*(worldCell-volume.y+volume.height*(worldCell-volume.z));glBufferSubData(GL_TEXTURE_BUFFER,cell*4L,new int[]{Integer.MAX_VALUE});}
                            glBindBuffer(GL_TEXTURE_BUFFER,0);lookup(volume,next,active.atlas(),true);
                        }
                    }else{
                    glBindTexture(GL_TEXTURE_3D,next.atlas());int aw=glGetTexLevelParameteri(GL_TEXTURE_3D,0,GL_TEXTURE_WIDTH),ah=glGetTexLevelParameteri(GL_TEXTURE_3D,0,GL_TEXTURE_HEIGHT),ad=glGetTexLevelParameteri(GL_TEXTURE_3D,0,GL_TEXTURE_DEPTH);var roots=MemoryUtil.memAllocInt(aw*ah*ad);try{glGetTexImage(GL_TEXTURE_3D,0,GL_RED_INTEGER,GL_INT,roots);for(int j=0;j<volume.roots.length;j++)check(roots.get(j)==volume.roots[j],"Full-size root readback");}finally{MemoryUtil.memFree(roots);}
                    }
                    for(int level=0;level<volume.transport.radianceMips.length;level++)floats(id(next.transport(),"radiance"),level,GL_RGBA,volume.transport.radianceMips[level]);floats(id(next.transport(),"distance"),GL_RED,volume.transport.distance);
                    var probe=VoxelTransport.class.getDeclaredField("probes");probe.setAccessible(true);int[] ids=(int[])probe.get(next.transport());for(int j=0;j<3;j++)floats(ids[j],GL_RGBA,volume.transport.probes[j]);
                    if(active!=null){stores.releaseTexture(active.atlas());stores.releaseTexture(active.irradiance());stores.releaseTexture(active.fineTexture());stores.releaseBuffer(active.fineBuffer());active.transport().close();}active=next;
                }
            }
            glFlush();long cancelLimit=System.nanoTime()+5_000_000_000L;while(!stores.available(true)&&System.nanoTime()<cancelLimit){glFlush();Thread.sleep(1);}check(stores.available(true),"Cancellation target available");
            try(var cancelled=new SceneLightingUpload(world,volume,null,true,stores)){
                cancelled.pump(System.nanoTime()+1_000_000L,256*1024);check(!cancelled.ready(),"Large PBO upload remains staged");
                world.setBlock(3,2,3,Blocks.BRICKS);check(!cancelled.valid(world),"Edit cancels in-flight PBO replacement");
            }
            cancelLimit=System.nanoTime()+5_000_000_000L;while(!stores.available(true)&&System.nanoTime()<cancelLimit){glFlush();Thread.sleep(1);}check(stores.available(true),"Cancelled targets retire without owner-thread waits");
            try(var replacement=new SceneLightingUpload(world,volume,null,true,stores)){
                int attempts=0;while(!replacement.ready()&&attempts++<2048)replacement.pump(System.nanoTime()+1_000_000L,4*1024*1024);
                check(replacement.ready()&&replacement.valid(world),"PBO replacement restarts with current fence");check(stores.allocations==allocations,"Cancellation/restart does not allocate GPU stores");var next=replacement.take();
                var bytes=MemoryUtil.memAlloc(volume.rgba.length);try{glBindTexture(GL_TEXTURE_3D,next.irradiance());glGetTexImage(GL_TEXTURE_3D,0,GL_RGBA,GL_UNSIGNED_BYTE,bytes);for(int j=0;j<volume.rgba.length;j++)check(bytes.get(j)==volume.rgba[j],"Cancelled PBO bytes cannot corrupt replacement");}finally{MemoryUtil.memFree(bytes);}
                stores.releaseTexture(active.atlas());stores.releaseTexture(active.irradiance());stores.releaseTexture(active.fineTexture());stores.releaseBuffer(active.fineBuffer());active.transport().close();active=next;
            }
            stores.releaseTexture(active.atlas());stores.releaseTexture(active.irradiance());stores.releaseTexture(active.fineTexture());stores.releaseBuffer(active.fineBuffer());active.transport().close();
        }
        for(int id:pixelIds)check(!glIsBuffer(id),"All pixel staging buffers released on context thread");
        times.sort(Long::compare);Files.writeString(out.resolve("full-volume-pumps.csv"),csv);
        Files.writeString(out.resolve("full-volume-result.txt"),"Playtest: PASS. Full96x128x96 volume, three prewarmed generations including buffer-prefix GI, initial8MiB/8ms and normal4MiB/1ms test pump caps;1MiB maximum transfer units. Exact roots/irradiance; all half-float radiance mips, distance and probes read back; zero new store allocations;in-flight edit cancellation/restart readback;16 fenced staging buffers (16MiB) cleaned up. Pump p50="+times.get(times.size()/2)/1e6+" ms; p99="+times.get(Math.min(times.size()-1,(int)Math.ceil(times.size()*.99)-1))/1e6+" ms; worst="+times.get(times.size()-1)/1e6+" ms; maximum pump bytes="+maximum+"; initial maximum bytes="+initialMaximum+"; initial worst ms="+initialTimes.stream().mapToLong(Long::longValue).max().orElse(0)/1e6+"; total bytes="+total+". Isolated GL integration, not game frame timing.\n");
    }
    static void set(Object object,String name,Object value)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    static void ownerBudgets()throws Exception{
        try(var world=new World(42);var renderer=new RenderPipeline()){
            renderer.prepareFocus(world,0,0);check(renderer.ownerBudgetNanos==8_000_000L,"Initial owner allowance");
            set(renderer,"initialLightingUpdates",64);
            renderer.prepareFocus(world,0,0);check(renderer.ownerBudgetNanos==4_000_000L,"Missing local lighting receives bounded catch-up allowance");
            for(int x=-2;x<2;x++)for(int z=-2;z<2;z++)world.addChunk(new ChunkPos(x,0,z),new Chunk());
            set(renderer,"worldSky",WorldSkyVisibility.build(world));
            set(renderer,"volume",LightVolume.bake(-48,-32,-48,96,1,96,(x,y,z)->0));
            set(renderer,"activeVolumeFence",new WorldLighting.Capture(world,0,0));set(renderer,"hasIrradiance",true);
            renderer.prepareFocus(world,0,0);check(renderer.ownerBudgetNanos==2_000_000L,"Ready resident lighting returns to normal allowance");
            check(renderer.nearLightingReady(world,0,0),"First playable lighting is current and covers nearby probes");
            set(renderer,"desiredVolume",LightVolume.bake(0,0,0,2,2,2,(x,y,z)->0));renderer.prepareFocus(world,0,0);check(renderer.ownerBudgetNanos==4_000_000L,"Useful prefetch upload gets bounded service before coverage is lost");set(renderer,"desiredVolume",null);
            try(var pendingSky=new SkyVisibilityScheduler()){set(renderer,"desiredSky",sky(pendingSky,world));renderer.prepareFocus(world,0,0);check(renderer.ownerBudgetNanos==4_000_000L,"Sky replacement gets bounded service while nearby GI remains usable");set(renderer,"desiredSky",null);}
            set(renderer,"lastVolumeLatency",60_000_000_000L);set(renderer,"previousFocusTime",System.nanoTime()-1_000_000_000L);
            renderer.prepareFocus(world,1,0);var request=renderer.getClass().getDeclaredField("requestedX");request.setAccessible(true);check(request.getFloat(renderer)<=32&&request.getFloat(renderer)>=24,"Moving forecast stays within two chunk widths of active coverage");
            try(var other=new World(43)){check(!renderer.nearLightingReady(other,0,0),"Previous world cannot mark a new spawn ready");}
            world.setBlock(0,0,0,Blocks.STONE);renderer.prepareFocus(world,0,0);check(renderer.ownerBudgetNanos==4_000_000L,"Local edit invalidation restores catch-up allowance");renderer.prepareFocus(world,20,0);check(request.getFloat(renderer)==20,"Invalidated local coverage prioritizes the actual camera");
            renderer.prepareFocus(world,2048,0);check(renderer.ownerBudgetNanos==4_000_000L,"Teleport never receives a second startup allowance");check(request.getFloat(renderer)==2048,"Teleport bypasses old-field forecast clamp");
        }
    }
    static void skyLookup(WorldSkyVisibility sky,int atlas,int offset)throws Exception{
        String shared;try(var input=StreamingUploadSmoke.class.getClassLoader().getResourceAsStream("shaders/irradiance.glsl")){shared=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        int vs=shader(GL_VERTEX_SHADER,"#version 330 core\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.-1.,0.,1.);}");
        int fs=shader(GL_FRAGMENT_SHADER,"#version 330 core\nuniform int uPlanetLighting;\n"+shared+"\nuniform vec3 uTest;out vec4 color;void main(){color=vec4(worldSkyVisibility(uTest),0,0,1);}");
        int program=glCreateProgram(),fbo=glGenFramebuffers(),texture=glGenTextures(),vao=glGenVertexArrays();
        try{
            glAttachShader(program,vs);glAttachShader(program,fs);glLinkProgram(program);check(glGetProgrami(program,GL_LINK_STATUS)==GL_TRUE,"Sky lookup linked");glUseProgram(program);
            glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,1,1,0,GL_RGBA,GL_FLOAT,(ByteBuffer)null);glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"Sky lookup framebuffer");glViewport(0,0,1,1);glBindVertexArray(vao);
            glActiveTexture(GL_TEXTURE4);glBindTexture(GL_TEXTURE_3D,atlas);
            for(var e:Map.of("uFineRoots",4,"uSkyColumns",sky.columns,"uSkyOffset",offset,"uSkyIndexOrigin",sky.indexOrigin,"uSkyIndexSpan",sky.indexSpan,"uSkyIndexOffset",sky.indexOffset).entrySet())glUniform1i(glGetUniformLocation(program,e.getKey()),e.getValue());
            for(float[] point:new float[][]{{-.5f,9,-.5f},{-.5f,11,-.5f},{1.5f,9,1.5f},{-1.99f,11,-1.99f},{-1.8f,11,-1.8f},{-.5f,9,8.5f},{24.5f,9,1.5f},{2.5f,9,2.5f},{-100,9,0},{640.5f,9,.5f},{640.5f,11,.5f}}){
                glUniform3f(glGetUniformLocation(program,"uTest"),point[0],point[1],point[2]);glDrawArrays(GL_TRIANGLES,0,3);float[] actual=new float[4];glReadPixels(0,0,1,1,GL_RGBA,GL_FLOAT,actual);
                check(Math.abs(actual[0]-sky.sampleKnown(point[0],point[1],point[2],0))<.00002f,"GPU row/dense/sparse/far sky lookup equals independent CPU binary reference");
            }
            check(glGetError()==GL_NO_ERROR,"Sky lookup GL error absent");
        }finally{glUseProgram(0);glBindFramebuffer(GL_FRAMEBUFFER,0);glBindVertexArray(0);glViewport(0,0,320,240);glActiveTexture(GL_TEXTURE0);glDeleteProgram(program);glDeleteShader(vs);glDeleteShader(fs);glDeleteFramebuffers(fbo);glDeleteTextures(texture);glDeleteVertexArrays(vao);}
    }
    static void skyDirectory()throws Exception{
        try(var world=new World(42);var scheduler=new SkyVisibilityScheduler()){
            for(var p:new ChunkPos[]{new ChunkPos(-1,0,-1),new ChunkPos(-1,0,1),new ChunkPos(0,0,0),new ChunkPos(2,0,0)})world.addChunk(p,new Chunk());
            world.apply(new Protocol.Edit(-1,10,-1,Blocks.STONE));world.apply(new Protocol.Edit(1,10,1,Blocks.GLASS));world.apply(new Protocol.Edit(-2,12,-2,Blocks.STONE,4,0,0,0));
            for(int phase=0;phase<2;phase++){
                if(phase==1){world.addChunk(new ChunkPos(40,0,0),new Chunk());world.apply(new Protocol.Edit(640,10,0,Blocks.STONE));}
                SkyVisibilityScheduler.Prepared ready=null;long end=System.nanoTime()+10_000_000_000L;
                while(System.nanoTime()<end){var next=scheduler.update(world,0,0,1_000_000L);if(next!=null)ready=next;if(ready!=null&&ready.valid(world)&&ready.data.columns==(phase==0?4:5)*256)break;Thread.sleep(1);}
                check(ready!=null&&ready.valid(world)&&ready.data.columns==(phase==0?4:5)*256,"Sky fixture complete");check((ready.data.indexSpan>0)==(phase==0),"Bounded directory or wide-world binary fallback selected");
                for(boolean combined:new boolean[]{false,true}){
                    var volume=combined?LightVolume.bake(0,0,0,8,8,8,(x,y,z)->0):null;
                    try(var upload=new SceneLightingUpload(world,volume,ready,combined)){
                        int n=0;while(!upload.ready()&&n++<4096)upload.pump(System.nanoTime()+1_000_000L,4096);check(upload.ready(),"Sky fixture uploaded");var data=upload.take();try{skyLookup(ready.data,data.atlas(),upload.offset);}finally{glDeleteTextures(data.atlas());if(combined){glDeleteTextures(data.irradiance());glDeleteTextures(data.fineTexture());glDeleteBuffers(data.fineBuffer());data.transport().close();}}
                    }
                }
            }
        }
    }
    public static void main(String[] args)throws Exception {
        Path out=Path.of(args[0]);Files.createDirectories(out);long handle=0;
        try {
            check(glfwInit(),"GLFW init");glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
            handle=glfwCreateWindow(320,240,"Voxel One staged upload integration",0,0);check(handle!=0,"GL 3.3 window");glfwMakeContextCurrent(handle);GL.createCapabilities();glfwSwapInterval(0);
            ownerBudgets();skyDirectory();
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
                concurrent(world,volume,prepared);
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
                // Production high-capacity path: roots and fine nodes share the existing buffer
                // sampler. A GI replacement must not copy or replace the global sky texture.
                try(var stores=new LightingStores()){
                    stores.warmDefaults();glFlush();
                    try(var separate=new SceneLightingUpload(world,volume,null,true,stores,null,true)){
                        int n=0;long transferred=0;
                        while(!separate.ready()&&n++<2048){int bytes=separate.pump(System.nanoTime()+500_000L,4096);check(bytes<=4096,"Separate root buffer byte cap");transferred+=bytes;}
                        check(separate.ready(),"Separate roots complete without sky publication");var data=separate.take();check(data.atlas()==0,"GI performs no global sky atlas allocation or copy");
                        var exact=MemoryUtil.memAllocInt(volume.roots.length+volume.fine.length);
                        try{glBindBuffer(GL_TEXTURE_BUFFER,data.fineBuffer());glGetBufferSubData(GL_TEXTURE_BUFFER,0,exact);for(int i=0;i<volume.roots.length;i++)check(exact.get(i)==volume.roots[i],"Separate exact roots");for(int i=0;i<volume.fine.length;i++)check(exact.get(volume.roots.length+i)==volume.fine[i],"Separate exact fractional nodes");}finally{glBindBuffer(GL_TEXTURE_BUFFER,0);MemoryUtil.memFree(exact);}
                        check(glIsTexture(resources.atlas()),"Existing global sky stays usable during GI replacement");lookup(volume,resources,resources.atlas(),false);lookup(volume,data,resources.atlas(),true);
                        stores.releaseTexture(data.irradiance());stores.releaseTexture(data.fineTexture());stores.releaseBuffer(data.fineBuffer());data.transport().close();check(transferred>0,"Separate transfer made progress");
                    }
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
                fullVolume(world,out);
                check(!glIsTexture(resources.atlas())&&!glIsTexture(resources.irradiance())&&!glIsBuffer(resources.fineBuffer()),"Resources released on context thread");check(glGetError()==GL_NO_ERROR,"Final GL error absent");
                Files.writeString(out.resolve("result.txt"),"Playtest: PASS. GL 3.3 staged upload integration on inherited role display; Mesa llvmpipe; synthetic world. Exact GPU readback of sky/fine roots, fine nodes and irradiance; byte-cap progress, sky-only reuse, edit cancellation and context-thread cleanup. Pumps="+pumps+", max bytes="+maxBytes+", total bytes="+totalBytes+", max owner pump ms="+maxNanos/1e6+"; pooled max pump ms="+pooledMaxNanos/1e6+", pooled warm max pump ms="+pooledWarmMaxNanos/1e6+", two-generation allocations="+pooledAllocations+"; transport allocation ms="+transportAllocationNanos/1e6+", mipmap ms="+transportMipmapNanos/1e6+". Standalone integration, not a game frame-time claim.\n");
            }
        }catch(Throwable e){Files.writeString(out.resolve("failure.txt"),e.getClass().getSimpleName()+": "+e.getMessage()+"\n");throw e;}
        finally{if(handle!=0)glfwDestroyWindow(handle);glfwTerminate();}
    }
}
