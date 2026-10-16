import dev.jayms.Camera;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import java.nio.file.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Exercises production GLFW polling on the inherited role display. No game account is used. */
public class CameraMovementSmoke {
    static void keys(String action, String... keys) throws Exception {
        if (keys.length == 0) { glfwPollEvents(); return; }
        String[] command = new String[keys.length + 2];
        command[0] = "xdotool"; command[1] = action;
        System.arraycopy(keys, 0, command, 2, keys.length);
        if (new ProcessBuilder(command).start().waitFor() != 0) throw new AssertionError("X11 input failed");
        Thread.sleep(80);
        glfwPollEvents();
    }
    public static void main(String[] args) throws Exception {
        if (!glfwInit()) throw new AssertionError("GLFW init failed");
        long window = glfwCreateWindow(960, 600, "Voxel One free-camera movement check", 0, 0);
        if (window == 0) throw new AssertionError("Window creation failed");
        try {
            glfwMakeContextCurrent(window);
            org.lwjgl.opengl.GL.createCapabilities();
            glfwShowWindow(window); glfwFocusWindow(window); glfwPollEvents();
            Thread.sleep(300);
            Camera camera = new Camera();
            String[][] cases = {{"w"}, {"w","d"}, {"w","d","space"},
                    {"w","s","d","a","space","Shift_L"}, {}, {"w","s","d"}};
            String[] names = {"Forward", "Forward + right", "Forward + right + up", "All opposing keys", "No input", "Forward cancels; right remains"};
            StringBuilder report = new StringBuilder("Linux X11; Java " + System.getProperty("java.version") + "; production Camera.updateCamera; dt=1 second\n");
            BufferedImage evidence = new BufferedImage(960, 600, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = evidence.createGraphics();
            g.setColor(new Color(20,30,45)); g.fillRect(0,0,960,600);
            g.setFont(new Font("SansSerif", Font.PLAIN, 22)); g.setColor(Color.WHITE);
            g.drawString("Voxel One: live GLFW free-camera checks", 30, 40);
            for (int i=0; i<cases.length; i++) {
                camera.position().zero();
                keys("keydown", cases[i]);
                camera.updateCamera(window, 1);
                keys("keyup", cases[i]);
                Vector3f delta = new Vector3f(camera.position());
                float expected = i==3 || i==4 ? 0 : 8;
                if (!delta.isFinite() || Math.abs(delta.length()-expected)>0.00001f)
                    throw new AssertionError(names[i] + " " + delta);
                Vector3f direction = switch(i) {
                    case 0 -> new Vector3f(0,0,-8);
                    case 1 -> new Vector3f(1,0,-1).normalize().mul(8);
                    case 2 -> new Vector3f(1,1,-1).normalize().mul(8);
                    case 5 -> new Vector3f(8,0,0);
                    default -> new Vector3f();
                };
                if (delta.distance(direction)>0.00001f) throw new AssertionError("Direction " + names[i]);
                String row = String.format(java.util.Locale.ROOT,"%s: %.6f units — PASS", names[i], delta.length());
                report.append(row).append('\n');
                g.drawString(row, 30, 100+i*65);
                glClearColor(0.08f,0.12f,0.18f,1); glClear(GL_COLOR_BUFFER_BIT);
                // Render the measured displacement as a line from the origin.
                glMatrixMode(GL_PROJECTION); glLoadIdentity(); glOrtho(-10,10,-10,10,-10,10);
                glMatrixMode(GL_MODELVIEW); glLoadIdentity();
                glColor3f(0.2f,1,0.5f); glBegin(GL_LINES); glVertex3f(0,0,0); glVertex3f(delta.x,delta.y-delta.z,0); glEnd();
                glfwSwapBuffers(window);
            }
            g.drawString("Real X11 keys → GLFW polling → normalized displacement",30,540); g.dispose();
            Path out = Path.of(args[0]); Files.createDirectories(out);
            var pixels = BufferUtils.createByteBuffer(960*600*3);
            for (int y=599; y>=0; y--) for (int x=0; x<960; x++) {
                int rgb = evidence.getRGB(x,y);
                pixels.put((byte)(rgb>>16)).put((byte)(rgb>>8)).put((byte)rgb);
            }
            pixels.flip();
            glMatrixMode(GL_PROJECTION); glLoadIdentity(); glOrtho(0,960,0,600,-1,1);
            glMatrixMode(GL_MODELVIEW); glLoadIdentity(); glRasterPos2i(0,0);
            glDrawPixels(960,600,GL_RGB,GL_UNSIGNED_BYTE,pixels);
            glfwSwapBuffers(window); glFinish(); Thread.sleep(500);
            int[] x = new int[1], y = new int[1]; glfwGetWindowPos(window,x,y);
            BufferedImage capture = new Robot().createScreenCapture(new Rectangle(x[0],y[0],960,600));
            ImageIO.write(capture,"png",out.resolve("free-camera-movement.png").toFile());
            Files.writeString(out.resolve("free-camera-glfw.txt"), report);
            System.out.print(report);
        } finally { glfwDestroyWindow(window); glfwTerminate(); }
    }
}
