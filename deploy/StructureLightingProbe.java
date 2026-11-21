import dev.jayms.Main;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.lwjgl.opengl.GL33.*;

/** One-pixel readback of the shipped irradiance function using Main's actual
 * previous-frame uniforms/textures. Synthetic workflow only; restores GL state. */
final class StructureLightingProbe {
    static int compile(int type,String source) {
        int shader=glCreateShader(type);
        try{glShaderSource(shader,source);glCompileShader(shader);if(glGetShaderi(shader,GL_COMPILE_STATUS)==0)throw new AssertionError("Lighting probe compile: "+glGetShaderInfoLog(shader));return shader;}
        catch(RuntimeException|Error failure){glDeleteShader(shader);throw failure;}
    }
    static int program()throws Exception {
        String include;
        try(var stream=Main.class.getResourceAsStream("/shaders/irradiance.glsl")){if(stream==null)throw new AssertionError("Shipped irradiance source missing");include=new String(stream.readAllBytes(),StandardCharsets.UTF_8);}
        int vertex=0,fragment=0,program=0;
        try {
            vertex=compile(GL_VERTEX_SHADER,"#version 330 core\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.-1.,0,1);}");
            fragment=compile(GL_FRAGMENT_SHADER,"#version 330 core\nuniform int uPlanetLighting;uniform vec3 uPoint;out vec4 result;\n"+include+"\nvoid main(){result=irradiance(uPoint);}");
            program=glCreateProgram();glAttachShader(program,vertex);glAttachShader(program,fragment);glLinkProgram(program);
            if(glGetProgrami(program,GL_LINK_STATUS)==0)throw new AssertionError("Lighting probe link: "+glGetProgramInfoLog(program));
            return program;
        }catch(Exception|Error failure){if(program!=0)glDeleteProgram(program);throw failure;}
        finally{if(vertex!=0)glDeleteShader(vertex);if(fragment!=0)glDeleteShader(fragment);}
    }
    static List<float[]> sample(int source,List<float[]> points)throws Exception {
        if(points.size()>8)throw new IllegalArgumentException("Lighting probe point budget");
        int draw=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),read=glGetInteger(GL_READ_FRAMEBUFFER_BINDING),previous=glGetInteger(GL_CURRENT_PROGRAM),array=glGetInteger(GL_VERTEX_ARRAY_BINDING),active=glGetInteger(GL_ACTIVE_TEXTURE),pack=glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        int[] viewport=new int[4];glGetIntegerv(GL_VIEWPORT,viewport);
        int[] toggles={GL_DEPTH_TEST,GL_BLEND,GL_CULL_FACE,GL_SCISSOR_TEST,GL_FRAMEBUFFER_SRGB};boolean[] enabled=new boolean[toggles.length];for(int i=0;i<toggles.length;i++)enabled[i]=glIsEnabled(toggles[i]);
        glActiveTexture(GL_TEXTURE15);int oldTexture=glGetInteger(GL_TEXTURE_BINDING_2D);
        int shader=0,fbo=0,texture=0,vao=0;
        try {
            shader=program();fbo=glGenFramebuffers();texture=glGenTextures();vao=glGenVertexArrays();
            glBindTexture(GL_TEXTURE_2D,texture);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,1,1,0,GL_RGBA,GL_FLOAT,0L);
            glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);
            if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new AssertionError("Lighting probe framebuffer incomplete");
            glViewport(0,0,1,1);for(int toggle:toggles)glDisable(toggle);glBindBuffer(GL_PIXEL_PACK_BUFFER,0);glBindVertexArray(vao);glUseProgram(shader);
            for(String name:List.of("uPlanetLighting","uHasIrradiance","uSkyColumns","uSkyOffset","uRootBuffer","uFineOffset","uSkyIndexOrigin","uSkyIndexSpan","uSkyIndexOffset","uIrradiance","uFineRoots","uFineLight")) {
                int from=glGetUniformLocation(source,name),to=glGetUniformLocation(shader,name);
                if(from<0||to<0)throw new AssertionError("Lighting probe integer uniform "+name);glUniform1i(to,glGetUniformi(source,from));
            }
            glUniform1f(glGetUniformLocation(shader,"uAmbient"),glGetUniformf(source,glGetUniformLocation(source,"uAmbient")));
            for(String name:List.of("uVolumeOrigin","uVolumeSize")) {
                float[] value=new float[3];glGetUniformfv(source,glGetUniformLocation(source,name),value);glUniform3f(glGetUniformLocation(shader,name),value[0],value[1],value[2]);
            }
            var results=new ArrayList<float[]>();
            for(float[] point:points) {
                if(point.length!=3)throw new IllegalArgumentException("Lighting probe coordinate count");for(float value:point)if(!Float.isFinite(value))throw new IllegalArgumentException("Lighting probe finite point required");
                glUniform3f(glGetUniformLocation(shader,"uPoint"),point[0],point[1],point[2]);glDrawArrays(GL_TRIANGLES,0,3);
                float[] result=new float[4];glReadPixels(0,0,1,1,GL_RGBA,GL_FLOAT,result);for(float value:result)if(!Float.isFinite(value))throw new AssertionError("Nonfinite lighting probe output");results.add(result);
            }
            if(glGetError()!=GL_NO_ERROR)throw new AssertionError("Lighting probe GL error");return results;
        }finally {
            glUseProgram(previous);glBindVertexArray(array);glBindFramebuffer(GL_DRAW_FRAMEBUFFER,draw);glBindFramebuffer(GL_READ_FRAMEBUFFER,read);glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);glBindBuffer(GL_PIXEL_PACK_BUFFER,pack);
            for(int i=0;i<toggles.length;i++)if(enabled[i])glEnable(toggles[i]);else glDisable(toggles[i]);
            glActiveTexture(GL_TEXTURE15);glBindTexture(GL_TEXTURE_2D,oldTexture);glActiveTexture(active);
            if(vao!=0)glDeleteVertexArrays(vao);if(texture!=0)glDeleteTextures(texture);if(fbo!=0)glDeleteFramebuffers(fbo);if(shader!=0)glDeleteProgram(shader);
        }
    }
}
