package dev.jayms.render;

import dev.jayms.World;
import dev.jayms.ShaderProgram;
import dev.jayms.net.Blocks;
import org.joml.Vector3f;
import java.util.*;
import static org.lwjgl.opengl.GL33.*;

/** World-space light clusters: bounded lists prevent per-fragment scans of every emissive source. */
public final class ClusteredLights implements AutoCloseable {
    public record Light(Vector3f position,Vector3f color,float radius) {}
    private final int cluster=glGenTextures(),indices=glGenTextures(),lights=glGenTextures();
    private final int indexBuffer=glGenBuffers(),lightBuffer=glGenBuffers();
    private long revision=Long.MIN_VALUE;private int cx=Integer.MIN_VALUE,cz;
    private List<Light> emitters=List.of();
    private final Vector3f origin=new Vector3f();
    public List<Light> emitters(){return emitters;}
    public boolean update(World world,float x,float z){
        int nx=Math.floorDiv((int)Math.floor(x),16)*16,nz=Math.floorDiv((int)Math.floor(z),16)*16;
        if(revision==world.editsVersion()&&cx==nx&&cz==nz)return false;
        cx=nx;cz=nz;revision=world.editsVersion();origin.set(cx-48,-32,cz-48);
        ArrayList<Light> list=new ArrayList<>();
        for(var edit:world.editsSnapshot().values()){
            if(Blocks.material(edit.type())!=Blocks.LED)continue;
            float side=1f/(1<<edit.depth());
            Vector3f p=new Vector3f(edit.x()+(edit.ix()+.5f)*side,edit.y()+(edit.iy()+.5f)*side,edit.z()+(edit.iz()+.5f)*side);
            if(p.x<origin.x-12||p.x>origin.x+108||p.y<origin.y-12||p.y>origin.y+140||p.z<origin.z-12||p.z>origin.z+108)continue;
            int c=edit.color();list.add(new Light(p,new Vector3f((c>>16&255)/255f,(c>>8&255)/255f,(c&255)/255f),12));
            if(list.size()==256)break;
        }
        emitters=List.copyOf(list);float[] lightData=new float[Math.max(8,list.size()*8)];
        for(int i=0;i<list.size();i++){
            Light l=list.get(i);int a=i*8;lightData[a]=l.position.x;lightData[a+1]=l.position.y;lightData[a+2]=l.position.z;lightData[a+3]=l.radius;
            lightData[a+4]=l.color.x;lightData[a+5]=l.color.y;lightData[a+6]=l.color.z;
        }
        int[] cells=new int[12*16*12*2];ArrayList<Integer> packed=new ArrayList<>();
        for(int k=0;k<12;k++)for(int j=0;j<16;j++)for(int i=0;i<12;i++){
            int base=(i+12*(j+16*k))*2;cells[base]=packed.size();
            for(int id=0;id<list.size();id++){
                Light l=list.get(id);float dx=Math.max(0,Math.abs(l.position.x-(origin.x+i*8+4))-4),dy=Math.max(0,Math.abs(l.position.y-(origin.y+j*8+4))-4),dz=Math.max(0,Math.abs(l.position.z-(origin.z+k*8+4))-4);
                if(dx*dx+dy*dy+dz*dz<l.radius*l.radius&&cells[base+1]<32){packed.add(id);cells[base+1]++;}
            }
        }
        int[] indexData=packed.isEmpty()?new int[]{0}:packed.stream().mapToInt(Integer::intValue).toArray();
        glActiveTexture(GL_TEXTURE11);glBindTexture(GL_TEXTURE_3D,cluster);
        glTexImage3D(GL_TEXTURE_3D,0,GL_RG32I,12,16,12,0,GL_RG_INTEGER,GL_INT,cells);
        glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        glBindBuffer(GL_TEXTURE_BUFFER,indexBuffer);glBufferData(GL_TEXTURE_BUFFER,indexData,GL_DYNAMIC_DRAW);
        glBindTexture(GL_TEXTURE_BUFFER,indices);glTexBuffer(GL_TEXTURE_BUFFER,GL_R32I,indexBuffer);
        glBindBuffer(GL_TEXTURE_BUFFER,lightBuffer);glBufferData(GL_TEXTURE_BUFFER,lightData,GL_DYNAMIC_DRAW);
        glBindTexture(GL_TEXTURE_BUFFER,lights);glTexBuffer(GL_TEXTURE_BUFFER,GL_RGBA32F,lightBuffer);
        glBindBuffer(GL_TEXTURE_BUFFER,0);return true;
    }
    public void bind(ShaderProgram shader){
        shader.setVector3("uClusterOrigin",origin.x,origin.y,origin.z);
        glActiveTexture(GL_TEXTURE11);glBindTexture(GL_TEXTURE_3D,cluster);shader.setInt("uClusters",11);
        glActiveTexture(GL_TEXTURE12);glBindTexture(GL_TEXTURE_BUFFER,indices);shader.setInt("uLightIndices",12);
        glActiveTexture(GL_TEXTURE13);glBindTexture(GL_TEXTURE_BUFFER,lights);shader.setInt("uLights",13);
        shader.setInt("uClusterReady",revision!=Long.MIN_VALUE?1:0);
    }
    @Override public void close(){glDeleteTextures(cluster);glDeleteTextures(indices);glDeleteTextures(lights);glDeleteBuffers(indexBuffer);glDeleteBuffers(lightBuffer);}
}
