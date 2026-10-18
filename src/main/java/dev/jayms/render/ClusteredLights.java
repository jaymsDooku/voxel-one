package dev.jayms.render;

import dev.jayms.World;
import dev.jayms.ChunkPos;
import dev.jayms.ShaderProgram;
import dev.jayms.net.Blocks;
import dev.jayms.net.Protocol;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;
import java.nio.IntBuffer;
import java.util.*;
import static org.lwjgl.opengl.GL33.*;

/** Local, sliced light capture/build/upload. All world and GL access stays on the owner. */
public final class ClusteredLights implements AutoCloseable {
    public record Light(Vector3f position,Vector3f color,float radius) {}
    private static final int CELLS=12*16*12, MAX_INDICES=CELLS*32;
    private static final class Stores {
        final int cluster=glGenTextures(),indices=glGenTextures(),lights=glGenTextures();
        final int indexBuffer=glGenBuffers(),lightBuffer=glGenBuffers();long fence;
        Stores(){
            glActiveTexture(GL_TEXTURE11);glBindTexture(GL_TEXTURE_3D,cluster);
            glTexImage3D(GL_TEXTURE_3D,0,GL_RG32I,12,16,12,0,GL_RG_INTEGER,GL_INT,(IntBuffer)null);
            glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            glBindBuffer(GL_TEXTURE_BUFFER,indexBuffer);glBufferData(GL_TEXTURE_BUFFER,MAX_INDICES*4L,GL_DYNAMIC_DRAW);
            glBindTexture(GL_TEXTURE_BUFFER,indices);glTexBuffer(GL_TEXTURE_BUFFER,GL_R32I,indexBuffer);
            glBindBuffer(GL_TEXTURE_BUFFER,lightBuffer);glBufferData(GL_TEXTURE_BUFFER,256*8*4L,GL_DYNAMIC_DRAW);
            glBindTexture(GL_TEXTURE_BUFFER,lights);glTexBuffer(GL_TEXTURE_BUFFER,GL_RGBA32F,lightBuffer);
            glBindBuffer(GL_TEXTURE_BUFFER,0);glActiveTexture(GL_TEXTURE0);
        }
        boolean available(){if(fence==0)return true;int s=glClientWaitSync(fence,0,0);if(s==GL_WAIT_FAILED)throw new IllegalStateException("Light cluster retirement fence failed");if(s!=GL_ALREADY_SIGNALED&&s!=GL_CONDITION_SATISFIED)return false;glDeleteSync(fence);fence=0;return true;}
        void close(){if(fence!=0)glDeleteSync(fence);glDeleteTextures(cluster);glDeleteTextures(indices);glDeleteTextures(lights);glDeleteBuffers(indexBuffer);glDeleteBuffers(lightBuffer);}
    }
    private final Stores[] stores={new Stores(),new Stores()};private int active;
    private final IntBuffer scratch=MemoryUtil.memAllocInt(16*1024);
    private final java.nio.FloatBuffer floatScratch=MemoryUtil.memFloatBuffer(MemoryUtil.memAddress(scratch),256*8);
    private final int[] cells=new int[CELLS*2],packed=new int[MAX_INDICES];
    private final float[] lightData=new float[256*8];
    private final ArrayList<Light> building=new ArrayList<>(256);
    private List<Light> emitters=List.of();
    private final Vector3f origin=new Vector3f();
    private World acceptedWorld,owner;
    private long revision=Long.MIN_VALUE,stamp;private int cx=Integer.MIN_VALUE,cz,nx,nz;
    private int column,chunk,cell,packedSize,phase=-1,uploadOffset;
    private Iterator<Protocol.Edit> edits=Collections.emptyIterator();
    private boolean closed;
    public List<Light> emitters(){return emitters;}
    private static long stamp(World w,int x,int z){return w.localRevision(x-60,x+59,z-60,z+59);}
    public boolean update(World world,float x,float z){return update(world,x,z,System.nanoTime()+FrameBudget.STREAMING_CPU_NANOS);}
    public boolean update(World world,float x,float z,long deadline){
        if(closed||System.nanoTime()>=deadline)return false;
        int tx=Math.floorDiv((int)Math.floor(x),16)*16,tz=Math.floorDiv((int)Math.floor(z),16)*16;
        long current=stamp(world,tx,tz);
        if(acceptedWorld==world&&revision==current&&cx==tx&&cz==tz)return false;
        if(owner!=world||nx!=tx||nz!=tz||stamp!=current||phase<0){
            if(phase>=3&&stores[active^1].fence==0)stores[active^1].fence=glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE,0);
            owner=world;nx=tx;nz=tz;stamp=current;phase=0;column=chunk=cell=packedSize=uploadOffset=0;
            edits=Collections.emptyIterator();building.clear();
        }
        int units=0;
        try {
            while(System.nanoTime()<deadline&&units++<512){
                if(phase==0){
                    if(building.size()==256){phase=1;continue;}
                    if(edits.hasNext()){
                        var e=edits.next();if(Blocks.material(e.type())!=Blocks.LED)continue;
                        float side=1f/(1<<e.depth());var p=new Vector3f(e.x()+(e.ix()+.5f)*side,e.y()+(e.iy()+.5f)*side,e.z()+(e.iz()+.5f)*side);
                        if(p.x<nx-60||p.x>nx+60||p.y< -44||p.y>108||p.z<nz-60||p.z>nz+60)continue;
                        int c=e.color();building.add(new Light(p,new Vector3f((c>>16&255)/255f,(c>>8&255)/255f,(c&255)/255f),12));continue;
                    }
                    int minX=Math.floorDiv(nx-60,16),maxX=Math.floorDiv(nx+59,16),minZ=Math.floorDiv(nz-60,16),maxZ=Math.floorDiv(nz+59,16),width=maxX-minX+1;
                    if(column==width*(maxZ-minZ+1)){phase=1;continue;}
                    edits=world.editsInChunk(new ChunkPos(minX+column%width,-3+chunk,minZ+column/width)).iterator();
                    if(++chunk==10){chunk=0;column++;}continue;
                }
                if(phase==1){
                    if(cell==CELLS){
                        if(building.isEmpty()&&emitters.isEmpty()&&acceptedWorld==world){commit(world);return true;}
                        for(int i=0;i<building.size();i++){var l=building.get(i);int a=i*8;lightData[a]=l.position.x;lightData[a+1]=l.position.y;lightData[a+2]=l.position.z;lightData[a+3]=l.radius;lightData[a+4]=l.color.x;lightData[a+5]=l.color.y;lightData[a+6]=l.color.z;lightData[a+7]=0;}
                        phase=2;continue;
                    }
                    int i=cell%12,j=cell/12%16,k=cell/(12*16),base=cell++*2;cells[base]=packedSize;cells[base+1]=0;
                    for(int id=0;id<building.size();id++){
                        var l=building.get(id);float dx=Math.max(0,Math.abs(l.position.x-(nx-48+i*8+4))-4),dy=Math.max(0,Math.abs(l.position.y-(-32+j*8+4))-4),dz=Math.max(0,Math.abs(l.position.z-(nz-48+k*8+4))-4);
                        if(dx*dx+dy*dy+dz*dz<l.radius*l.radius&&cells[base+1]<32){packed[packedSize++]=id;cells[base+1]++;}
                    }
                    continue;
                }
                var next=stores[active^1];if(!next.available())return false;
                if(phase==2){
                    glActiveTexture(GL_TEXTURE11);glBindTexture(GL_TEXTURE_3D,next.cluster);scratch.clear();scratch.put(cells).flip();glTexSubImage3D(GL_TEXTURE_3D,0,0,0,0,12,16,12,GL_RG_INTEGER,GL_INT,scratch);phase=3;continue;
                }
                if(phase==3){
                    int count=Math.min(16*1024,packedSize-uploadOffset);
                    if(count>0){scratch.clear();scratch.put(packed,uploadOffset,count).flip();glBindBuffer(GL_TEXTURE_BUFFER,next.indexBuffer);glBufferSubData(GL_TEXTURE_BUFFER,uploadOffset*4L,scratch);uploadOffset+=count;}
                    if(uploadOffset==packedSize)phase=4;continue;
                }
                glBindBuffer(GL_TEXTURE_BUFFER,next.lightBuffer);
                // At most256 emitters /8KiB. Upload only after all dependent indices are ready.
                var floats=floatScratch;floats.clear();floats.put(lightData,0,Math.max(8,building.size()*8)).flip();
                glBufferSubData(GL_TEXTURE_BUFFER,0,floats);glBindBuffer(GL_TEXTURE_BUFFER,0);
                stores[active].fence=glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE,0);active^=1;commit(world);return true;
            }
            return false;
        }finally{glBindBuffer(GL_TEXTURE_BUFFER,0);glActiveTexture(GL_TEXTURE0);}
    }
    private void commit(World world){acceptedWorld=world;cx=nx;cz=nz;revision=stamp;origin.set(cx-48,-32,cz-48);emitters=List.copyOf(building);phase=-1;edits=Collections.emptyIterator();}
    public void bind(ShaderProgram shader){
        var s=stores[active];shader.setVector3("uClusterOrigin",origin.x,origin.y,origin.z);
        glActiveTexture(GL_TEXTURE11);glBindTexture(GL_TEXTURE_3D,s.cluster);shader.setInt("uClusters",11);
        glActiveTexture(GL_TEXTURE12);glBindTexture(GL_TEXTURE_BUFFER,s.indices);shader.setInt("uLightIndices",12);
        glActiveTexture(GL_TEXTURE13);glBindTexture(GL_TEXTURE_BUFFER,s.lights);shader.setInt("uLights",13);
        shader.setInt("uClusterReady",acceptedWorld==owner&&revision!=Long.MIN_VALUE?1:0);
    }
    @Override public void close(){if(closed)return;closed=true;for(var s:stores)s.close();MemoryUtil.memFree(scratch);building.clear();emitters=List.of();owner=acceptedWorld=null;edits=Collections.emptyIterator();}
}
