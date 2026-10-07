package dev.jayms.render;

import dev.jayms.ShaderProgram;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.nio.ByteBuffer;
import static org.lwjgl.opengl.GL33.*;

/** Conservative maximum-depth hierarchy. Stale data is never used after camera/world changes. */
public final class HiZ implements AutoCloseable {
    private final ShaderProgram reduce=new ShaderProgram("shaders/fullscreen.vert","shaders/hiz.frag");
    private final int vao=glGenVertexArrays(),fbo=glGenFramebuffers();
    private final int[] textures=new int[5];
    private int width,height,coarseWidth,coarseHeight;
    private float[] depths;
    private final Matrix4f stored=new Matrix4f();
    private boolean valid;
    public void invalidate(){valid=false;}
    public void build(int source,int w,int h,Matrix4f vp){
        if(w!=width||h!=height){
            for(int id:textures)glDeleteTextures(id);width=w;height=h;
            int tw=w,th=h;
            for(int i=0;i<5;i++){
                tw=(tw+1)/2;th=(th+1)/2;textures[i]=glGenTextures();glBindTexture(GL_TEXTURE_2D,textures[i]);
                glTexImage2D(GL_TEXTURE_2D,0,GL_R32F,tw,th,0,GL_RED,GL_FLOAT,(ByteBuffer)null);
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            }
            coarseWidth=tw;coarseHeight=th;depths=new float[tw*th];
        }
        glBindFramebuffer(GL_FRAMEBUFFER,fbo);glDrawBuffer(GL_COLOR_ATTACHMENT0);
        glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);reduce.bind();reduce.setInt("uSource",0);
        int tw=w,th=h;
        for(int i=0;i<5;i++){
            tw=(tw+1)/2;th=(th+1)/2;glViewport(0,0,tw,th);
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,textures[i],0);
            glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,i==0?source:textures[i-1]);
            glBindVertexArray(vao);glDrawArrays(GL_TRIANGLES,0,3);
        }
        glBindTexture(GL_TEXTURE_2D,textures[4]);glGetTexImage(GL_TEXTURE_2D,0,GL_RED,GL_FLOAT,depths);
        stored.set(vp);valid=true;glBindVertexArray(0);glBindFramebuffer(GL_FRAMEBUFFER,0);
    }
    public boolean visible(Matrix4f vp,float x,float y,float z){
        if(!valid||!stored.equals(vp,0f))return true;
        float minX=1,minY=1,maxX=0,maxY=0,near=1;
        for(int i=0;i<8;i++){
            Vector4f p=vp.transform(new Vector4f(x+((i&1)==0?0:16),y+((i&2)==0?0:16),z+((i&4)==0?0:16),1));
            if(p.w<=0||p.z<=-p.w)return true;
            float px=p.x/p.w*.5f+.5f,py=p.y/p.w*.5f+.5f;
            minX=Math.min(minX,px);maxX=Math.max(maxX,px);minY=Math.min(minY,py);maxY=Math.max(maxY,py);
            near=Math.min(near,p.z/p.w*.5f+.5f);
        }
        // Expand two full-resolution pixels for subpixel jitter and rasterization boundaries.
        int a=Math.max(0,(int)Math.floor((minX-2f/width)*width/32));
        int b=Math.min(coarseWidth-1,(int)Math.floor((maxX+2f/width)*width/32));
        int c=Math.max(0,(int)Math.floor((minY-2f/height)*height/32));
        int d=Math.min(coarseHeight-1,(int)Math.floor((maxY+2f/height)*height/32));
        for(int j=c;j<=d;j++)for(int i=a;i<=b;i++)if(near<=depths[i+j*coarseWidth]+.0001f)return true;
        return a>b||c>d;
    }
    @Override public void close(){for(int id:textures)glDeleteTextures(id);glDeleteFramebuffers(fbo);glDeleteVertexArrays(vao);reduce.close();}
}
