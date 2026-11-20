import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Read back directional shadow visibility on a known plane, with a blocker covering its left half. */
public final class ShadowReceiverPlaytest {
    private static final int WIDTH = 48, HEIGHT = 16;
    private static final String PROBE = """
            uniform vec3 receiverNormal, sun;
            uniform vec2 depthSlope, phase;
            out vec4 result;
            void main() {
                vec2 uv = .5 + (gl_FragCoord.xy - vec2(24.,8.) + phase) / vec2(textureSize(uShadow,0).xy);
                vec3 q = vec3(uv, .5 + dot(uv - .5, depthSlope));
                vec4 world = inverse(uShadowMatrix[0]) * vec4(q * 2. - 1., 1.);
                result = vec4(shadowAt(world.xyz/world.w, receiverNormal, sun));
            }
            """;
    private static int compile(int type, String source) {
        int id = glCreateShader(type);
        glShaderSource(id, source);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) throw new AssertionError(glGetShaderInfoLog(id));
        return id;
    }

    public static void main(String[] args) throws IOException {
        if (!glfwInit()) throw new AssertionError("GLFW initialization failed");
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(WIDTH, HEIGHT, "Directional shadow checks", 0, 0);
        if (window == 0) throw new AssertionError("GL context creation failed");
        try {
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            String source;
            try (var stream = Objects.requireNonNull(ShadowReceiverPlaytest.class.getResourceAsStream("/shaders/voxel.frag"))) {
                source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            String helpers = source.substring(source.indexOf("vec2 disk("), source.indexOf("#include \"shaders/irradiance.glsl\""));
            int vs = compile(GL_VERTEX_SHADER, """
                    #version 330 core
                    void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.-1.,0.,1.);}
                    """);
            int fs = compile(GL_FRAGMENT_SHADER, """
                    #version 330 core
                    uniform int uShadowEnabled,uHeld,uShadowCascades;
                    uniform sampler2DArray uShadow;
                    uniform mat4 uShadowMatrix[3];
                    """ + helpers + PROBE);
            int program = glCreateProgram();
            glAttachShader(program, vs);
            glAttachShader(program, fs);
            glLinkProgram(program);
            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) throw new AssertionError(glGetProgramInfoLog(program));
            glUseProgram(program);
            glUniform1i(glGetUniformLocation(program, "uShadowEnabled"), 1);
            glUniform1i(glGetUniformLocation(program, "uShadowCascades"), 1);
            glUniform1i(glGetUniformLocation(program, "uShadow"), 0);
            int target = glGenTextures(), fbo = glGenFramebuffers(), vao = glGenVertexArrays();
            glBindVertexArray(vao);
            glBindTexture(GL_TEXTURE_2D, target);
            glTexImage2D(GL_TEXTURE_2D,0,GL_R32F,WIDTH,HEIGHT,0,GL_RED,GL_FLOAT,(ByteBuffer)null);
            glBindFramebuffer(GL_FRAMEBUFFER,fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,target,0);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) throw new AssertionError("Readback framebuffer incomplete");
            glViewport(0,0,WIDTH,HEIGHT);
            int shadow = glGenTextures();
            glBindTexture(GL_TEXTURE_2D_ARRAY, shadow);
            glTexParameteri(GL_TEXTURE_2D_ARRAY,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D_ARRAY,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
            int[] counts = new int[3];
            Vector3f sun = new Vector3f(.29f,.82f,.49f).normalize();
            glUniform3f(glGetUniformLocation(program,"sun"),sun.x,sun.y,sun.z);
            for (int resolution : new int[]{1024,2048})
                for (float extent : new float[]{32,96,190}) {
                    float distance = extent == 190 ? extent + 16 : 384;
                    Matrix4f matrix = new Matrix4f().ortho(-extent,extent,-extent,extent,1,distance*2)
                            .lookAt(new Vector3f(sun).mul(distance),new Vector3f(),new Vector3f(0,1,0));
                    glUniformMatrix4fv(glGetUniformLocation(program,"uShadowMatrix[0]"),false,matrix.get(new float[16]));
                    for (Vector3f normal : new Vector3f[]{new Vector3f(1,0,0),new Vector3f(0,1,0),new Vector3f(0,0,1)}) {
                        check(program,resolution,matrix,normal,.001f,counts);
                        // A second caster sits one detailed voxel (1/16 block) above the plane.
                        float leafDepth = .0625f / (sun.dot(normal) * (2*distance-1));
                        check(program,resolution,matrix,normal,leafDepth,counts);
                    }
                }
            String report = String.format(Locale.ROOT,"%s: %d plane probes; %d falsely shadowed; %d missed blockers.%n",
                    glGetString(GL_RENDERER),counts[0],counts[1],counts[2]);
            System.out.print(report);
            Files.writeString(Path.of(args[0]),report);
            if (counts[1] != 0 || counts[2] != 0 || glGetError() != GL_NO_ERROR) throw new AssertionError("Directional shadow readback");
            glDeleteProgram(program);glDeleteShader(vs);glDeleteShader(fs);
            glDeleteVertexArrays(vao);glDeleteTextures(shadow);glDeleteTextures(target);glDeleteFramebuffers(fbo);
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }

    private static void check(int program, int resolution, Matrix4f matrix, Vector3f normal,
                              float blockerDepth, int[] counts) {
        // Intersect light-space pixel rays with the world plane. The blocker covers x < .5.
        Matrix4f inverse = new Matrix4f(matrix).invert();
        float axisZ = normal.dot(inverse.transformDirection(new Vector3f(0,0,1)));
        float slopeU = -normal.dot(inverse.transformDirection(new Vector3f(1,0,0))) / axisZ;
        float slopeV = -normal.dot(inverse.transformDirection(new Vector3f(0,1,0))) / axisZ;
        glUniform3f(glGetUniformLocation(program,"receiverNormal"),normal.x,normal.y,normal.z);
        glUniform2f(glGetUniformLocation(program,"depthSlope"),slopeU,slopeV);
        float[] depth = new float[resolution*resolution];
        for (int y = 0; y < resolution; y++)
            for (int x = 0; x < resolution; x++) {
                float u = (x+.5f)/resolution, v = (y+.5f)/resolution;
                depth[y*resolution+x] = .5f+(u-.5f)*slopeU+(v-.5f)*slopeV-(u<.5f?blockerDepth:0);
            }
        glTexImage3D(GL_TEXTURE_2D_ARRAY,0,GL_DEPTH_COMPONENT24,resolution,resolution,1,0,GL_DEPTH_COMPONENT,GL_FLOAT,depth);
        for (float phase : new float[]{-.39f,0,.31f}) {
            glUniform2f(glGetUniformLocation(program,"phase"),phase,-phase*.5f);
            glDrawArrays(GL_TRIANGLES,0,3);
            float[] result = new float[WIDTH*HEIGHT];
            glReadPixels(0,0,WIDTH,HEIGHT,GL_RED,GL_FLOAT,result);
            for (int i = 0; i < result.length; i++) {
                int x = i%WIDTH;
                if (Math.abs(x+.5f-WIDTH*.5f)<6) continue;
                counts[0]++;
                if (x>WIDTH/2 && result[i]!=1) counts[1]++;
                if (x<WIDTH/2 && result[i]!=0) counts[2]++;
            }
        }
    }
}
