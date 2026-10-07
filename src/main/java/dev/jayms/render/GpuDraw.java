package dev.jayms.render;

import dev.jayms.Mesh;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL43.*;

/** Optional compute-generated indirect commands. The baseline always uses ordinary indexed draws. */
public final class GpuDraw implements AutoCloseable {
    private int program, command;
    public GpuDraw() {
        if (!GL.getCapabilities().OpenGL43 || Boolean.getBoolean("voxel.gl33")) return;
        String source="""
                #version 430 core
                layout(local_size_x=1) in;
                layout(std430,binding=0) buffer Commands { uint count,instances,firstIndex,baseVertex,baseInstance; };
                uniform mat4 uVP;
                uniform vec3 uMin,uMax;
                uniform uint uCount;
                void main(){
                    bool outside[6]=bool[6](true,true,true,true,true,true);
                    for(int i=0;i<8;i++){
                        vec3 p=mix(uMin,uMax,vec3(float(i&1),float((i>>1)&1),float((i>>2)&1)));
                        vec4 q=uVP*vec4(p,1);
                        outside[0]=outside[0]&&(q.x < -q.w); outside[1]=outside[1]&&(q.x > q.w);
                        outside[2]=outside[2]&&(q.y < -q.w); outside[3]=outside[3]&&(q.y > q.w);
                        outside[4]=outside[4]&&(q.z < -q.w); outside[5]=outside[5]&&(q.z > q.w);
                    }
                    bool visible=true;for(int i=0;i<6;i++)visible=visible&&!outside[i];
                    count=visible?uCount:0u;instances=1u;firstIndex=0u;baseVertex=0u;baseInstance=0u;
                }
                """;
        int shader=glCreateShader(GL_COMPUTE_SHADER);glShaderSource(shader,source);glCompileShader(shader);
        if(glGetShaderi(shader,GL_COMPILE_STATUS)==GL_FALSE)throw new IllegalStateException(glGetShaderInfoLog(shader));
        program=glCreateProgram();glAttachShader(program,shader);glLinkProgram(program);glDeleteShader(shader);
        if(glGetProgrami(program,GL_LINK_STATUS)==GL_FALSE)throw new IllegalStateException(glGetProgramInfoLog(program));
        command=glGenBuffers();glBindBuffer(GL_DRAW_INDIRECT_BUFFER,command);
        glBufferData(GL_DRAW_INDIRECT_BUFFER,20,GL_DYNAMIC_DRAW);glBindBuffer(GL_DRAW_INDIRECT_BUFFER,0);
    }
    public boolean enabled(){return program!=0;}
    public void draw(Mesh mesh,Matrix4f vp,float x,float y,float z){
        if(!enabled()){mesh.render();return;}
        int prior=glGetInteger(GL_CURRENT_PROGRAM);glUseProgram(program);
        try(var stack=org.lwjgl.system.MemoryStack.stackPush()){
            glUniformMatrix4fv(glGetUniformLocation(program,"uVP"),false,vp.get(stack.mallocFloat(16)));
        }
        glUniform3f(glGetUniformLocation(program,"uMin"),x,y,z);
        glUniform3f(glGetUniformLocation(program,"uMax"),x+16,y+16,z+16);
        glUniform1ui(glGetUniformLocation(program,"uCount"),mesh.indexCount());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER,0,command);glDispatchCompute(1,1,1);
        glMemoryBarrier(GL_COMMAND_BARRIER_BIT);glUseProgram(prior);
        mesh.renderIndirect(command);glBindBufferBase(GL_SHADER_STORAGE_BUFFER,0,0);
    }
    @Override public void close(){if(program!=0)glDeleteProgram(program);if(command!=0)glDeleteBuffers(command);}
}
