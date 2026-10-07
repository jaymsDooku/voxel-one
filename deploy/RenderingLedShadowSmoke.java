import dev.jayms.net.*;
import dev.jayms.render.TransportField;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import org.lwjgl.opengl.GL;

/** Executes the production fragment helper against actual transport textures. */
public final class RenderingLedShadowSmoke {
    static int program,vao,radiance,distance;
    static final List<String> results=new ArrayList<>();
    static int shader(int type,String source){int id=glCreateShader(type);glShaderSource(id,source);glCompileShader(id);if(glGetShaderi(id,GL_COMPILE_STATUS)==0)throw new AssertionError(glGetShaderInfoLog(id));return id;}
    static float check(String name,float[] p,float[] target,int[][] blocks,float expected){
        int[] material=new int[16*8*8];
        for(int[] b:blocks)material[b[0]+16*(b[1]+8*b[2])]=WorldVoxels.encode(b[3]);
        var field=new TransportField(16,8,8,material,new byte[material.length*4]);
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_3D,radiance);glTexImage3D(GL_TEXTURE_3D,0,GL_RGBA16F,16,8,8,0,GL_RGBA,GL_FLOAT,field.radiance);
        glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_3D,distance);glTexImage3D(GL_TEXTURE_3D,0,GL_R16F,16,8,8,0,GL_RED,GL_FLOAT,field.distance);
        glUniform3f(glGetUniformLocation(program,"uOrigin"),p[0],p[1],p[2]);glUniform3f(glGetUniformLocation(program,"uTarget"),target[0],target[1],target[2]);
        glDrawArrays(GL_TRIANGLES,0,3);float[] pixel=new float[4];glReadPixels(0,0,1,1,GL_RGBA,GL_FLOAT,pixel);
        if(!Float.isFinite(pixel[0])||Math.abs(pixel[0]-expected)>.001f)throw new AssertionError(name+" expected="+expected+" observed="+pixel[0]);
        results.add(name+": expected="+expected+", observed="+pixel[0]);return pixel[0];
    }
    public static void main(String[] args)throws Exception{
        glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);if(!glfwInit())throw new AssertionError("GLFW init");
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window=glfwCreateWindow(64,64,"LED segment GPU checks",0,0);if(window==0)throw new AssertionError("Window");glfwMakeContextCurrent(window);GL.createCapabilities();
        String source=Files.readString(Path.of("src/main/resources/shaders/voxel.frag"));String helper=source.substring(source.indexOf("bool volumeInside"),source.indexOf("vec3 cone"));
        int vs=shader(GL_VERTEX_SHADER,"#version 330 core\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.-1.,0,1);}");
        int fs=shader(GL_FRAGMENT_SHADER,"#version 330 core\nuniform vec3 uVolumeOrigin,uVolumeSize,uOrigin,uTarget;uniform sampler3D uVoxelRadiance,uDistanceField;out vec4 color;\n"+helper+"\nvoid main(){color=vec4(pointShadow(uOrigin,uTarget));}");
        program=glCreateProgram();glAttachShader(program,vs);glAttachShader(program,fs);glLinkProgram(program);if(glGetProgrami(program,GL_LINK_STATUS)==0)throw new AssertionError(glGetProgramInfoLog(program));glUseProgram(program);
        glUniform3f(glGetUniformLocation(program,"uVolumeOrigin"),0,0,0);glUniform3f(glGetUniformLocation(program,"uVolumeSize"),16,8,8);glUniform1i(glGetUniformLocation(program,"uVoxelRadiance"),0);glUniform1i(glGetUniformLocation(program,"uDistanceField"),1);
        radiance=glGenTextures();distance=glGenTextures();for(int id:new int[]{radiance,distance}){glBindTexture(GL_TEXTURE_3D,id);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);}
        int fbo=glGenFramebuffers(),color=glGenTextures();glBindTexture(GL_TEXTURE_2D,color);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,1,1,0,GL_RGBA,GL_FLOAT,(java.nio.ByteBuffer)null);glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,color,0);if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new AssertionError("Float framebuffer");
        vao=glGenVertexArrays();glBindVertexArray(vao);glViewport(0,0,1,1);
        float[] origin={.5f,3.5f,3.5f},target={6.5f,3.5f,3.5f};int[] led={6,3,3,Blocks.LED};
        check("empty segment",origin,target,new int[][]{},1);
        check("target LED excludes self-occlusion",origin,target,new int[][]{led},1);
        check("wall before LED",origin,target,new int[][]{led,{3,3,3,Blocks.STONE}},0);
        check("wall adjacent before LED",origin,target,new int[][]{led,{5,3,3,Blocks.STONE}},0);
        check("wall beyond LED",origin,target,new int[][]{led,{8,3,3,Blocks.STONE}},1);
        check("beyond empty endpoint",origin,target,new int[][]{{8,3,3,Blocks.STONE}},1);
        check("near emitter",new float[]{5.9f,3.5f,3.5f},target,new int[][]{led},1);
        check("fractional emitter endpoint",origin,new float[]{6.125f,3.125f,3.125f},new int[][]{led},1);
        check("inside emitter",target,target,new int[][]{led},1);
        check("reverse ray beyond light",new float[]{9.5f,3.5f,3.5f},new float[]{3.5f,3.5f,3.5f},new int[][]{{3,3,3,Blocks.LED},{2,3,3,Blocks.STONE}},1);
        check("reverse ray before light",new float[]{9.5f,3.5f,3.5f},new float[]{3.5f,3.5f,3.5f},new int[][]{{3,3,3,Blocks.LED},{5,3,3,Blocks.STONE}},0);
        check("diagonal emitter",new float[]{.5f,.5f,.5f},new float[]{6.5f,6.5f,6.5f},new int[][]{{6,6,6,Blocks.LED}},1);
        check("diagonal blocker",new float[]{.5f,.5f,.5f},new float[]{6.5f,6.5f,6.5f},new int[][]{{6,6,6,Blocks.LED},{3,3,3,Blocks.STONE}},0);
        check("corner-only contact is clear",new float[]{.5f,.5f,.5f},new float[]{6.5f,6.5f,6.5f},new int[][]{{6,6,6,Blocks.LED},{3,2,3,Blocks.STONE}},1);
        if(glGetError()!=GL_NO_ERROR)throw new AssertionError("GL errors");
        Files.writeString(Path.of(args[0]),"Playtest: PASS. Production pointShadow GLSL helper; real worker TransportField textures; inherited assigned X11; synthetic cells; "+glGetString(GL_VERSION)+"; "+glGetString(GL_RENDERER)+".\n"+String.join("\n",results)+"\n");
        glDeleteProgram(program);glDeleteShader(vs);glDeleteShader(fs);glDeleteVertexArrays(vao);glDeleteFramebuffers(fbo);glDeleteTextures(color);glDeleteTextures(radiance);glDeleteTextures(distance);glfwDestroyWindow(window);glfwTerminate();System.out.println("PASS: "+results.size()+" GPU point-light segment cases");
    }
}
