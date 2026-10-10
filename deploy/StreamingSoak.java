package dev.jayms;

import dev.jayms.net.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.FrustumIntersection;
import org.lwjgl.opengl.GL;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Real World.stream, detailed worker and GL uploads; fixed synthetic regions, never player saves. */
public class StreamingSoak {
    static Path out;static long ticks,maxJobs,maxWaiting,maxChunks,maxBytes,maxAge,borders,unloads,reloads,edits;
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    static int field(Mesh m,String n)throws Exception{var f=Mesh.class.getDeclaredField(n);f.setAccessible(true);return f.getInt(m);}
    static void matches(World w,ChunkPos p)throws Exception{
        Chunk c=w.getLoadedChunks().get(p);check(!c.dirty()&&c.getMesh()!=null,"Latest boundary mesh installed");
        MeshData d=MeshDataGenerator.generate(new ChunkSnapshot(w,p,c,true));check(d.indices().length==36,"Single opaque boundary block stays closed");
        glBindBuffer(GL_ARRAY_BUFFER,field(c.getMesh(),"vbo"));float[] vertices=new float[d.vertices().length];
        check(glGetBufferParameteri(GL_ARRAY_BUFFER,GL_BUFFER_SIZE)==vertices.length*4,"No stale vertex buffer size");glGetBufferSubData(GL_ARRAY_BUFFER,0,vertices);
        check(Arrays.equals(vertices,d.vertices()),"Installed GPU mesh matches latest boundary edit");
        glBindBuffer(GL_ARRAY_BUFFER,field(c.getMesh(),"ebo"));int[] indices=new int[d.indices().length];glGetBufferSubData(GL_ARRAY_BUFFER,0,indices);check(Arrays.equals(indices,d.indices()),"Installed border indices match");glBindBuffer(GL_ARRAY_BUFFER,0);borders++;
    }
    static void sample(World w,DetailedMeshScheduler s)throws Exception{
        ticks++;maxJobs=Math.max(maxJobs,s.queued());maxWaiting=Math.max(maxWaiting,s.waiting());maxChunks=Math.max(maxChunks,w.getLoadedChunks().size());maxBytes=Math.max(maxBytes,Mesh.estimatedResidentBytes());maxAge=Math.max(maxAge,s.oldestMillis());
        check(s.queued()<=4,"Bounded queued/uploading jobs");check(s.waiting()<=w.getLoadedChunks().size(),"Waiting set bounded by resident chunks");check(w.getLoadedChunks().size()<=1690,"Production load/unload radius bound");check(Mesh.estimatedResidentBytes()<32L*1024*1024,"Synthetic resident geometry budget");check(s.error.isEmpty(),"No mesh/upload failure");check(glGetError()==GL_NO_ERROR,"No GL error");
    }
    public static void main(String[] args)throws Exception{
        try{run(args);}catch(Throwable e){Path dir=Path.of(args[0]);Files.createDirectories(dir);Files.writeString(dir.resolve("failure.txt"),e.getClass().getSimpleName()+": "+e.getMessage()+"\n");throw e;}
    }
    static void run(String[] args)throws Exception{
        out=Path.of(args[0]);Files.createDirectories(out);int cycles=Integer.parseInt(args[1]);check(cycles>=32,"Sustained test needs >=32 cycles");
        check(glfwInit(),"GLFW init");glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window=glfwCreateWindow(480,320,"Voxel One streaming/edit soak",0,0);check(window!=0,"Window creation");glfwMakeContextCurrent(window);GL.createCapabilities();glfwSwapInterval(0);
        long started=System.nanoTime(),baseline=Mesh.estimatedResidentBytes();long heapFirst=0,heapLast=0,heapMax=0;long cancelled=0;int visits[]=new int[8];
        StringBuilder csv=new StringBuilder("cycle,region,loaded_chunks,jobs,waiting,resident_mesh_bytes,rejected,border_checks,heap_after_gc_bytes\n");
        try(var world=new World(42);var scheduler=new DetailedMeshScheduler();var shader=new ShaderProgram("shaders/shadow.vert","shaders/shadow.frag")){
            FrustumIntersection visible=new FrustumIntersection(new Matrix4f().ortho(-100000,100000,-100000,100000,-100000,100000));
            for(int cycle=0;cycle<cycles;cycle++){
                long cycleStart=System.nanoTime();int region=cycle%8,cx=region*32,x=cx*16;ChunkPos left=new ChunkPos(cx,7,0),right=new ChunkPos(cx+1,7,0);
                Set<ChunkPos> previous=new HashSet<>(world.getLoadedChunks().keySet());world.stream(x+8,8,1);world.stream(x+24,8,1);for(var p:previous)if(!world.getLoadedChunks().containsKey(p))unloads++;if(visits[region]++>0)reloads++;
                // Generation/reload must reproduce the previous authoritative edit before new edits.
                if(visits[region]>1)check(world.getBlock(x+15,120,0)==Blocks.LED,"Authoritative edit survived unload/reload");
                world.apply(new Protocol.Edit(x+15,120,0,Blocks.BRICKS));world.apply(new Protocol.Edit(x+16,120,0,Blocks.STONE));edits+=2;
                check(scheduler.request(world,left),"Queue old left mesh");check(scheduler.request(world,right),"Queue old right mesh");
                world.apply(new Protocol.Edit(x+16,120,0,0));edits++;
                world.unloadChunk(left);unloads++;world.addChunk(left,new Chunk());reloads++;
                long before=scheduler.rejected;scheduler.ready(world);check(scheduler.rejected>=before+2,"Both identity/revision obsolete jobs rejected");cancelled+=2;
                // Repeated material/removal/addition edits coalesce while an upload is pending.
                for(int edit=0;edit<8;edit++){world.apply(new Protocol.Edit(x+15,120,0,edit%2==0?Blocks.PLANKS:Blocks.LED).withColor(0xff3040));world.apply(new Protocol.Edit(x+16,120,0,edit%2==0?0:Blocks.STONE));edits+=2;}
                check(scheduler.request(world,left)&&scheduler.request(world,right),"Latest jobs queued");
                // Keep terrain generation bounded and realistic; two extra columns per region.
                world.stream(x+8,8,2);
                long deadline=System.nanoTime()+20_000_000_000L;int localTicks=0;
                while(System.nanoTime()<deadline){
                    // Disable incidental scheduling outside the two target chunks without changing world streaming.
                    scheduler.update(world,x+8,8,visible,0,0,2_000_000,4096);sample(world,scheduler);glfwPollEvents();
                    glBindFramebuffer(GL_FRAMEBUFFER,0);glViewport(0,0,480,320);glClearColor(.12f,.2f,.3f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glEnable(GL_DEPTH_TEST);
                    shader.bind();shader.setInt("uInstanced",0);shader.setMatrix4("uProjection",new Matrix4f().perspective(1.1f,1.5f,.1f,100));shader.setMatrix4("uView",new Matrix4f().lookAt(new Vector3f(x+15.5f,122,5),new Vector3f(x+15.5f,120.5f,.5f),new Vector3f(0,1,0)));
                    for(var p:List.of(left,right)){Chunk c=world.getLoadedChunks().get(p);if(c.getMesh()!=null){shader.setMatrix4("uModel",new Matrix4f().translation(p.chunkX()*16,p.chunkY()*16,0));c.getMesh().render();}}
                    if(cycle==cycles-1&&localTicks==24)capture(out.resolve("streaming-soak-borders.png"));glfwSwapBuffers(window);localTicks++;Thread.sleep(2);
                    if(localTicks>=25&&!world.getLoadedChunks().get(left).dirty()&&!world.getLoadedChunks().get(right).dirty()&&System.nanoTime()-cycleStart>=2_000_000_000L)break;
                }
                matches(world,left);matches(world,right);check(world.getBlock(x+15,120,0)==Blocks.LED&&world.getBlock(x+16,120,0)==Blocks.STONE,"Latest edits remain authoritative");
                // Test-only GC distinguishes retained heap from transient generation allocations.
                System.gc();Thread.sleep(5);long heap=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();heapMax=Math.max(heapMax,heap);if(cycle==15)heapFirst=heap;heapLast=heap;
                csv.append(cycle+","+region+","+world.getLoadedChunks().size()+","+scheduler.queued()+","+scheduler.waiting()+","+Mesh.estimatedResidentBytes()+","+scheduler.rejected+","+borders+","+heap+"\n");
                if(cycle%8==7)Files.writeString(out.resolve("progress.txt"),"Completed "+(cycle+1)+"/"+cycles+" cycles; rejected="+scheduler.rejected+"; borders="+borders+"\n");
            }
            check(scheduler.rejected>=cancelled,"Obsolete jobs cannot install");check(heapLast-heapFirst<64L*1024*1024,"Retained heap plateaus after warm-up");
            Files.writeString(out.resolve("streaming-soak.csv"),csv);
            Files.writeString(out.resolve("streaming-soak-results.txt"),"Playtest: PASS. Actual World.stream and async GL mesh scheduler; "+cycles+" cycles across eight regions 512 blocks apart; "+ticks+" ticks; "+((System.nanoTime()-started)/1_000_000_000)+" seconds; "+unloads+" chunks unloaded; "+reloads+" reload events; "+edits+" boundary edits; "+borders+" installed GPU border comparisons. Max jobs="+maxJobs+", waiting="+maxWaiting+", loaded chunks="+maxChunks+", geometry bytes="+maxBytes+", oldest job ms="+maxAge+", rejected="+scheduler.rejected+". Retained heap after warm-up="+heapFirst+", last="+heapLast+", max="+heapMax+" bytes. Test-only GC and GPU readback; no reference performance claim. GL errors absent.\n");
        }finally{check(Mesh.estimatedResidentBytes()==baseline,"Unloaded/disposed GPU geometry returns to baseline");glfwDestroyWindow(window);glfwTerminate();}
    }
    static void capture(Path p)throws Exception{var b=java.nio.ByteBuffer.allocateDirect(480*320*3);glReadPixels(0,0,480,320,GL_RGB,GL_UNSIGNED_BYTE,b);var img=new java.awt.image.BufferedImage(480,320,java.awt.image.BufferedImage.TYPE_INT_RGB);for(int y=0;y<320;y++)for(int x=0;x<480;x++){int i=(x+y*480)*3;img.setRGB(x,319-y,(b.get(i)&255)<<16|(b.get(i+1)&255)<<8|(b.get(i+2)&255));}javax.imageio.ImageIO.write(img,"png",p.toFile());}
}
