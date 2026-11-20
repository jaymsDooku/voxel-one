import dev.jayms.net.city.RoadMarkings;
import dev.jayms.net.city.StressGrid;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Compare the production grid filter with explicit road/pixel rectangle intersections. */
public final class DistantGridFilterPlaytest {
    private static final int PERIOD=StressGrid.STRIDE,ROAD=StressGrid.ROAD;

    private static double coverage(double center,double footprint,double band) {
        double low=center-footprint/2,high=center+footprint/2,total=0;
        for(int k=(int)Math.floor(low/PERIOD)-1;k<=(int)Math.floor(high/PERIOD)+1;k++) {
            double mid=k*PERIOD+ROAD*.5;
            total+=Math.max(0,Math.min(high,mid+band*.5)-Math.max(low,mid-band*.5));
        }
        return total/footprint;
    }
    private static double union(double x,double z){return x+z-x*z;}

    private static int shader(int type,String source) {
        int id=glCreateShader(type);
        glShaderSource(id,source);glCompileShader(id);
        if(glGetShaderi(id,GL_COMPILE_STATUS)==GL_FALSE) throw new AssertionError(glGetShaderInfoLog(id));
        return id;
    }

    public static void main(String[] args) throws Exception {
        Path output=Files.createDirectories(Path.of(args[0]));
        if(!glfwInit()) throw new AssertionError("GLFW initialization");
        glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
        glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window=glfwCreateWindow(16,16,"Distant grid filter test",0,0);
        if(window==0) throw new AssertionError("GL context");
        try {
            glfwMakeContextCurrent(window);GL.createCapabilities();
            String helper;
            try(var stream=Objects.requireNonNull(DistantGridFilterPlaytest.class.getResourceAsStream("/shaders/stress-grid.glsl"))){
                helper=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
            }
            int vs=shader(GL_VERTEX_SHADER,"#version 330 core\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.-1.,0.,1.);}");
            int fs=shader(GL_FRAGMENT_SHADER,"#version 330 core\n"+helper+"""
                    uniform vec2 pos,footprint,origin; uniform vec3 layoutParams; out vec4 color;
                    void main(){color=vec4(gridAlbedo(pos,footprint,origin,layoutParams,
                        vec3(1,0,0),vec3(0,1,0),vec3(0,0,1)),1);}
                    """);
            int program=glCreateProgram();glAttachShader(program,vs);glAttachShader(program,fs);glLinkProgram(program);
            if(glGetProgrami(program,GL_LINK_STATUS)==GL_FALSE) throw new AssertionError(glGetProgramInfoLog(program));
            glUseProgram(program);
            glUniform2f(glGetUniformLocation(program,"origin"),StressGrid.MIN_X,StressGrid.MIN_Z);
            int fbo=glGenFramebuffers(),tex=glGenTextures(),vao=glGenVertexArrays();
            glBindVertexArray(vao);glBindTexture(GL_TEXTURE_2D,tex);
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,1,1,0,GL_RGBA,GL_FLOAT,(ByteBuffer)null);
            glBindFramebuffer(GL_FRAMEBUFFER,fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,tex,0);
            if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE) throw new AssertionError("Float readback target");
            glViewport(0,0,1,1);
            double worst=0;int checks=0;
            // Positions on either side of zero and quadtree boundaries, from
            // resolved half-block samples to elongated grazing-angle footprints.
            for(float paint:new float[]{0,RoadMarkings.GRID_STRIPE_WIDTH})
                for(float[] p:new float[][]{{1.5f,9},{0,8},{3,1.5f},{-17.5f,-36.5f},{256,512},{-1024,128},{1024.25f,-512.5f}})
                    for(float[] w:new float[][]{{.0625f,.125f},{1,1},{.25f,63},{19,19},{173,381}}){
                        glUniform3f(glGetUniformLocation(program,"layoutParams"),PERIOD,ROAD,paint);
                        glUniform2f(glGetUniformLocation(program,"pos"),p[0]+StressGrid.MIN_X,p[1]+StressGrid.MIN_Z);
                        glUniform2f(glGetUniformLocation(program,"footprint"),w[0],w[1]);
                        glDrawArrays(GL_TRIANGLES,0,3);
                        float[] result=new float[4];glReadPixels(0,0,1,1,GL_RGBA,GL_FLOAT,result);
                        double road=union(coverage(p[0],w[0],ROAD),coverage(p[1],w[1],ROAD));
                        double stripe=union(coverage(p[0],w[0],paint),coverage(p[1],w[1],paint));
                        double[] expected={1-road,road-stripe,stripe};
                        for(int i=0;i<3;i++) worst=Math.max(worst,Math.abs(expected[i]-result[i]));
                        checks++;
                    }
            if(glGetError()!=GL_NO_ERROR || !(worst<.001)) throw new AssertionError("Grid coverage error "+worst);
            String report=String.format(Locale.ROOT,"%s: %d GPU coverage checks; maximum absolute area error %.8f%n",
                    glGetString(GL_RENDERER),checks,worst);
            Files.writeString(output.resolve("grid-filter.txt"),report);System.out.print(report);
            glDeleteFramebuffers(fbo);glDeleteTextures(tex);glDeleteVertexArrays(vao);
            glDeleteProgram(program);glDeleteShader(vs);glDeleteShader(fs);
        } finally {glfwDestroyWindow(window);glfwTerminate();}
    }
}
