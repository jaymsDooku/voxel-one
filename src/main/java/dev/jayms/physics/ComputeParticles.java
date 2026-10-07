package dev.jayms.physics;

import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL43.*;

/** Optional GL 4.3 SSBO integration. CPU path has identical semi-implicit Euler semantics. */
public final class ComputeParticles implements AutoCloseable {
    private int program,buffer;
    public boolean available() {return GL.getCapabilities().OpenGL43;}
    public void integrate(float[] particles,float dt,float gravity) {
        if(particles.length%8!=0||dt<0||!Float.isFinite(dt)||!Float.isFinite(gravity))throw new IllegalArgumentException("Eight floats per particle: position vec4, velocity vec4");
        if(particles.length==0)return;
        if(!available()) {cpu(particles,dt,gravity);return;}
        if(program==0) {
            int shader=glCreateShader(GL_COMPUTE_SHADER);
            glShaderSource(shader,"""
                    #version 430
                    layout(local_size_x=64) in;
                    struct Particle { vec4 p; vec4 v; };
                    layout(std430,binding=0) buffer State { Particle particles[]; };
                    uniform uint count;
                    uniform float dt,gravity;
                    void main() {
                        uint i=gl_GlobalInvocationID.x;if(i>=count)return;
                        if(particles[i].p.w<=0)return;
                        particles[i].v.y+=gravity*dt;
                        particles[i].p.xyz+=particles[i].v.xyz*dt;
                    }
                    """);
            glCompileShader(shader);
            if(glGetShaderi(shader,GL_COMPILE_STATUS)==0){glDeleteShader(shader);throw new IllegalStateException("Physics compute shader compilation failed");}
            program=glCreateProgram();glAttachShader(program,shader);glLinkProgram(program);glDeleteShader(shader);
            if(glGetProgrami(program,GL_LINK_STATUS)==0){glDeleteProgram(program);program=0;throw new IllegalStateException("Physics compute link failed");}
            buffer=glGenBuffers();
        }
        int oldProgram=glGetInteger(GL_CURRENT_PROGRAM),oldBuffer=glGetInteger(GL_SHADER_STORAGE_BUFFER_BINDING),oldBase=glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING,0);
        try {
            glBindBuffer(GL_SHADER_STORAGE_BUFFER,buffer);glBufferData(GL_SHADER_STORAGE_BUFFER,particles,GL_DYNAMIC_COPY);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER,0,buffer);glUseProgram(program);
            glUniform1ui(glGetUniformLocation(program,"count"),particles.length/8);
            glUniform1f(glGetUniformLocation(program,"dt"),dt);glUniform1f(glGetUniformLocation(program,"gravity"),gravity);
            glDispatchCompute((particles.length/8+63)/64,1,1);glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT|GL_SHADER_STORAGE_BARRIER_BIT);
            glGetBufferSubData(GL_SHADER_STORAGE_BUFFER,0,particles);
        } finally {glUseProgram(oldProgram);glBindBufferBase(GL_SHADER_STORAGE_BUFFER,0,oldBase);glBindBuffer(GL_SHADER_STORAGE_BUFFER,oldBuffer);}
    }
    public static void cpu(float[] p,float dt,float gravity) {for(int i=0;i<p.length;i+=8)if(p[i+3]>0){p[i+5]+=gravity*dt;for(int a=0;a<3;a++)p[i+a]+=p[i+4+a]*dt;}}
    @Override public void close(){if(buffer!=0)glDeleteBuffers(buffer);if(program!=0)glDeleteProgram(program);buffer=program=0;}
}
