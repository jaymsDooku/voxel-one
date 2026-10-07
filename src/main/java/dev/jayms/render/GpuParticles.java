package dev.jayms.render;

import dev.jayms.ShaderProgram;
import org.joml.Matrix4f;
import java.util.List;
import static org.lwjgl.opengl.GL33.*;

/** Transform-feedback simulation runs on OpenGL 3.3 GPUs; no per-frame CPU particle integration. */
public final class GpuParticles implements AutoCloseable {
    private final int update,vao=glGenVertexArrays();
    private final int[] buffers={glGenBuffers(),glGenBuffers()};
    private final ShaderProgram render=new ShaderProgram("shaders/particles.vert","shaders/particles.frag");
    private int count,index;private long time;
    private List<ClusteredLights.Light> emitters=List.of();
    public GpuParticles(){
        int vertex=glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vertex,"""
                #version 330 core
                layout(location=0) in vec4 aPosition;
                layout(location=1) in vec4 aVelocity;
                layout(location=2) in vec4 aOrigin;
                uniform float uDt;
                out vec4 oPosition,oVelocity,oOrigin;
                float hash(float n){return fract(sin(n*127.1)*43758.5453);}
                void main(){
                    oPosition=aPosition;oVelocity=aVelocity;oOrigin=aOrigin;
                    oPosition.w+=uDt;
                    if(oPosition.w>2.5){
                        oPosition=vec4(aOrigin.xyz,0);
                        float seed=aOrigin.w+aPosition.w;
                        oVelocity.xyz=vec3(hash(seed)*.7-.35,.3+hash(seed+1.)*.5,hash(seed+2.)*.7-.35);
                    }
                    oVelocity.y+=uDt*.08;oPosition.xyz+=oVelocity.xyz*uDt;
                }
                """);glCompileShader(vertex);
        if(glGetShaderi(vertex,GL_COMPILE_STATUS)==GL_FALSE)throw new IllegalStateException(glGetShaderInfoLog(vertex));
        update=glCreateProgram();glAttachShader(update,vertex);
        glTransformFeedbackVaryings(update,new CharSequence[]{"oPosition","oVelocity","oOrigin"},GL_INTERLEAVED_ATTRIBS);
        glLinkProgram(update);glDeleteShader(vertex);
        if(glGetProgrami(update,GL_LINK_STATUS)==GL_FALSE)throw new IllegalStateException(glGetProgramInfoLog(update));
    }
    public void emitters(List<ClusteredLights.Light> lights){
        emitters=lights.subList(0,Math.min(16,lights.size()));count=emitters.size()*16;
        float[] data=new float[Math.max(12,count*12)];
        for(int i=0;i<count;i++){
            var l=emitters.get(i/16);int a=i*12;
            data[a]=l.position().x;data[a+1]=l.position().y;data[a+2]=l.position().z;data[a+3]=i%16/16f*2.5f;
            data[a+5]=.5f;data[a+7]=i/16;
            data[a+8]=l.position().x;data[a+9]=l.position().y;data[a+10]=l.position().z;data[a+11]=i;
        }
        for(int buffer:buffers){glBindBuffer(GL_ARRAY_BUFFER,buffer);glBufferData(GL_ARRAY_BUFFER,data,GL_DYNAMIC_COPY);}
        glBindBuffer(GL_ARRAY_BUFFER,0);time=System.nanoTime();
    }
    private void bind(int buffer){
        glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,buffer);
        for(int attr=0;attr<3;attr++){glVertexAttribPointer(attr,4,GL_FLOAT,false,48,attr*16L);glEnableVertexAttribArray(attr);}
    }
    public void render(Matrix4f vp,int viewportHeight){
        if(count==0)return;long now=System.nanoTime();float dt=Math.min(.05f,(now-time)/1e9f);time=now;
        int previous=glGetInteger(GL_CURRENT_PROGRAM);
        glUseProgram(update);glUniform1f(glGetUniformLocation(update,"uDt"),dt);
        bind(buffers[index]);glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER,0,buffers[1-index]);
        glEnable(GL_RASTERIZER_DISCARD);glBeginTransformFeedback(GL_POINTS);glDrawArrays(GL_POINTS,0,count);glEndTransformFeedback();glDisable(GL_RASTERIZER_DISCARD);
        glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER,0,0);index=1-index;
        render.bind();render.setMatrix4("uVP",vp);render.setFloat("uHeight",viewportHeight);
        for(int i=0;i<emitters.size();i++){var c=emitters.get(i).color();render.setVector3("uColours["+i+"]",c.x,c.y,c.z);}
        bind(buffers[index]);glEnable(GL_PROGRAM_POINT_SIZE);glEnable(GL_BLEND);glBlendFunc(GL_ONE,GL_ONE);glDepthMask(false);
        glDrawArrays(GL_POINTS,0,count);
        glDepthMask(true);glDisable(GL_BLEND);glDisable(GL_PROGRAM_POINT_SIZE);glBindVertexArray(0);glUseProgram(previous);
    }
    public int count(){return count;}
    @Override public void close(){glDeleteProgram(update);render.close();glDeleteVertexArrays(vao);for(int b:buffers)glDeleteBuffers(b);}
}
