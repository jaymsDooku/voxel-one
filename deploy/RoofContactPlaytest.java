import dev.jayms.ShaderProgram;
import java.util.Locale;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Measures the production screen-space contact pass on oblique walls with and without raised trim. */
public final class RoofContactPlaytest {
    private static final int WIDTH = 384, HEIGHT = 216;
    private static final float FOV = (float) Math.toRadians(65), NEAR = .1f, FAR = 4096;

    private record Wall(Vector3f normal, Vector3f tangent, Vector3f sun) {
        static Wall rotated(double angle) {
            float x = (float) Math.cos(angle), y = (float) Math.sin(angle);
            Vector3f normal = new Vector3f(.94f*x, .94f*y, .34f).normalize();
            Vector3f tangent = new Vector3f(.34f*x, .34f*y, -.94f).normalize();
            return new Wall(normal, tangent, new Vector3f(tangent).fma(.10f, normal).normalize());
        }
    }

    private static Vector3f rayAtPixel(int x, int y) {
        float scale = (float) Math.tan(FOV/2);
        return new Vector3f((2*(x+.5f)/WIDTH-1)*scale*WIDTH/HEIGHT, (2*(y+.5f)/HEIGHT-1)*scale, -1);
    }

    // Intersect an infinite-length strip, half a block wide and half a block high.
    // Coordinates are tangent distance and height above the receiver.
    private static float trimHit(float s, float h, float ds, float dh) {
        float a = (-.25f-s)/ds, b = (.25f-s)/ds, c = -h/dh, d = (.5f-h)/dh;
        float near = Math.max(Math.min(a,b), Math.min(c,d)), far = Math.min(Math.max(a,b), Math.max(c,d));
        return near <= far && near > 0 ? near : Float.POSITIVE_INFINITY;
    }

    private static void check(ShaderProgram shader, Wall wall, float distance, float jitter, boolean trim) {
        check(shader,wall,distance,jitter,trim,false,1);
    }

    private static void check(ShaderProgram shader, Wall wall, float distance, float jitter, boolean trim,
                              boolean preview,float directShare) {
        float[] depths = new float[WIDTH*HEIGHT];
        boolean[] measured = new boolean[depths.length];
        float startHeight = wall.normal.z*distance, startTangent = wall.tangent.z*distance;
        for (int y=0; y<HEIGHT; y++) for (int x=0; x<WIDTH; x++) {
            int i = x+y*WIDTH;
            Vector3f ray = rayAtPixel(x,y);
            float dh = wall.normal.dot(ray), ds = wall.tangent.dot(ray);
            float receiver = -startHeight/dh;
            float strip = trim ? trimHit(startTangent,startHeight,ds,dh) : Float.POSITIVE_INFINITY;
            float visible = Math.min(receiver > 0 ? receiver : FAR, strip);
            depths[i] = visible >= FAR ? 1 : (float)((double)FAR/(FAR-(double)NEAR)*(1-(double)NEAR/visible));

            if (x<9 || y<9 || x>=WIDTH-8 || y>=HEIGHT-8 || receiver<1 || receiver>=distance*1.7f || receiver>=strip)
                continue;
            float offset = startTangent+ds*receiver;
            // The light travels toward positive tangent. Only measure receiver points
            // whose 0.96-block light ray clearly intersects the raised strip.
            measured[i] = !trim || (offset>-.87f && offset<-.48f);
        }

        // White surface with a known direct-light fraction in preview scene alpha.
        glActiveTexture(GL_TEXTURE0);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA16F,1,1,0,GL_RGBA,GL_FLOAT,new float[]{1,1,1,directShare});
        glActiveTexture(GL_TEXTURE1);
        glTexImage2D(GL_TEXTURE_2D,0,GL_DEPTH_COMPONENT24,WIDTH,HEIGHT,0,GL_DEPTH_COMPONENT,GL_FLOAT,depths);
        shader.setFloat("uJitterU",jitter/WIDTH);
        shader.setFloat("uJitterV",-jitter/HEIGHT);
        shader.setVector3("uSun",wall.sun.x,wall.sun.y,wall.sun.z);
        shader.setInt("uPreviewContactWeight",preview?1:0);
        glDrawArrays(GL_TRIANGLES,0,3);
        float[] pixels = new float[depths.length];
        glReadPixels(0,0,WIDTH,HEIGHT,GL_RED,GL_FLOAT,pixels);
        float expected=trim ? 1-.35f*(preview?directShare:1) : 1;
        int matched = 0, samples = 0;
        for (int i=0; i<pixels.length; i++) if (measured[i]) {
            samples++;
            if (Math.abs(pixels[i]-expected)<.01f) matched++;
        }
        System.out.printf(Locale.ROOT,"distance %.0f normal (%.2f,%.2f,%.2f) jitter %.3f trim %s preview %s direct %.2f: %d/%d at expected %.3f%n",
                distance,wall.normal.x,wall.normal.y,wall.normal.z,jitter,trim,preview,directShare,matched,samples,expected);
        if (samples<100 || (expected<1 ? matched<samples*.85f : matched!=samples))
            throw new AssertionError("Incorrect contact visibility");
    }

    public static void main(String[] args) {
        if (!glfwInit()) throw new AssertionError("GLFW initialization");
        glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
        glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(WIDTH,HEIGHT,"Roof contact test",0,0);
        if (window==0) throw new AssertionError("OpenGL context");
        try {
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            System.out.println(glGetString(GL_RENDERER));
            try (var shader = new ShaderProgram("shaders/fullscreen.vert","shaders/effects.frag")) {
                shader.bind();
                glBindVertexArray(glGenVertexArrays());
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D,glGenTextures());
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
                glActiveTexture(GL_TEXTURE1);
                glBindTexture(GL_TEXTURE_2D,glGenTextures());
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
                shader.setInt("uScene",0);
                shader.setInt("uDepth",1);
                shader.setInt("uContact",1);
                shader.setFloat("uDaylight",1);
                Matrix4f vp = new Matrix4f().perspective(FOV,(float)WIDTH/HEIGHT,NEAR,FAR);
                shader.setMatrix4("uVP",vp);
                shader.setMatrix4("uInverseVP",new Matrix4f(vp).invert());
                for (float angle : new float[]{0,.7f}) {
                    Wall wall = Wall.rotated(angle);
                    for (float distance : new float[]{10,30,75}) {
                        check(shader,wall,distance,0,false);
                        check(shader,wall,distance,.375f,false);
                    }
                    check(shader,wall,10,0,true);
                    for (float direct : new float[]{0,.5f,1}) check(shader,wall,10,0,true,true,direct);
                    check(shader,wall,10,0,true,false,0);
                }
                if (glGetError()!=GL_NO_ERROR) throw new AssertionError("OpenGL error");
            }
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }
}
